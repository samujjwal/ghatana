/**
 * Governance & Policy API Service
 *
 * Provides API client for policy management, compliance, and audit operations.
 *
 * @doc.type service
 * @doc.purpose Governance and policy API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

interface ApiEnvelope<T> {
  data: T;
  error?: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  meta: {
    tenantId: string;
    requestId: string;
    apiVersion?: string;
  };
}

interface PiiFieldRegistry {
  globalFields: string[];
  tenantFields: string[];
  effectiveCount: number;
}

interface ComplianceSummary {
  tenantId: string;
  collectionsTotal: number;
  collectionsClassified: number;
  collectionsUnclassified: number;
  piiFieldsRegistered: number;
  legalHoldsActive: number;
  retentionExpirationsIn30Days: number;
  lastAuditAt: string;
  auditEventsIn30Days: number;
  authFailuresIn30Days: number;
  redactionsIn30Days: number;
  purgesIn30Days: number;
  recentAuditEvents: Array<Record<string, unknown>>;
  complianceStatus: 'COMPLIANT' | 'NEEDS_CLASSIFICATION' | 'REVIEW_REQUIRED';
  generatedAt: string;
}

export interface Policy {
  id: string;
  name: string;
  type: 'SECURITY' | 'PRIVACY' | 'RETENTION' | 'ACCESS' | 'QUALITY';
  scope: {
    datasets?: string[];
    users?: string[];
    roles?: string[];
  };
  rules: PolicyRule[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  metadata: Record<string, unknown>;
}

export interface PolicyRule {
  condition: string;
  action: 'ALLOW' | 'DENY' | 'MASK' | 'AUDIT' | 'REQUIRE_APPROVAL';
  severity: 'INFO' | 'WARNING' | 'ERROR';
  metadata?: Record<string, unknown>;
}

export interface PolicyViolation {
  id: string;
  policyId: string;
  policyName: string;
  timestamp: string;
  userId: string;
  datasetId: string;
  action: string;
  status: 'BLOCKED' | 'WARNED' | 'APPROVED' | 'PENDING_APPROVAL';
  details: string;
  metadata: Record<string, unknown>;
}

export interface ComplianceReport {
  id: string;
  period: string;
  generatedAt: string;
  summary: {
    totalPolicies: number;
    activePolicies: number;
    violations: number;
    remediations: number;
    complianceScore: number;
  };
  details: {
    piiScans: {
      totalDatasets: number;
      datasetsWithPII: number;
      violations: number;
      remediated: number;
    };
    accessAudits: {
      totalAccesses: number;
      unauthorizedAttempts: number;
      blockedAccesses: number;
    };
    retentionCompliance: {
      datasetsCompliant: number;
      datasetsViolating: number;
    };
  };
}

export interface AuditLog {
  id: string;
  timestamp: string;
  userId: string;
  userName: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'BLOCKED';
  details: Record<string, unknown>;
}

export interface AccessRequest {
  id: string;
  requestedBy: string;
  datasetId: string;
  datasetName: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
}

function unwrapEnvelope<T>(envelope: ApiEnvelope<T>): T {
  return envelope.data;
}

function toComplianceScore(summary: ComplianceSummary): number {
  if (summary.complianceStatus === 'COMPLIANT') {
    return 100;
  }
  if (summary.complianceStatus === 'REVIEW_REQUIRED') {
    return 72;
  }
  return 58;
}

function buildPolicies(summary: ComplianceSummary, piiFields: PiiFieldRegistry): Policy[] {
  const timestamp = summary.generatedAt;
  return [
    {
      id: 'privacy-pii-registry',
      name: 'PII Registry Coverage',
      type: 'PRIVACY',
      scope: {},
      rules: [
        {
          condition: 'Registered PII fields must be reviewed before export',
          action: 'MASK',
          severity: 'WARNING',
        },
      ],
      enabled: piiFields.effectiveCount > 0,
      createdAt: timestamp,
      updatedAt: timestamp,
      metadata: {
        registeredFields: piiFields.effectiveCount,
        piiFields: [...piiFields.globalFields, ...piiFields.tenantFields],
      },
    },
    {
      id: 'retention-classification',
      name: 'Retention Classification Coverage',
      type: 'RETENTION',
      scope: {},
      rules: [
        {
          condition: 'All collections should carry a retention policy',
          action: 'REQUIRE_APPROVAL',
          severity: summary.collectionsUnclassified > 0 ? 'ERROR' : 'INFO',
        },
      ],
      enabled: summary.collectionsClassified > 0,
      createdAt: timestamp,
      updatedAt: timestamp,
      metadata: {
        collectionsClassified: summary.collectionsClassified,
        collectionsUnclassified: summary.collectionsUnclassified,
        expirationsIn30Days: summary.retentionExpirationsIn30Days,
      },
    },
    {
      id: 'security-audit-posture',
      name: 'Security Audit Posture',
      type: 'SECURITY',
      scope: {},
      rules: [
        {
          condition: 'Authentication failures should be investigated within 30 days',
          action: 'AUDIT',
          severity: summary.authFailuresIn30Days > 0 ? 'ERROR' : 'INFO',
        },
      ],
      enabled: true,
      createdAt: timestamp,
      updatedAt: timestamp,
      metadata: {
        authFailuresIn30Days: summary.authFailuresIn30Days,
        auditEventsIn30Days: summary.auditEventsIn30Days,
      },
    },
    {
      id: 'access-review',
      name: 'Access Review Guardrail',
      type: 'ACCESS',
      scope: {},
      rules: [
        {
          condition: 'Recent audit trail should remain free of authorization failures',
          action: 'AUDIT',
          severity: summary.authFailuresIn30Days > 0 ? 'WARNING' : 'INFO',
        },
      ],
      enabled: true,
      createdAt: timestamp,
      updatedAt: timestamp,
      metadata: {
        lastAuditAt: summary.lastAuditAt,
      },
    },
  ];
}

function buildViolations(summary: ComplianceSummary): PolicyViolation[] {
  const timestamp = summary.generatedAt;
  const violations: PolicyViolation[] = [];

  if (summary.collectionsUnclassified > 0) {
    violations.push({
      id: 'retention-unclassified',
      policyId: 'retention-classification',
      policyName: 'Retention Classification Coverage',
      timestamp,
      userId: 'system',
      datasetId: 'all',
      action: 'COLLECTION_CLASSIFICATION',
      status: 'PENDING_APPROVAL',
      details: `${summary.collectionsUnclassified} collections still require retention classification.`,
      metadata: {
        collectionsUnclassified: summary.collectionsUnclassified,
      },
    });
  }

  if (summary.authFailuresIn30Days > 0) {
    violations.push({
      id: 'security-auth-failures',
      policyId: 'security-audit-posture',
      policyName: 'Security Audit Posture',
      timestamp,
      userId: 'system',
      datasetId: 'auth',
      action: 'AUTH_FAILURE',
      status: 'WARNED',
      details: `${summary.authFailuresIn30Days} authentication failures were recorded in the last 30 days.`,
      metadata: {
        authFailuresIn30Days: summary.authFailuresIn30Days,
      },
    });
  }

  if (summary.retentionExpirationsIn30Days > 0) {
    violations.push({
      id: 'retention-expiring',
      policyId: 'retention-classification',
      policyName: 'Retention Classification Coverage',
      timestamp,
      userId: 'system',
      datasetId: 'retention',
      action: 'RETENTION_EXPIRING',
      status: 'PENDING_APPROVAL',
      details: `${summary.retentionExpirationsIn30Days} retention policies expire in the next 30 days.`,
      metadata: {
        retentionExpirationsIn30Days: summary.retentionExpirationsIn30Days,
      },
    });
  }

  return violations;
}

function buildAuditLogs(summary: ComplianceSummary): AuditLog[] {
  if (summary.recentAuditEvents.length > 0) {
    return summary.recentAuditEvents.map((event, index) => ({
      id: String(event.id ?? `audit-${index}`),
      timestamp: String(event.timestamp ?? summary.generatedAt),
      userId: String(event.userId ?? 'system'),
      userName: String(event.userName ?? event.principal ?? 'System'),
      action: String(event.action ?? event.eventType ?? 'AUDIT_EVENT'),
      resourceType: String(event.resourceType ?? 'governance'),
      resourceId: String(event.resourceId ?? 'summary'),
      outcome: String(event.outcome ?? 'SUCCESS').toUpperCase() === 'FAILURE' ? 'FAILURE' : 'SUCCESS',
      details: event,
    }));
  }

  return [
    {
      id: 'audit-summary',
      timestamp: summary.lastAuditAt,
      userId: 'system',
      userName: 'System',
      action: 'COMPLIANCE_SUMMARY_GENERATED',
      resourceType: 'governance',
      resourceId: summary.tenantId,
      outcome: summary.complianceStatus === 'REVIEW_REQUIRED' ? 'FAILURE' : 'SUCCESS',
      details: {
        auditEventsIn30Days: summary.auditEventsIn30Days,
        authFailuresIn30Days: summary.authFailuresIn30Days,
      },
    },
  ];
}

/**
 * Governance Service Client
 */
export class GovernanceService {
  private async getPiiFieldRegistry(): Promise<PiiFieldRegistry> {
    const response = await apiClient.get<ApiEnvelope<PiiFieldRegistry>>('/governance/privacy/pii-fields');
    return unwrapEnvelope(response);
  }

  private async getComplianceSummary(): Promise<ComplianceSummary> {
    const response = await apiClient.get<ApiEnvelope<ComplianceSummary>>('/governance/compliance/summary');
    return unwrapEnvelope(response);
  }

  async getPolicies(type?: string): Promise<Policy[]> {
    const [summary, piiFields] = await Promise.all([
      this.getComplianceSummary(),
      this.getPiiFieldRegistry(),
    ]);
    const policies = buildPolicies(summary, piiFields);
    return type ? policies.filter((policy) => policy.type === type) : policies;
  }

  async getPolicy(policyId: string): Promise<Policy> {
    const policies = await this.getPolicies();
    const policy = policies.find((entry) => entry.id === policyId);
    if (!policy) {
      throw new Error(`Policy not found: ${policyId}`);
    }
    return policy;
  }

  async createPolicy(policy: Partial<Policy>): Promise<Policy> {
    void policy;
    throw new Error('Policy creation is not exposed by the current Data Cloud governance API.');
  }

  async updatePolicy(policyId: string, policy: Partial<Policy>): Promise<Policy> {
    void policyId;
    void policy;
    throw new Error('Policy updates are not exposed by the current Data Cloud governance API.');
  }

  async deletePolicy(policyId: string): Promise<void> {
    void policyId;
    throw new Error('Policy deletion is not exposed by the current Data Cloud governance API.');
  }

  async togglePolicy(policyId: string, enabled: boolean): Promise<Policy> {
    void policyId;
    void enabled;
    throw new Error('Policy toggles are not exposed by the current Data Cloud governance API.');
  }

  async getViolations(policyId?: string, limit: number = 50): Promise<PolicyViolation[]> {
    const summary = await this.getComplianceSummary();
    const violations = buildViolations(summary);
    const filtered = policyId ? violations.filter((violation) => violation.policyId === policyId) : violations;
    return filtered.slice(0, limit);
  }

  async resolveViolation(violationId: string, resolution: string): Promise<void> {
    void violationId;
    void resolution;
    throw new Error('Violation resolution is not exposed by the current Data Cloud governance API.');
  }

  async getComplianceReport(period: string = '30d'): Promise<ComplianceReport> {
    const [summary, policies, violations] = await Promise.all([
      this.getComplianceSummary(),
      this.getPolicies(),
      this.getViolations(undefined, 50),
    ]);
    return {
      id: `compliance-${summary.generatedAt}`,
      period,
      generatedAt: summary.generatedAt,
      summary: {
        totalPolicies: policies.length,
        activePolicies: policies.filter((policy) => policy.enabled).length,
        violations: violations.length,
        remediations: summary.redactionsIn30Days + summary.purgesIn30Days,
        complianceScore: toComplianceScore(summary),
      },
      details: {
        piiScans: {
          totalDatasets: summary.collectionsTotal,
          datasetsWithPII: summary.collectionsClassified,
          violations: summary.authFailuresIn30Days,
          remediated: summary.redactionsIn30Days,
        },
        accessAudits: {
          totalAccesses: summary.auditEventsIn30Days,
          unauthorizedAttempts: summary.authFailuresIn30Days,
          blockedAccesses: summary.authFailuresIn30Days,
        },
        retentionCompliance: {
          datasetsCompliant: summary.collectionsClassified,
          datasetsViolating: summary.collectionsUnclassified + summary.retentionExpirationsIn30Days,
        },
      },
    };
  }

  async generateComplianceReport(period: string): Promise<{ reportId: string; status: string }> {
    const report = await this.getComplianceReport(period);
    return { reportId: report.id, status: 'ready' };
  }

  async downloadComplianceReport(reportId: string): Promise<Blob> {
    const report = await this.getComplianceReport();
    if (report.id !== reportId) {
      throw new Error(`Compliance report not found: ${reportId}`);
    }
    return new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
  }

  async getAuditLogs(
    resourceType?: string,
    userId?: string,
    limit: number = 100
  ): Promise<AuditLog[]> {
    const summary = await this.getComplianceSummary();
    return buildAuditLogs(summary)
      .filter((log) => (resourceType ? log.resourceType === resourceType : true))
      .filter((log) => (userId ? log.userId === userId : true))
      .slice(0, limit);
  }

  async searchAuditLogs(query: string): Promise<AuditLog[]> {
    const logs = await this.getAuditLogs();
    const normalized = query.toLowerCase();
    return logs.filter((log) =>
      log.action.toLowerCase().includes(normalized)
      || log.resourceType.toLowerCase().includes(normalized)
      || log.resourceId.toLowerCase().includes(normalized)
      || log.userName.toLowerCase().includes(normalized),
    );
  }

  async getAccessRequests(status?: string): Promise<AccessRequest[]> {
    void status;
    return [];
  }

  async requestAccess(datasetId: string, reason: string): Promise<AccessRequest> {
    return {
      id: `access-${datasetId}`,
      requestedBy: 'current-user',
      datasetId,
      datasetName: datasetId,
      reason,
      status: 'PENDING',
      requestedAt: new Date().toISOString(),
    };
  }

  async reviewAccessRequest(
    requestId: string,
    decision: 'APPROVED' | 'REJECTED',
    comment?: string
  ): Promise<AccessRequest> {
    return {
      id: requestId,
      requestedBy: 'current-user',
      datasetId: 'unknown',
      datasetName: 'unknown',
      reason: comment ?? '',
      status: decision,
      requestedAt: new Date().toISOString(),
      reviewedBy: 'current-user',
      reviewedAt: new Date().toISOString(),
    };
  }
}

export const governanceService = new GovernanceService();

export default governanceService;
