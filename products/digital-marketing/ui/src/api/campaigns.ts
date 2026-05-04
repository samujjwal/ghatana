/**
 * Campaign API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS campaign HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type { Campaign, CreateCampaignRequest } from '@/types/campaign';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/campaigns`;
}

export async function listCampaigns(workspaceId: string): Promise<Campaign[]> {
  return apiRequest<Campaign[]>(base(workspaceId));
}

export async function createCampaign(
  workspaceId: string,
  body: CreateCampaignRequest,
): Promise<Campaign> {
  return apiRequest<Campaign>(base(workspaceId), {
    method: 'POST',
    body,
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
): Promise<Campaign> {
  return apiRequest<Campaign>(
    `${base(workspaceId)}/${encodeURIComponent(campaignId)}/launch`,
    { method: 'POST' },
  );
}

export async function pauseCampaign(
  workspaceId: string,
  campaignId: string,
): Promise<Campaign> {
  return apiRequest<Campaign>(
    `${base(workspaceId)}/${encodeURIComponent(campaignId)}/pause`,
    { method: 'POST' },
  );
}
