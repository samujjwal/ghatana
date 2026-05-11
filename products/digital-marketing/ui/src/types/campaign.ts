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
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'PENDING_LAUNCH'
  | 'LAUNCH_RUNNING'
  | 'LAUNCH_FAILED'
  | 'EXTERNAL_EXECUTION_BLOCKED'
  | 'LAUNCHED'
  | 'PAUSED'
  | 'COMPLETED'
  | 'ARCHIVED'
  | 'ROLLED_BACK';

export interface Campaign {
  id: string;
  workspaceId: string;
  name: string;
  status: CampaignStatus;
  type: CampaignType;
  /** Objective / goal of the campaign (e.g. AWARENESS, LEADS, CONVERSIONS) */
  objective?: string | null;
  /** Target monthly or total budget in minor currency units (e.g. cents) */
  budgetCents?: number | null;
  /** ISO 8601 date string for campaign start */
  startDate?: string | null;
  /** ISO 8601 date string for campaign end */
  endDate?: string | null;
  /** Audience segment identifier or free-form description */
  audience?: string | null;
  /** Landing page URL for the campaign */
  landingPageUrl?: string | null;
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
  /** Total count of campaigns across all pages (not just current page) */
  count: number;
  offset: number;
}
