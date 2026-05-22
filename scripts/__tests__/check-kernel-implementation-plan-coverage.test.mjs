import test from 'node:test';
import assert from 'node:assert/strict';
import { runImplementationPlanCoverageCheck } from '../check-kernel-implementation-plan-coverage.mjs';

test('implementation plan coverage validates 47 dimensions and actionable tickets', () => {
  const evidence = runImplementationPlanCoverageCheck({ writeEvidence: false });

  assert.equal(evidence.status, 'passed');
  assert.equal(evidence.dimensions.total, 47);
  assert.equal(evidence.dimensions.covered, 47);
  assert.equal(evidence.productionReadinessTickets.total, 18);
  assert.deepEqual(evidence.dimensions.uncovered, []);
});
