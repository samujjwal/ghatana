import test from 'node:test';
import assert from 'node:assert/strict';
import { runProductReleaseReadinessCheck } from '../check-product-release-readiness.mjs';

test('product release readiness validates release workflow and manifest provenance', () => {
  const evidence = runProductReleaseReadinessCheck({ writeEvidence: false });

  assert.equal(evidence.status, 'passed');
  assert.equal(evidence.journeys.length, 4);
  assert.ok(evidence.releaseProfiles.includes('standard-web-api-release'));
});
