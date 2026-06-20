package io.kronikol.core.constants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DependencyCategoriesTest {

    @Test
    void stringValuesMatchDotNetForParity() {
        // These exact spellings are what render in diagrams and feed DependencyPalette,
        // so they must match the .NET values byte-for-byte.
        assertThat(DependencyCategories.SQL).isEqualTo("SQL");
        assertThat(DependencyCategories.COSMOS_DB).isEqualTo("CosmosDB");
        assertThat(DependencyCategories.REDIS).isEqualTo("Redis");
        assertThat(DependencyCategories.MESSAGE_QUEUE).isEqualTo("MessageQueue");
        assertThat(DependencyCategories.GRPC).isEqualTo("gRPC");
        assertThat(DependencyCategories.MEDIATR).isEqualTo("MediatR");
    }
}
