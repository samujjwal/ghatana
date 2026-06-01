import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { findPhrWebProductionDataViolations } from '../check-phr-web-production-data.mjs';

const guardedFiles = [
  'products/phr/apps/web/src/api/fhirMappers.ts',
  'products/phr/apps/web/src/api/phrApiCore.ts',
  'products/phr/apps/web/src/pages/DashboardPage.tsx',
  'products/phr/apps/web/src/pages/MedicationDetailPage.tsx',
  'products/phr/apps/web/src/pages/MedicationsPage.tsx',
  'products/phr/apps/web/src/pages/RecordDetailPage.tsx',
  'products/phr/apps/web/src/pages/RecordsPage.tsx',
];

function fixtureRoot() {
  const root = path.join(tmpdir(), `phr-web-production-data-${process.pid}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  for (const relativePath of guardedFiles) {
    const absolutePath = path.join(root, relativePath);
    mkdirSync(path.dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, 'export const sourceBacked = true;\n');
  }
  return root;
}

test('PHR web production-data guard accepts source-backed DTO rendering', () => {
  const root = fixtureRoot();

  assert.deepEqual(findPhrWebProductionDataViolations(root), []);
});

test('PHR web production-data guard rejects fabricated dashboard and record facts', () => {
  const root = fixtureRoot();
  writeFileSync(
    path.join(root, 'products/phr/apps/web/src/api/fhirMappers.ts'),
    [
      "const name = raw.name ?? 'Unknown';",
      'let age = 0;',
      'const updatedAt = raw.meta?.lastUpdated ?? new Date().toISOString();',
      "const location = raw.comment ?? 'TBD';",
    ].join('\n'),
  );

  const violations = findPhrWebProductionDataViolations(root);

  assert(violations.some((violation) => violation.rule === 'unknown-placeholder'));
  assert(violations.some((violation) => violation.rule === 'zero-age-placeholder'));
  assert(violations.some((violation) => violation.rule === 'current-time-record-placeholder'));
  assert(violations.some((violation) => violation.rule === 'tbd-placeholder'));
});

test('PHR web production-data guard rejects fabricated medication facts', () => {
  const root = fixtureRoot();
  writeFileSync(
    path.join(root, 'products/phr/apps/web/src/pages/MedicationDetailPage.tsx'),
    [
      'const detail = {',
      '  adherence: 100,',
      '  interactions: [],',
      '  history: [],',
      '};',
    ].join('\n'),
  );

  const violations = findPhrWebProductionDataViolations(root);

  assert(violations.some((violation) => violation.rule === 'hardcoded-adherence-placeholder'));
  assert(violations.some((violation) => violation.rule === 'empty-medication-safety-placeholder'));
});
