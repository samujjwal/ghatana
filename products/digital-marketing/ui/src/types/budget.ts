/**
 * Budget recommendation domain types for DMOS.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for budget recommendation domain objects
 * @doc.layer frontend
 */

export interface ChannelAllocation {
  channelType: string;
  recommendedAmount: number;
  dailyCap: number;
  rationale: string;
}

export interface BudgetRecommendation {
  recommendationId: string;
  workspaceId: string;
  strategyId: string;
  status: string;
  totalMonthlyCap: number;
  changeThresholdPct: number;
  channelAllocations: ChannelAllocation[];
  rationale: string;
  assumptions: string;
  modelVersion: string;
  generatedAt: string;
  generatedBy: string;
  approvedAt: string | null;
  approvedBy: string | null;
}

export interface GenerateBudgetRequest {
  strategyId: string;
  totalMonthlyCap: number;
  changeThreshold: number;
}
