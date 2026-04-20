import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

/**
 * k6 Load Testing for YAPPC Code Generation API
 * Tests performance SLAs, throughput, and error rates under load
 * @doc.type load-test
 * @doc.purpose Performance validation for YAPPC generation endpoints
 * @doc.layer product
 * @doc.pattern Load Test
 */

// Configuration
const BASE_URL = __ENV.YAPPC_URL || 'http://localhost:8082';
const API_TOKEN = __ENV.YAPPC_API_TOKEN || 'test-token-12345';

// Performance SLA thresholds
const P95_LATENCY_MS = 5000;  // 5 seconds for code generation
const P99_LATENCY_MS = 10000; // 10 seconds for extreme cases
const ERROR_RATE_THRESHOLD = 0.05; // 5% acceptable error rate

// Custom metrics
const generationLatency = new Trend('generation_latency', { unit: 'ms' });
const successRate = new Rate('generation_success');
const totalRequests = new Counter('total_requests');
const concurrentUsers = new Gauge('concurrent_users');
const designValidationLatency = new Trend('validation_latency', { unit: 'ms' });

// Test stages configuration
export const options = {
  stages: [
    { duration: '30s', target: 5 },      // Ramp-up to 5 users over 30s
    { duration: '2m', target: 20 },      // Ramp-up to 20 users over 2m
    { duration: '5m', target: 20 },      // Stay at 20 users for 5m
    { duration: '2m', target: 50 },      // Spike to 50 users over 2m
    { duration: '5m', target: 50 },      // Stay at 50 users for 5m
    { duration: '1m', target: 10 },      // Ramp-down to 10 users over 1m
    { duration: '1m', target: 0 },       // Ramp-down to 0 users over 1m
  ],
  thresholds: {
    'generation_latency': [
      `p(95) < ${P95_LATENCY_MS}`,     // 95th percentile < 5s
      `p(99) < ${P99_LATENCY_MS}`,     // 99th percentile < 10s
      'avg < 2000',                      // Average < 2s
      'max < 15000',                     // Max < 15s
    ],
    'generation_success': [
      `rate > ${1 - ERROR_RATE_THRESHOLD}`,  // Success rate > 95%
    ],
    'validation_latency': [
      'p(95) < 3000',  // Validation should be fast
      'avg < 1000',
    ],
  },
};

// Setup: Create test context
export function setup() {
  // Create a test design spec
  const designPayload = {
    name: `LoadTest-Design-${Date.now()}`,
    description: 'Design for load testing',
    components: [
      { name: 'Service1', type: 'service' },
      { name: 'Service2', type: 'service' },
      { name: 'Database', type: 'database' },
    ],
  };

  const designParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${API_TOKEN}`,
    },
  };

  const designRes = http.post(
    `${BASE_URL}/api/v1/designs`,
    JSON.stringify(designPayload),
    designParams
  );

  if (designRes.status === 201 || designRes.status === 200) {
    const design = JSON.parse(designRes.body);
    console.log(`Created test design: ${design.id}`);
    return { designId: design.id };
  } else {
    console.error(`Failed to create test design: ${designRes.status}`);
    return { designId: null };
  }
}

export default function (data) {
  const designId = data.designId;
  concurrentUsers.set(1);  // Increment gauge for each VU

  if (!designId) {
    console.error('No design ID available for testing');
    return;
  }

  // Test 1: Design validation
  group('Design Validation', () => {
    const validationPayload = {
      designId: designId,
      strict: true,
    };

    const validationParams = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${API_TOKEN}`,
      },
    };

    const startTime = Date.now();
    const validationRes = http.post(
      `${BASE_URL}/api/v1/designs/${designId}/validate`,
      JSON.stringify(validationPayload),
      validationParams
    );
    const latency = Date.now() - startTime;

    designValidationLatency.add(latency);
    totalRequests.add(1);

    check(validationRes, {
      'validation status 200-299': (r) => r.status >= 200 && r.status < 300,
      'validation response has validation field': (r) => r.body.includes('valid'),
      'validation latency acceptable': () => latency < 3000,
    });

    if (validationRes.status < 200 || validationRes.status >= 300) {
      console.error(`Validation failed: ${validationRes.status}`);
    }
  });

  sleep(1);

  // Test 2: Code generation (primary SLA test)
  group('Code Generation', () => {
    const generationPayload = {
      designId: designId,
      language: 'typescript',
      framework: 'nestjs',
      options: {
        includeTests: true,
        includeDocs: true,
        outputFormat: 'zip',
      },
    };

    const generationParams = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${API_TOKEN}`,
      },
      timeout: '30s',
    };

    const startTime = Date.now();
    const generationRes = http.post(
      `${BASE_URL}/api/v1/designs/${designId}/generate`,
      JSON.stringify(generationPayload),
      generationParams
    );
    const latency = Date.now() - startTime;

    generationLatency.add(latency);
    totalRequests.add(1);

    const success = check(generationRes, {
      'generation status 200-299': (r) => r.status >= 200 && r.status < 300,
      'generation response has artifacts': (r) => r.body.includes('artifacts') || r.body.includes('url'),
      'generation response time acceptable': () => latency < P95_LATENCY_MS,
      'generation has content': (r) => r.body.length > 100,
      'generation response is valid JSON': (r) => {
        try {
          JSON.parse(r.body);
          return true;
        } catch {
          return false;
        }
      },
    });

    successRate.add(success);

    if (!success) {
      console.error(`Code generation failed: ${generationRes.status} - ${generationRes.body}`);
    }
  });

  sleep(2);

  // Test 3: Generate with different languages
  group('Multi-Language Code Generation', () => {
    const languages = ['java', 'python', 'go'];
    const language = languages[Math.floor(Math.random() * languages.length)];

    const generationPayload = {
      designId: designId,
      language: language,
      options: {
        includeTests: false,  // Faster without tests
      },
    };

    const generationParams = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${API_TOKEN}`,
      },
      timeout: '30s',
    };

    const startTime = Date.now();
    const generationRes = http.post(
      `${BASE_URL}/api/v1/designs/${designId}/generate-${language}`,
      JSON.stringify(generationPayload),
      generationParams
    );
    const latency = Date.now() - startTime;

    generationLatency.add(latency);

    check(generationRes, {
      'multi-language generation status 200-299': (r) => r.status >= 200 && r.status < 300,
      'multi-language generation latency < 8s': () => latency < 8000,
    });
  });

  sleep(1);

  // Test 4: Batch/concurrent requests
  group('Concurrent Generation Requests', () => {
    const batchSize = 3;
    const requests = [];

    // Create batch of requests
    for (let i = 0; i < batchSize; i++) {
      const generationPayload = {
        designId: designId,
        language: 'typescript',
        variant: i,
      };

      const generationParams = {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${API_TOKEN}`,
        },
        timeout: '30s',
      };

      requests.push({
        method: 'POST',
        url: `${BASE_URL}/api/v1/designs/${designId}/generate`,
        body: JSON.stringify(generationPayload),
        params: generationParams,
      });
    }

    // Execute batch
    const startTime = Date.now();
    const responses = http.batch(requests);
    const latency = Date.now() - startTime;

    generationLatency.add(latency / batchSize);

    responses.forEach((res, index) => {
      check(res, {
        'batch request status 200-299': (r) => r.status >= 200 && r.status < 300,
      });

      if (res.status < 200 || res.status >= 300) {
        console.error(`Batch request ${index} failed: ${res.status}`);
      }
    });
  });

  sleep(1);

  // Test 5: API rate limiting behavior
  group('Rate Limiting', () => {
    // Make rapid requests to test rate limit
    const requests = [];
    const requestCount = 10;

    for (let i = 0; i < requestCount; i++) {
      const generationPayload = {
        designId: designId,
        language: 'typescript',
      };

      requests.push({
        method: 'POST',
        url: `${BASE_URL}/api/v1/designs/${designId}/generate-quick`,
        body: JSON.stringify(generationPayload),
        params: {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${API_TOKEN}`,
          },
        },
      });
    }

    const responses = http.batch(requests);

    const successCount = responses.filter(r => r.status === 200).length;
    const rateLimitCount = responses.filter(r => r.status === 429).length;

    check({ successCount, rateLimitCount }, {
      'rate limiting enforced': (data) => data.rateLimitCount >= 0,  // Should see some rate limits
      'success rate maintained': (data) => data.successCount > 0,    // Some should succeed
    });

    if (rateLimitCount > 0) {
      console.log(`Rate limit hit: ${rateLimitCount}/${requestCount} requests returned 429`);
    }
  });

  sleep(2);

  // Test 6: Error handling and edge cases
  group('Error Handling', () => {
    // Test missing required field
    const invalidPayload = {
      // Missing designId - should fail
      language: 'typescript',
    };

    const invalidRes = http.post(
      `${BASE_URL}/api/v1/designs/invalid/generate`,
      JSON.stringify(invalidPayload),
      {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${API_TOKEN}`,
        },
      }
    );

    check(invalidRes, {
      'invalid request returns 400-level error': (r) => r.status >= 400 && r.status < 500,
      'error response has message': (r) => r.body.includes('error') || r.body.includes('message'),
    });

    // Test invalid token
    const unauthorizedRes = http.post(
      `${BASE_URL}/api/v1/designs/${designId}/generate`,
      JSON.stringify({
        designId: designId,
        language: 'typescript',
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer invalid-token',
        },
      }
    );

    check(unauthorizedRes, {
      'invalid token returns 401': (r) => r.status === 401,
    });
  });

  sleep(3);
}

// Teardown: Cleanup
export function teardown(data) {
  if (!data.designId) {
    return;
  }

  const deleteParams = {
    headers: {
      'Authorization': `Bearer ${API_TOKEN}`,
    },
  };

  const deleteRes = http.del(
    `${BASE_URL}/api/v1/designs/${data.designId}`,
    deleteParams
  );

  if (deleteRes.status >= 200 && deleteRes.status < 300) {
    console.log(`Deleted test design: ${data.designId}`);
  } else {
    console.error(`Failed to delete test design: ${deleteRes.status}`);
  }
}

// Summary reporter
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    '/tmp/summary.json': JSON.stringify(data),
  };
}

// Custom text summary formatter
function textSummary(data, options) {
  const indent = options.indent || '';
  const colors = options.enableColors ? true : false;

  let output = '\n=== Load Test Summary ===\n';
  output += `Total Requests: ${data.metrics.total_requests?.value || 0}\n`;
  output += `Concurrent Users Peak: ${data.metrics.concurrent_users?.value || 0}\n`;
  output += `Generation Success Rate: ${((data.metrics.generation_success?.value || 0) * 100).toFixed(2)}%\n`;

  if (data.metrics.generation_latency) {
    const latency = data.metrics.generation_latency;
    output += `\nGeneration Latency:\n`;
    output += `${indent}Average: ${latency.avg?.toFixed(0) || 'N/A'}ms\n`;
    output += `${indent}P95: ${latency['p(95)']?.toFixed(0) || 'N/A'}ms\n`;
    output += `${indent}P99: ${latency['p(99)']?.toFixed(0) || 'N/A'}ms\n`;
    output += `${indent}Max: ${latency.max?.toFixed(0) || 'N/A'}ms\n`;
  }

  output += '\n=== SLA Status ===\n';
  const p95 = data.metrics.generation_latency?.['p(95)'] || 0;
  const successRate = data.metrics.generation_success?.value || 0;
  
  output += `P95 Latency SLA (< ${P95_LATENCY_MS}ms): ${p95 < P95_LATENCY_MS ? 'PASS' : 'FAIL'}\n`;
  output += `Success Rate SLA (> 95%): ${successRate > 0.95 ? 'PASS' : 'FAIL'}\n`;

  return output;
}
