#!/usr/bin/env node

/**
 * Data Cloud Architecture Scorecard
 *
 * Produces a machine-readable and markdown scorecard for Data Cloud plane coverage,
 * runtime-truth gates, and shared-library boundary evidence.
 */

import fs from 'node:fs';
import path from 'node:path';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');

function fileExists(relativePath) {
  return fs.existsSync(path.join(repoRoot, relativePath));
}

function listDirectories(relativePath) {
  const fullPath = path.join(repoRoot, relativePath);
  if (!fs.existsSync(fullPath)) {
    return [];
  }

  return fs
    .readdirSync(fullPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function check(name, passed, evidence, weight = 1) {
  return {
    name,
    passed,
    evidence,
    weight,
    score: passed ? weight : 0,
  };
}

const planes = listDirectories('products/data-cloud/planes');

const checks = [
  check(
    'Plane topology present',
    planes.length >= 7,
    `products/data-cloud/planes (${planes.join(', ')})`,
    2,
  ),
  check(
    'Runtime truth gate script exists',
    fileExists('scripts/check-truth-surfaces.mjs'),
    'scripts/check-truth-surfaces.mjs',
    2,
  ),
  check(
    'Canonical runtime surface registry exists',
    fileExists('products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts'),
    'products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts',
    2,
  ),
  check(
    'Release truth dashboard route exists',
    fileExists('products/data-cloud/delivery/ui/src/pages/ReleaseTruthDashboardPage.tsx'),
    'products/data-cloud/delivery/ui/src/pages/ReleaseTruthDashboardPage.tsx',
    1,
  ),
  check(
    'Shared UI component library module exists',
    fileExists('products/data-cloud/libs/ui-components/package.json'),
    'products/data-cloud/libs/ui-components/package.json',
    1,
  ),
  check(
    'Durable load workflow exists',
    fileExists('.github/workflows/data-cloud-durable-load.yml'),
    '.github/workflows/data-cloud-durable-load.yml',
    2,
  ),
  check(
    'Audit todo burndown automation exists',
    fileExists('scripts/generate-audit-todo-burndown.mjs'),
    'scripts/generate-audit-todo-burndown.mjs',
    1,
  ),
  check(
    'Route/action gate generator exists',
    fileExists('products/data-cloud/delivery/ui/src/lib/routing/RuntimeRouteActionGateGenerator.ts'),
    'products/data-cloud/delivery/ui/src/lib/routing/RuntimeRouteActionGateGenerator.ts',
    1,
  ),
];

const maxScore = checks.reduce((acc, item) => acc + item.weight, 0);
const achievedScore = checks.reduce((acc, item) => acc + item.score, 0);
const scorePercent = Math.round((achievedScore / maxScore) * 100);

const report = {
  generatedAt: new Date().toISOString(),
  scorePercent,
  achievedScore,
  maxScore,
  planes,
  checks,
};

const reportDir = path.join(repoRoot, 'build/reports/architecture');
fs.mkdirSync(reportDir, { recursive: true });

const jsonPath = path.join(reportDir, 'data-cloud-architecture-scorecard.json');
fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf8');

const markdownLines = [
  '# Data Cloud Architecture Scorecard',
  '',
  `- Generated: ${report.generatedAt}`,
  `- Score: ${report.scorePercent}% (${report.achievedScore}/${report.maxScore})`,
  `- Planes: ${planes.join(', ')}`,
  '',
  '## Checks',
  '',
  ...checks.map((item) => {
    const mark = item.passed ? 'PASS' : 'FAIL';
    return `- [${mark}] ${item.name} (weight ${item.weight}) — ${item.evidence}`;
  }),
  '',
];

const markdownPath = path.join(reportDir, 'data-cloud-architecture-scorecard.md');
fs.writeFileSync(markdownPath, `${markdownLines.join('\n')}`, 'utf8');

console.log(JSON.stringify({
  scorePercent,
  reportJson: path.relative(repoRoot, jsonPath),
  reportMarkdown: path.relative(repoRoot, markdownPath),
}, null, 2));

if (process.argv.includes('--check') && scorePercent < 80) {
  process.exitCode = 1;
}
