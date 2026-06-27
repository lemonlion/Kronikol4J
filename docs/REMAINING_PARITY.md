# Kronikol4J — Remaining Parity Roadmap

**Purpose.** A prioritized, checklist-style breakdown of what is left to reach the stated goal:
**Kronikol4J as a fully ported, usable port of Kronikol with every single feature / full functional
parity — minus only server-side PlantUML image rendering.**

**How to read this.** This is the companion to [PORT_PLAN.md](PORT_PLAN.md) — the plan describes *intent
and architecture*; this file is the *live gap list* and work queue. Items derive from a file/method-level
audit of `c:\Code\Kronikol\src\Kronikol` (the .NET source of truth) against the Java modules on
**2026-06-27**. `file:line` citations are point-in-time — re-verify against current code when you pick up an
item. Follow the repo rules: TDD (red→green→refactor), golden-prove every behavior from the real .NET via
`parity-harness/dotnet-capture` where output is observable, keep the full suite + Playwright green, version-
bump + CHANGELOG + wiki at milestones.

**Legend.** `[ ]` not started · `[~]` partial · `[x]` done · **MISSING** no Java code · **PARTIAL** exists
but incomplete.

---

## §0 — Cold-start resume guide (read this first)

*Written so a fresh session with zero prior context can resume from this file alone. Everything below was
verified against the working tree on 2026-06-27; re-confirm paths if the tree has moved.*

### Repos & key locations

| What | Where |
|---|---|
| **Java port** (you work here) | `c:\Code\Kronikol4J` — git branch `main`, origin `github.com/lemonlion/Kronikol4J` (push authorized) |
| **.NET source of truth** (behavioral spec — read, don't translate line-by-line) | `c:\Code\Kronikol\src\Kronikol` |
| **Wiki** | `c:\Code\Kronikol4J.wiki` → `github.com/lemonlion/Kronikol4J.wiki.git` (branch `master`) |
| **Parity harness** (drives REAL .NET, emits goldens) | `parity-harness/dotnet-capture/` (`Capture.csproj`, `Program.cs`) |
| **Golden fixtures** (committed) | `kronikol4j-report/src/test/resources/parity/`, `kronikol4j-diagram/src/test/resources/parity/` |
| **Golden test** (asserts byte-parity) | `kronikol4j-report/src/test/java/io/kronikol/report/GoldenHtmlParityTest.java` |
| **Playwright E2E** | `playwright/` (`playwright.config.js`, `tests/`) |

The .NET version pinned into the current goldens is **Kronikol v3.0.43** (see `PINNED_VERSION` in
`GoldenHtmlParityTest`). If you re-capture against a newer .NET, that constant + the goldens move together.

### How the system fits together — the seams you'll actually touch

Three boundaries (full detail in [PORT_PLAN.md](PORT_PLAN.md) §1; condensed here):

- **Seam A — ingestion (where capture work lands).** *Every* tracker, no matter what it instruments,
  ultimately builds one `RequestResponseLog` and calls **one sink**:
  `kronikol4j-core/src/main/java/io/kronikol/core/tracking/RequestResponseLogger.java` → `log(...)`. The data
  model + builder is `…/core/tracking/RequestResponseLog.java`. **An integration's whole job is: observe a
  dependency call → build a `RequestResponseLog` (request half + response half, sharing `traceId` +
  `requestResponseId`) → `log()` it.** Almost all of Tier 1/3 is "do this automatically from an SDK hook
  instead of making the user call a recorder by hand."
- **Seam B — context/identity.** Trackers resolve *which test* they belong to via
  `…/core/context/TestInfoResolver.java` (the fallback cascade), `TestIdentityScope.java` (ambient scope),
  and `TestCorrelationStore.java` (data-keyed correlation for background work). Phase (Setup vs Action) lives
  in `…/core/context/PhaseConfiguration.java`. Phase-aware suppression + verbosity (cross-cutting infra
  below) plug in here.
- **Seam C — output (DONE — you feed it, you don't rebuild it).** `RequestResponseLog[]` → PlantUML → HTML
  is byte-complete. You generally only touch it to add an *option* that changes rendering (Tier 2).
- **Dependency categories** (participant shapes/colors; add `CLICK_HOUSE`/`SPANNER`/`BIGTABLE` here):
  `…/core/constants/DependencyCategories.java`.

### Build / test / prove (the gate — verified commands)

```bash
# Full suite (the gate — must stay green):
c:\Code\Kronikol4J\gradlew.bat -p c:\Code\Kronikol4J test
# or the whole build incl. compile of every module:
./gradlew build

# Golden capture loop (when a feature's OUTPUT is observable in the report/diagram):
#  1. Add/extend a CaptureXxx(...) case in parity-harness/dotnet-capture/Program.cs (fixed GUIDs/times so
#     fixtures are reproducible — see the existing cases).
#  2. Capture from the REAL .NET:
dotnet run --project c:\Code\Kronikol4J\parity-harness\dotnet-capture -c Release
#     → fixtures are written under parity-harness/dotnet-capture/fixtures/
#  3. Copy the new fixture into the test resources (…-report or …-diagram)/src/test/resources/parity/.
#     Keep SCRATCH fixtures gitignored; only commit the golden you assert against.
#  4. Assert byte-equality from Java (GoldenHtmlParityTest for HTML, or a focused *.puml/json test for
#     diagram/data). gzip payloads (puml-data, data-flame-z) are asserted DECODED, not byte-raw (plan §6.4).

# Playwright UI check (offline; real render of the generated HTML):
./gradlew :kronikol4j-report:generatePlaywrightFixture
cd playwright && npx playwright test
```

Capture-side behavior is provable end-to-end: drive a real dependency from a Java test, capture the .NET
equivalent, assert the rendered report matches. For pure logic with no rendered output (e.g. an operation
classifier's `GET → Get` mapping), a plain TDD unit test is the proof; add a golden only if it changes
rendered bytes.

### How to know what's already done

1. Run the suite (above) — green = the current committed surface works.
2. Read `CHANGELOG.md` (newest entry = last milestone) and `git log --oneline -20`.
3. The report/diagram **output rendering is byte-complete** — do not re-audit it; treat any "add X to the
   report" as a possibly *new* .NET feature, not a missed gap.
4. This file's checkboxes are the work ledger. `[ ]` = open. Update them as you land items.

### How to pick & execute the next item

1. Pick the top unchecked item per **Suggested sequencing** (end of this file). Cross-cutting infra first.
2. Open the cited `.NET` file(s) under `c:\Code\Kronikol\src\Kronikol` — that is the behavioral spec. Read
   the actual logic (fields, branches, defaults), not just the class name.
3. Locate the Java target module/file (each item names the module; `kronikol4j-<area>/src/main/java/io/
   kronikol/<area>/…`). Confirm whether it's MISSING (new file) or PARTIAL (extend).
4. **TDD (repo rule, non-negotiable):** red → green → refactor. Failing test first — a golden when output is
   observable, else a unit test. Implement the minimum to pass. Add edge-case tests. Fix any bug you find
   along the way (repo rule) and add the missing coverage.
5. Keep the full suite + Playwright green. Update this file's checkbox, add a `CHANGELOG.md` entry, and a
   wiki page/section for any user-facing feature. Commit a green checkpoint per feature. Version-bump all
   packages + tag `v{x.y.z}` at milestones (see [CLAUDE.md](../CLAUDE.md) "Versioning & Release").

### Definition of done (per item)

- [ ] Behavior matches the cited .NET source (verified by reading it, not assumed from the name).
- [ ] Proven by a test: golden (byte-parity, captured from real .NET) where output is observable, else a
  unit test covering the mapping/branches + edge cases.
- [ ] Honors the cross-cutting contracts where relevant: verbosity levels, `TrackDuringSetup/Action` phase
  filtering, identity resolution via Seam B, the `RequestResponseLogger.log` sink.
- [ ] Full `gradlew test` + Playwright green.
- [ ] Checkbox ticked here · `CHANGELOG.md` updated · wiki updated (if user-facing) · green commit.

### Anatomy of a work item — worked example: "Redis operation classification" (Tier 1)

1. **Read the spec.** `.NET RedisOperationClassifier.cs` maps 25+ command names → typed operations
   (`GET → Get`, `SETEX → Set`, `HGET → HashGet`, …) and detects GET hit/miss from the response; the
   operation + endpoint/db/key feed the diagram URI + note.
2. **Find the Java target.** `kronikol4j-redis/src/main/java/io/kronikol/redis/RedisTracking.java` — today a
   bare recorder with a fixed `redis://cache/` URI and no classification (PARTIAL).
3. **Red.** Add `RedisOperationClassifierTest` asserting the command→operation table + hit/miss detection
   (pure logic → unit test). It fails (class doesn't exist).
4. **Green.** Add `RedisOperationClassifier`, wire it into `RedisTracking` so the `RequestResponseLog` it
   builds carries the classified operation + a `redis://<endpoint>/<db>?key=…` URI.
5. **Prove the rendered effect.** Because the operation now changes the diagram note/URI, capture a .NET
   golden of a report containing a Redis interaction (add a `CaptureXxx` case in `Program.cs`), copy it to
   `kronikol4j-report/src/test/resources/parity/`, and assert the Java report matches byte-for-byte.
6. **Edge cases + done.** Unknown command → fallback; hit vs miss; verbosity levels (depends on the
   cross-cutting verbosity infra — do that first). Tick the checkbox, CHANGELOG, wiki, commit.

---

## The reframe — what is already DONE vs what remains

**DONE (do not re-litigate):** the report/diagram **OUTPUT rendering**. Given a set of
`RequestResponseLog`s, the HTML + PlantUML Kronikol4J emits is **byte-for-byte identical** to .NET, golden-
proven across the full `PlantUmlCreator.Create` surface and every `ReportGenerator` input-conditional
branch — plus the merge engine, report-data JSON/XML/YAML + schema, CI markdown summary, diagnostic report,
and whole-test-flow/internal-flow *rendering*. Server-side PlantUML rendering is excluded by design.

**REMAINING (this document):** the **CAPTURE / instrumentation** side (the SDK adapters that actually hook
HTTP/SQL/Redis/Kafka/cloud and feed the logger automatically) and the **configuration surface** (the per-
tracker + report options that drive behavior). The rendering engine draws the picture; these supply and
shape the data. This is PORT_PLAN "Phase 5+ breadth" — additive work against a frozen, stable core.

---

## Cross-cutting infrastructure (build these first — every Tier-1 item depends on them)

These are shared mechanisms the .NET trackers all use. Building them once unblocks the per-integration work.

- [ ] **Verbosity framework** — a shared `TrackingVerbosity` enum (`Raw` / `Detailed` / `Summarised` /
  `HeadersOnly` / `None`) + the per-tracker resolution logic. .NET every tracker has it; Java has none.
- [ ] **Phase-aware tracking suppression** — wire `PhaseConfiguration.shouldTrack()` (already in
  `kronikol4j-core`) into every extension execution path, honoring `TrackDuringSetup` / `TrackDuringAction`
  + `SetupVerbosity` / `ActionVerbosity`. Currently no Java tracker consults phase at all.
- [ ] **Service-name resolution chain** — `PortsToServiceNames`, `ClientNamesToServiceNames` (with
  suffix/contains fallback for generated client names), `FixedNameForReceivingService`, `ExcludedHosts`.
  Used by HTTP + cloud adapters. (.NET `TestTrackingMessageHandler.cs:58-139`.)
- [ ] **Operation classifiers** — per-protocol command/operation classification (SQL, Redis, Mongo,
  Elasticsearch, gRPC, cloud). .NET has `UnifiedSqlClassifier`, `RedisOperationClassifier`, etc.; Java has
  keyword-only stubs.
- [ ] **`TrackingSafeSerializer` equivalent** — mock-proxy detection, `Future`/`CompletableFuture` result
  unwrapping, circular-ref handling, `MaxDepth`, `SkipTypes`. (.NET `Tracking/TrackingSafeSerializer.cs`.)
- [ ] **`CorrelationKeys` completion** — add the 6 missing key-format helpers: `cosmos(svc,partition,doc)`
  3-arg, `eventHubs`, `pubSub`, `sqs`, `sns`, `storageQueue`. (.NET `CorrelationKeys.cs`.)
- [ ] **`ProcessingCorrelation` async wrappers** — `CompletableFuture`/`Callable` handler wrapping (Java has
  only sync `Consumer` forms). (.NET `ProcessingCorrelation.cs:21`.)
- [ ] **`TestCorrelationStore` gaps** — `onResolveMiss` callback, `remove(key)`, `seed(...)`, public TTL.
- [ ] **Deferred flush** — `DeferredLogFlushHandler` + `PendingRequestResponseLogs`: queue logs emitted
  before identity is known, flush once it resolves. (.NET `Tracking/DeferredLogFlushHandler.cs`.)

---

## Tier 1 — Turn the existing "recorder" modules into real auto-capturing adapters

Today these expose `record(...)`/`publish(...)` you call by hand. .NET ships SDK hooks that capture
automatically. Each needs: the real wire adapter + operation classification + verbosity + phase-awareness.

- [ ] **HTTP** (`kronikol4j-http`, `-spring`) — add real client adapters: **OkHttp interceptor**, **JDK
  `java.net.http.HttpClient`**, **Spring `WebClient`/Reactor**. Add service-name resolution chain,
  `ExcludedHosts`, W3C `traceparent` injection, header forwarding, phase filtering. *(.NET
  `TestTrackingMessageHandler.cs`; Java `HttpExchangeRecorder.java` is a bare recorder.)*
- [ ] **SQL / JDBC** (`kronikol4j-jdbc`) — wrap `DataSource`/`Connection`/`Statement`/`ResultSet`; multi-
  dialect `UnifiedSqlClassifier` (table extraction, CTE stripping, upsert variants, stored-proc detection);
  response capture (`TrackingDbDataReader` → row count / columns / rows); per-driver `DependencyCategory`;
  two-phase start/end correlation. *(.NET `Sql/`; Java `SqlOperationClassifier` extracts first word only.)*
- [ ] **Redis** (`kronikol4j-redis`) — Lettuce/Jedis command hook; `RedisOperationClassifier` (25+
  commands); GET hit/miss; endpoint/db/key in URI; verbosity. *(.NET `RedisTracking*`.)*
- [ ] **MongoDB** (`kronikol4j-mongodb`) — register a driver `CommandListener` (the `IEventSubscriber`
  analog) for true two-phase correlation; operation classification; filter extraction; response document
  preview; `autoCorrelateWrites`; `ignoredCommands`; change-stream support. *(.NET
  `MongoDbTrackingSubscriber.cs`.)*
- [ ] **Kafka / messaging** (`kronikol4j-messaging`) — **producer/consumer wrappers that stamp + read
  `kronikol-test-name`/`kronikol-test-id` in Kafka message headers** (this is what enables cross-service
  event-driven correlation — currently impossible in Java); Subscribe/Commit/Flush/Unsubscribe/Assign op
  tracking; the distinct tracking methods .NET exposes — `trackSendEvent` (event styling) vs `trackSendMessage`
  (standard arrow) vs `trackConsumeEvent`, and the separate `trackMessageRequest`/`trackMessageResponse`
  pair (caller controls request/response timing independently); `isCurrentRequestFromMyHost()` multi-WAF
  host isolation; an **injectable (non-static) tracker** (Java is static methods only → can't be DI-injected
  or hold per-instance options) with `ITrackingComponent` self-registration; full `MessageTrackerOptions`
  (verbosity, phase, serializer). *(.NET `MessageTracker.cs`, `TrackingKafkaProducer/Consumer`.)*
- [ ] **`TrackingProxy` enhancements** (`kronikol4j-proxy`) — `TrackingLogMode` (Immediate **+ Deferred**,
  integrating `PendingRequestResponseLogs`); `ActivitySource`/OTel span lifecycle for InternalFlow span
  production (`InternalFlowSpanStore.complete(...)`); configurable `uriScheme` (hardcoded `proxy://local/`)
  and `activitySourceName`; `TrackingSafeSerializer` options. *(.NET `TrackingProxy.cs:25,53-116`.)*
- [ ] **gRPC** (`kronikol4j-grpc`) — extend beyond unary to **server-streaming, client-streaming, duplex**;
  Protobuf→JSON; `traceparent` injection; gRPC-status→HTTP-status mapping; verbosity. *(.NET
  `GrpcTrackingInterceptor.cs` overrides 5 call types; Java handles 1.)*
- [ ] **Elasticsearch** (`kronikol4j-elasticsearch`) — SDK callback hook; operation classification;
  verbosity. *(.NET `ElasticsearchTrackingCallbackHandler`.)*
- [ ] **AWS** (`kronikol4j-aws`) — real `ExecutionInterceptor` (AWS SDK v2) for S3/DynamoDB/SQS/SNS;
  per-service classifiers + verbosity + phase. *(.NET ships a `DelegatingHandler` per service.)*
- [ ] **Azure** (`kronikol4j-azure`) — SDK pipeline policies for Cosmos (+ operation classification,
  `autoCorrelateWrites`, change-feed key extractor), Blob, Service Bus. *(.NET `CosmosTrackingMessageHandler`
  etc.)*
- [ ] **GCP** (`kronikol4j-gcp`) — SDK adapters for BigQuery, Cloud Storage, Pub/Sub; per-service
  classifiers + verbosity. *(.NET ships handlers + interceptors per service.)*

---

## Tier 2 — Configuration / options surface

Per-tracker option classes are mostly ~3-of-N fields; several whole option classes are absent.

- [ ] **`ComponentDiagramOptions`** (entire class MISSING) — `fileName`, `embedInTestRunReport`, `title`,
  `plantUmlTheme`, `participantFilter`, `relationshipLabelFormatter`, `showRelationshipFlows`,
  `relationshipFlowStyle`, `showSystemFlameChart`, `lowCoverageThreshold`, `arrowColorMode`,
  `dependencyColors`, `maxFlameChartTests`. *(.NET `ComponentDiagram/ComponentDiagramOptions.cs`.)*
- [ ] **`TestTrackingMessageHandlerOptions`** (3/12) — add `portsToServiceNames`, `clientNamesToServiceNames`,
  `fixedNameForReceivingService`, `headersToForward`, `excludedHosts`, `trackDuringSetup/Action`,
  `currentStepTypeFetcher`, `internalFlowActivitySources`.
- [ ] **`SqlTrackingOptionsBase`** (3/17) — add `verbosity`, `excludedOperations`, `logParameters`,
  `logSqlText`, `setup/actionVerbosity`, `trackDuringSetup/Action`, `uriScheme`, `logResponseContent`,
  `maxResponseRows`, `maxValueDisplayLength`, `responseDetail`.
- [ ] **`MessageTrackerOptions`** (2/14) — add `verbosity`, `setup/actionVerbosity`, `trackDuringSetup/
  Action`, `dependencyCategory`, `callerDependencyCategory`, `useHttpContextCorrelation`,
  `currentStepTypeFetcher`, `serializerOptions`.
- [ ] **Report control flags** — `testRunReportTitle` (currently hardcoded `"Kronikol4J Test Run"`),
  `htmlTestRunReportFileName`, `reportsFolderPath`, `fixedNameForReceivingService`, `expectedTestCount`
  guard, `generateComponentDiagram` toggle, `diagnosticMode` toggle, `requestResponsePostProcessor`/
  `midProcessor` hooks, `inlineBackgroundSteps`, `lazyLoadDiagramImages` (HTML-attribute form only — the
  server-render meaning is out of scope), and the explicit booleans Java currently infers implicitly:
  `generateTestRunReportData` (Java infers from `dataFormats.isEmpty()`) and `generateMergeableData`
  (Java infers from whether `kronikol.run.dir` is set).
- [ ] **Per-report-type data formats** — split the single `ReportOptions.dataFormats` set back into the
  two .NET options `testRunReportDataFormat` vs `specificationsDataFormat` (different formats per report
  type). *(Depends on the Specifications report, Tier 4.)*
- [ ] **`ScenarioTitleResolver`** — `formatScenarioDisplayName` (PascalCase splitting), `formatFeatureName`,
  `appendTestParameters`, `resolveScenarioTitle` (BDDfy-style). Java uses the framework `getDisplayName()`
  directly, which is fine for JUnit/parameterized but diverges for BDD-style sources. *(.NET
  `ScenarioTitleResolver.cs`.)*
- [ ] **HTML customization wiring** — `HtmlCustomization` (CSS/favicon/logo/step-numbers) exists as a model
  but is **not passed through `ReportFinalizer`** → users can't set it. Wire it + expose via system props.
- [ ] **CI publish options** — `writeCiSummary`, `maxCiSummaryDiagrams`, `publishCiArtifacts`,
  `ciArtifactName`, `ciArtifactRetentionDays`. (Generator `CiSummaryGenerator` is ported; the options +
  artifact publishing are not.)
- [ ] **Gradle plugin rich options** — surface `ReportOptions` (colors, formats, schema, …) through the
  `kronikol {}` extension instead of only `-D` system properties.

---

## Tier 3 — Missing integration modules (no Java code at all)

- [ ] **ORM / EF-Core analog** — Hibernate `StatementInspector` (+ JPA/Spring Data hook). This is the
  primary integration point for ORM users and is entirely absent. *(.NET `Extensions.EfCore.Relational`
  `SqlTrackingInterceptor : DbCommandInterceptor`.)* **Highest-value missing module.**
- [ ] **ClickHouse** — `TrackingClickHouseConnection/Command/Transaction`; `CLICK_HOUSE` category. (Shared
  classifier already understands ClickHouse syntax.)
- [ ] **Spanner** — connection/command/transaction wrappers + async stream reader; `SPANNER` category.
- [ ] **Bigtable** — `BigtableTracker` + options + classifier; `BIGTABLE` category.
- [ ] **Azure EventHubs** — producer/consumer client wrappers.
- [ ] **Azure Storage Queues** — message-handler analog.
- [ ] **AWS EventBridge** — interceptor.
- [ ] **MassTransit analog** — bus observer hooks (Java equivalent: Spring `ApplicationEvent`s / Axon — see
  PORT_PLAN Appendix B open question).
- [ ] **Atlas Data API** — HTTP-handler analog.
- [ ] **Dapper analog** — N/A directly (raw JDBC covers it); just expose verbosity + classifier on JDBC.

---

## Tier 4 — Whole features absent

- [ ] **Step tracking** — `StepCollector` (start/complete/bypass, nested sub-steps, keyword sequencing,
  `whenTriggersAction` phase transition, step delimiters, assertion sub-steps, attachments) +
  `StepTrackingOptions` + the `@GivenStep/@WhenStep/@ThenStep/@ButStep/@Step` annotations + build-time
  weaving (Gradle/Maven plugin + bytecode/AST pass; PORT_PLAN §3.4 Tier-2). *(.NET `Tracking/StepCollector.cs`
  + `Kronikol.StepTracking` MSBuild targets.)*
- [ ] **TabularAttributes** — `@Inputs`/`@Outputs`/`@HeadOut`/`@HeadIn` annotations + `TabularResolver` +
  `TabularDeserializer` + typed `TabularInputs<T>`/`TabularOutputs<T>` + `TabularVerificationException`.
  (Java has only the render-side data model `TabularParameterValue`.) *(.NET `TabularAttributes/`.)*
- [ ] **Specifications report** — the separate `Specifications.html` + `Specifications.yaml` outputs and
  their options (`generateSpecificationsReport`, `specificationsTitle`, filenames, `showStepNumbers`, …).
  Java only emits `TestRunReport.html`.
- [ ] **InternalFlow CAPTURE side** — `ActivityListener` (subscribe to OTel `ActivitySource`s, excluding the
  AppInsights-conflict set) + `SpanStore` + `SpanCollector` (granularity filtering) + `ActivitySourceDiscovery`
  + DI/eager-start registration. The *rendering* is done; nothing currently captures spans. Plus the ~12
  InternalFlow sub-options (`InternalFlowDisplay/Trigger/DiagramStyle/SpanGranularity/...`) and
  `WholeTestFlowVisualization` as a user option.
- [ ] **`TrackingDiagramOverride`** — inject arbitrary PlantUML fragments + programmatic phase boundaries
  (`insertPlantUml`/`startOverride`/`endOverride`/`startAction`/`startSetup`).
- [ ] **`DiagramFocus`** — ambient "emphasize these JSON fields in the next note" mechanism.
- [ ] **Assertion fidelity** — `Track.attachment(file, name)`; `Track.that` returning a value (`<T>`);
  `@SuppressAssertionTracking`; `Track.diagnosticMode` toggle + `diagnosticLog`/`clearDiagnosticLog`;
  `Track.testIdResolver` static hook; closure-value resolution + `AssertionExpressionFormatter` (readable
  "Order status should be equivalent to 'Confirmed'" text). *(C#-reflection-specific parts — closure-field
  inspection — may be a documented boundary; decide per item. `thatAsync` is N/A in Java.)*
- [ ] **`TrackingTraceContext`** (`beginTrace`/`createParentContext`) — creates a new ambient trace id and
  builds a parent span context for the proxy's `ActivitySource` (the *write* counterpart to the read-only
  `OtelBridge`). Pairs with the `TrackingProxy` span-lifecycle work. *(.NET `Tracking/TrackingTraceContext.cs`.)*
- [ ] **`TestTrackingServerBridge.getCurrentTestInfo()`** — expose the server-side "read test identity from
  the current request" logic as a public API (today it's internal to `KronikolServletFilter`).
- [ ] **`ITabularParameterData`** — the interface for supplying tabular data as a *step parameter* (distinct
  from the TabularAttributes declaration feature; consumed by step tracking). *(.NET
  `Tracking/Tabular/ITabularParameterData.cs`.)*
- [ ] **`TrackingHttpMessageHandlerBuilderFilter` analog** — auto-inject tracking into every framework-
  created HTTP client (Spring Boot starter currently covers only `RestTemplate`).
- [ ] **`UnmatchedClientNameRegistry`** — diagnostic registry of unresolved client names (feeds the
  diagnostic report).

---

## Tier 5 — Tooling & onboarding

- [ ] **Maven plugin** — a Mojo mirroring `kronikol4j-gradle-plugin` (fork dir + merge task). Maven users
  currently have only the CLI.
- [ ] **Project templates / archetypes** — the `dotnet new kronikol-*` analog (Maven archetype / `gradle
  init` skeleton) for each test-framework combo.
- [ ] **Build-time weaving auto-wiring** — the assertion/step weavers as Gradle/Maven tasks, so users don't
  need an explicit `-javaagent:` argument (the ByteBuddy agent exists but isn't auto-wired). .NET ships
  three distinct build packages: `Kronikol.StepTracking` (`.targets` that codegen the step attributes + run
  the IL weaver after compile, gated by `<TrackStepsEnabled>`), `Kronikol.AssertionTracking` (Mono.Cecil IL
  weaver + `@TrackAssertions`/`@SuppressAssertionTracking` codegen), and `Kronikol.AssertionRewriter` (a
  Roslyn *source* rewriter running before compile). Decide the Java equivalent for each (AST/bytecode pass)
  or document any as an explicit boundary.
- [ ] **Kafka build-interception package** — `Kronikol.Extensions.Kafka.BuildInterception` (MSBuild
  interception targets that auto-wire Kafka tracking). Decide Gradle/Maven equivalent.
- [ ] **CLI distribution form** — fat-jar is built; decide on `jbang` / `jreleaser` packaging and a
  `dotnet tool install`-equivalent one-line install (PORT_PLAN Appendix B).

---

## Tier 6 — Minor helpers, exposed-API gaps & wiring fixes

Small but real parity items — convenience helpers, factory methods, and "the code exists but isn't wired in"
fixes. Listed for completeness so nothing is silently dropped.

- [ ] **`PhaseVariantExtensions`** (`attachVariants`/`withVariants`) — the helper that conditionally computes
  + sets `setupVariant`/`actionVariant` on a log (only when phase is Unknown and a verbosity override is
  configured). The fields exist on the log; the helper that populates them does not. *(.NET
  `PhaseVariantExtensions.cs:24`.)*
- [ ] **`TestInfoResolver.createHttpFallbackFetcher`** — the static factory producing a combined
  "HTTP-headers-first, then delegate" identity fetcher. *(.NET `TestInfoResolver.cs:86`.)*
- [ ] **`PhaseConfiguration.resolvePhaseFromStepType`** — expose the Given/And/But→Setup, When/Then→Action
  mapping on `PhaseConfiguration` (the logic exists only inside the Cucumber module's `GherkinPhase`).
- [ ] **`ProcessingCorrelation` naming/signature parity** — `wrapSync` named alias + the cancellation-signal
  parameter on the batch wrapper (Java's batch wrapper omits it). *(.NET `ProcessingCorrelation.cs:41`.)*
- [ ] **Wire `DiagnosticReportGenerator` into `ReportFinalizer`** — the diagnostic generator is fully ported
  but never triggered from the finalization path (.NET invokes it when diagnostic mode is on and there are
  logs but no test contexts). Depends on the `diagnosticMode` toggle (Tier 2).
- [ ] **CLI merge title resolution** — `kronikol4j merge` overrides the title unconditionally when `-t` is
  given; .NET resolves it from the first fragment's CI metadata when not supplied. Minor behavior parity.

---

## Explicitly OUT OF SCOPE (locked boundaries — do not implement)

- Server-side PlantUML image rendering (PlantUML-server / IKVM / Node.js / inline-SVG `ImgSrc` pre-render).
  Diagrams render in-browser via PlantUML-WASM. *(PORT_PLAN §3.5.)*
- CI summary **inline rendered PNGs** — replaced by a link to the HTML report artifact (deliberate behavior
  change; the markdown generator itself is ported).
- Reflection-based `ExampleRawValues` live-object-graph rendering — not cross-runtime byte-stable; only
  deterministic string-based paths are ported.
- `.NET`-DI-specific helpers with no Java analog (`ServiceCollectionDecoratorExtensions` etc.) — Java uses
  Spring `@Bean`/`@Primary` idioms instead.

---

## Suggested sequencing

1. **Cross-cutting infra** (verbosity, phase-awareness, service-name resolution, classifiers, safe
   serializer) — unblocks everything in Tier 1/2.
2. **Tier 1** integration adapters, highest-traffic first: HTTP → JDBC → Redis → Mongo → Kafka.
3. **Tier 2** options (especially the resolution/exclusion options that change captured output).
4. **Tier 3** modules, ORM first.
5. **Tier 4** features (step tracking + tabular + specifications + internal-flow capture).
6. **Tier 5** tooling.
7. **Tier 6** minor helpers / wiring fixes — pull each one in alongside its related tier rather than as a
   separate pass (e.g. the `DiagnosticReportGenerator` wiring lands with the Tier-2 `diagnosticMode` option;
   `PhaseVariantExtensions` lands with the phase-awareness infra).

Capture-side behavior is golden-provable end-to-end: drive a real dependency from a Java test, capture the
.NET equivalent via the parity harness, and assert the resulting report matches — same methodology that got
the output surface to byte-parity.
