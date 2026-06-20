package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import io.kronikol.mongodb.MongoTracking.MongoTrackingOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class MongoTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsAMongoOperationAsADatabaseParticipant() {
        MongoTracking.record(MongoTrackingOptions.forDatabase("OrderStore"),
            "find", "orders", "{\"id\":42}", "1 document");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("FIND");
        assertThat(logs.get(0).content()).contains("orders");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"OrderStore\" as OrderStore")
            .contains("Test -> OrderStore : FIND /");
    }
}
