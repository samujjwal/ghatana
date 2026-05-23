#!/usr/bin/env node

/**
 * Tests for Phase 1 measured SLO evidence provider
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { 
  loadSLOBudgets, 
  loadMetricMapping, 
  checkMetricMappingCoverage, 
  validateMetricConfiguration,
  generateEvidence 
} from '../check-product-slo-measurements.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

function createTempConfig() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-slo-measurements-'));
}

test('should validate metric configuration structure', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.5, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              },
              p95: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              },
              p99: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.99, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              }
            },
            throughputRps: {
              min: {
                metricName: 'http_requests_total',
                labels: { workflow: 'test-workflow' },
                query: 'rate(http_requests_total{workflow="test-workflow"}[5m])'
              }
            },
            memoryMb: {
              max: {
                metricName: 'process_resident_memory_bytes',
                labels: { product: 'test-product' },
                query: 'process_resident_memory_bytes{product="test-product"} / 1024 / 1024'
              }
            },
            queueDepth: {
              max: {
                metricName: 'queue_depth',
                labels: { workflow: 'test-workflow' },
                query: 'queue_depth{workflow="test-workflow"}'
              }
            },
            backgroundJobRuntimeMs: {
              max: {
                metricName: 'background_job_duration_seconds',
                labels: { product: 'test-product' },
                query: 'histogram_quantile(0.99, rate(background_job_duration_seconds_bucket{product="test-product"}[5m])) * 1000'
              }
            }
          }
        }
      }
    }
  };

  const violations = validateMetricConfiguration(metricMapping);
  assert.equal(violations.length, 0);
});

test('should detect missing metricName in latency config', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.5, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              }
            }
          }
        }
      }
    }
  };

  const violations = validateMetricConfiguration(metricMapping);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('missing metricName')));
});

test('should detect missing query in latency config', () => {
  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' }
              }
            }
          }
        }
      }
    }
  };

  const violations = validateMetricConfiguration(metricMapping);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('missing query')));
});

test('should detect missing metric mapping for budget items', () => {
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 200, p95: 800, p99: 1400 },
            throughputRps: { min: 40 },
            memoryMb: { max: 2048 }
          }
        }
      }
    }
  };

  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.5, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              },
              p95: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              }
              // Missing p99
            },
            throughputRps: {
              min: {
                metricName: 'http_requests_total',
                labels: { workflow: 'test-workflow' },
                query: 'rate(http_requests_total{workflow="test-workflow"}[5m])'
              }
            }
            // Missing memoryMb
          }
        }
      }
    }
  };

  const violations = checkMetricMappingCoverage(sloBudgets, metricMapping);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('latencyMs.p99')));
  assert.ok(violations.some(v => v.includes('memoryMb.max')));
});

test('should generate evidence with workflow results', () => {
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 200, p95: 800, p99: 1400 }
          }
        }
      }
    }
  };

  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.5, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              }
            }
          }
        }
      }
    }
  };

  const evidence = generateEvidence(sloBudgets, metricMapping, []);
  assert.equal(evidence.results.length, 1);
  assert.equal(evidence.results[0].product, 'test-product');
  assert.equal(evidence.results[0].workflows.length, 1);
  assert.equal(evidence.results[0].workflows[0].workflowId, 'test-workflow');
  assert.equal(evidence.summary.totalMetrics, 1);
});

test('should handle products with no metric mapping', () => {
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 200, p95: 800, p99: 1400 }
          }
        }
      }
    }
  };

  const metricMapping = {
    products: {}
  };

  const violations = checkMetricMappingCoverage(sloBudgets, metricMapping);
  assert.ok(violations.length > 0);
  assert.ok(violations.some(v => v.includes('no metric mapping defined')));
});

test('should pass when all budget items have mappings', () => {
  const sloBudgets = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: { p50: 200, p95: 800, p99: 1400 },
            throughputRps: { min: 40 },
            memoryMb: { max: 2048 }
          }
        }
      }
    }
  };

  const metricMapping = {
    products: {
      'test-product': {
        workflows: {
          'test-workflow': {
            latencyMs: {
              p50: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.5, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              },
              p95: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              },
              p99: {
                metricName: 'http_request_duration_seconds_bucket',
                labels: { workflow: 'test-workflow' },
                query: 'histogram_quantile(0.99, rate(http_request_duration_seconds_bucket{workflow="test-workflow"}[5m])) * 1000'
              }
            },
            throughputRps: {
              min: {
                metricName: 'http_requests_total',
                labels: { workflow: 'test-workflow' },
                query: 'rate(http_requests_total{workflow="test-workflow"}[5m])'
              }
            },
            memoryMb: {
              max: {
                metricName: 'process_resident_memory_bytes',
                labels: { product: 'test-product' },
                query: 'process_resident_memory_bytes{product="test-product"} / 1024 / 1024'
              }
            }
          }
        }
      }
    }
  };

  const violations = checkMetricMappingCoverage(sloBudgets, metricMapping);
  assert.equal(violations.length, 0);
});
