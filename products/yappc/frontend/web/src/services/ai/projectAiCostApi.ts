/**
 * Project AI Cost API Client
 *
 * Fetches per-project AI agent run counts and estimated cost from the
 * `/api/projects/:projectId/ai-cost` REST endpoint.
 *
 * @doc.type service
 * @doc.purpose Per-project AI cost data retrieval
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse } from '@/lib/http';

const importMetaEnv = import.meta as ImportMeta & {
  env?: {
    DEV?: boolean;
    VITE_API_ORIGIN?: string;
  };
};

const API_BASE_URL = importMetaEnv.env?.DEV
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface AgentBreakdownEntry {
  count: number;
  estimatedCostUSD: number;
}

export interface ProjectAiCostResponse {
  projectId: string;
  totalRuns: number;
  succeededRuns: number;
  estimatedCostUSD: number;
  currency: string;
  /** 'indicative' until real per-token rates are available from AIMetric */
  costBasis: 'indicative' | 'actual';
  byAgent: Record<string, AgentBreakdownEntry>;
}

// ── API ────────────────────────────────────────────────────────────────────────

export async function fetchProjectAiCost(
  projectId: string
): Promise<ProjectAiCostResponse> {
  const response = await fetch(
    `${API_BASE_URL}/projects/${encodeURIComponent(projectId)}/ai-cost`,
    { credentials: 'include' }
  );
  return parseJsonResponse<ProjectAiCostResponse>(response);
}
