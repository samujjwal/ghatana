import { test, expect } from '@playwright/test';
import ApiTestClient, { ApiResponse } from './ApiTestClient';

/**
 * Finance API E2E Tests
 * 
 * Validates:
 * - Order placement and lifecycle
 * - Risk evaluation workflows
 * - Fraud detection
 * - Ledger integrity (double-entry)
 * - Audit trail completeness
 * 
 * Compliance: SOX, PCI-DSS
 */

const FINANCE_API_URL = process.env.FINANCE_API_URL || 'http://localhost:8081/api/v1/finance';
const MERCHANT_TOKEN = process.env.MERCHANT_AUTH_TOKEN || 'test-merchant-jwt-token';
const FRAUD_ANALYST_TOKEN = process.env.FRAUD_ANALYST_TOKEN || 'test-analyst-jwt-token';

test.describe('Finance Order Management', () => {
  let merchantClient: ApiTestClient;
  let orderId: string;

  test.beforeAll(async () => {
    merchantClient = new ApiTestClient(FINANCE_API_URL, 'finance-001').setAuthToken(MERCHANT_TOKEN);
  });

  test('FINANCE-E2E-001: Create order', async () => {
    const orderRequest = {
      customerId: 'customer-123',
      items: [
        { sku: 'ITEM-001', quantity: 2, unitPrice: 50.00 },
        { sku: 'ITEM-002', quantity: 1, unitPrice: 100.00 },
      ],
      shippingAddress: {
        street: 'Kantipath',
        city: 'Kathmandu',
        postalCode: '44600',
        country: 'NP',
      },
      paymentMethod: 'CREDIT_CARD',
    };

    const response = await merchantClient.post('/orders', orderRequest);
    
    response
      .assertOk()
      .assertShape(['orderId', 'status', 'totalAmount', 'createdAt'])
      .assertCorrelationIdEchoed();

    expect((response.data as any).status).toBe('CREATED');
    expect((response.data as any).totalAmount).toBe(200.00);
    
    orderId = (response.data as any).orderId;
  });

  test('FINANCE-E2E-002: Retrieve order details', async () => {
    const response = await merchantClient.get(`/orders/${orderId}`);
    
    response
      .assertOk()
      .assertShape(['orderId', 'status', 'totalAmount', 'items'])
      .assertTenantIsolation('finance-001');

    expect((response.data as any).orderId).toBe(orderId);
  });

  test('FINANCE-E2E-003: List orders with filtering', async () => {
    const response = await merchantClient.get('/orders?status=CREATED&limit=10');
    
    response
      .assertOk()
      .assertShape(['orders', 'total', 'limit']);

    expect(Array.isArray((response.data as any).orders)).toBe(true);
  });

  test('FINANCE-E2E-004: Prevent order read across tenant boundary', async () => {
    const otherTenantClient = new ApiTestClient(FINANCE_API_URL, 'finance-999').setAuthToken(MERCHANT_TOKEN);
    
    const response = await otherTenantClient.get(`/orders/${orderId}`);
    
    expect(response.status).toBeGreaterThanOrEqual(403);
  });

  test('FINANCE-E2E-005: Process payment for order', async () => {
    const paymentRequest = {
      orderId,
      amount: 200.00,
      paymentToken: 'tok_visa_4242',
      idempotencyKey: 'idempotency-key-001',
    };

    const response = await merchantClient.post('/payments', paymentRequest);
    
    response
      .assertOk()
      .assertShape(['transactionId', 'status', 'amount'])
      .assertCorrelationIdEchoed();

    expect((response.data as any).status).toMatch(/SUCCESS|PENDING/);

    // Verify order status changed
    const orderResponse = await merchantClient.get(`/orders/${orderId}`);
    orderResponse.assertOk();
    expect((orderResponse.data as any).status).toMatch(/PAID|PROCESSING/);
  });

  test('FINANCE-E2E-006: Prevent duplicate payment (idempotency)', async () => {
    const paymentRequest = {
      orderId,
      amount: 200.00,
      paymentToken: 'tok_visa_4242',
      idempotencyKey: 'idempotency-key-001', // Same key
    };

    const firstResponse = await merchantClient.post('/payments', paymentRequest);
    const firstTransactionId = (firstResponse.data as any).transactionId;

    merchantClient.resetCorrelationId(); // Simulate new request

    const secondResponse = await merchantClient.post('/payments', paymentRequest);
    const secondTransactionId = (secondResponse.data as any).transactionId;

    // Both should return same transaction ID (idempotent)
    expect(firstTransactionId).toBe(secondTransactionId);
  });

  test('FINANCE-E2E-007: Refund order payment', async () => {
    const refundRequest = {
      orderId,
      amount: 50.00, // Partial refund
      reason: 'PARTIAL_RETURN',
    };

    const response = await merchantClient.post('/refunds', refundRequest);
    
    response
      .assertOk()
      .assertShape(['refundId', 'status', 'amount'])
      .assertCorrelationIdEchoed();

    expect((response.data as any).status).toMatch(/APPROVED|PROCESSING/);
  });
});

test.describe('Finance Risk Evaluation', () => {
  let merchantClient: ApiTestClient;
  let analystClient: ApiTestClient;

  test.beforeAll(async () => {
    merchantClient = new ApiTestClient(FINANCE_API_URL, 'finance-001').setAuthToken(MERCHANT_TOKEN);
    analystClient = new ApiTestClient(FINANCE_API_URL, 'finance-001').setAuthToken(FRAUD_ANALYST_TOKEN);
  });

  test('FINANCE-E2E-010: Evaluate risk for high-value order', async () => {
    // Create high-value order
    const orderRequest = {
      customerId: 'customer-456',
      items: [{ sku: 'LUXURY-ITEM', quantity: 1, unitPrice: 75000.00 }], // > 50k threshold
      shippingAddress: { street: 'Test', city: 'Test', postalCode: '00000', country: 'NP' },
      paymentMethod: 'CREDIT_CARD',
    };

    const orderResponse = await merchantClient.post('/orders', orderRequest);
    const orderId = (orderResponse.data as any).orderId;

    // Risk evaluation should be triggered automatically
    const riskResponse = await analystClient.get(`/risk-assessments?orderId=${orderId}`);
    
    riskResponse
      .assertOk()
      .assertShape(['assessments'])
      .assertCorrelationIdEchoed();

    const assessments = (riskResponse.data as any).assessments;
    expect(assessments.length).toBeGreaterThan(0);
    
    const riskLevel = assessments[0].riskLevel;
    expect(riskLevel).toMatch(/LOW|MEDIUM|HIGH/);
  });

  test('FINANCE-E2E-011: Fraud detection triggers on suspicious patterns', async () => {
    // Create multiple orders from same customer in short time (suspicious)
    const customerId = 'customer-777';
    const orderIds: string[] = [];

    for (let i = 0; i < 3; i++) {
      const orderResponse = await merchantClient.post('/orders', {
        customerId,
        items: [{ sku: `ITEM-${i}`, quantity: 1, unitPrice: 100.00 }],
        shippingAddress: { street: 'Test', city: 'Test', postalCode: '00000', country: 'NP' },
        paymentMethod: 'CREDIT_CARD',
      });
      orderIds.push((orderResponse.data as any).orderId);
    }

    // Check fraud signals
    const fraudResponse = await analystClient.get(`/fraud-signals?customerId=${customerId}`);
    
    fraudResponse
      .assertOk()
      .assertShape(['signals'])
      .assertCorrelationIdEchoed();

    const signals = (fraudResponse.data as any).signals;
    // Expect at least one signal (multiple orders in short time)
    expect(signals.length).toBeGreaterThan(0);
  });

  test('FINANCE-E2E-012: Analyst can manually review and override risk', async () => {
    // Create order
    const orderResponse = await merchantClient.post('/orders', {
      customerId: 'customer-999',
      items: [{ sku: 'TEST', quantity: 1, unitPrice: 5000.00 }],
      shippingAddress: { street: 'Test', city: 'Test', postalCode: '00000', country: 'NP' },
      paymentMethod: 'CREDIT_CARD',
    });
    const orderId = (orderResponse.data as any).orderId;

    // Get risk assessment
    const riskResponse = await analystClient.get(`/risk-assessments?orderId=${orderId}`);
    const assessmentId = (riskResponse.data as any).assessments[0].assessmentId;

    // Analyst overrides risk decision
    const overrideResponse = await analystClient.put(
      `/risk-assessments/${assessmentId}`,
      {
        decision: 'APPROVED',
        reason: 'Customer verified via phone call',
        reviewer: 'analyst-001',
      }
    );

    overrideResponse
      .assertOk()
      .assertCorrelationIdEchoed();

    expect((overrideResponse.data as any).decision).toBe('APPROVED');
  });
});

test.describe('Finance Ledger Integrity', () => {
  let merchantClient: ApiTestClient;

  test.beforeAll(async () => {
    merchantClient = new ApiTestClient(FINANCE_API_URL, 'finance-001').setAuthToken(MERCHANT_TOKEN);
  });

  test('FINANCE-E2E-020: Verify double-entry ledger on transaction', async () => {
    const orderRequest = {
      customerId: 'customer-ledger',
      items: [{ sku: 'LEDGER-TEST', quantity: 1, unitPrice: 1000.00 }],
      shippingAddress: { street: 'Test', city: 'Test', postalCode: '00000', country: 'NP' },
      paymentMethod: 'CREDIT_CARD',
    };

    const orderResponse = await merchantClient.post('/orders', orderRequest);
    const orderId = (orderResponse.data as any).orderId;

    // Process payment
    const paymentResponse = await merchantClient.post('/payments', {
      orderId,
      amount: 1000.00,
      paymentToken: 'tok_visa_4242',
      idempotencyKey: 'ledger-test-001',
    });

    // Retrieve ledger entries
    const ledgerResponse = await merchantClient.get(
      `/ledger/entries?orderId=${orderId}`
    );

    ledgerResponse
      .assertOk()
      .assertShape(['entries', 'balanceVerified'])
      .assertCorrelationIdEchoed();

    const entries = (ledgerResponse.data as any).entries;
    const balanceVerified = (ledgerResponse.data as any).balanceVerified;

    // Verify double-entry: debit + credit should balance
    expect(entries.length).toBeGreaterThanOrEqual(2);
    expect(balanceVerified).toBe(true);

    // Verify debit total = credit total
    let debitSum = 0;
    let creditSum = 0;
    for (const entry of entries) {
      if (entry.type === 'DEBIT') debitSum += entry.amount;
      if (entry.type === 'CREDIT') creditSum += entry.amount;
    }
    expect(debitSum).toBe(creditSum);
  });

  test('FINANCE-E2E-021: Ledger entries are immutable', async () => {
    // Get all recent ledger entries
    const ledgerResponse = await merchantClient.get('/ledger/entries?limit=1');
    const entries = (ledgerResponse.data as any).entries;
    const entryId = entries[0].entryId;

    // Try to modify ledger entry (should fail)
    const updateResponse = await merchantClient.put(`/ledger/entries/${entryId}`, {
      amount: 999.00, // Invalid modification
    });

    // Should return 400 or 403 (invalid operation)
    expect(updateResponse.status).toBeGreaterThanOrEqual(400);
  });

  test('FINANCE-E2E-022: Reconciliation report shows net-zero', async () => {
    const reconcileResponse = await merchantClient.get('/ledger/reconciliation');
    
    reconcileResponse
      .assertOk()
      .assertShape(['balanceSheet', 'netTotal', 'reconciled'])
      .assertCorrelationIdEchoed();

    expect((reconcileResponse.data as any).netTotal).toBe(0);
    expect((reconcileResponse.data as any).reconciled).toBe(true);
  });
});

test.describe('Finance Audit Trail', () => {
  let merchantClient: ApiTestClient;

  test.beforeAll(async () => {
    merchantClient = new ApiTestClient(FINANCE_API_URL, 'finance-001').setAuthToken(MERCHANT_TOKEN);
  });

  test('FINANCE-E2E-030: All operations are recorded in audit trail', async () => {
    // Perform operation
    const orderResponse = await merchantClient.post('/orders', {
      customerId: 'customer-audit',
      items: [{ sku: 'AUDIT-TEST', quantity: 1, unitPrice: 500.00 }],
      shippingAddress: { street: 'Test', city: 'Test', postalCode: '00000', country: 'NP' },
      paymentMethod: 'CREDIT_CARD',
    });
    const orderId = (orderResponse.data as any).orderId;
    const correlationId = orderResponse.correlationId;

    // Retrieve audit trail
    const auditResponse = await merchantClient.get(`/audit-trail?correlationId=${correlationId}`);
    
    auditResponse
      .assertOk()
      .assertShape(['entries'])
      .assertCorrelationIdEchoed();

    const entries = (auditResponse.data as any).entries;
    expect(entries.length).toBeGreaterThan(0);

    // Verify correlation ID is present in audit
    const correlationInAudit = entries.some(
      (e: any) => e.correlationId === correlationId
    );
    expect(correlationInAudit).toBe(true);
  });

  test('FINANCE-E2E-031: Audit entries have tamper-evident hash chain', async () => {
    const auditResponse = await merchantClient.get('/audit-trail?limit=10');
    
    auditResponse.assertOk();

    const entries = (auditResponse.data as any).entries;
    
    // Verify hash chain integrity
    for (let i = 1; i < entries.length; i++) {
      const currentEntry = entries[i];
      const previousEntry = entries[i - 1];

      // Current entry's previousHash should match previous entry's hash
      expect(currentEntry.previousHash).toBe(previousEntry.hash);
    }
  });
});

test.describe('Cross-Product PHR-to-Finance', () => {
  let phrClient: ApiTestClient;
  let financeClient: ApiTestClient;

  test.beforeAll(async () => {
    phrClient = new ApiTestClient(
      process.env.PHR_API_URL || 'http://localhost:8080/api/v1/phr',
      'shared-tenant'
    ).setAuthToken(MERCHANT_TOKEN);

    financeClient = new ApiTestClient(
      FINANCE_API_URL,
      'shared-tenant'
    ).setAuthToken(MERCHANT_TOKEN);
  });

  test('FINANCE-E2E-040: PHR patient billing event triggers Finance ledger entry', async () => {
    // This test validates cross-product event flow
    // In production, would use event streaming (Kafka/RabbitMQ)
    
    const patientResponse = await phrClient.post('/patients', {
      firstName: 'CrossProduct',
      lastName: 'Test',
      dateOfBirth: '1990-01-01',
      email: 'cross@example.com',
    });
    const patientId = (patientResponse.data as any).patientId;

    // Simulate billing event
    const billingEventRequest = {
      patientId,
      eventType: 'CONSULTATION_CHARGE',
      amount: 500.00,
      description: 'Virtual consultation',
      timestamp: new Date().toISOString(),
    };

    // Post to Finance system
    const billingResponse = await financeClient.post('/billing-events', billingEventRequest);
    
    billingResponse
      .assertOk()
      .assertCorrelationIdEchoed();

    // Verify ledger entry was created
    const ledgerResponse = await financeClient.get(
      `/ledger/entries?patientId=${patientId}`
    );
    
    ledgerResponse.assertOk();
    expect((ledgerResponse.data as any).entries.length).toBeGreaterThan(0);
  });
});
