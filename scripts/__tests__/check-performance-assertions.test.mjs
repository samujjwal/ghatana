#!/usr/bin/env node

/**
 * Tests for CI-safe performance assertions
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { 
  assertPerformanceMetric,
  validateLatencyAssertions,
  validateThroughputAssertions,
  validateErrorRateAssertions,
  validateMemoryAssertions,
  runPerformanceAssertions,
} from '../check-performance-assertions.mjs';

test('assertPerformanceMetric passes when within budget', () => {
  const result = assertPerformanceMetric(80, 100, 'test_metric', 0.1);
  assert.equal(result.pass, true);
  assert.equal(result.reason, 'Within budget');
  assert.equal(result.measuredValue, 80);
  assert.equal(result.budgetValue, 100);
});

test('assertPerformanceMetric fails when exceeding budget', () => {
  const result = assertPerformanceMetric(120, 100, 'test_metric', 0.1);
  assert.equal(result.pass, false);
  assert.ok(result.reason.includes('Exceeds budget'));
});

test('assertPerformanceMetric returns failure when no measurement', () => {
  const result = assertPerformanceMetric(null, 100, 'test_metric', 0.1);
  assert.equal(result.pass, false);
  assert.equal(result.reason, 'No measurement available');
});

test('validateLatencyAssertions validates p50, p95, p99', () => {
  const budget = {
    latencyMs: { p50: 100, p95: 200, p99: 500 },
  };
  const measurements = {
    latency: { p50: 80, p95: 170, p99: 450 },
  };

  const assertions = validateLatencyAssertions('test-workflow', budget, measurements);
  assert.equal(assertions.length, 3);
  assert.ok(assertions.every(a => a.pass));
});

test('validateLatencyAssertions fails when p95 exceeds budget', () => {
  const budget = {
    latencyMs: { p50: 100, p95: 200, p99: 500 },
  };
  const measurements = {
    latency: { p50: 80, p95: 250, p99: 450 },
  };

  const assertions = validateLatencyAssertions('test-workflow', budget, measurements);
  const p95Assertion = assertions.find(a => a.metricName === 'latency_p95_ms');
  assert.equal(p95Assertion.pass, false);
});

test('validateThroughputAssertions validates minimum throughput', () => {
  const budget = {
    throughputRps: { min: 100 },
  };
  const measurements = {
    throughput: { actual: 120 },
  };

  const assertions = validateThroughputAssertions('test-workflow', budget, measurements);
  assert.equal(assertions.length, 1);
  assert.equal(assertions[0].pass, true);
});

test('validateThroughputAssertions fails when below minimum', () => {
  const budget = {
    throughputRps: { min: 100 },
  };
  const measurements = {
    throughput: { actual: 80 },
  };

  const assertions = validateThroughputAssertions('test-workflow', budget, measurements);
  assert.equal(assertions[0].pass, false);
});

test('validateErrorRateAssertions validates error rate budget', () => {
  const budget = {
    errorRate: { max: 0.01 },
  };
  const measurements = {
    errorRate: 0.005,
  };

  const assertions = validateErrorRateAssertions('test-workflow', budget, measurements);
  assert.equal(assertions.length, 1);
  assert.equal(assertions[0].pass, true);
});

test('validateErrorRateAssertions fails when error rate exceeds budget', () => {
  const budget = {
    errorRate: { max: 0.01 },
  };
  const measurements = {
    errorRate: 0.02,
  };

  const assertions = validateErrorRateAssertions('test-workflow', budget, measurements);
  assert.equal(assertions[0].pass, false);
});

test('validateMemoryAssertions validates peak memory', () => {
  const budget = {
    memoryMb: { max: 512 },
  };
  const measurements = {
    memory: { peak: 400 },
  };

  const assertions = validateMemoryAssertions('test-workflow', budget, measurements);
  assert.equal(assertions.length, 1);
  assert.equal(assertions[0].pass, true);
});

test('validateMemoryAssertions fails when memory exceeds budget', () => {
  const budget = {
    memoryMb: { max: 512 },
  };
  const measurements = {
    memory: { peak: 600 },
  };

  const assertions = validateMemoryAssertions('test-workflow', budget, measurements);
  assert.equal(assertions[0].pass, false);
});

test('runPerformanceAssertions generates synthetic measurements in CI mode', () => {
  const result = runPerformanceAssertions(true);
  assert.equal(result.mode, 'synthetic-ci');
  assert.ok(result.summary.totalAssertions >= 0);
});

test('runPerformanceAssertions returns pass status based on assertions', () => {
  const result = runPerformanceAssertions(true);
  assert.equal(typeof result.pass, 'boolean');
});
