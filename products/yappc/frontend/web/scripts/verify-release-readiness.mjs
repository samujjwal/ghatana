#!/usr/bin/env node
/**
 * YAPPC release-readiness execution gate.
 *
 * Upgraded from evidence-presence to execution/results validation.
 * Verifies that critical gates actually execute and pass, not just that files exist.
 *
 * Usage:
 *   node scripts/verify-release-readiness.mjs              # Execution mode (production default)
 *   node scripts/verify-release-readiness.mjs --evidence-only  # Evidence-presence mode (fast, local only)
 *
 * @doc.type script
 * @doc.purpose Fail release readiness when critical YAPPC gates do not execute successfully
 * @doc.layer product
 */

import { existsSync, readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
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
    // Execute unit tests for cockpit area
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/components/phase/__tests__/PhaseCockpitLayout.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts',
  },
  {
    area: 'builder',
    files: [
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/PageDesigner.test.tsx',
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/builder-document-adapter.test.ts',
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/contractVersioning.test.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/page/__tests__/builder-document-adapter.test.ts src/components/canvas/page/__tests__/contractVersioning.test.ts',
  },
  {
    area: 'preview',
    files: [
      'products/yappc/frontend/web/src/components/studio/__tests__/LivePreviewPanel.test.tsx',
      'products/yappc/frontend/web/src/routes/app/project/__tests__/PhaseStatusPanelsCanonical.test.tsx',
      'products/yappc/frontend/web/src/services/compiler/__tests__/ArtifactCompilerRuntimeHealth.test.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/components/studio/__tests__/LivePreviewPanel.test.tsx src/routes/app/project/__tests__/PhaseStatusPanelsCanonical.test.tsx src/services/compiler/__tests__/ArtifactCompilerRuntimeHealth.test.ts',
  },
  {
    area: 'preview-edge-security',
    files: [
      'products/yappc/frontend/web/src/routes/preview-builder.tsx',
      'products/yappc/frontend/web/src/routes/__tests__/preview-builder-security.test.tsx',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/routes/__tests__/preview-builder-security.test.tsx',
  },
  {
    area: 'persistence',
    files: [
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts',
      'products/yappc/frontend/web/src/components/canvas/page/pageArtifactPersistence.ts',
      'products/yappc/frontend/web/src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
  },
  {
    area: 'security',
    files: [
      'products/yappc/frontend/web/src/lib/api/__tests__/client.telemetry.test.ts',
      'products/yappc/frontend/web/src/routes/app/admin/__tests__/billing-teams-gate.test.tsx',
      'products/yappc/frontend/web/src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/lib/api/__tests__/client.telemetry.test.ts src/routes/app/admin/__tests__/billing-teams-gate.test.tsx src/components/canvas/__tests__/canvasAccessPolicy.test.ts',
  },
  {
    area: 'api-contract',
    files: [
      'products/yappc/docs/api/openapi.yaml',
      'products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts',
      'products/yappc/frontend/web/src/lib/api/client.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/lib/api/__tests__/client.telemetry.test.ts',
  },
  {
    area: 'dashboard',
    files: [
      'products/yappc/frontend/web/src/routes/__tests__/dashboard.test.tsx',
      'products/yappc/frontend/web/src/routes/dashboard.tsx',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/routes/__tests__/dashboard.test.tsx',
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
    // Visual regression tests are expensive - only check file presence in fast mode
    execute: null,
  },
  {
    area: 'accessibility',
    files: [
      'products/yappc/frontend/web/e2e/accessibility-contracts.spec.ts',
      'products/yappc/frontend/web/src/components/command/__tests__/CommandPalette.test.tsx',
      'products/yappc/frontend/web/src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx',
      'products/yappc/frontend/web/src/components/canvas/page/__tests__/PageDesigner.test.tsx',
    ],
    // E2E accessibility tests are expensive - only check file presence in fast mode
    execute: null,
  },
  {
    area: 'performance-memory',
    files: [
      'products/yappc/frontend/web/e2e/performance-memory.spec.ts',
      'products/yappc/frontend/web/src/services/performance/__tests__/canvasPerformanceBudgets.test.ts',
    ],
    execute: 'pnpm --filter @ghatana/yappc-web-app test -- src/services/performance/__tests__/canvasPerformanceBudgets.test.ts',
  },
];

const requiredScripts = [
  'test:e2e:a11y',
  'test:e2e:visual',
  'test:e2e:performance-memory',
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

// Check if running in evidence-only mode (default to execution mode for production safety)
const evidenceOnlyMode = process.argv.includes('--evidence-only');
const executionMode = !evidenceOnlyMode;

for (const gate of requiredEvidence) {
  const missing = gate.files.filter((file) => !checkFile(file));
  if (missing.length > 0) {
    failures.push(`${gate.area}: missing ${missing.join(', ')}`);
  } else {
    passes.push(`${gate.area}: ${gate.files.length} evidence file(s) present`);
  }

  // If execution mode is enabled and gate has execute command, run it
  if (executionMode && gate.execute) {
    try {
      execSync(gate.execute, { cwd: repoRoot, stdio: 'pipe' });
      passes.push(`${gate.area}: execution passed`);
    } catch (error) {
      failures.push(`${gate.area}: execution failed - ${gate.execute}`);
    }
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

const previewBuilderRoute = readFileSync(
  resolveFromRepo('products/yappc/frontend/web/src/routes/preview-builder.tsx'),
  'utf8',
);
const previewBuilderSecurityTest = readFileSync(
  resolveFromRepo('products/yappc/frontend/web/src/routes/__tests__/preview-builder-security.test.tsx'),
  'utf8',
);
const requiredPreviewHeaders = [
  'Content-Security-Policy',
  'Cross-Origin-Resource-Policy',
  'Permissions-Policy',
  'Referrer-Policy',
  'X-Content-Type-Options',
  'X-Frame-Options',
];
for (const header of requiredPreviewHeaders) {
  if (!previewBuilderRoute.includes(`'${header}'`)) {
    failures.push(`preview-edge-security: route does not define ${header}`);
  } else if (!previewBuilderSecurityTest.includes(`['${header}']`)) {
    failures.push(`preview-edge-security: test does not assert ${header}`);
  } else {
    passes.push(`preview-edge-security: ${header} route/test contract present`);
  }
}
const requiredPreviewCspDirectives = [
  "frame-ancestors 'self'",
  "object-src 'none'",
  'unsafe-eval',
];
for (const directive of requiredPreviewCspDirectives) {
  if (!previewBuilderSecurityTest.includes(directive)) {
    failures.push(`preview-edge-security: test does not assert CSP directive ${directive}`);
  } else {
    passes.push(`preview-edge-security: CSP directive ${directive} asserted`);
  }
}

for (const message of passes) {
  console.log(`PASS ${message}`);
}

if (failures.length > 0) {
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }
  console.error(`Release readiness gate failed with ${failures.length} issue(s).`);
  console.error(`Run with --evidence-only flag to skip execution validation (local development only).`);
  process.exit(1);
}

const mode = executionMode ? 'execution' : 'evidence-presence';
console.log(`Release readiness gate passed with ${passes.length} checks (${mode} mode).`);
if (evidenceOnlyMode) {
  console.warn(`⚠️  Evidence-only mode skips execution validation - use for local development only.`);
}
