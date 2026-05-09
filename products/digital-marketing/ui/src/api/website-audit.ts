import { apiRequest } from '@/lib/http-client';

export interface WebsiteAuditFinding {
  severity: string;
  category: string;
  evidence: string;
  rationale: string;
  recommendedAction: string;
  sourceUrl: string;
}

export interface WebsiteAuditReport {
  reportId: string;
  workspaceId: string;
  websiteUrl: string;
  findings: WebsiteAuditFinding[];
  generatedAt: string;
  generatedBy: string;
}

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/audit/latest`;
}

export async function getLatestWebsiteAudit(workspaceId: string): Promise<WebsiteAuditReport> {
  return apiRequest<WebsiteAuditReport>(base(workspaceId));
}
