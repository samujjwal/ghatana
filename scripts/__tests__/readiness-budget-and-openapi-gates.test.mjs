import test from 'node:test';
import assert from 'node:assert/strict';

import { runProductSloBudgetCheck } from '../check-product-slo-budgets.mjs';
import { runProductCostBudgetCheck } from '../check-product-cost-budgets.mjs';
import { runProductDomainInvariantCheck } from '../check-product-domain-invariants.mjs';

test('product SLO budgets are defined for active business products', () => {
  const result = runProductSloBudgetCheck();
  assert.equal(result.pass, true);
  assert.deepEqual(result.violations, []);
});

test('product cost budgets are defined for active business products', () => {
  const result = runProductCostBudgetCheck();
  assert.equal(result.pass, true);
  assert.deepEqual(result.violations, []);
});

test('product domain invariant evidence is present', () => {
  const result = runProductDomainInvariantCheck();
  assert.equal(result.pass, true);
  assert.deepEqual(result.violations, []);
});
