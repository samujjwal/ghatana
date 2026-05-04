/**
 * Strategy domain types for DMOS.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for strategy domain objects
 * @doc.layer frontend
 */

export interface StrategyGoal {
  goalType: string;
  description: string;
  targetMetric: string;
  measurementMethod: string;
}

export interface CampaignPlan {
  channelType: string;
  objective: string;
  estimatedBudget: number;
  keyMessages: string[];
  targetKeywords: string[];
}

export interface MarketingStrategy {
  strategyId: string;
  workspaceId: string;
  status: string;
  goals: StrategyGoal[];
  channelPlans: CampaignPlan[];
  budgetCap: number;
  rationale: string;
  assumptions: string;
  measurementPlan: string;
  contentPlan: string;
  modelVersion: string;
  generatedAt: string;
  generatedBy: string;
  approvedAt: string | null;
  approvedBy: string | null;
}

export interface GenerateStrategyRequest {
  intakeCompletionPct: number;
  serviceArea: string;
  monthlyBudget: number;
  auditFindingCount: number;
  trackingGapsDetected: boolean;
  keywordOpportunityCount: number;
  topCompetitorCount: number;
  primaryOffer: string;
}
