/**
 * Manual quick-audit runner for the demo HTML page.
 *
 * This test is intended to be run directly to exercise the library against
 * the example `examples/demo.html` page in a jsdom environment.
 *
 * Run with:
 * pnpm --filter @ghatana/accessibility-audit run test -- -t "manual quick audit"
 */

import fs from 'fs';
import path from 'node:path';

import { it, expect } from 'vitest';

import { runQuickAudit } from '../index';

it('manual quick audit - demo.html', async () => {
  // Load the demo HTML into the jsdom document
  // Use import.meta.url to locate the examples file in an ESM-friendly way
  // Resolve demo HTML path depending on current working directory. When
  // tests are executed from the package folder the demo is at ./examples;
  // when executed from workspace root it's at ./libs/accessibility-audit/examples.
  let demoPath: string;
  if (process.cwd().endsWith(path.join('libs', 'accessibility-audit'))) {
    demoPath = path.join(process.cwd(), 'examples', 'demo.html');
  } else {
    demoPath = path.join(process.cwd(), 'libs', 'accessibility-audit', 'examples', 'demo.html');
  }
  const html = fs.readFileSync(demoPath, 'utf-8');

  // Replace the entire document with the demo HTML so axe-core can analyze it
  // JSDOM provided by Vitest will use this document
  document.documentElement.innerHTML = html;

  // Ensure title and body exist
  const title = document.title || 'Demo Page';
  expect(document.body).toBeDefined();

  // Run a quick audit against the current document
  const report = await runQuickAudit(document);

  // Basic sanity checks
  expect(report).toBeDefined();
  expect(report.score).toBeDefined();
  expect(Array.isArray(report.findings)).toBe(true);

  // Log a small summary so the user can see output when running this test
  // (Vitest captures console output by default, but the printed lines will appear)
  // eslint-disable-next-line no-console
  console.log('Manual Quick Audit Summary:');
  // eslint-disable-next-line no-console
  console.log(`  Page title: ${title}`);
  // eslint-disable-next-line no-console
  console.log(`  Overall score: ${report.score.overall}/100 (${report.score.grade})`);
  // eslint-disable-next-line no-console
  console.log(`  Total findings: ${report.findings.length}`);
});
