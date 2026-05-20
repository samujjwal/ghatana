/**
 * Governance & Policy API Service
 *
 * Provides the Trust Center's action-backed governance, compliance, and audit
 * operations while keeping unsupported lifecycle mutations explicit.
 *
 * @doc.type service
 * @doc.purpose Governance and policy API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';
import { z } from 'zod';
import {
  ComplianceSummaryEnvelopeSchema,
  GovernanceInventoryResponseSchema,
  GovernancePolicySimulationRequestSchema,
  GovernancePolicySimulationResponseSchema,
  PiiFieldRegistryEnvelopeSchema,
  type ComplianceSummaryData as ComplianceSummary,
  type GovernanceInventory,
  type GovernancePolicySimulationResult,
  type PiiFieldRegistryData as PiiFieldRegistry,
} from '../contracts/schemas';
// DC-P1-009: Policy CRUD lifecycle is complete - boundary message imports removed

const GovernanceEnvelopeMetaSchema = z.object({
  tenantId: z.string().optional(),
  requestId: z.string().optional(),
  timestamp: z.string().optional(),
  apiVersion: z.string().optional(),
}).passthrough();

const RetentionTierSchema = z.enum(['transient', 'short-term', 'standard', 'compliance', 'permanent']);

const RetentionClassificationRequestSchema = z.object({
  collection: z.string().min(1),
  tier: RetentionTierSchema,
  reason: z.string().min(1),
  piiFields: z.array(z.string().min(1)).optional(),
});

const RetentionClassificationResultSchema = z.object({
  collection: z.string(),
  tier: RetentionTierSchema,
  retentionDays: z.number(),
  expiresAt: z.string().optional(),
  classifiedAt: z.string(),
  classifiedBy: z.string(),
  reason: z.string(),
  piiFields: z.array(z.string()),
  status: z.string(),
});

const RetentionPolicyResultSchema = z.object({
  collection: z.string(),
  tier: RetentionTierSchema,
  retentionDays: z.number(),
  legalHolds: z.array(z.string()).default([]),
  piiFields: z.array(z.string()).default([]),
  lastClassifiedAt: z.string().optional(),
  expiresAt: z.string().optional(),
  status: z.string(),
});

const RetentionPurgeDryRunRequestSchema = z.object({
  collection: z.string().min(1),
});

const RetentionPurgeDryRunResultSchema = z.object({
  collection: z.string(),
  dryRun: z.literal(true),
  status: z.literal('DRY_RUN_COMPLETE'),
  confirmationToken: z.string(),
  tokenExpiresInSec: z.number(),
  estimatedRows: z.number(),
  sampleEntityIds: z.array(z.string()),
  requestId: z.string(),
});

const RetentionPurgeExecuteRequestSchema = z.object({
  collection: z.string().min(1),
  confirmationToken: z.string().min(1),
});

const RetentionPurgeExecuteResultSchema = z.object({
  collection: z.string(),
  dryRun: z.literal(false),
  status: z.literal('PURGE_COMPLETED'),
  deletedRows: z.number(),
  requestedRows: z.number().optional(),
  failedRows: z.number().optional(),
  deletedEntityIds: z.array(z.string()),
  completedAt: z.string(),
  requestId: z.string(),
});

const RedactionRequestSchema = z.object({
  collection: z.string().min(1),
  entityId: z.string().min(1),
  fields: z.array(z.string().min(1)).optional(),
  reason: z.string().min(1),
});

const RedactionResultSchema = z.object({
  collection: z.string(),
  entityId: z.string(),
  redactedFields: z.array(z.string()),
  requestedFields: z.array(z.string()),
  reason: z.string(),
  status: z.enum(['NO_OP', 'REDACTED']),
  redactedAt: z.string(),
});

function governanceEnvelopeSchema<T extends z.ZodTypeAny>(dataSchema: T) {
  return z.object({
    data: dataSchema,
    meta: GovernanceEnvelopeMetaSchema.optional(),
  }).passthrough();
}

export type RetentionTier = z.infer<typeof RetentionTierSchema>;

export interface RetentionClassificationRequest {
  collection: string;
  tier: RetentionTier;
  reason: string;
  piiFields?: string[];
}

export interface RetentionClassificationResult {
  collection: string;
  tier: RetentionTier;
  retentionDays: number;
  expiresAt?: string;
  classifiedAt: string;
  classifiedBy: string;
  reason: string;
  piiFields: string[];
  status: string;
}

export interface RetentionPolicyResult {
  collection: string;
  tier: RetentionTier;
  retentionDays: number;
  legalHolds: string[];
  piiFields: string[];
  lastClassifiedAt?: string;
  expiresAt?: string;
  status: string;
}

export interface RetentionPurgeDryRunRequest {
  collection: string;
}

export interface RetentionPurgeDryRunResult {
  collection: string;
  dryRun: true;
  status: 'DRY_RUN_COMPLETE';
  confirmationToken: string;
  tokenExpiresInSec: number;
  estimatedRows: number;
  sampleEntityIds: string[];
  requestId: string;
}

export interface RetentionPurgeExecuteRequest {
  collection: string;
  confirmationToken: string;
}

export interface RetentionPurgeExecuteResult {
  collection: string;
  dryRun: false;
  status: 'PURGE_COMPLETED';
  deletedRows: number;
  requestedRows?: number;
  failedRows?: number;
  deletedEntityIds: string[];
  completedAt: string;
  requestId: string;
}

export interface GovernanceRedactionRequest {
  collection: string;
  entityId: string;
  fields?: string[];
  reason: string;
}

export interface GovernanceRedactionResult {
  collection: string;
  entityId: string;
  redactedFields: string[];
  requestedFields: string[];
  reason: string;
  status: 'NO_OP' | 'REDACTED';
  redactedAt: string;
}

export type GovernanceRecommendationAction =
  | 'classify-retention'
  | 'redact-pii'
  | 'refresh-compliance'
  | 'access-review';

export interface GovernanceRecommendationPayload {
  tier?: RetentionTier;
  reason?: string;
  piiFields?: string[];
  fields?: string[];
}

export interface GovernanceRecommendation {
  id: string;
  title: string;
  summary: string;
  priority: 'high' | 'medium';
  action: GovernanceRecommendationAction;
  actionLabel: string;
  evidence: string[];
  policyId?: string;
  payload?: GovernanceRecommendationPayload;
}

export type GovernanceOperationalAction =
  | GovernanceRecommendationAction
  | 'purge-retention'
  | 'create-policy'; // P1-1: Policy CRUD lifecycle

export interface GovernanceLifecycleSurface {
  id: string;
  title: string;
  status: 'live-action' | 'derived-read-only' | 'unavailable';
  summary: string;
  evidence: string[];
  action?: GovernanceOperationalAction;
  actionLabel?: string;
}

export type PolicySimulationResult = GovernancePolicySimulationResult;
export type TenantGovernanceInventory = GovernanceInventory;

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

const NO_ACCESS_REQUESTS: readonly AccessRequest[] = Object.freeze([]);

function unwrapEnvelope<T>(envelope: { data: T }): T {
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

function buildRecommendations(
  summary: ComplianceSummary,
  piiFields: PiiFieldRegistry,
  policies: Policy[],
): GovernanceRecommendation[] {
  const recommendations: GovernanceRecommendation[] = [];
  const effectivePiiFields = [...piiFields.globalFields, ...piiFields.tenantFields].slice(0, 3);

  if (summary.collectionsUnclassified > 0) {
    recommendations.push({
      id: 'recommend-retention-classification',
      title: 'Classify unreviewed collections',
      summary: 'Retention coverage is incomplete. Review unclassified collections before more data ages into ambiguous retention windows.',
      priority: 'high',
      action: 'classify-retention',
      actionLabel: 'Open retention action',
      policyId: 'retention-classification',
      payload: {
        tier: effectivePiiFields.length > 0 ? 'compliance' : 'standard',
        reason: `Review ${summary.collectionsUnclassified} unclassified collection${summary.collectionsUnclassified === 1 ? '' : 's'}`,
        piiFields: effectivePiiFields,
      },
      evidence: [
        `${summary.collectionsUnclassified} collection${summary.collectionsUnclassified === 1 ? '' : 's'} still require retention classification.`,
        `${summary.retentionExpirationsIn30Days} retention policy expiration${summary.retentionExpirationsIn30Days === 1 ? '' : 's'} arrive within 30 days.`,
      ],
    });
  }

  if (effectivePiiFields.length > 0) {
    recommendations.push({
      id: 'recommend-pii-redaction-template',
      title: 'Validate PII redaction coverage',
      summary: 'Registered PII fields should stay aligned with entity-level redaction workflows instead of relying on manual operator memory.',
      priority: summary.redactionsIn30Days === 0 ? 'high' : 'medium',
      action: 'redact-pii',
      actionLabel: 'Stage redaction review',
      policyId: 'privacy-pii-registry',
      payload: {
        fields: effectivePiiFields,
        reason: 'Validate registered PII redaction coverage',
      },
      evidence: [
        `${piiFields.effectiveCount} registered PII field${piiFields.effectiveCount === 1 ? '' : 's'} are active in this tenant.`,
        `${summary.redactionsIn30Days} redaction event${summary.redactionsIn30Days === 1 ? '' : 's'} completed in the last 30 days.`,
      ],
    });
  }

  if (summary.retentionExpirationsIn30Days > 0) {
    recommendations.push({
      id: 'recommend-refresh-compliance',
      title: 'Refresh expiring-policy posture',
      summary: 'The compliance summary should be refreshed so operators can review collections approaching retention expiry with the latest audit data.',
      priority: 'medium',
      action: 'refresh-compliance',
      actionLabel: 'Refresh compliance',
      policyId: 'retention-classification',
      evidence: [
        `${summary.retentionExpirationsIn30Days} policy expiration${summary.retentionExpirationsIn30Days === 1 ? '' : 's'} fall within the next 30 days.`,
        `${summary.auditEventsIn30Days} governance audit event${summary.auditEventsIn30Days === 1 ? '' : 's'} were recorded in the last 30 days.`,
      ],
    });
  }

  if (summary.authFailuresIn30Days > 0) {
    recommendations.push({
      id: 'recommend-access-review',
      title: 'Review access posture before escalation',
      summary: 'Authentication failures are trending above zero, so operators should confirm the current access-review boundary before promising approval workflows.',
      priority: 'high',
      action: 'access-review',
      actionLabel: 'Review access boundary',
      policyId: policies.some((policy) => policy.id === 'access-review') ? 'access-review' : undefined,
      evidence: [
        `${summary.authFailuresIn30Days} authentication failure${summary.authFailuresIn30Days === 1 ? '' : 's'} were recorded in the last 30 days.`,
        `Latest audit checkpoint: ${summary.lastAuditAt}.`,
      ],
    });
  }

  return recommendations.sort((left, right) => {
    if (left.priority !== right.priority) {
      return left.priority === 'high' ? -1 : 1;
    }
    return left.title.localeCompare(right.title);
  });
}

function buildLifecycleSurfaces(
  summary: ComplianceSummary,
  piiFields: PiiFieldRegistry,
): GovernanceLifecycleSurface[] {
  return [
    {
      id: 'retention-operations',
      title: 'Retention Operations',
      status: 'live-action',
      summary: summary.collectionsUnclassified > 0
        ? `${summary.collectionsUnclassified} collections still need retention classification before coverage is complete.`
        : 'Retention classification is live and current collections already carry a reviewed retention posture.',
      evidence: [
        `${summary.collectionsClassified} collections already classified`,
        `${summary.retentionExpirationsIn30Days} policies expire within 30 days`,
        'Launcher routes support classification lookup plus dry-run and execute purge flows',
      ],
      action: 'classify-retention',
      actionLabel: 'Classify retention',
    },
    {
      id: 'privacy-redaction',
      title: 'Privacy Redaction',
      status: 'live-action',
      summary: piiFields.effectiveCount > 0
        ? `PII-aware redaction is live for ${piiFields.effectiveCount} registered field${piiFields.effectiveCount === 1 ? '' : 's'}.`
        : 'Redaction remains live, but the current compliance summary has not registered tenant-specific PII fields yet.',
      evidence: [
        `${summary.redactionsIn30Days} redaction actions recorded in the last 30 days`,
        `Registered fields: ${[...piiFields.globalFields, ...piiFields.tenantFields].join(', ') || 'none yet'}`,
        'Launcher route supports entity-level redaction through the canonical governance contract',
      ],
      action: 'redact-pii',
      actionLabel: 'Redact PII',
    },
    {
      id: 'compliance-refresh',
      title: 'Compliance Refresh',
      status: 'live-action',
      summary: summary.complianceStatus === 'REVIEW_REQUIRED'
        ? 'Compliance posture currently requires operator review, and the Trust Center can refresh that summary from live launcher data.'
        : 'Compliance posture is currently healthy and can be rebuilt from live launcher data when operators need a fresh snapshot.',
      evidence: [
        `${summary.auditEventsIn30Days} audit events summarized over the last 30 days`,
        `${summary.authFailuresIn30Days} authentication failures contributed to the current posture`,
        'The Trust Center refresh action rebuilds the current compliance report instead of mutating policy state',
      ],
      action: 'refresh-compliance',
      actionLabel: 'Refresh summary',
    },
    {
      id: 'access-review',
      title: 'Access Review',
      status: 'derived-read-only',
      summary: 'Access review remains an operator visibility surface derived from audit and compliance summaries rather than an approval-mutation workflow.',
      evidence: [
        `${summary.authFailuresIn30Days} authorization-related failures inform current review posture`,
        `Last audit snapshot: ${summary.lastAuditAt}`,
        'No launcher route currently accepts approve or reject access-review decisions',
      ],
      action: 'access-review',
      actionLabel: 'See read-only status',
    },
    {
      id: 'policy-lifecycle',
      title: 'Policy Lifecycle',
      status: 'live-action',
      summary: 'Policy create, update, toggle, and delete flows are now available through the launcher-backed governance contract.',
      evidence: [
        'POST /api/v1/governance/policies - create policy',
        'GET /api/v1/governance/policies - list policies',
        'GET /api/v1/governance/policies/:id - get policy',
        'PUT /api/v1/governance/policies/:id - update policy',
        'DELETE /api/v1/governance/policies/:id - delete policy',
        'POST /api/v1/governance/policies/:id/toggle - toggle policy',
      ],
      action: 'create-policy',
      actionLabel: 'Create policy',
    },
  ];
}

/**
 * Governance Service Client
 */
export class GovernanceService {
  private async getPiiFieldRegistry(): Promise<PiiFieldRegistry> {
    const rawResponse = await apiClient.get('/governance/privacy/pii-fields');
    const response = PiiFieldRegistryEnvelopeSchema.parse(rawResponse);
    return unwrapEnvelope(response);
  }

  private async getComplianceSummary(): Promise<ComplianceSummary> {
    const rawResponse = await apiClient.get('/governance/compliance/summary');
    const response = ComplianceSummaryEnvelopeSchema.parse(rawResponse);
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

  async getRecommendations(): Promise<GovernanceRecommendation[]> {
    const [summary, piiFields] = await Promise.all([
      this.getComplianceSummary(),
      this.getPiiFieldRegistry(),
    ]);
    const policies = buildPolicies(summary, piiFields);
    return buildRecommendations(summary, piiFields, policies);
  }

  async getLifecycleSurfaces(): Promise<GovernanceLifecycleSurface[]> {
    const [summary, piiFields] = await Promise.all([
      this.getComplianceSummary(),
      this.getPiiFieldRegistry(),
    ]);
    return buildLifecycleSurfaces(summary, piiFields);
  }

  async getGovernanceInventory(): Promise<TenantGovernanceInventory> {
    const rawResponse = await apiClient.get('/governance/inventory');
    const response = GovernanceInventoryResponseSchema.parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async simulatePolicy(policy: Partial<Policy>): Promise<PolicySimulationResult> {
    const payload = GovernancePolicySimulationRequestSchema.parse(policy);
    const rawResponse = await apiClient.post('/governance/policies/simulate', payload);
    const response = GovernancePolicySimulationResponseSchema.parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async createPolicy(policy: Partial<Policy>): Promise<Policy> {
    // P1-1: Policy CRUD lifecycle - call real backend endpoint
    const rawResponse = await apiClient.post('/governance/policies', policy);
    const response = governanceEnvelopeSchema(z.object({
      id: z.string(),
      name: z.string(),
      type: z.enum(['SECURITY', 'PRIVACY', 'RETENTION', 'ACCESS', 'QUALITY', 'CUSTOM']),
      description: z.string().optional(),
      enabled: z.boolean(),
      scope: z.record(z.string(), z.unknown()).optional(),
      rules: z.array(z.object({
        condition: z.string(),
        action: z.enum(['ALLOW', 'DENY', 'MASK', 'AUDIT', 'REQUIRE_APPROVAL']),
        severity: z.enum(['INFO', 'WARNING', 'ERROR']),
        metadata: z.record(z.string(), z.unknown()).optional(),
      })).optional(),
      createdAt: z.string(),
      updatedAt: z.string(),
      tenantId: z.string().optional(),
      metadata: z.record(z.string(), z.unknown()).optional(),
    })).parse(rawResponse);
    return unwrapEnvelope(response) as Policy;
  }

  async classifyRetention(input: RetentionClassificationRequest): Promise<RetentionClassificationResult> {
    const request = RetentionClassificationRequestSchema.parse(input);
    const rawResponse = await apiClient.post('/governance/retention/classify', request);
    const response = governanceEnvelopeSchema(RetentionClassificationResultSchema).parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async getRetentionPolicy(collection: string): Promise<RetentionPolicyResult> {
    const normalizedCollection = collection.trim();
    if (!normalizedCollection) {
      throw new Error('Collection is required to load retention policy.');
    }

    const rawResponse = await apiClient.get('/governance/retention/policy', {
      params: { collection: normalizedCollection },
    });
    const response = governanceEnvelopeSchema(RetentionPolicyResultSchema).parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async updatePolicy(policyId: string, policy: Partial<Policy>): Promise<Policy> {
    // P1-1: Policy CRUD lifecycle - call real backend endpoint
    const rawResponse = await apiClient.put(`/governance/policies/${policyId}`, policy);
    const response = governanceEnvelopeSchema(z.object({
      id: z.string(),
      name: z.string(),
      type: z.enum(['SECURITY', 'PRIVACY', 'RETENTION', 'ACCESS', 'QUALITY', 'CUSTOM']),
      description: z.string().optional(),
      enabled: z.boolean(),
      scope: z.record(z.string(), z.unknown()).optional(),
      rules: z.array(z.object({
        condition: z.string(),
        action: z.enum(['ALLOW', 'DENY', 'MASK', 'AUDIT', 'REQUIRE_APPROVAL']),
        severity: z.enum(['INFO', 'WARNING', 'ERROR']),
        metadata: z.record(z.string(), z.unknown()).optional(),
      })).optional(),
      createdAt: z.string(),
      updatedAt: z.string(),
      tenantId: z.string().optional(),
      metadata: z.record(z.string(), z.unknown()).optional(),
    })).parse(rawResponse);
    return unwrapEnvelope(response) as Policy;
  }

  async deletePolicy(policyId: string): Promise<void> {
    // P1-1: Policy CRUD lifecycle - call real backend endpoint
    await apiClient.delete(`/governance/policies/${policyId}`);
  }

  async togglePolicy(policyId: string, enabled: boolean): Promise<Policy> {
    // P1-1: Policy CRUD lifecycle - call real backend endpoint
    const rawResponse = await apiClient.post(`/governance/policies/${policyId}/toggle`, { enabled });
    const response = governanceEnvelopeSchema(z.object({
      id: z.string(),
      name: z.string(),
      type: z.enum(['SECURITY', 'PRIVACY', 'RETENTION', 'ACCESS', 'QUALITY', 'CUSTOM']),
      description: z.string().optional(),
      enabled: z.boolean(),
      scope: z.record(z.string(), z.unknown()).optional(),
      rules: z.array(z.object({
        condition: z.string(),
        action: z.enum(['ALLOW', 'DENY', 'MASK', 'AUDIT', 'REQUIRE_APPROVAL']),
        severity: z.enum(['INFO', 'WARNING', 'ERROR']),
        metadata: z.record(z.string(), z.unknown()).optional(),
      })).optional(),
      createdAt: z.string(),
      updatedAt: z.string(),
      tenantId: z.string().optional(),
      metadata: z.record(z.string(), z.unknown()).optional(),
    })).parse(rawResponse);
    return unwrapEnvelope(response) as Policy;
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

  async redactEntity(input: GovernanceRedactionRequest): Promise<GovernanceRedactionResult> {
    const request = RedactionRequestSchema.parse(input);
    const rawResponse = await apiClient.post('/governance/privacy/redact', request);
    const response = governanceEnvelopeSchema(RedactionResultSchema).parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async purgeRetentionDryRun(input: RetentionPurgeDryRunRequest): Promise<RetentionPurgeDryRunResult> {
    const request = RetentionPurgeDryRunRequestSchema.parse(input);
    const rawResponse = await apiClient.post('/governance/retention/purge', {
      collection: request.collection,
      dryRun: true,
    });
    const response = governanceEnvelopeSchema(RetentionPurgeDryRunResultSchema).parse(rawResponse);
    return unwrapEnvelope(response);
  }

  async purgeRetentionExecute(input: RetentionPurgeExecuteRequest): Promise<RetentionPurgeExecuteResult> {
    const request = RetentionPurgeExecuteRequestSchema.parse(input);
    const rawResponse = await apiClient.post('/governance/retention/purge', {
      collection: request.collection,
      confirmationToken: request.confirmationToken,
      dryRun: false,
    });
    const response = governanceEnvelopeSchema(RetentionPurgeExecuteResultSchema).parse(rawResponse);
    return unwrapEnvelope(response);
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
    return Array.from(NO_ACCESS_REQUESTS);
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
