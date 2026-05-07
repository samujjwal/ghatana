#!/usr/bin/env node
/**
 * YAPPC release-readiness evidence gate.
 *
 * This is intentionally fast: it verifies the release-critical cockpit,
 * builder, preview, persistence, security, OpenAPI, dashboard, and visual
 * regression gates are present and wired into package scripts before CI runs
 * the heavier suites.
 *
 * @doc.type script
 * @doc.purpose Fail release readiness when critical YAPPC gate evidence is missing
 * @doc.layer product
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const webRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(webRoot, '../../../..');

const requiredEvidence = [
  {
    area: 'cockpit',
    files: [
      'products/yappc/frontend/web/src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx',
      'products/yappc/frontend/web/src/components/phase/__tests__/PhaseCockpitLayout.test.tsx',
      'products/yappc/frontend/web/src/services/phase/__tests__/PhaseBuilders.test.ts',
    ],
  },
  {
    area: 'builder',
    files: [
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/PageDesigner.test.tsx',
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/builder-document-adapter.test.ts',
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/contractVersioning.test.ts',
    ],
  },
  {
    area: 'preview',
    files: [
      'products/yappc/frontend/web/src/components/studio/__tests__/LivePreviewPanel.test.tsx',
      'products/yappc/frontend/web/src/routes/app/project/__tests__/PhaseStatusPanels.test.tsx',
      'products/yappc/frontend/web/src/services/compiler/__tests__/ArtifactCompilerRuntimeHealth.test.ts',
    ],
  },
  {
    area: 'persistence',
    files: [
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts',
      'products/yappc/frontend/web/src/components/canvas/page/pageArtifactPersistence.ts',
      'products/yappc/frontend/web/src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
    ],
  },
  {
    area: 'security',
    files: [
      'products/yappc/frontend/web/src/lib/api/__tests__/client.telemetry.test.ts',
      'products/yappc/frontend/web/src/routes/app/admin/__tests__/billing-teams-gate.test.tsx',
      'products/yappc/frontend/web/src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
    ],
  },
  {
    area: 'api-contract',
    files: [
      'products/yappc/docs/api/openapi.yaml',
      'products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts',
      'products/yappc/frontend/web/src/lib/api/client.ts',
    ],
  },
  {
    area: 'dashboard',
    files: [
      'products/yappc/frontend/web/src/routes/__tests__/dashboard.test.tsx',
      'products/yappc/frontend/web/src/routes/dashboard.tsx',
    ],
  },
  {
    area: 'visual-regression',
    files: [
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/dashboard-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/cockpit-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/canvas-collapsed-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/canvas-expanded-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/builder-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/preview-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/conflict-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/offline-chromium-darwin.png',
      'products/yappc/frontend/web/e2e/visual-regression.spec.ts-snapshots/import-chromium-darwin.png',
    ],
  },
];

const requiredScripts = [
  'test:e2e:visual',
  'test:performance:budgets',
  'verify:release-readiness',
];

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function resolveFromRepo(relativePath) {
  return path.join(repoRoot, relativePath);
}

function checkFile(relativePath) {
  return existsSync(resolveFromRepo(relativePath));
}

const failures = [];
const passes = [];

for (const gate of requiredEvidence) {
  const missing = gate.files.filter((file) => !checkFile(file));
  if (missing.length > 0) {
    failures.push(`${gate.area}: missing ${missing.join(', ')}`);
  } else {
    passes.push(`${gate.area}: ${gate.files.length} evidence file(s) present`);
  }
}

const packageJson = readJson(path.join(webRoot, 'package.json'));
const scripts = packageJson.scripts ?? {};

for (const script of requiredScripts) {
  if (typeof scripts[script] !== 'string' || scripts[script].trim().length === 0) {
    failures.push(`package.json: missing script ${script}`);
  } else {
    passes.push(`package.json: script ${script} present`);
  }
}

const readinessChecklist = readFileSync(
  resolveFromRepo('products/yappc/docs/RELEASE_READINESS_CHECKLIST.md'),
  'utf8',
);
if (!readinessChecklist.includes('pnpm --filter @ghatana/yappc-web-app run verify:release-readiness')) {
  failures.push('release checklist: verify:release-readiness command is not documented');
} else {
  passes.push('release checklist: verify:release-readiness command documented');
}

for (const message of passes) {
  console.log(`PASS ${message}`);
}

if (failures.length > 0) {
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }
  console.error(`Release readiness gate failed with ${failures.length} issue(s).`);
  process.exit(1);
}

console.log(`Release readiness gate passed with ${passes.length} checks.`);
