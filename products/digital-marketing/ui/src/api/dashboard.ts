/**
 * Dashboard API client.
 *
 * @doc.type api-client
 * @doc.purpose Fetch backend-computed dashboard summary metrics
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type { DashboardSummary } from '@/types/dashboard';

export async function getDashboardSummary(workspaceId: string): Promise<DashboardSummary> {
  return apiRequest<DashboardSummary>(
    `/v1/workspaces/${encodeURIComponent(workspaceId)}/dashboard`,
  );
}
