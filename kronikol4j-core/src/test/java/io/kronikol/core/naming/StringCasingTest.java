package io.kronikol.core.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies the {@link StringCasing#titleize} port matches Humanizer's {@code Titleize()} behaviour. */
class StringCasingTest {

    @Test
    void titleizesPascalCase() {
        assertThat(StringCasing.titleize("PlaceOrder")).isEqualTo("Place Order");
        assertThat(StringCasing.titleize("CustomerCheckoutTests")).isEqualTo("Customer Checkout Tests");
    }

    @Test
    void titleizesUnderscoreAndDashSeparated() {
        assertThat(StringCasing.titleize("place_order")).isEqualTo("Place Order");
        assertThat(StringCasing.titleize("place-order")).isEqualTo("Place Order");
    }

    @Test
    void preservesAcronyms() {
        // An embedded all-caps run longer than one letter is kept as an acronym.
        assertThat(StringCasing.titleize("HTTPClient")).isEqualTo("HTTP Client");
    }

    @Test
    void allUpperInputIsReturnedUnchanged() {
        // Humanize() short-circuits all-upper input, then ToTitleCase leaves the acronym intact.
        assertThat(StringCasing.titleize("API")).isEqualTo("API");
    }

    @Test
    void emptyInputIsReturnedUnchanged() {
        assertThat(StringCasing.titleize("")).isEqualTo("");
    }
}
