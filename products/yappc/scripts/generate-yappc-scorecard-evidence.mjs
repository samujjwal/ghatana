#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const outputDir = process.argv[2] ?? 'products/yappc/build/release-evidence';
const artifactRoot = process.argv[3] ?? 'artifacts';
const outputFile = join(outputDir, 'yappc-scorecard-evidence.json');

function gitValue(args) {
  try {
    return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function artifact(path) {
  return join(artifactRoot, path);
}

function evidenceStatus(paths) {
  const missing = paths.filter((path) => !existsSync(path));
  return {
    status: missing.length === 0 ? 'present' : 'missing',
    missing,
  };
}

const dimensions = [
  {
    id: 'backend',
    label: 'Backend build and tests',
    scorecardArea: 'Correctness',
    commands: [
      './gradlew :products:yappc:core:yappc-services:test',
      './gradlew :products:yappc:core:scaffold:api:test',
    ],
    requiredArtifacts: [artifact('backend-tests')],
    evidenceRefs: ['products/yappc/docs/TEST_SUITES.md#backend-focused-slices'],
  },
  {
    id: 'frontend',
    label: 'Frontend build, typecheck, and tests',
    scorecardArea: 'Correctness',
    commands: ['pnpm -C products/yappc/frontend/web test:regression'],
    requiredArtifacts: [artifact('frontend-tests'), artifact('frontend-coverage')],
    evidenceRefs: ['products/yappc/docs/TEST_SUITES.md#yappc-test-suites'],
  },
  {
    id: 'contract',
    label: 'OpenAPI, route, client, and i18n contracts',
    scorecardArea: 'Contract Integrity',
    commands: ['pnpm -C products/yappc/frontend/web test:contract'],
    requiredArtifacts: [artifact('contract-report')],
    evidenceRefs: [
      'products/yappc/docs/api/route-manifest.yaml',
      'products/yappc/docs/api/openapi.yaml',
      'products/yappc/build/release-evidence/yappc-ci-execution-proof.json',
    ],
  },
  {
    id: 'e2e',
    label: 'Lifecycle E2E readiness',
    scorecardArea: 'End-to-End Lifecycle',
    commands: ['pnpm -C products/yappc/frontend/web test:e2e'],
    requiredArtifacts: [artifact('playwright-report'), artifact('e2e-results')],
    evidenceRefs: ['products/yappc/frontend/web/docs/e2e-matrix.json'],
  },
  {
    id: 'a11y',
    label: 'Accessibility readiness',
    scorecardArea: 'User Experience',
    commands: ['pnpm -C products/yappc/frontend/web test:a11y'],
    requiredArtifacts: [artifact('a11y-readiness')],
    evidenceRefs: ['products/yappc/frontend/web/e2e/accessibility-contracts.spec.ts'],
  },
  {
    id: 'performance',
    label: 'Performance and bundle budgets',
    scorecardArea: 'Performance',
    commands: [
      'pnpm -C products/yappc/frontend/web test:performance',
      'pnpm -C products/yappc/frontend --filter @ghatana/yappc-web-app run check:bundle-budget -- --report --ci',
    ],
    requiredArtifacts: [
      artifact('release-gate-evidence/products/yappc/frontend/performance/reports/web-bundle-budget.json'),
    ],
    evidenceRefs: [
      'products/yappc/frontend/scripts/check-web-bundle-budget.cjs',
      'products/yappc/frontend/web/src/services/performance/__tests__/canvasPerformanceBudgets.test.ts',
    ],
  },
  {
    id: 'security',
    label: 'Tenant, role, scope, flag, and policy security',
    scorecardArea: 'Security',
    commands: [
      './gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.YappcSecurityMatrixTest"',
    ],
    requiredArtifacts: [artifact('backend-tests')],
    evidenceRefs: [
      'products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/YappcSecurityMatrixTest.java',
    ],
  },
  {
    id: 'privacy',
    label: 'Privacy classification and redaction',
    scorecardArea: 'Privacy',
    commands: [
      './gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.YappcPrivacyContractTest"',
    ],
    requiredArtifacts: [artifact('backend-tests')],
    evidenceRefs: [
      'products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/YappcPrivacyContractTest.java',
    ],
  },
  {
    id: 'governance',
    label: 'Policy and governance fail-closed behavior',
    scorecardArea: 'Governance',
    commands: [
      './gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.PhaseActionAuthorizationServiceTest"',
    ],
    requiredArtifacts: [artifact('backend-tests'), artifact('release-gate-evidence')],
    evidenceRefs: [
      'products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationServiceTest.java',
      'products/yappc/docs/YAPPC_BACKLOG_PROGRESS.md',
    ],
  },
  {
    id: 'release-gates',
    label: 'SLO, cost, domain invariant, and OpenAPI release gates',
    scorecardArea: 'Release Readiness',
    commands: [
      'pnpm check:product-slo-budgets',
      'pnpm check:product-cost-budgets',
      'pnpm check:product-domain-invariants',
      'pnpm check:openapi-breaking-changes',
    ],
    requiredArtifacts: [
      artifact('release-gate-evidence/product-slo-budgets.json'),
      artifact('release-gate-evidence/product-cost-budgets.json'),
      artifact('release-gate-evidence/product-domain-invariants.json'),
      artifact('release-gate-evidence/openapi-breaking-changes.json'),
    ],
    evidenceRefs: [
      '.kernel/evidence/product-slo-budgets.json',
      '.kernel/evidence/product-cost-budgets.json',
      '.kernel/evidence/product-domain-invariants.json',
      '.kernel/evidence/openapi-breaking-changes.json',
    ],
  },
  {
    id: 'startup-diagnostics',
    label: 'Runtime startup health and metrics',
    scorecardArea: 'Operational Readiness',
    commands: ['curl /health/liveness', 'curl /health/readiness', 'curl /metrics'],
    requiredArtifacts: [artifact('startup-diagnostics')],
    evidenceRefs: ['.github/workflows/yappc-ci.yml#startup-diagnostics'],
  },
];

const scoredDimensions = dimensions.map((dimension) => {
  const evidence = evidenceStatus(dimension.requiredArtifacts);
  return {
    ...dimension,
    status: evidence.status,
    missingArtifacts: evidence.missing,
  };
});

const missingDimensions = scoredDimensions.filter((dimension) => dimension.status !== 'present');
const scorecard = {
  schemaVersion: '1.0.0',
  product: 'yappc',
  generatedAt: process.env.YAPPC_RELEASE_EVIDENCE_GENERATED_AT ?? new Date().toISOString(),
  workflow: process.env.GITHUB_WORKFLOW ?? '.github/workflows/yappc-ci.yml',
  run: {
    runId: process.env.GITHUB_RUN_ID ?? null,
    runAttempt: process.env.GITHUB_RUN_ATTEMPT ?? null,
    commit: process.env.GITHUB_SHA ?? gitValue(['rev-parse', 'HEAD']),
    branch: process.env.GITHUB_REF_NAME ?? gitValue(['rev-parse', '--abbrev-ref', 'HEAD']),
  },
  summary: {
    status: missingDimensions.length === 0 ? 'complete' : 'missing-evidence',
    totalDimensions: scoredDimensions.length,
    presentDimensions: scoredDimensions.length - missingDimensions.length,
    missingDimensions: missingDimensions.map((dimension) => dimension.id),
  },
  dimensions: scoredDimensions,
};

mkdirSync(outputDir, { recursive: true });
writeFileSync(outputFile, `${JSON.stringify(scorecard, null, 2)}\n`, 'utf8');

console.log(`Wrote ${outputFile}`);
