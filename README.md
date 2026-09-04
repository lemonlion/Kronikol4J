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
