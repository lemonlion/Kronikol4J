package io.kronikol.diagram.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void preservesKeyOrderAndStripsNullProperties() {
        String pretty = Json.tryPrettyPrint("{\"b\":1,\"a\":null,\"c\":\"x\"}");
        assertThat(pretty).isEqualTo("""
            {
              "b": 1,
              "c": "x"
            }""");
    }

    @Test
    void doesNotEscapeAngleBracketsAmpersandsOrNonAscii() {
        // UnsafeRelaxed semantics (§6.4): < > & + and non-ASCII pass through unescaped.
        String pretty = Json.tryPrettyPrint("{\"html\":\"<a>&é+</a>\"}");
        assertThat(pretty).isEqualTo("""
            {
              "html": "<a>&é+</a>"
            }""");
    }

    @Test
    void keepsNullArrayElements() {
        assertThat(Json.tryPrettyPrint("[1,null,2]")).isEqualTo("""
            [
              1,
              null,
              2
            ]""");
    }

    @Test
    void preservesNumberLiteralsVerbatim() {
        assertThat(Json.tryPrettyPrint("{\"a\":1.0,\"b\":2e3}")).isEqualTo("""
            {
              "a": 1.0,
              "b": 2e3
            }""");
    }

    @Test
    void returnsNullForNonJson() {
        assertThat(Json.tryPrettyPrint("not json")).isNull();
        assertThat(Json.tryPrettyPrint("{broken")).isNull();
        assertThat(Json.tryPrettyPrint("{} trailing")).isNull();
        assertThat(Json.tryPrettyPrint("")).isNull();
    }
}
