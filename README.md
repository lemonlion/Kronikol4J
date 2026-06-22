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
> plan, architecture, and deep-dive analyses (its §0 is the authoritative status & resume guide).

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

Key decisions (plan §0): Java 17 floor · Gradle · functional parity · browser-only rendering ·
`io.kronikol` / `kronikol4j-*`.
