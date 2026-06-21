# Kronikol4J — Playwright render verification

Pixel-level proof that a generated Kronikol4J HTML report actually paints its PlantUML diagram in a
real browser. This is the visual counterpart to the byte-for-byte PlantUML parity tests: parity
proves the *diagram source* matches .NET; this proves that source *renders to a real SVG*.

It runs **fully offline against local files** — no network, no request mocking (per the project's
Playwright rules). The PlantUML-WASM library (`viz-global.js`, `plantuml.js`) is copied next to the
report and referenced with a relative asset base.

## Run it

```bash
# 1. Generate the self-contained fixture: report.html + the two WASM assets, all offline-linked.
#    (Java side — uses the real HtmlReportGenerator with -Dkronikol.report.assetBase=.)
./gradlew :kronikol4j-report:generatePlaywrightFixture

# 2. Install Playwright + Chromium (first time only).
cd playwright
npm install
npx playwright install chromium

# 3. Render and verify.
npx playwright test
```

The test loads `kronikol4j-report/build/playwright/report.html`, waits for the WASM renderer to
inject an `<svg>`, and asserts it painted with real dimensions, contains text glyphs and the tracked
participant (`OrderService`), and that nothing touched the network. A full-page screenshot is
attached to the HTML report (`npx playwright show-report`) as visual proof.

## Where the WASM assets come from

`OfflineReportFixture` copies `viz-global.js` / `plantuml.js` from the local cache the .NET renderer
populates (`%LOCALAPPDATA%/Kronikol/plantuml-js`); if that cache is absent it downloads them from the
pinned CDN release once. Override the source dir with `-Dkronikol.plantuml.libDir=<dir>`.
