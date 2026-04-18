import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';
import SessionBootstrap from '../lib/auth/session';
import {
  AiQualitySummaryResponseSchema,
  type AiQualitySummary,
} from '../contracts/schemas';

export type AiQualitySummaryResult = AiQualitySummary;

export async function getAiQualitySummary(): Promise<AiQualitySummaryResult> {
  const tenantId = SessionBootstrap.requireTenantId();
  const response = await apiClient.get<unknown>('/ai/quality-summary', {
    headers: { 'X-Tenant-ID': tenantId },
  });
  return AiQualitySummaryResponseSchema.parse(response).data;
}

export function useAiQualitySummary() {
  return useQuery({
    queryKey: ['ai-quality-summary', SessionBootstrap.getTenantId() ?? 'missing-tenant'],
    queryFn: getAiQualitySummary,
    staleTime: 60_000,
    retry: false,
  });
}