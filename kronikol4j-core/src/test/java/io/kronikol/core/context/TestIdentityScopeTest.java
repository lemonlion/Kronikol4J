package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestIdentityScopeTest {

    @AfterEach
    void cleanup() {
        TestIdentityScope.clear();
        TestIdentityScope.clearGlobalFallback();
    }

    @Test
    void beginSetsCurrentAndCloseRemovesWhenNoPrevious() {
        assertThat(TestIdentityScope.current()).isNull();
        try (var scope = TestIdentityScope.begin("MyTest", "id-1")) {
            assertThat(TestIdentityScope.current()).isEqualTo(new TestInfo("MyTest", "id-1"));
        }
        // mandatory clearing: closing restores "no identity"
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void nestedScopesRestoreTheOuterIdentity() {
        try (var outer = TestIdentityScope.begin("Outer", "o")) {
            try (var inner = TestIdentityScope.begin("Inner", "i")) {
                assertThat(TestIdentityScope.current().name()).isEqualTo("Inner");
            }
            assertThat(TestIdentityScope.current().name()).isEqualTo("Outer");
        }
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void setFromMessageSetsIdentityWithoutScope() {
        TestIdentityScope.setFromMessage("Consumer", "c-1");
        assertThat(TestIdentityScope.current()).isEqualTo(new TestInfo("Consumer", "c-1"));
        TestIdentityScope.clear();
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void globalFallbackIsIndependentOfScope() {
        TestIdentityScope.setGlobalFallback("Fallback", "f-1");
        assertThat(TestIdentityScope.globalFallback()).isEqualTo(new TestInfo("Fallback", "f-1"));
        assertThat(TestIdentityScope.current()).isNull();
    }
}
