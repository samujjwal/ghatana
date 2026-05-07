/**
 * Budget recommendation API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS budget recommendation HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type { BudgetRecommendation, GenerateBudgetRequest } from '@/types/budget';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/budget-recommendation`;
}

export async function generateBudgetRecommendation(
  workspaceId: string,
  body: GenerateBudgetRequest,
  idempotencyKey?: string,
): Promise<BudgetRecommendation> {
  return apiRequest<BudgetRecommendation>(base(workspaceId), {
    method: 'POST',
    body,
    idempotencyKey,
  });
}

export async function getLatestBudgetRecommendation(
  workspaceId: string,
): Promise<BudgetRecommendation> {
  return apiRequest<BudgetRecommendation>(base(workspaceId));
}

export async function submitBudgetForApproval(
  workspaceId: string,
  recId: string,
  idempotencyKey?: string,
): Promise<BudgetRecommendation> {
  return apiRequest<BudgetRecommendation>(
    `${base(workspaceId)}/${encodeURIComponent(recId)}/submit`,
    { method: 'POST', idempotencyKey },
  );
}

export async function approveBudgetRecommendation(
  workspaceId: string,
  recId: string,
  idempotencyKey?: string,
  auditComment?: string,
): Promise<BudgetRecommendation> {
  return apiRequest<BudgetRecommendation>(
    `${base(workspaceId)}/${encodeURIComponent(recId)}/approve`,
    { method: 'POST', idempotencyKey, body: auditComment ? { auditComment } : undefined },
  );
}
