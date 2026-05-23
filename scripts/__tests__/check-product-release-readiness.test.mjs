import test from 'node:test';
import assert from 'node:assert/strict';
import { runProductReleaseReadinessCheck } from '../check-product-release-readiness.mjs';

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
