/**
 * Performance and Load Testing for Phase 1
 * Uses k6 for load testing
 * 
 * Run: k6 run load_test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '2m', target: 100 },   // Stay at 100 users
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'], // 95% of requests under 3s
    http_req_failed: ['rate<0.1'],     // Error rate under 10%
    errors: ['rate<0.1'],
  },
};

const TRACING_SERVICE = 'http://localhost:8080';
const LOGS_SERVICE = 'http://localhost:8081';

export default function () {
  // Test 1: Query traces
  {
    const now = new Date();
    const oneHourAgo = new Date(now - 3600000);
    
    const params = {
      start: oneHourAgo.toISOString(),
      end: now.toISOString(),
    };
    
    const res = http.get(`${TRACING_SERVICE}/api/v1/traces`, { params });
    
    const success = check(res, {
      'traces query status is 200': (r) => r.status === 200,
      'traces query response time < 3s': (r) => r.timings.duration < 3000,
    });
    
    errorRate.add(!success);
  }
  
  sleep(1);
  
  // Test 2: Query logs
  {
    const now = new Date();
    const oneHourAgo = new Date(now - 3600000);
    
    const params = {
      start: oneHourAgo.toISOString(),
      end: now.toISOString(),
      limit: 100,
    };
    
    const res = http.get(`${LOGS_SERVICE}/api/v1/logs`, { params });
    
    const success = check(res, {
      'logs query status is 200': (r) => r.status === 200,
      'logs query response time < 3s': (r) => r.timings.duration < 3000,
    });
    
    errorRate.add(!success);
  }
  
  sleep(1);
  
  // Test 3: Search logs
  {
    const res = http.get(`${LOGS_SERVICE}/api/v1/logs/search?q=error&limit=10`);
    
    const success = check(res, {
      'log search status is 200': (r) => r.status === 200,
      'log search response time < 2s': (r) => r.timings.duration < 2000,
    });
    
    errorRate.add(!success);
  }
  
  sleep(1);
  
  // Test 4: Aggregate logs
  {
    const now = new Date();
    const oneHourAgo = new Date(now - 3600000);
    
    const params = {
      field: 'level',
      start: oneHourAgo.toISOString(),
      end: now.toISOString(),
    };
    
    const res = http.get(`${LOGS_SERVICE}/api/v1/logs/aggregate`, { params });
    
    const success = check(res, {
      'log aggregation status is 200': (r) => r.status === 200,
      'log aggregation response time < 2s': (r) => r.timings.duration < 2000,
    });
    
    errorRate.add(!success);
  }
  
  sleep(2);
}

export function handleSummary(data) {
  return {
    'performance-results.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const enableColors = options.enableColors || false;
  
  let summary = '\n' + indent + '='.repeat(60) + '\n';
  summary += indent + 'Performance Test Results\n';
  summary += indent + '='.repeat(60) + '\n\n';
  
  // Request metrics
  summary += indent + 'HTTP Requests:\n';
  summary += indent + `  Total: ${data.metrics.http_reqs.values.count}\n`;
  summary += indent + `  Failed: ${data.metrics.http_req_failed.values.rate * 100}%\n`;
  summary += indent + `  Duration (avg): ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
  summary += indent + `  Duration (p95): ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
  summary += indent + `  Duration (p99): ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n\n`;
  
  // Throughput
  summary += indent + 'Throughput:\n';
  summary += indent + `  Requests/sec: ${data.metrics.http_reqs.values.rate.toFixed(2)}\n\n`;
  
  // Thresholds
  summary += indent + 'Thresholds:\n';
  for (const [name, threshold] of Object.entries(data.thresholds || {})) {
    const passed = threshold.ok ? '✅' : '❌';
    summary += indent + `  ${passed} ${name}\n`;
  }
  
  summary += '\n' + indent + '='.repeat(60) + '\n';
  
  return summary;
}
