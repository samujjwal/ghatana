#!/usr/bin/env node

/**
 * Tests for Phase 1 workflow performance smoke tests
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { 
  loadMetricMapping, 
  checkPerformanceTestCoverage, 
  findPerformanceTestPath,
  generatePerformanceTestEvidence 
} from '../check-workflow-performance-smoke.mjs';

test('should validate performance test coverage for workflows', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: [
          {
            name: 'test-workflow',
            description: 'Test workflow',
            sloMetrics: {}
          }
        ]
      }
    }
  };

  const { violations, results } = checkPerformanceTestCoverage(metricMapping);
  assert.ok(results.length > 0);
  assert.equal(results[0].product, 'test-product');
});

test('should detect missing performance tests', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: [
          {
            name: 'unknown-workflow',
            description: 'Unknown workflow',
            sloMetrics: {}
          }
        ]
      }
    }
  };

  const { violations } = checkPerformanceTestCoverage(metricMapping);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('no performance test')));
});

test('should generate performance test evidence with workflow results', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: [
          {
            name: 'test-workflow',
            description: 'Test workflow',
            sloMetrics: {}
          }
        ]
      }
    }
  };

  const { violations, results } = checkPerformanceTestCoverage(metricMapping);
  const evidence = generatePerformanceTestEvidence(metricMapping, violations, results);
  
  assert.equal(evidence.results.length, 1);
  assert.equal(evidence.summary.totalWorkflows, 1);
});

test('should pass when all workflows have performance tests', () => {
  const metricMapping = {
    products: {
      'digital-marketing': {
        workflows: [
          {
            name: 'campaign-activation',
            description: 'Campaign activation',
            sloMetrics: {}
          }
        ]
      }
    }
  };

  const { violations } = checkPerformanceTestCoverage(metricMapping);
  assert.deepEqual(violations, []);
});

test('should handle products with no workflows', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: []
      }
    }
  };

  const { violations, results } = checkPerformanceTestCoverage(metricMapping);
  assert.equal(violations.length, 0);
  assert.equal(results[0].workflows.length, 0);
});

test('should map PHR workflows to the current product performance smoke suite', () => {
  assert.equal(
    findPerformanceTestPath('phr', 'patient-record-fetch'),
    'products/phr/apps/web/e2e/phr-performance-smoke.spec.ts'
  );
  assert.equal(
    findPerformanceTestPath('phr', 'fhir-validation'),
    'products/phr/src/test/java/com/ghatana/phr/performance/PhrWorkflowPerformanceSmokeTest.java'
  );
});

test('should scope performance coverage to a target product', () => {
  const metricMapping = loadMetricMapping();

  const { violations, results } = checkPerformanceTestCoverage(metricMapping, {
    targetProduct: 'phr'
  });

  assert.deepEqual(violations, []);
  assert.deepEqual(results.map(result => result.product), ['phr']);
  assert.equal(results[0].workflows.length, 4);
  assert.ok(results[0].workflows.every(workflow => workflow.hasPerformanceTest));
});

test('should report mapped workflows whose test file is missing', () => {
  const rootDir = mkdtempSync(path.join(tmpdir(), 'workflow-performance-smoke-'));
  try {
    const metricMapping = {
      products: {
        'digital-marketing': {
          workflows: [
            {
              name: 'campaign-activation',
              description: 'Campaign activation',
              sloMetrics: {}
            }
          ]
        }
      }
    };

    const { violations } = checkPerformanceTestCoverage(metricMapping, { rootDir });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /campaign-activation/);
  } finally {
    rmSync(rootDir, { recursive: true, force: true });
  }
});

test('should report an unknown target product', () => {
  const metricMapping = {
    products: {
      phr: {
        workflows: []
      }
    }
  };

  const { violations, results } = checkPerformanceTestCoverage(metricMapping, {
    targetProduct: 'missing-product'
  });

  assert.equal(results.length, 0);
  assert.deepEqual(violations, [
    "Product 'missing-product' was not found in config/product-metric-mapping.json"
  ]);
});
