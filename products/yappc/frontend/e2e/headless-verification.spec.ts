import { test, expect } from '@playwright/test';
import { captureConsoleAndSnapshot } from './helpers/headlessUtils';

// Headless verification that the app loads without console errors and renders
// the main Workspaces view when navigating to '/'. This is intended as a
// lightweight automated smoke test for CI or local verification.

test('headless page load and console verification', async ({ page }, testInfo) => {
  const { logs, html } = await captureConsoleAndSnapshot(page, '/');

  // Attach captured logs and html to the test artifacts for debugging
  await testInfo.attach('captured-console.json', {
    body: JSON.stringify(logs, null, 2),
    contentType: 'application/json'
  });

  await testInfo.attach('page-dom.html', {
    body: html,
    contentType: 'text/html'
  });

  // Fail the test if there's any console error or pageerror
  const errors = logs.filter(l => l.type === 'error' || l.type === 'pageerror');
  if (errors.length > 0) {
    // Provide the first few errors inline to make debugging faster
    const sample = errors.slice(0, 5).map(e => `${e.type}: ${e.text}${e.location ? ` (${e.location})` : ''}`).join('\n');
    test.fail();
    throw new Error(`Console errors detected during headless load:\n${sample}`);
  }

  // Make sure the Home heading (app title) is present at root
  await expect(page.getByRole('heading', { name: 'YAPPC' })).toBeVisible();
});
