/**
 * A/B Testing API Client
 *
 * Operator-level API for the ABTestingEvaluationService: list experiments,
 * register new variants, view metrics, and promote a winner.
 *
 * @doc.type service
 * @doc.purpose A/B testing operator API client
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
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api/v1`
  : '/api/v1';

// ── Types ──────────────────────────────────────────────────────────────────────

export type ExperimentStatus = 'running' | 'completed' | 'paused';

export interface VariantMetrics {
  variantId: string;
  variantName: string;
  impressions: number;
  conversions: number;
  conversionRate: number;
  avgResponseTimeMs: number;
  avgCostUsd: number;
  avgQualityScore: number;
  statisticalSignificance: boolean;
  pValue?: number;
  confidenceInterval?: [number, number];
}

export interface Experiment {
  id: string;
  name: string;
  description: string;
  status: ExperimentStatus;
  promptName: string;
  createdAt: string;
  endedAt?: string;
  variants: VariantMetrics[];
  winnerId?: string;
}

export interface ExperimentListResponse {
  items: Experiment[];
  total: number;
}

export interface CreateVariantRequest {
  experimentName: string;
  description: string;
  promptName: string;
  variantA: string;
  variantB: string;
}

export interface PromoteWinnerRequest {
  variantId: string;
  reason: string;
}

class ABTestingApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = 'ABTestingApiError';
  }
}

async function handleErrorResponse(response: Response): Promise<never> {
  const raw = await response.text();
  let detail = raw.trim();
  try {
    const parsed = JSON.parse(raw) as { detail?: string; title?: string; message?: string };
    detail = parsed.detail ?? parsed.title ?? parsed.message ?? detail;
  } catch {
    // raw text fallback
  }
  throw new ABTestingApiError(
    detail || `HTTP ${response.status}`,
    response.status
  );
}

// ── API Functions ──────────────────────────────────────────────────────────────

export async function listExperiments(
  status?: ExperimentStatus
): Promise<ExperimentListResponse> {
  const url = new URL(`${API_BASE_URL}/admin/ab-experiments`, window.location.origin);
  if (status) {
    url.searchParams.set('status', status);
  }
  const response = await fetch(url.toString(), {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    await handleErrorResponse(response);
  }
  return parseJsonResponse<ExperimentListResponse>(response, 'list experiments');
}

export async function createExperiment(
  req: CreateVariantRequest
): Promise<Experiment> {
  const response = await fetch(`${API_BASE_URL}/admin/ab-experiments`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    await handleErrorResponse(response);
  }
  return parseJsonResponse<Experiment>(response, 'create experiment');
}

export async function promoteWinner(
  experimentId: string,
  variantId: string,
  reason: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/admin/ab-experiments/${encodeURIComponent(experimentId)}/promote`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ variantId, reason } satisfies PromoteWinnerRequest),
    }
  );
  if (!response.ok) {
    await handleErrorResponse(response);
  }
}

export async function pauseExperiment(experimentId: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/admin/ab-experiments/${encodeURIComponent(experimentId)}/pause`,
    { method: 'POST', headers: { Accept: 'application/json' } }
  );
  if (!response.ok) {
    await handleErrorResponse(response);
  }
}
