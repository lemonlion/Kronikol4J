package io.kronikol.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.cassandra.CassandraTracking.CassandraTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class CassandraTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsACqlOperationAsADatabaseParticipant() {
        CassandraTracking.record(CassandraTrackingOptions.forKeyspace("OrderStore"),
            "select", "shop.orders", "SELECT * FROM shop.orders WHERE id = 42", "1 row");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("SELECT");
        assertThat(logs.get(0).content()).contains("shop.orders");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"OrderStore\" as orderStore")
            .contains("test -[#E74C3C]> orderStore: SELECT: /");
    }
}
