import { useQuery } from '@tanstack/react-query';
import { copilotClient } from '../services/mockClient';

export const COPILOT_QUERY_KEY = ['copilot', 'recommendations'] as const;

export const useCopilotData = () =>
  useQuery({
    queryKey: COPILOT_QUERY_KEY,
    queryFn: copilotClient.fetchRecommendations,
    staleTime: 30_000,
  });
