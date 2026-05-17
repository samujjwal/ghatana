#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];
const pnpmCommand = 'pnpm';

function read(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
    return '';
  }
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (!source.includes(needle)) {
    errors.push(`${label} must include ${needle}`);
  }
}

function requireNotIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (source.includes(needle)) {
    errors.push(`${label} must not include ${needle}`);
  }
}

const routePath = 'products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts';
const routeTestPath = 'products/yappc/frontend/apps/api/src/__tests__/product-unit-intents.test.ts';
const frontendServicePath =
  'products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts';
const frontendTestPath =
  'products/yappc/frontend/web/src/services/canvas/commands/__tests__/ProductUnitIntentExportService.test.ts';

requireIncludes(routePath, "ProductUnitIntentSchema", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "SemanticArtifactReferenceSchema", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "providerMode === 'platform'", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "platform-mode-requires-data-cloud-evidence-ref", 'YAPPC ProductUnitIntent route');
requireNotIncludes(routePath, 'writeFileSync', 'YAPPC ProductUnitIntent route');
requireNotIncludes(routePath, 'kernel-product.yaml', 'YAPPC ProductUnitIntent route');
// New assertion: YAPPC route must call Kernel service in apply mode
requireIncludes(routePath, "applyProductUnitIntent", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "requestedAction === 'apply'", 'YAPPC ProductUnitIntent route');
// New assertion: ProductUnitIntent apply must produce application result schema
requireIncludes(routePath, "ProductUnitIntentApplicationResultSchema", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "applicationResult", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "lifecycleEventRefs", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "provenanceRefs", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "runtimeTruthRefs", 'YAPPC ProductUnitIntent route');
requireIncludes(routePath, "evidenceRef: kernelResult.provenanceRefs[0]", 'YAPPC ProductUnitIntent route');

requireIncludes(frontendServicePath, "DEFAULT_INTENT_ENDPOINT = '/api/v1/yappc/product-unit-intents'");
requireIncludes(frontendServicePath, 'dataCloudEvidenceEndpoint');
requireIncludes(frontendServicePath, 'parseDataCloudEvidencePersistenceResponse');
requireIncludes(frontendServicePath, 'evidenceRefs: persistedEvidenceRefs');

requireIncludes(routeTestPath, 'accepts schema-backed artifact intelligence evidence bundles');
requireIncludes(routeTestPath, 'requires stored Data Cloud evidence refs in platform mode');
requireIncludes(routeTestPath, 'requires explicit permission for apply');
requireIncludes(frontendTestPath, 'stores evidence through Data Cloud before platform-mode handoff');
requireIncludes(frontendTestPath, 'requires Data Cloud evidence persistence responses to include evidence refs');
requireIncludes(routeTestPath, 'marks result providerMode as platform in platform mode');
requireIncludes(routeTestPath, 'queues apply when explicit permission is present');

function runPnpm(args, cwd) {
  return spawnSync(pnpmCommand, args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
}

for (const command of [
  {
    cwd: 'products/yappc/frontend/apps/api',
    args: ['exec', 'vitest', 'run', 'src/__tests__/product-unit-intents.test.ts'],
  },
  {
    cwd: 'products/yappc/frontend/web',
    args: [
      'exec',
      'vitest',
      'run',
      'src/services/canvas/commands/__tests__/ProductUnitIntentExportService.test.ts',
    ],
  },
]) {
  const result = runPnpm(command.args, join(repoRoot, command.cwd));
  if (result.error) {
    errors.push(
      `Focused YAPPC ProductUnitIntent handoff test failed in ${command.cwd}: ${result.error.message}`,
    );
    continue;
  }
  if (result.status !== 0) {
    errors.push(
      `Focused YAPPC ProductUnitIntent handoff test failed in ${command.cwd}: ${
        typeof result.status === 'number' ? `exit status ${result.status}` : 'unknown failure'
      }`,
    );
  }
}

const boundaryResult = runPnpm(['check:yappc-artifact-intelligence-boundary'], repoRoot);
if (boundaryResult.error) {
  errors.push(
    `YAPPC artifact intelligence boundary check failed: ${boundaryResult.error.message}`,
  );
} else if (boundaryResult.status !== 0) {
  errors.push(
    `YAPPC artifact intelligence boundary check failed: exit status ${boundaryResult.status}`,
  );
}

if (errors.length > 0) {
  console.error('YAPPC ProductUnitIntent handoff check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('YAPPC ProductUnitIntent handoff check passed.');
