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
import axios, { type AxiosInstance } from 'axios';

const BASE_URL = import.meta.env.VITE_AEP_API_URL ?? 'http://localhost:8081';

const client: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

// ─── Types ───────────────────────────────────────────────────────────

export type AgentStatus = 'ACTIVE' | 'IDLE' | 'ERROR' | 'UNKNOWN';
export type ReviewItemStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type PolicyStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'ACTIVE' | 'DEPRECATED';
export type EpisodeOutcome = 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'CANCELLED';

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
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
  startedAt: string;
  finishedAt?: string;
  eventsProcessed: number;
  errorsCount: number;
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
  itemType: 'POLICY' | 'PATTERN' | 'AGENT_DECISION';
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

// ─── Agent Registry ──────────────────────────────────────────────────

export async function listAgents(tenantId = 'default'): Promise<AgentRegistration[]> {
  const { data } = await client.get('/api/v1/agents', { params: { tenantId } });
  return data.agents ?? [];
}

export async function getAgent(agentId: string, tenantId = 'default'): Promise<AgentRegistration> {
  const { data } = await client.get(`/api/v1/agents/${agentId}`, { params: { tenantId } });
  return data;
}

export async function deregisterAgent(agentId: string, tenantId = 'default'): Promise<void> {
  await client.delete(`/api/v1/agents/${agentId}`, { params: { tenantId } });
}

// ─── Monitoring ──────────────────────────────────────────────────────

export async function listPipelineRuns(
  tenantId = 'default',
  limit = 20,
): Promise<PipelineRun[]> {
  const { data } = await client.get('/api/v1/runs', { params: { tenantId, limit } });
  return data.runs ?? [];
}

export async function getPipelineMetrics(tenantId = 'default'): Promise<PipelineMetrics[]> {
  const { data } = await client.get('/api/v1/metrics/pipelines', { params: { tenantId } });
  return data.metrics ?? [];
}

export async function cancelRun(runId: string, tenantId = 'default'): Promise<void> {
  await client.post(`/api/v1/runs/${runId}/cancel`, null, { params: { tenantId } });
}

// ─── HITL Queue ──────────────────────────────────────────────────────

export async function listPendingReviews(tenantId = 'default'): Promise<ReviewItem[]> {
  const { data } = await client.get('/api/v1/hitl/pending', { params: { tenantId } });
  return data.items ?? [];
}

export async function approveReview(
  reviewId: string,
  options: { note?: string; tenantId?: string } = {},
): Promise<ReviewItem> {
  const { note = '', tenantId = 'default' } = options;
  const { data } = await client.post(
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
  const { reason = '', tenantId = 'default' } = options;
  const { data } = await client.post(
    `/api/v1/hitl/${reviewId}/reject`,
    { reason },
    { params: { tenantId } },
  );
  return data;
}

// ─── Learning ────────────────────────────────────────────────────────

export async function listEpisodes(
  tenantId = 'default',
  limit = 50,
): Promise<EpisodeRecord[]> {
  const { data } = await client.get('/api/v1/learning/episodes', { params: { tenantId, limit } });
  return data.episodes ?? [];
}

export async function listPolicies(tenantId = 'default'): Promise<LearnedPolicy[]> {
  const { data } = await client.get('/api/v1/learning/policies', { params: { tenantId } });
  return data.policies ?? [];
}

export async function approvePolicy(
  policyId: string,
  tenantId = 'default',
): Promise<LearnedPolicy> {
  const { data } = await client.post(`/api/v1/learning/policies/${policyId}/approve`, null, {
    params: { tenantId },
  });
  return data;
}

export async function rejectPolicy(
  policyId: string,
  reason: string,
  tenantId = 'default',
): Promise<LearnedPolicy> {
  const { data } = await client.post(
    `/api/v1/learning/policies/${policyId}/reject`,
    { reason },
    { params: { tenantId } },
  );
  return data;
}

export async function triggerReflection(tenantId = 'default'): Promise<{ triggered: boolean }> {
  const { data } = await client.post('/api/v1/learning/reflect', null, { params: { tenantId } });
  return data;
}
