import { useQuery } from '@tanstack/react-query';
import { reportsClient } from '../services/mockClient';

export const REPORTS_QUERY_KEY = ['reports', 'workspace'] as const;

export const useReportsData = () =>
  useQuery({
    queryKey: REPORTS_QUERY_KEY,
    queryFn: reportsClient.fetchReports,
    staleTime: 60_000,
  });

export default useReportsData;

