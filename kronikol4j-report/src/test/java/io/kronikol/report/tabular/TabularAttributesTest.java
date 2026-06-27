package io.kronikol.report.tabular;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.VerificationStatus;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the TabularAttributes feature: deserialization, the typed {@link TabularInputs}/
 *  {@link TabularOutputs} carriers, position-based verification, and {@link TabularResolver}. */
class TabularAttributesTest {

    record Person(String name, int age, Role role) {
    }

    enum Role { ADMIN, USER }

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
    }

    // --- deserializer ---

    @Test
    void deserializesARecordConvertingCellStrings() {
        Person p = TabularDeserializer.deserialize(Person.class,
            new String[] {"name", "age", "role"}, new Object[] {"Ada", "36", "admin"});
        assertThat(p).isEqualTo(new Person("Ada", 36, Role.ADMIN)); // int + case-insensitive enum parsed
    }

    @Test
    void sanitizeNameMatchesDotNet() {
        assertThat(TabularDeserializer.sanitizeName("First & Last Name")).isEqualTo("firstandlastname");
    }

    // --- TabularInputs ---

    @Test
    void inputsExposeColumnsAndRows() {
        TabularInputs<Person> inputs = new TabularInputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name", "age"}, Person.class);

        assertThat(inputs.getColumns()).extracting(c -> c.name()).containsExactly("name", "age");
        assertThat(inputs.getRows()).hasSize(1);
        assertThat(inputs.getRows().get(0).values()).extracting(c -> c.value()).containsExactly("Ada", "36");
        assertThat(inputs.isLinkedOutput()).isFalse();
    }

    @Test
    void iteratingInputsEmitsRowDelimiters() {
        TabularInputs<Person> inputs = new TabularInputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN), new Person("Linus", 54, Role.USER)),
            new String[] {"name", "age"}, Person.class);

        try (var scope = TestIdentityScope.begin("T", "t1")) {
            for (Person ignored : inputs) {
                // iteration drives the delimiter emission
            }
        }
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(l -> assertThat(l.plantUml()).contains("Row 1"));
        assertThat(logs).anySatisfy(l -> assertThat(l.plantUml()).contains("Row 2"));
    }

    // --- TabularOutputs ---

    @Test
    void outputsVerifyPassesWhenActualsMatch() {
        TabularOutputs<Person> outputs = new TabularOutputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name", "age", "role"}, Person.class);
        outputs.recordActualResult(new Person("Ada", 36, Role.ADMIN));
        outputs.verify(); // no throw
        assertThat(outputs.isLinkedOutput()).isTrue();
        assertThat(outputs.getRows().get(0).values()).allSatisfy(c ->
            assertThat(c.status()).isEqualTo(VerificationStatus.SUCCESS));
    }

    @Test
    void outputsVerifyThrowsOnMismatch() {
        TabularOutputs<Person> outputs = new TabularOutputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name", "age"}, Person.class);
        outputs.recordActualResult(new Person("Ada", 99, Role.ADMIN));

        assertThatThrownBy(outputs::verify)
            .isInstanceOf(TabularVerificationException.class)
            .hasMessageContaining("Expected: 36, Actual: 99");
    }

    @Test
    void outputsVerifyFlagsSurplusAndMissingRows() {
        TabularOutputs<Person> surplus = new TabularOutputs<>(
            List.of(), new String[] {"name"}, Person.class);
        surplus.recordActualResult(new Person("Extra", 1, Role.USER));
        assertThatThrownBy(surplus::verify).isInstanceOf(TabularVerificationException.class);
        assertThat(surplus.getRows().get(0).type()).isEqualTo(TableRowType.SURPLUS);

        TabularOutputs<Person> missing = new TabularOutputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name"}, Person.class);
        assertThatThrownBy(missing::verify).isInstanceOf(TabularVerificationException.class);
        assertThat(missing.getRows().get(0).type()).isEqualTo(TableRowType.MISSING);
    }

    @Test
    void closeAutoVerifies() {
        TabularOutputs<Person> outputs = new TabularOutputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name", "age"}, Person.class);
        outputs.recordActualResult(new Person("Ada", 1, Role.ADMIN)); // mismatch
        assertThatThrownBy(outputs::close).isInstanceOf(TabularVerificationException.class);
    }

    @Test
    void getRowsBeforeVerifyAreNotProvided() {
        TabularOutputs<Person> outputs = new TabularOutputs<>(
            List.of(new Person("Ada", 36, Role.ADMIN)), new String[] {"name"}, Person.class);
        assertThat(outputs.getRows().get(0).values().get(0).status())
            .isEqualTo(VerificationStatus.NOT_PROVIDED);
    }

    // --- resolver ---

    @HeadIn({"name", "age"})
    @Inputs({"Ada", "36"})
    @Inputs({"Linus", "54"})
    static void inputsMethod(TabularInputs<Person> people) {
    }

    @HeadOut({"name", "age"})
    @Outputs({"Ada", "36"})
    static void outputsMethod(TabularOutputs<Person> people) {
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolverBuildsInputsFromAnnotations() throws Exception {
        Method m = TabularAttributesTest.class.getDeclaredMethod("inputsMethod", TabularInputs.class);
        Object[] args = TabularResolver.resolve(m, m.getAnnotation(HeadIn.class).value());

        assertThat(args).hasSize(1);
        TabularInputs<Person> inputs = (TabularInputs<Person>) args[0];
        assertThat(inputs).containsExactly(new Person("Ada", 36, null), new Person("Linus", 54, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolverBuildsOutputsFromAnnotations() throws Exception {
        Method m = TabularAttributesTest.class.getDeclaredMethod("outputsMethod", TabularOutputs.class);
        Object[] args = TabularResolver.resolve(m, null);

        TabularOutputs<Person> outputs = (TabularOutputs<Person>) args[0];
        assertThat(outputs).containsExactly(new Person("Ada", 36, null));
        assertThat(outputs.getColumns()).extracting(c -> c.name()).containsExactly("name", "age");
    }
}
