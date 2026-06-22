# Changelog

All notable changes to Kronikol4J are documented here. Versions follow SemVer.

## [0.1.23] — unreleased

**Final completeness pass** — a file- and method-level cross-check of the .NET `Reports` namespace against
the Java surfaced one last unported feature.

### Added — proven byte-parity for
- **`BackgroundStepsDetector`** (Gherkin Background extraction) — a deterministic, string-based algorithm
  that detects a step prefix shared across scenarios within a `Rule` group and extracts it into each
  scenario's `backgroundSteps`, trimming it from `steps` (defined in .NET `Reports/` but invoked by the
  runtime adapters, outside the report core). The .NET mutates in place; the immutable Java `Scenario`
  record rebuilds the affected scenarios. All guards ported (a `Rule` group needs ≥2 step-bearing members;
  a scenario opening with `And`/`When` skips; a zero-length common prefix skips; a remaining step
  re-opening with `Given`/`When` skips). Proven by the `report-background` golden (extraction → rendered
  Background section, byte-for-byte) + a `BackgroundStepsDetectorTest` covering the no-extract guards.
- **Coverage**: blank scenario/step names (`report-blankname`) render byte-for-byte.

### Notes — documented `.NET`-options-orchestration boundaries
- `GetTestRunReportTitle` (default `"Test Run Report"` resolution) and `ShouldEmbedComponentDiagram` both
  read `ReportConfigurationOptions` fields (`TestRunReportTitle`/`ComponentDiagramOptions.Title`/
  `FixedNameForReceivingService`) that have no Java equivalent. The Java render path is faithful for any
  given title + component diagram; the options→value resolution is the .NET runtime config layer (the same
  boundary class as the CI IO writers). With this, the **report surface is complete** — every
  `PlantUmlCreator.Create` parameter and every `ReportGenerator` input-conditional branch is golden-proven,
  minus only server-side PlantUML image rendering and the documented options-orchestration / runtime-state
  boundaries.

## [0.1.22] — unreleased

**Report re-sweep, batch 3** — continuing the audit of `ReportGenerator`'s input-conditional branches.

### Fixed
- **Scenario anchor-id dedup** — .NET pre-computes a unique anchor id per scenario, suffixing duplicate
  display names with `-N` (the first keeps the base, the next is `-2`, …; `ReportGenerator` lines 798-814).
  The Java slugged the name inline at each site, so two scenarios sharing a `DisplayName` collided on the
  same section id + deep-link target. Added `buildScenarioAnchorIds` (computed once, keyed by test id) and
  threaded it into the scenario sections, the parameterized rows, and the failure-cluster links. (The
  parameterized-group anchor stays a raw slug of the group name, matching .NET line 1601.) Golden:
  `report-dupnames`. Untested — no golden had two identically-named scenarios.

### Notes
- The tabular-param key-alignment branches (`useKeyAlignment`/`sharedKeyNames`/`IsLinkedOutput`) are
  already ported and covered by the `report-combinedtable` golden; the merge diagram/log-lookup branches
  are covered by the merge tests. Remaining minor/wrapper-level items: `GetTestRunReportTitle`'s default
  resolution (the renderer is faithful for any given title) and blank/empty scenario names.

## [0.1.21] — unreleased

**Report re-sweep, batch 2** — continuing the audit of `ReportGenerator`'s input-conditional branches
against the golden corpora, two whole report features turned out to be **unported** (invisible because no
golden supplied their triggering input).

### Added — proven byte-parity for
- **The category-filters box** — a scenario carrying `Categories` renders a `<div class="category-filters">`
  toggle set (`All` + one button per distinct, sorted category + `Uncategorized`). The Java already consumed
  categories for the `data-categories` attribute + search, but never rendered the filter box. Golden:
  `report-filterstoggles` (which also covers the Assertions/Steps/Databases toolbar toggles — diagram
  markers `<<assertionNote>>`/`<<stepDelimiter>>`/`database "` — and the dependency-filters box with a
  database participant + the scenario timeline).
- **The Failure Clusters section** — failed scenarios sharing a normalised first-line error are grouped
  (clusters of ≥2) into a collapsible `<details class="failure-clusters">` list with per-scenario deep-link
  anchors, rendered before the timeline (.NET `ReportGenerator` lines 816-840). The Java `FailureClusterer`
  (already ported for the diagnostic report) is reused. Golden: `report-failureclusters` (two clusters of
  two).

## [0.1.20] — unreleased

**Report edge-branch re-sweep** — an audit of `ReportGenerator`'s input-conditional branches against the
golden corpora (which branch does each input trigger, and does a golden supply it?). Found one real
divergence and proved several correct-but-untested branches.

### Fixed
- **Empty-feature summary class** — a feature with zero scenarios (alongside a populated one; an
  <em>all</em>-empty set bails out instead) rendered its summary as `class="h2"`, but .NET emits
  `class="h2 skipped"`: .NET's `Scenarios.All(s => Result == Skipped)` is vacuously true for an empty
  collection (as is Java's `allMatch` on an empty stream), but the Java had a spurious
  `!scenarios.isEmpty()` guard that suppressed the class. Removed it. Golden: `report-emptyfeature`.

### Proven (added goldens; the code already matched .NET)
- `report-edgefields` — a scenario with no duration (no badge/`data-duration-ms`, matching
  `Duration.HasValue == false`) and steps with no status / no duration (`Status`/`Duration.HasValue`).
- `report-paramedge` — `FormatDisplayValue`'s literal-`"null"` (`<pre>null</pre>`) and whitespace-only
  (`<pre>{ws}</pre>`) branches.

## [0.1.19] — unreleased

**HTML-escaping fidelity** — a report-level corpus-coverage audit (no golden ever fed a name/value with
special characters) found that the `HtmlEscaper` — used at the report's 60+ escaping call sites — diverged
from .NET's `System.Net.WebUtility.HtmlEncode` in four ways. The **application** was already correct (every
call site escapes); the bug was the **encoder** itself.

### Fixed
- **The apostrophe `'`** was left raw — WebUtility escapes it to `&#39;`. The most impactful case: any .NET
  report with a name like *"User's cart"* would not have been byte-identical.
- **The C1 control range** `U+0080..U+009F` was escaped — WebUtility leaves it raw.
- **The BMP above `U+00FF`** (`✓`, `€`, CJK, …) was escaped — WebUtility escapes <em>only</em> the Latin-1
  supplement `U+00A0..U+00FF`; higher BMP stays raw.
- **Surrogate pairs** were emitted as two `&#half;` entities — WebUtility emits the combined code point
  (`😀` → `&#128512;`).

### Proven
- `html-escape-samples.txt` (`HtmlEscaperParityTest`) pins the encoder against real WebUtility across the
  apostrophe, C1, Latin-1, higher-BMP and astral-emoji cases.
- Two special-char report goldens (`report-escaping`, `report-escaping-params`) carry the markup-trigger
  marker `<b>&"'` in every user-text field — feature/scenario/rule/step/substep/attachment/error/title and
  parameter name/value/`outlineId` — and match .NET byte-for-byte, confirming every call site escapes.

## [0.1.18] — unreleased

**Diagram-creator parameter completeness** — ports the four remaining deterministic
`GetPlantUmlImageTagsPerTestId` parameters no golden corpus exercised, completing the full
`PlantUmlCreator.Create` surface (every parameter except the server-rendering ones —
`plantUmlServerRendererUrl`/`lazyLoadImages` — and server-side splitting `maxEncodedDiagramLength`, all
documented boundaries). Closing this also fixed two latent participant/arrow divergences the simpler Java
algorithm carried.

### Added — proven byte-parity for
- **`excludeAllHeaders`** — drops every header from notes (vs the default `Cache-Control`/`Pragma`-only
  exclusion). Golden: `exclude-all-headers`.
- **`truncateNotesAfterLines`** — caps each note body at N lines, the rest replaced by a trailing `...`
  line (.NET `TruncateNoteContent`). Golden: `truncate-notes`.
- **`dependencyColors`** — a `category → colour` override map, honoured by both the arrow colour and the
  participant declaration. Golden: `dependency-colors`.
- **`serviceTypeOverrides`** — a `serviceName/callerName → category` override map that changes a
  participant's detected category, hence its shape <em>and</em> colour. Golden: `service-type-overrides`.

### Fixed — participant declaration now a faithful port of `CreateEntitiesPlantUml`
- The **pure caller** (a `CallerName` never appearing as a `ServiceName`) is declared **first**, even when
  it is not the first-seen caller — the previous first-seen algorithm mis-ordered it. Golden:
  `pure-caller-order`.
- A caller carrying a **`CallerDependencyCategory`** is now **shaped** (queue/database/…) instead of always
  rendering as an `actor`, and the **arrow colour falls back** to the caller's category when the service
  has none (event-consume arrows). Golden: `caller-category`.
- Arrow colours now use an override-aware, first-non-null **service-category cache** over all traces
  (.NET `GetArrowColor`), with `OrdinalIgnoreCase` category lookups.

### Exposed
- All four are first-class on `DiagramOptions`/`ReportOptions` with `with…` methods and
  `kronikol.diagram.{excludeAllHeaders,truncateNotesAfterLines,dependencyColors,serviceTypeOverrides}`
  system properties (the map properties parse `key=value,key=value`).

### Notes
- The full `PlantUmlCreator.Create` diagram-generation surface is now ported and byte-parity-proven. The
  remaining lower-risk audit item is the main `TestRunReport.html` escaping of scenario/feature **names**
  containing `<&">` (the `HtmlEscaper` is proven; its application at those call sites is unverified).

## [0.1.17] — unreleased

**Diagram note + label parity** — closes the diagram-note and arrow-label gaps a corpus-coverage
re-audit surfaced (code paths gated on inputs no `.puml` golden corpus exercised), and exposes the full
diagram-option surface on `ReportOptions`. The lesson driving this round: a "complete parity" claim is
only as strong as the golden corpora's input coverage, so each fix ships with a corpus that triggers it.

### Added — proven byte-parity for
- **GraphQL request bodies + operation labels** (`PlantUmlCreator` / `NoteFormatter`): a GraphQL request
  note renders as its formatted query (brace-driven indentation, inline parentheses, directives, aliases,
  fragment spreads, inline + top-level fragments), and the arrow label gains an operation label
  (`\n(query GetUser)`, `(mutation PlaceOrder)`, …) in every mode. The `GraphQlBodyFormat` mode (`Json` /
  `FormattedQueryOnly` / `Formatted` / `FormattedWithMetadata`, the default) controls header + metadata
  display; the `variables`/`extensions` sections use System.Text.Json's indented *default* (HTML-safe)
  encoder, distinct from the relaxed encoder JSON-mode bodies use. Ported `GraphQlOperationDetector`,
  `GraphQlQueryFormatter`, `GraphQlBodyFormatter`. Goldens: `graphql`, `graphql-query-only`,
  `graphql-json`, `graphql-mutation`, `graphql-complex`, plus a `graphql-labels` detector fixture
  covering the branches the rendered diagrams don't reach (subscription, anonymous shorthand,
  `operationName` override, escaped whitespace, non-GraphQL rejection).
- **Internal-flow tracking links** — with `internalFlowTracking` on, each request arrow label is wrapped
  in a clickable `[[#iflow-<requestResponseId> …]]` PlantUML link — the anchor the interactive
  internal-flow popup keys on (`#iflow-<id>` matches the `InternalFlowSegmentBuilder` segment id). Golden:
  `internal-flow`.
- **Note-content branches** the goldens never exercised: binary content → `[binary content]`, non-JSON
  request bodies → form-url-encoded rendering, request URLs over 100 chars → wrapped labels, and the
  request `NoteOnRight` side. Goldens: `binary-content`, `form-encoded`, `long-url`, `note-on-right`.
- **Full diagram-option surface on `ReportOptions`** — `excludedHeaders`, `separateSetup`,
  `highlightSetup`, `setupHighlightColor`, `focusEmphasis`, `focusDeEmphasis`, `graphQlBodyFormat` and
  `internalFlowTracking` are now first-class `with…` methods + system properties (e.g.
  `-Dkronikol.diagram.internalFlowTracking=true`), alongside the existing colour/theme options.

### Fixed
- `ReportOptions.fromSystemProperties` built a reduced `DiagramOptions` shape (it stopped at
  `focusDeEmphasis`) — now threads every field, including `graphQlBodyFormat`/`internalFlowTracking`.

### Notes
- CI-summary multipart/truncated rendering gained golden coverage (the code already matched .NET).
- Response-note chunking (>15k) remains a documented server-only boundary (`!clientSideSplitting`).
- **Still unported in the diagram creator** (the next parity target): `truncateNotesAfterLines`,
  `excludeAllHeaders`, and the `dependencyColors`/`serviceTypeOverrides` palette/shape override maps —
  four deterministic `GetPlantUmlImageTagsPerTestId` parameters no golden yet exercises (the participant
  declaration also needs verifying against `CreateEntitiesPlantUml`'s caller-category/pure-caller logic).
  The earlier "complete report parity" milestone holds for the default option set, not these advanced
  per-call overrides.

## [0.1.16] — unreleased

**Audit follow-up** — closes the three gaps a deep parity re-audit surfaced after the v0.1.15
"complete report parity" milestone: the merge path was a reduced subset, the report-data schema was
unported, and the diagram-generation styling options were missing (the latter exposing a real bug).

### Fixed
- **Diagram header rendering** — a pre-existing divergence: headers in diagram notes rendered as
  `Key: Value`, but .NET renders gray, bracketed, ordinal-sorted `<color:gray>[Key=Value]` (batched into
  ≤80-char chunks) with `Cache-Control`/`Pragma` excluded by default. Undetected because every `.puml`
  golden corpus used `NoHeaders()`. `NoteFormatter` now matches `FormatNoteContent` exactly.

### Added — proven byte-parity for
- **Full-fidelity merge** — the merged HTML report now equals what a single combined run would render:
  the fragment carries the complete `Feature`/`Scenario`/`ScenarioStep` model (steps, parameters,
  attachments, rules), per-test diagrams, aggregated `componentRelationships` (re-summed across shards),
  interactive `internalFlowSegments`, `ciMetadata`, `wholeTestVisualization` and `wholeTestFlow`, and the
  renderer feeds the component diagram + popup + CI banner + `includeTestRunData` summary. A generic
  `RecordJson` converter round-trips the whole nested model. Proven by a merge-equals-direct-render
  equivalence test.
- **Report-data schema** — `ReportDataSchema.jsonSchema()` (JSON Schema draft 2020-12, for the Json + Yaml
  formats) and `xmlSchema()` (the XSD), byte-identical to .NET `GenerateTestRunReportSchema`; the runtime
  emits `TestRunReport.schema.{json,xsd}` per data format under `ReportOptions.generateSchema`.
- **Diagram-generation styling options** (`PlantUmlCreator` via the new `DiagramOptions` /
  `NoteProcessors`): the `plantUmlTheme` `!theme` directive; `ExcludedHeaders` (+ the default exclusion);
  `SeparateSetup`/`HighlightSetup`/`SetupHighlightColor` (the `partition <color> Setup … end` block);
  `FocusEmphasis`/`FocusDeEmphasis` (the `JsonFocusFormatter`); and the request/response pre/mid/post
  content-processor hooks (caller-supplied — placement proven by unit test). `plantUmlTheme` is wired
  through `ReportOptions`; the advanced options are available via `PlantUmlCreator.create(logs,
  DiagramOptions)` and applied at the .NET defaults from the report path.

## [0.1.15] — unreleased

**Phase 3 completeness sweep** — the remaining configurable report options and the secondary report
artifacts, closing the port to **complete byte-for-byte parity with .NET Kronikol's reporting, minus
only server-side PlantUML image rendering** (the Node.js / inline-SVG `ImgSrc` pre-render path; diagrams
render in-browser instead).

### Added — proven byte-parity for
- **Parameterized option flags** — `ParameterizedOptions` (`groupParameterizedTests` /
  `maxParameterColumns` / `titleizeParameterNames`) threaded through `render(…)`: ungrouped flat tables,
  the `maxParameterColumns` scalar-column fallback, and the un-titleized raw parameter names.
- **Interactive internal-flow popup** — the per-diagram popup data (`window.__iflowConfig` +
  `window.__iflowSegments`), built from per-boundary segments (`InternalFlowSegmentBuilder.buildSegments`)
  and an `InternalFlowPopupInput`, emitted in the head. The compact `System.Text.Json`-faithful segment
  payload (`InternalFlowHtmlGenerator.generateSegmentDataScript`) and the inline activity-diagram
  (`renderActivityDiagramInline`, `data-plantuml-z`) are byte/decoded-verified.
- **Merge whole-test-flow fragments** — each test's pre-rendered internal-flow content
  (`WholeTestFlowContent`: activity HTML + flame HTML + span count) now rides in the report fragment
  (`ReportFragment.wholeTestFlow`, serialized in `FragmentJson` only when present), merges across shards,
  and feeds the renderer as `precomputedWholeTestContent` — so a merged report shows each shard's
  whole-test-flow without re-resolving from raw spans (mirrors `ResolveWholeTestFlowContent`'s precomputed
  branch).
- **Custom stylesheet param** — the .NET `stylesheet` positional
  (`HtmlSpecificationsCustomStyleSheet`): `combinedStylesheet = HtmlReportStyleSheet + "\n" + (stylesheet ??
  "")`, appended **into** the main `<style>` block right after the base sheet, distinct from `customCss`
  (a separate trailing `<style>`). Threaded via `HtmlCustomization.customStyleSheet`.
- **CI markdown summary** — `CiSummaryGenerator` (the `$GITHUB_STEP_SUMMARY` artifact): the metrics
  table + failed-scenario details (error / stack trace / escaping) or passed-scenario sequence diagrams,
  reusing the ported `PlantUmlTextEncoder` for the server diagram links. The byte-stable structure is
  golden-verified; the non-byte-stable DEFLATE server token is masked and proven by decoding.
- **Diagnostic report** — `DiagnosticReportGenerator` (the standalone `DiagnosticReport.html`): the
  deterministic sections (configuration dump, request/response log summary, entries per service / per
  test, the "unknown" entries breakdown with first/last seen, unpaired requests, orphaned test ids,
  no-log scenarios, activity-span count), plus `FailureClusterer` (group failed scenarios by normalised
  first-line error) and `ReportDiagnostics.analyse` (the deterministic console warnings).

### Notes — deliberate boundaries (documented at the call sites)
- **`DiagramFormat` is PlantUml-only** — the .NET enum has a single value and `ExtractDependencies`
  ignores its `format` argument; there is no Mermaid format, so the dep-extraction port is PlantUml-only.
- **Runtime/environment-state sections are not cross-runtime byte-parity-able** — the diagnostic
  report's and `ReportDiagnostics`' registry-driven warnings (discovered activity sources, tracking
  components, unmatched HTTP client names, unresolved assertion arguments) read mutable .NET DI/tracking
  state with no portable equivalent (the same boundary as finished-span capture); they are conditional on
  non-empty state, so an empty-registry report matches byte-for-byte. The CI writers (`CiSummaryWriter` /
  `CiArtifactPublisher`) are pure CI-environment IO (`GITHUB_STEP_SUMMARY` / `GITHUB_OUTPUT`) — the
  parity-able content is the ported markdown.
- Re-scan confirmed every `GenerateHtmlReport` parameter is represented except the server-rendering trio
  (`lazyLoadImages` / non-`BrowserJs` `plantUmlRendering` / `inlineSvgRendering`), which is the one
  deliberate exclusion. The reflection-based `ExampleRawValues` object rendering remains a documented
  boundary (only the deterministic string-based parameter paths are ported).

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
