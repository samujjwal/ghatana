#!/usr/bin/env node

/**
 * Tests for Phase 1 cost metering provider
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { 
  loadCostBudgets, 
  validateCostMeteringConfiguration, 
  generateCostMeteringEvidence,
  computeBudgetStatus,
  getBudgetStatus,
  BUDGET_STATUS,
} from '../check-product-cost-metering.mjs';

test('should validate cost metering configuration structure', () => {
  const costBudgets = {
    products: {
      'test-product': {
        ai: 100,
        query: 50
      }
    }
  };

  const violations = validateCostMeteringConfiguration(costBudgets);
  assert.equal(violations.length, 0);
});

test('should detect invalid cost budget structure', () => {
  const costBudgets = {
    products: {
      'test-product': 'invalid'
    }
  };

  const violations = validateCostMeteringConfiguration(costBudgets);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('invalid cost budget structure')));
});

test('should generate cost metering evidence with category results', () => {
  const costBudgets = {
    products: {
      'test-product': {
        ai: 100,
        storage: 50
      }
    }
  };

  const evidence = generateCostMeteringEvidence(costBudgets, []);
  assert.equal(evidence.results.length, 1);
  assert.equal(evidence.results[0].product, 'test-product');
  assert.equal(evidence.results[0].costCategories.length, 2);
  assert.equal(evidence.summary.totalCostCategories, 2);
});

test('should handle products with no cost budgets', () => {
  const costBudgets = {
    products: {}
  };

  const evidence = generateCostMeteringEvidence(costBudgets, []);
  assert.equal(evidence.results.length, 0);
  assert.equal(evidence.summary.totalCostCategories, 0);
});

test('should pass when all cost categories have metering sources', () => {
  const costBudgets = {
    products: {
      'test-product': {
        ai: 100,
        query: 50,
        storage: 25
      }
    }
  };

  const violations = validateCostMeteringConfiguration(costBudgets);
  assert.equal(violations.length, 0);
});

test('should generate metering source names for categories', () => {
  const costBudgets = {
    products: {
      'test-product': {
        ai: 100,
        compute: 50
      }
    }
  };

  const evidence = generateCostMeteringEvidence(costBudgets, []);
  const aiCategory = evidence.results[0].costCategories.find(c => c.category === 'ai');
  const computeCategory = evidence.results[0].costCategories.find(c => c.category === 'compute');
  
  assert.equal(aiCategory.meteringSource, 'llm-token-meter');
  assert.equal(computeCategory.meteringSource, 'compute-resource-meter');
});

test('computeBudgetStatus returns within-budget when usage is low', () => {
  const status = computeBudgetStatus(50, 100);
  assert.equal(status, BUDGET_STATUS.WITHIN_BUDGET);
});

test('computeBudgetStatus returns warning when usage exceeds threshold', () => {
  const status = computeBudgetStatus(85, 100);
  assert.equal(status, BUDGET_STATUS.WARNING);
});

test('computeBudgetStatus returns blocked when usage exceeds budget', () => {
  const status = computeBudgetStatus(110, 100);
  assert.equal(status, BUDGET_STATUS.BLOCKED);
});

test('computeBudgetStatus returns unknown when usage is null', () => {
  const status = computeBudgetStatus(null, 100);
  assert.equal(status, BUDGET_STATUS.UNKNOWN);
});

test('computeBudgetStatus returns unknown when budget is zero', () => {
  const status = computeBudgetStatus(50, 0);
  assert.equal(status, BUDGET_STATUS.UNKNOWN);
});

test('getBudgetStatus returns complete status information', () => {
  const status = getBudgetStatus('test-product', 'ai', 75, 100);
  
  assert.equal(status.product, 'test-product');
  assert.equal(status.category, 'ai');
  assert.equal(status.status, BUDGET_STATUS.WITHIN_BUDGET);
  assert.equal(status.currentUsage, 75);
  assert.equal(status.budget, 100);
  assert.equal(status.usageRatio, 0.75);
  assert.equal(status.warningThreshold, 0.8);
  assert.equal(status.blockThreshold, 1.0);
  assert.ok(status.recommendedAction);
});

test('getBudgetStatus returns blocked status with recommended action', () => {
  const status = getBudgetStatus('test-product', 'ai', 120, 100);
  
  assert.equal(status.status, BUDGET_STATUS.BLOCKED);
  assert.equal(status.usageRatio, 1.2);
  assert.ok(status.recommendedAction.includes('block'));
});

test('generateCostMeteringEvidence includes budget status in results', () => {
  const costBudgets = {
    products: {
      'test-product': {
        ai: 100,
      }
    }
  };

  const evidence = generateCostMeteringEvidence(costBudgets, []);
  assert.equal(evidence.results[0].overallBudgetStatus, BUDGET_STATUS.UNKNOWN);
  assert.ok(evidence.summary.productsByStatus);
});

test('generateCostMeteringEvidence tracks products by status', () => {
  const costBudgets = {
    products: {
      'product-1': { ai: 100 },
      'product-2': { ai: 100 },
    }
  };

  const evidence = generateCostMeteringEvidence(costBudgets, []);
  assert.equal(evidence.summary.productsByStatus.unknown, 2);
  assert.equal(evidence.summary.productsByStatus.withinBudget, 0);
});
