/**
 * Campaign domain types for DMOS.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for campaign domain objects
 * @doc.layer frontend
 */

export type CampaignType = 'EMAIL' | 'SOCIAL' | 'PAID_SEARCH' | 'PUSH' | 'SMS' | 'OMNICHANNEL';

export type CampaignStatus = 'DRAFT' | 'LAUNCHED' | 'PAUSED';

export interface Campaign {
  id: string;
  workspaceId: string;
  name: string;
  status: CampaignStatus;
  type: CampaignType;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCampaignRequest {
  name: string;
  type: CampaignType;
}
