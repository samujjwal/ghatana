/**
 * Strategy API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS strategy HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type { MarketingStrategy, GenerateStrategyRequest } from '@/types/strategy';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/strategy`;
}

export async function generateStrategy(
  workspaceId: string,
  body: GenerateStrategyRequest,
  idempotencyKey?: string,
): Promise<MarketingStrategy> {
  return apiRequest<MarketingStrategy>(base(workspaceId), {
    method: 'POST',
    body,
    idempotencyKey,
  });
}

export async function getLatestStrategy(workspaceId: string): Promise<MarketingStrategy> {
  return apiRequest<MarketingStrategy>(base(workspaceId));
}

export async function submitStrategyForApproval(
  workspaceId: string,
  strategyId: string,
  idempotencyKey?: string,
): Promise<MarketingStrategy> {
  return apiRequest<MarketingStrategy>(
    `${base(workspaceId)}/${encodeURIComponent(strategyId)}/submit`,
    { method: 'POST', idempotencyKey },
  );
}

export async function approveStrategy(
  workspaceId: string,
  strategyId: string,
  idempotencyKey?: string,
): Promise<MarketingStrategy> {
  return apiRequest<MarketingStrategy>(
    `${base(workspaceId)}/${encodeURIComponent(strategyId)}/approve`,
    { method: 'POST', idempotencyKey },
  );
}
