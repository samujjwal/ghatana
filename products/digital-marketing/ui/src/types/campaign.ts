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
  /** Objective / goal of the campaign (e.g. AWARENESS, LEADS, CONVERSIONS) */
  objective?: string;
  /** Target monthly or total budget in minor currency units (e.g. cents) */
  budgetCents?: number;
  /** ISO 8601 date string for campaign start */
  startDate?: string;
  /** ISO 8601 date string for campaign end */
  endDate?: string;
  /** Audience segment identifier or free-form description */
  audience?: string;
  /** Landing page URL for the campaign */
  landingPageUrl?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

/** P1-003: Campaign objectives */
export type CampaignObjective =
  | 'AWARENESS'
  | 'LEADS'
  | 'CONVERSIONS'
  | 'RETENTION'
  | 'ENGAGEMENT'
  | 'TRAFFIC';

export interface CreateCampaignRequest {
  name: string;
  type: CampaignType;
  /** Required: campaign objective */
  objective: CampaignObjective;
  /** Required: budget in cents (e.g. 50000 = $500.00) */
  budgetCents: number;
  /** Required: ISO 8601 campaign start date */
  startDate: string;
  /** Required: ISO 8601 campaign end date */
  endDate: string;
  /** Required: audience segment description or ID */
  audience: string;
  /** Optional: landing page URL */
  landingPageUrl?: string;
}

/** P0-001: Paginated campaign list response */
export interface CampaignListResponse {
  items: Campaign[];
  count: number;
  offset: number;
}
