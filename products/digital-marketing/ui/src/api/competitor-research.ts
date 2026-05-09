import { apiRequest } from '@/lib/http-client';

export interface CompetitorFinding {
  competitorDomain: string;
  observedFact: string;
  interpretation: string;
  isInferred: boolean;
  source: string;
}

export interface KeywordFinding {
  keyword: string;
  intent: string;
  relevanceScore: number;
  suggestedCampaignUse: string;
  evidence: string;
  source: string;
}

export interface CompetitorResearchSnapshot {
  snapshotId: string;
  workspaceId: string;
  competitorFindings: CompetitorFinding[];
  keywordFindings: KeywordFinding[];
  opportunitySummary: string;
  generatedAt: string;
  generatedBy: string;
}

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/research/competitor`;
}

export async function getLatestCompetitorResearch(workspaceId: string): Promise<CompetitorResearchSnapshot> {
  return apiRequest<CompetitorResearchSnapshot>(base(workspaceId));
}
