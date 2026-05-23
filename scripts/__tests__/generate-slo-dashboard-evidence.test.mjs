#!/usr/bin/env node

/**
 * Tests for SLO dashboard evidence generation
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { 
  generateDashboardEvidence, 
  generateProductDashboard, 
  generateCurrentMetrics 
} from '../generate-slo-dashboard-evidence.mjs';

test('generateDashboardEvidence creates dashboard evidence', () => {
  const evidence = generateDashboardEvidence('production');
  
  assert.equal(evidence.environment, 'production');
  assert.ok(evidence.generatedAt);
  assert.ok(evidence.dashboards);
  assert.ok(evidence.currentMetrics);
  assert.ok(evidence.summary);
});

test('generateProductDashboard creates panels for metrics', () => {
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 100, p95: 200, p99: 500 },
          },
        },
      },
    },
  };
  
  const metricMapping = {
    products: {
      'test-product': {
        workflows: [
          {
            name: 'test-workflow',
            sloMetrics: {
              'latency_p50': {
                metricName: 'http_request_duration_seconds_bucket{le="0.5"}',
                type: 'histogram',
                budgetRef: 'latencyMs.p50',
              },
            },
          },
        ],
      },
    },
  };
  
  const dashboard = generateProductDashboard('test-product', sloBudgets, metricMapping);
  
  assert.ok(dashboard);
  assert.equal(dashboard.title, 'test-product - SLO Dashboard');
  assert.ok(dashboard.panels.length > 0);
});

test('generateCurrentMetrics creates synthetic metric values', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: [
          {
            name: 'test-workflow',
            sloMetrics: {
              'latency_p50': {
                metricName: 'http_request_duration_seconds',
                type: 'histogram',
                budgetRef: 'latencyMs.p50',
              },
            },
          },
        ],
      },
    },
  };
  
  const currentMetrics = generateCurrentMetrics(metricMapping);
  
  assert.ok(currentMetrics['test-product']);
  assert.ok(currentMetrics['test-product'].workflows['test-workflow']);
  assert.ok(currentMetrics['test-product'].workflows['test-workflow'].metrics['latency_p50']);
});

test('generateDashboardEvidence includes summary statistics', () => {
  const evidence = generateDashboardEvidence('production');
  
  assert.ok(typeof evidence.summary.totalDashboards === 'number');
  assert.ok(typeof evidence.summary.totalPanels === 'number');
  assert.ok(Array.isArray(evidence.summary.products));
});
