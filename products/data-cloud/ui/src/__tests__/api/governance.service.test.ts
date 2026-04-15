import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
  },
}));

vi.mock('@/lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import { governanceService } from '@/api/governance.service';

describe('governanceService contract mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockApiClient.get.mockImplementation((path: string) => {
      if (path === '/governance/privacy/pii-fields') {
        return Promise.resolve({
          data: {
            globalFields: ['email'],
            tenantFields: ['ssn'],
            effectiveCount: 2,
          },
          meta: {
            tenantId: 'tenant-a',
            requestId: 'req-pii',
          },
        });
      }

      if (path === '/governance/compliance/summary') {
        return Promise.resolve({
          data: {
            tenantId: 'tenant-a',
            collectionsTotal: 12,
            collectionsClassified: 9,
            collectionsUnclassified: 3,
            piiFieldsRegistered: 2,
            legalHoldsActive: 1,
            retentionExpirationsIn30Days: 2,
            lastAuditAt: '2026-04-15T08:00:00Z',
            auditEventsIn30Days: 18,
            authFailuresIn30Days: 1,
            redactionsIn30Days: 4,
            purgesIn30Days: 1,
            recentAuditEvents: [
              {
                id: 'evt-1',
                timestamp: '2026-04-15T08:00:00Z',
                userId: 'auditor-1',
                userName: 'Auditor',
                action: 'PII_SCAN',
                resourceType: 'governance',
                resourceId: 'tenant-a',
                outcome: 'SUCCESS',
              },
            ],
            complianceStatus: 'REVIEW_REQUIRED',
            generatedAt: '2026-04-15T08:05:00Z',
          },
          meta: {
            tenantId: 'tenant-a',
            requestId: 'req-summary',
          },
        });
      }

      return Promise.reject(new Error(`Unexpected path: ${path}`));
    });
  });

  it('derives governance policies from canonical summary and pii registry envelopes', async () => {
    const policies = await governanceService.getPolicies();

    expect(mockApiClient.get).toHaveBeenCalledWith('/governance/compliance/summary');
    expect(mockApiClient.get).toHaveBeenCalledWith('/governance/privacy/pii-fields');
    expect(policies.map((policy) => policy.id)).toEqual([
      'privacy-pii-registry',
      'retention-classification',
      'security-audit-posture',
      'access-review',
    ]);
    expect(policies[0]?.metadata).toMatchObject({
      registeredFields: 2,
      piiFields: ['email', 'ssn'],
    });
  });

  it('derives violations and audit logs from the compliance summary envelope', async () => {
    const violations = await governanceService.getViolations(undefined, 10);
    const logs = await governanceService.getAuditLogs('governance');

    expect(violations.map((violation) => violation.id)).toEqual([
      'retention-unclassified',
      'security-auth-failures',
      'retention-expiring',
    ]);
    expect(logs).toEqual([
      {
        id: 'evt-1',
        timestamp: '2026-04-15T08:00:00Z',
        userId: 'auditor-1',
        userName: 'Auditor',
        action: 'PII_SCAN',
        resourceType: 'governance',
        resourceId: 'tenant-a',
        outcome: 'SUCCESS',
        details: {
          id: 'evt-1',
          timestamp: '2026-04-15T08:00:00Z',
          userId: 'auditor-1',
          userName: 'Auditor',
          action: 'PII_SCAN',
          resourceType: 'governance',
          resourceId: 'tenant-a',
          outcome: 'SUCCESS',
        },
      },
    ]);
  });

  it('fails explicitly for unsupported governance mutations', async () => {
    await expect(governanceService.createPolicy({ name: 'No export without review' })).rejects.toThrow(/Policy creation is not exposed/i);
    await expect(governanceService.updatePolicy('policy-1', { enabled: false })).rejects.toThrow(/Policy updates are not exposed/i);
    await expect(governanceService.deletePolicy('policy-1')).rejects.toThrow(/Policy deletion is not exposed/i);
    await expect(governanceService.togglePolicy('policy-1', false)).rejects.toThrow(/Policy toggles are not exposed/i);
    await expect(governanceService.resolveViolation('violation-1', 'accepted risk')).rejects.toThrow(/Violation resolution is not exposed/i);
  });
});