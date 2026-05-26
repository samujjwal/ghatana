#!/usr/bin/env node

import { createChecker, readJson, readText } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-001 independent release evidence bundle',
  evidencePath: '.kernel/evidence/yappc/release-bundle.json',
});

const evidenceItems = [
  {
    id: 'platformProviderReadiness',
    command: 'pnpm check:yappc-platform-provider-readiness',
    files: ['scripts/check-yappc-platform-provider-readiness.mjs'],
  },
  {
    id: 'productFamilyControlPlane',
    command: 'node ./scripts/check-yappc-product-family-control-plane.mjs',
    files: ['scripts/check-yappc-product-family-control-plane.mjs'],
  },
  {
    id: 'productUnitIntentHandoff',
    command: 'pnpm check:yappc-product-unit-intent-handoff',
    files: ['scripts/check-yappc-product-unit-intent-handoff.mjs'],
  },
  {
    id: 'artifactIntelligenceBoundary',
    command: 'pnpm check:yappc-artifact-intelligence-boundary',
    files: ['scripts/check-yappc-artifact-intelligence-boundary.mjs'],
  },
  {
    id: 'dataCloudIntegration',
    command: 'pnpm check:yappc-datacloud-contract-integration',
    files: ['.kernel/evidence/yappc/datacloud-contract-integration.json'],
  },
  {
    id: 'agentIntegration',
    command: 'pnpm check:yappc-governed-agent-runtime',
    files: ['.kernel/evidence/yappc/governed-agent-runtime.json'],
  },
  {
    id: 'kernelBridgeIntegration',
    command: 'pnpm check:yappc-kernel-bridge-contracts',
    files: ['.kernel/evidence/yappc/kernel-bridge-contracts.json'],
  },
  {
    id: 'featureCompletenessMatrix',
    command: 'pnpm check:yappc-feature-completeness-matrix',
    files: ['.kernel/evidence/yappc/feature-completeness-matrix.json'],
  },
  {
    id: 'artifactRoundtrip',
    command: 'pnpm check:yappc-kernel-artifact-roundtrip',
    files: ['.kernel/evidence/yappc/kernel-artifact-roundtrip.json'],
  },
  {
    id: 'duplicateRuntimePaths',
    command: 'pnpm check:yappc-duplicate-runtime-paths',
    files: ['.kernel/evidence/yappc/duplicate-runtime-paths.json'],
  },
  {
    id: 'failureDegradedWorkflows',
    command: 'pnpm check:yappc-failure-degraded-workflows',
    files: ['.kernel/evidence/yappc/failure-degraded-workflows.json'],
  },
];

for (const item of evidenceItems) {
  for (const file of item.files) {
    checker.requireFile(file, `${item.id} evidence file ${file}`);
    checker.record(`${item.id} is scoped to YAPPC evidence`, !file.startsWith('.kernel/evidence/data-cloud'), { file });
    if (file.endsWith('.json')) {
      const evidence = readJson(file);
      checker.record(`${item.id} evidence passes`, evidence.pass === true, { file });
    }
  }
}

const packageJson = readText('package.json');
for (const item of evidenceItems) {
  checker.record(`${item.id} command is declared or script-backed`, packageJson.includes(item.command.replace('pnpm ', '')) || item.command.startsWith('node '), {
    command: item.command,
  });
}

checker.finish({ evidenceItems });
