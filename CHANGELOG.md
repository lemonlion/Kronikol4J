# Changelog

All notable changes to Kronikol4J are documented here. Versions follow SemVer.

## [0.1.0] — unreleased

The initial Java-native implementation, built core-first per [docs/PORT_PLAN.md](docs/PORT_PLAN.md).
**24 modules, ~101 tests, all green** on JDK 17–25.

### Core pipeline
- **kronikol4j-core** — the stable ingestion seam: `RequestResponseLog` + builder, the static
  `RequestResponseLogger`, sealed `Method`/`StatusCode` types, the 4-layer context/identity system
  (`TestIdentityScope`, `TestInfoResolver`, `TestPhaseContext`, `PhaseConfiguration`), the
  parallel-safe `TestCorrelationStore` + correlation decorators, the component registry, the
  `IdGenerator` determinism seam, assertion Tier 0 (`Track.that`), and `Interactions.recordPair`.
  Zero runtime dependencies.
- **kronikol4j-diagram** — `PlantUmlCreator` (sequence diagrams, one-per-test client-side splitting),
  `PlantUmlTextEncoder` (round-trip-verified), `DependencyPalette`, `HttpStatusNames`, and a
  hand-rolled canonical JSON note formatter (parity-controlled per the plan §6.4).
- **kronikol4j-report** — `HtmlReportGenerator` (browser-render HTML), the `WebUtility.HtmlEncode`
  parity shim, and the merge engine (`ReportFragment`, `FragmentJson`, `MergeableReportMerger`,
  `MergeableReportRenderer`).
- **kronikol4j-runtime** — `RunResults`, `ReportFinalizer` (standalone HTML vs forked-fragment
  emission with atomic writes), `ReportFragments`.
- **kronikol4j-cli** — the `merge` command (the "Merging Parallel Reports" feature).
- **kronikol4j-gradle-plugin** — wires `kronikol.run.dir` onto test tasks and registers a
  `kronikolReport` merge task (build-level fork aggregation).

### Test frameworks
- **kronikol4j-junit5** — `KronikolExtension` + ServiceLoader `LauncherSessionListener`.
- **kronikol4j-testng** — `ITestListener`/`IExecutionListener`, ServiceLoader-registered.
- **kronikol4j-cucumber** — `EventListener` with Given/When/Then → Setup/Action phase mapping.

### Tracking adapters
- **kronikol4j-proxy** — `TrackingProxy` (the DispatchProxy pattern).
- **kronikol4j-http** — `HttpExchangeRecorder` (LogPair pattern).
- **kronikol4j-spring** — RestTemplate `ClientHttpRequestInterceptor` (buffers the response).
- **kronikol4j-spring-boot-starter** — auto-config + `@ConfigurationProperties`.
- **kronikol4j-servlet** — Layer-1 identity filter (jakarta).
- **kronikol4j-jdbc** — `SqlTracking` (direct-log pattern; one tracker for all relational DBs).
- **kronikol4j-redis**, **-mongodb**, **-messaging** — cache / NoSQL / Kafka-JMS event tracking.
- **kronikol4j-grpc** — `GrpcTracking` + `ClientInterceptor`.
- **kronikol4j-aws**, **-azure**, **-gcp** — cloud SDK recorders (S3/DynamoDB/SQS/SNS; Cosmos/Blob/
  Service Bus; BigQuery/Pub-Sub/Storage).

### Cross-cutting
- **kronikol4j-assertj** — assertion Tier 1 (zero-weave: soft-assertion capture + description consumer).
- **kronikol4j-opentelemetry** — `OtelBridge` stamps span/trace ids onto logs.

### Infrastructure
- Gradle 9.6 multi-module build, `build-logic` convention plugin (Java 17 via `--release`,
  `Automatic-Module-Name`, Maven Central publishing with gated signing + javadoc/sources jars).
- GitHub Actions CI (matrix JDK 17/21/25 + publish-on-tag).
- A documentation wiki (`../Kronikol4J.wiki`).

### Known gaps (roadmap)
- Assertion Tier 2 (automatic expression text + variable capture) — needs a compile-time AST plugin
  or ByteBuddy agent.
- The golden-file parity harness — needs the determinism/asset-externalization instrumentation added
  to the upstream .NET Kronikol first (plan §6.2, §4.2).
- Spock adapter; additional DB drivers (Cassandra, Elasticsearch).
