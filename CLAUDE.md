# CLAUDE.md — Kronikol4J

Conventions for working in this repo. The authoritative design is [docs/PORT_PLAN.md](docs/PORT_PLAN.md)
(start at §0). This is the Java port of the .NET Kronikol at `../Kronikol` (the behavioural spec).

## Workflow
- **TDD**: write a failing test, make it pass, refactor. Every substantive class has tests.
- Build/test with the wrapper: `./gradlew build`, `./gradlew :<module>:test`. JDK 17+ (validated on 25).
- Keep the build green at every step — add a module to `settings.gradle.kts` only with its build file.
- New module = apply `id("kronikol4j.java-library-conventions")` + a one-line `description`.

## Architecture rules (from the plan)
- **Dependency philosophy (§15):** `kronikol4j-core` and `kronikol4j-diagram` have **zero required
  runtime dependencies**. Adapters declare the tracked tech as `compileOnly`/`provided`. Never force
  a version on the user's app.
- **The seam is sacred (§1):** everything funnels through `RequestResponseLogger.log(RequestResponseLog)`.
  Don't change that signature lightly — every future adapter depends on it.

## Parity discipline (these prevent silent cross-runtime diffs)
- **Newlines:** emit `\n` only, never `System.lineSeparator()` (§6.5).
- **Culture:** invariant/`Locale.ROOT` casing and number formatting; ordinal string sorts (§6.5).
- **JSON note bodies:** use the hand-rolled `Json` formatter — preserves input key order, strips null
  object-properties, 2-space indent, UnsafeRelaxed escaping (`< > & +` and non-ASCII pass through) (§6.4).
- **PlantUML splitting:** one un-split diagram per test (client-side splitting). Never base a
  generation decision on the Deflate-encoded length (§6.5).
- **Encoder:** verify by round-trip, never byte-pin against .NET (Deflate isn't byte-stable) (§6.4).
- **HTML escaping (report module):** reproduce `WebUtility.HtmlEncode` exactly via the parity shim (§4.4).

## Context / threading (§3.2)
- `ThreadLocal`-backed identity & phase. **Clearing is mandatory** — `TestIdentityScope.begin(...)`
  returns an `AutoCloseable`; always use try-with-resources. Adapters must clear in teardown/`finally`.
- For parallel-safe background work, use the data-keyed `TestCorrelationStore`, not the global fallback.

## Determinism (§6.1)
- Use the `IdGenerator` seam (`seeded()` in tests/golden capture, `random()` in production) and an
  injectable clock so golden-file output is reproducible.

## Documentation
- Docs-as-you-go: a wiki page lands with each new adapter/extension module (plan §12.2). Wiki lives at
  `../Kronikol4J.wiki`.
