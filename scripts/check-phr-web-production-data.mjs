#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');

const productionDataFiles = [
  'products/phr/apps/web/src/api/fhirMappers.ts',
  'products/phr/apps/web/src/api/phrApiCore.ts',
  'products/phr/apps/web/src/pages/DashboardPage.tsx',
  'products/phr/apps/web/src/pages/MedicationDetailPage.tsx',
  'products/phr/apps/web/src/pages/MedicationsPage.tsx',
  'products/phr/apps/web/src/pages/RecordDetailPage.tsx',
  'products/phr/apps/web/src/pages/RecordsPage.tsx',
];

const forbiddenPatterns = [
  {
    id: 'unknown-placeholder',
    pattern: /(['"`])Unknown\1/,
    message: 'Do not fabricate Unknown production facts; require backend/FHIR source fields or omit optional UI.',
  },
  {
    id: 'tbd-placeholder',
    pattern: /(['"`])TBD\1/,
    message: 'Do not fabricate TBD production facts; require backend/FHIR source fields or omit optional UI.',
  },
  {
    id: 'zero-age-placeholder',
    pattern: /\bage\s*=\s*0\b|\bage:\s*0\b/,
    message: 'Do not synthesize zero age for patient facts.',
  },
  {
    id: 'current-time-record-placeholder',
    pattern: /new Date\(\)\.toISOString\(\)/,
    message: 'Do not synthesize record timestamps on the client.',
  },
  {
    id: 'empty-medication-safety-placeholder',
    pattern: /\b(?:interactions|history|warnings):\s*\[\s*\]/,
    message: 'Do not synthesize empty medication safety/history arrays in production data.',
  },
  {
    id: 'hardcoded-adherence-placeholder',
    pattern: /\badherence:\s*(?:100|0)\b/,
    message: 'Do not synthesize medication adherence values on the client.',
  },
  {
    id: 'record-title-from-id-placeholder',
    pattern: /title:\s*`[^`]*(?:recordId|resourceType|raw\.id|\.id)[^`]*`/,
    message: 'Record detail titles must come from backend DTO/FHIR display fields, not IDs or resource type alone.',
  },
];

export function findPhrWebProductionDataViolations(root = repoRoot) {
  const violations = [];

  for (const relativePath of productionDataFiles) {
    const absolutePath = path.join(root, relativePath);
    if (!existsSync(absolutePath)) {
      violations.push({
        file: relativePath,
        line: 1,
        rule: 'missing-production-data-file',
        message: 'Expected PHR web production data file is missing from the guard surface.',
      });
      continue;
    }

    const lines = readFileSync(absolutePath, 'utf8').split(/\r?\n/);
    lines.forEach((lineText, index) => {
      for (const rule of forbiddenPatterns) {
        if (rule.pattern.test(lineText)) {
          violations.push({
            file: relativePath,
            line: index + 1,
            rule: rule.id,
            message: rule.message,
            text: lineText.trim(),
          });
        }
      }
    });
  }

  return violations;
}

function main() {
  const violations = findPhrWebProductionDataViolations();
  if (violations.length > 0) {
    console.error('PHR web production-data guard failed:');
    for (const violation of violations) {
      console.error(`- ${violation.file}:${violation.line} [${violation.rule}] ${violation.message}`);
      if (violation.text) {
        console.error(`  ${violation.text}`);
      }
    }
    process.exit(1);
  }
  console.log('PHR web production-data guard passed.');
}

if (process.argv[1] === __filename) {
  main();
}
