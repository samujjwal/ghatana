import { apiRequest } from '@/lib/http-client';
import type { AiActionLogEntry, ListAiActionLogResponse } from '@/types/ai-action';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/ai-actions`;
}

export async function listAiActions(
  workspaceId: string,
  correlationId?: string,
): Promise<AiActionLogEntry[]> {
  const params = new URLSearchParams();
  if (correlationId) params.set('correlationId', correlationId);
  const suffix = params.toString() ? `?${params.toString()}` : '';
  const response = await apiRequest<ListAiActionLogResponse>(
    `${base(workspaceId)}${suffix}`,
  );
  return response.entries;
}

export async function getAiAction(
  workspaceId: string,
  actionId: string,
): Promise<AiActionLogEntry> {
  return apiRequest<AiActionLogEntry>(
    `${base(workspaceId)}/${encodeURIComponent(actionId)}`,
  );
}
