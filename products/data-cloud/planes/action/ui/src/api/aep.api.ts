/**
 * AEP REST API client — agents, HITL, monitoring, learning.
 *
 * Extends `pipeline.api.ts` (which handles pipeline CRUD).
 * This module covers all other AEP backend resources.
 *
 * @doc.type api-client
 * @doc.purpose AEP agent registry, HITL queue, observability, and learning endpoints
 * @doc.layer frontend
 */
import { apiClient as client } from "@/lib/http-client";

// ─── Types ───────────────────────────────────────────────────────────

export type AgentStatus = "ACTIVE" | "IDLE" | "ERROR" | "UNKNOWN";
export type AgentRegistrationMode = "direct" | "manifest-only";
export type ReviewItemStatus = "PENDING" | "APPROVED" | "REJECTED";
export type PolicyStatus =
  | "PENDING_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "ACTIVE"
  | "DEPRECATED";
export type EpisodeOutcome = "SUCCESS" | "FAILURE" | "TIMEOUT" | "CANCELLED";

export interface AgentRegistration {
  id: string;
  name: string;
  tenantId: string;
  version: string;
  type: string;
  status: AgentStatus;
  capabilities: string[];
  memoryCount: number;
  description: string;
  registrationMode: AgentRegistrationMode;
  executable: boolean;
  registryStorage: string;
  memoryPersistence: string;
  lastSeen?: string;
  registeredAt: string;
  config?: Record<string, unknown>;
}

export interface PipelineRun {
  id: string;
  pipelineId: string;
  pipelineName: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  startedAt: string;
  finishedAt?: string;
  eventsProcessed: number;
  errorsCount: number;
}

export interface RunLineageEntry {
  eventType: string;
  timestamp: string;
  pipelineId: string;
  stepType: string;
  status: string;
  details: Record<string, unknown>;
}

export interface RunAgentVersionReference {
  agentId: string;
  version: string;
}

export interface RunProvenanceSummary {
  pipelineVersion?: string;
  agentVersions: RunAgentVersionReference[];
  policyBundle: string[];
  evaluationGate?: string;
  complianceBundle: Record<string, unknown>;
}

export interface RunDecisionEntry {
  reviewItemId: string;
  skillId: string;
  decision: string;
  decidedAt: string;
  stepType: string;
}

export interface RunPolicyEntry {
  policyId: string;
  skillId: string;
  version: string;
  promotedAt: string;
  stepType: string;
}

export interface PipelineRunDetail extends PipelineRun {
  lineage: RunLineageEntry[];
  decisions: RunDecisionEntry[];
  policies: RunPolicyEntry[];
  provenance: RunProvenanceSummary;
}

export interface GovernanceComplianceSummary {
  tenantId: string;
  configured: boolean;
  supportedOperations: string[];
  registeredCollections: string[];
  soc2: {
    title: string;
    generatedAt: string;
    overallStatus: string;
    controlCount: number;
    freshness: {
      status: "FRESH" | "STALE" | "MISSING";
      reportAvailable: boolean;
      newestEvidenceAt: string | null;
      evidenceAgeDays: number | null;
      maxAgeDays: number;
      message: string;
    };
    controls: Array<{
      controlId: string;
      description: string;
      status: string;
      /** Link to audit evidence or scan report for this control. */
      evidenceUrl?: string;
      /** Related audit entry ID that generated this control status. */
      auditEntryId?: string;
    }>;
  };
  timestamp: string;
}

export interface GovernanceAuditEntry {
  eventType: string;
  timestamp: string;
  runId?: string;
  pipelineId?: string;
  status?: string;
  payload?: Record<string, unknown>;
}

export interface GovernanceAuditSummary {
  tenantId: string;
  configured: boolean;
  entries: GovernanceAuditEntry[];
  count: number;
  timestamp: string;
}

export interface GovernanceTenancySummary {
  tenantId: string;
  active: boolean;
  globalActive: boolean;
  mode: string;
}

export interface GovernanceKillSwitchSummary {
  tenantId: string;
  active: boolean;
  globalActive: boolean;
  timestamp: string;
}

export interface GovernanceKillSwitchMutationResult {
  activated?: boolean;
  deactivated?: boolean;
  tenantId: string;
  incidentId?: string;
  auditId?: string;
}

export interface MarkAnomalyFalsePositiveResult {
  anomalyId: string;
  tenantId: string;
  markedFalsePositive: boolean;
  auditId?: string;
  timestamp: string;
}

export type ConsentDecisionStatus = "granted" | "denied" | "withdrawn";

export interface ConsentDecisionRecord {
  consentId: string;
  userId: string;
  status: ConsentDecisionStatus;
  purposes: string[];
  decidedAt: string;
}

export interface ConsentDecisionSummary {
  tenantId: string;
  count: number;
  items: ConsentDecisionRecord[];
}

export interface GovernanceOpsSummary {
  tenantId: string;
  backupConfigured: boolean;
  backupCount: number;
  lastBackupAt: string | null;
  latestBackupStatus: string;
  drReadiness: "PASS" | "FAIL" | "UNAVAILABLE";
  lastDrDrillAt: string | null;
  exportQueueConfigured: boolean;
  exportQueueDepth: number | null;
  automatedBackupsScheduled: boolean;
  trustedProxyForwardedAcceptedCount: number;
  trustedProxyForwardedRejectedCount: number;
  trustedProxyAlertState: "OK" | "ALERT" | "UNAVAILABLE";
  trustedProxyRejectionReasons: Record<string, number>;
  notes: string[];
  timestamp: string;
}

export type PipelineRunWire = Partial<PipelineRun> & {
  runId?: string;
  completedAt?: string;
  durationMs?: number;
};

export function normalizePipelineRun(raw: PipelineRunWire): PipelineRun {
  const id = raw.id ?? raw.runId ?? "";
  return {
    id,
    pipelineId: raw.pipelineId ?? "event",
    pipelineName: raw.pipelineName ?? raw.pipelineId ?? "event",
    status: raw.status ?? "RUNNING",
    startedAt: raw.startedAt ?? new Date(0).toISOString(),
    finishedAt: raw.finishedAt ?? raw.completedAt,
    eventsProcessed: raw.eventsProcessed ?? 0,
    errorsCount: raw.errorsCount ?? 0,
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

const EMPTY_STRING_ARRAY: string[] = [];

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return EMPTY_STRING_ARRAY;
  }
  return value
    .map((item) => asString(item))
    .filter((item): item is string => item !== undefined);
}

function candidateRecords(details: Record<string, unknown>): Record<string, unknown>[] {
  const nested = [asRecord(details.provenance), asRecord(details.metadata), asRecord(details.complianceBundle)];
  return [details, ...nested.filter((value): value is Record<string, unknown> => value !== null)];
}

function derivePipelineVersion(lineage: RunLineageEntry[]): string | undefined {
  for (const entry of lineage) {
    for (const record of candidateRecords(entry.details)) {
      const value = asString(record.pipelineVersion);
      if (value) {
        return value;
      }
    }
  }
  return undefined;
}

function deriveEvaluationGate(lineage: RunLineageEntry[]): string | undefined {
  for (const entry of lineage) {
    for (const record of candidateRecords(entry.details)) {
      const value = asString(record.evaluationGate) ?? asString(record.gateName);
      if (value) {
        return value;
      }
    }
  }
  return undefined;
}

function deriveComplianceBundle(lineage: RunLineageEntry[]): Record<string, unknown> {
  for (const entry of lineage) {
    const directBundle = asRecord(entry.details.complianceBundle);
    if (directBundle) {
      return directBundle;
    }
  }

  const flattened: Record<string, unknown> = {};
  for (const entry of lineage) {
    for (const record of candidateRecords(entry.details)) {
      for (const key of ["piiEnforcement", "piiBlockDefault", "auditLogEnabled", "killSwitchEnabled"]) {
        if (!(key in flattened) && key in record) {
          flattened[key] = record[key];
        }
      }
    }
  }
  return flattened;
}

function derivePolicyBundle(lineage: RunLineageEntry[], policies: RunPolicyEntry[]): string[] {
  const values = new Set<string>();
  for (const policy of policies) {
    const versionSuffix = policy.version ? `@${policy.version}` : "";
    if (policy.policyId) {
      values.add(`${policy.policyId}${versionSuffix}`);
    }
  }

  for (const entry of lineage) {
    for (const record of candidateRecords(entry.details)) {
      for (const value of asStringArray(record.policyBundle)) {
        values.add(value);
      }
      for (const value of asStringArray(record.policySet)) {
        values.add(value);
      }
    }
  }

  return Array.from(values);
}

function deriveAgentVersions(lineage: RunLineageEntry[]): RunAgentVersionReference[] {
  const versions = new Map<string, string>();

  for (const entry of lineage) {
    for (const record of candidateRecords(entry.details)) {
      const agentVersionsList = record.agentVersions;
      if (Array.isArray(agentVersionsList)) {
        for (const item of agentVersionsList) {
          const raw = asRecord(item);
          if (!raw) {
            continue;
          }
          const agentId = asString(raw.agentId) ?? asString(raw.skillId) ?? asString(raw.agent);
          const version = asString(raw.version) ?? asString(raw.agentVersion);
          if (agentId && version) {
            versions.set(agentId, version);
          }
        }
      } else {
        const agentVersionMap = asRecord(agentVersionsList);
        if (agentVersionMap) {
          for (const [agentId, version] of Object.entries(agentVersionMap)) {
            const normalizedVersion = asString(version);
            if (normalizedVersion) {
              versions.set(agentId, normalizedVersion);
            }
          }
        }
      }

      const singleVersion = asString(record.agentVersion);
      const singleAgentId =
        asString(record.agentId) ?? asString(record.skillId) ?? asString(record.agent);
      if (singleAgentId && singleVersion) {
        versions.set(singleAgentId, singleVersion);
      }
    }
  }

  return Array.from(versions.entries()).map(([agentId, version]) => ({ agentId, version }));
}

function normalizeRunProvenance(
  provenance: unknown,
  lineage: RunLineageEntry[],
  policies: RunPolicyEntry[],
): RunProvenanceSummary {
  const raw = asRecord(provenance);
  return {
    pipelineVersion: asString(raw?.pipelineVersion) ?? derivePipelineVersion(lineage),
    agentVersions: raw?.agentVersions && Array.isArray(raw.agentVersions)
      ? deriveAgentVersions([
          {
            eventType: "summary",
            timestamp: "",
            pipelineId: "",
            stepType: "summary",
            status: "",
            details: { agentVersions: raw.agentVersions },
          },
          ...lineage,
        ])
      : deriveAgentVersions(lineage),
    policyBundle: asStringArray(raw?.policyBundle).length > 0
      ? asStringArray(raw?.policyBundle)
      : derivePolicyBundle(lineage, policies),
    evaluationGate: asString(raw?.evaluationGate) ?? deriveEvaluationGate(lineage),
    complianceBundle: asRecord(raw?.complianceBundle) ?? deriveComplianceBundle(lineage),
  };
}

export interface PipelineMetrics {
  pipelineId: string;
  pipelineName: string;
  throughputPerSec: number;
  errorRate: number;
  avgLatencyMs: number;
  activeRuns: number;
  totalRuns: number;
  lastRunAt?: string;
}

export interface RuntimeDurabilityStatus {
  mode: "durable" | "degraded" | "ephemeral";
  title: string;
  description: string;
  components: Record<string, string>;
  checkedAt: string;
  profile?: string;
  dataCloudStorage?: string;
  reasons?: string[];
}

interface DeepHealthDurabilityResponse {
  mode: RuntimeDurabilityStatus["mode"];
  title: string;
  description: string;
  profile?: string;
  dataCloudStorage?: string;
  executionHistory?: string;
  pipelineStorage?: string;
  memoryPersistence?: string;
  reasons?: string[];
}

export interface ReviewItem {
  reviewId: string;
  tenantId: string;
  skillId: string;
  itemType: "POLICY" | "PATTERN" | "AGENT_DECISION";
  status: ReviewItemStatus;
  proposedVersion: Record<string, unknown>;
  confidenceScore?: number;
  createdAt: string;
  reviewedAt?: string;
  reviewerNote?: string;
}

export interface EpisodeRecord {
  id: string;
  tenantId: string;
  agentId: string;
  pipelineId: string;
  outcome: EpisodeOutcome;
  latencyMs: number;
  inputSummary?: string;
  outputSummary?: string;
  timestamp: string;
}

export interface LearnedPolicy {
  id: string;
  tenantId: string;
  skillId: string;
  name: string;
  description: string;
  status: PolicyStatus;
  confidenceScore: number;
  episodeCount: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  /** Related review queue item for pending policies. */
  reviewId?: string;
  decidedAt?: string;
  reviewerId?: string;
  reviewerRationale?: string;
  autoPromotable: boolean;
  autoPromoted: boolean;
  provenance?: {
    policyId?: string;
    skillId?: string;
    version?: number;
    sourceEpisodeIds: string[];
    evaluationMetrics: Record<string, number>;
    activationMode?: string;
    approverId?: string;
    approverRationale?: string;
    promotedAt?: string;
    rollbackPointerId?: string;
    canaryFraction?: number;
  };
  gateResult?: {
    gateName?: string;
    passed?: boolean;
    score?: number;
    threshold?: number;
    reason?: string;
  };
  /** Pipeline this policy was derived from. */
  relatedPipelineId?: string;
  /** Run that produced this policy. */
  relatedRunId?: string;
  /** Agent that generated this policy. */
  relatedAgentId?: string;
}

export interface MarketplaceAgentListing {
  id: string;
  name: string;
  description: string;
  version: string;
  domain: string;
  level: string;
  capabilities: string[];
  tags: string[];
  source: string;
  owner: string;
  publishedAt?: string;
  updatedAt?: string;
  averageRating: number;
  reviewCount: number;
}

export interface MarketplaceReview {
  id: string;
  agentId: string;
  tenantId: string;
  reviewer: string;
  rating: number;
  title: string;
  comment: string;
  createdAt: string;
}

export interface MarketplaceAgentDetail {
  listing: MarketplaceAgentListing;
  reviews: MarketplaceReview[];
}

export interface MarketplaceInstallSimulation {
  agentId: string;
  agentName: string;
  requestedVersion: string;
  availableVersion: string;
  targetEnvironment: string;
  versionPinned: boolean;
  compatibilityStatus: "COMPATIBLE" | "REVIEW_REQUIRED" | "BLOCKED";
  compatibilityNotes: string[];
  directExecutionMode: string;
  productionExecutionMode: string;
  requiresHitl: boolean;
  recommendedPath: string;
  allowedToInstall: boolean;
}

export interface PublishMarketplaceAgentInput {
  id?: string;
  name: string;
  description?: string;
  version?: string;
  domain?: string;
  level?: string;
  capabilities: string[];
  tags: string[];
  owner?: string;
}

export interface CreateMarketplaceReviewInput {
  reviewer?: string;
  rating: number;
  title?: string;
  comment?: string;
}

export interface MarketplaceInstallInput {
  targetEnvironment: "sandbox" | "staging" | "production";
  expectedVersion: string;
  config?: Record<string, unknown>;
}

export interface MarketplaceInstallResult {
  installId: string;
  agentId: string;
  agentName: string;
  agentVersion: string;
  tenantId: string;
  compatibilityStatus: string;
  recommendedPath: string;
  directExecutionMode: string;
  productionExecutionMode: string;
  targetEnvironment: string;
  installedAt: string;
  status: string;
}

function asNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function normalizeLearnedPolicy(raw: Partial<LearnedPolicy> | Record<string, unknown>, tenantId: string): LearnedPolicy {
  const rawRecord = raw as Partial<LearnedPolicy> & Record<string, unknown>;
  const provenanceRecord = asRecord(rawRecord.provenance);
  const gateResultRecord = asRecord(rawRecord.gateResult);
  const sourceEpisodeIds = asStringArray(provenanceRecord?.sourceEpisodeIds);
  const evaluationMetrics = asRecord(provenanceRecord?.evaluationMetrics);
  const typedMetrics = Object.fromEntries(
    Object.entries(evaluationMetrics ?? {}).flatMap(([key, value]) => {
      const numeric = asNumber(value);
      return numeric === undefined ? [] : [[key, numeric] as const];
    }),
  );
  const autoPromoted =
    rawRecord.autoPromoted === true ||
    asString(rawRecord.reviewerId)?.toLowerCase() === "auto-promote" ||
    asString(provenanceRecord?.approverId)?.toLowerCase() === "auto-promote";

  return {
    id: asString(rawRecord.id) ?? asString(rawRecord.reviewId) ?? "policy",
    tenantId: asString(rawRecord.tenantId) ?? tenantId,
    skillId: asString(rawRecord.skillId) ?? asString(rawRecord.agentId) ?? "unknown-skill",
    name: asString(rawRecord.name) ?? `Policy proposal for ${asString(rawRecord.skillId) ?? "unknown skill"}`,
    description: asString(rawRecord.description) ?? asString(rawRecord.summary) ?? "",
    status: (asString(rawRecord.status) as PolicyStatus | undefined) ?? "PENDING_REVIEW",
    confidenceScore: asNumber(rawRecord.confidenceScore) ?? asNumber(rawRecord.confidence) ?? 0,
    episodeCount: asNumber(rawRecord.episodeCount) ?? sourceEpisodeIds.length,
    version: asNumber(rawRecord.version) ?? 1,
    createdAt: asString(rawRecord.createdAt) ?? new Date(0).toISOString(),
    updatedAt: asString(rawRecord.updatedAt) ?? asString(rawRecord.decidedAt) ?? asString(rawRecord.createdAt) ?? new Date(0).toISOString(),
    autoPromotable: rawRecord.autoPromotable === true,
    autoPromoted,
    ...(asString(rawRecord.reviewId) ? { reviewId: asString(rawRecord.reviewId) } : {}),
    ...(asString(rawRecord.decidedAt) ? { decidedAt: asString(rawRecord.decidedAt) } : {}),
    ...(asString(rawRecord.reviewerId) ? { reviewerId: asString(rawRecord.reviewerId) } : {}),
    ...(asString(rawRecord.reviewerRationale) ? { reviewerRationale: asString(rawRecord.reviewerRationale) } : {}),
    ...(provenanceRecord
      ? {
          provenance: {
            sourceEpisodeIds,
            evaluationMetrics: typedMetrics,
            ...(asString(provenanceRecord.policyId) ? { policyId: asString(provenanceRecord.policyId) } : {}),
            ...(asString(provenanceRecord.skillId) ? { skillId: asString(provenanceRecord.skillId) } : {}),
            ...(asNumber(provenanceRecord.version) !== undefined ? { version: asNumber(provenanceRecord.version) } : {}),
            ...(asString(provenanceRecord.activationMode) ? { activationMode: asString(provenanceRecord.activationMode) } : {}),
            ...(asString(provenanceRecord.approverId) ? { approverId: asString(provenanceRecord.approverId) } : {}),
            ...(asString(provenanceRecord.approverRationale)
              ? { approverRationale: asString(provenanceRecord.approverRationale) }
              : {}),
            ...(asString(provenanceRecord.promotedAt) ? { promotedAt: asString(provenanceRecord.promotedAt) } : {}),
            ...(asString(provenanceRecord.rollbackPointerId)
              ? { rollbackPointerId: asString(provenanceRecord.rollbackPointerId) }
              : {}),
            ...(asNumber(provenanceRecord.canaryFraction) !== undefined
              ? { canaryFraction: asNumber(provenanceRecord.canaryFraction) }
              : {}),
          },
        }
      : {}),
    ...(gateResultRecord
      ? {
          gateResult: {
            ...(asString(gateResultRecord.gateName) ? { gateName: asString(gateResultRecord.gateName) } : {}),
            ...(typeof gateResultRecord.passed === "boolean" ? { passed: gateResultRecord.passed } : {}),
            ...(asNumber(gateResultRecord.score) !== undefined ? { score: asNumber(gateResultRecord.score) } : {}),
            ...(asNumber(gateResultRecord.threshold) !== undefined
              ? { threshold: asNumber(gateResultRecord.threshold) }
              : {}),
            ...(asString(gateResultRecord.reason) ? { reason: asString(gateResultRecord.reason) } : {}),
          },
        }
      : {}),
    ...(asString(rawRecord.relatedPipelineId) ? { relatedPipelineId: asString(rawRecord.relatedPipelineId) } : {}),
    ...(asString(rawRecord.relatedRunId) ? { relatedRunId: asString(rawRecord.relatedRunId) } : {}),
    ...(asString(rawRecord.relatedAgentId) ? { relatedAgentId: asString(rawRecord.relatedAgentId) } : {}),
  };
}

export interface CostBreakdown {
  id: string;
  name: string;
  costUsd: number;
  sharePercent: number;
  runCount: number;
  lastSeenAt?: string;
}

export interface CostBudgetWindow {
  budgetUsd: number;
  observedUsd: number;
  remainingUsd: number;
  usagePercent: number;
  status: "healthy" | "warning" | "exceeded";
}

export interface CostBudgetSummary {
  daily: CostBudgetWindow;
  monthly: CostBudgetWindow;
}

export interface CostAlert {
  id: string;
  severity: "info" | "warning" | "critical";
  title: string;
  description: string;
  currentValue: number;
  thresholdValue: number;
  relatedPipelineId?: string;
  relatedRunId?: string;
  /** Owner or assignee who acknowledged this alert. */
  owner?: string;
  /** Whether the alert has been acknowledged. */
  acknowledgedAt?: string;
  /** Whether the alert has been snoozed until this ISO timestamp. */
  snoozedUntil?: string;
  /** Whether the alert has been resolved. */
  resolvedAt?: string;
  /** Resolution note. */
  resolutionNote?: string;
}

export interface CostSummary {
  tenantId: string;
  windowStart: string;
  windowEnd: string;
  totalCostUsd: number;
  projectedMonthlyCostUsd: number;
  averageCostPerRunUsd: number;
  perPipeline: CostBreakdown[];
  perAgent: CostBreakdown[];
  perModel: CostBreakdown[];
  budget: CostBudgetSummary;
  alerts: CostAlert[];
  dataSource: string;
  allocationModel: string;
  /** True when cost figures are synthesised from formula rather than real billing telemetry. */
  estimated: boolean;
}

export type PrivacyRequestType =
  | "gdpr_access"
  | "gdpr_erasure"
  | "gdpr_portability"
  | "ccpa_opt_out";

export interface ComplianceReport {
  operationType: PrivacyRequestType | string;
  tenantId: string;
  subjectId: string;
  success: boolean;
  message: string;
  total: number;
  breakdown: Record<string, number>;
  warnings: string[];
  start: string;
  end: string;
}

export interface PrivacyPortabilityExport {
  tenantId?: string;
  subjectId?: string;
  exportedAt?: string;
  [key: string]: unknown;
}

interface AgentListResponse {
  agents?: AgentRegistration[];
}

interface DeepHealthResponse {
  status: string;
  probe: string;
  timestamp: string;
  components?: Record<string, string>;
  durability?: DeepHealthDurabilityResponse;
}

interface AgentMemoryResponse {
  agentId: string;
  tenantId: string;
  total: number;
  byType: Record<string, number>;
  timestamp: string;
}

interface AgentEpisodesResponse {
  episodes?: AgentEpisodeRecord[];
}

interface AgentFactsResponse {
  facts?: AgentFact[];
}

interface AgentPoliciesResponse {
  policies?: AgentPolicyRecord[];
}

interface PipelineRunsResponse {
  runs?: PipelineRunWire[];
}

type AgentRegistrationWire = Partial<AgentRegistration> & {
  capabilities?: unknown;
  memoryCount?: number | string;
  config?: Record<string, unknown>;
};

function normalizeAgentStatus(status: unknown): AgentStatus {
  switch (status) {
    case "ACTIVE":
    case "IDLE":
    case "ERROR":
    case "UNKNOWN":
      return status;
    default:
      return "UNKNOWN";
  }
}

function normalizeAgentRegistration(raw: AgentRegistrationWire): AgentRegistration {
  const capabilities = Array.isArray(raw.capabilities)
    ? raw.capabilities.map((capability) => String(capability))
    : [];
  const memoryCount = typeof raw.memoryCount === "string"
    ? Number.parseInt(raw.memoryCount, 10)
    : raw.memoryCount;

  return {
    id: raw.id ?? "",
    name: raw.name ?? raw.id ?? "Unnamed agent",
    tenantId: raw.tenantId ?? "default",
    version: raw.version ?? "1.0.0",
    type: raw.type ?? "unknown",
    status: normalizeAgentStatus(raw.status),
    capabilities,
    memoryCount: Number.isFinite(memoryCount) ? Number(memoryCount) : 0,
    description: raw.description ?? "",
    registrationMode: raw.registrationMode === "manifest-only" ? "manifest-only" : "direct",
    executable: raw.executable ?? true,
    registryStorage: raw.registryStorage ?? "unconfigured",
    memoryPersistence: raw.memoryPersistence ?? "unavailable",
    lastSeen: raw.lastSeen,
    registeredAt: raw.registeredAt ?? new Date(0).toISOString(),
    config: raw.config ?? {},
  };
}

function deriveRuntimeDurabilityStatus(health: DeepHealthResponse): RuntimeDurabilityStatus {
  const components = health.components ?? {};
  if (health.durability) {
    return {
      mode: health.durability.mode,
      title: health.durability.title,
      description: health.durability.description,
      components,
      checkedAt: health.timestamp,
      profile: health.durability.profile,
      dataCloudStorage: health.durability.dataCloudStorage,
      reasons: health.durability.reasons,
    };
  }

  const executionHistory = components["execution-history"] ?? "unknown";
  const eventLog = components["data-cloud.event-log"] ?? "unknown";
  const runLedger = components["run-ledger"] ?? "unknown";
  const pipelineStorage = components["pipeline-storage"] ?? "unknown";

  if (executionHistory === "ok" && eventLog === "ok" && runLedger === "ok" && pipelineStorage === "ok") {
    return {
      mode: "durable",
      title: "Durable runtime state",
      description: "Run history, ledger state, and pipeline storage are backed by configured persistent services.",
      components,
      checkedAt: health.timestamp,
      reasons: [],
    };
  }

  if (executionHistory === "ok" && eventLog === "ok") {
    return {
      mode: "degraded",
      title: "Partially durable runtime state",
      description: `Run history is durable, but related runtime state is degraded: pipeline storage=${pipelineStorage}, run ledger=${runLedger}.`,
      components,
      checkedAt: health.timestamp,
      reasons: ["At least one runtime backing surface is not durable."],
    };
  }

  return {
    mode: "ephemeral",
    title: "Ephemeral runtime state",
    description: `Execution history is not durable: execution-history=${executionHistory}, event-log=${eventLog}, run-ledger=${runLedger}.`,
    components,
    checkedAt: health.timestamp,
    reasons: ["Runtime state will be lost on restart."],
  };
}

interface GovernanceKillSwitchResponse {
  tenantId: string;
  active: boolean;
  globalActive: boolean;
  timestamp: string;
}

interface GovernanceDegradationResponse {
  tenantId: string;
  mode: string;
  timestamp: string;
}

interface PipelineMetricsResponse {
  metrics?: PipelineMetrics[];
}

interface PendingReviewsResponse {
  items?: ReviewItem[];
}

interface EpisodesResponse {
  episodes?: EpisodeRecord[];
}

interface PoliciesResponse {
  policies?: Array<Partial<LearnedPolicy> & Record<string, unknown>>;
}

interface ReflectionResponse {
  triggered: boolean;
}

interface MarketplaceAgentsResponse {
  agents?: MarketplaceAgentListing[];
}

interface MarketplaceAgentResponse {
  listing: MarketplaceAgentListing;
  reviews?: MarketplaceReview[];
}

interface MarketplaceInstallSimulationResponse {
  simulation: MarketplaceInstallSimulation;
}

interface MarketplaceReviewsResponse {
  reviews?: MarketplaceReview[];
}

interface CostSummaryResponse {
  summary: CostSummary;
  estimated: boolean;
  timestamp: string;
}

interface WorkflowTemplatesResponse {
  templates?: WorkflowTemplate[];
}

interface WorkflowTemplateVersionsResponse {
  versions?: WorkflowTemplateVersion[];
}

interface InstantiateTemplateResponse {
  pipelineId: string;
}

// ─── Agent Registry ──────────────────────────────────────────────────

export async function listAgents(
  tenantId = "default",
): Promise<AgentRegistration[]> {
  const { data } = await client.get<AgentListResponse>("/api/v1/agents", {
    params: { tenantId },
  });
  return (data.agents ?? []).map((agent) => normalizeAgentRegistration(agent));
}

export async function getAgent(
  agentId: string,
  tenantId = "default",
): Promise<AgentRegistration> {
  const { data } = await client.get<AgentRegistrationWire>(
    `/api/v1/agents/${agentId}`,
    {
      params: { tenantId },
    },
  );
  return normalizeAgentRegistration(data);
}

export async function getAgentMemory(
  agentId: string,
  tenantId = "default",
): Promise<{
  agentId: string;
  tenantId: string;
  total: number;
  byType: Record<string, number>;
  timestamp: string;
}> {
  const { data } = await client.get<AgentMemoryResponse>(
    `/api/v1/agents/${agentId}/memory`,
    {
      params: { tenantId },
    },
  );
  return data;
}

export interface AgentEpisodeRecord {
  id: string;
  agentId: string;
  tenantId: string;
  type: "EPISODIC";
  input?: string;
  output?: string;
  outcome?: string;
  latencyMs?: number;
  timestamp: string;
  [key: string]: unknown;
}

export interface AgentFact {
  id: string;
  agentId: string;
  tenantId: string;
  type: "SEMANTIC";
  subject?: string;
  predicate?: string;
  object?: string;
  confidence: number;
  validityStatus?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface AgentPolicyRecord {
  id: string;
  agentId: string;
  tenantId: string;
  type: "PROCEDURAL";
  name?: string;
  description?: string;
  confidence?: number;
  status?: string;
  episodeCount?: number;
  createdAt?: string;
  [key: string]: unknown;
}

export async function getAgentEpisodes(
  agentId: string,
  tenantId = "default",
  limit = 50,
): Promise<AgentEpisodeRecord[]> {
  const { data } = await client.get<AgentEpisodesResponse>(
    `/api/v1/agents/${agentId}/memory/episodes`,
    {
      params: { tenantId, limit },
    },
  );
  return data.episodes ?? [];
}

export async function getAgentFacts(
  agentId: string,
  tenantId = "default",
  limit = 100,
): Promise<AgentFact[]> {
  const { data } = await client.get<AgentFactsResponse>(
    `/api/v1/agents/${agentId}/memory/facts`,
    {
      params: { tenantId, limit },
    },
  );
  return data.facts ?? [];
}

export async function getAgentPolicies(
  agentId: string,
  tenantId = "default",
  limit = 50,
): Promise<AgentPolicyRecord[]> {
  const { data } = await client.get<AgentPoliciesResponse>(
    `/api/v1/agents/${agentId}/memory/policies`,
    {
      params: { tenantId, limit },
    },
  );
  return data.policies ?? [];
}

export async function deregisterAgent(
  agentId: string,
  tenantId = "default",
): Promise<void> {
  await client.delete(`/api/v1/agents/${agentId}`, { params: { tenantId } });
}

// ─── Monitoring ──────────────────────────────────────────────────────

export async function listPipelineRuns(
  tenantId = "default",
  limit = 20,
): Promise<PipelineRun[]> {
  const { data } = await client.get<PipelineRunsResponse>("/api/v1/runs", {
    params: { tenantId, limit },
  });
  return (data.runs ?? []).map((run: PipelineRunWire) =>
    normalizePipelineRun(run),
  );
}

export async function getRuntimeDurabilityStatus(): Promise<RuntimeDurabilityStatus> {
  const { data } = await client.get<DeepHealthResponse>("/health/deep");
  return deriveRuntimeDurabilityStatus(data);
}

export async function getPipelineMetrics(
  tenantId = "default",
): Promise<PipelineMetrics[]> {
  const { data } = await client.get<PipelineMetricsResponse>(
    "/api/v1/metrics/pipelines",
    {
      params: { tenantId },
    },
  );
  return data.metrics ?? [];
}

export async function cancelRun(
  runId: string,
  tenantId = "default",
): Promise<void> {
  await client.post(`/api/v1/runs/${runId}/cancel`, null, {
    params: { tenantId },
  });
}

export async function markAnomalyFalsePositive(
  anomalyId: string,
  input: {
    reviewer?: string;
    rationale: string;
    notes?: string;
  },
  tenantId = "default",
): Promise<MarkAnomalyFalsePositiveResult> {
  const { data } = await client.post<MarkAnomalyFalsePositiveResult>(
    `/api/v1/analytics/anomalies/${anomalyId}/false-positive`,
    input,
    { params: { tenantId } },
  );
  return data;
}

export async function getRunDetail(
  runId: string,
  tenantId = "default",
): Promise<PipelineRunDetail> {
  const { data } = await client.get<PipelineRunWire & Partial<PipelineRunDetail>>(`/api/v1/runs/${runId}`, {
    params: { tenantId },
  });
  const lineage = data.lineage ?? [];
  const policies = data.policies ?? [];
  return {
    ...normalizePipelineRun(data),
    lineage,
    decisions: data.decisions ?? [],
    policies,
    provenance: normalizeRunProvenance((data as Partial<PipelineRunDetail>).provenance, lineage, policies),
  };
}

export async function getGovernanceComplianceSummary(
  tenantId = "default",
): Promise<GovernanceComplianceSummary> {
  const { data } = await client.get<GovernanceComplianceSummary>(
    "/api/v1/governance/compliance/summary",
    {
      params: { tenantId },
    },
  );
  return data;
}

export async function getGovernanceAuditSummary(
  tenantId = "default",
  limit = 20,
): Promise<GovernanceAuditSummary> {
  const { data } = await client.get<GovernanceAuditSummary>(
    "/api/v1/governance/audit/summary",
    {
      params: { tenantId, limit },
    },
  );
  return data;
}

export async function getGovernanceTenancySummary(
  tenantId = "default",
): Promise<GovernanceTenancySummary> {
  const [{ data: killSwitch }, { data: degradation }] = await Promise.all([
    client.get<GovernanceKillSwitchResponse>("/api/v1/governance/kill-switch", {
      params: { tenantId },
    }),
    client.get<GovernanceDegradationResponse>("/api/v1/governance/degradation", {
      params: { tenantId },
    }),
  ]);

  return {
    tenantId,
    active: killSwitch.active,
    globalActive: killSwitch.globalActive,
    mode: degradation.mode,
  };
}

export async function getGovernanceKillSwitch(
  tenantId = "default",
): Promise<GovernanceKillSwitchSummary> {
  const { data } = await client.get<GovernanceKillSwitchResponse>("/api/v1/governance/kill-switch", {
    params: { tenantId },
  });
  return data;
}

export async function activateGovernanceKillSwitch(
  input: {
    tenantId: string;
    reason: string;
    incidentId: string;
    mfaCode?: string;
    userId?: string;
  },
): Promise<GovernanceKillSwitchMutationResult> {
  const { data } = await client.post<GovernanceKillSwitchMutationResult>(
    "/api/v1/governance/kill-switch/activate",
    input,
  );
  return data;
}

export async function deactivateGovernanceKillSwitch(
  input: {
    tenantId: string;
    reason: string;
    userId?: string;
  },
): Promise<GovernanceKillSwitchMutationResult> {
  const { data } = await client.post<GovernanceKillSwitchMutationResult>(
    "/api/v1/governance/kill-switch/deactivate",
    input,
  );
  return data;
}

export async function listConsentDecisions(
  tenantId = "default",
  limit = 100,
  offset = 0,
): Promise<ConsentDecisionSummary> {
  const { data } = await client.get<ConsentDecisionSummary>("/api/v1/consent", {
    params: { tenantId, limit, offset },
  });
  return {
    tenantId: data.tenantId ?? tenantId,
    count: data.count ?? 0,
    items: data.items ?? [],
  };
}

export async function getGovernanceOpsSummary(
  tenantId = "default",
): Promise<GovernanceOpsSummary> {
  const { data } = await client.get<GovernanceOpsSummary>("/api/v1/governance/ops/summary", {
    params: { tenantId },
  });
  return data;
}

export async function listMarketplaceAgents(
  tenantId = "default",
  search?: string,
  capability?: string,
): Promise<MarketplaceAgentListing[]> {
  const { data } = await client.get<MarketplaceAgentsResponse>(
    "/api/v1/catalog/marketplace/agents",
    {
      params: { tenantId, search, capability },
    },
  );
  return data.agents ?? [];
}

export async function getMarketplaceAgent(
  agentId: string,
  tenantId = "default",
): Promise<MarketplaceAgentDetail> {
  const { data } = await client.get<MarketplaceAgentResponse>(
    `/api/v1/catalog/marketplace/agents/${agentId}`,
    {
      params: { tenantId },
    },
  );
  return {
    listing: data.listing,
    reviews: data.reviews ?? [],
  };
}

export async function publishMarketplaceAgent(
  input: PublishMarketplaceAgentInput,
  tenantId = "default",
): Promise<MarketplaceAgentListing> {
  const { data } = await client.post<{ agent: MarketplaceAgentListing }>(
    "/api/v1/catalog/marketplace/agents",
    {
      ...input,
      tenantId,
    },
  );
  return data.agent;
}

export async function listMarketplaceReviews(
  agentId: string,
  tenantId = "default",
): Promise<MarketplaceReview[]> {
  const { data } = await client.get<MarketplaceReviewsResponse>(
    `/api/v1/catalog/marketplace/agents/${agentId}/reviews`,
    {
      params: { tenantId },
    },
  );
  return data.reviews ?? [];
}

export async function createMarketplaceReview(
  agentId: string,
  input: CreateMarketplaceReviewInput,
  tenantId = "default",
): Promise<MarketplaceReview> {
  const { data } = await client.post<{ review: MarketplaceReview }>(
    `/api/v1/catalog/marketplace/agents/${agentId}/reviews`,
    {
      ...input,
      tenantId,
    },
  );
  return data.review;
}

export async function simulateMarketplaceInstall(
  agentId: string,
  input: MarketplaceInstallInput,
  tenantId = "default",
): Promise<MarketplaceInstallSimulation> {
  const { data } = await client.post<MarketplaceInstallSimulationResponse>(
    `/api/v1/catalog/marketplace/agents/${agentId}/simulate-install`,
    {
      ...input,
      tenantId,
    },
  );
  return data.simulation;
}

export async function installMarketplaceAgent(
  agentId: string,
  input: MarketplaceInstallInput,
  tenantId = "default",
): Promise<MarketplaceInstallResult> {
  const { data } = await client.post<MarketplaceInstallResult>(
    `/api/v1/catalog/marketplace/agents/${agentId}/install`,
    {
      ...input,
      tenantId,
    },
  );
  return data;
}

export async function getCostSummary(
  tenantId = "default",
  options: {
    dailyBudgetUsd?: number;
    monthlyBudgetUsd?: number;
    from?: string;
    to?: string;
  } = {},
): Promise<CostSummary> {
  const { data } = await client.get<CostSummaryResponse>("/api/v1/costs/summary", {
    params: { tenantId, ...options },
  });
  return { ...data.summary, estimated: data.estimated ?? false };
}

export async function requestGdprAccess(
  subjectId: string,
  tenantId = "default",
): Promise<ComplianceReport> {
  const { data } = await client.post<ComplianceReport>("/api/v1/compliance/gdpr/access", {
    tenantId,
    subjectId,
  });
  return data;
}

export async function requestGdprErasure(
  subjectId: string,
  tenantId = "default",
): Promise<ComplianceReport> {
  const { data } = await client.post<ComplianceReport>("/api/v1/compliance/gdpr/erasure", {
    tenantId,
    subjectId,
  });
  return data;
}

export async function requestGdprPortability(
  subjectId: string,
  tenantId = "default",
): Promise<PrivacyPortabilityExport> {
  const { data } = await client.post<PrivacyPortabilityExport>("/api/v1/compliance/gdpr/portability", {
    tenantId,
    subjectId,
  });
  return data;
}

export async function requestCcpaOptOut(
  consumerId: string,
  tenantId = "default",
): Promise<ComplianceReport> {
  const { data } = await client.post<ComplianceReport>("/api/v1/compliance/ccpa/opt-out", {
    tenantId,
    consumerId,
  });
  return data;
}

// ─── HITL Queue ──────────────────────────────────────────────────────

export async function listPendingReviews(
  tenantId = "default",
): Promise<ReviewItem[]> {
  const { data } = await client.get<PendingReviewsResponse>(
    "/api/v1/hitl/pending",
    {
      params: { tenantId },
    },
  );
  return data.items ?? [];
}

export async function approveReview(
  reviewId: string,
  options: { note?: string; tenantId?: string } = {},
): Promise<ReviewItem> {
  const { note = "", tenantId = "default" } = options;
  const { data } = await client.post<ReviewItem>(
    `/api/v1/hitl/${reviewId}/approve`,
    { note },
    { params: { tenantId } },
  );
  return data;
}

export async function rejectReview(
  reviewId: string,
  options: { reason?: string; tenantId?: string } = {},
): Promise<ReviewItem> {
  const { reason = "", tenantId = "default" } = options;
  const { data } = await client.post<ReviewItem>(
    `/api/v1/hitl/${reviewId}/reject`,
    { reason },
    { params: { tenantId } },
  );
  return data;
}

// ─── Learning ────────────────────────────────────────────────────────

export async function listEpisodes(
  tenantId = "default",
  limit = 50,
): Promise<EpisodeRecord[]> {
  const { data } = await client.get<EpisodesResponse>(
    "/api/v1/learning/episodes",
    {
      params: { tenantId, limit },
    },
  );
  return data.episodes ?? [];
}

export async function listPolicies(
  tenantId = "default",
): Promise<LearnedPolicy[]> {
  const { data } = await client.get<PoliciesResponse>(
    "/api/v1/learning/policies",
    {
      params: { tenantId },
    },
  );
  return (data.policies ?? []).map((policy) => normalizeLearnedPolicy(policy, tenantId));
}

export async function approvePolicy(
  policyId: string,
  tenantId = "default",
): Promise<LearnedPolicy> {
  const { data } = await client.post<LearnedPolicy>(
    `/api/v1/learning/policies/${policyId}/approve`,
    null,
    {
      params: { tenantId },
    },
  );
  return normalizeLearnedPolicy(data, tenantId);
}

export async function rejectPolicy(
  policyId: string,
  reason: string,
  tenantId = "default",
): Promise<LearnedPolicy> {
  const { data } = await client.post<LearnedPolicy>(
    `/api/v1/learning/policies/${policyId}/reject`,
    { reason },
    { params: { tenantId } },
  );
  return normalizeLearnedPolicy(data, tenantId);
}

export async function triggerReflection(
  tenantId = "default",
): Promise<{ triggered: boolean }> {
  const { data } = await client.post<ReflectionResponse>(
    "/api/v1/learning/reflect",
    null,
    {
      params: { tenantId },
    },
  );
  return data;
}

// ─── Workflow Templates ───────────────────────────────────────────────

export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  operatorCount: number;
  version: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  /** YAML or JSON representation of the pipeline template */
  templateBody?: string;
}

export interface WorkflowTemplateVersion {
  version: string;
  createdAt: string;
  changelog: string;
}

export async function listWorkflowTemplates(
  tenantId = "default",
): Promise<WorkflowTemplate[]> {
  const { data } = await client.get<WorkflowTemplatesResponse>(
    "/api/v1/workflows/templates",
    {
      params: { tenantId },
    },
  );
  return data.templates ?? [];
}

export async function getWorkflowTemplate(
  templateId: string,
  tenantId = "default",
): Promise<WorkflowTemplate> {
  const { data } = await client.get<WorkflowTemplate>(
    `/api/v1/workflows/templates/${templateId}`,
    {
      params: { tenantId },
    },
  );
  return data;
}

export async function getWorkflowTemplateVersions(
  templateId: string,
  tenantId = "default",
): Promise<WorkflowTemplateVersion[]> {
  const { data } = await client.get<WorkflowTemplateVersionsResponse>(
    `/api/v1/workflows/templates/${templateId}/versions`,
    {
      params: { tenantId },
    },
  );
  return data.versions ?? [];
}

export async function instantiateTemplate(
  templateId: string,
  tenantId = "default",
): Promise<{ pipelineId: string }> {
  const { data } = await client.post<InstantiateTemplateResponse>(
    `/api/v1/workflows/templates/${templateId}/instantiate`,
    null,
    { params: { tenantId } },
  );
  return data;
}

// ─── Operations ───────────────────────────────────────────────────────────

export type OperationStatus = "pending" | "running" | "completed" | "failed" | "cancelled";

export interface OperationRecord {
  id: string;
  type: string;
  status: OperationStatus;
  startedAt: string;
  finishedAt?: string;
  attempts: number;
  maxAttempts: number;
  resourceType?: "pipeline" | "run" | "agent" | "policy";
  resourceId?: string;
  errorMessage?: string;
  auditEntryId?: string;
  initiatedBy?: string;
}

interface OperationsResponse {
  operations?: OperationRecord[];
}

export async function listOperations(
  tenantId = "default",
  limit = 50,
): Promise<OperationRecord[]> {
  const { data } = await client.get<OperationsResponse>("/api/v1/operations", {
    params: { tenantId, limit },
  });
  return data.operations ?? [];
}

export async function retryOperation(
  operationId: string,
  tenantId = "default",
): Promise<{ retried: boolean; operationId: string }> {
  const { data } = await client.post<{ retried: boolean; operationId: string }>(
    `/api/v1/operations/${operationId}/retry`,
    null,
    { params: { tenantId } },
  );
  return data;
}

export async function cancelOperation(
  operationId: string,
  tenantId = "default",
): Promise<{ cancelled: boolean; operationId: string }> {
  const { data } = await client.post<{ cancelled: boolean; operationId: string }>(
    `/api/v1/operations/${operationId}/cancel`,
    null,
    { params: { tenantId } },
  );
  return data;
}

// ─── NLQ (Natural Language Query) ─────────────────────────────────────────────

export type NlqIntent =
  | "list_runs"
  | "list_pipelines"
  | "list_agents"
  | "list_anomalies"
  | "filter_failed"
  | "filter_running"
  | "filter_success"
  | "time_window"
  | "trigger_reflect"
  | "kill_switch"
  | "status_query"
  | "search"
  | "unknown";

export interface NlqEntity {
  type: "time_window" | "pipeline_name" | "status" | "keyword";
  value?: string;
  confidence?: number;
  amount?: number;
  unit?: string;
  iso8601?: string;
}

export interface NlqParseResult {
  intent: NlqIntent;
  confidence: number;
  entities: NlqEntity[];
  query: string;
  tenantId: string;
  timestamp: string;
}

/**
 * Parses a natural-language query into a structured intent and entity set.
 *
 * @param query    free-text operator query (e.g. "show me failing pipelines last hour")
 * @param tenantId tenant context for the query
 */
export async function parseNlQuery(
  query: string,
  tenantId = "default",
  endpoint = "/api/v1/nlp/parse",
): Promise<NlqParseResult> {
  const { data } = await client.post<NlqParseResult>(
    endpoint,
    { query, tenantId },
  );
  return data;
}
