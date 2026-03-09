import { test, expect } from '@playwright/test';
import { captureConsoleAndSnapshot } from './helpers/headlessUtils';

test('headless load projects page and capture console', async ({ page }, testInfo) => {
  const url = '/app/w/ws-1/projects';
  const { logs, html } = await captureConsoleAndSnapshot(page, url);

  await testInfo.attach('projects-console.json', {
    body: JSON.stringify(logs, null, 2),
    contentType: 'application/json'
  });

  await testInfo.attach('projects-page.html', {
    body: html,
    contentType: 'text/html'
  });

  const errors = logs.filter(l => l.type === 'error' || l.type === 'pageerror');
  if (errors.length > 0) {
    const sample = errors.slice(0, 5).map(e => `${e.type}: ${e.text}${e.location ? ` (${e.location})` : ''}`).join('\n');
    test.fail();
    throw new Error(`Console errors detected on projects page:\n${sample}`);
  }

  // Expect the workspace heading to be visible
  await expect(page.getByRole('heading', { name: 'Personal Projects' })).toBeVisible();

  // Expect at least one project link to be present
  await expect(page.getByRole('link', { name: /E-commerce Platform/i })).toBeVisible();
});
