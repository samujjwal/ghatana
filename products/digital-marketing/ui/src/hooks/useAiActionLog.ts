import { useQuery } from '@tanstack/react-query';
import { getAiAction, listAiActions } from '@/api/ai-actions';
import type { AiActionLogEntry } from '@/types/ai-action';

export function useAiActionLog(
  workspaceId: string | null,
  correlationId?: string,
): {
  entries: AiActionLogEntry[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<AiActionLogEntry[], Error>({
    queryKey: ['ai-actions', workspaceId, correlationId ?? null],
    queryFn: () => listAiActions(workspaceId!, correlationId),
    enabled: workspaceId !== null,
    staleTime: 15_000,
  });

  return {
    entries: data ?? [],
    isLoading,
    isError,
    error: error ?? null,
  };
}

export function useAiActionDetail(
  workspaceId: string | null,
  actionId: string | null,
): {
  entry: AiActionLogEntry | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<AiActionLogEntry, Error>({
    queryKey: ['ai-actions', 'detail', workspaceId, actionId],
    queryFn: () => getAiAction(workspaceId!, actionId!),
    enabled: workspaceId !== null && actionId !== null,
    staleTime: 15_000,
  });

  return {
    entry: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
  };
}
