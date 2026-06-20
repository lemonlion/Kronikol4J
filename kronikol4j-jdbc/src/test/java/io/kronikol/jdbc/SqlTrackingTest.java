package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(KronikolExtension.class)
class SqlTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @ParameterizedTest
    @CsvSource({
        "'select * from orders', SELECT",
        "'  INSERT INTO orders VALUES (1)', INSERT",
        "'update orders set x=1', UPDATE",
        "'delete from orders', DELETE",
        "'something weird', SOMETHING"
    })
    void classifiesTheSqlVerb(String sql, String expected) {
        assertThat(SqlOperationClassifier.keyword(sql)).isEqualTo(expected);
    }

    @Test
    void recordsSqlAndRendersADatabaseInteraction() {
        SqlTracking.record(JdbcTrackingOptions.forDatabase("OrderDb"),
            "SELECT * FROM orders WHERE id = 42", "1 row");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("SELECT");
        assertThat(logs.get(0).content()).isEqualTo("SELECT * FROM orders WHERE id = 42");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"OrderDb\" as OrderDb")   // SQL category -> database shape
            .contains("Test -> OrderDb : SELECT /")
            .contains("OrderDb --> Test : OK");
    }
}
