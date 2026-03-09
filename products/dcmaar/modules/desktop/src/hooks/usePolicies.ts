import { useQuery } from '@tanstack/react-query';
import { policyClient } from '../services/mockClient';

export const POLICIES_QUERY_KEY = ['policies', 'catalogue'] as const;

export const usePolicyData = () =>
  useQuery({
    queryKey: POLICIES_QUERY_KEY,
    queryFn: policyClient.fetchPolicies,
    staleTime: 60_000,
  });
