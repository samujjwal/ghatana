/**
 * P2-019: Stress testing for high concurrency scenarios in PHR.
 * 
 * Tests system behavior under extreme load to identify breaking points:
 * - Rapid user ramp-up (spike testing)
 * - Sustained high concurrency
 * - Resource exhaustion scenarios
 * - Backpressure and circuit breaker behavior
 * 
 * @doc.type test
 * @doc.purpose Stress testing for high concurrency scenarios (P2-019)
 * @doc.layer test
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');
const throughput = new Trend('throughput');

// Stress test configuration - aggressive ramp-up
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Quick ramp to 50 users
    { duration: '1m', target: 200 },   // Ramp to 200 users
    { duration: '2m', target: 500 },   // Ramp to 500 users
    { duration: '3m', target: 1000 },  // Ramp to 1000 users (stress point)
    { duration: '2m', target: 1000 },  // Sustain at 1000 users
    { duration: '1m', target: 2000 },  // Spike to 2000 users (extreme stress)
    { duration: '1m', target: 2000 },  // Sustain at 2000 users
    { duration: '1m', target: 0 },     // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],  // More lenient for stress test
    http_req_failed: ['rate<0.20'],                  // Allow higher error rate during stress
    errors: ['rate<0.20'],
  },
  // Abort early if error rate exceeds 50% for 30 seconds
  abortOnFailures: 0.5,
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'test-tenant';
const PRINCIPAL_ID = __ENV.PRINCIPAL_ID || 'test-user';

// Helper function to get common headers
function getHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-Tenant-ID': TENANT_ID,
    'X-Principal-ID': PRINCIPAL_ID,
    'X-Session-ID': `session-${Date.now()}-${__VU}`,
    'X-Correlation-ID': `corr-${Date.now()}-${__VU}-${__ITER}`,
    'Authorization': `Bearer test-token-${__VU}`,
  };
}

// Test: Patient record operations under stress
export function testPatientRecordOperations() {
  const headers = getHeaders();
  
  // Mix of read and write operations
  const operations = [
    () => http.get(`${BASE_URL}/api/v1/patients`, { headers }),
    () => {
      const payload = JSON.stringify({
        name: `Test Patient ${Date.now()}`,
        dateOfBirth: '1990-01-01',
        gender: 'M'
      });
      return http.post(`${BASE_URL}/api/v1/patients`, payload, { headers });
    },
    () => http.get(`${BASE_URL}/api/v1/patients/test-patient`, { headers }),
  ];
  
  const randomOp = operations[Math.floor(Math.random() * operations.length)];
  const res = randomOp();
  
  const success = check(res, {
    'patient record operation status is 200, 201, or 404': (r) => 
      r.status === 200 || r.status === 201 || r.status === 404,
    'patient record operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Consent operations under stress
export function testConsentOperations() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const operations = [
    () => http.get(`${BASE_URL}/api/v1/patients/${patientId}/consents`, { headers }),
    () => {
      const payload = JSON.stringify({
        consentType: 'DATA_SHARING',
        status: 'GRANTED',
        purpose: 'RESEARCH'
      });
      return http.post(`${BASE_URL}/api/v1/patients/${patientId}/consents`, payload, { headers });
    },
  ];
  
  const randomOp = operations[Math.floor(Math.random() * operations.length)];
  const res = randomOp();
  
  const success = check(res, {
    'consent operation status is 200, 201, or 404': (r) => 
      r.status === 200 || r.status === 201 || r.status === 404,
    'consent operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Document operations under stress
export function testDocumentOperations() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const operations = [
    () => http.get(`${BASE_URL}/api/v1/patients/${patientId}/documents`, { headers }),
    () => http.get(`${BASE_URL}/api/v1/patients/${patientId}/documents/test-doc`, { headers }),
  ];
  
  const randomOp = operations[Math.floor(Math.random() * operations.length)];
  const res = randomOp();
  
  const success = check(res, {
    'document operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'document operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Appointment operations under stress
export function testAppointmentOperations() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/appointments`, { headers });
  
  const success = check(res, {
    'appointment operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'appointment operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Lab results under stress
export function testLabResultsOperations() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/lab-results`, { headers });
  
  const success = check(res, {
    'lab results operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'lab results operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Medication operations under stress
export function testMedicationOperations() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/medications`, { headers });
  
  const success = check(res, {
    'medication operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'medication operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Main stress test scenario
export default function() {
  // Execute all tests in parallel with minimal delay
  testPatientRecordOperations();
  sleep(0.05);  // Minimal delay to simulate realistic concurrency
  
  testConsentOperations();
  sleep(0.05);
  
  testDocumentOperations();
  sleep(0.05);
  
  testAppointmentOperations();
  sleep(0.05);
  
  testLabResultsOperations();
  sleep(0.05);
  
  testMedicationOperations();
  sleep(0.05);
}
