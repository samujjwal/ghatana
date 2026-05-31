import { useQuery } from "@tanstack/react-query";
import {
  AiQualitySummaryResponseSchema,
  type AiQualitySummary,
} from "../contracts/schemas";
import { apiClient } from "../lib/api/client";
import SessionBootstrap from "../lib/auth/session";
import { isAiOperationsEnabled } from "../lib/feature-gates";
import {
  AI_OBSERVABILITY_DISABLED_BOUNDARY_MESSAGE,
  createRuntimeBoundaryError,
} from "../lib/runtime-boundaries";

export type AiQualitySummaryResult = AiQualitySummary;

export async function getAiQualitySummary(): Promise<AiQualitySummaryResult> {
  if (!isAiOperationsEnabled()) {
    throw createRuntimeBoundaryError(
      AI_OBSERVABILITY_DISABLED_BOUNDARY_MESSAGE,
    );
  }
  const tenantId = SessionBootstrap.requireTenantId();
  const response = await apiClient.get<unknown>("/ai/quality-summary", {
    headers: { "X-Tenant-ID": tenantId },
  });
  return AiQualitySummaryResponseSchema.parse(response).data;
}

export function useAiQualitySummary() {
  return useQuery({
    queryKey: [
      "ai-quality-summary",
      SessionBootstrap.getTenantId() ?? "missing-tenant",
    ],
    queryFn: getAiQualitySummary,
    staleTime: 60_000,
    retry: false,
  });
}
