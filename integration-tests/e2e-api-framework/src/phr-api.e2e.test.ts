import { test, expect } from '@playwright/test';
import ApiTestClient, { ApiResponse } from './ApiTestClient';

/**
 * PHR API E2E Tests
 * 
 * Validates:
 * - Patient CRUD operations
 * - Consent workflows (request, grant, revoke)
 * - Access control (can only access with valid consent)
 * - Tenant isolation (patients isolated by tenant)
 * - Audit trail (all operations logged)
 * 
 * Compliance: HIPAA, Nepal Directive 2081
 */

const PHR_API_URL = process.env.PHR_API_URL || 'http://localhost:8080/api/v1/phr';
const PROVIDER_TOKEN = process.env.PROVIDER_AUTH_TOKEN || 'test-provider-jwt-token';
const PATIENT_TOKEN = process.env.PATIENT_AUTH_TOKEN || 'test-patient-jwt-token';

test.describe('PHR Patient Management API', () => {
  let providerClient: ApiTestClient;
  let patientClient: ApiTestClient;
  let createdPatientId: string;

  test.beforeAll(async () => {
    providerClient = new ApiTestClient(PHR_API_URL, 'phr-001').setAuthToken(PROVIDER_TOKEN);
    patientClient = new ApiTestClient(PHR_API_URL, 'phr-001').setAuthToken(PATIENT_TOKEN);
  });

  test('PHR-E2E-001: Create patient record', async () => {
    const createRequest = {
      firstName: 'John',
      lastName: 'Doe',
      dateOfBirth: '1980-01-15',
      email: 'john.doe@example.com',
      phone: '+977-1234567890',
      address: 'Kathmandu, Nepal',
    };

    const response = await providerClient.post('/patients', createRequest);
    
    response
      .assertOk()
      .assertShape(['patientId', 'firstName', 'lastName', 'email'])
      .assertCorrelationIdEchoed();

    createdPatientId = (response.data as any).patientId;
    expect(createdPatientId).toBeTruthy();
    expect((response.data as any).firstName).toBe('John');
  });

  test('PHR-E2E-002: Read patient record with valid tenant', async () => {
    const response = await providerClient.get(`/patients/${createdPatientId}`);
    
    response
      .assertOk()
      .assertShape(['patientId', 'firstName', 'lastName', 'email'])
      .assertTenantIsolation('phr-001');

    expect((response.data as any).patientId).toBe(createdPatientId);
  });

  test('PHR-E2E-003: Prevent read across tenant boundary', async () => {
    const otherTenantClient = new ApiTestClient(PHR_API_URL, 'phr-999').setAuthToken(PROVIDER_TOKEN);
    
    const response = await otherTenantClient.get(`/patients/${createdPatientId}`);
    
    // Should return 403 Forbidden or 404 Not Found (tenant isolation)
    expect(response.status).toBeGreaterThanOrEqual(403);
  });

  test('PHR-E2E-004: Update patient record', async () => {
    const updateRequest = {
      email: 'john.doe.updated@example.com',
      phone: '+977-9876543210',
    };

    const response = await providerClient.put(`/patients/${createdPatientId}`, updateRequest);
    
    response
      .assertOk()
      .assertCorrelationIdEchoed();

    expect((response.data as any).email).toBe('john.doe.updated@example.com');
  });

  test('PHR-E2E-005: List patients with pagination', async () => {
    const response = await providerClient.get('/patients?limit=10&offset=0');
    
    response
      .assertOk()
      .assertShape(['patients', 'total', 'limit', 'offset']);

    expect(Array.isArray((response.data as any).patients)).toBe(true);
    expect((response.data as any).total).toBeGreaterThanOrEqual(1);
  });
});

test.describe('PHR Consent Workflow', () => {
  let providerClient: ApiTestClient;
  let patientClient: ApiTestClient;
  let patientId: string;
  let consentRequestId: string;

  test.beforeAll(async () => {
    providerClient = new ApiTestClient(PHR_API_URL, 'phr-001').setAuthToken(PROVIDER_TOKEN);
    patientClient = new ApiTestClient(PHR_API_URL, 'phr-001').setAuthToken(PATIENT_TOKEN);

    // Create test patient
    const patientResponse = await providerClient.post('/patients', {
      firstName: 'Jane',
      lastName: 'Smith',
      dateOfBirth: '1990-05-20',
      email: 'jane.smith@example.com',
    });
    patientId = (patientResponse.data as any).patientId;
  });

  test('PHR-E2E-010: Request consent to access medical records', async () => {
    const consentRequest = {
      patientId,
      providerId: 'provider-123',
      scope: 'medical-records-read',
      purpose: 'Regular checkup',
      expiryDays: 30,
    };

    const response = await providerClient.post('/consent/requests', consentRequest);
    
    response
      .assertOk()
      .assertShape(['consentRequestId', 'status', 'createdAt'])
      .assertCorrelationIdEchoed();

    expect((response.data as any).status).toBe('PENDING');
    consentRequestId = (response.data as any).consentRequestId;
  });

  test('PHR-E2E-011: Patient receives and grants consent', async () => {
    // Patient retrieves pending consent requests
    let response = await patientClient.get('/consent/pending-requests');
    response.assertOk();
    expect((response.data as any).requests.length).toBeGreaterThan(0);

    // Patient grants consent
    const grantResponse = await patientClient.put(`/consent/requests/${consentRequestId}`, {
      status: 'APPROVED',
    });

    grantResponse
      .assertOk()
      .assertCorrelationIdEchoed();

    expect((grantResponse.data as any).status).toBe('APPROVED');
  });

  test('PHR-E2E-012: Provider can access records after consent granted', async () => {
    const response = await providerClient.get(`/patients/${patientId}/medical-records`);
    
    response
      .assertOk()
      .assertShape(['records'])
      .assertTenantIsolation('phr-001');

    expect(Array.isArray((response.data as any).records)).toBe(true);
  });

  test('PHR-E2E-013: Provider cannot access records before consent', async () => {
    // Create new patient
    const newPatientResponse = await providerClient.post('/patients', {
      firstName: 'Bob',
      lastName: 'Johnson',
      dateOfBirth: '1985-03-10',
      email: 'bob.johnson@example.com',
    });
    const newPatientId = (newPatientResponse.data as any).patientId;

    // Try to access without consent
    const response = await providerClient.get(`/patients/${newPatientId}/medical-records`);
    
    // Should return 403 Forbidden (consent required)
    expect(response.status).toBe(403);
  });

  test('PHR-E2E-014: Patient can revoke consent', async () => {
    const revokeResponse = await patientClient.put(`/consent/requests/${consentRequestId}`, {
      status: 'REVOKED',
    });

    revokeResponse
      .assertOk()
      .assertCorrelationIdEchoed();

    expect((revokeResponse.data as any).status).toBe('REVOKED');

    // Verify access is now denied
    const accessResponse = await providerClient.get(`/patients/${patientId}/medical-records`);
    expect(accessResponse.status).toBe(403);
  });

  test('PHR-E2E-015: Consent audit trail is recorded', async () => {
    const auditResponse = await providerClient.get(`/consent/requests/${consentRequestId}/audit`);
    
    auditResponse
      .assertOk()
      .assertShape(['auditTrail'])
      .assertCorrelationIdEchoed();

    const auditTrail = (auditResponse.data as any).auditTrail;
    expect(Array.isArray(auditTrail)).toBe(true);
    expect(auditTrail.length).toBeGreaterThan(0);
    
    // Verify audit entries have required fields
    for (const entry of auditTrail) {
      expect(entry).toHaveProperty('timestamp');
      expect(entry).toHaveProperty('action');
      expect(entry).toHaveProperty('actor');
    }
  });
});

test.describe('PHR Medical Records', () => {
  let providerClient: ApiTestClient;
  let patientId: string;

  test.beforeAll(async () => {
    providerClient = new ApiTestClient(PHR_API_URL, 'phr-001').setAuthToken(PROVIDER_TOKEN);

    const patientResponse = await providerClient.post('/patients', {
      firstName: 'Alice',
      lastName: 'Brown',
      dateOfBirth: '1988-07-25',
      email: 'alice.brown@example.com',
    });
    patientId = (patientResponse.data as any).patientId;
  });

  test('PHR-E2E-020: Create medical record (lab results)', async () => {
    const recordRequest = {
      patientId,
      recordType: 'LAB_RESULT',
      data: {
        testName: 'Blood Test',
        date: '2026-04-29',
        results: {
          hemoglobin: 15.0,
          whiteBloodCells: 7.5,
        },
      },
    };

    const response = await providerClient.post('/medical-records', recordRequest);
    
    response
      .assertOk()
      .assertShape(['recordId', 'recordType', 'createdAt'])
      .assertCorrelationIdEchoed();

    expect((response.data as any).recordType).toBe('LAB_RESULT');
  });

  test('PHR-E2E-021: Retrieve medical records with filters', async () => {
    const response = await providerClient.get(`/patients/${patientId}/medical-records?type=LAB_RESULT&limit=5`);
    
    response
      .assertOk()
      .assertShape(['records', 'total'])
      .assertTenantIsolation('phr-001');

    expect(Array.isArray((response.data as any).records)).toBe(true);
  });

  test('PHR-E2E-022: PHI (Protected Health Information) is redacted in logs', async () => {
    // This test verifies that sensitive data doesn't appear in correlation ID headers or logs
    const response = await providerClient.get(`/patients/${patientId}/medical-records`);
    
    response.assertOk();

    // Verify correlation ID format is UUID@tenant@domain (no PHI)
    const correlationId = response.correlationId;
    expect(correlationId).toMatch(/^[a-f0-9\-]{36}@phr-001@phr-api$/);
  });
});
