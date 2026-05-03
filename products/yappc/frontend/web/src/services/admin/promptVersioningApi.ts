/**
 * Prompt Versioning API Client
 *
 * Admin-only API for managing prompt versions: listing, rollback, and weight rebalancing.
 * Used by PromptVersionsPage to replace mock data with real backend calls.
 *
 * @doc.type service
 * @doc.purpose Admin prompt version management API client
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

export interface PromptVersion {
  id: string;
  promptName: string;
  content: string;
  contentHash: string;
  description: string;
  author: string;
  active: boolean;
  weight: number;
  createdAt: string;
  metrics?: {
    avgCost: number;
    avgLatencyMs: number;
    successRate: number;
    sampleCount: number;
  };
}

export interface PromptVersionListResponse {
  items: PromptVersion[];
  total: number;
}

export interface RollbackRequest {
  reason: string;
}

export interface WeightUpdateRequest {
  weights: Record<string, number>; // promptVersionId → weight (0.0–1.0, must sum to 1)
}

class PromptVersioningApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = 'PromptVersioningApiError';
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
  throw new PromptVersioningApiError(
    detail || `HTTP ${response.status}`,
    response.status
  );
}

// ── API Functions ──────────────────────────────────────────────────────────────

export async function listPromptVersions(
  promptName?: string
): Promise<PromptVersionListResponse> {
  const url = new URL(`${API_BASE_URL}/admin/prompt-versions`, window.location.origin);
  if (promptName) {
    url.searchParams.set('promptName', promptName);
  }
  const response = await fetch(url.toString(), {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    await handleErrorResponse(response);
  }
  return parseJsonResponse<PromptVersionListResponse>(response, 'list prompt versions');
}

export async function rollbackPromptVersion(
  versionId: string,
  reason: string
): Promise<PromptVersion> {
  const response = await fetch(
    `${API_BASE_URL}/admin/prompt-versions/${encodeURIComponent(versionId)}/rollback`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ reason } satisfies RollbackRequest),
    }
  );
  if (!response.ok) {
    await handleErrorResponse(response);
  }
  return parseJsonResponse<PromptVersion>(response, 'rollback prompt version');
}

export async function updatePromptWeights(
  weights: Record<string, number>
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/admin/prompt-versions/weights`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ weights } satisfies WeightUpdateRequest),
  });
  if (!response.ok) {
    await handleErrorResponse(response);
  }
}
