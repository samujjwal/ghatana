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
  status: AgentStatus;
  capabilities: string[];
  memoryCount: number;
  lastSeen?: string;
  registeredAt: string;
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

export interface CostBreakdown {
  id: string;
  name: string;
  costUsd: number;
  sharePercent: number;
  runCount: number;
  lastSeenAt?: string;
}

export interface CostAlert {
  id: string;
  severity: "info" | "warning" | "critical";
  title: string;
  description: string;
  currentValue: number;
  thresholdValue: number;
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
  alerts: CostAlert[];
  dataSource: string;
  allocationModel: string;
}

interface AgentListResponse {
  agents?: AgentRegistration[];
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
  policies?: LearnedPolicy[];
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

interface MarketplaceReviewsResponse {
  reviews?: MarketplaceReview[];
}

interface CostSummaryResponse {
  summary: CostSummary;
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
  return data.agents ?? [];
}

export async function getAgent(
  agentId: string,
  tenantId = "default",
): Promise<AgentRegistration> {
  const { data } = await client.get<AgentRegistration>(
    `/api/v1/agents/${agentId}`,
    {
      params: { tenantId },
    },
  );
  return data;
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

export async function getRunDetail(
  runId: string,
  tenantId = "default",
): Promise<PipelineRun> {
  const { data } = await client.get<PipelineRunWire>(`/api/v1/runs/${runId}`, {
    params: { tenantId },
  });
  return normalizePipelineRun(data);
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

export async function getCostSummary(
  tenantId = "default",
): Promise<CostSummary> {
  const { data } = await client.get<CostSummaryResponse>("/api/v1/costs/summary", {
    params: { tenantId },
  });
  return data.summary;
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
  return data.policies ?? [];
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
  return data;
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
  return data;
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
