# Kronikol4J

A Java-native reimplementation of [Kronikol](../Kronikol) — automatically captures real dependency
interactions during tests (HTTP, SQL/NoSQL, cache, messaging, cloud SDKs) and generates interactive
HTML reports with PlantUML diagrams. Deterministic diagrams from actual execution, not AI.

> **Status: foundation in progress.** The build, the core tracking seam, and the diagram pipeline
> are implemented and fully tested. Breadth toward full feature parity (HTML report, adapters,
> integrations) is ongoing. See [docs/PORT_PLAN.md](docs/PORT_PLAN.md) for the complete plan,
> architecture, and the five deep-dive analyses; the plan's §0 is the authoritative status & resume guide.

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
| `kronikol4j-report` | ⏳ planned | HTML report assembly + embedded JS/CSS + `MergeableReportMerger` |
| `kronikol4j-runtime` | ⏳ planned | Per-JVM report-fragment emission + cross-fork merge |
| `kronikol4j-junit5` | ⏳ planned | JUnit 5 extension + run-completion listener |
| `kronikol4j-http` / `-jdbc` / `-proxy` / `-servlet` | ⏳ planned | First tracking adapters (the three ingestion patterns) |
| _… many more (Spring, messaging, cloud, gRPC, OTel, CLI, plugins)_ | ⏳ planned | Breadth to full parity (plan §9 Phase 5+) |

## Design

Three architectural seams (plan §1): **ingestion** (`RequestResponseLogger.log`), **context**
(identity/phase/correlation), and **output** (logs → PlantUML → HTML). Full parity is the end goal,
delivered core-first; the core is engineered so extensions plug in without touching it.

Key decisions (plan §0): Java 17 floor · Gradle · functional parity · browser-only rendering ·
`io.kronikol` / `kronikol4j-*`.
