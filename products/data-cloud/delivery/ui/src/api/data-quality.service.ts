import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';
import SessionBootstrap from '../lib/auth/session';
import {
  DataQualityTrustScoresResponseSchema,
  type DataQualityTrustScores,
} from '../contracts/schemas';

export type DataQualityTrustScoresResult = DataQualityTrustScores;

export async function getDataQualityTrustScores(): Promise<DataQualityTrustScoresResult> {
  const tenantId = SessionBootstrap.requireTenantId();
  const rawResponse = await apiClient.get<unknown>('/data-quality/trust-scores', {
    headers: { 'X-Tenant-ID': tenantId },
  });
  return DataQualityTrustScoresResponseSchema.parse(rawResponse).data;
}

export function useDataQualityTrustScores() {
  return useQuery({
    queryKey: ['data-quality-trust-scores', SessionBootstrap.getTenantId() ?? 'missing-tenant'],
    queryFn: getDataQualityTrustScores,
    staleTime: 30_000,
    retry: false,
  });
}
