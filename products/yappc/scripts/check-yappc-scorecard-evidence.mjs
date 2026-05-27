#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';

const evidenceFile = process.argv[2] ?? 'products/yappc/build/release-evidence/yappc-scorecard-evidence.json';
const requiredDimensions = [
  'backend',
  'frontend',
  'contract',
  'e2e',
  'a11y',
  'performance',
  'security',
  'privacy',
  'governance',
  'release-gates',
  'startup-diagnostics',
];

function fail(message) {
  console.error(message);
  process.exit(1);
}

if (!existsSync(evidenceFile)) {
  fail(`Missing scorecard evidence file: ${evidenceFile}`);
}

const parsed = JSON.parse(readFileSync(evidenceFile, 'utf8'));

if (parsed.schemaVersion !== '1.0.0') {
  fail(`Unexpected scorecard schema version: ${parsed.schemaVersion}`);
}

if (parsed.product !== 'yappc') {
  fail(`Unexpected scorecard product: ${parsed.product}`);
}

if (!Array.isArray(parsed.dimensions)) {
  fail('Scorecard evidence must include a dimensions array.');
}

const dimensions = new Map(parsed.dimensions.map((dimension) => [dimension.id, dimension]));
const missingDimensions = requiredDimensions.filter((id) => !dimensions.has(id));

if (missingDimensions.length > 0) {
  fail(`Scorecard evidence is missing dimensions: ${missingDimensions.join(', ')}`);
}

for (const id of requiredDimensions) {
  const dimension = dimensions.get(id);
  if (!Array.isArray(dimension.commands) || dimension.commands.length === 0) {
    fail(`Scorecard dimension ${id} must include at least one validation command.`);
  }
  if (!Array.isArray(dimension.evidenceRefs) || dimension.evidenceRefs.length === 0) {
    fail(`Scorecard dimension ${id} must include at least one evidence reference.`);
  }
  if (!Array.isArray(dimension.requiredArtifacts) || dimension.requiredArtifacts.length === 0) {
    fail(`Scorecard dimension ${id} must include at least one required artifact.`);
  }
  if (!['present', 'missing'].includes(dimension.status)) {
    fail(`Scorecard dimension ${id} has invalid status: ${dimension.status}`);
  }
}

if (!parsed.summary || parsed.summary.totalDimensions !== requiredDimensions.length) {
  fail('Scorecard summary does not match the required dimension count.');
}

console.log(`YAPPC scorecard evidence check passed: ${evidenceFile}`);
