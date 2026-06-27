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

- [x] **Verbosity framework** — shared `TrackingVerbosity` enum + per-tracker resolution logic.
  **Resolved:** the .NET source of truth defines exactly three levels (`Raw` / `Detailed` / `Summarised`),
  duplicated across `MessageTrackerVerbosity` + `SqlTrackingVerbosityLevel`; the `HeadersOnly` / `None`
  levels originally floated here do **not** exist anywhere in `c:\Code\Kronikol\src\Kronikol`, so they were
  deliberately not modelled (an unbacked enum member would be a stub). Java now has
  `io.kronikol.core.tracking.TrackingVerbosity` (RAW/DETAILED/SUMMARISED + `DEFAULT`, `includesPayload()`,
  `includesRawDetail()`). The per-tracker resolution logic already existed as the generic
  `PhaseConfiguration.effectiveVerbosity(...)` / `shouldTrack(...)`; `TrackingVerbosity` composes with it.
  Per-tracker *wiring* lands with each Tier-1 adapter. Proven by `TrackingVerbosityTest`.
- [~] **Phase-aware tracking suppression** — wire `PhaseConfiguration.shouldTrack()` (already in
  `kronikol4j-core`) into every extension execution path, honoring `TrackDuringSetup` / `TrackDuringAction`
  + `SetupVerbosity` / `ActionVerbosity`. Currently no Java tracker consults phase at all.
  **Primitives complete:** `shouldTrack(...)`, `effectiveVerbosity(...)` (both in `PhaseConfiguration`,
  tested) and now `PhaseVariantExtensions.attachVariants/withVariants` (tested) are all in place. The
  remaining work is the *per-adapter wiring*, which is intentionally deferred: no Java tracker yet exposes
  `TrackDuringSetup/Action` or `Setup/ActionVerbosity` options (those option surfaces are the Tier-1 adapter
  + Tier-2 option items). Each Tier-1 adapter wires these primitives in as it is built; this box flips to
  `[x]` once every execution path consults them.
- [x] **Service-name resolution chain** — `PortsToServiceNames`, `ClientNamesToServiceNames` (with
  suffix/contains fallback for generated client names), `FixedNameForReceivingService`, `ExcludedHosts`.
  Used by HTTP + cloud adapters. (.NET `TestTrackingMessageHandler.cs:58-139`.)
  **Done:** `io.kronikol.core.naming.ServiceNameResolver` ports the full 4-step `ResolveServiceName` chain
  (fixed → exact client → fuzzy endsWith-with-boundary then assembly-qualified-only contains → port →
  `localhost:<port>`), preserving insertion order for deterministic fuzzy matching; `ExcludedHosts` ports the
  OrdinalIgnoreCase host-exclusion check. The unmatched-client-name recording is exposed as an injectable
  callback seam (the `UnmatchedClientNameRegistry` itself is a separate Tier-4 item). Per-adapter wiring
  lands with the Tier-1 HTTP/cloud adapters. Proven by `ServiceNameResolverTest` + `ExcludedHostsTest`.
- [~] **Operation classifiers** — per-protocol command/operation classification (SQL, Redis, Mongo,
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
- [~] **Deferred flush** — `DeferredLogFlushHandler` + `PendingRequestResponseLogs`: queue logs emitted
  before identity is known, flush once it resolves. (.NET `Tracking/DeferredLogFlushHandler.cs`.)
  **Mechanism done:** `io.kronikol.core.tracking.PendingRequestResponseLogs` (thread-safe queue:
  `enqueue`/`count`/`flushAll`/`clear`) + `PendingLogEntry` (record + builder). `flushAll(name, id, ids)`
  drains the queue, emitting each entry as a request+response pair sharing one trace/request-response id
  (from the `IdGenerator` determinism seam) through `RequestResponseLogger`. Proven by
  `PendingRequestResponseLogsTest`. **Remaining:** the `DeferredLogFlushHandler` itself is an HTTP-client
  `DelegatingHandler` (flushes after each response) — HTTP-specific, so it lands with the Tier-1 HTTP adapter
  (and the proxy's deferred `TrackingLogMode` consumes the same queue).

---

## Tier 1 — Turn the existing "recorder" modules into real auto-capturing adapters

Today these expose `record(...)`/`publish(...)` you call by hand. .NET ships SDK hooks that capture
automatically. Each needs: the real wire adapter + operation classification + verbosity + phase-awareness.

- [~] **HTTP** (`kronikol4j-http`, `-spring`) — add real client adapters: **OkHttp interceptor**, **JDK
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
  (JDK connector + MockWebServer). **Remaining (2 small bits):** (a) WebClient *request*-body capture — its
  body is a write-only reactive `BodyInserter`, readable only at the `ClientHttpConnector` layer (OkHttp/JDK
  capture both bodies); (b) arbitrary `headersToForward` propagation from an incoming request context
  (servlet-coupled; overlaps the Tier-2 `TestTrackingMessageHandlerOptions` item).
- [~] **SQL / JDBC** (`kronikol4j-jdbc`) — wrap `DataSource`/`Connection`/`Statement`/`ResultSet`; multi-
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
  statements) + `SqlResultSummaryTest`. **Remaining (tracked follow-ups):** `FULL_ROWS` cell-level JSON
  capture; untyped `execute(...)`/`executeBatch()` tracking; a golden-rendered proof; per-driver
  `DependencyCategory` defaults; the ClickHouse/Spanner/Bigtable modules consume this same plumbing.
- [~] **Redis** (`kronikol4j-redis`) — Lettuce/Jedis command hook; `RedisOperationClassifier` (25+
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
  GET hit/miss, SET, Object-method pass-through). **Remaining:** a Jedis wrapper, per-connection database-
  number extraction (currently 0), and a golden-rendered proof.
- [~] **MongoDB** (`kronikol4j-mongodb`) — register a driver `CommandListener` (the `IEventSubscriber`
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
  the correlation key matches across runtimes. **Remaining:** document-preview JSON byte-parity (golden) +
  change-stream end-to-end proof.
- [~] **Kafka / messaging** (`kronikol4j-messaging`) — **producer/consumer wrappers that stamp + read
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
  records still returned, no scope leakage). **Remaining:** Subscribe/Commit/Flush/Unsubscribe/Assign op
  tracking, `isCurrentRequestFromMyHost()`, `ITrackingComponent` self-registration, and a golden proof.
- [~] **`TrackingProxy` enhancements** (`kronikol4j-proxy`) — `TrackingLogMode` (Immediate **+ Deferred**,
  integrating `PendingRequestResponseLogs`); `ActivitySource`/OTel span lifecycle for InternalFlow span
  production (`InternalFlowSpanStore.complete(...)`); configurable `uriScheme` (hardcoded `proxy://local/`)
  and `activitySourceName`; `TrackingSafeSerializer` options. *(.NET `TrackingProxy.cs:25,53-116`.)*
  **Done:** added `TrackingLogMode` (Immediate/Deferred) — Deferred captures each call into
  `PendingRequestResponseLogs` (flushed once identity resolves), no identity needed at call time; configurable
  `uriScheme` (was hardcoded `proxy://local`); the `IdGenerator` determinism seam for trace/request-response
  ids; and a pluggable `payloadSerializer` (default `String.valueOf`, plug a `TrackingSafeSerializer`-backed
  function for JSON). `ProxyOptions` gained the matching `withUriScheme`/`withLogMode`/`withIds`/`withSerializer`/
  `withTestInfoFetcher` builders. Proven by `TrackingProxyEnhancementsTest` (existing e2e test stays green via
  the default serializer). **Remaining:** the `ActivitySource`/OTel span lifecycle for InternalFlow span
  production (`InternalFlowSpanStore.complete`) + `activitySourceName` — OTel-coupled, lands with the
  InternalFlow capture item.
- [~] **gRPC** (`kronikol4j-grpc`) — extend beyond unary to **server-streaming, client-streaming, duplex**;
  Protobuf→JSON; `traceparent` injection; gRPC-status→HTTP-status mapping; verbosity. *(.NET
  `GrpcTrackingInterceptor.cs` overrides 5 call types; Java handles 1.)*
  **Done:** the Java `ClientInterceptor` is generic over all four call types (gRPC's `ClientCall` abstraction
  is uniform, so the existing send/receive hooks already cover streaming structurally). Added
  `GrpcOperationClassifier` (+ `GrpcOperation`) — classifies the `MethodType` and labels streaming calls
  (`Subscribe (server-stream)` / `(client-stream)` / `(duplex-stream)`); `GrpcStatusMapping` — the
  gRPC-status→HTTP-status port (NOT_FOUND→404, PERMISSION_DENIED→403, UNAUTHENTICATED→401, …, default 500).
  The interceptor now injects a W3C `traceparent` (reusing `W3CTraceparent`), maps the close status via
  `GrpcStatusMapping`, and labels via the classifier. Proven by `GrpcStatusMappingTest` +
  `GrpcOperationClassifierTest`. **Remaining:** Protobuf→JSON message rendering (currently the protobuf
  `toString`), verbosity wiring on the interceptor options, and a golden proof.
- [~] **Elasticsearch** (`kronikol4j-elasticsearch`) — SDK callback hook; operation classification;
  verbosity. *(.NET `ElasticsearchTrackingCallbackHandler`.)*
  **Classifier done:** `ElasticsearchOperationClassifier` (+ `ElasticsearchOperation`,
  `ElasticsearchOperationInfo`) ports the full .NET classifier — HTTP method + URL-path → one of 24
  operations (document CRUD by id, `_doc`/`_update`/`_search`/`_count`/`_bulk`/`_mapping`/`_refresh`,
  index create/delete/exists, `_cluster/health`, `_cat`, `_msearch`, `_reindex`, `_index_template`,
  scroll), with index/document-id extraction, directional-arrow diagram labels, and the
  `elasticsearch:///index` URI builder. Pure logic (no ES SDK dep). Proven by
  `ElasticsearchOperationClassifierTest` (6 cases). **Remaining:** the ES Java client callback/transport hook
  that feeds the classifier + emits the log pair, verbosity wiring, and a golden proof.
- [~] **AWS** (`kronikol4j-aws`) — real `ExecutionInterceptor` (AWS SDK v2) for S3/DynamoDB/SQS/SNS;
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
  parity.) Proven by `AwsServiceRouterTest` (5 cases). **Remaining:** the AWS SDK v2 `ExecutionInterceptor`
  that maps the SDK request `Context` → the router → emits the log pair, verbosity/phase wiring, and a golden
  proof.
- [~] **Azure** (`kronikol4j-azure`) — SDK pipeline policies for Cosmos (+ operation classification,
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
  **All three Azure service classifiers (Cosmos, Blob, Service Bus) are now done. Remaining:** the Azure SDK
  pipeline policies that feed them + emit the log pair, `autoCorrelateWrites`/change-feed key extractor, and a
  golden proof.
- [~] **GCP** (`kronikol4j-gcp`) — SDK adapters for BigQuery, Cloud Storage, Pub/Sub; per-service
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
  Cloud Storage) are now done. Remaining:** the GCP SDK adapters that feed them + emit the log pair, and a
  golden proof.

---

## Tier 2 — Configuration / options surface

Per-tracker option classes are mostly ~3-of-N fields; several whole option classes are absent.

- [~] **`ComponentDiagramOptions`** (entire class MISSING) — `fileName`, `embedInTestRunReport`, `title`,
  `plantUmlTheme`, `participantFilter`, `relationshipLabelFormatter`, `showRelationshipFlows`,
  `relationshipFlowStyle`, `showSystemFlameChart`, `lowCoverageThreshold`, `arrowColorMode`,
  `dependencyColors`, `maxFlameChartTests`. *(.NET `ComponentDiagram/ComponentDiagramOptions.cs`.)*
  **Class done:** `io.kronikol.report.component.ComponentDiagramOptions` (builder-based) ports all 13 fields
  with the .NET defaults; plus the missing `io.kronikol.diagram.component.ArrowColorMode` enum
  (DEPENDENCY_TYPE/PERFORMANCE). Placed in the report module (it references report's `InternalFlowDiagramStyle`
  and composes diagram's `ArrowColorMode`/`ComponentRelationship`; report→diagram avoids a dependency cycle).
  Proven by `ComponentDiagramOptionsTest`. **Remaining:** wiring it through the report orchestration
  (`HtmlReportGenerator`/`ComponentDiagramGenerator`) so the options actually drive generation — lands with the
  report control-flags wiring — plus a golden proof.
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
- [ ] **Report control flags** — `testRunReportTitle` (currently hardcoded `"Kronikol4J Test Run"`),
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
- [ ] **Per-report-type data formats** — split the single `ReportOptions.dataFormats` set back into the
  two .NET options `testRunReportDataFormat` vs `specificationsDataFormat` (different formats per report
  type). *(Depends on the Specifications report, Tier 4.)*
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

- [~] **ORM / EF-Core analog** — Hibernate `StatementInspector` (+ JPA/Spring Data hook). This is the
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
  JPA `DataSource` with the existing JDBC `TrackingDataSource` (documented in the wiki); a dedicated
  Spring-Data auto-registration helper + a golden-rendered proof are the follow-ups.
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

- [x] **`PhaseVariantExtensions`** (`attachVariants`/`withVariants`) — the helper that conditionally computes
  + sets `setupVariant`/`actionVariant` on a log (only when phase is Unknown and a verbosity override is
  configured). The fields exist on the log; the helper that populates them does not. *(.NET
  `PhaseVariantExtensions.cs:24`.)* **Done:** `io.kronikol.core.tracking.PhaseVariantExtensions` (static
  generic helpers, since Java has no extension methods); both no-op guards ported (phase ≠ Unknown → skip;
  no override → skip) and per-override fallback to base verbosity. Proven by `PhaseVariantExtensionsTest`.
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
