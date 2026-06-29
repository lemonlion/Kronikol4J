package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for MongoDB: drives the <em>real {@link KronikolMongoCommandListener}</em>
 * against a live Mongo (Testcontainers) and diffs the emitted {@link RequestResponseLog}s against the captured
 * output of the <em>real .NET MongoDbTrackingSubscriber</em> driven against a live Mongo (fixture
 * {@code mongo-interactions.txt} from {@code parity-harness/dotnet-capture}, env {@code KRON_MONGO_E2E=1}).
 *
 * <p><strong>Result:</strong> the operation classification is byte-identical cross-runtime — {@code type}, the
 * {@code Insert → / Find ← / Update → / Delete →} method label, the {@code mongodb:///test/orders} URI and the
 * {@code status} match exactly on every line, and the metadata response content ({@code n=1},
 * {@code n=1, nModified=1}) matches too. The only divergence is the BSON→JSON <em>dialect</em> used for the
 * {@code find} filter and document preview: the .NET {@code MongoDB.Bson} writer emits shell-style spacing
 * ({@code &#123; "_id" : 1 &#125;}) while the Java {@code org.bson} writer emits compact relaxed JSON
 * ({@code &#123;"_id": 1&#125;}). That is an inherent third-party driver serialization convention (not a
 * Kronikol bug) — pinned below, not hidden.
 */
class MongoInteractionParityTest {

    // Self-managed via Testcontainers by default; -Dkron.mongo.endpoint=host:port overrides it. Skips gracefully
    // when no Docker/Mongo is reachable, keeping the suite green on machines without a container engine.
    private static GenericContainer<?> container;
    private static MongoClient client;

    @BeforeAll
    static void connectMongo() {
        String endpoint = System.getProperty("kron.mongo.endpoint");
        if (endpoint == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "No Docker available and no -Dkron.mongo.endpoint set — skipping the live-Mongo parity test");
            container = new GenericContainer<>(DockerImageName.parse("mongo:7")).withExposedPorts(27017);
            container.start();
            endpoint = container.getHost() + ":" + container.getMappedPort(27017);
        }
        client = buildTrackedClient(endpoint);
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (client != null) {
            client.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static MongoClient buildTrackedClient(String endpoint) {
        var listener = new KronikolMongoCommandListener(MongoDbTrackingOptions.builder()
            .serviceName("OrdersDb").testInfoFetcher(() -> new io.kronikol.core.context.TestInfo("MyTest", "t1"))
            .build());
        var settings = com.mongodb.MongoClientSettings.builder()
            .applyConnectionString(new com.mongodb.ConnectionString("mongodb://" + endpoint))
            .addCommandListener(listener)
            .build();
        return MongoClients.create(settings);
    }

    @Test
    void javaMongoCaptureMatchesDotNetOnTheParityFields() throws IOException {
        MongoDatabase db = client.getDatabase("test");
        MongoCollection<BsonDocument> coll = db.getCollection("orders", BsonDocument.class);
        coll.deleteMany(new BsonDocument()); // deterministic starting state (tracked, then cleared)

        RequestResponseLogger.clear();
        coll.insertOne(new BsonDocument().append("_id", new BsonInt32(1)).append("name", new BsonString("a")));
        coll.find(new BsonDocument("_id", new BsonInt32(1))).into(new ArrayList<>()); // read (filter + doc preview)
        coll.updateOne(new BsonDocument("_id", new BsonInt32(1)),
            new BsonDocument("$set", new BsonDocument("name", new BsonString("b"))));
        coll.deleteOne(new BsonDocument("_id", new BsonInt32(1)));

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/mongo-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        // (1) type | method-label | uri | status are byte-identical cross-runtime on EVERY line — the core
        //     capture parity (operation+collection classification, directional-arrow label, mongodb:/// URI,
        //     OK status). Content is byte-identical too EXCEPT the two Find cells (filter + doc preview) — an
        //     inherent BSON-library JSON-dialect difference handled in (3).
        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[1] + ")";
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " method/label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
        }

        // (2) CONTENT is byte-identical on every non-Find line: the write request payloads are null and the
        //     write/insert response metadata strings (n=1, "n=1, nModified=1") are client-independent.
        for (int i = 0; i < golden.size(); i++) {
            if (!golden.get(i)[1].startsWith("Find")) {
                assertThat(actual.get(i)[3]).as("line " + i + " content (" + golden.get(i)[1] + ")")
                    .isEqualTo(golden.get(i)[3]);
            }
        }

        // (3) The only divergence: the Find filter + document-preview JSON dialect. .NET MongoDB.Bson writes
        //     shell-style spacing; Java org.bson writes compact relaxed JSON. Inherent third-party serialization
        //     convention (not a Kronikol bug) — pinned so a regression / accidental convergence is caught.
        assertThat(content(golden, "Find ← orders", "Request")).isEqualTo("{ \"_id\" : 1 }");
        assertThat(content(actual, "Find ← orders", "Request")).isEqualTo("{\"_id\": 1}");
        assertThat(content(golden, "Find ← orders", "Response"))
            .isEqualTo("[\\n  {\\n    \"_id\" : 1,\\n    \"name\" : \"a\"\\n  }\\n]");
        assertThat(content(actual, "Find ← orders", "Response"))
            .isEqualTo("[\\n  {\\n    \"_id\": 1,\\n    \"name\": \"a\"\\n  }\\n]");
    }

    private static String content(List<String[]> rows, String label, String type) {
        return rows.stream().filter(r -> r[1].equals(label) && r[0].equals(type)).findFirst().orElseThrow()[3];
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            // Mirror the harness projection: CRLF→LF then escape embedded newlines to a literal "\n" so the
            // multi-line document preview stays on one row. The Mongo URI carries no host, so no normalisation.
            String content = l.content() == null ? "~null~"
                : l.content().replace("\r\n", "\n").replace("\n", "\\n");
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                l.uri() == null ? "~null~" : l.uri().toString(),
                content,
                l.statusCode() == null ? "~null~" : statusText(l.statusCode())});
        }
        return out;
    }

    private static String statusText(StatusCode s) {
        return s instanceof StatusCode.Http h ? String.valueOf(h.code()) : ((StatusCode.Custom) s).value();
    }

    private static List<String[]> parse(String golden) {
        List<String[]> out = new ArrayList<>();
        for (String line : golden.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line.split("\\|", -1));
            }
        }
        return out;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = MongoInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
