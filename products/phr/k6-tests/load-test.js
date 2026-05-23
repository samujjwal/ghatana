/**
 * P2-018: Load testing for critical PHR API endpoints.
 * 
 * Tests performance under sustained load for critical endpoints:
 * - Patient record operations
 * - Consent management
 * - Document retrieval
 * - Appointment scheduling
 * - Lab results
 * 
 * @doc.type test
 * @doc.purpose Load testing for critical PHR API endpoints (P2-018)
 * @doc.layer test
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');

// Test configuration
export const options = {
  stages: [
    { duration: '2m', target: 10 },   // Ramp up to 10 users
    { duration: '5m', target: 10 },   // Stay at 10 users
    { duration: '2m', target: 50 },   // Ramp up to 50 users
    { duration: '5m', target: 50 },   // Stay at 50 users
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 0 },    // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95% of requests < 500ms, 99% < 1s
    http_req_failed: ['rate<0.05'],                  // Error rate < 5%
    errors: ['rate<0.05'],
  },
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
    'X-Session-ID': `session-${Date.now()}`,
    'X-Correlation-ID': `corr-${Date.now()}-${__VU}`,
    'Authorization': `Bearer test-token-${__VU}`,
  };
}

// Test: Patient record listing
export function testPatientRecordList() {
  const headers = getHeaders();
  const res = http.get(`${BASE_URL}/api/v1/patients`, { headers });
  
  const success = check(res, {
    'patient record list status is 200': (r) => r.status === 200,
    'patient record list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Consent listing
export function testConsentList() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/consents`, { headers });
  
  const success = check(res, {
    'consent list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'consent list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Document retrieval
export function testDocumentList() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/documents`, { headers });
  
  const success = check(res, {
    'document list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'document list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Appointment listing
export function testAppointmentList() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/appointments`, { headers });
  
  const success = check(res, {
    'appointment list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'appointment list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Lab results retrieval
export function testLabResultsList() {
  const headers = getHeaders();
  const patientId = 'test-patient';
  
  const res = http.get(`${BASE_URL}/api/v1/patients/${patientId}/lab-results`, { headers });
  
  const success = check(res, {
    'lab results list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'lab results list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Main scenario
export default function() {
  // Run tests in sequence with small delays
  testPatientRecordList();
  sleep(0.1);
  
  testConsentList();
  sleep(0.1);
  
  testDocumentList();
  sleep(0.1);
  
  testAppointmentList();
  sleep(0.1);
  
  testLabResultsList();
  sleep(0.1);
}
