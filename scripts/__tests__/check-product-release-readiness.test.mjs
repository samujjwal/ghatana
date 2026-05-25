import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildScopedExecutionOrder,
  buildProductScorecard,
  filterReleaseEligibleProducts,
  runProductReleaseReadinessCheck,
} from '../check-product-release-readiness.mjs';

test('product release readiness validates release workflow and manifest provenance', () => {
  const evidence = runProductReleaseReadinessCheck({ writeEvidence: false });

  assert.equal(evidence.status, 'passed');
  assert.equal(evidence.journeys.length, 4);
  assert.ok(evidence.releaseProfiles.includes('standard-web-api-release'));
  assert.deepEqual(evidence.artifactAuthoringGateScripts, [
    'pnpm:check:kernel-authoring-pipeline',
    'pnpm:check:artifact-roundtrip',
    'pnpm:check:generated-artifact-validation-pipeline',
    'pnpm:check:studio-production-profile:strict',
    'pnpm:check:studio-source-acquisition-worker',
    'pnpm:check:studio-workflow-persistence-contracts',
  ]);
  assert.ok(
    evidence.journeys.some((journey) =>
      journey.areas.some((area) => area.area === 'artifact-authoring-platform-readiness'),
    ),
  );
  assert.ok(
    evidence.releaseAreas.some((area) => area.area === 'artifact-authoring-release-gates'),
  );
});

test('release readiness filters out non-execution-eligible products', () => {
  const registry = {
    'digital-marketing': {
      kind: 'business-product',
      metadata: { status: 'active' },
      lifecycleExecutionAllowed: true,
      lifecycleStatus: 'enabled',
    },
    finance: {
      kind: 'business-product',
      metadata: { status: 'active' },
      lifecycleExecutionAllowed: false,
      lifecycleStatus: 'planned',
    },
    infra: {
      kind: 'platform-provider',
      metadata: { status: 'active' },
      lifecycleExecutionAllowed: true,
      lifecycleStatus: 'enabled',
    },
  };

  assert.deepEqual(
    filterReleaseEligibleProducts(['digital-marketing', 'finance', 'infra'], registry),
    ['digital-marketing'],
  );
});

test('product release readiness scopes execution to the selected product family', () => {
  const dataCloudPlan = buildScopedExecutionOrder(['data-cloud'], {
    paths: ['products/data-cloud/lifecycle/readiness-evidence.yaml'],
    releaseRisk: false,
  });
  const dmosPlan = buildScopedExecutionOrder(['digital-marketing'], {
    paths: ['products/digital-marketing/kernel-product.yaml'],
    releaseRisk: false,
  });

  assert.ok(dataCloudPlan.includes('pnpm:check:data-cloud-release-gate'));
  assert.ok(!dataCloudPlan.includes('./scripts/check-dmos-production-wiring.mjs'));
  assert.ok(dmosPlan.includes('./scripts/check-dmos-production-wiring.mjs'));
  assert.ok(dmosPlan.includes('./scripts/check-data-cloud-platform-provider-readiness.mjs'));
  assert.ok(dmosPlan.includes('./scripts/check-data-cloud-release-runtime-profile.mjs'));
  assert.ok(!dmosPlan.includes('pnpm:check:data-cloud-release-gate'));
});

test('product release readiness runs Data Cloud foundation checks before product scoped gates', () => {
  const phrPlan = buildScopedExecutionOrder(['phr'], {
    explicitProductScope: true,
    paths: ['products/phr/kernel-product.yaml'],
    releaseRisk: false,
  });

  assert.ok(phrPlan.includes('./scripts/check-data-cloud-platform-provider-readiness.mjs'));
  assert.ok(phrPlan.includes('./scripts/check-data-cloud-release-runtime-profile.mjs'));
  assert.ok(phrPlan.indexOf('./scripts/check-data-cloud-platform-provider-readiness.mjs') < phrPlan.indexOf('./scripts/check-phr-lifecycle-readiness.mjs'));
});

test('product scorecard uses product execution evidence instead of global maturity baseline', () => {
  const registry = {
    phr: {
      id: 'phr',
      kind: 'business-product',
      metadata: { status: 'active' },
      lifecycleExecutionAllowed: true,
      lifecycleStatus: 'enabled',
    },
  };
  const wave2Rows = new Map([
    ['phr', { productId: 'phr', area: { a11y: true, i18n: true, aiGovernance: true, performanceSlo: true, runtimeTruth: true } }],
  ]);
  const runs = [
    { script: 'gate-a', status: 0 },
    { script: 'gate-b', status: 0 },
    { script: 'gate-c', status: 0 },
  ];

  const scorecard = buildProductScorecard('phr', runs, ['standard-web-api-release'], registry, {
    wave2Rows,
    releaseTargetScore: 4,
  });

  assert.equal(scorecard.releaseVerdict, 'pass');
  assert.equal(scorecard.averageScore, 5);
  assert.deepEqual(scorecard.belowTargetDimensions, []);
});
