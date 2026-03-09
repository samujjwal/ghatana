import { useQuery } from '@tanstack/react-query';
import { diagnosticsClient } from '../services/mockClient';

export const DIAGNOSTICS_QUERY_KEY = ['diagnostics', 'snapshot'] as const;

export const useDiagnosticsSnapshot = () =>
  useQuery({
    queryKey: DIAGNOSTICS_QUERY_KEY,
    queryFn: diagnosticsClient.fetchDiagnostics,
    staleTime: 30_000,
  });
