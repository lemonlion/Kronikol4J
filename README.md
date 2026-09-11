# Kronikol4J

A Java-native port of **[Kronikol](https://github.com/lemonlion/Kronikol)** (the .NET original) —
automatically captures real dependency interactions during tests (HTTP, SQL/NoSQL, cache, messaging,
cloud SDKs) and generates interactive HTML reports with PlantUML diagrams. Deterministic diagrams from
actual execution, not AI.

> **This repository is a fork/port of [lemonlion/Kronikol](https://github.com/lemonlion/Kronikol).** It
> re-implements the .NET reporting pipeline in Java; the report + diagram output is **byte-for-byte
> identical** to the .NET original (proven by golden-file parity tests captured from real .NET), minus
> only server-side PlantUML image rendering — diagrams render in-browser via PlantUML-WASM instead. See
> the [**Wiki**](https://github.com/lemonlion/Kronikol4J/wiki) for usage, architecture, and the parity
> boundaries.

> **Status: all green** on JDK 17–25. The full pipeline (track → diagram → HTML report), the
> fork-aggregation merge engine + CLI + Gradle plugin, three test frameworks, eleven tracking
> integrations (incl. AWS/Azure/GCP), assertion tracking (Tiers 0–1), OpenTelemetry, the Spring Boot
> starter, Maven Central publishing, and CI are implemented and tested. The entire `PlantUmlCreator`
> diagram surface and every `ReportGenerator` rendering branch are golden-proven against real .NET. See
> [CHANGELOG.md](CHANGELOG.md) for the full list and [docs/PORT_PLAN.md](docs/PORT_PLAN.md) for the
> plan, architecture, and deep-dive analyses (its §0 captures the original design-time intent). For
> **current status and how to resume work**, use [docs/REMAINING_PARITY.md](docs/REMAINING_PARITY.md) §0 —
> the cold-start runbook (repo map, the seams, build/prove commands, a worked example).

> **Known divergence (since .NET Kronikol 3.0.45, 2026-08-22).** .NET moved browser rendering off the
> main thread: its `plantuml-browser-render-script.js` now bootstraps the PlantUML engine in Web Workers
> (engine fetched as text and inlined into a Blob worker together with a new embedded
> `plantuml-worker-host.js`), caches rendered SVG per fragment source, prefetches note-toggle fragments
> (`collapsible-notes-script.js`), and exposes three `ReportConfigurationOptions`
> (`BrowserRenderWorkers`, `BrowserRenderCacheMegabytes`, `BrowserFragmentMaxHeight`) that are baked
> into the script as constants. .NET 3.0.50 (2026-08-25) additionally re-based the CDN engine on npm
> `@plantuml/core` 1.2026.6 (`…plantuml_limit_size_98304@v1.2026.6-patched`, an ES module whose trailing
> `export` every .NET consumer rewrites or `import()`s — this port's script-tag loading would need the
> same treatment, and the old `@v1.2026.3beta6-patched` tag remains published for it). .NET 3.0.59
> (2026-08-26) additionally added a note payload **JSON ⇄ YAML hover toggle** to
> `collapsible-notes-script.js` (+ a `.note-format-btn` rule in `collapsible-notes-styles.css`):
> entirely client-side reconstruction of the JSON from the note text, a token-level YAML emitter, and a
> per-note `Y`/`J` hover button riding the existing rebuild/re-render machinery — adopting it is a
> straight byte-copy of those two assets once the goldens are re-captured (the new code degrades
> gracefully without the worker engine: every 3.0.45+ hook it touches is feature-guarded). This port
> still ships the pre-3.0.45 main-thread scripts, so the report HTML is **no longer byte-identical** to
> .NET ≥ 3.0.45 in those assets (everything else is unchanged). Porting the render side means copying
> the two scripts + the worker host, JSON-escaping the host into the render script in
> `DiagramContextMenu`'s Java counterpart, and plumbing the three options. See the .NET wiki pages
> *PlantUML Browser Rendering → How Rendering Runs (3.0.45+)* and *→ Note Payload JSON ⇄ YAML Toggle
> (3.0.59+)*. .NET 3.0.66 (2026-08-29) extended the toggle further — pending sync here too: bulk
> JSON/YAML `<select>` dropdowns at report + scenario level in `collapsible-notes-script.js` (+
> `.note-format-select` rules in `collapsible-notes-styles.css`, a `__NOTE_FORMAT_DEFAULT__` token
> substituted by `DiagramContextMenu.GetCollapsibleNotesScript(NotePayloadFormat)`, and a
> `ReportConfigurationOptions.NotePayloadFormat` option threaded into `GenerateHtmlReport`'s toolbar
> emission), plus copy-text fixes in `context-menu-script.js` (YAML notes copy the displayed YAML;
> creole `~` escapes no longer leak into the clipboard in either view). .NET 3.0.79 (2026-09-04)
> extended the YAML view's display normalisation in `collapsible-notes-script.js` (trailing space/tab
> runs before line breaks and all-whitespace tails are stripped for multiline strings — client-side
> script only, no report-output impact). .NET 3.0.80 (2026-09-04) added **configurable toggle default
> start states** — a `ReportToggleDefaults` group pair on `ReportConfigurationOptions`
> (`TestRunReportToggleDefaults` / `SpecificationsToggleDefaults`, inherit-unless-overridden) resolved
> by a new `ReportToggleDefaultsResolver` into a `ResolvedToggleDefaults` record that
> `GenerateHtmlReport` consumes: the whole `collapsible-notes-script.js` globals block became `__…__`
> tokens (details state, truncate lines, headers/assertions/steps/databases, note format), the
> dependency/category filter scripts gained `_depModeDefault`/`_catModeDefault` seeds consumed by
> `report-url-hash-function.js` (now accepts `depmode=AND`/`catmode=OR` symmetrically) and
> `clear_all_filters`, the report/scenario toolbars + `open` attributes + diagram-tab/panel visibility
> are computed from the resolved record (five verbatim scenario-toolbar strings factored into one
> builder), and the search reveal opens rule/steps/background sections. Options surface, markup and
> five script assets all diverge further; this port is pinned to 3.0.43 assets. .NET 3.0.82
> (2026-09-04) added the `isComponentDiagramContainer` guard to `collapsible-notes-script.js`
> (the filter toggles — databases above all — no longer rewrite or re-render the embedded component
> diagram; client-side script only, no report-output impact) and one new `.scenario-description
> { margin: 1em 0 }` rule in `stylesheets.css` (the div previously had no rule at all — this one
> DOES change generated-HTML bytes).
> Report-**output** bytes also diverged earlier than the ledger previously recorded: .NET 3.0.77
> (2026-09-04) made live Reqnroll runs emit scenario descriptions and dedented feature descriptions
> (HTML `scenario-description` divs, YAML `Description:` lines, search corpus); .NET 3.0.78
> (2026-09-04) drew step tables/doc strings **inside** the step-delimiter bar — a new styled
> `hnote across <<stepDelimiter>><<stepBody>>: text\n|= … |` single-line form, an injected `.stepBody`
> style block per diagram, and (Cucumber ingest) pickle-substituted table values — and .NET 3.0.81
> (2026-09-04) padded each such table/doc-string block with a blank display line above and below
> (`…: text\n\n|= … |\n`, trailing `\n` included). Diagrams from a port of those capture paths would
> need the same emitter to byte-match.
>
> .NET 3.0.83 (2026-09-04) changed the **component diagram** emitter and the **SQL classifier**, both of
> which change generated bytes. The component diagram now breaks an edge label longer than 100 characters
> and a participant name longer than 80 with `\n` escapes — at `", "` boundaries first, then whitespace,
> hard-breaking only tokens carrying no creole markup — because real Java PlantUML does not wrap arrow
> labels at all and crops the drawn diagram at 4096 pixels. The two `rectangle` participant forms reopen
> their `**` bold markers on every wrapped line (`**a**\n**b**`, never `**a\nb**`, which loses the weight
> and draws a literal `**`); the plain shapes and the C4 macros take the breaks alone. Short labels and
> names are byte-identical, so only diagrams that would have cropped differ. The SQL classifier now strips
> `--` comments, `/* */` comments and `'…'` literals before extracting the operation and the table, so a
> query commented `-- rolled up from the weekly aggregate` no longer reports the table `the` and a query
> that opens with a comment no longer classifies as `Other` — that changes the arrow label and the URI
> path segment for such statements at Detailed and Summarised verbosity, and stops Summarised dropping the
> call. A port matching diagram output must carry both.
>
> .NET 3.0.84 (2026-09-11) changed the **note payload formatter** (`PlantUmlCreator.FormatNoteContent`),
> which changes generated bytes for every non-JSON **request** body. Such a body used to fall through to
> the form-url-encoded formatter, which sliced the whole string every 80 characters with `string.Chunk` —
> the payload's own newlines counted as ordinary characters, so the breaks landed mid-identifier and
> inside indentation — and turned every `&` in the payload into a `<font color="lightgray">&` divider plus
> a line break. That treatment is now gated on a body that really is form-url-encoded (one physical line
> of `key=value` with no raw newline, space or tab); everything else — SQL, XML, CSV, plain text,
> unparsed GraphQL — reaches the note as captured, on the same branch a response body takes, and is
> creole-escaped whole rather than per chunk. Genuine form bodies keep the dividers, the per-chunk escape
> and the 80-character chunk. A port matching diagram output must carry the gate: SQL notes are among the
> most common in a real report, and their bytes differ on every line longer than 80 characters.
>
> .NET 3.0.85 (2026-09-11) changes **diagram output on several width axes at once**, and adds a
> client-side-only note appearance control. A port matching diagram output must carry all of the
> generation-time half:
>
> - **Participant names** are broken onto 80-character display lines (`participant "a\nb" as x`), and
>   **user-action message labels**, **activity-diagram node labels and swimlane names**, and
>   **diagram titles** onto 100-character lines, by one shared wrapper lifted out of the component
>   diagram's 3.0.83 rules. Measured on real Java PlantUML: an 800-character participant name drew
>   5,650 px and 610 after.
> - **Internal-flow activity diagrams** gain `skinparam wrapWidth 800` in both header emitters — they
>   were the only family with neither a cap nor a wrap.
> - **Assertion notes** and **step-delimiter bars** are broken onto 110-character display lines, with
>   the break characters differing by note form: a `note … end note` block honours only REAL newlines
>   (a `\n` escape inside one draws as two literal characters and breaks nothing — measured), while
>   the single-line `hnote across <<stepBody>>: …` form honours only the escape. The ingest-side
>   assertion note therefore stops folding newlines into `\n` escapes, which also fixes multi-line
>   failure messages drawing as one long line. A body line reading exactly `end note` is neutralised
>   with a zero-width space.
> - **Step-bar table cells** are elided against a shared ROW budget (creole cells never wrap, not even
>   at spaces, and a row is the sum of its cells). A step whose label has to be broken now switches to
>   the styled `<<stepBody>>` form.
> - **Participant count** becomes a diagram-splitting trigger, and each fragment declares only the
>   participants it draws (the prefix was previously rebuilt from the whole test's traces, so every
>   fragment re-declared everyone and came out as wide as the unsplit diagram). Unlike the encoded
>   length and estimated height triggers, this one is NOT gated on client-side splitting, because
>   participant count does not change with note state.
> - **`DiagramNoteWrapWidth`** (720-4096, default 800) replaces the hard-coded `skinparam wrapWidth`
>   literal — an options-surface addition as well as an output one.
>
> Single-fragment diagrams inside the width budget — nearly all of them — are byte-identical.
>
> The same release extends the standing `collapsible-notes-script.js` divergence (client-side script
> only; the port is pinned to 3.0.43 assets) with the per-note monospace and full-width controls, their
> report/scenario dropdowns, and two new `ReportToggleDefaults` members (`NoteFont`, `NoteWidth`).
> .NET 3.0.86 (unreleased) changes **`TestRunReport.schema.json`**, which this port pins byte-for-byte
> (`ReportDataSchema.java` against the `.NET`-captured golden `testrunreport-schema.json`): `exampleFlatValues`
> and `exampleDisplayName` are now declared (the writers on both sides already emitted them), every property
> carries a `description`, `stableId` / `stepPath` / `activityTraceId` carry `examples`, and a top-level
> `$comment` names the size trap and `kronikol query`. The report JSON's bytes are unchanged; only the schema
> file differs, so re-capturing the golden and mirroring the dictionary is the whole port. The same release
> made adapter-driven .NET runs record structured diagnostics (a collector is scoped when the host did not
> scope one, so the `diagnostics` array is no longer always empty outside ingest) — the Java report finaliser
> should scope its collector the same way if it ever records entries on that path.
>
> The same release also changes `report-export-function.js` (script-only): `export_html` now appends the
> body's `application/json` data payloads to the exported file, with `#puml-data` pruned to the ids the
> exported markup contains. Without it an exported filtered report carries the whole render machinery and
> none of the diagram sources, and every diagram in it is silently blank.

> **Scope note.** The report/diagram **output rendering** is byte-for-byte complete. The **capture
> (instrumentation) breadth** and **configuration-options surface** — auto-capturing SDK adapters, per-
> tracker options, and several whole features/modules — are the remaining work toward *every-feature*
> parity. See [docs/REMAINING_PARITY.md](docs/REMAINING_PARITY.md) for the complete, prioritized gap list.

## Building

A JDK (17+) is required. Gradle is provided via the wrapper.

```bash
./gradlew build        # compile + test everything
./gradlew :kronikol4j-core:test
./gradlew printModules # list wired modules
```

The build targets **Java 17 bytecode** (via `--release 17`) so a single modern JDK suffices; it is
validated on JDK 25.

## Modules

| Module | Status | Purpose |
|---|---|---|
| `build-logic` | ✅ | Gradle convention plugin (shared config, Java 17, JUnit 5 + AssertJ, `Automatic-Module-Name`) |
| `kronikol4j-core` | ✅ tested | The stable tracking seam — `RequestResponseLog` + builder, `RequestResponseLogger`, 4-layer context/identity, `TestCorrelationStore`, registry, constants. **Zero runtime dependencies.** |
| `kronikol4j-diagram` | ✅ tested | Logs → PlantUML text — `PlantUmlCreator`, the PlantUML encoder, dependency palette, canonical JSON note formatter. Pure logic. |
| `kronikol4j-report` | ✅ tested | HTML report assembly + merge engine (`MergeableReportMerger`) |
| `kronikol4j-runtime` | ✅ tested | Result collection + report finalization + per-JVM fragment emission |
| `kronikol4j-cli` / `-gradle-plugin` | ✅ tested | Cross-fork merge (the "Merging Parallel Reports" feature) |
| `kronikol4j-junit5` / `-testng` / `-cucumber` | ✅ tested | Three test-framework adapters |
| `-proxy` / `-http` / `-jdbc` / `-servlet` / `-spring` / `-spring-boot-starter` | ✅ tested | HTTP/SQL/proxy tracking + Spring |
| `-redis` / `-mongodb` / `-messaging` / `-grpc` | ✅ tested | Cache / NoSQL / messaging / RPC tracking |
| `-aws` / `-azure` / `-gcp` | ✅ tested | Cloud SDK recorders |
| `-assertj` / `-opentelemetry` | ✅ tested | Assertion Tier 1 (zero-weave) + OTel bridge |

See [CHANGELOG.md](CHANGELOG.md) for the full module list and [docs/PORT_PLAN.md](docs/PORT_PLAN.md)
§9 Phase 5+ for the remaining roadmap (Tier-2 assertions, golden-file harness, Spock).

## Design

Three architectural seams (plan §1): **ingestion** (`RequestResponseLogger.log`), **context**
(identity/phase/correlation), and **output** (logs → PlantUML → HTML). Full parity is the end goal,
delivered core-first; the core is engineered so extensions plug in without touching it.

Key decisions (plan §0): Java 17 floor · Gradle · functional parity · browser-only rendering · Maven
group `io.github.lemonlion`, root package `io.kronikol`, `kronikol4j-*` modules.
