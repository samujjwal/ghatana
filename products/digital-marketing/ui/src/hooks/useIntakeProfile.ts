import { useQuery } from '@tanstack/react-query';
import { getIntakeDraft, type IntakeDraft } from '@/api/intake';

export function useIntakeProfile(workspaceId: string | null): {
  intake: IntakeDraft | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<IntakeDraft, Error>({
    queryKey: ['intake', 'draft', workspaceId],
    queryFn: () => getIntakeDraft(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 60_000,
  });

  return {
    intake: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
  };
}
