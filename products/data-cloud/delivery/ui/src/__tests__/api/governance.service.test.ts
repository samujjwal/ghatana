import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@/lib/api/client", () => ({
  apiClient: mockApiClient,
}));

import { governanceService } from "@/api/governance.service";

describe("governanceService contract mapping", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiClient.post.mockReset();
    mockApiClient.put.mockReset();
    mockApiClient.delete.mockReset();

    mockApiClient.get.mockImplementation((path: string) => {
      if (path === "/governance/privacy/pii-fields") {
        return Promise.resolve({
          data: {
            globalFields: ["email"],
            tenantFields: ["ssn"],
            effectiveCount: 2,
          },
          meta: {
            tenantId: TEST_TENANT_ID,
            requestId: "req-pii",
            timestamp: "2026-04-15T08:01:00Z",
            apiVersion: "v1",
          },
        });
      }

      if (path === "/governance/compliance/summary") {
        return Promise.resolve({
          data: {
            tenantId: TEST_TENANT_ID,
            collectionsTotal: 12,
            collectionsClassified: 9,
            collectionsUnclassified: 3,
            piiFieldsRegistered: 2,
            legalHoldsActive: 1,
            retentionExpirationsIn30Days: 2,
            lastAuditAt: "2026-04-15T08:00:00Z",
            auditEventsIn30Days: 18,
            authFailuresIn30Days: 1,
            redactionsIn30Days: 4,
            purgesIn30Days: 1,
            recentAuditEvents: [
              {
                id: "evt-1",
                timestamp: "2026-04-15T08:00:00Z",
                userId: "auditor-1",
                userName: "Auditor",
                action: "PII_SCAN",
                resourceType: "governance",
                resourceId: TEST_TENANT_ID,
                outcome: "SUCCESS",
              },
            ],
            complianceStatus: "REVIEW_REQUIRED",
            generatedAt: "2026-04-15T08:05:00Z",
          },
          meta: {
            tenantId: TEST_TENANT_ID,
            requestId: "req-summary",
            timestamp: "2026-04-15T08:05:00Z",
            apiVersion: "v1",
          },
        });
      }

      return Promise.reject(new Error(`Unexpected path: ${path}`));
    });
  });

  it("derives governance policies from canonical summary and pii registry envelopes", async () => {
    const policies = await governanceService.getPolicies();

    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/governance/compliance/summary",
    );
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/governance/privacy/pii-fields",
    );
    expect(policies.map((policy) => policy.id)).toEqual([
      "privacy-pii-registry",
      "retention-classification",
      "security-audit-posture",
      "access-review",
    ]);
    expect(policies[0]?.metadata).toMatchObject({
      registeredFields: 2,
      piiFields: ["email", "ssn"],
    });
  });

  it("derives actionable governance recommendations from compliance and pii summary data", async () => {
    const recommendations = await governanceService.getRecommendations();

    expect(recommendations.map((recommendation) => recommendation.id)).toEqual(
      expect.arrayContaining([
        "recommend-access-review",
        "recommend-retention-classification",
        "recommend-pii-redaction-template",
        "recommend-refresh-compliance",
      ]),
    );
    expect(recommendations).toHaveLength(4);
    expect(
      recommendations.find(
        (recommendation) =>
          recommendation.id === "recommend-retention-classification",
      ),
    ).toMatchObject({
      action: "classify-retention",
      policyId: "retention-classification",
      payload: {
        tier: "compliance",
        piiFields: ["email", "ssn"],
      },
    });
  });

  it("derives lifecycle truth surfaces from compliance and pii summary data", async () => {
    const surfaces = await governanceService.getLifecycleSurfaces();

    expect(surfaces.map((surface) => surface.id)).toEqual([
      "retention-operations",
      "privacy-redaction",
      "compliance-refresh",
      "access-review",
      "policy-lifecycle",
    ]);
    expect(
      surfaces.find((surface) => surface.id === "retention-operations"),
    ).toMatchObject({
      status: "live-action",
      action: "classify-retention",
    });
    expect(
      surfaces.find((surface) => surface.id === "access-review"),
    ).toMatchObject({
      status: "derived-read-only",
      action: "access-review",
    });
    expect(
      surfaces.find((surface) => surface.id === "policy-lifecycle")?.evidence,
    ).toEqual(
      expect.arrayContaining([
        "POST /api/v1/governance/policies - create policy",
        "PUT /api/v1/governance/policies/:id - update policy",
        "DELETE /api/v1/governance/policies/:id - delete policy",
        "POST /api/v1/governance/policies/:id/toggle - toggle policy",
      ]),
    );
  });

  it("derives violations and audit logs from the compliance summary envelope", async () => {
    const violations = await governanceService.getViolations(undefined, 10);
    const logs = await governanceService.getAuditLogs("governance");

    expect(violations.map((violation) => violation.id)).toEqual([
      "retention-unclassified",
      "security-auth-failures",
      "retention-expiring",
    ]);
    expect(logs).toEqual([
      {
        id: "evt-1",
        timestamp: "2026-04-15T08:00:00Z",
        userId: "auditor-1",
        userName: "Auditor",
        action: "PII_SCAN",
        resourceType: "governance",
        resourceId: TEST_TENANT_ID,
        outcome: "SUCCESS",
        details: {
          id: "evt-1",
          timestamp: "2026-04-15T08:00:00Z",
          userId: "auditor-1",
          userName: "Auditor",
          action: "PII_SCAN",
          resourceType: "governance",
          resourceId: TEST_TENANT_ID,
          outcome: "SUCCESS",
        },
      },
    ]);
  });

  it("classifies retention through the canonical governance route", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        collection: "customers",
        tier: "compliance",
        retentionDays: 2555,
        expiresAt: "2033-04-15T08:05:00Z",
        classifiedAt: "2026-04-15T08:05:00Z",
        classifiedBy: "tenant-a",
        reason: "GDPR Article 17 review",
        piiFields: ["email", "ssn"],
        status: "CLASSIFIED",
      },
      meta: {
        tenantId: TEST_TENANT_ID,
        requestId: "req-classify",
      },
    });

    const result = await governanceService.classifyRetention({
      collection: "customers",
      tier: "compliance",
      reason: "GDPR Article 17 review",
      piiFields: ["email", "ssn"],
    });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/governance/retention/classify",
      {
        collection: "customers",
        tier: "compliance",
        reason: "GDPR Article 17 review",
        piiFields: ["email", "ssn"],
      },
    );
    expect(result).toMatchObject({
      collection: "customers",
      tier: "compliance",
      status: "CLASSIFIED",
    });
  });

  it("loads retention policy through the canonical governance route", async () => {
    mockApiClient.get.mockImplementation(
      (path: string, options?: { params?: { collection?: string } }) => {
        if (path === "/governance/retention/policy") {
          expect(options).toEqual({ params: { collection: "customers" } });
          return Promise.resolve({
            data: {
              collection: "customers",
              tier: "standard",
              retentionDays: 365,
              legalHolds: [],
              piiFields: ["email"],
              lastClassifiedAt: "1970-01-01T00:00:00Z",
              status: "DEFAULT",
            },
            meta: {
              tenantId: TEST_TENANT_ID,
              requestId: "req-policy",
            },
          });
        }

        return Promise.reject(new Error(`Unexpected path: ${path}`));
      },
    );

    const result = await governanceService.getRetentionPolicy("customers");

    expect(result).toMatchObject({
      collection: "customers",
      tier: "standard",
      retentionDays: 365,
      status: "DEFAULT",
    });
  });

  it("redacts entity fields through the canonical governance route", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        collection: "customers",
        entityId: "ent-123",
        redactedFields: ["email"],
        requestedFields: ["email", "phone"],
        reason: "Customer privacy request",
        status: "REDACTED",
        redactedAt: "2026-04-15T08:05:00Z",
      },
      meta: {
        tenantId: TEST_TENANT_ID,
        requestId: "req-redact",
      },
    });

    const result = await governanceService.redactEntity({
      collection: "customers",
      entityId: "ent-123",
      fields: ["email", "phone"],
      reason: "Customer privacy request",
    });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/governance/privacy/redact",
      {
        collection: "customers",
        entityId: "ent-123",
        fields: ["email", "phone"],
        reason: "Customer privacy request",
      },
    );
    expect(result).toMatchObject({
      collection: "customers",
      entityId: "ent-123",
      status: "REDACTED",
    });
  });

  it("runs retention purge as a dry run and then executes with the returned token", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({
        data: {
          collection: "customers",
          dryRun: true,
          status: "DRY_RUN_COMPLETE",
          confirmationToken: "confirm-123",
          tokenExpiresInSec: 900,
          estimatedRows: 24,
          sampleEntityIds: ["cust-1", "cust-2"],
          requestId: "req-dry-run",
        },
        meta: {
          tenantId: TEST_TENANT_ID,
        },
      })
      .mockResolvedValueOnce({
        data: {
          collection: "customers",
          dryRun: false,
          status: "PURGE_COMPLETED",
          deletedRows: 24,
          requestedRows: 24,
          failedRows: 0,
          deletedEntityIds: ["cust-1", "cust-2"],
          completedAt: "2026-04-15T08:10:00Z",
          requestId: "req-execute",
        },
        meta: {
          tenantId: TEST_TENANT_ID,
        },
      });

    const dryRun = await governanceService.purgeRetentionDryRun({
      collection: "customers",
    });
    const execute = await governanceService.purgeRetentionExecute({
      collection: "customers",
      confirmationToken: dryRun.confirmationToken,
    });

    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      "/governance/retention/purge",
      {
        collection: "customers",
        dryRun: true,
      },
    );
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      "/governance/retention/purge",
      {
        collection: "customers",
        confirmationToken: "confirm-123",
        dryRun: false,
      },
    );
    expect(dryRun).toMatchObject({
      collection: "customers",
      status: "DRY_RUN_COMPLETE",
      confirmationToken: "confirm-123",
    });
    expect(execute).toMatchObject({
      collection: "customers",
      status: "PURGE_COMPLETED",
      deletedRows: 24,
    });
  });

  it("supports policy lifecycle mutations through canonical governance routes", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({
        data: {
          id: "policy-1",
          name: "No export without review",
          type: "PRIVACY",
          enabled: true,
          rules: [],
          createdAt: "2026-04-15T08:05:00Z",
          updatedAt: "2026-04-15T08:05:00Z",
          metadata: {},
        },
      })
      .mockResolvedValueOnce({
        data: {
          id: "policy-1",
          name: "No export without review",
          type: "PRIVACY",
          enabled: true,
          rules: [],
          createdAt: "2026-04-15T08:05:00Z",
          updatedAt: "2026-04-15T08:05:00Z",
          metadata: {},
        },
      });
    mockApiClient.put.mockResolvedValue({
      data: {
        id: "policy-1",
        name: "No export without review",
        type: "PRIVACY",
        enabled: false,
        rules: [],
        createdAt: "2026-04-15T08:05:00Z",
        updatedAt: "2026-04-15T09:00:00Z",
        metadata: {},
      },
    });
    mockApiClient.delete.mockResolvedValue({});

    const created = await governanceService.createPolicy({
      name: "No export without review",
      type: "PRIVACY",
      enabled: true,
    });
    const updated = await governanceService.updatePolicy("policy-1", {
      enabled: false,
    });
    const toggled = await governanceService.togglePolicy("policy-1", true);
    await governanceService.deletePolicy("policy-1");

    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      "/governance/policies",
      {
        name: "No export without review",
        type: "PRIVACY",
        enabled: true,
      },
    );
    expect(mockApiClient.put).toHaveBeenCalledWith(
      "/governance/policies/policy-1",
      { enabled: false },
    );
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      "/governance/policies/policy-1/toggle",
      { enabled: true },
    );
    expect(mockApiClient.delete).toHaveBeenCalledWith(
      "/governance/policies/policy-1",
    );

    expect(created).toMatchObject({ id: "policy-1", enabled: true });
    expect(updated).toMatchObject({ id: "policy-1", enabled: false });
    expect(toggled).toMatchObject({ id: "policy-1", enabled: true });
  });

  it("fails explicitly for unsupported governance violation resolution mutation", async () => {
    await expect(
      governanceService.resolveViolation("violation-1", "accepted risk"),
    ).rejects.toThrow(
      "Violation resolution is not exposed by the current Data Cloud governance API.",
    );
  });

  it("generates a compliance report handle from the canonical summary payload", async () => {
    const result = await governanceService.generateComplianceReport("7d");

    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/governance/compliance/summary",
    );
    expect(result).toEqual({
      reportId: "compliance-2026-04-15T08:05:00Z",
      status: "ready",
    });
  });

  it("downloads the derived compliance report payload for the current summary", async () => {
    const blob = await governanceService.downloadComplianceReport(
      "compliance-2026-04-15T08:05:00Z",
    );
    const text = await blob.text();
    const report = JSON.parse(text) as { summary: { complianceScore: number } };

    expect(report.summary.complianceScore).toBe(72);
  });

  it("searches derived audit logs without requiring a dedicated backend search route", async () => {
    const results = await governanceService.searchAuditLogs("auditor");

    expect(results).toHaveLength(1);
    expect(results[0]?.userName).toBe("Auditor");
  });
});
