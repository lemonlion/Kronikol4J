// @ts-check
const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

// The self-contained report produced by:
//   ./gradlew :kronikol4j-report:generatePlaywrightFixture
const reportPath = path.resolve(
  __dirname, '..', '..', 'kronikol4j-report', 'build', 'playwright', 'report.html');
const reportUrl = 'file://' + reportPath.replace(/\\/g, '/');

test.beforeAll(() => {
  if (!fs.existsSync(reportPath)) {
    throw new Error(
      'Report fixture not found at ' + reportPath +
      '\nRun: ./gradlew :kronikol4j-report:generatePlaywrightFixture');
  }
});

test('generated report paints a PlantUML SVG with real dimensions, offline', async ({ page }, testInfo) => {
  const consoleErrors = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });
  page.on('pageerror', (err) => consoleErrors.push('pageerror: ' + err.message));
  // file:// must not reach the network: fail loudly if anything tries to.
  const networkAttempts = [];
  page.on('request', (req) => {
    if (!req.url().startsWith('file:')) networkAttempts.push(req.url());
  });

  await page.goto(reportUrl, { waitUntil: 'load' });

  // The container is present from the server-rendered HTML.
  const container = page.locator('.plantuml-browser#puml-0');
  await expect(container).toBeAttached();

  // The bundled renderer signals the WASM library finished loading.
  await expect(page.locator('body.plantuml-ready')).toBeAttached({ timeout: 30_000 });

  // PlantUML-WASM injects an <svg> into the container.
  const svg = container.locator('svg').first();
  await expect(svg).toBeVisible({ timeout: 30_000 });

  // Pixel check: the SVG actually has a non-zero layout box (it painted, not just attached).
  const box = await svg.boundingBox();
  expect(box, 'rendered <svg> should have a layout box').not.toBeNull();
  expect(box.width).toBeGreaterThan(10);
  expect(box.height).toBeGreaterThan(10);

  // It is a real sequence diagram, not an empty canvas: it has text glyphs...
  const textCount = await svg.locator('text').count();
  expect(textCount, 'diagram should contain text glyphs').toBeGreaterThan(0);

  // ...and the participants from our tracked interaction are drawn.
  const svgMarkup = await svg.evaluate((el) => el.outerHTML);
  expect(svgMarkup).toContain('OrderService');

  // The run-level component diagram also paints (a second diagram type in the same report).
  const componentSvg = page.locator('.plantuml-browser#puml-component svg').first();
  await expect(componentSvg).toBeVisible({ timeout: 30_000 });
  const componentBox = await componentSvg.boundingBox();
  expect(componentBox, 'component <svg> should have a layout box').not.toBeNull();
  expect(componentBox.width).toBeGreaterThan(10);
  expect(componentBox.height).toBeGreaterThan(10);
  const componentMarkup = await componentSvg.evaluate((el) => el.outerHTML);
  expect(componentMarkup).toContain('OrderService'); // the aggregated component is drawn

  // The whole thing ran from local files only, and cleanly.
  expect(networkAttempts, 'report must render with no network').toEqual([]);
  expect(consoleErrors, 'no console/page errors during render').toEqual([]);

  // Attach the painted report as visual proof (shown in the HTML report / trace).
  await testInfo.attach('rendered-report', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
});
