#!/usr/bin/env node

import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const outputDir = process.argv[2] ?? 'products/yappc/build/release-evidence';
const artifactRoot = process.argv[3] ?? 'artifacts';

function statusFor(path) {
  return existsSync(path) ? 'present' : 'missing';
}

const checks = [
  ['backend', 'YAPPC backend build and tests', 'backend-tests'],
  ['frontend', 'YAPPC frontend build, lint, typecheck, and tests', 'frontend-tests'],
  ['e2e', 'YAPPC Playwright E2E readiness', 'e2e-results'],
  ['a11y', 'YAPPC accessibility readiness', 'a11y-readiness'],
  ['contract-tests', 'YAPPC OpenAPI contract tests', 'contract-report'],
  ['startup-diagnostics', 'YAPPC startup health and metrics diagnostics', 'startup-diagnostics'],
].map(([name, scope, artifactName]) => {
  const artifact = join(artifactRoot, artifactName);
  return {
    name,
    scope,
    artifact,
    status: statusFor(artifact),
  };
});

mkdirSync(outputDir, { recursive: true });
writeFileSync(
  join(outputDir, 'yappc-ci-execution-proof.json'),
  `${JSON.stringify({
    generatedAt: new Date().toISOString(),
    workflow: '.github/workflows/yappc-ci.yml',
    checks,
  }, null, 2)}\n`,
  'utf8'
);

console.log(`Wrote ${join(outputDir, 'yappc-ci-execution-proof.json')}`);
