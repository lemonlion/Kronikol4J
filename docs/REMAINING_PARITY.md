# Kronikol4J — Remaining Parity Roadmap

> ## ✅ COMPLETE — incl. 2 audit-found gaps now fixed (2026-06-28)
> An independent re-audit against the .NET source (not trusting this ledger) **confirmed** the dead-config /
> boundary closes were correct and the full `ReportConfigurationOptions` surface is covered — and found **two
> real gaps that had been wrongly closed, now fixed:**
> 1. **Standalone `ComponentDiagram.html` report** — `.NET ReportGenerator` emits a *separate* component-diagram
>    HTML file (gated by `GenerateComponentDiagram`, default true); its `useBrowserJs` path is in-scope. **Fixed:**
>    new `io.kronikol.report.ComponentDiagramReportGenerator` (browserJs self-contained page, reusing the
>    golden-proven assets), written by `ReportFinalizer`, and **byte-for-byte golden-proven** vs real .NET
>    (`ComponentDiagramReportGoldenTest`, captured via a new `CaptureComponentDiagramReport` harness case). Flag
>    semantics realigned: `generateComponentDiagram` → standalone file; `embedInTestRunReport` → embedding.
> 2. **`GenerateTestRunReport`** (HTML master switch, default true). **Fixed:** added to `ReportControlOptions`;
>    `ReportFinalizer` skips `TestRunReport.html` when off (data/specs/component still run). Proven by
>    `ReportFinalizerTest`.
>
> **Byte-parity — capture side (updated after audit follow-up):** report *rendering* is golden byte-proven.
> Capture-side parity is now also being byte-proven cross-runtime, not just unit-tested:
> - **Classifiers (env-free):** `SqlClassificationParityTest` (19 cases) and `RedisClassificationParityTest`
>   (15 cases, incl. cache hit/miss) byte-diff the Java classifiers against goldens captured from the **real
>   .NET classifiers** (`parity-harness/dotnet-capture`). Both pass.
> - **End-to-end vs a live service:** `RedisInteractionParityTest` drives the **real Jedis adapter** against a
>   running Redis and byte-diffs the emitted `RequestResponseLog`s against the **real .NET StackExchange
>   adapter** driven against a live Redis (golden `redis-interactions.txt`, harness `KRON_REDIS_E2E=1`).
>   **Result:** `type | method-label | uri | status` are byte-identical on every line, and read-op content
>   (hit value / miss null) matches. This **found real differences** the fake-proxy unit tests miss:
>   write-op response content differs by client library (Jedis `SET`→`OK`/`DEL`→count vs StackExchange→bool),
>   and a genuine **Java gap** — the Jedis `HashSet` tracker captures no `field=value` request content where
>   .NET captures `f=v` (flagged for follow-up). Remaining adapters (Postgres/Mongo/Kafka/Elasticsearch +
>   cloud emulators) follow the same recipe. Caveat: Testcontainers couldn't self-manage the container from the
>   JDK-25 test JVM via Rancher's Windows npipe, so the e2e test takes a configurable `kron.redis.endpoint`
>   (skips when no Redis is reachable). `./gradlew clean build` + full suite + Playwright green.

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

- [x] **Verbosity framework** — shared `TrackingVerbosity` enum + per-tracker resolution logic.
  **Resolved:** the .NET source of truth defines exactly three levels (`Raw` / `Detailed` / `Summarised`),
  duplicated across `MessageTrackerVerbosity` + `SqlTrackingVerbosityLevel`; the `HeadersOnly` / `None`
  levels originally floated here do **not** exist anywhere in `c:\Code\Kronikol\src\Kronikol`, so they were
  deliberately not modelled (an unbacked enum member would be a stub). Java now has
  `io.kronikol.core.tracking.TrackingVerbosity` (RAW/DETAILED/SUMMARISED + `DEFAULT`, `includesPayload()`,
  `includesRawDetail()`). The per-tracker resolution logic already existed as the generic
  `PhaseConfiguration.effectiveVerbosity(...)` / `shouldTrack(...)`; `TrackingVerbosity` composes with it.
  Per-tracker *wiring* lands with each Tier-1 adapter. Proven by `TrackingVerbosityTest`.
- [x] **Phase-aware tracking suppression** — wire `PhaseConfiguration.shouldTrack()` (already in
  `kronikol4j-core`) into every extension execution path, honoring `TrackDuringSetup` / `TrackDuringAction`
  + `SetupVerbosity` / `ActionVerbosity`. Currently no Java tracker consults phase at all.
  **Primitives complete:** `shouldTrack(...)`, `effectiveVerbosity(...)` (both in `PhaseConfiguration`,
  tested) and now `PhaseVariantExtensions.attachVariants/withVariants` (tested) are all in place. The
  remaining work is the *per-adapter wiring*, which is intentionally deferred: no Java tracker yet exposes
  `TrackDuringSetup/Action` or `Setup/ActionVerbosity` options (those option surfaces are the Tier-1 adapter
  + Tier-2 option items). Each Tier-1 adapter wires these primitives in as it is built; this box flips to
  `[x]` once every execution path consults them.
  **Wiring progress (2026-06-28):** HTTP (`HttpTrackingConfig.trackDuringSetup/Action`) and SQL/JDBC
  (`SqlTrackingOptions`, reused by Hibernate) already consult phase. AWS now does too — `AwsTrackingOptions`
  gained `trackDuringSetup`/`trackDuringAction` (default true, `withTrackDuringSetup/Action`) and the s3/
  dynamoDb/sqs/sns recorders guard on `PhaseConfiguration.shouldTrack(...)`. Proven by `AwsTrackingTest`
  (Action-phase suppression skips all three; Setup unaffected). **Azure + GCP done (2026-06-28):** same wiring
  — `AzureTrackingOptions`/`GcpTrackingOptions` gained `trackDuringSetup/Action` and their cosmos/blob/
  serviceBus and bigQuery/storage/pubSub recorders guard on `shouldTrack(...)`. Proven by
  `AzureTrackingTest`/`GcpTrackingTest`. **Cassandra + Elasticsearch done (2026-06-28):** same wiring —
  `CassandraTrackingOptions`/`ElasticsearchTrackingOptions` gained `trackDuringSetup/Action` and their
  `record(...)` paths guard on `shouldTrack(...)`. Proven by `CassandraTrackingTest`/`ElasticsearchTrackingTest`.
  **gRPC done (2026-06-28):** `GrpcTrackingOptions` gained `trackDuringSetup/Action`; `GrpcTracking.record`
  guards on `shouldTrack(...)`. Proven by `GrpcTrackingTest`. **Already phase-aware** (verified): the richer
  two-phase `InteractionRecorder`s — Redis, Mongo, SQL/JDBC, Bigtable, EventHubs, EventBus — and the
  `MessageTracker` (so the Kafka producer/consumer wrappers inherit it). **MessageTracking facade + TrackingProxy
  done (2026-06-28):** `MessageTrackingOptions`/`ProxyOptions` gained `trackDuringSetup/Action`;
  `publish`/`consume` and `TrackingProxy.invoke` guard on `shouldTrack(...)`. Proven by
  `MessageTrackingTest`/`TrackingProxyEnhancementsTest`.
  **The `TrackDuringSetup/Action` on/off dimension is now wired into every tracking execution path** (HTTP,
  JDBC/Hibernate, Redis, Mongo, AWS, Azure, GCP, Cassandra, Elasticsearch, gRPC, MessageTracker + Kafka
  wrappers, MessageTracking, Bigtable, EventHubs, EventBus, TrackingProxy). **Remaining (the only thing left
  before `[x]`):** the second dimension — `Setup/ActionVerbosity` per-phase verbosity overrides — wired via
  `PhaseConfiguration.effectiveVerbosity(default, setup, action)`. **Started (2026-06-28):** AWS done —
  `AwsTrackingOptions` gained `setupVerbosity`/`actionVerbosity` (`withSetupVerbosity/ActionVerbosity`), and
  the recorders resolve the effective level per phase. Proven by `AwsTrackingTest` (Setup-override drops the
  payload while Action keeps it). **Azure + GCP done (2026-06-28):** same wiring — `setupVerbosity`/
  `actionVerbosity` on both options, recorders resolve `effectiveVerbosity`. Proven by
  `AzureTrackingTest`/`GcpTrackingTest`. **Cassandra + Elasticsearch + gRPC done (2026-06-28):** same wiring
  (`setupVerbosity`/`actionVerbosity` + `effectiveVerbosity` resolution). Proven by their tracking tests.
  **Verified already per-phase:** the richer `InteractionRecorder`s — SQL/JDBC, Redis, Mongo, Bigtable,
  EventHubs, EventBus — and `MessageTracker` all resolve `effectiveVerbosity(base, setup, action)`. The
  `MessageTracking` facade + `TrackingProxy` have no verbosity dimension (payload always / serializer-based),
  so per-phase verbosity is N/A there. **HTTP done (2026-06-28) → item complete `[x]`:** `HttpTrackingConfig`
  gained `setupVerbosity`/`actionVerbosity` + an `effectiveVerbosity()` resolver, and the OkHttp/JDK/WebClient
  adapters now gate body capture on `config.effectiveVerbosity()`. Proven by `HttpTrackingConfigTest`.
  **Both phase dimensions are now honored across every tracking execution path:** the `TrackDuringSetup/Action`
  on/off suppression *and* the `Setup/ActionVerbosity` per-phase verbosity overrides — HTTP, JDBC/Hibernate,
  Redis, Mongo, AWS, Azure, GCP, Cassandra, Elasticsearch, gRPC, MessageTracker (+ Kafka wrappers),
  MessageTracking, Bigtable, EventHubs, EventBus, TrackingProxy. The primitives
  (`PhaseConfiguration.shouldTrack`/`effectiveVerbosity` + `PhaseVariantExtensions`) were already in place;
  this completes the per-adapter wiring.
- [x] **Service-name resolution chain** — `PortsToServiceNames`, `ClientNamesToServiceNames` (with
  suffix/contains fallback for generated client names), `FixedNameForReceivingService`, `ExcludedHosts`.
  Used by HTTP + cloud adapters. (.NET `TestTrackingMessageHandler.cs:58-139`.)
  **Done:** `io.kronikol.core.naming.ServiceNameResolver` ports the full 4-step `ResolveServiceName` chain
  (fixed → exact client → fuzzy endsWith-with-boundary then assembly-qualified-only contains → port →
  `localhost:<port>`), preserving insertion order for deterministic fuzzy matching; `ExcludedHosts` ports the
  OrdinalIgnoreCase host-exclusion check. The unmatched-client-name recording is exposed as an injectable
  callback seam (the `UnmatchedClientNameRegistry` itself is a separate Tier-4 item). Per-adapter wiring
  lands with the Tier-1 HTTP/cloud adapters. Proven by `ServiceNameResolverTest` + `ExcludedHostsTest`.
- [x] **Operation classifiers** — per-protocol command/operation classification (SQL, Redis, Mongo,
  Elasticsearch, gRPC, cloud). .NET has `UnifiedSqlClassifier`, `RedisOperationClassifier`, etc.; Java has
  keyword-only stubs.
  **SQL done:** `io.kronikol.core.sql.UnifiedSqlClassifier` (+ `UnifiedSqlOperation`, `UnifiedSqlOperationInfo`,
  `SqlCommandType`) ports the full .NET `UnifiedSqlClassifier` — multi-dialect prefix stripping (Spanner
  hints, SET, CTE), 20+ operations incl. upsert variants (`ON CONFLICT`/`ON DUPLICATE KEY`/`INSERT OR …`),
  stored-proc detection, ClickHouse `OPTIMIZE`/`RENAME`/`ATTACH`/`DETACH` + lightweight `ALTER … UPDATE/DELETE`
  mutations, quoted/schema-qualified table extraction, plus `getDiagramLabel` (Raw/Detailed/Summarised),
  `getRawKeyword`, `extractProcName`. Placed in core (zero-dep) since JDBC/ClickHouse/Spanner/Bigtable all
  share it. Proven by `UnifiedSqlClassifierTest` (18 cases). The Redis/Mongo/Elasticsearch/gRPC/cloud
  classifiers are tracked under their respective Tier-1 adapter items and land with each adapter.
  **Kafka classifier done (2026-06-28):** `io.kronikol.messaging.KafkaOperationClassifier` (+ `KafkaOperation`
  enum, `KafkaOperationInfo` record) ports the .NET `KafkaOperationClassifier` byte-for-byte —
  `getDiagramLabel` (Raw `"<Op> <topic>[partition]@offset"`, Detailed directional `"Produce → …"`/
  `"Consume ← …"`/`"Subscribe …"`, Summarised terse `"Init Txn"`/`"Send Offsets"`/…) and `buildUri`
  (`kafka:///<topic>[/partition][@offset]` matrix), over the unified `TrackingVerbosity` (the .NET
  `KafkaTrackingVerbosity` Raw/Detailed/Summarised collapses into it). Proven by
  `KafkaOperationClassifierTest` (5 cases across the label + URI matrix).
  **All per-protocol classifiers complete (2026-06-28) → item `[x]`:** every protocol named here now has a
  full classifier, each unit-proven byte-for-byte against .NET — SQL (`UnifiedSqlClassifier`), Redis, Mongo
  (+ `AtlasDataApiOperationClassifier`), Elasticsearch, gRPC, Kafka, the in-process bus
  (`EventBusOperationClassifier`), and the full cloud set: AWS (`S3`/`DynamoDb`/`Sqs`/`Sns`/`EventBridge`),
  Azure (`Blob`/`Cosmos`/`ServiceBus`/`StorageQueue`), GCP (`BigQuery`/`CloudStorage`/`PubSub`), `Bigtable`,
  `EventHubs`. Each is wired into its adapter to drive the diagram label + URI — the last gap, the Kafka
  wrapper wiring, is now closed: `TrackingKafkaProducer`/`TrackingKafkaConsumer` classify
  `Produce`/`Consume` via `KafkaOperationClassifier` (using the tracker's per-phase `effectiveVerbosity()`)
  so labels read `"Produce → <topic>"` / `"Consume ← <topic>"` (Detailed) and `"Consume <topic>[part]@offset"`
  (Raw, from the consume record's partition + offset), with the matching `kafka://` URIs. Proven by the
  updated `TrackingKafkaProducerTest`/`TrackingKafkaConsumerTest` (Detailed labels + Raw partition/offset).
  The remaining Kafka *adapter*-specific threads (Subscribe/Commit/Flush lifecycle-op tracking, the Spring
  `BeanPostProcessor` auto-wiring) stay tracked under the Tier-1 Kafka item — they are not classifier work.
  The .NET `Dapper`/`MassTransit` classifiers are runtime boundaries (Dapper is .NET-only; MassTransit maps to
  the generic `EventBusOperationClassifier`).
- [x] **`TrackingSafeSerializer` equivalent** — mock-proxy detection, `Future`/`CompletableFuture` result
  unwrapping, circular-ref handling, `MaxDepth`, `SkipTypes`. (.NET `Tracking/TrackingSafeSerializer.cs`.)
  **Done:** `io.kronikol.core.serialization.TrackingSafeSerializer` + `TrackingSerializerOptions`. Ports the
  guard layer (null→null, mock-proxy→`"<mock proxy>"`, future unwrap→value/`"<pending Task>"`, `Object[]`
  filtering by skip-types/proxies keeping nulls) and — because the JDK has no reflective JSON and core is
  zero-dep — a dependency-free reflective JSON writer (maps/collections/arrays/records/getter-POJOs/
  primitives) with IgnoreCycles-style cycle handling, a max-depth guard, null-property stripping, UnsafeRelaxed
  escaping, and a quoted-`toString()` fallback on failure. Two .NET fields adapted to the platform:
  `mockProxyMarkers` (configurable, replaces the hard-coded `Castle.Proxies`) and dropping
  `FilterCancellationTokens` (no Java analog → use `skipTypes`). Proven by `TrackingSafeSerializerTest`
  (14 cases). Per-adapter wiring (e.g. `TrackingProxy` serializer options) lands with those adapters.
- [x] **`CorrelationKeys` completion** — add the 6 missing key-format helpers: `cosmos(svc,partition,doc)`
  3-arg, `eventHubs`, `pubSub`, `sqs`, `sns`, `storageQueue`. (.NET `CorrelationKeys.cs`.)
  **Done:** all 6 added to `io.kronikol.core.context.CorrelationKeys` with byte-identical prefixes
  (`cosmos:`/`eventhubs:`/`pubsub:`/`sqs:`/`sns:`/`storagequeue:`). Proven by `CorrelationKeysTest`
  (covers all 11 helpers).
- [x] **`ProcessingCorrelation` async wrappers** — `CompletableFuture`/`Callable` handler wrapping (Java has
  only sync `Consumer` forms). (.NET `ProcessingCorrelation.cs:21`.)
  **Done:** added `wrapAsync` (per-item `Function<T, CompletionStage<Void>>`), `wrapBatchAsync`,
  `wrapCallable(key, Callable)` and `wrapRunnable(key, Runnable)`. The Callable/Runnable forms establish the
  ThreadLocal scope on the executing thread (the correct shape for executor submissions); the async form
  establishes it for the synchronous handler launch (a Java ThreadLocal cannot flow into cross-thread
  `CompletableFuture` continuations like .NET `AsyncLocal` — cross-thread attribution uses the data-keyed
  `TestCorrelationStore`, documented). **Bug fixed along the way:** the batch scope-selection picked the
  first *non-null* key and stopped, leaving the batch unattributed when that key didn't resolve; now it picks
  the first key that actually *resolves*, matching .NET `WrapBatch` (fixes the existing sync `wrapBatch` too).
  Proven by `ProcessingCorrelationTest`.
- [x] **`TestCorrelationStore` gaps** — `onResolveMiss` callback, `remove(key)`, `seed(...)`, public TTL.
  **Done:** added `onResolveMiss(Consumer<String>)` (fires on both not-found and expired, matching .NET),
  `remove(key)` (boolean), `seed(...)` (intent alias of `correlate`), and public `defaultTtl()` getter/setter.
  Also corrected the TTL model to .NET semantics: entries store `createdAt` and the TTL is evaluated at
  `resolve` time against the live `defaultTtl`, so shrinking the TTL retroactively expires existing entries
  (the old Java code computed `expiresAt` at write time and ignored later TTL changes). Proven by the
  expanded `TestCorrelationStoreTest`.
- [x] **Deferred flush** — `DeferredLogFlushHandler` + `PendingRequestResponseLogs`: queue logs emitted
  before identity is known, flush once it resolves. (.NET `Tracking/DeferredLogFlushHandler.cs`.)
  **Mechanism done:** `io.kronikol.core.tracking.PendingRequestResponseLogs` (thread-safe queue:
  `enqueue`/`count`/`flushAll`/`clear`) + `PendingLogEntry` (record + builder). `flushAll(name, id, ids)`
  drains the queue, emitting each entry as a request+response pair sharing one trace/request-response id
  (from the `IdGenerator` determinism seam) through `RequestResponseLogger`. Proven by
  `PendingRequestResponseLogsTest`. **HTTP flush handler done (2026-06-28) → item complete `[x]`:**
  `io.kronikol.http.DeferredLogFlushInterceptor` is an OkHttp `Interceptor` (okhttp `compileOnly`) — the
  faithful analog of the .NET `DeferredLogFlushHandler` `DelegatingHandler`: after each exchange it drains
  `PendingRequestResponseLogs` (the point where ambient identity is reliably resolvable), attributing the
  deferred entries to the resolved test; when no test context resolves the flush is skipped and entries remain
  queued for the next exchange (mirroring .NET swallowing a throwing fetcher). Two constructors mirror .NET
  (`Supplier<TestInfo>` + `IdGenerator`, or an `HttpTrackingConfig`), and the Javadoc documents the
  install-outside-the-tracking-interceptor ordering (the .NET "place OUTSIDE TestTrackingMessageHandler"
  guidance). Proven by `DeferredLogFlushInterceptorTest` (MockWebServer: flush-after-response attributed to
  the test, no-context leaves entries queued, nothing-pending no-op). The proxy's deferred `TrackingLogMode`
  consumes the same queue (the `TrackingProxy` item), so both .NET consumers of `PendingRequestResponseLogs`
  now have Java analogs.

---

## Tier 1 — Turn the existing "recorder" modules into real auto-capturing adapters

Today these expose `record(...)`/`publish(...)` you call by hand. .NET ships SDK hooks that capture
automatically. Each needs: the real wire adapter + operation classification + verbosity + phase-awareness.

- [x] **HTTP** (`kronikol4j-http`, `-spring`) — add real client adapters: **OkHttp interceptor**, **JDK
  `java.net.http.HttpClient`**, **Spring `WebClient`/Reactor**. Add service-name resolution chain,
  `ExcludedHosts`, W3C `traceparent` injection, header forwarding, phase filtering. *(.NET
  `TestTrackingMessageHandler.cs`; Java `HttpExchangeRecorder.java` is a bare recorder.)*
  **OkHttp done:** `KronikolOkHttpInterceptor` (+ `OkHttpTrackingOptions`) — a real `okhttp3.Interceptor`
  (okhttp `compileOnly`) auto-capturing each exchange. Wires in the shared infra: `ServiceNameResolver`
  (per-request port), `ExcludedHosts`, phase filtering (`shouldTrack`), `TrackingVerbosity` (body capture),
  the `IdGenerator` seam; stamps the test-identity + `TRACE_ID` headers and injects a W3C `traceparent`
  (new reusable `io.kronikol.core.tracking.W3CTraceparent`) when absent. Proven by
  `KronikolOkHttpInterceptorTest` (MockWebServer end-to-end: capture, header injection, excluded-host skip,
  no-test-context skip, summarised verbosity, traceparent passthrough).
  **JDK `HttpClient` done:** `TrackingHttpClient` — an `HttpClient` decorator (no external dep) wrapping a
  real client; rebuilds each request with identity/trace headers + traceparent, tees the request body through
  a capturing `BodyPublisher` (honest body capture despite the write-only publisher model), captures
  string/byte response bodies, and records the pair for both `send` and both `sendAsync` overloads. Shares
  one `HttpTrackingConfig` with the OkHttp adapter (generalised from `OkHttpTrackingOptions`). Proven by
  `TrackingHttpClientTest` (sync + async + tee body + gating).
  **Spring `WebClient` done:** `KronikolWebClientFilter` (an `ExchangeFilterFunction`, spring-webflux
  `compileOnly`) — gating, identity/trace + traceparent injection, service-name resolution, and response-body
  capture via buffer-and-re-supply (the caller still reads the body). Proven by `KronikolWebClientFilterTest`
  (JDK connector + MockWebServer).
  **WebClient request-body capture done (2026-06-28):** `KronikolWebClientConnector` — a `ClientHttpConnector`
  decorator (the transport layer, where the otherwise write-only `BodyInserter` is readable) that captures
  **both** bodies, the WebClient analog of the OkHttp/JDK both-body capture and the .NET `DelegatingHandler`.
  It tees the request body (`ClientHttpRequestDecorator.writeWith`/`writeAndFlushWith`, peek-copy without
  advancing the read position) and the response body (`ClientHttpResponseDecorator.getBody`, emit-on-finally),
  injects the identity/trace + W3C `traceparent` headers via `beforeCommit` (the supported connector-decorator
  hook — eager header sets are dropped at commit), and reuses the shared `ServiceNameResolver`/`ExcludedHosts`/
  phase-gating/`TrackingVerbosity` infra. The existing `KronikolWebClientFilter` stays as the lighter
  response-only option. Proven by `KronikolWebClientConnectorTest` (JDK connector + MockWebServer: both bodies
  captured + caller still reads the response, identity/traceparent on the wire, no-test-context pass-through).
  **`headersToForward` done (2026-06-28) → HTTP item complete `[x]`:** the .NET `HttpContextAccessor` is now
  mirrored by the zero-dep ambient seam `io.kronikol.core.context.IncomingRequestHeaders` (a `ThreadLocal`
  name→value lookup, `begin(...)` returning an `AutoCloseable` scope; covers synchronous request handling —
  reactive cross-thread propagation is the documented same boundary as the rest of the ambient context). The
  `KronikolServletFilter` opens it per incoming request (alongside the identity scope, cleared in `finally`).
  The shared helper `io.kronikol.http.ForwardedHeaders.collect(names)` resolves the configured header names
  against it (the .NET `ForwardHeaders`: forward a name only when present on the incoming request), and **every
  `HttpTrackingConfig`-based client adapter** copies them onto the outgoing request — OkHttp
  (`KronikolOkHttpInterceptor`), JDK (`TrackingHttpClient`), WebClient filter (`KronikolWebClientFilter`) and
  WebClient connector (`KronikolWebClientConnector`, via `beforeCommit`). (The `RestTemplate` interceptor is the
  separate `HttpTrackingOptions` bare-recorder path that injects no headers by design — not part of the
  `TestTrackingMessageHandler`/`HttpTrackingConfig` contract.) Proven by `IncomingRequestHeadersTest` (core
  seam: lookup, nesting/restore, clearing, throwing-lookup swallowed), `KronikolOkHttpInterceptorTest` +
  `KronikolWebClientConnectorTest` (present-incoming → forwarded, absent → skipped, no-scope → no-op) and
  `KronikolServletFilterTest` (filter exposes incoming headers during handling, clears after). This closes the
  HTTP adapter item end-to-end: OkHttp/JDK/WebClient capture (both bodies), identity/trace/traceparent
  injection, service-name resolution, excluded-hosts, phase filtering, and header forwarding.
- [x] **SQL / JDBC** (`kronikol4j-jdbc`) — wrap `DataSource`/`Connection`/`Statement`/`ResultSet`; multi-
  dialect `UnifiedSqlClassifier` (table extraction, CTE stripping, upsert variants, stored-proc detection);
  response capture (`TrackingDbDataReader` → row count / columns / rows); per-driver `DependencyCategory`;
  two-phase start/end correlation. *(.NET `Sql/`; Java `SqlOperationClassifier` extracts first word only.)*
  **Log-builder core done:** `SqlInteractionRecorder` ports .NET `SqlDiagnosticTracker`'s `LogRequest`/
  `LogResponse` with full parity — two-phase correlation (returns a `Correlation` token), verbosity-driven
  method/content/URI building (`getDiagramLabel`/`getRawKeyword`, the `sql://ds/db/table` vs `sql:///db` vs
  `sql://ds/db` URI matrix), Summarised/Other skip, excluded operations, phase suppression, and unknown-phase
  variants — all via the already-built `UnifiedSqlClassifier`/`PhaseConfiguration`/`PhaseVariantExtensions`.
  Backed by `SqlTrackingOptions` (+ `SqlResponseDetail`), which also satisfies most of the Tier-2
  `SqlTrackingOptionsBase` item. Proven by `SqlInteractionRecorderTest` (7 cases across all verbosity levels).
  **Auto-capture plumbing done:** `TrackingDataSource.wrap(ds, options)` returns a `DataSource` whose
  connections proxy (dynamic `java.lang.reflect.Proxy`, not hand-written delegates) `Statement`/
  `PreparedStatement`/`CallableStatement` — recording `executeQuery`/`executeUpdate`/`executeLargeUpdate` via
  the recorder, with `ResultSet` response capture (`ResultSetInvocationHandler` counts rows and emits
  `"N rows [Col1, Col2]"` on exhaustion/close, the `TrackingDbDataReader` analog). Response formatting in
  `SqlResultSummary` (ROW_COUNT_ONLY + ROW_COUNT_AND_COLUMNS, 20-column truncation). Proven end-to-end
  against in-memory **H2** (`TrackingDataSourceTest`: insert→row count, select→rows+columns, prepared
  statements). **Untyped `execute(...)` + batch tracking done (2026-06-28):** `StatementInvocationHandler`
  now also records `execute(...)` (response row count read back via `getUpdateCount()`, left unknown when it
  produced a `ResultSet`) and `executeBatch()`/`executeLargeBatch()` for prepared statements (response = the
  summed positive per-statement counts, skipping the `SUCCESS_NO_INFO`/`EXECUTE_FAILED` sentinels). Proven by
  `TrackingDataSourceTest` (`untypedExecuteIsTrackedWithUpdateCount`, `preparedStatementBatchIsTrackedWithSummedCounts`).
  **`FULL_ROWS` cell-level capture done (2026-06-28) → item complete `[x]`:** `ResultSetInvocationHandler` now
  captures each row's cells as it is read (gated by `maxResponseRows`, mirroring .NET `CaptureCurrentRowIfNeeded`
  + `FormatCellValue`: `byte[]`→`"[bytes: N]"`, over-`maxValueDisplayLength` strings truncated to
  `"…(N chars)"`, else the raw value), and `SqlResultSummary.formatFullRows(...)` renders them as compact JSON
  (`WriteIndented=false`, `UnsafeRelaxedJsonEscaping`, **null cells kept** — the exact .NET `JsonSerializer`
  settings) with the `"\n... (N more rows not shown)"` trailer when `totalRows > maxRows` and the
  `maxResponseRows == 0` → column-format fallback. Proven by `SqlResultSummaryTest` (compact JSON keeping nulls,
  more-rows trailer, zero-max fallback, UnsafeRelaxed escaping) + `TrackingDataSourceTest` end-to-end against H2
  (`fullRowsDetailCapturesCellLevelJson`, `fullRowsDetailTruncatesAtMaxResponseRows`). **Other listed
  follow-ups resolved/covered:** per-driver `DependencyCategory` defaults are supplied by the dedicated module
  wrappers (`ClickHouseTracking`→`CLICK_HOUSE`, `SpannerTracking`→`SPANNER`, Bigtable's recorder→`BIGTABLE`),
  which is exactly how .NET assigns a category per provider; those modules already consume this `TrackingDataSource`
  plumbing. **Golden proof covered, not a gap:** SQL rendering is byte-golden-proven by `kronikol4j-diagram`
  `sql.puml`, and the FULL_ROWS note JSON is unit-proven byte-exact against the .NET serializer settings for the
  cross-runtime-stable cell types (null/string/integral & decimal/boolean); temporal/LOB cells render via each
  runtime's natural form — the same documented boundary as reflection-based value rendering.
- [x] **Redis** (`kronikol4j-redis`) — Lettuce/Jedis command hook; `RedisOperationClassifier` (25+
  commands); GET hit/miss; endpoint/db/key in URI; verbosity. *(.NET `RedisTracking*`.)*
  **Classifier done:** `RedisOperationClassifier` (+ `RedisOperation`, `RedisCacheResult`,
  `RedisOperationInfo`) ports the full .NET classifier — the command→operation table (GET/SET/INCR/DECR/DEL/
  EXISTS/EXPIRE/hash/list/set/PUBLISH families, 40+ command names), GET/HGET hit/miss detection from whether a
  value was returned, key + database number, and `getDiagramLabel` (`"Get (Hit)"` / `"Get (Miss)"` /
  Raw→null). Proven by `RedisOperationClassifierTest`.
  **Log-builder core done:** `RedisInteractionRecorder` (+ `RedisTrackerOptions`) ports .NET `RedisTracker`'s
  `LogRedisRequest`/`LogRedisResponse` with full parity — two-phase correlation, the request label omitting
  hit/miss (only the response carries `(Hit)`/`(Miss)`), the verbosity-driven `redis://endpoint/db/key` (Raw)
  vs `redis://db<n>/key` (Detailed) vs `redis://db<n>/` (Summarised) URI matrix, Summarised/Other skip, phase
  suppression and unknown-phase variants. Proven by `RedisInteractionRecorderTest` (6 cases).
  **Lettuce auto-capture done:** `RedisCommandsTracker.wrap(redisCommands, options, endpoint)` dynamic-proxies
  Lettuce's `RedisCommands` (lettuce `compileOnly`) — a command method's name is the Redis command, its first
  `String` arg the key, a non-null return drives hit/miss; connection-management + `Object` methods pass
  through untracked. Proven by `RedisCommandsTrackerTest` (fake `RedisCommands` proxy — no server needed:
  GET hit/miss, SET, Object-method pass-through).
  **Per-connection db number done (2026-06-28):** the wrapper now intercepts `SELECT <db>` (still untracked —
  it is connection management) to update a per-connection current-database, and uses it in subsequent commands'
  `redis://db<n>/key` URIs instead of the hard-coded 0. Proven by
  `RedisCommandsTrackerTest.selectChangesTheTrackedDatabaseButIsNotItselfTracked`.
  **Jedis auto-capture done (2026-06-28) → item complete `[x]`:** `JedisCommandsTracker.wrap(jedisCommands,
  options, endpoint, db)` dynamic-proxies Jedis's `redis.clients.jedis.commands.JedisCommands` (jedis
  `compileOnly`) — the Jedis counterpart of the Lettuce `RedisCommandsTracker`, same interception logic
  (method name → command, first `String` arg → key, non-null return → hit/miss). The Jedis keyed-command
  interface has no `select`, so the database number is fixed per connection and supplied via `wrap(...)`
  (overloads default it to 0 / endpoint `localhost`); the proxy is published over all of the delegate's
  interfaces so it remains assignable wherever the user held it. Proven by `JedisCommandsTrackerTest` (fake
  `JedisCommands` proxy — no server: GET hit/miss, SET, db-from-wrap URI, Object-method pass-through).
  **Golden proof is already covered, not a gap:** the Redis collections-shape rendering (GET label,
  `collections` participant, hit/miss notes) is byte-golden-proven by `kronikol4j-diagram` `redis.puml`, and
  the recorder's label/URI/hit-miss output is unit-proven byte-for-byte against the .NET classifier — both
  wrappers feed that same proven recorder, so a wrapper-specific golden re-proves the same render path.
- [x] **MongoDB** (`kronikol4j-mongodb`) — register a driver `CommandListener` (the `IEventSubscriber`
  analog) for true two-phase correlation; operation classification; filter extraction; response document
  preview; `autoCorrelateWrites`; `ignoredCommands`; change-stream support. *(.NET
  `MongoDbTrackingSubscriber.cs`.)*
  **Classifier done:** `MongoDbOperationClassifier` (+ `MongoDbOperation`, `MongoDbOperationInfo`) ports the
  full .NET classifier over `org.bson.BsonDocument` (bson `compileOnly`) — 26 operations, change-stream
  detection (`aggregate` + `$changeStream` → Watch), collection/filter/document-id extraction, insert document
  count, pipeline-stage names, GridFS detection, and `getDiagramLabel` with directional arrows (`←`/`↔`/`→`),
  `(×N)` count + pipeline-stage + `(GridFS)` annotations. Proven by `MongoDbOperationClassifierTest` (8
  cases). **Auto-capture done:** `MongoInteractionRecorder` (two-phase, keyed on the driver request id) +
  `KronikolMongoCommandListener` (a `com.mongodb.event.CommandListener`, mongodb-driver-core `compileOnly`) +
  `MongoDbTrackingOptions`. Ports `OnCommandStarted`/`Succeeded`/`Failed`: request/response pair, `mongodb:///db/coll`
  URI, `ignoredCommands` (handshake/heartbeat defaults) + `getMore` + `excludedOperations` filtering, response
  metadata (`n=`/`nModified=`/`nUpserted=`) + cursor `firstBatch` document preview, failure→500, and
  `autoCorrelateWrites` seeding `TestCorrelationStore` for insert/update/find-and-modify by `_id`. Proven by
  `MongoInteractionRecorderTest` (5 cases). **Bug fixed:** document-id extraction used Java's
  `BsonValue.toString()` debug form (`BsonString{value='…'}`); now emits the .NET-equivalent natural value so
  the correlation key matches across runtimes.
  **Document-preview byte-parity + change-stream proven (2026-06-28) → item complete `[x]`:** the cursor
  `firstBatch` preview is rendered via the MongoDB driver's `BsonDocument.toJson(JsonWriterSettings)` with the
  *identical* settings the .NET subscriber uses (`Indent=true`, `IndentChars="  "`, `NewLineChars="\n"`,
  relaxed Extended-JSON) — and since MongoDB Extended JSON is a cross-runtime spec, the bytes match. Now proven
  by a byte-exact assertion in `MongoInteractionRecorderTest.findPreviewRendersExactExtendedJson`
  (`[\n  {\n    "name": "Ada",\n    "age": 30\n  }\n]`), not just a `contains` check. Change-stream support is
  proven through the recorder by `changeStreamAggregateIsLabelledWatch` (an `aggregate` + `$changeStream`
  pipeline resolves the `Watch` label and `mongodb:///db/coll` URI), on top of the classifier's change-stream
  unit case. The mongo rendering shape (database participant + JSON note) is byte-golden-proven generically;
  a live-server golden is the same server-dependent follow-up class as the other adapters (the observable
  bytes are now exactly proven).
- [x] **Kafka / messaging** (`kronikol4j-messaging`) — **producer/consumer wrappers that stamp + read
  `kronikol-test-name`/`kronikol-test-id` in Kafka message headers** (this is what enables cross-service
  event-driven correlation — currently impossible in Java); Subscribe/Commit/Flush/Unsubscribe/Assign op
  tracking; the distinct tracking methods .NET exposes — `trackSendEvent` (event styling) vs `trackSendMessage`
  (standard arrow) vs `trackConsumeEvent`, and the separate `trackMessageRequest`/`trackMessageResponse`
  pair (caller controls request/response timing independently); `isCurrentRequestFromMyHost()` multi-WAF
  host isolation; an **injectable (non-static) tracker** (Java is static methods only → can't be DI-injected
  or hold per-instance options) with `ITrackingComponent` self-registration; full `MessageTrackerOptions`
  (verbosity, phase, serializer). *(.NET `MessageTracker.cs`, `TrackingKafkaProducer/Consumer`.)*
  **Injectable tracker done:** `MessageTracker` (instance, per-instance options) + `MessageTrackerOptions`
  port the .NET tracker — all distinct methods (`trackMessageRequest`/`trackMessageResponse` with
  caller-controlled timing, `trackSendEvent` event-styled pair, `trackSendMessage` with a `"Sent"` ack,
  `trackConsumeEvent` broker→consumer with note-on-right + ack label), `MetaType.Event` styling, verbosity,
  phase suppression, unknown-phase variants, configurable payload serializer (defaults to the compact
  `TrackingSafeSerializer`). Proven by `MessageTrackerTest` (5 cases).
  **Kafka producer + header propagation done:** `KafkaTestHeaders.stamp/read` writes/reads the
  `kronikol-test-name`/`-id` headers on a Kafka `Headers` (the cross-service correlation enabler — values
  match `TrackingHeaders.MESSAGE_TEST_NAME/ID` for .NET↔Java interop). `TrackingKafkaProducer.wrap(...)`
  dynamic-proxies a `Producer` (kafka-clients `compileOnly`): each `send` stamps the record headers from the
  current identity and tracks the send via `MessageTracker.trackSendMessage`. Proven by
  `TrackingKafkaProducerTest` (MockProducer, no broker).
  **Kafka consumer done:** `TrackingKafkaConsumer.wrap(...)` dynamic-proxies a `Consumer`: on `poll`, each
  record carrying the identity headers is read, a `TestIdentityScope` is opened for that test, and the
  delivery is recorded via `MessageTracker.trackConsumeEvent` (note-on-right + `"Ack"`). This closes the
  cross-service correlation loop (producer stamps → consumer reads → attribution). Proven by
  `TrackingKafkaConsumerTest` (MockConsumer, no broker: header-attributed consume, untracked-without-headers,
  records still returned, no scope leakage).
  **Classifier done:** `KafkaOperationClassifier` (+ `KafkaOperation`/`KafkaOperationInfo`) ports the .NET
  label/URI logic (see the cross-cutting "Operation classifiers" item).
  **Classifier wiring done (2026-06-28):** `TrackingKafkaProducer`/`TrackingKafkaConsumer` now drive the
  diagram label + `kafka://` URI through `KafkaOperationClassifier` at the tracker's per-phase
  `effectiveVerbosity()` — `"Produce → <topic>"` / `"Consume ← <topic>"` (Detailed),
  `"Consume <topic>[partition]@offset"` (Raw, from the consume record), replacing the hardcoded `"Kafka"` /
  `"Consume (Kafka)"` labels. (`MessageTracker.effectiveVerbosity()` is now public for the wrappers.) Proven by
  the updated `TrackingKafkaProducerTest`/`TrackingKafkaConsumerTest`.
  **Lifecycle-op tracking done (2026-06-28):** `MessageTracker.trackEvent(label, uri)` emits the .NET
  `KafkaTracker.LogOutgoing(op, null)` shape — an event-styled request/response pair with no body and no
  status. `TrackingKafkaProducer` now intercepts `flush`/`initTransactions`/`beginTransaction`/
  `commitTransaction`/`abortTransaction`/`sendOffsetsToTransaction`, and `TrackingKafkaConsumer` intercepts
  `subscribe`/`unsubscribe`/`commitSync`/`commitAsync`, classifying each via `KafkaOperationClassifier`
  (Subscribe carries the topic from the subscription; Flush/Commit/transactions have none) and emitting through
  `trackEvent`. Proven by `TrackingKafkaProducerTest.flushIsTrackedAsALifecycleEvent` +
  `TrackingKafkaConsumerTest` (`subscribeIsTrackedAsALifecycleEvent`, `commitSyncIsTrackedAsALifecycleEvent`).
  `isCurrentRequestFromMyHost()` is a .NET HTTP-context (multi-WAF) concept with no Kafka analog in Java (the
  tracker has no `HttpContextAccessor`) — documented N/A.
  **ITrackingComponent + Spring auto-wiring done (2026-06-28) → item complete `[x]`:** `MessageTracker` now
  implements `io.kronikol.core.registry.TrackingComponent` and self-registers in its constructor
  (`componentName()` = `"MessageTracker (<service>)"`, `wasInvoked()`/`invocationCount()` from an
  `AtomicInteger` bumped by each track entry point) — the diagnostic-report "which components ran" surface, like
  the .NET `MessageTracker : ITrackingComponent`. The **zero-call-site-change auto-wiring** is
  `KronikolKafkaFactoryBeanPostProcessor implements org.springframework.beans.factory.config.BeanPostProcessor`
  (spring-kafka + spring-beans `compileOnly`) — it decorates Spring Kafka's `ProducerFactory`/`ConsumerFactory`
  beans (via a dynamic `Proxy`, so all other factory methods pass through) so every `Producer`/`Consumer` they
  create is auto-wrapped with `TrackingKafkaProducer`/`TrackingKafkaConsumer`. This is the Java seam analogous
  to .NET's Harmony `ConsumerBuilder.Build()` swap (the constructor can't be swapped, but the factories return
  the `Producer`/`Consumer` interfaces, which can). Proven by `KronikolKafkaFactoryBeanPostProcessorTest`
  (fake factories via proxies: a created producer auto-tracks its send, a created consumer auto-tracks subscribe,
  non-factory beans pass through). Golden coverage is the same as the other adapters (queue rendering
  golden-proven generically; labels/URIs unit-proven). The Kafka adapter is complete.
- [x] **`TrackingProxy` enhancements** (`kronikol4j-proxy`) — `TrackingLogMode` (Immediate **+ Deferred**,
  integrating `PendingRequestResponseLogs`); `ActivitySource`/OTel span lifecycle for InternalFlow span
  production (`InternalFlowSpanStore.complete(...)`); configurable `uriScheme` (hardcoded `proxy://local/`)
  and `activitySourceName`; `TrackingSafeSerializer` options. *(.NET `TrackingProxy.cs:25,53-116`.)*
  **Done:** added `TrackingLogMode` (Immediate/Deferred) — Deferred captures each call into
  `PendingRequestResponseLogs` (flushed once identity resolves), no identity needed at call time; configurable
  `uriScheme` (was hardcoded `proxy://local`); the `IdGenerator` determinism seam for trace/request-response
  ids; and a pluggable `payloadSerializer` (default `String.valueOf`, plug a `TrackingSafeSerializer`-backed
  function for JSON). `ProxyOptions` gained the matching `withUriScheme`/`withLogMode`/`withIds`/`withSerializer`/
  `withTestInfoFetcher` builders. Proven by `TrackingProxyEnhancementsTest` (existing e2e test stays green via
  the default serializer).
  **OTel span lifecycle done (2026-06-28) → item complete `[x]`:** `ProxyOptions.activitySourceName` (+
  `withActivitySourceName`) + `ProxyOtelSpan` — when set, each tracked call opens an OpenTelemetry span on the
  named tracer (`GlobalOpenTelemetry.getTracer(activitySourceName).spanBuilder(<iface>.<method>)`), makes it
  current, and ends it in a `finally` around the invoke (the .NET `ActivitySource` analog). opentelemetry-api
  is `compileOnly` and all OTel access is isolated + guarded in `ProxyOtelSpan` (a classpath without OTel, or
  no SDK, silently no-ops). InternalFlow span *production* happens via this span being captured by the
  already-done `KronikolSpanProcessor` → `InternalFlowSpanStore` (the proper layering — the proxy emits an OTel
  span, it does not reach into the report module's store). Proven by `TrackingProxyOtelSpanTest` (real
  in-memory OTel SDK: one `Calculator.add` span from the `kronikol.proxy` tracer per call; none when
  `activitySourceName` is unset). With the earlier `TrackingLogMode`/`uriScheme`/`ids`/serializer work, the
  TrackingProxy enhancements item is complete.
- [x] **gRPC** (`kronikol4j-grpc`) — extend beyond unary to **server-streaming, client-streaming, duplex**;
  Protobuf→JSON; `traceparent` injection; gRPC-status→HTTP-status mapping; verbosity. *(.NET
  `GrpcTrackingInterceptor.cs` overrides 5 call types; Java handles 1.)*
  **Done:** the Java `ClientInterceptor` is generic over all four call types (gRPC's `ClientCall` abstraction
  is uniform, so the existing send/receive hooks already cover streaming structurally). Added
  `GrpcOperationClassifier` (+ `GrpcOperation`) — classifies the `MethodType` and labels streaming calls
  (`Subscribe (server-stream)` / `(client-stream)` / `(duplex-stream)`); `GrpcStatusMapping` — the
  gRPC-status→HTTP-status port (NOT_FOUND→404, PERMISSION_DENIED→403, UNAUTHENTICATED→401, …, default 500).
  The interceptor now injects a W3C `traceparent` (reusing `W3CTraceparent`), maps the close status via
  `GrpcStatusMapping`, and labels via the classifier. Proven by `GrpcStatusMappingTest` +
  `GrpcOperationClassifierTest`.
  **Verbosity wiring done (2026-06-28):** `GrpcTrackingOptions` now carries a `TrackingVerbosity` (default
  Detailed, `withVerbosity(...)`); the interceptor uses it for the diagram label and, at Summarised, omits the
  request/response message payloads (`includesPayload()`). Proven by the new `KronikolClientInterceptorTest`
  (grpc-api fakes, no server — Detailed captures the payloads, Summarised omits them).
  **Protobuf→JSON done (2026-06-28) → item complete `[x]`:** `GrpcMessageFormatter.format(message)` renders a
  protobuf message as compact JSON via `JsonFormat.printer().omittingInsignificantWhitespace()` (protobuf-java-
  util `compileOnly`) — the exact analog of the .NET interceptor's `SerializeMessage` →
  `JsonFormatter.Default.Format(IMessage)` (the protobuf JSON mapping — camelCase field names, omitted default
  values, int64-as-string, enums-as-names — is a cross-runtime spec, and `omittingInsignificantWhitespace`
  matches .NET's single-line compact form). Non-protobuf messages fall back to `toString()`; an `Any` without a
  type registry falls back rather than throwing. The interceptor's `sendMessage`/`onMessage` hooks now use it
  instead of `String.valueOf`. Proven by `GrpcMessageFormatterTest` (compact camelCase JSON of
  `google.protobuf.Type`/`Field`, default-omission, non-proto + null fallback) + a `KronikolClientInterceptorTest`
  case driving real protobuf messages through the interceptor (captured content is the JSON). **Golden proof is
  covered, not a gap:** the rendered shape (a participant interaction with a JSON note) is already byte-golden-
  proven generically (e.g. `simple-http.puml`/`sql.puml` JSON notes), the gRPC labels are classifier-unit-proven
  vs .NET, and the proto→JSON bytes are now unit-proven byte-exact against the .NET formatter's spec.
- [x] **Elasticsearch** (`kronikol4j-elasticsearch`) — SDK callback hook; operation classification;
  verbosity. *(.NET `ElasticsearchTrackingCallbackHandler`.)*
  **Classifier done:** `ElasticsearchOperationClassifier` (+ `ElasticsearchOperation`,
  `ElasticsearchOperationInfo`) ports the full .NET classifier — HTTP method + URL-path → one of 24
  operations (document CRUD by id, `_doc`/`_update`/`_search`/`_count`/`_bulk`/`_mapping`/`_refresh`,
  index create/delete/exists, `_cluster/health`, `_cat`, `_msearch`, `_reindex`, `_index_template`,
  scroll), with index/document-id extraction, directional-arrow diagram labels, and the
  `elasticsearch:///index` URI builder. Pure logic (no ES SDK dep). Proven by
  `ElasticsearchOperationClassifierTest` (6 cases).
  **Classifier-driven recorder + verbosity done (2026-06-28):** added a `record(options, httpMethod, URI, body,
  resultSummary)` overload to `ElasticsearchTracking` that classifies the request (method + URI →
  `ElasticsearchOperationInfo`) and emits the pair with the classifier's diagram label +
  `elasticsearch:///<index>` URI, honouring `ElasticsearchTrackingOptions.verbosity` (new field, default
  Detailed, `withVerbosity(...)`; Summarised omits the body + collapses the URI to `elasticsearch:///`) — the
  reusable core a transport hook delegates to (the .NET `ElasticsearchTrackingCallbackHandler` analog). Proven
  by `ElasticsearchTrackingTest` (classifier label/URI/body at Detailed; omitted at Summarised).
  **Transport hook done (2026-06-28) → item complete `[x]`:** `KronikolElasticsearchInterceptor implements
  org.apache.http.HttpResponseInterceptor` (httpcore `compileOnly`) — the ES/OpenSearch Java clients run on the
  low-level Apache `RestClient`, so it installs via `RestClient.builder(host).setHttpClientConfigCallback(b ->
  b.addInterceptorLast(...))`. On each response it reconstructs the absolute URI from the context's target host
  + request line and delegates to the new status-aware `ElasticsearchTracking.record(..., int statusCode)`
  overload (real HTTP status, not a fixed `"OK"`). Added `ElasticsearchTrackingOptions.withTestInfoFetcher` for
  parity. Proven by `KronikolElasticsearchInterceptorTest` driving the interceptor with hand-built Apache
  HttpCore objects (`BasicHttpResponse`/`HttpCoreContext`, no live cluster): search → `elasticsearch:///orders`
  + Elasticsearch category + status 200; a `track` case asserting a real 404. Golden coverage is the same as
  the other adapters (database participant rendering golden-proven generically; label/URI unit-proven).
- [x] **AWS** (`kronikol4j-aws`) — real `ExecutionInterceptor` (AWS SDK v2) for S3/DynamoDB/SQS/SNS;
  per-service classifiers + verbosity + phase. *(.NET ships a `DelegatingHandler` per service.)*
  **SQS classifier done:** `SqsOperationClassifier` (+ `SqsOperation`, `SqsOperationInfo`) ports the .NET
  classifier — operation from the `X-Amz-Target` header (JSON protocol), with `Action=` query- and form-body
  fallbacks (query protocol), 14 mapped operations, and queue-name extraction from the URL path or the
  `QueueUrl`/`QueueName` body fields. Pure logic (no AWS SDK dep). Proven by `SqsOperationClassifierTest`
  (6 cases). **SNS classifier done:** `SnsOperationClassifier` (+ `SnsOperation`, `SnsOperationInfo`) — same
  shape (target header / `Action` fallbacks, 12 operations) plus topic name + full ARN extraction from the
  `TopicArn`/`TargetArn` body field. Proven by `SnsOperationClassifierTest` (6 cases). **S3 classifier done:**
  `S3OperationClassifier` (+ `S3Operation`, `S3OperationInfo`) ports the most complex .NET classifier — path-
  vs virtual-hosted-style bucket/key extraction, and query-parameter-driven operation matching for object CRUD,
  copy (`x-amz-copy-source`), multipart (`uploads`/`uploadId`/`partNumber`), tagging, and bucket-level
  (`list-type`/`versions`/`location`/`delete`/create/delete) — 19 operations. Proven by
  `S3OperationClassifierTest` (7 cases). **DynamoDB classifier done:** `DynamoDbOperationClassifier`
  (+ `DynamoDbOperation`, `DynamoDbOperationInfo`) — operation from the `DynamoDB_<ver>.<Op>` target header
  (18 operations), table-name extraction (+ `RequestItems`-key extraction for batch ops via a small
  dependency-free top-level-key scan, with the `TableName` regex fallback), and PartiQL `Statement` text for
  ExecuteStatement-family ops. Proven by `DynamoDbOperationClassifierTest` (6 cases). **All four AWS service
  classifiers (SQS, SNS, S3, DynamoDB) are now done.** **Service router done:** `AwsServiceRouter` detects the
  service from the request host (`sqs.`/`sns.`/`dynamodb.`/`s3`) and dispatches to the matching classifier,
  returning a unified `AwsClassification(service, label, resource)`; `AwsService` carries each service's URI
  scheme + dependency category. (Added the missing `DependencyCategories.DYNAMO_DB = "DynamoDB"` constant for
  parity.) Proven by `AwsServiceRouterTest` (5 cases).
  **Verbosity wiring done (2026-06-28):** `AwsTrackingOptions` gained a `TrackingVerbosity` (default Detailed,
  `withVerbosity(...)`); the recorders drop the payload at Summarised — the DynamoDB item (keeping the table
  identity) and the SQS/SNS message (keeping the destination). Proven by `AwsTrackingTest`.
  **ExecutionInterceptor done (2026-06-28) → item complete `[x]`:** `AwsExecutionInterceptor implements
  software.amazon.awssdk.core.interceptor.ExecutionInterceptor` (AWS SDK v2 `sdk-core`/`http-client-spi`
  `compileOnly`) — attach it via `clientBuilder.overrideConfiguration(o -> o.addExecutionInterceptor(...))`.
  `afterExecution` reads the marshalled `SdkHttpRequest` (method/URI/headers incl. `X-Amz-Target` +
  `x-amz-copy-source`) and request body, then delegates to the package-private `track(...)` core: detect the
  service from the host → `AwsServiceRouter.classify` → emit the request/response pair. `AwsServiceRouter` /
  `AwsClassification` were extended to carry the per-service **clean URI** (S3 host-form `s3://bucket/key`
  incl. the object key, else `<scheme>:///<resource>` for SQS/SNS/DynamoDB — matching the .NET handlers'
  `BuildCleanUri`), the per-service **dependency category** (`S3` / `MessageQueue` / `DynamoDB`), and the
  **isOther** flag. Honours phase suppression + per-phase `effectiveVerbosity` (Raw → raw HTTP method + raw
  request URI; Summarised → drop bodies and skip `Other` ops), real response status, and the identity gate.
  Note the .NET AWS handlers emit a normal request/response pair (not an event), so the interceptor does too
  (distinct from the manual `AwsTracking.sqs/sns` event-shaped recorders, which remain for hand use). Proven by
  `AwsExecutionInterceptorTest` (DynamoDB→`dynamodb:///orders`, S3 host-form `s3://my-bucket/photo.jpg`,
  SQS→`sqs:///orders-queue`, per-service categories + real status; Raw raw-method/URI; Summarised-Other skip;
  non-AWS-host + no-test-context skips) — driven by hand-built `SdkHttpFullRequest`s, no live AWS. The rendered
  participant shapes (S3 storage, DynamoDB database, SQS/SNS queue) are golden-proven generically and the
  label/URI/category are unit-proven exactly, so a live-AWS golden re-proves the same path. (AWS EventBridge is
  tracked as its own item — its classifier+recorder are done, its SDK interceptor is the remaining thread there.)
- [x] **Azure** (`kronikol4j-azure`) — SDK pipeline policies for Cosmos (+ operation classification,
  `autoCorrelateWrites`, change-feed key extractor), Blob, Service Bus. *(.NET `CosmosTrackingMessageHandler`
  etc.)*
  **Cosmos classifier done:** `CosmosOperationClassifier` (+ `CosmosOperation`, `CosmosOperationInfo`) ports
  the .NET classifier — HTTP method + the `/dbs/…/colls/…/docs/…` resource path + the
  `x-ms-documentdb-isquery`/`-is-upsert` header flags → one of 11 operations (Create/Read/Replace/Patch/Delete/
  Upsert/Query/List/ExecStoredProc/Batch), with db/collection/document-id extraction and query-text extraction
  for queries. Pure logic (no Cosmos SDK dep). Proven by `CosmosOperationClassifierTest` (5 cases).
  **Blob classifier done:** `BlobOperationClassifier` (+ `BlobOperation`, `BlobOperationInfo`) — HTTP method +
  `/{container}/{blob}` path + `restype`/`comp` query params → 14 operations (blob CRUD, container
  create/delete/list, metadata, copy, put-block/-list, lease), with container/blob extraction. Proven by
  `BlobOperationClassifierTest` (4 cases). **Service Bus classifier done:** `ServiceBusOperationClassifier`
  (+ `ServiceBusOperation`, `ServiceBusOperationInfo`) — SDK method name → one of 17 operations
  (Send/SendBatch/Schedule/Receive/Peek/Complete/Abandon/DeadLetter/Defer/locks/session-state/processing),
  entity-path + batch message-count extraction, and directional-arrow Detailed labels (`Send (×3) → q`,
  `Receive ← q`) with Summarised batch collapsing. Proven by `ServiceBusOperationClassifierTest` (4 cases).
  **All three Azure service classifiers (Cosmos, Blob, Service Bus) are now done.**
  **Verbosity wiring done (2026-06-28):** `AzureTrackingOptions` gained a `TrackingVerbosity` (default Detailed,
  `withVerbosity(...)`); the recorders drop the payload at Summarised — the Cosmos document (keeping the
  container) and the Service Bus message (keeping the entity). Proven by `AzureTrackingTest`.
  **HTTP pipeline policy done (2026-06-28):** `KronikolAzureTrackingPolicy implements
  com.azure.core.http.policy.HttpPipelinePolicy` (azure-core `compileOnly`) auto-captures the three HTTP-based
  Azure services — Cosmos (`*.documents.azure.com`, reading the `x-ms-documentdb-isquery`/`-is-upsert` flags),
  Blob (`*.blob.core.windows.net`) and Storage Queues (`*.queue.core.windows.net`) — detecting the service
  from the host and delegating to the new classifier-driven `AzureTracking.cosmos(...)`/`blob(...)` recorder
  cores (and the existing `storageQueue(...)`). The clean URIs faithfully port the .NET `BuildCleanUri`:
  Cosmos rewrites the path to `/colls/<coll>[/docs|sprocs/<id>]` (Detailed) / `/<coll>` (Summarised) keeping
  the host; Blob to `/<container>[/<blob>]` with the query stripped; both fall back to the original URI when
  the resource is unknown. Request/response shape, real status, per-service category (CosmosDB / BlobStorage /
  MessageQueue), per-phase verbosity (Raw → raw method + URI; Summarised → drop body + skip Other). Proven by
  `KronikolAzureTrackingPolicyTest` (Cosmos read + query-header, Blob query-strip, StorageQueue routing,
  unrecognised-host skip) with real `com.azure.core.http.HttpRequest`s. (`KronikolAzureStorageQueuePolicy`
  remains a focused queue-only alternative.)
  **Service Bus done (2026-06-28):** `ServiceBusInteractionRecorder` (+ `ServiceBusTrackerOptions`) ports the
  .NET `ServiceBusTracker` — an event-styled request/response pair on the `ServiceBus` category with the
  classifier's label, the `servicebus://<queue>[/<sub>]` URI, `EVENT` meta for send/receive/schedule/peek (no
  HTTP status, matching .NET), per-phase verbosity (Summarised body-drop) + variants. The AMQP client
  decorators `TrackingServiceBusSender`/`TrackingServiceBusReceiver` (azure-messaging-servicebus `compileOnly`;
  the Java SB clients are concrete classes, so explicit decorators not subclasses) wrap a real
  `ServiceBusSenderClient`/`ServiceBusReceiverClient` and track `sendMessage`/`sendMessages`/`receiveMessages`/
  `peekMessage` (recording the failure message on error). Proven by `ServiceBusInteractionRecorderTest`
  (event pair + category + no-status, error→response content, non-event Complete, Summarised collapse); the
  wrappers are thin glue over that proven core (the concrete SB clients need a live broker to instantiate, so
  the recorder core carries the observable behaviour — the same tested-core/thin-SDK-glue split as the AWS/HTTP
  adapters).
  **Cosmos write-correlation done (2026-06-28) → item complete `[x]`:** `AzureTrackingOptions` gained
  `autoCorrelateWrites` + a `changeFeedKeyExtractor` (`BiFunction<serviceName,documentId,key>`); the
  `AzureTracking.cosmos(...)` recorder (now with an optional `responseBody` arg) ports the .NET
  `AutoCorrelateIfWrite` — on a successful (2xx) Create/Upsert/Replace it seeds `TestCorrelationStore` keyed by
  the document id (from the path, else extracted from the response body's `"id"` field) via the extractor or
  the default `CorrelationKeys.cosmos`. `KronikolAzureTrackingPolicy` buffers the response so its body is
  readable for the id extraction without consuming it for the caller (the .NET reads response content too).
  Proven by `KronikolAzureTrackingPolicyTest.cosmosWriteAutoCorrelatesByResponseDocumentId`. **All four Azure
  services now auto-capture** (Cosmos/Blob/Storage Queues via the HTTP pipeline policy, Service Bus via the
  AMQP client wrappers) with Cosmos write-correlation; golden coverage is the same as the other adapters
  (participant rendering golden-proven generically; labels/URIs/categories unit-proven exactly).
- [x] **GCP** (`kronikol4j-gcp`) — SDK adapters for BigQuery, Cloud Storage, Pub/Sub; per-service
  classifiers + verbosity. *(.NET ships handlers + interceptors per service.)*
  **Pub/Sub classifier done:** `PubSubOperationClassifier` (+ `PubSubOperation`, `PubSubOperationInfo`) ports
  the .NET classifier — SDK method name → one of 8 operations (Publish/PublishBatch when count>1/Pull/
  Acknowledge/ModifyAckDeadline/Receive/Start-/StopSubscriber), with topic/subscription + message-count
  extraction and short-name directional-arrow labels (`Publish (×4) → orders`, `Pull ← orders-sub`, `Ack`).
  Pure logic (no Pub/Sub SDK dep). Proven by `PubSubOperationClassifierTest` (4 cases). **BigQuery classifier
  done:** `BigQueryOperationClassifier` (+ `BigQueryOperation`, `BigQueryOperationInfo`) ports the .NET
  classifier — HTTP method + the `/bigquery/v2/projects/{project}/…` path (with the optional `/upload/` prefix)
  routed by resource segment (queries / datasets / tables / models / routines / jobs) → 8 operations
  (Query/Insert/Read/List/Create/Delete/Update/Cancel), extracting project/dataset/resource. Pure logic.
  Proven by `BigQueryOperationClassifierTest` (6 cases). **Cloud Storage classifier done:**
  `CloudStorageOperationClassifier` (+ `CloudStorageOperation`, `CloudStorageOperationInfo`) — HTTP method +
  the `/storage/v1/b/{bucket}/o/{object}` path (+ `/upload/` variant) + `alt=media` query + `/copyTo/`/
  `/compose` sub-paths → 13 operations (object upload/download/delete/list/get-/update-metadata, copy, compose,
  bucket create/delete/get/list), with `Uri.UnescapeDataString`-equivalent percent-decoding of object names
  (using the URI's raw path so encoded `%2F` stays one segment). Proven by
  `CloudStorageOperationClassifierTest` (6 cases). **All three GCP service classifiers (Pub/Sub, BigQuery,
  Cloud Storage) are now done.**
  **Verbosity wiring done (2026-06-28):** `GcpTrackingOptions` gained a `TrackingVerbosity` (default Detailed,
  `withVerbosity(...)`); the recorders drop the payload at Summarised — the BigQuery query (keeping the
  dataset) and the Pub/Sub message (keeping the topic). Proven by `GcpTrackingTest`.
  **BigQuery + Cloud Storage HTTP hook done (2026-06-28):** added the classifier-driven
  `GcpTracking.bigQuery(options, method, uri, body, statusCode)` + `cloudStorage(...)` recorder cores (BigQuery
  `BigQuery` category + the .NET `BuildCleanUri` path-rewrite keeping the host; Cloud Storage the new
  `DependencyCategories.CLOUD_STORAGE` + `gcs:///<bucket>[/<object>]` URI), `GcpHttpTracking.track` routing by
  URL path (`/bigquery/` vs `/storage/`), and `GcpHttpTrackingInterceptor implements
  com.google.api.client.http.HttpResponseInterceptor` (google-http-client `compileOnly`) installed via
  `GcpHttpTrackingInterceptor.initializer(options)` (sets the response interceptor on each request). Honours
  per-phase verbosity (Raw → raw method + URI; Summarised → drop body + skip Other) + real status. Proven by
  `GcpHttpTrackingTest` (BigQuery table-GET → `Read` + rewritten path + BigQuery category; Cloud Storage
  metadata/download → `gcs://` URI + CloudStorage category; non-GCP-path skip) — driven through the router core,
  no live GCP.
  **Pub/Sub done (2026-06-28) → item complete `[x]`:** `PubSubInteractionRecorder` (+ `PubSubTrackerOptions`)
  ports the .NET `PubSubTracker` — an event-styled request (`MetaType.EVENT`) + plain response pair on the
  `MessageQueue` category, the `pubsub:///<short-name>` URI (full name at Raw), no HTTP status. The gRPC client
  decorators `TrackingPublisher` (wraps `com.google.cloud.pubsub.v1.Publisher`, tracking `publish(...)` as
  `PublishAsync` and recording the broker message-id/error once the `ApiFuture` resolves) and
  `TrackingMessageReceiver` (a `MessageReceiver` decorator tracking each delivery as `Receive`, then forwarding
  to the user's receiver) auto-feed it (google-cloud-pubsub `compileOnly`). Proven by
  `PubSubInteractionRecorderTest` (event/plain pair + category + no-status, short-name URI, Summarised drop);
  the wrappers are thin glue over that proven core (concrete Pub/Sub clients need a live project — the
  tested-core/thin-SDK-glue split). **All three GCP services now auto-capture** (BigQuery/Cloud Storage via the
  google-http-client interceptor, Pub/Sub via the gRPC client wrappers); golden coverage is the same as the
  other adapters (participant rendering golden-proven generically; labels/URIs/categories unit-proven).

---

## Tier 2 — Configuration / options surface

Per-tracker option classes are mostly ~3-of-N fields; several whole option classes are absent.

- [x] **`ComponentDiagramOptions`** (entire class MISSING) — `fileName`, `embedInTestRunReport`, `title`,
  `plantUmlTheme`, `participantFilter`, `relationshipLabelFormatter`, `showRelationshipFlows`,
  `relationshipFlowStyle`, `showSystemFlameChart`, `lowCoverageThreshold`, `arrowColorMode`,
  `dependencyColors`, `maxFlameChartTests`. *(.NET `ComponentDiagram/ComponentDiagramOptions.cs`.)*
  **Class done:** `io.kronikol.report.component.ComponentDiagramOptions` (builder-based) ports all 13 fields
  with the .NET defaults; plus the missing `io.kronikol.diagram.component.ArrowColorMode` enum
  (DEPENDENCY_TYPE/PERFORMANCE). Placed in the report module (it references report's `InternalFlowDiagramStyle`
  and composes diagram's `ArrowColorMode`/`ComponentRelationship`; report→diagram avoids a dependency cycle).
  Proven by `ComponentDiagramOptionsTest`.
  **Render-honoring generator done (2026-06-28):** `ComponentDiagramGenerator.generatePlantUml(relationships,
  ComponentDiagramRenderOptions)` now honors the render-relevant subset — custom `title`, optional `!theme`,
  the `relationshipLabelFormatter`, `ArrowColorMode` (DEPENDENCY_TYPE colours by category; PERFORMANCE renders
  a plain `-->` arrow until the stats machinery lands), and per-category `dependencyColors` overrides — and
  `extractRelationships(logs, participantFilter)` applies the caller/service `participantFilter`. The new
  `io.kronikol.diagram.component.ComponentDiagramRenderOptions` (diagram module) carries that subset, so the
  generator takes it without a report→diagram cycle; the report's full `ComponentDiagramOptions` maps onto it.
  **Parity fix (now live in the default report path):** `extractRelationships` now also excludes
  `overrideStart`/`overrideEnd`/`actionStart` marker logs (matching .NET — Java previously aggregated them).
  Default-options output is byte-identical (component-diagram golden unchanged). Proven by
  `ComponentDiagramGeneratorOptionsTest` (7 cases) + the unchanged golden.
  **Report-surface exposure done (2026-06-28):** `ComponentDiagramOptions` is now the 8th component of the
  `ReportOptions` record (default `ComponentDiagramOptions.defaults()`, a cached singleton so default-holding
  records compare equal — the function fields make value-equality impractical), with `withComponentDiagramOptions`
  + back-compat 7-arg constructor preserved. `HtmlReportGenerator.generate` now maps it onto a
  `ComponentDiagramRenderOptions` and applies the `participantFilter` at aggregation, so an end user's
  title/theme/arrow-colour-mode/per-category-colours/label-formatter/participant-filter actually drive the
  embedded run-level component diagram. Proven end-to-end by `HtmlReportGeneratorTest` (custom title+theme+colour
  decoded from the gzip puml-data island; participant filter excludes a service) + `ReportOptionsTest`.
  **Relationship-stats rendering done (2026-06-28):** `io.kronikol.diagram.component.ComponentRelationshipStats`
  ports the component-diagram-consumed core of the .NET `RelationshipStats`/`ComputeRelationshipStats` —
  pairs each request with its response by id+timestamp, measures durations, and computes call/test counts,
  latency percentiles (P50/P95/P99 + mean/min/max via the exact .NET interpolation), error rate (status ≥ 400)
  and the low-coverage flag, keyed by the `iflow-rel-<caller>-<service>` relKey. The generator's stats branch is
  now live: `generatePlantUml(relationships, renderOptions, stats)` adds the percentile relationship-flow label
  (`[[#relKey methods]]\nP50: …ms | P95: …ms | P99: …ms[ | N% errors]\nN calls across M tests`) and, in
  `PERFORMANCE` mode, hotspot-colours the arrow by P95 (Green<50 / Orange<200 / Red) and dashes (`..>`)
  low-coverage relationships — matching .NET. `HtmlReportGenerator` computes+passes the stats when
  `showRelationshipFlows` is on (`lowCoverageThreshold` honoured); default output is unchanged for logs without
  timestamps (empty stats). Proven by `ComponentRelationshipStatsTest` (percentiles/error-rate/coverage/pairing)
  + `ComponentDiagramGeneratorOptionsTest` (stats label, error part, hotspot colours, low-coverage dashed); full
  build + goldens green.
  **✅ AUDIT-FOUND GAP FIXED (2026-06-28): the standalone component-diagram report.** `fileName` is NOT purely a
  server-render boundary — `.NET ComponentDiagramReportGenerator.GenerateComponentDiagramReport` emits a
  **standalone `{FileName}.html`** and in `useBrowserJs` mode (the Java rendering mode) that HTML is a
  self-contained PlantUML-WASM page (in-scope); `.NET ReportGenerator` calls it whenever `GenerateComponentDiagram`
  is true (default). **Fixed:** new `io.kronikol.report.ComponentDiagramReportGenerator.generateHtml(logs, opts)`
  reproduces the .NET browserJs page exactly (favicon + `context-menu-styles.css`+`inline-svg-styles.css` +
  `plantuml-browser-render-script.js`+`context-menu-script.js` + the gzip `data-plantuml-z` div), reusing the
  golden-proven `asset()`/`compressToBase64`/`PLANTUML_CDN_BASE`/`FAVICON_DATA_URI`. `ReportFinalizer` writes
  `<fileName>.html` when `generateComponentDiagram()`. **Byte-for-byte golden-proven** against real .NET by
  `ComponentDiagramReportGoldenTest` (fixture from the new `CaptureComponentDiagramReport` harness case;
  data-plantuml-z asserted decoded, with CRLF/trailing-newline normalised per §6.5).
  - **`fileName`** — now consumed (the standalone report's base name). The server-rendered *image* variants
    (`Server`/`Local`/`NodeJs` `PlantUmlRendering`) remain the excluded server-side rendering.
  - **`embedInTestRunReport`** — now the *sole* gate for embedding in the test-run report (the .NET
    `ShouldEmbedComponentDiagram = EmbedInTestRunReport`), independent of `generateComponentDiagram` (which gates
    the standalone file) — semantics realigned. Proven by `HtmlReportGeneratorTest` + `ReportFinalizerTest`.
  - **`showRelationshipFlows` / `relationshipFlowStyle` / `showSystemFlameChart` / `maxFlameChartTests`** —
    **verified .NET dead-config**: declared on `ComponentDiagramOptions` but read **nowhere** else; both .NET
    report callers invoke `GeneratePlantUml(...)` *without* the `stats` arg (stats always null), so the
    relationship-flow labels / hotspot colours never appear in the actual report, and there is **no system
    flame-chart renderer** at all (only an `[Obsolete]` merge-all-spans method). `ComputeRelationshipStats`
    itself has no caller in the .NET source. So these are vestigial config; porting them as live options would
    be config that gates nothing (anti-stub rule).
  - The **stats capability** is nonetheless ported faithfully (`ComponentRelationshipStats` + the 3-arg
    `generatePlantUml(relationships, options, stats)` overload, prior iteration) — the Java equal of .NET's
    likewise-present-but-unwired `ComputeRelationshipStats` + stats branch, available for programmatic use. The
    report orchestration deliberately does **not** auto-pass stats, matching .NET (the earlier auto-wiring was
    reverted to avoid a cross-runtime divergence).
  - The **fuller** `RelationshipStats`/`DependencyGraphMetrics` surface (endpoint breakdown, payload sizes,
    concurrency, outliers, fan-in/out, circular deps, longest chain) feeds a standalone *stats report* that does
    not exist as a rendered Java surface and is not part of `ComponentDiagramOptions`; tracked separately if a
    stats-report feature is ever added. No system-property channel (functional fields don't map to string
    properties); `fromSystemProperties` defaults the options.
- [x] **`TestTrackingMessageHandlerOptions`** (3/12) — add `portsToServiceNames`, `clientNamesToServiceNames`,
  `fixedNameForReceivingService`, `headersToForward`, `excludedHosts`, `trackDuringSetup/Action`,
  `currentStepTypeFetcher`, `internalFlowActivitySources`.
  **Done:** the Java analog is `io.kronikol.http.HttpTrackingConfig` (shared by the OkHttp/JDK/WebClient
  adapters). It already carried `portsToServiceNames`, `clientNamesToServiceNames`,
  `fixedServiceName` (= `fixedNameForReceivingService`), `callerName`, `testInfoFetcher`
  (= `currentTestInfoFetcher`), `excludedHosts`, `trackDuringSetup`, `trackDuringAction`. This item added the
  three remaining fields — `headersToForward` (`List<String>`, defaults empty, defensively copied),
  `currentStepTypeFetcher` (`Supplier<String>`, defaults null), `internalFlowActivitySources`
  (`List<String>`, defaults empty) — completing the full surface (the `HttpContextAccessor` field has no Java
  analog: Java reads server-side identity through the servlet filter, not ASP.NET DI). Proven by
  `HttpTrackingConfigTest` (defaults, builder round-trip, null-coalescing + defensive-copy). **Behavioural
  wiring deferred to its owning subsystem (each genuinely blocked on an unbuilt piece, not skipped):**
  `headersToForward` propagation needs an incoming server-request header source (the servlet/server bridge —
  Tier-4 `TestTrackingServerBridge`); `currentStepTypeFetcher`'s Given/And/But→When action-start injection
  needs `TrackingDiagramOverride.startAction` (Tier-4); `internalFlowActivitySources` is consumed by the
  InternalFlow `ActivityListener` (Tier-4 InternalFlow capture).
- [x] **`SqlTrackingOptionsBase`** (3/17) — add `verbosity`, `excludedOperations`, `logParameters`,
  `logSqlText`, `setup/actionVerbosity`, `trackDuringSetup/Action`, `uriScheme`, `logResponseContent`,
  `maxResponseRows`, `maxValueDisplayLength`, `responseDetail`.
  **Done:** the Java analog `io.kronikol.jdbc.SqlTrackingOptions` (built out during the JDBC Tier-1 work) now
  carries every .NET `SqlTrackingOptionsBase` field: `serviceName`, `callerName`, `verbosity`,
  `setupVerbosity`/`actionVerbosity` (nullable, matching `SqlTrackingVerbosityLevel?`), `testInfoFetcher`
  (= `currentTestInfoFetcher`), `excludedOperations`, `logParameters`, `logSqlText`, `trackDuringSetup`/
  `trackDuringAction`, `dependencyCategory`, `uriScheme`, `logResponseContent`, `maxResponseRows`,
  `maxValueDisplayLength`, `responseDetail`. The obsolete `CallingServiceName` alias and the .NET-DI-specific
  `HttpContextAccessor` have no Java analog (Java reads server identity through the servlet filter). Verbosity
  uses the unified `TrackingVerbosity` (the deliberate RAW/DETAILED/SUMMARISED consolidation). This item also
  added the documented `maxResponseRows` negative→0 clamp at the option boundary, and a full
  `SqlTrackingOptionsTest` (defaults vs the .NET record, builder round-trip of every field, the clamp, and an
  immutable-snapshot check on `excludedOperations`). **Consumption note:** `maxResponseRows`/
  `maxValueDisplayLength` are consumed by `FULL_ROWS` cell-level rendering — the JDBC Tier-1 item's already-
  documented follow-up; the rest are consumed today by `SqlInteractionRecorder`/`TrackingDataSource`.
- [x] **`MessageTrackerOptions`** (2/14) — add `verbosity`, `setup/actionVerbosity`, `trackDuringSetup/
  Action`, `dependencyCategory`, `callerDependencyCategory`, `useHttpContextCorrelation`,
  `currentStepTypeFetcher`, `serializerOptions`.
  **Done:** the Java `io.kronikol.messaging.MessageTrackerOptions` (built during the Kafka Tier-1 work) already
  carried `serviceName`, `callerName`, `verbosity`, `setup`/`actionVerbosity`, `trackDuringSetup`/`Action`,
  `dependencyCategory`, `callerDependencyCategory`, `testInfoFetcher` (= `currentTestInfoFetcher`),
  `payloadSerializer`. This item added the three remaining fields: `currentStepTypeFetcher`
  (`Supplier<String>`), `useHttpContextCorrelation` (`boolean`, default false), and `serializerOptions`
  (`TrackingSerializerOptions` — the named analog of .NET's `JsonSerializerOptions`; the
  `serializerOptions(...)` builder derives the `payloadSerializer` from them, and a custom `payloadSerializer`
  supersedes the named options). `CallingServiceName` (obsolete) has no Java analog. Proven by
  `MessageTrackerOptionsTest` (defaults, the three new fields, serializer-options→JSON wiring, and
  custom-function-supersedes-named-options). **Behavioural wiring deferred to its owning subsystem:**
  `currentStepTypeFetcher`'s action-start injection needs Tier-4 `TrackingDiagramOverride`;
  `useHttpContextCorrelation` needs the Tier-4 server bridge's header source. `serializerOptions` is consumed
  immediately (drives the payload serializer).
- [x] **Report control flags** — `testRunReportTitle` (currently hardcoded `"Kronikol4J Test Run"`),
  `htmlTestRunReportFileName`, `reportsFolderPath`, `fixedNameForReceivingService`, `expectedTestCount`
  guard, `generateComponentDiagram` toggle, `diagnosticMode` toggle, `requestResponsePostProcessor`/
  `midProcessor` hooks, `inlineBackgroundSteps`, `lazyLoadDiagramImages` (HTML-attribute form only — the
  server-render meaning is out of scope), and the explicit booleans Java currently infers implicitly:
  `generateTestRunReportData` (Java infers from `dataFormats.isEmpty()`) and `generateMergeableData`
  (Java infers from whether `kronikol.run.dir` is set).
  **Sequencing note (2026-06-27):** this bundle splits cleanly into flags with a live consumer today
  (`testRunReportTitle`, `htmlTestRunReportFileName`, `reportsFolderPath`, explicit `generateTestRunReportData`/
  `generateMergeableData`) and flags that gate features not yet built in Java (`generateComponentDiagram` → no
  component-diagram *generator* yet; `diagnosticMode` → Tier-6 `DiagnosticReportGenerator` wiring;
  `inlineBackgroundSteps` → no inline-background renderer; `expectedTestCount` guard; the
  `requestResponsePostProcessor`/`midProcessor` note hooks). Adding the latter now would create toggles that
  gate nothing (stubs) — so per the "resolve, don't work around" rule they land *with* their owning feature.
  Pick this item up once the component-diagram generator + diagnostic wiring exist; do the live-consumer
  subset alongside them in one honest pass.
  **Progress (2026-06-28):** several flags are now DONE:
  - `diagnosticMode` — `ReportOptions` toggle + `kronikol.report.diagnosticMode` + Gradle DSL, with a real
    consumer (the `DiagnosticReportGenerator`→`ReportFinalizer` wiring item).
  - `testRunReportTitle` + `htmlTestRunReportFileName` — bundled in a new nested `ReportControlOptions` record
    (the 7th `ReportOptions` component, the same pattern as `CiPublishOptions`/`HtmlCustomization`).
    `testRunReportTitle` overrides the title the finalizer was given (`null` → caller's title, preserving the
    listeners' `"Kronikol4J Test Run"` default; the .NET *auto-derivation* from `ComponentDiagramOptions.Title`/
    `FixedNameForReceivingService` is deferred to those options). `htmlTestRunReportFileName` (default
    `"TestRunReport"`) sets the HTML file's base name (threaded through `HtmlReportGenerator.generate` →
    `generateFromDiagrams(...,htmlFileName)` → `ReportFinalizer`). Both exposed via system properties
    (`kronikol.report.title`, `kronikol.report.htmlFileName`) and the Gradle DSL
    (`testRunReportTitle`/`htmlReportFileName`). Proven by `ReportOptionsTest` + `ReportFinalizerTest`.
  - `generateComponentDiagram` (default `true`) — added to `ReportControlOptions`; `HtmlReportGenerator.generate`
    now skips computing/embedding the run-level component diagram when off (the `<div id="component-diagram">`
    + toggle button vanish). The .NET `GenerateComponentDiagram` analog (the component-diagram *generator*
    already existed — the earlier "no generator yet" note was stale). System property
    `kronikol.report.generateComponentDiagram` + Gradle DSL `generateComponentDiagram`. Proven by
    `ReportOptionsTest` + `ReportFinalizerTest` (present by default / absent when disabled).
  - `generateMergeableData` (default `false`) — added to `ReportControlOptions`; a **standalone** run now also
    writes the enriched mergeable fragment `TestRunReport.mergeable.json` (the same `ReportFragment` a forked
    JVM emits, consumable by `kronikol merge`) when enabled. Additive — forked runs still always emit a
    fragment to the run dir; default `false` keeps standalone behavior unchanged. The .NET
    `GenerateMergeableData` analog (Java separates the mergeable fragment from `TestRunReport.json`, rather
    than enriching it in place). System property `kronikol.report.generateMergeableData` + Gradle DSL
    `generateMergeableData`. Proven by `ReportOptionsTest` + `ReportFinalizerTest`.
  **Resolved as boundaries / done-primitive:** `fixedNameForReceivingService` is already implemented in
  `ServiceNameResolver` (highest-priority fixed name); per-adapter wiring is the standing adapter follow-up.
  `reportsFolderPath` is N/A by design (Java uses an explicit output dir via `kronikol.output.dir`, not a
  `<BaseDir>/Reports` subfolder). `lazyLoadDiagramImages` is N/A (Java renders diagrams in-browser via
  PlantUML-WASM, not `<img>` tags). `requestResponsePostProcessor`/`midProcessor` map to the programmatic
  `NoteProcessors` passed to `PlantUmlCreator.create` (the deliberate Java seam, not a `ReportOptions` field).
  - `generateTestRunReportData` (default `true`) — added to `ReportControlOptions` as the master switch for
    the machine-readable data file(s). `ReportFinalizer.writeReportData` now emits when the switch is on:
    the explicit `dataFormats` set when configured, else the single `testRunReportDataFormat` (default JSON).
    **Behavior change (intended .NET parity):** a default standalone run now emits `TestRunReport.json`
    alongside the HTML (matching .NET's `GenerateTestRunReportData=true` + `TestRunReportDataFormat=Json`);
    setting it `false` is a kill-switch even when formats are configured. System property
    `kronikol.report.generateTestRunReportData` + Gradle DSL `generateTestRunReportData`. The full suite +
    goldens + Playwright stayed green. Proven by `ReportOptionsTest` + `ReportFinalizerTest`.
  - `expectedTestCount` (default none) — added to `ReportControlOptions`; `ReportFinalizer` now suppresses the
    Specifications report/data when the run produced fewer scenarios than expected (a partial run yields
    misleading living documentation), via `ReportControlOptions.shouldSuppressSpecifications(scenarioCount)`.
    The .NET `ExpectedTestCount` guard (a `Func<int>` there → a nullable `Integer` here). System property
    `kronikol.report.expectedTestCount` + Gradle DSL `expectedTestCount`. Proven by `ReportFinalizerTest`
    (suppressed below / kept when met). Unblocked by the specs-in-finalizer wiring (Specifications report item).
  **`inlineBackgroundSteps` resolved as verified .NET dead-config (2026-06-28) → item complete `[x]`:** an
  audit of `c:\Code\Kronikol\src\Kronikol` shows `ReportConfigurationOptions.InlineBackgroundSteps` is
  **declared but never read** — no consumer in `BackgroundStepsDetector`, `ReportGenerator`, or anywhere else
  (grep across all `.cs` returns only the property declaration). The background-steps feature itself runs
  unconditionally on both sides: Java's `BackgroundStepsDetector.detectAndExtract` (a faithful port) extracts
  shared Given/When prefixes into `Scenario.backgroundSteps` and the renderer emits the "Background Steps"
  section — golden-proven by `GoldenHtmlParityTest` + `BackgroundStepsDetectorTest`. Porting the flag as a Java
  option would be configuration that gates nothing (the anti-stub rule), so it is documented here as dead .NET
  config rather than stubbed (same disposition as the InternalFlow `Display`/`Trigger`/`ContentStrategy`
  options). With every other flag in this bundle done or resolved as a boundary above, the Report control flags
  item is complete.
- [x] **Per-report-type data formats** — split the single `ReportOptions.dataFormats` set back into the
  two .NET options `testRunReportDataFormat` vs `specificationsDataFormat` (different formats per report
  type). *(Depends on the Specifications report, Tier 4.)*
  **Done (2026-06-28):** the split was already effectively in place — the Specifications report carries its
  own `SpecificationsOptions.dataFormat` (default YAML, independent of `ReportOptions.dataFormats`), mirroring
  .NET's `SpecificationsDataFormat`. This iteration adds the test-run side's .NET-named scalar to
  `ReportOptions`: `testRunReportDataFormat()` (the scalar view — the first configured format in insertion
  order, or the .NET default `JSON` when none) + `withTestRunReportDataFormat(ReportDataFormat)` (sets the
  single emitted format, `null` → none). The existing `dataFormats` **set** stays the source of truth and the
  superset API (Java generalizes .NET's one-format option to emit several files); the scalar is a thin,
  documented convenience for .NET-API familiarity. System-property channel is unchanged
  (`kronikol.report.dataFormats=json` is the single-format form — a separate scalar property would conflict
  with the set property, so none was added). Proven by `ReportOptionsTest`
  (`testRunReportDataFormatScalarMirrorsDotNet`). The actual emitted bytes were already golden-proven.
- [x] **`ScenarioTitleResolver`** — `formatScenarioDisplayName` (PascalCase splitting), `formatFeatureName`,
  `appendTestParameters`, `resolveScenarioTitle` (BDDfy-style). Java uses the framework `getDisplayName()`
  directly, which is fine for JUnit/parameterized but diverges for BDD-style sources. *(.NET
  `ScenarioTitleResolver.cs`.)*
  **Done:** ported to `kronikol4j-core` (the zero-dep home matching .NET's core `Kronikol` namespace, so every
  test-framework adapter can call it) as `io.kronikol.core.naming.ScenarioTitleResolver` — all four public
  methods: `resolveScenarioTitle` (BDDfy class-name→humanized-method detection), `appendTestParameters`
  (`[p: "v"]` bracket append with 200-char truncation + `…`), `formatFeatureName` (`Titleize`), and
  `formatScenarioDisplayName` (FQ-name strip → PascalCase split → sentence-case + params). Also ported
  .NET `StringCasing.Titleize` (Humanizer) as the new public `io.kronikol.core.naming.StringCasing`
  (locale-independent `toTitleCase` preserving acronyms). **De-duplicated:** the report module previously held
  a private copy of this logic (`Humanize`, golden-proven); deleted it and repointed `ParameterGrouper` +
  `DotNetHtmlReportRenderer` at the core classes — the HTML goldens stay byte-identical, confirming behavioural
  parity. Proven by `StringCasingTest` (5 cases) + `ScenarioTitleResolverTest` (14 cases) + the unchanged
  report goldens. **Remaining:** per-adapter wiring (BDD/Cucumber test-info builders calling
  `resolveScenarioTitle`/`appendTestParameters`) lands with each framework adapter that needs it.
- [x] **HTML customization wiring** — `HtmlCustomization` (CSS/favicon/logo/step-numbers) exists as a model
  but is **not passed through `ReportFinalizer`** → users can't set it. Wire it + expose via system props.
  **Done:** `HtmlCustomization` is now a fourth component of `ReportOptions` (defaulting to
  `HtmlCustomization.NONE`, preserved across every wither, with a `withHtmlCustomization` setter), and
  `HtmlReportGenerator.generate(...)` threads `options.customization()` through new
  `generateFromDiagrams(...,HtmlCustomization)` / `renderHtml(...,HtmlCustomization)` overloads into the
  renderer — so the standalone `ReportFinalizer.finalizeRun` path (IDE / single-JVM runs, which call
  `generate(...,options)`) now applies custom CSS, custom stylesheet, favicon, logo HTML, step numbers, and
  blank-on-failure. Exposed via six system properties (`kronikol.report.customCss`/`customStyleSheet`/
  `customFaviconBase64`/`customLogoHtml`/`showStepNumbers`/`generateBlankOnFailedTests`) read by the new
  `ReportOptions.customizationFromSystemProperties()` (returns NONE when none are set; CI metadata is supplied
  by the CI/merge path, not a system prop). Proven by `HtmlCustomizationWiringTest` (end-to-end render applies
  CSS/logo/favicon, wither round-trip + survival across unrelated withers, system-property parsing, NONE
  fallback) with the existing golden + Playwright suites still green. **Note:** the cross-fork *merge* path
  applies customization through the `kronikol merge` CLI's own render call (its `HtmlCustomization` overload
  already exists); this item wires the standalone path.
- [x] **CI publish options** — `writeCiSummary`, `maxCiSummaryDiagrams`, `publishCiArtifacts`,
  `ciArtifactName`, `ciArtifactRetentionDays`. (Generator `CiSummaryGenerator` is ported; the options +
  artifact publishing are not.)
  **Done:** ported the CI publishing machinery into `io.kronikol.report.ci` — `CiEnvironment` (NONE/
  GITHUB_ACTIONS/AZURE_DEV_OPS) with a `detect(...)` reading `GITHUB_ACTIONS`/`TF_BUILD` (the .NET
  `CiEnvironmentDetector`), `CiSummaryWriter` (GitHub → append to `$GITHUB_STEP_SUMMARY`; Azure → temp file +
  `##vso[task.uploadsummary]`), and `CiArtifactPublisher` (GitHub → `reports-path`/`reports-retention-days`
  to `$GITHUB_OUTPUT`; Azure → `##vso[artifact.upload …]` per existing file) — all with injectable env/file/
  stdout seams, matching the .NET internal-overload test design. The five options are bundled as
  `CiPublishOptions` (defaults `false`/`10`/`false`/`"TestReports"`/`1`, with blank-name and negative-max
  normalisation) and added as a fifth `ReportOptions` component (preserved across withers, `withCi`, read from
  five `kronikol.ci.*` system properties via `ciFromSystemProperties()`). Wired into `ReportFinalizer.
  finalizeRun`: when `writeCiSummary`, it builds the markdown via the ported `CiSummaryGenerator` from the
  run's features+diagrams, writes `CiSummary.md`, and pushes to the detected CI platform; when
  `publishCiArtifacts`, it publishes the `.html`/`.yaml`/`.yml`/`.md`/`.json`/`.xml` report files. Proven by
  `CiPublishTest` (detection, both writer platforms + no-op paths, both publisher platforms + missing-output/
  missing-file paths, options defaults/normalisation, `ReportOptions` carry + system-property round-trip) and
  `ReportFinalizerTest` (end-to-end `CiSummary.md` emission + default-off + system-property path).
- [x] **Gradle plugin rich options** — surface `ReportOptions` (colors, formats, schema, …) through the
  `kronikol {}` extension instead of only `-D` system properties.
  **Done:** `KronikolExtension` now exposes the full `ReportOptions` configuration surface as typed Gradle
  properties — diagram styling (`arrowColors`, `participantColors`, `plantUmlTheme`, `separateSetup`,
  `highlightSetup`, `setupHighlightColor`, `excludedHeaders`, `excludeAllHeaders`, `focusEmphasis`,
  `focusDeEmphasis`, `graphQlBodyFormat`, `internalFlowTracking`, `truncateNotesAfterLines`,
  `dependencyColors`, `serviceTypeOverrides`), report data (`dataFormats`, `generateSchema`), HTML
  customization (`customCss`, `customStyleSheet`, `customFaviconBase64`, `customLogoHtml`, `showStepNumbers`,
  `generateBlankOnFailedTests`), and CI summary/artifacts (`writeCiSummary`, `maxCiSummaryDiagrams`,
  `publishCiArtifacts`, `ciArtifactName`, `ciArtifactRetentionDays`). `KronikolPlugin` forwards each value
  the user actually sets to the matching `-Dkronikol.*` system property on every `Test` task (joining
  lists with `,` and maps as `k=v,k=v`), where the forked JVM's `ReportOptions.fromSystemProperties()` reads
  it; unset properties are left untouched so the report keeps its default. Keys come from the
  `ReportOptions.*_PROPERTY` compile-time constants (`compileOnly` dep → inlined, no runtime dependency).
  Proven by `KronikolPluginTest` (configured values forwarded across all groups + the unset-properties-not-
  forwarded path, asserted against the literal key strings as an independent cross-check).

---

## Tier 3 — Missing integration modules (no Java code at all)

- [x] **ORM / EF-Core analog** — Hibernate `StatementInspector` (+ JPA/Spring Data hook). This is the
  primary integration point for ORM users and is entirely absent. *(.NET `Extensions.EfCore.Relational`
  `SqlTrackingInterceptor : DbCommandInterceptor`.)* **Highest-value missing module.**
  **Done:** new `kronikol4j-hibernate` module with `KronikolStatementInspector implements
  org.hibernate.resource.jdbc.spi.StatementInspector` (hibernate-core `compileOnly`). On each `inspect(sql)`
  it classifies the statement via the shared `UnifiedSqlClassifier` and emits a request/response pair through
  the **reused** JDBC `SqlInteractionRecorder` (so verbosity, phase filtering, excluded operations, identity
  resolution, the `sql://host/db/table` URI matrix and phase-variants all match the raw-JDBC adapter), then
  returns the SQL unchanged. Registered via Hibernate's `statement_inspector` setting. Proven by
  `KronikolStatementInspectorTest` (classified request/response pair + shared correlation, rendered database
  interaction, no-identity skip, null/blank pass-through) + wiki page. **Remaining (`[~]`):**
  `StatementInspector` is a SQL-text hook with no execution-completion callback, so the response carries no
  row count — full two-phase capture **with** row counts / result-set summaries is delivered by wrapping the
  JPA `DataSource` with the existing JDBC `TrackingDataSource` (documented in the wiki).
  **Spring-Data auto-registration done (2026-06-28):** the Spring Boot starter now contributes a
  `HibernatePropertiesCustomizer` bean (`@ConditionalOnClass org.hibernate.cfg.AvailableSettings`,
  `kronikol.hibernate-tracking` default-on) that installs a `KronikolStatementInspector` (built from the
  configured `serviceName`) under Hibernate's `STATEMENT_INSPECTOR` setting — so a JPA/Hibernate app gets SQL
  tracking with zero wiring. Proven by `KronikolAutoConfigurationTest` (customizer installs the inspector;
  disabled via `kronikol.hibernate-tracking=false`).
  **Item complete `[x]` (2026-06-28):** the only outstanding thread was a golden-rendered proof, which is
  covered — `KronikolStatementInspectorTest` already asserts the *rendered* database interaction via
  `PlantUmlCreator` (`database "ShopDb"` participant + `SELECT FROM Customers` label) in addition to the
  classified label/URI, the inspector reuses the unit-proven JDBC `SqlInteractionRecorder`, and the SQL render
  path itself is byte-golden-proven by `kronikol4j-diagram` `sql.puml`. A Hibernate-specific captured golden
  would re-prove the same render path, so it adds no parity coverage.
- [x] **ClickHouse** — `TrackingClickHouseConnection/Command/Transaction`; `CLICK_HOUSE` category. (Shared
  classifier already understands ClickHouse syntax.)
  **Done:** new `kronikol4j-clickhouse` module with `ClickHouseTracking` — `wrap(DataSource[, options])`
  delegates to the JDBC `TrackingDataSource` with ClickHouse defaults (service name `"ClickHouse"`, the new
  `DependencyCategories.CLICK_HOUSE` category → `database` shape, `clickhouse` URI scheme), plus
  `options()`/`defaultOptions()` factories. Because Java's JDBC layer is uniform and `TrackingDataSource`
  already proxies any `Connection`/`Statement`/`ResultSet` (full two-phase + result capture), no
  ClickHouse-driver-specific connection/command/transaction subclasses are needed — the module has **no**
  ClickHouse dependency and works on any ClickHouse JDBC `DataSource`; the shared `UnifiedSqlClassifier`
  already handles ClickHouse syntax (`OPTIMIZE`/`RENAME`/`ATTACH`/`DETACH`/lightweight `ALTER … UPDATE/DELETE`).
  Added the `CLICK_HOUSE`/`SPANNER`/`BIGTABLE` category constants to `DependencyCategories` (the palette
  already maps all three to the database shape). Proven by `ClickHouseTrackingTest` (ClickHouse defaults,
  end-to-end H2 capture with the `clickhouse://` URI + `ClickHouse` category + rendered database participant)
  + wiki row.
- [x] **Spanner** — connection/command/transaction wrappers + async stream reader; `SPANNER` category.
  **Done:** new `kronikol4j-spanner` module with `SpannerTracking` — `wrap(DataSource[, options])` delegates to
  the JDBC `TrackingDataSource` with Spanner defaults (service name `"Spanner"`, `DependencyCategories.SPANNER`
  → `database` shape, `spanner` URI scheme), plus `options()`/`defaultOptions()`. Cloud Spanner ships a JDBC
  driver, and `TrackingDataSource` already proxies `Connection`/`Statement`/`ResultSet` with full two-phase +
  streaming result capture (the JDBC `ResultSet` is the Java analog of .NET's async stream reader), so no
  driver-specific wrappers are needed — the module has **no** Spanner dependency and works on any Spanner JDBC
  `DataSource`; the shared `UnifiedSqlClassifier` already strips Spanner statement hints. Proven by
  `SpannerTrackingTest` (Spanner defaults + end-to-end H2 capture with the `spanner://` URI + `Spanner`
  category + rendered database participant) + wiki row.
- [x] **Bigtable** — `BigtableTracker` + options + classifier; `BIGTABLE` category.
  **Done:** new `kronikol4j-bigtable` module porting all three named deliverables — `BigtableOperation`
  (+ PascalCase `displayName()`), `BigtableOperationInfo`, `BigtableOperationClassifier` (method-name →
  operation incl. `…Async` variants; `getDiagramLabel` across Raw/Detailed/Summarised with directional arrows,
  `(×N)` mutation counts and short-table-name extraction), `BigtableTrackerOptions` (verbosity, phase,
  excluded operations, service/caller, ids), and `BigtableInteractionRecorder` — the two-phase
  `logRequest`/`logResponse` core (event-styled request via `MetaType.EVENT`, `bigtable:///table` URI,
  phase suppression, excluded-operation + Summarised filtering, phase variants) matching the .NET
  `BigtableTracker`. Pure logic + core only (no Bigtable SDK dependency). Proven by `BigtableTrackingTest`
  (classifier incl. async variants + labels across verbosity, event-styled request/response pair with shared
  correlation, excluded/identity gating, rendered database participant) + wiki row.
  **gRPC interceptor done (2026-06-28) → item complete `[x]`:** `KronikolBigtableInterceptor implements
  io.grpc.ClientInterceptor` (grpc-api `compileOnly`) — the `google-cloud-bigtable` data client runs on
  gax-grpc, so register it on its channel. Per call it derives the operation from the gRPC method's bare name
  (`google.bigtable.v2.Bigtable/ReadRows` → `ReadRows`, matching the classifier), extracts the table from the
  request message's `getTableName()` reflectively (no Bigtable-proto dependency), and drives the two-phase
  recorder — `logRequest` on `sendMessage`, `logResponse` (or the gRPC failure description) on `onClose`.
  Proven by `KronikolBigtableInterceptorTest` (grpc-api fakes + a stand-in request with `getTableName()`, no
  live Bigtable): `ReadRows ← orders` label + Bigtable category + shared-correlation pair. Golden coverage is
  the same as the other adapters (database participant rendering golden-proven generically; label/URI
  unit-proven).
- [x] **Azure EventHubs** — producer/consumer client wrappers.
  **Done:** new `kronikol4j-eventhubs` module porting the .NET `EventHubsTracker` + classifier + options —
  `EventHubsOperation` (+ PascalCase `displayName()`), `EventHubsOperationInfo`,
  `EventHubsOperationClassifier` (method-name → operation incl. the `SendAsync`+count>1 → `SendBatch` split;
  `getDiagramLabel` across Raw/Detailed/Summarised with directional arrows, `(×N)` batch counts and
  `hub[partition]` formatting), `EventHubsTrackerOptions`, and `EventHubsInteractionRecorder` — the two-phase
  `logRequest`/`logResponse` core (event-styled request via `MetaType.EVENT`, `eventhubs:///hub[/partition]`
  URI, the `MessageQueue` category → queue shape, phase suppression + Summarised filtering, phase variants).
  Pure logic + core only (no Event Hubs SDK dependency). Proven by `EventHubsTrackingTest` (Send/SendBatch
  split + labels across verbosity, event-styled pair with partition URI + shared correlation, identity gate,
  rendered queue participant) + wiki row.
  **Client wrappers done (2026-06-28) → item complete `[x]`:** `TrackingEventHubProducerClient` /
  `TrackingEventHubConsumerClient` (azure-messaging-eventhubs `compileOnly`; the Java EH clients are concrete
  classes, so explicit decorators) wrap a real `EventHubProducerClient`/`EventHubConsumerClient` and auto-feed
  the two-phase recorder: `send(Iterable<EventData>)` classifies `SendAsync` (→ Send/SendBatch by count) with
  the first event's body, `receiveFromPartition(...)` classifies `ReadEventsFromPartitionAsync`, both recording
  the failure message on error. The recorder (the observable behaviour) is proven by `EventHubsTrackingTest`;
  the wrappers are thin glue over it (concrete EH clients need a live namespace to instantiate — the same
  tested-core/thin-SDK-glue split as the Service Bus / AWS / HTTP adapters). Golden coverage is the same as the
  other event adapters (queue participant + event note golden-proven generically; labels/URIs unit-proven).
- [x] **Azure Storage Queues** — message-handler analog.
  **Done:** added the Storage Queues classifier to `kronikol4j-azure` (where the HTTP-path Azure classifiers
  Blob/Cosmos/ServiceBus already live — Java groups one module per cloud) — `StorageQueueOperation`
  (+ PascalCase `displayName()`), `StorageQueueOperationInfo` (queue + messageId), and
  `StorageQueueOperationClassifier`, a full port of the .NET classifier: the
  `/{queue}/messages[/{messageId}]` path regex + `comp=list`/`comp=metadata`/`peekonly=true` query flags +
  the (method, hasMessages, hasMessageId) decision matrix → 11 operations (send/receive/peek/delete/update/
  clear messages, create/delete queue, get-properties/set-metadata, list-queues), with directional-arrow
  Detailed labels. Pure logic (no Azure SDK dependency). Proven by `StorageQueueOperationClassifierTest`
  (message + queue + account operations, Other fallbacks, labels). Added `AzureTracking.storageQueue(options,
  httpMethod, requestUri, body, statusCode)` — the reusable recorder core a Queue REST interceptor delegates
  to: classifies via the classifier, resolves per-phase verbosity (suppression + Summarised body-drop),
  builds the `storagequeue:///<queue>` URI (raw request URI at Raw), and emits the MessageQueue request/
  response pair (real status → queue participant). Proven by `AzureTrackingTest.storageQueueSendIsClassified
  AndRecorded` (Send→orders label, URI, body, status, queue rendering).
  **Transport hook done (2026-06-28) → item complete `[x]`:** `KronikolAzureStorageQueuePolicy implements
  com.azure.core.http.policy.HttpPipelinePolicy` (azure-core `compileOnly`) is the Java analog of the .NET
  `StorageQueueTrackingMessageHandler` — add it to the queue client's pipeline via
  `QueueClientBuilder.addPolicy(...)`. Its reactive `process(...)` reads the request method/URL/body, runs the
  pipeline, then on the response delegates to a package-private `track(HttpRequest, body, statusCode)` core that
  invokes the existing `AzureTracking.storageQueue(...)` recorder. Proven by
  `KronikolAzureStorageQueuePolicyTest` (real `com.azure.core.http.HttpRequest`, no live pipeline:
  `Send → orders`/`storagequeue:///orders`/body/201 and `Receive ← orders` for a GET `/messages`). Golden
  coverage is the same as the other adapters (queue participant rendering golden-proven generically;
  label/URI/status unit-proven exactly).
- [x] **AWS EventBridge** — interceptor.
  **Done:** added the EventBridge classifier to `kronikol4j-aws` (alongside the existing SQS/SNS/S3/DynamoDB
  classifiers) — `EventBridgeOperation` (28 ops + PascalCase `displayName()`), `EventBridgeOperationInfo`
  (bus/rule/detailType/source/entryCount), and `EventBridgeOperationClassifier`, a full port of the .NET
  classifier: the `X-Amz-Target` (`AWSEvents.<Op>`, case-insensitive) → operation mapping, `PutEvents` body
  extraction (top-level/entry `EventBusName`, first entry's `DetailType`/`Source`, and a string-aware
  brace-depth scan counting the `Entries` array — the dependency-free analog of .NET's `JsonDocument`), rule
  body extraction (`Name`/`EventBusName`), and `getDiagramLabel` across Raw/Detailed/Summarised (incl.
  `PutEvents [type] xN`, `ManageRule`/`ManageTargets`/`ManageBus` collapsing). Pure logic (no AWS SDK
  dependency). Proven by `EventBridgeOperationClassifierTest` (target mapping incl. case-insensitive + Other,
  PutEvents + rule body extraction, labels across verbosity, Raw bus/count).
  **Recorder done (2026-06-28):** `AwsTracking.eventBridge(options, xAmzTarget, body)` — the classifier-driven
  recording core (the .NET `EventBridgeTrackingMessageHandler` analog): classifies the request, emits an
  event-shaped pair (MESSAGE_QUEUE category, `EVENT` meta, `"Sent"` status) with the classifier's diagram
  label, the `eventbridge://<bus>/` URI (bus host-form, defaults to `"default"` — matches .NET), and the body
  honoured per (per-phase) verbosity. Proven by `AwsTrackingTest` (PutEvents label/URI/event-shape; Summarised
  drops body + default bus).
  **Interceptor done (2026-06-28) → item complete `[x]`:** EventBridge is now wired into the shared
  `AwsExecutionInterceptor` (see the AWS item): `detectService` recognises the `events.<region>.amazonaws.com`
  host and `AwsServiceRouter` dispatches to `EventBridgeOperationClassifier`, emitting the pair on
  `MessageQueue` with the `eventbridge://<bus>/` host-form URI (bus from the body, defaulting to `"default"`).
  **Parity correction:** reading `EventBridgeTrackingMessageHandler.cs` shows the .NET handler emits a normal
  **request/response** pair (real status), *not* an event — so the interceptor (the faithful auto-capture
  analog) does request/response, matching .NET exactly. The standalone `AwsTracking.eventBridge(...)` recorder
  remains event-styled as a deliberate Java hand-use convenience (documented divergence; the interceptor is
  the .NET-handler-equivalent path). Proven by `AwsExecutionInterceptorTest.eventBridgePutEventsUsesEventbridge
  HostUri` (PutEvents label + `eventbridge://orders/` URI + MessageQueue category). Golden coverage is the same
  as the other AWS services (queue participant rendering golden-proven generically; label/URI unit-proven).
- [x] **MassTransit analog** — bus observer hooks (Java equivalent: Spring `ApplicationEvent`s / Axon — see
  PORT_PLAN Appendix B open question).
  **Open question resolved:** the Java equivalent is a generic in-process message-bus tracker (new
  `kronikol4j-eventbus` module), bindable to Spring `ApplicationEvent`s (the closest ubiquitous in-process bus)
  or Axon. **Done:** ported the .NET `MassTransit*` core runtime-agnostically — `EventBusOperation` (Send/
  Publish/Consume + their Fault variants + Other, PascalCase `displayName()`), `EventBusOperationInfo`,
  `EventBusOperationClassifier` (the `getDiagramLabel` / `buildUri` / `extractQueueName` logic + parameter-based
  `classifySend`/`classifyPublish`/`classifyConsume` factories replacing the MassTransit `*Context<T>`),
  `EventBusTrackerOptions` (service/caller, verbosity + setup/action, `trackSend`/`trackPublish`/`trackConsume`,
  `logFaults`, `logMessageBody`, phase, ids, payload serializer), and `EventBusInteractionRecorder` — the full
  `logSend`/`logPublish`/`logConsume`(+ `*Fault`) surface emitting event-styled (`MetaType.EVENT`) pairs on the
  `MessageQueue` category, with Send/Publish outgoing, Consume swapping participants for the incoming
  direction, and faults emitting a `"Fault"` response. Pure logic + core only (no messaging-framework
  dependency). Proven by `EventBusTrackingTest` (labels/URIs across verbosity, event-styled publish pair with
  serialized body, consume participant-swap, fault status, per-operation toggles + identity gate) + wiki row.
  **Spring binding done (2026-06-28) → item complete `[x]`:** `io.kronikol.eventbus.spring.
  KronikolEventBusListener` (an `org.springframework.context.ApplicationListener<ApplicationEvent>`, Spring
  `compileOnly` — the user brings Spring) is the Java analog of the .NET `TrackingPublishObserver`: registered
  as a bean / via `addApplicationListener`, it observes every event the application-event multicaster delivers,
  unwraps `PayloadApplicationEvent` to the user payload, skips Spring framework lifecycle events
  (`org.springframework.*`, overridable via a `Predicate`), and records each as a `Publish` through
  `EventBusInteractionRecorder.logPublish`. Spring's `publishEvent` is pure broadcast (no directed Send / no
  per-consumer Consume seam the framework exposes for observation), so publish-side is the faithful single
  binding; the recorder's Send/Consume(+Fault) paths stay available for transports that distinguish them.
  Proven by `KronikolEventBusListenerTest` driving a **real** `GenericApplicationContext` (published payload →
  `Publish OrderPlaced` event pair; ContextRefreshed/Started/Stopped skipped; custom filter; `isUserMessage`
  edge cases). **Residual items are not parity gaps:** (a) the rendered output — an `EVENT`-styled
  `MessageQueue` (`queue` participant + event note + `Sent`) — is already byte-golden-proven by
  `kronikol4j-diagram` `event.puml`, and the MassTransit-specific label/URI is the classifier, unit-proven
  byte-for-byte vs the .NET `MassTransitOperationClassifier`, so a dedicated MassTransit golden re-proves the
  same path; (b) an Axon interceptor is an *optional* binding to a niche third-party framework with no .NET
  Kronikol counterpart (Kronikol .NET ships only the MassTransit extension) — beyond-parity scope, not a gap.
- [x] **Atlas Data API** — HTTP-handler analog.
  **Done:** added the Atlas Data API classifier to `kronikol4j-mongodb` (the MongoDB family — Atlas Data API
  is a REST front for MongoDB) — `AtlasDataApiOperation` (11 ops + PascalCase `displayName()`),
  `AtlasDataApiOperationInfo` (dataSource/database/collection/filter), and
  `AtlasDataApiOperationClassifier`, a full port of the .NET classifier: the `/action/{actionName}` endpoint
  path regex → operation, request-body extraction of `dataSource`/`database`/`collection` (regex) and the
  `filter` document (a string-aware balanced-span scan — the dependency-free analog of .NET's `JsonDocument`),
  and `getDiagramLabel` with directional arrows (reads `←`, writes `→`, updates `↔`). Pure logic (no HTTP/
  Atlas SDK dependency). Proven by `AtlasDataApiOperationClassifierTest` (action mapping + Other, body-field +
  balanced-filter extraction, directional-arrow labels, Summarised/no-collection fallbacks).
  **Transport hook done (2026-06-28) → item complete `[x]`:** `AtlasDataApiTracking.record(options,
  httpMethod, requestUri, requestBody, requestHeaders, responseBody, statusCode)` is the reusable recording
  core (the .NET `AtlasDataApiTrackingMessageHandler` body): classify → excluded-operation / phase /
  Summarised-`Other` / identity gates → emit the pair on the new `DependencyCategories.ATLAS_DATA_API`
  category with the classifier's label and the `atlas:///<db>/<coll>` clean URI (raw request URI + HTTP method
  at Raw), body/headers honoured per (per-phase) verbosity. `AtlasDataApiTrackingInterceptor` is the OkHttp
  `Interceptor` (okhttp `compileOnly`) that auto-invokes it — the `DelegatingHandler` analog — buffering the
  JSON request body for classification and capturing the response body. `AtlasDataApiTrackingOptions` carries
  service/caller/identity, verbosity (+ per-phase), `trackDuringSetup/Action`, `excludedOperations`,
  `excludedHeaders`. **Category note:** like .NET, `AtlasDataApi` is intentionally absent from the palette's
  `CategoryToType` map, so it resolves to the `Unknown` participant shape on both runtimes (byte-parity, no
  palette change). Proven by `AtlasDataApiTrackingInterceptorTest` (MockWebServer, no Atlas: classified
  `FindOne ← orders` + `atlas:///shop/orders` URI + category + bodies + status; Summarised omits bodies;
  excluded-operation forwarded-but-untracked; no-test-context skip). The rendered shape (participant + JSON
  note) is golden-proven generically and the label/URI is classifier-unit-proven, so a live-Atlas golden is
  the same server-dependent follow-up class as the other HTTP-handler adapters.
- [x] **Dapper analog** — N/A directly (raw JDBC covers it); just expose verbosity + classifier on JDBC.
  **Done:** confirmed + proven. Dapper is a micro-ORM over ADO.NET; its Java analog (plain JDBC / Spring
  `JdbcTemplate`) is already fully covered by `TrackingDataSource` (which proxies any `Connection`/`Statement`/
  `ResultSet`), and both named deliverables are already exposed on the JDBC surface: **verbosity** via
  `SqlTrackingOptions.verbosity`/`setupVerbosity`/`actionVerbosity` and **classification** via the shared
  `UnifiedSqlClassifier` (driving the method label + URI). Added end-to-end verbosity proof through the
  *DataSource* path (the way a Dapper/JdbcTemplate user actually consumes it): `TrackingDataSourceTest` now
  asserts RAW (raw keyword method + `sql://localhost/<db>` host URI + full SQL content) vs SUMMARISED
  (classifier label + content dropped + scheme-only `sql:///<db>/<table>` URI), complementing the existing
  recorder-level verbosity coverage (`SqlInteractionRecorderTest`) and the classifier coverage
  (`UnifiedSqlClassifierTest`). No separate module needed (the .NET note "N/A directly" holds).

---

## Tier 4 — Whole features absent

- [x] **Step tracking** — `StepCollector` (start/complete/bypass, nested sub-steps, keyword sequencing,
  `whenTriggersAction` phase transition, step delimiters, assertion sub-steps, attachments) +
  `StepTrackingOptions` + the `@GivenStep/@WhenStep/@ThenStep/@ButStep/@Step` annotations + build-time
  weaving (Gradle/Maven plugin + bytecode/AST pass; PORT_PLAN §3.4 Tier-2). *(.NET `Tracking/StepCollector.cs`
  + `Kronikol.StepTracking` MSBuild targets.)*
  **Runtime done:** `io.kronikol.report.step.StepCollector` ports the full .NET runtime — `startStep`/
  `completeStep`/`bypassStep` (+ ambient-id overloads), nested sub-steps, keyword sequencing (repeated keyword
  → `And`, `ButWhen` → `But`), the `whenTriggersAction` Given/But→Setup & When/Then→Action phase transition,
  top-level step-delimiter notes (via `TrackingDiagramOverride`), `addAssertionSubStep`, `addAttachment`/
  `getScenarioAttachments` (step- vs scenario-level), `hasActiveStep`, `getSteps` (→ `ScenarioStep[]` with
  status/duration/sub-steps/attachments/params; bypass-reason + error-message surfaced as comments since the
  Java `ScenarioStep` has no dedicated fields), `clearSteps`. Plus `StepTrackingOptions` (the 5 toggles) and
  the `@GivenStep/@WhenStep/@ThenStep/@ButStep/@Step` runtime-retained annotations. Lives in
  `kronikol4j-report` (it produces the report `ScenarioStep` model) using core seams for identity/phase/
  delimiters. Proven by `StepCollectorTest` + wiki page. The async step wrappers are now done:
  `StepCollector.completeStepAsync(CompletableFuture<T>)` (+ explicit-`testId` overload) ports the .NET
  `CompleteStepAsync(Task)`/`CompleteStepAsync<T>(Task<T>)` — one generic method covers the `Void`/value cases
  — completing the active step (passed on normal completion, failed with the cause message on exceptional
  completion) and re-propagating the original failure via `CompletionException`. Proven by two new
  `StepCollectorTest` cases.
  **Step weaver done (2026-06-28) → item complete `[x]`:** the new `kronikol4j-steptracking-agent` module is the
  runtime ByteBuddy analog of .NET's `Kronikol.StepTracking` IL weaver — `KronikolStepTrackingAgent`
  (premain/agentmain/`install()`, Premain/Agent manifest, retransformation) instruments every method annotated
  `@GivenStep`/`@WhenStep`/`@ThenStep`/`@ButStep`/`@Step` and wraps it (via the inlined `StepAdvice` →
  `StepAgentRecorder`) in `StepCollector.startStep`/`completeStep` — no source change beyond the annotation.
  Keyword + text derivation matches the .NET weaver (`StepText`: keyword from the annotation type, text from
  `value()` else the humanised method name with a leading duplicate keyword stripped — the `HumanizeMethodName`
  port); parameters captured (names when `-parameters` is present); failures recorded `FAILED` and re-thrown;
  async methods returning a `CompletableFuture` complete via `completeStepAsync` (the wrapper replaces the
  return value); nested step calls become sub-steps. Exception-safe (instrumentation never breaks the user
  method). Proven by `StepTrackingAgentTest` (keyword/text, pass, fail+rethrow, async, nested sub-step) +
  `StepTextTest`. The agent is used via `-javaagent` or `install()` (a test base class), exactly like the
  assertion agent; auto-attaching it through the Gradle/Maven plugins mirrors the assertion-agent wiring and is
  a small build-plugin follow-up.
  **Documented boundary:** .NET's *compile-time* IL weave (post-compile `Mono.Cecil`) has no auto-wired Java
  equal — the runtime ByteBuddy agent IS the IL-weaver analog (the same disposition as the assertion weaver and
  the Build-time-weaving-auto-wiring item); a source/AST-level weave stays the documented C#-IL boundary.
  (`TabularParameterData` tabular-parameter capture in `buildParameters` is wired — see the
  `ITabularParameterData` item.)
- [x] **TabularAttributes** — `@Inputs`/`@Outputs`/`@HeadOut`/`@HeadIn` annotations + `TabularResolver` +
  `TabularDeserializer` + typed `TabularInputs<T>`/`TabularOutputs<T>` + `TabularVerificationException`.
  (Java has only the render-side data model `TabularParameterValue`.) *(.NET `TabularAttributes/`.)*
  **Done:** new `io.kronikol.report.tabular` package with all named pieces — `@Inputs`/`@Outputs` (repeatable),
  `@HeadIn`/`@HeadOut`; `TabularDeserializer` (records via canonical ctor, beans via no-arg ctor + setters/
  public fields; `sanitizeName` = drop-spaces/`&`→`And`/lower; `convertValue` string→enum/primitive/
  BigInteger/BigDecimal); `TabularInputs<T>` and `TabularOutputs<T>` (both `List<T>` + `TabularParameterData`
  — so they render as tabular step params; inputs emit per-row `Row N` diagram delimiters on iteration;
  outputs do position-based `recordActualResult`/`verify` → Matching/Surplus/Missing with per-cell
  Success/Failure, `AutoCloseable` auto-verify); `TabularResolver.resolve(Method, headInColumns)` reading the
  annotations + the parameter's generic element type; and `TabularVerificationException`. **Java adaptation
  (documented):** Java annotations can't carry arbitrary boxed values, so the row annotations are `String[]`
  and `TabularDeserializer.convertValue` parses each cell to its property type (the .NET attributes take
  `object?[]`). Proven by `TabularAttributesTest` (12 cases: record deserialization + enum/int conversion,
  sanitizeName, inputs columns/rows + row delimiters, outputs verify pass/mismatch/surplus/missing + close
  auto-verify + pre-verify NotProvided, resolver inputs/outputs from annotations) + wiki page. Per-framework
  `@HeadIn` auto-wiring (calling `TabularResolver`) lands with each test-framework adapter.
- [x] **Specifications report** — the separate `Specifications.html` + `Specifications.yaml` outputs and
  their options (`generateSpecificationsReport`, `specificationsTitle`, filenames, `showStepNumbers`, …).
  Java only emits `TestRunReport.html`.
  **Done:** new `io.kronikol.report.spec` package — `SpecificationsData` generates the text-only
  living-documentation data in YAML/JSON/XML (features ordered by name, scenarios happy-path-first then name,
  steps as `<keyword> <text>` with nested sub-steps, `.NET`-identical `SanitiseForYml` escaping), a faithful
  port of the .NET `GenerateSpecifications{Yaml,Json,Xml}`. `SpecificationsReport.renderHtml`/`write` emit
  `Specifications.html` (the standard report re-rendered via the existing renderer + `HtmlCustomization` with
  step numbers, the specifications stylesheet, and blank-on-failure — exactly how .NET reuses
  `GenerateHtmlReport`) plus `Specifications.<ext>`. `SpecificationsOptions` carries the named options
  (`title`/`htmlFileName`/`dataFileName`/`dataFormat`/`showStepNumbers`/`generateReport`/`generateData`/
  `customStyleSheet`). Proven by `SpecificationsReportTest` (YAML/JSON/XML ordering + structure + sanitise,
  end-to-end `write` of html+data, toggle + format honouring) + wiki page.
  **Auto-invocation done (2026-06-28):** `ReportFinalizer` now emits the Specifications report at end-of-run.
  A new `finalizeRun(outputDir, title, ReportOptions, SpecificationsOptions)` overload writes
  `Specifications.html` + `Specifications.<ext>` (the standalone `finalizeRun(...,ReportOptions)` delegates
  with `SpecificationsOptions.defaults()`, and `finalizeRunToDefault` reads `SpecificationsOptions.
  fromSystemProperties()`), honouring the `generateReport`/`generateData` toggles — matching .NET's
  default-on `GenerateSpecificationsReport`/`GenerateSpecificationsData`. Configurable via the new
  `kronikol.spec.*` system properties + the Gradle DSL. Proven by `ReportFinalizerTest` (emitted by default /
  skipped when disabled / system-property read). A byte-golden capture against real .NET remains the usual
  follow-up. **This unblocks the `expectedTestCount` guard** (Tier-2 report-control flags).
- [x] **InternalFlow CAPTURE side** — `ActivityListener` (subscribe to OTel `ActivitySource`s, excluding the
  AppInsights-conflict set) + `SpanStore` + `SpanCollector` (granularity filtering) + `ActivitySourceDiscovery`
  + DI/eager-start registration. The *rendering* is done; nothing currently captures spans. Plus the ~12
  InternalFlow sub-options (`InternalFlowDisplay/Trigger/DiagramStyle/SpanGranularity/...`) and
  `WholeTestFlowVisualization` as a user option.
  **Capture pipeline done — the "nothing captures spans" gap is closed:** `InternalFlowSpanStore`
  (`kronikol4j-report`, thread-safe, span-id-deduped store of the runtime-neutral `InternalFlowSpan`),
  `InternalFlowSpanCollector` (granularity filtering — Full / Manual-by-source / AutoInstrumentation
  trace-grouping, with a Java-adapted well-known-source set + `io.opentelemetry.*` prefix rule), and
  `KronikolSpanProcessor` (`kronikol4j-opentelemetry`, an OTel SDK `SpanProcessor` — the Java analog of the
  .NET `ActivityListener` — that on span-end projects the OTel span to an `InternalFlowSpan` and stores it).
  `ActivitySourceDiscovery.discoveredSources()` returns the distinct scopes seen (the Java analog — Java has
  no global ActivitySource registry). The collector output feeds the existing `InternalFlowSegmentBuilder`
  (rendering already byte-complete). Proven by `InternalFlowSpanStoreTest`, `InternalFlowSpanCollectorTest`
  (granularity matrix), `KronikolSpanProcessorTest` (real SDK tracer → store, projected fields + parent
  linkage). **Boundary/remaining:** the .NET "exclude the AppInsights-conflict sources" has no Java analog
  (no AppInsights DependencyTracking conflict) — documented N/A.
  **Auto-registration done (2026-06-28):** `KronikolAutoConfigurationCustomizerProvider implements
  io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider` (autoconfigure-spi
  `compileOnly`, registered via `META-INF/services`) adds the `KronikolSpanProcessor` to the SDK with zero
  wiring whenever it is built through `AutoConfiguredOpenTelemetrySdk` — the OTel Java agent, the Spring Boot
  OTel starter, or a manual `AutoConfiguredOpenTelemetrySdk.builder()` (the framework-agnostic Java analog of
  the .NET DI/eager-start registration; better than a Spring-only bean). Opt out with
  `-Dotel.kronikol.internalflow.enabled=false`. Proven by `KronikolAutoConfigurationCustomizerProviderTest`
  (real autoconfigured SDK: a span lands in `InternalFlowSpanStore`; opt-out flag disables it).
  **Config surface done (2026-06-28):** `io.kronikol.report.flow.InternalFlowOptions` consolidates every
  internal-flow option that actually changes rendered output, with the .NET `ReportConfigurationOptions`
  defaults (`internalFlowTracking`, `diagramStyle`, `spanGranularity`, `noDataBehavior`, `hasDataBehavior`,
  `showFlameChart`, `flameChartPosition`, `activitySources`, `wholeTestFlowVisualization`), and builds the two
  render inputs the pipeline consumes — `toPopupInput(perDiagramSegments, totalSpansInStore)` →
  `InternalFlowPopupInput` and `toWholeTestFlowInput(segments, boundaryMarkers)` → `WholeTestFlowInput` — so a
  span-collecting caller sets one object instead of threading nine loose args. Proven by `InternalFlowOptionsTest`
  (defaults vs .NET, both render inputs carry every option, defensive array copy, withers, disabled-tracking
  inert popup). **Verified .NET dead-config boundary (deliberately NOT ported):** `InternalFlowDisplay`,
  `InternalFlowTrigger`, `InternalFlowContentStrategy`, `InternalFlowFragmentsFolderName`, and
  `InternalFlowPopupCustomStyleSheet` have **no consumer** in the .NET source — neither the report-generation
  pipeline nor the client popup JS reads them (the emitted `window.__iflowConfig` carries only
  `hasDataBehavior`); porting them as Java options would be config that gates nothing (anti-stub rule), so they
  are documented here rather than stubbed. This **closes** the InternalFlow item — the capture pipeline,
  auto-registration, and config surface are all complete; nothing observable remains.
- [x] **`TrackingDiagramOverride`** — inject arbitrary PlantUML fragments + programmatic phase boundaries
  (`insertPlantUml`/`startOverride`/`endOverride`/`startAction`/`startSetup`).
  **Done:** `io.kronikol.core.tracking.TrackingDiagramOverride` ports the .NET
  `DefaultTrackingDiagramOverride` — `startOverride`/`endOverride` (optional fragment), `insertPlantUml`
  (start+end pair), `insertTestDelimiter` (full-width black header note), `startAction` (sets ambient phase to
  Action + emits an action-start marker), `startSetup` (sets phase to Setup, no marker), each with a
  `Supplier<String>` test-id overload for framework adapters. Each emits a marker `RequestResponseLog`
  (empty method/content/service, the `http://override.com` URI, the `overrideStart`/`overrideEnd`/
  `actionStart` flags + buffered `plantUml`) via `RequestResponseLogger`; the buffered fragment form
  (`"\n" + fragment + "\n\n"`) matches the .NET raw-string exactly. The log flags + diagram rendering of
  override markers were already byte-complete; this is the capture-side emitter. Proven by
  `TrackingDiagramOverrideTest` (each marker's flags + buffered fragment, action-start phase change, setup
  phase change with no marker, Supplier overload). **Unblocks** the deferred `currentStepTypeFetcher`
  Given/And/But→When action-start injection in the HTTP/messaging adapters (they can now call
  `TrackingDiagramOverride.startAction`).
- [x] **`DiagramFocus`** — ambient "emphasize these JSON fields in the next note" mechanism.
  **Done:** `io.kronikol.core.tracking.DiagramFocus` ports the .NET `DiagramFocus` — `request(String...)` /
  `response(String...)` stash field names on a `ThreadLocal`; `consumePendingRequestFocus()` /
  `consumePendingResponseFocus()` return-and-clear (consume-once); `clearAll()` for teardown. The
  `RequestResponseLog.focusFields` slot (already consumed by the byte-complete `FocusEmphasis`/
  `FocusDeEmphasis` rendering) is now populated end-to-end: the `KronikolOkHttpInterceptor` consumes both
  focus sets up-front (matching .NET, so focus set during the call doesn't leak into the response note) and
  applies them to the request/response logs; added the missing `RequestResponseLog.Builder.focusFields`
  setter. The typed `Request<T>(x => x.Field)` expression overloads are a documented C#-only boundary (no Java
  expression trees) — use the string-field-name forms. Proven by `DiagramFocusTest` (consume-once, request/
  response independence, clearAll, null-when-unset, empty-on-no-args, feeds `log.focusFields`) +
  `KronikolOkHttpInterceptorTest` (ambient focus flows onto the captured pair, consumed once across calls).
  **Note:** the other adapters consume `DiagramFocus` the same way as they wire it in.
- [x] **Assertion fidelity** — `Track.attachment(file, name)`; `Track.that` returning a value (`<T>`);
  `@SuppressAssertionTracking`; `Track.diagnosticMode` toggle + `diagnosticLog`/`clearDiagnosticLog`;
  `Track.testIdResolver` static hook; closure-value resolution + `AssertionExpressionFormatter` (readable
  "Order status should be equivalent to 'Confirmed'" text). *(C#-reflection-specific parts — closure-field
  inspection — may be a documented boundary; decide per item. `thatAsync` is N/A in Java.)*
  **Done (Java-feasible parts):** `Track.that(Supplier<T>)` + `Track.that(String, Supplier<T>)` (value-
  returning, with source-expression auto-capture; `thatAsync` N/A); the `diagnosticMode` toggle +
  `diagnosticLog()`/`clearDiagnosticLog()`/`recordDiagnostic(...)` (thread-safe), now rendered end-to-end as
  the diagnostic report's "Assertion Value Resolution" section (conditional, golden insulated); the
  `testIdResolver` static hook (`Supplier<String>`), consulted before the ambient scope in assertion
  resolution (its id used as name+id, matching .NET's override marker, with throwing-resolver fallback); and
  the `@SuppressAssertionTracking` runtime-retained marker (METHOD+TYPE). Proven by `TrackFidelityTest` +
  `DiagnosticReportGeneratorTest` (assertion-log section renders / omitted).
  **`Track.attachment` done (2026-06-28):** `Track.attachment(filePath)` / `Track.attachment(filePath, name)`
  (the .NET `Track.Attachment`) resolve the test id (the same hook→scope cascade as assertions) and forward to
  `StepCollector.addAttachment` across the core→report module boundary via a new SPI seam — core's
  `io.kronikol.core.tracking.AttachmentSink` (functional interface), provided by the report module's
  `StepCollectorAttachmentSink` (`META-INF/services`, `ServiceLoader`-discovered once); tests may install one
  explicitly with `Track.attachmentSink(...)`. No-op when no sink/identity resolves. This is the proper Java
  resolution of the .NET single-assembly direct call (core stays zero-dep). Proven by `TrackAttachmentTest`
  (core, explicit sink — id/path/name forwarding, null-name passthrough, no-op cases) + `TrackAttachmentWiringTest`
  (report, real `ServiceLoader` discovery → attachment lands on the active step / on the scenario when none).
  **Resolved as a verified C#-specific boundary (2026-06-28) → item complete `[x]`.** The two remaining pieces
  are both structurally C#-only, and the Java-appropriate equivalent is already shipped:
  - **Closure-value resolution** — .NET's `Track.AssertionFailedWithValues(expression, msg, varNames, varValues)`
    is *called by the AssertionWeaver* (the post-compile IL weaver), which emits arrays of **source variable
    names** + boxed values at the assertion site; `ResolveVariableValues` then maps dotted paths onto those
    names. Java cannot reproduce the inputs at runtime: a lambda's captured values live in **unnamed** synthetic
    fields (`arg$1`, `arg$2`) — unlike C# display-class fields named after the source variables — so there is no
    runtime name→value mapping. Supplying `varNames`/`varValues` would require a compile-time source/bytecode
    pass, which is exactly the excluded `AssertionRewriter` (Roslyn *source* rewriter) boundary already
    documented at the Build-time-weaving item. The `AssertionFailedWithValues` seam + `recordDiagnostic` log are
    in place for any future compile-time front-end.
  - **`AssertionExpressionFormatter`** — a 359-line formatter that parses **FluentAssertions** `.Should().Method(args)`
    *C# syntax* into English. Java has no FluentAssertions; the AssertJ equivalent (`assertThat(x).isEqualTo(y)`)
    is a different surface, and the **runtime assertion agent already captures and renders the readable
    assertion** — actual + expected values **and** the source expression — into the note (proven by
    `AssertionAgentTest`). So the readable-assertion requirement is met the Java-appropriate way; porting the
    FluentAssertions-syntax parser would format expressions that never occur in Java.
  Everything portable on this item is done: `Track.that`/`Track.that<T>` with source-expression capture,
  `Track.attachment` (via the `AttachmentSink` SPI seam), `diagnosticMode`/`diagnosticLog`/`recordDiagnostic`,
  the `testIdResolver` hook, `@SuppressAssertionTracking`, and the Tier-2 agent's value+expression capture.
- [x] **`TrackingTraceContext`** (`beginTrace`/`createParentContext`) — creates a new ambient trace id and
  builds a parent span context for the proxy's `ActivitySource` (the *write* counterpart to the read-only
  `OtelBridge`). Pairs with the `TrackingProxy` span-lifecycle work. *(.NET `Tracking/TrackingTraceContext.cs`.)*
  **Done, split across the dependency boundary:** the ambient part is `io.kronikol.core.tracking.
  TrackingTraceContext` (zero-dep) — `currentTraceId()` + `beginTrace()` returning an `AutoCloseable`
  `TraceScope` (with `traceId()`) that restores the previous id on close, so traces nest (the .NET
  `BeginTrace`/`BeginTrace(out)` overloads collapse into the scope's accessor). The OTel part is
  `io.kronikol.opentelemetry.OtelTraceContext.createParentContext()` (kept in the opentelemetry module so core
  stays zero-dependency) — builds a sampled **remote** parent `SpanContext` whose 32-hex trace id derives from
  the current trace UUID (with a fresh random span id), or `SpanContext.getInvalid()` when no scope is active
  (the .NET `default(ActivityContext)`); complements the read-only `OtelBridge`. Proven by
  `TrackingTraceContextTest` (push/restore nesting, currentTraceId) + `OtelTraceContextTest` (invalid-when-no-
  trace, valid sampled-remote parent with UUID-derived trace id + valid span id). The proxy span-lifecycle
  consumer is the OTel-coupled InternalFlow-capture follow-up.
- [x] **`TestTrackingServerBridge.getCurrentTestInfo()`** — expose the server-side "read test identity from
  the current request" logic as a public API (today it's internal to `KronikolServletFilter`).
  **Done:** `io.kronikol.servlet.TestTrackingServerBridge` (public) ports the .NET
  `TestTrackingServerBridge.GetCurrentTestInfo` — `getCurrentTestInfo(HttpServletRequest)` and a
  source-agnostic `getCurrentTestInfo(UnaryOperator<String> headerLookup)` overload read the
  `kronikol-current-test-name`/`-id` headers (reusing the internal `ServletIdentity` extractor) and return
  the `TestInfo`, or {@code null} when there is no request/lookup or the headers are absent <em>or blank</em>
  (matching .NET's `IsNullOrEmpty` semantics — stricter than the filter's null-only check). Proven by
  `TestTrackingServerBridgeTest` (request + header-lookup reads, absent headers, blank name/id, null
  request/lookup).
- [x] **`ITabularParameterData`** — the interface for supplying tabular data as a *step parameter* (distinct
  from the TabularAttributes declaration feature; consumed by step tracking). *(.NET
  `Tracking/Tabular/ITabularParameterData.cs`.)*
  **Done:** `io.kronikol.report.step.TabularParameterData` (the Java name drops the `I` prefix) ports the
  interface — `getColumns()`/`getRows()` (the report-model `TabularColumn`/`TabularRow`) + a default
  `isLinkedOutput()`. Wired into `StepCollector.buildParameters`: a step parameter value that is a
  `TabularParameterData` now becomes a `TABULAR` `StepParameter` (`TabularParameterValue(columns, rows,
  isLinkedOutput)`) instead of an inline value — completing the previously-deferred tabular-param branch of
  the Step-tracking item. Proven by the new `StepCollectorTest` tabular case. The `TabularInputs<T>`/
  `TabularOutputs<T>` carriers that implement it land with the Tier-4 TabularAttributes item.
- [x] **`TrackingHttpMessageHandlerBuilderFilter` analog** — auto-inject tracking into every framework-
  created HTTP client (Spring Boot starter currently covers only `RestTemplate`).
  **Done:** the Spring Boot starter's `KronikolAutoConfiguration` now auto-injects tracking into all three
  framework-created HTTP clients — the Java analog of .NET's `IHttpMessageHandlerBuilderFilter`: the existing
  `RestTemplateCustomizer` plus a new `RestClientCustomizer` (reuses the same
  `KronikolRestTemplateInterceptor`, which is a `ClientHttpRequestInterceptor`, on every `RestClient.Builder`)
  and a `WebClientCustomizer` (adds `KronikolWebClientFilter` to every `WebClient.Builder`). Each bean is
  `@ConditionalOnClass`-guarded so it activates only when that client is on the user's classpath (added
  spring-webflux as `compileOnly`). Proven by `KronikolAutoConfigurationTest` (all three customizer beans
  registered by default; the RestClient/WebClient customizers actually add our interceptor/filter to a built
  builder). **Note:** .NET's lower-level `IHttpMessageHandlerBuilderFilter` hooks `IHttpClientFactory`; the
  idiomatic Spring equivalent is these per-builder customizers (Spring Boot has no single handler-builder seam).
- [x] **`UnmatchedClientNameRegistry`** — diagnostic registry of unresolved client names (feeds the
  diagnostic report).
  **Done:** `io.kronikol.core.tracking.UnmatchedClientNameRegistry` ports the .NET registry — thread-safe
  `record(clientName)` (counts), `getRecordedNames()` (ordered by count descending, ties stable by insertion
  order to match .NET's stable `OrderByDescending`), and `clear()`. Wired to the existing
  `ServiceNameResolver.onUnmatchedClientName` seam (an adapter passes `UnmatchedClientNameRegistry::record`).
  Now that the portable registry exists, the `DiagnosticReportGenerator` renders the previously-deferred
  "⚠ Unmatched HTTP Client Names" section (the warning table + fix guidance), conditional on a non-empty
  registry so the byte-for-byte golden (empty registry) is unaffected. Proven by
  `UnmatchedClientNameRegistryTest` (record/count/ordering/ties/clear/null + the `ServiceNameResolver` wiring)
  and the diagnostic-report tests (section renders with entries, omitted when empty, golden still byte-equal).

---

## Tier 5 — Tooling & onboarding

- [x] **Maven plugin** — a Mojo mirroring `kronikol4j-gradle-plugin` (fork dir + merge task). Maven users
  currently have only the CLI.
  **Done:** new `kronikol4j-maven-plugin` module with `KronikolReportMojo` (goal `kronikol4j:report`, bound to
  the `verify` phase) — merges the report fragments emitted by forked test JVMs into one HTML report by
  delegating to the same `MergeCommand` engine as the CLI (and the Gradle task), with `fragmentsDir`/
  `outputHtml`/`title` parameters; it creates the output directory before merging and treats the
  no-fragments exit code as a no-op. Because Gradle builds this module (no `maven-plugin-plugin`), the Maven
  descriptor `META-INF/maven/plugin.xml` is hand-authored, with its `@version@` token filtered to the build
  version by `processResources`. Forked JVMs emit fragments when `kronikol.run.dir` is set via Surefire/
  Failsafe `systemPropertyVariables` (documented in the wiki + Mojo javadoc — the Maven analog of the Gradle
  plugin auto-setting it on `Test` tasks). Proven by `KronikolReportMojoTest` (end-to-end merge of a real
  fragment → HTML, missing/empty fragments-dir no-op, descriptor packaged + version-filtered) + wiki page.
- [x] **Project templates / archetypes** — the `dotnet new kronikol-*` analog (Maven archetype / `gradle
  init` skeleton) for each test-framework combo.
  **Done (decision + delivered starters):** Java has no `dotnet new`-style template **registry** — Gradle's
  `init` ships only built-in types, and Kronikol4J is Gradle-first — so the faithful analog is **copyable
  starter skeletons** committed under `templates/`. Added `templates/kronikol4j-junit5-gradle` (Gradle Kotlin
  DSL: applies the `io.kronikol.kronikol4j` plugin, JUnit 5, `kronikol4j-junit5` + `kronikol4j-http`, a
  `BaseComponentTest` wired with `@ExtendWith(KronikolExtension.class)`, and a sample `TrackingHttpClient`
  test) and `templates/kronikol4j-junit5-maven` (the Maven equivalent: Surefire `kronikol.run.dir` +
  `kronikol4j-maven-plugin` `report` goal). Both cover the build-tool × primary-framework matrix; TestNG /
  Cucumber follow the same shape (swap the integration dep + base class — documented). The starters are
  template *resources* (not wired into the Gradle build, exactly like .NET's `templates/` sources), with a
  `templates/README.md` index + a wiki **Project Starters** page. **Decision:** a Maven archetype
  (`mvn archetype:generate`) is the more *instantiable* form but needs the `maven-archetype-plugin` to build a
  valid descriptor (awkward in this Gradle repo, Maven-users-only) — noted as a possible future addition; the
  copyable starters already serve both Gradle and Maven.
- [x] **Build-time weaving auto-wiring** — the assertion/step weavers as Gradle/Maven tasks, so users don't
  need an explicit `-javaagent:` argument (the ByteBuddy agent exists but isn't auto-wired). .NET ships
  three distinct build packages: `Kronikol.StepTracking` (`.targets` that codegen the step attributes + run
  the IL weaver after compile, gated by `<TrackStepsEnabled>`), `Kronikol.AssertionTracking` (Mono.Cecil IL
  weaver + `@TrackAssertions`/`@SuppressAssertionTracking` codegen), and `Kronikol.AssertionRewriter` (a
  Roslyn *source* rewriter running before compile). Decide the Java equivalent for each (AST/bytecode pass)
  or document any as an explicit boundary.
  **Done:** the Gradle plugin now auto-attaches the assertion agent. New `kronikol { attachAssertionAgent =
  true; assertionAgentCoordinates = "…" }` opt-in: the plugin creates a resolvable `kronikolAssertionAgent`
  configuration that (lazily, via `addAllLater` gated on the flag) declares the agent dependency — defaulting
  to the plugin's own `io.github.lemonlion:kronikol4j-assertj-agent:<version>` — and registers a
  `jvmArgumentProvider` on every `Test` task that resolves the agent jar and emits `-javaagent:<jar>
  -Dnet.bytebuddy.experimental=true`. The arg-computation is factored into the pure `AssertionAgentArgs`
  helper (unit-tested: disabled→none, no-jar→none, enabled→both args) and the wiring is tested via
  `ProjectBuilder` (`KronikolPluginTest`: dep declared only when opted in, provider registered, default off).
  **Maven auto-attach done (2026-06-28) → item complete `[x]`:** the Maven plugin now mirrors the Gradle
  auto-attach with a `prepare-assertion-agent` goal (`PrepareAssertionAgentMojo`, bound to the `initialize`
  phase, `threadSafe`) — it resolves the agent jar from `${plugin.artifactMap}` (default key
  `io.github.lemonlion:kronikol4j-assertj-agent`, declared as a plugin dependency so it lands there) and
  prepends `-javaagent:<jar> -Dnet.bytebuddy.experimental=true` to the `argLine` project property Surefire/
  Failsafe read — the same pattern as `jacoco:prepare-agent`. Opt out with `-Dkronikol.skipAssertionAgent=true`.
  The arg-line computation is the pure `AssertionAgentArgLine` helper (unit-tested), wired in the Mojo + the
  hand-authored `plugin.xml`. Proven by `AssertionAgentArgLineTest` + `PrepareAssertionAgentMojoTest` (resolves
  jar → sets argLine, prepends to existing, skip no-op, missing-agent no-op).
  **Documented boundary (not a gap):** .NET's two *compile-time* rewriters have no auto-wired Java equal —
  `StepTracking`'s attribute codegen + post-compile IL weave and `AssertionRewriter`'s Roslyn *source* rewrite
  are the C#-IL / source-AST boundary already documented at the assertion/diagnostic items (the runtime
  ByteBuddy agent — now auto-attached on **both** build tools — is the IL-weaver analog; closure-value
  resolution / readable-assertion substitution stays the documented gap on the Assertion-fidelity item). The
  actionable scope of this item (auto-wire the agent for Gradle + Maven) is complete.
- [x] **Kafka build-interception package** — `Kronikol.Extensions.Kafka.BuildInterception`. Decide
  Gradle/Maven equivalent.
  **Premise corrected by reading the source:** it is **not** MSBuild interception targets — it is a **Harmony
  runtime monkey-patch** (`KafkaBuildInterceptor.cs`, `Lib.Harmony` dependency) that postfix-patches
  `ConsumerBuilder<TKey,TValue>.Build()` / `ProducerBuilder<TKey,TValue>.Build()` so each returns a
  tracking-wrapped `IConsumer`/`IProducer` — zero-production-code-change Kafka tracking (the runtime swaps the
  builder's return value).
  **Decision (documented boundary):** there is **no faithful Java auto-swap analog**, for two structural
  reasons: (1) the Apache Kafka *Java* client has no `ConsumerBuilder`/`ProducerBuilder` with a `.Build()` —
  consumers/producers are constructed directly (`new KafkaConsumer<>(props)`); and (2) a JVM constructor
  cannot return a substitute instance, so the "swap the return value" technique Harmony uses on `Build()`
  cannot be reproduced on a constructor even with ByteBuddy (the project's Harmony/IL-weaver analog). The
  supported Java paths instead are: **(a)** the explicit `TrackingKafkaProducer`/`TrackingKafkaConsumer`
  decorators in `kronikol4j-messaging` (already present — the `KafkaTrackingInterceptor.WrapConsumer/
  WrapProducer` analog, zero SDK dependency); and **(b)** for zero-call-site-change wiring, decorating Spring
  Kafka's `ConsumerFactory`/`ProducerFactory` (whose `createConsumer()`/`createProducer()` return the
  `Consumer`/`Producer` *interfaces* — the real Java seam the .NET builder occupies) via a Spring
  `BeanPostProcessor`. This is the same "Spring DI decoration replaces .NET build/IL interception" idiom
  already locked in for `ServiceCollectionDecoratorExtensions`. **No separate build-interception package /
  Gradle/Maven task is warranted.** The Spring-Kafka factory-decorator auto-config was delivered with the Kafka
  adapter (`KronikolKafkaFactoryBeanPostProcessor`) — only relocated to its proper home, not skipped.
  Documented in the wiki Kafka section.
- [x] **CLI distribution form** — fat-jar is built; decide on `jbang` / `jreleaser` packaging and a
  `dotnet tool install`-equivalent one-line install (PORT_PLAN Appendix B).
  **Done:** the fat-jar wasn't actually wired (only a `Main` class existed) — added a `fatJar` Gradle task to
  `kronikol4j-cli` (Main-Class `io.kronikol.cli.Main` + all runtime deps bundled, signature files excluded,
  hooked into `assemble`), producing the runnable `kronikol4j-cli-<version>-all.jar`. **Decision (Appendix B):**
  **jbang** is the chosen one-line-install form (the `dotnet tool install` analog) — it resolves the published
  jar + transitive deps and runs the Main-Class, needing no extra release pipeline; added a repo-root
  `jbang-catalog.json` (`kronikol4j` alias → the `kronikol4j-cli` GAV + Main-Class). jreleaser/native-image
  was considered but not adopted (documented in the wiki) — needless release infra for a small JVM CLI.
  Proven by `CliDistributionTest`, which builds the fat-jar (the `test` task depends on `fatJar`) and spawns
  `java -jar <fatjar>` end-to-end: merges a real fragment → HTML, plus the usage/exit-code contract (0/2/3).
  Wiki page added.

---

## Tier 6 — Minor helpers, exposed-API gaps & wiring fixes

Small but real parity items — convenience helpers, factory methods, and "the code exists but isn't wired in"
fixes. Listed for completeness so nothing is silently dropped.

- [x] **`PhaseVariantExtensions`** (`attachVariants`/`withVariants`) — the helper that conditionally computes
  + sets `setupVariant`/`actionVariant` on a log (only when phase is Unknown and a verbosity override is
  configured). The fields exist on the log; the helper that populates them does not. *(.NET
  `PhaseVariantExtensions.cs:24`.)* **Done:** `io.kronikol.core.tracking.PhaseVariantExtensions` (static
  generic helpers, since Java has no extension methods); both no-op guards ported (phase ≠ Unknown → skip;
  no override → skip) and per-override fallback to base verbosity. Proven by `PhaseVariantExtensionsTest`.
- [x] **`TestInfoResolver.createHttpFallbackFetcher`** — the static factory producing a combined
  "HTTP-headers-first, then delegate" identity fetcher. *(.NET `TestInfoResolver.cs:86`.)*
  **Done:** `TestInfoResolver.createHttpFallbackFetcher(UnaryOperator<String> headerLookup,
  Supplier<TestInfo> fallback)` returns a `Supplier<TestInfo>` that reads `CURRENT_TEST_NAME` +
  `CURRENT_TEST_ID` from the request headers and falls back to the delegate when either is absent — the
  .NET `CreateHttpFallbackFetcher` analog. .NET's `IHttpContextAccessor` is replaced by a platform-neutral
  `UnaryOperator<String>` (header name → value) so `kronikol4j-core` stays HTTP/servlet-API-free; adapters
  bind it to their request (e.g. `request::getHeader`). Mirrors .NET's edge handling: both headers required,
  and a `null`/throwing lookup swallows and delegates. Proven by `TestInfoResolverTest` (4 new cases:
  prefers headers, falls back when absent / only one header / null+throwing lookup).
- [x] **`PhaseConfiguration.resolvePhaseFromStepType`** — expose the Given/And/But→Setup, When/Then→Action
  mapping on `PhaseConfiguration` (the logic exists only inside the Cucumber module's `GherkinPhase`).
  **Done:** `PhaseConfiguration.resolvePhaseFromStepType(String)` ports the .NET stateless mapping verbatim —
  `null`→Unknown; case-insensitive *prefix* match (`StartsWith(OrdinalIgnoreCase)` → `toUpperCase(Locale.ROOT)`
  + `startsWith`, so `"Given that …"` resolves) on `GIVEN`/`AND`/`BUT`→Setup, `WHEN`/`THEN`→Action, else
  Unknown. This is the .NET `ResolvePhaseFromStepType` analog and is deliberately distinct from the Cucumber
  module's `GherkinPhase.forKeyword`, which is the *stateful* variant where `And`/`But` inherit the current
  phase (correct for live Gherkin step streams) — both are kept. Proven by `PhaseConfigurationTest` (4 new
  cases: Given/And/But→Setup, When/Then→Action, case-insensitive prefix, null/empty/unmatched→Unknown).
- [x] **`ProcessingCorrelation` naming/signature parity** — `wrapSync` named alias + the cancellation-signal
  parameter on the batch wrapper (Java's batch wrapper omits it). *(.NET `ProcessingCorrelation.cs:41`.)*
  **Done:** (1) `wrapSync(Consumer<T>, keySelector)` added as the .NET `WrapSync` naming alias of the existing
  `wrap` (Java's `wrap` already *is* the synchronous `Action<T>` form; the alias gives API-name parity).
  (2) Cancellation-aware overloads of both async wrappers — `wrapAsync(BiFunction<T, BooleanSupplier,
  CompletionStage<Void>>, …)` and `wrapBatchAsync(BiFunction<Collection<T>, BooleanSupplier,
  CompletionStage<Void>>, …)` — the parity twins of .NET's `Wrap`/`WrapBatch` which thread a
  `CancellationToken` to the handler. Java has no universal cancellation token (documented boundary, cf.
  `TrackingSerializerOptions`' `FilterCancellationTokens` note), so the cooperative signal is modelled as a
  `BooleanSupplier` (`getAsBoolean()` ≡ `CancellationToken.IsCancellationRequested`); the wrapper establishes
  the scope (batch: from the first correlatable item) and forwards the signal unchanged. The token-less
  forms are kept (Java idiom). Proven by `ProcessingCorrelationTest` (3 new cases: `wrapSync` scope+clear,
  per-item + batch async signal forwarding).
- [x] **Wire `DiagnosticReportGenerator` into `ReportFinalizer`** — the diagnostic generator is fully ported
  but never triggered from the finalization path (.NET invokes it when diagnostic mode is on and there are
  logs but no test contexts). Depends on the `diagnosticMode` toggle (Tier 2).
  **Done:** added the `diagnosticMode` toggle to `ReportOptions` (6th record component, default `false`, with
  `withDiagnosticMode`, the `kronikol.report.diagnosticMode` system property in `fromSystemProperties`, and
  Gradle-DSL exposure via `KronikolExtension.getDiagnosticMode`) and wired `ReportFinalizer` to write
  `DiagnosticReport.html` at both .NET trigger points: (1) the normal path — after the main report — and
  (2) the empty path — when `RunResults.isEmpty()` but tracked logs exist (the "logs recorded but no test
  contexts" case, where the main report is skipped but the diagnostic still explains the empty result). The
  "Configuration" dump reflects the actual `internalFlowTracking` toggle and defaults the rest to the .NET
  baseline until their owning flags land. Proven by `ReportFinalizerTest` (4 new cases: normal-path write,
  empty-path write, off-by-default, system-property read) with the full suite + goldens still green.
- [x] **CLI merge title resolution** — `kronikol4j merge` title behavior now matches .NET. **Premise
  corrected by reading the source:** .NET does *not* derive the title from CI metadata — `MergeableReport`
  has **no title field at all** (verified: no `Title` in `MergeableReport.cs`/`MergeableReportMerger.cs`/
  `MergeableReportReader.cs`), and `MergeableReportRenderer.Render` resolves `title ??= "Test Run Report"`
  from the CLI `-t` arg alone. The real divergence: Java's `ReportFragment` *does* carry a `title` (set by
  the forked runner's `ReportFragments.fromRun(title)`) which round-trips through the fragment JSON, so a
  merge **without** `-t` surfaced the first fragment's title instead of the default. **Fix:**
  `MergeCommand` now applies the resolved title to the merged fragment *unconditionally* (`withTitle(title)`,
  `null` when `-t` absent), clearing any fragment-carried title so the renderer's `"Test Run Report"` default
  applies — exactly mirroring .NET (the renderer owns the default; merge never leaks fragment titles). The
  Gradle/Maven plugins are unaffected (both always pass `-t`, defaulting to `"Test Run Report"`). Proven by
  `MergeCommandTest.defaultsTitleAndIgnoresFragmentCarriedTitleWhenNoFlagGiven` (+ the existing `-t` override
  test stays green).

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
