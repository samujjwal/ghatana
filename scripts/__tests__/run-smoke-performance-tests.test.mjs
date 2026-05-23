#!/usr/bin/env node

/**
 * Tests for smoke performance tests
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { runSmokeTest, validateAgainstBudgets } from '../run-smoke-performance-tests.mjs';

test('runSmokeTest measures latency statistics', async () => {
  const config = { iterations: 10, concurrency: 2, timeoutMs: 30000 };
  const result = await runSmokeTest('http://localhost:8080/api/test', config);
  
  assert.equal(result.totalRequests, 10);
  assert.ok(result.successfulRequests >= 0);
  assert.ok(result.latency.p50 !== undefined);
  assert.ok(result.latency.p95 !== undefined);
  assert.ok(result.latency.p99 !== undefined);
  assert.ok(result.latency.avg !== undefined);
});

test('runSmokeTest calculates percentiles correctly', async () => {
  const config = { iterations: 20, concurrency: 2, timeoutMs: 30000 };
  const result = await runSmokeTest('http://localhost:8080/api/test', config);
  
  assert.ok(result.latency.min <= result.latency.p50);
  assert.ok(result.latency.p50 <= result.latency.p95);
  assert.ok(result.latency.p95 <= result.latency.p99);
  assert.ok(result.latency.p99 <= result.latency.max);
});

test('validateAgainstBudgets detects latency violations', () => {
  const smokeResults = [
    {
      product: 'test-product',
      workflow: 'test-workflow',
      latency: { p50: 150, p95: 250, p99: 500 },
      errorRate: 0.01,
    },
  ];
  
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 100, p95: 200, p99: 400 },
            errorRate: { max: 0.05 },
          },
        },
      },
    },
  };
  
  const violations = validateAgainstBudgets(smokeResults, sloBudgets);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('p50 latency')));
});

test('validateAgainstBudgets detects error rate violations', () => {
  const smokeResults = [
    {
      product: 'test-product',
      workflow: 'test-workflow',
      latency: { p50: 80, p95: 150, p99: 300 },
      errorRate: 0.1,
    },
  ];
  
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 100, p95: 200, p99: 400 },
            errorRate: { max: 0.05 },
          },
        },
      },
    },
  };
  
  const violations = validateAgainstBudgets(smokeResults, sloBudgets);
  assert.ok(violations.some(v => v.includes('error rate')));
});

test('validateAgainstBudgets passes when within budget', () => {
  const smokeResults = [
    {
      product: 'test-product',
      workflow: 'test-workflow',
      latency: { p50: 80, p95: 150, p99: 300 },
      errorRate: 0.01,
    },
  ];
  
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 100, p95: 200, p99: 400 },
            errorRate: { max: 0.05 },
          },
        },
      },
    },
  };
  
  const violations = validateAgainstBudgets(smokeResults, sloBudgets);
  assert.equal(violations.length, 0);
});
