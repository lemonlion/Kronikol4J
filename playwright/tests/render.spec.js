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

test('the .NET-parity report paints its PlantUML SVGs in a real browser, offline', async ({ page }, testInfo) => {
  const consoleErrors = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });
  page.on('pageerror', (err) => consoleErrors.push('pageerror: ' + err.message));
  // file:// must not reach the network: fail loudly if anything tries to.
  const networkAttempts = [];
  page.on('request', (req) => {
    if (!req.url().startsWith('file:')) networkAttempts.push(req.url());
  });

  await page.goto(reportUrl, { waitUntil: 'load' });

  // The bundled renderer signals the WASM library finished loading.
  await expect(page.locator('body.plantuml-ready')).toBeAttached({ timeout: 30_000 });

  // Features/scenarios are collapsed by default (.NET parity); the toolbar's Expand-All reveals them.
  await page.getByRole('button', { name: 'Expand All Features' }).click();
  await page.getByRole('button', { name: 'Expand All Scenarios' }).click();

  // The scenario's sequence diagram is now visible and PlantUML-WASM injected an <svg>.
  const seq = page.locator('.example-diagrams .plantuml-browser').first();
  await expect(seq).toBeAttached();
  const seqSvg = seq.locator('svg').first();
  await expect(seqSvg).toBeVisible({ timeout: 30_000 });

  // Pixel check: the SVG actually has a non-zero layout box (it painted, not just attached).
  const seqBox = await seqSvg.boundingBox();
  expect(seqBox, 'rendered sequence <svg> should have a layout box').not.toBeNull();
  expect(seqBox.width).toBeGreaterThan(10);
  expect(seqBox.height).toBeGreaterThan(10);

  // It is a real sequence diagram, not an empty canvas: text glyphs + the tracked participant.
  expect(await seqSvg.locator('text').count(), 'diagram should contain text glyphs').toBeGreaterThan(0);
  expect(await seqSvg.evaluate((el) => el.outerHTML)).toContain('OrderService');

  // The run-level component diagram is hidden until toggled, then lazy-renders (matching .NET).
  const componentSection = page.locator('#component-diagram');
  await expect(componentSection).toBeHidden();
  await page.locator('button[onclick*="toggle_component_diagram"]').click();
  await expect(componentSection).toBeVisible();

  const componentSvg = componentSection.locator('.plantuml-browser svg').first();
  await expect(componentSvg).toBeVisible({ timeout: 30_000 });
  const componentBox = await componentSvg.boundingBox();
  expect(componentBox, 'component <svg> should have a layout box').not.toBeNull();
  expect(componentBox.width).toBeGreaterThan(10);
  expect(componentBox.height).toBeGreaterThan(10);
  expect(await componentSvg.evaluate((el) => el.outerHTML)).toContain('OrderService');

  // The whole thing ran from local files only, and cleanly.
  expect(networkAttempts, 'report must render with no network').toEqual([]);
  expect(consoleErrors, 'no console/page errors during render').toEqual([]);

  // Attach the painted report as visual proof (shown in the HTML report / trace).
  await testInfo.attach('rendered-report', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
});
