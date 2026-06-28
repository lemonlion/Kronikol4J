# Kronikol4J project starters

The Java analog of .NET's `dotnet new kronikol-*` templates. Java has no `dotnet new`-style template
registry (Gradle's `init` has no custom-template support, and this is a Gradle-first project), so the
starters here are **copyable skeletons**: copy a directory, rename it, point the sample test at your
system-under-test, and you have a working component-test project that produces a Kronikol4J report.

| Starter | Build tool | Test framework |
|---|---|---|
| [`kronikol4j-junit5-gradle/`](kronikol4j-junit5-gradle) | Gradle (Kotlin DSL) | JUnit 5 |
| [`kronikol4j-junit5-maven/`](kronikol4j-junit5-maven) | Maven | JUnit 5 |

## What you get

- The Kronikol4J test-framework integration wired in (`kronikol4j-junit5` — auto-registers the report
  listener + the identity-scoping extension via `@ExtendWith(KronikolExtension.class)`).
- A `BaseComponentTest` your tests extend (scopes test identity so tracked calls attribute correctly).
- A `SampleComponentTest` that tracks an HTTP call via `TrackingHttpClient` — replace the URL with your SUT.
- Report output at `build/kronikol-report/TestRunReport.html` (Gradle) / merged via the Maven plugin.

## Customising

- Swap `kronikol4j-http` for another tracker (`-jdbc`, `-redis`, `-mongodb`, …) — see the
  [Tracking Adapters](https://github.com/lemonlion/Kronikol4J/wiki/Tracking-Adapters) wiki page.
- Tune the report via the `kronikol { … }` block (Gradle) or `-Dkronikol.report.*` properties — see
  [Report Options](https://github.com/lemonlion/Kronikol4J/wiki).

> Other framework combos (TestNG via `kronikol4j-testng`, Cucumber via `kronikol4j-cucumber`) follow the
> same shape — swap the integration dependency and the base-class wiring.
