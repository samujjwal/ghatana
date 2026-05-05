/**
 * Campaign API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS campaign HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type { Campaign, CreateCampaignRequest, CampaignListResponse } from '@/types/campaign';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/campaigns`;
}

/**
 * P0-001: List campaigns with pagination support.
 *
 * @param workspaceId the workspace scope
 * @param limit maximum number of results (default 20, max 100)
 * @param offset pagination offset (default 0)
 * @returns paginated campaign list response
 */
export async function listCampaigns(
  workspaceId: string,
  limit: number = 20,
  offset: number = 0,
): Promise<CampaignListResponse> {
  const params = new URLSearchParams();
  params.set('limit', String(Math.min(Math.max(limit, 1), 100)));
  params.set('offset', String(Math.max(offset, 0)));

  return apiRequest<CampaignListResponse>(`${base(workspaceId)}?${params.toString()}`);
}

export async function createCampaign(
  workspaceId: string,
  body: CreateCampaignRequest,
  idempotencyKey?: string,
): Promise<Campaign> {
  return apiRequest<Campaign>(base(workspaceId), {
    method: 'POST',
    body,
    idempotencyKey,
  });
}

export async function getCampaign(
  workspaceId: string,
  campaignId: string,
): Promise<Campaign> {
  return apiRequest<Campaign>(`${base(workspaceId)}/${encodeURIComponent(campaignId)}`);
}

export async function launchCampaign(
  workspaceId: string,
  campaignId: string,
  idempotencyKey?: string,
): Promise<Campaign> {
  return apiRequest<Campaign>(
    `${base(workspaceId)}/${encodeURIComponent(campaignId)}/launch`,
    { method: 'POST', idempotencyKey },
  );
}

export async function pauseCampaign(
  workspaceId: string,
  campaignId: string,
  idempotencyKey?: string,
): Promise<Campaign> {
  return apiRequest<Campaign>(
    `${base(workspaceId)}/${encodeURIComponent(campaignId)}/pause`,
    { method: 'POST', idempotencyKey },
  );
}
