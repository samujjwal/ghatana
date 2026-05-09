import { useQuery } from '@tanstack/react-query';
import {
  getLatestCompetitorResearch,
  type CompetitorResearchSnapshot,
} from '@/api/competitor-research';

export function useLatestCompetitorResearch(workspaceId: string | null): {
  snapshot: CompetitorResearchSnapshot | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<CompetitorResearchSnapshot, Error>({
    queryKey: ['competitor-research', 'latest', workspaceId],
    queryFn: () => getLatestCompetitorResearch(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 60_000,
  });

  return {
    snapshot: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
  };
}
