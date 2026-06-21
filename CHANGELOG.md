# Changelog

All notable changes to Kronikol4J are documented here. Versions follow SemVer.

## [0.1.14] — unreleased

Adds the **internal-flow / whole-test-flow** report views — the activity diagrams and flame
charts generated from captured spans — reaching byte-for-byte parity with the .NET renderer for
the report-rendering side.

### Added — proven byte-parity for
- **Activity diagrams** (`io.kronikol.report.flow.InternalFlowRenderer.renderActivityDiagram`) — a
  PlantUML swimlane-per-source activity diagram from a runtime-neutral span tree
  (`InternalFlowSpan`/`InternalFlowSegment`), including the >100-span batching. The PlantUML keeps
  native CRLF line endings (it is gzipped into `puml-data` before the report's
  `ReplaceLineEndings`), so the decoded payload matches verbatim.
- **Flame-chart data** (`getFlameChartData`/`getFlameChartDataWithMarkers`) — the compact
  `{s, f, m}` JSON serialized byte-identically to **System.Text.Json**: integers without a decimal
  point, doubles as shortest-decimal (trailing zeros stripped), `Math.Round(x, 2)` banker's
  rounding, and the default HTML-safe string escaping (`" & ' + < > \`` → upper-case `\uXXXX`).
- **Whole-test-flow report wiring** — `WholeTestFlowVisualization` (None/FlameChart/ActivityDiagram/
  Both) + `WholeTestFlowInput` + a `render(…)` overload. The multi-view **Diagrams** toolbar
  (Sequence/Activity/Flame toggle buttons) and the `diagram-view-seq/activity/flame` wrappers are
  wired into both individual scenarios and parameterized groups (per-example vs the shared
  "identical across test cases" view), and the `internalFlowTracking`-gated head scripts are emitted.

### Notes
- The flame chart's gzipped payload is an inline `data-flame-z` body attribute (not the `puml-data`
  island); like `puml-data`, gzip is not byte-stable across runtimes, so the parity suite masks it
  for the byte-compare and asserts decoded (gunzip) equality.
- This release covers the whole-test-flow **rendering**; producing the segments from captured OTel
  spans (the .NET `InternalFlowSegmentBuilder`/`InternalFlowSpanStore` capture side) is the
  runtime-integration follow-on.

## [0.1.13] — unreleased

Completes **Phase 1 (rich reporting)**: the byte-for-byte HTML report parity now covers the
rich step, parameterized, and customization feature surface — each proven against a dedicated
golden captured from the real .NET `ReportGenerator`. This supersedes the v0.1.12 "out of scope"
note: R2 flattened-object detection, the parameter flatten-toggle, complex-object (R3/R4) cells,
display-name-prefix grouping, and inline/tabular/tree step parameters are now **implemented**
(via their deterministic string-based paths — see Notes). Full suite green; Playwright passes.

### Added — proven byte-parity for
- **Rich steps** — step comments + doc-strings (`<pre><code class="language-…">`); inline,
  tabular (`step-param-table`) and tree (`step-param-tree`) step parameters via `RenderParameter`;
  the combined setup+assertion tabular table (key-aligned); structured `TextSegments` with inline
  parameter spans and table references (small complex → inline summary, large → expandable button).
- **String-based parameter rendering** — `ParameterParser` (record `ToString()` parsing,
  `parse`/`extractBaseName` display-name parsing) and `ParameterValueRenderer` (R3 `cell-subtable`
  / R4 `param-expand` with highlighted JSON, nested-record recursion, `List<T>` type-name cleanup).
- **Rich parameterized groups** — R2 string-based flattened-object grouping (`FlattenedObject`),
  the `ExampleFlatValues` flatten toggle (`param-table-wrapper` / `param-table-flat` / grouped),
  display-name-prefix grouping for non-`OutlineId` scenarios, and per-example vs shared sequence
  diagrams (`AllDiagramsIdentical` badge), with the model expanded (`exampleFlatValues`, rich
  `ScenarioStep` parameters) without disturbing the report-data serializers.
- **Customization** — CI metadata block (`ci-chart-group` + provider/build/branch/commit/pipeline/
  repository table), custom CSS / favicon / logo, `showStepNumbers` (incl. nested `1.1.` sub-steps),
  `generateBlankOnFailedTests`, and the assertion-note / step-delimiter / database diagram-toolbar
  toggles — all via a new `HtmlCustomization` carrier and a `render(…)` overload.

### Notes
- **The only deliberate exclusion remains server-side PlantUML image rendering** (the Node.js /
  inline-SVG `ImgSrc` pre-render path); diagrams render in-browser from the gzip `puml-data` island.
- The .NET **reflection-based** object rendering over a live `ExampleRawValues` object graph is
  runtime-specific (PascalCase property names, .NET type names) and is *not* cross-runtime
  byte-parity-able — analogous to the gzip `puml-data` boundary — so only the deterministic
  **string-based** paths (operating on the `ExampleValues`/`InlineValue` strings) are ported.
  Documented at the call sites.

## [0.1.12] — unreleased

Extends the byte-for-byte HTML report parity (v0.1.11) across the report's richer
features, each proven against a dedicated golden fixture captured from the real .NET
`ReportGenerator` (the no-diagram goldens are byte-identical end to end). Full suite green
on JDK 17–25; the Playwright render check still passes.

### Added — proven byte-parity for
- **BDD steps + background steps** — the `scenario-steps`/`scenario-background` `<details>`,
  `RenderStep` (status icon/tooltip, keyword, duration, nested sub-steps).
- **Skipped + bypassed scenarios** — `data-status`, summary classes, tooltips, timeline bars.
- **Attachments** — scenario-level (`target="_blank"`) and step-level (lightbox) image/link forms.
- **Rule grouping** — consecutive same-`Rule` scenarios under `<details class="rule">`.
- **`ErrorDiffParser`** — the expected/actual character-level LCS diff in the failure-result block
  (xUnit / NUnit / FluentAssertions / Shouldly message shapes).
- **Parameterized groups** — OutlineId-grouped `ScalarColumns` parameter table + per-example
  detail panels (new `Humanize` = Titleize/FormatScenarioDisplayName port, and `ParameterGrouper`).
- **`includeTestRunData`** — the Features Summary table (conditional Steps/Duration columns), the
  Test Execution Summary and the status pie-chart SVG; a `render(…)` overload adds the
  `includeTestRunData` + start/end-time inputs (production stays `false`).

### Notes
- Out of scope (not representable in the Java model, which has no `ExampleRawValues`/
  `ExampleFlatValues` or rich step parameters): R2 flattened-object detection, the parameter
  flatten-toggle, complex-object (R3/R4) cells, display-name-prefix grouping, and inline/tabular
  step parameters + doc-strings. Documented at the call sites.

## [0.1.11] — unreleased

Completes the **browser-only HTML report port**: `TestRunReport.html` is now a byte-for-byte
reproduction of the .NET Kronikol report (browser-rendering path), proven against captured golden
files. Full suite green on JDK 17–25; the Playwright render check passes on Chromium.

### Added
- **`DotNetHtmlReportRenderer`** — assembles the report byte-identically to .NET's
  `ReportGenerator.GenerateHtmlReport`: the head is built from the shared embedded assets interleaved
  with the literal template + interpolations (reproducing C# raw-string-literal semantics exactly,
  including the pure-whitespace lines empty holes emit); the body ports the filtering box, toolbar,
  scenario timeline, per-feature/scenario `<details>`, failure-result block, run-level component
  diagram and the gzip+base64 `puml-data` island.
- **`GoldenHtmlParityTest`** — asserts byte-identity against two golden fixtures (a simple passed
  scenario, and a rich one with a component diagram + a failure). The gzip diagram payload is not
  byte-stable across runtimes (the harness's gzip differs from the JVM's); it is asserted by
  *decoded* equality. Independent `diff`: only the `puml-data` line differs, and it decodes equal.

### Changed
- **The report is now the .NET-parity layout.** `HtmlReportGenerator.renderHtml`/`generate`/
  `generateFromDiagrams` (and thus the runtime finalizer, JUnit 5 / TestNG listeners, CLI and merge
  path) emit the new HTML; the legacy inline-PlantUML renderer is removed. The
  `kronikol.report.assetBase` offline override is preserved (it rewrites the baked-in CDN base).
- The 23 CRLF report asset scripts are normalised to LF so the embedded bytes match the golden.

## [0.1.10] — unreleased

Wires the report-data serializers into the run: a standalone report now emits the machine-readable
`TestRunReport.{json,xml,yaml}` files alongside `TestRunReport.html`. Full suite green on JDK 17–25.

### Added
- **Report-data emission** — `ReportFinalizer` writes `TestRunReport.<ext>` for each requested
  {@code ReportDataFormat}, built from the run's features + tracked logs (per-test diagrams +
  httpInteractions). Opt in via `ReportOptions.withDataFormats(…)` or, zero-touch through the
  JUnit 5 / TestNG listeners, `-Dkronikol.report.dataFormats=xml,yaml`.
- Run timing is captured in `RunResults` (start = first recorded scenario); the `KronikolVersion`
  comes from the jar manifest (`"unknown"` in dev, matching .NET's fallback). `ReportOptions` gains a
  `dataFormats` set (back-compatible — the two-arg colour shape is unchanged).

The HTML report (`TestRunReport.html`) is still always emitted; the data files are opt-in.

## [0.1.9] — unreleased

Ports the full report-data model and adds byte-for-byte JSON/XML/YAML report-data serializers
(closing the YAML/XML output gap). Full suite green on JDK 17–25.

### Added
- **Full report model** — `Feature` gains `endpoint`/`description`/`labels`; `Scenario` gains
  `isHappyPath`, `errorStackTrace`, `labels`, `categories`, `rule`, `outlineId`, `exampleValues`,
  `exampleDisplayName`, `attachments`, `backgroundSteps`, `steps` (with a fluent builder); new
  `ScenarioStep`, `FileAttachment`, and `ScenarioStableId` (SHA-256 → first 16 hex, lower-cased).
  Existing call sites are unchanged — the previous shapes remain as convenience constructors.
- **`ReportDataSerializer`** — serializes the test-run report data to **JSON, XML, and YAML**,
  aligned **byte-for-byte** with the .NET `GenerateTestRunReportData`: System.Text.Json indentation
  and default-encoder escaping (`"`/`&`/`<`…), `XDocument` formatting, the hand-built
  YAML with `SanitiseForYml`, F3 vs raw-double durations, per-format field orders, and conditional
  inclusion — including steps, attachments, diagrams, and httpInteractions. Verified by
  `ReportDataParityTest` against fixtures captured from real .NET.

The serializers are public building blocks; wiring them into the report run (emitting
`TestRunReport.{json,xml,yaml}` with run timing + version) is the next integration step.

## [0.1.8] — unreleased

Embeds the component diagram in the HTML report and fixes multi-diagram rendering. Full suite green
on JDK 17–25.

### Added
- **Component diagram in the report** — `HtmlReportGenerator.generate(logs, …)` now renders the
  run-level component diagram as a section at the top of the report (browser-rendered, with a
  collapsible PlantUML source block), computed from all tracked logs. The standalone/finalize path
  (`ReportFinalizer`) picks it up automatically. The merge path is unchanged for now (no component
  diagram). Verified end-to-end: the Playwright pixel test now asserts **both** the sequence and the
  component `<svg>` paint, offline.

### Fixed
- **Multi-diagram rendering** — `kronikol-render.js` rendered all `.plantuml-browser` elements in a
  tight loop, but PlantUML-WASM (TeaVM) renders asynchronously off shared global state, so concurrent
  `render()` calls clobbered each other and a report with 2+ diagrams kept only one. Renders are now
  serialized (render → await the injected `<svg>` → next). This also fixes reports with multiple
  per-test sequence diagrams, not just the new component section.

## [0.1.7] — unreleased

Adds the **component diagram** — the second diagram type (closing a Phase-2 gap). Full suite green
on JDK 17–25.

### Added
- **`io.kronikol.diagram.component.ComponentDiagramGenerator`** — the run-level view of which
  participants call which services, aggregated across all tests. `extractRelationships(logs)` groups
  requests by `(caller, service, protocol)` with per-relationship call/test counts and method sets;
  `generatePlantUml(relationships)` emits the .NET browser (non-C4) component diagram — the
  skinparam preamble, `rectangle`/`database`/`collections`/`queue` shapes (a person for pure
  callers, a "Software System" for HTTP/unknown services), and `-[#colour]->` arrows labelled
  `"PROTO: methods - N calls across M tests"`.
- Captured byte-for-byte from real .NET (`parity-harness` `component` scenario);
  `PlantUmlParityTest.componentDiagram` asserts exact equality, plus `ComponentDiagramGeneratorTest`
  for the aggregation maths and per-type shapes.

### Parity hardening (.NET side)
- The .NET `ComponentDiagramGenerator` participant order was non-deterministic (`HashSet` iteration,
  JAVA_PORT_PLAN §6.5 HIGH hazard). Pinned to deterministic first-seen order (callers then services)
  — behaviour-preserving (the order it already emitted) — so the golden fixture is reproducible.

> Generator-level for now; embedding the component diagram into the HTML report is a follow-up
> (as participant colours were generator-first, then wired through `ReportOptions`).

## [0.1.6] — unreleased

Aligns the diagram-rendering defaults with .NET across the board. Full suite green on JDK 17–25.

### Changed (behaviour)
- **Diagrams are now coloured by default**, matching the .NET `PlantUmlCreator` defaults
  (`sequenceDiagramArrowColors = true`, `sequenceDiagramParticipantColors = false`): arrows are
  coloured per dependency type; participants stay uncoloured.
  - `PlantUmlCreator.create(logs)` now colours arrows (was uncoloured). Pass
    `create(logs, false)` for the previous behaviour.
  - `ReportOptions.defaults()` is now `(arrowColors=true, participantColors=false)`, and
    `fromSystemProperties()` falls back to those — so reports keep the .NET look out of the box and
    opt out with `-Dkronikol.diagram.arrowColors=false`.
- Test suites across the diagram, adapter, report, and runtime modules were updated to assert the
  coloured-by-default output; the byte-for-byte parity fixtures are unchanged (the uncoloured
  scenarios now pass `arrowColors=false` explicitly, matching how those fixtures were captured).

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
