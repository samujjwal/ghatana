import { useQuery } from '@tanstack/react-query';
import { dashboardClient } from '../services/mockClient';

export const DASHBOARD_QUERY_KEY = ['dashboard-overview'] as const;

export const useDashboardData = () =>
  useQuery({
    queryKey: DASHBOARD_QUERY_KEY,
    queryFn: dashboardClient.fetchOverview,
    staleTime: 60_000,
  });

export default useDashboardData;
