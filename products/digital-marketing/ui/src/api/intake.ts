import { apiRequest } from '@/lib/http-client';

export interface IntakeDraft {
  intakeId: string;
  workspaceId: string;
  businessName: string;
  websiteUrl: string;
  offerSummary: string;
  targetAudience: string;
  primaryGeography: string;
  monthlyBudgetAmount: number | null;
  competitorDomains: string[];
  constraints: string[];
  growthGoal: string;
  riskTolerance: string;
  aiSummary: string | null;
  aiConfidenceScore: number;
  aiUnknowns: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
}

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/intake/questionnaire/draft`;
}

export async function getIntakeDraft(workspaceId: string): Promise<IntakeDraft> {
  return apiRequest<IntakeDraft>(base(workspaceId));
}
