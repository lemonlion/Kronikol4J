package io.kronikol.core.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies the {@link ScenarioTitleResolver} port matches the .NET {@code ScenarioTitleResolver}. */
class ScenarioTitleResolverTest {

    // --- formatScenarioDisplayName ---

    @Test
    void formatScenarioDisplayNameHumanizesMethodFromFullyQualifiedName() {
        assertThat(ScenarioTitleResolver.formatScenarioDisplayName("Ns.Class.MyTestMethod"))
            .isEqualTo("My test method");
    }

    @Test
    void formatScenarioDisplayNameAppendsParameters() {
        assertThat(ScenarioTitleResolver.formatScenarioDisplayName("Ns.Class.MyTestMethod(p: \"v\")"))
            .isEqualTo("My test method [p: \"v\"]");
    }

    @Test
    void formatScenarioDisplayNameWithoutParensOrDots() {
        assertThat(ScenarioTitleResolver.formatScenarioDisplayName("PlaceOrder"))
            .isEqualTo("Place order");
    }

    @Test
    void formatScenarioDisplayNameTruncatesLongParameters() {
        String longParam = "x".repeat(250);
        String result = ScenarioTitleResolver.formatScenarioDisplayName("Method(" + longParam + ")");
        assertThat(result).endsWith("…]");
        assertThat(result).hasSize("Method ".length() - 1 + " [".length() + 200 + "…]".length());
    }

    // --- formatFeatureName ---

    @Test
    void formatFeatureNameTitleizesClassName() {
        assertThat(ScenarioTitleResolver.formatFeatureName("CustomerCheckoutTests"))
            .isEqualTo("Customer Checkout Tests");
    }

    // --- appendTestParameters ---

    @Test
    void appendTestParametersAddsBracketedParams() {
        assertThat(ScenarioTitleResolver.appendTestParameters("My scenario", "Method(a: 1, b: 2)"))
            .isEqualTo("My scenario [a: 1, b: 2]");
    }

    @Test
    void appendTestParametersReturnsTitleWhenNoParens() {
        assertThat(ScenarioTitleResolver.appendTestParameters("My scenario", "Method"))
            .isEqualTo("My scenario");
    }

    @Test
    void appendTestParametersReturnsTitleWhenEmptyParams() {
        assertThat(ScenarioTitleResolver.appendTestParameters("My scenario", "Method()"))
            .isEqualTo("My scenario");
    }

    @Test
    void appendTestParametersReturnsTitleWhenDisplayNameNull() {
        assertThat(ScenarioTitleResolver.appendTestParameters("My scenario", null))
            .isEqualTo("My scenario");
    }

    @Test
    void appendTestParametersTruncatesLongParams() {
        String longParam = "y".repeat(250);
        String result = ScenarioTitleResolver.appendTestParameters("T", "M(" + longParam + ")");
        assertThat(result).isEqualTo("T [" + "y".repeat(200) + "…]");
    }

    // --- resolveScenarioTitle ---

    @Test
    void resolveScenarioTitleHumanizesWhenTitleEqualsClassName() {
        // BDDfy(nameof(ClassName)) leaves the scenario title equal to the class name → humanize the method.
        assertThat(ScenarioTitleResolver.resolveScenarioTitle("CheckoutTests", "CheckoutTests", "PlaceOrder"))
            .isEqualTo("Place order");
    }

    @Test
    void resolveScenarioTitleLeavesCustomTitleUnchanged() {
        assertThat(ScenarioTitleResolver.resolveScenarioTitle("My custom title", "CheckoutTests", "PlaceOrder"))
            .isEqualTo("My custom title");
    }

    @Test
    void resolveScenarioTitleReturnsTitleWhenClassOrMethodNull() {
        assertThat(ScenarioTitleResolver.resolveScenarioTitle("T", null, "M")).isEqualTo("T");
        assertThat(ScenarioTitleResolver.resolveScenarioTitle("T", "C", null)).isEqualTo("T");
    }

    @Test
    void resolveScenarioTitleHandlesUnderscoresAndCollapsesSpaces() {
        assertThat(ScenarioTitleResolver.resolveScenarioTitle("C", "C", "Place_Order_Successfully"))
            .isEqualTo("Place order successfully");
    }
}
