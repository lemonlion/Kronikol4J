# Changelog

All notable changes to Kronikol4J are documented here. Versions follow SemVer.

## [0.1.5] — unreleased

Wires the diagram colour modes through the report-generation API end-to-end. Full suite green on
JDK 17–25.

### Added
- **`ReportOptions`** (`arrowColors`, `participantColors`) — immutable, with `defaults()`, `with…`
  builders, and `fromSystemProperties()`. Threaded through `HtmlReportGenerator.generate(…)`,
  `ReportFragments.fromRun(…)` (fork path), and `ReportFinalizer` (standalone + fragment).
- **Zero-touch enablement**: `ReportFinalizer.finalizeRunToDefault(…)` (used by the JUnit 5 / TestNG
  listeners) reads `-Dkronikol.diagram.arrowColors=true` / `-Dkronikol.diagram.participantColors=true`,
  so a run can colour its diagrams without any code change. Explicit overloads taking `ReportOptions`
  are available for programmatic callers.

The default remains uncoloured (back-compatible); colours are opt-in.

## [0.1.4] — unreleased

Adds the participant-colour rendering branch and pins it with parity. Full suite green on JDK 17–25.

### Added
- **Participant-colour mode** — `PlantUmlCreator.create(logs, arrowColors, participantColors)` appends
  each categorised participant's dependency colour to its declaration (e.g.
  `entity "OrderService" as orderService #438DD5`), mirroring .NET's
  `sequenceDiagramParticipantColors`. The un-categorised caller actor and any null-category service get
  no colour suffix — matching .NET exactly. Closes the last unported `PlantUmlCreator` rendering branch.

### Parity (testing)
- Corpus expanded 11 → 13 scenarios: `participant-colors` (isolated: coloured entity, uncoloured
  actor) and `participant-colors-fanout` (combined arrow + participant colours across
  entity/database/collections), both captured byte-for-byte from real .NET.
- Added a `PlantUmlCreatorTest` case for the colour guard: a null-category service stays uncoloured
  even in participant-colour mode (an edge no fixture exercises).

## [0.1.3] — unreleased

Strengthens the cross-runtime PlantUML parity guarantee and fixes a status-label divergence it
surfaced. Full suite green on JDK 17–25.

### Fixed
- **HTTP 302 status label** now renders `Found (Redirect)` (was `Redirect`), matching .NET's
  `HttpStatusCode.ToString()` + the reader-friendly redirect suffix. Found by the expanded parity
  corpus.

### Parity (testing)
- **Corpus expanded 5 → 11 scenarios**, each captured byte-for-byte from the real .NET
  `PlantUmlCreator` (`parity-harness/dotnet-capture`):
  - every dependency-type shape + arrow colour — `redis` (cache → `collections`, `#F39C12`),
    `storage` (S3 → `database`, `#2ECC71`), `unknown-category` (→ `participant`, `#95A5A6`);
  - `status-codes` — 404 → `Not Found`, 500 → `Internal Server Error`, 302 → `Found (Redirect)`;
  - `escaping` — backslash-doubling, no HTML-escaping inside notes, unicode, and nested-JSON
    pretty-printing;
  - `fan-out` — one test calling three services, pinning first-seen participant order.
- **Tightened the parity assertion**: it now normalises CRLF→LF and strips only trailing newlines, so
  a stray trailing space or extra blank line fails parity (previously `stripTrailing()` masked them).
- Added `HttpStatusNamesTest` covering titleization, the 302 disambiguation, and the unknown-code
  bare-number fallback.

## [0.1.2] — unreleased

Rounds out the roadmap extensions: a Spock test-framework adapter and two more database trackers.
**28 modules**, full suite green on JDK 17–25.

### Test frameworks
- **kronikol4j-spock** — a global Spock extension (`IGlobalExtension`, ServiceLoader-registered) that
  scopes each feature-iteration's identity and records its `Scenario`, and maps Spock blocks to
  phases (`given`/`setup`/`where` → Setup, `when`/`then`/`expect` → Action; `SpockBlock`). Verified
  by a **real Spock spec** that asserts the live identity scope and SETUP→ACTION phase transitions
  (the spec forces Groovy 4.0.28 so the Groovy compiler runs on the JDK 25 toolchain).

### Tracking adapters
- **kronikol4j-cassandra** — records Apache Cassandra CQL operations as tracked interactions
  (`Cassandra` category → `database` shape). A Java-native category (no .NET counterpart), added to
  `DependencyCategories` and the palette.
- **kronikol4j-elasticsearch** — records Elasticsearch / OpenSearch operations
  (`Elasticsearch` category → `database` shape; the category already mirrored .NET).

## [0.1.1] — unreleased

Closes the two roadmap gaps flagged in 0.1.0 (assertion Tier 2 and the golden-file parity harness)
and adds real-browser render verification. Full suite green on JDK 17–25.

### Byte-for-byte PlantUML parity (closes the golden-file gap)
- **Real cross-runtime parity harness** — a C# capture program (`parity-harness/dotnet-capture`)
  drives the *real* .NET `PlantUmlCreator` to emit golden `.puml` fixtures; `PlantUmlParityTest`
  builds the identical corpus in Java and asserts byte-for-byte equality (only the trailing newline
  normalised). **5 scenarios**: simple HTTP, colored arrows, multi-trace, SQL, and fire-and-forget
  events.
- Aligned the Java generator to .NET exactly: `@startuml`/`!pragma teoz`/event-`<style>` prefix,
  the `autonumber`/`skinparam wrapWidth` preamble, arrow + label formatting, note openers
  (`note left`/`note right`, `note<<eventNote>>`), and camelCase-sanitised aliases.
- **Fixed a participant-shape bug** caught by parity: a null-category response log was clobbering the
  request's dependency category, so services rendered as `participant` instead of `entity`/`database`.
  Now the first non-null category per service wins. `DependencyPalette` rewritten to the .NET
  two-level category→type→shape+color model (null category → `entity`).

### Assertion Tier 2 — automatic capture (closes the Tier 2 gap)
- **Tier 2a (source expression)** — `Track.that(Runnable)` / `Track.record(...)` capture the
  assertion's source text by reading the caller's `.java` line via `StackWalker`
  (`SourceExpression.forCallerOutsidePackages`), the analog of .NET reading PDB sequence points.
- **Tier 2b (`kronikol4j-assertj-agent`)** — a **ByteBuddy agent** that instruments AssertJ's
  `AbstractAssert` hierarchy to auto-capture every assertion's **actual + expected values AND source
  expression** with **no wrapper and no `.as()`** — the full fidelity .NET gets from IL weaving.
  - Instruments `AbstractAssert` *and its `org.assertj.core.api` subtypes*, so primitive-specialised
    overloads (`AbstractIntegerAssert.isEqualTo(int)`, …) are covered, not just the `Object` form.
  - Records **exactly once per logical assertion** via call-depth tracking (outermost frame only),
    so a subclass override delegating to `super.isEqualTo` doesn't double-count.
  - Works on **JDK 25**: sets `net.bytebuddy.experimental` (in a static initializer and the test JVM
    args) so ByteBuddy 1.15 can parse AssertJ types whose generics reference JDK 25 classes.
  - Usable as a `-javaagent` (declares `Premain-Class`/`Agent-Class`) or via self-attach `install()`.

### Browser rendering + offline reports
- **Client-side PlantUML-WASM rendering** wired into `HtmlReportGenerator`: diagram sources go into a
  `#kronikol-diagrams` JSON map and the bundled `kronikol-render.js` loads the WASM library
  (`plantumlLoad([], …)` → `plantuml.render(lines, id)`) to paint each `.plantuml-browser` element.
- **Self-contained / offline reports** — `kronikol.report.assetBase` system property repoints the
  WASM assets at a local directory (default: the CDN), so a report can render with no network.
- **Playwright pixel verification** (`playwright/`) — a real headless-Chromium test loads a generated
  report from `file://` and asserts the WASM pipeline actually injects an `<svg>` with real
  dimensions, text glyphs, and the tracked participant — fully offline, no request mocking. Fed by
  the `:kronikol4j-report:generatePlaywrightFixture` task (`OfflineReportFixture`).

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
