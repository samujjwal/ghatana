/**
 * Campaign domain types for DMOS.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for campaign domain objects
 * @doc.layer frontend
 */

/** P0-010: Campaign channel types - aligned with backend enum */
export type CampaignType =
  | 'EMAIL'
  | 'SOCIAL'
  | 'PAID_SEARCH'
  | 'PUSH'
  | 'SMS'
  | 'OMNICHANNEL';

/** P0-010: Campaign lifecycle statuses - aligned with backend enum */
export type CampaignStatus =
  | 'DRAFT'
  | 'LAUNCHED'
  | 'PAUSED'
  | 'COMPLETED'
  | 'ARCHIVED';

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

/** P0-001: Paginated campaign list response */
export interface CampaignListResponse {
  items: Campaign[];
  count: number;
  offset: number;
}
