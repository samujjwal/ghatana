/**
 * usePipelineRuns — combined TanStack Query + SSE hook for live pipeline runs.
 *
 * 1. Initial data from REST: `GET /api/v1/runs`
 * 2. Live updates pushed via SSE: run_started / run_completed / stage_failed events
 *    update the local query cache without a full refetch.
 *
 * @doc.type hook
 * @doc.purpose Provide live pipeline run data with initial REST load + SSE updates
 * @doc.layer frontend
 */
import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { listPipelineRuns, cancelRun, type PipelineRun } from '@/api/aep.api';
import { subscribeToAepStream } from '@/api/sse';
import { useMutation } from '@tanstack/react-query';

export const PIPELINE_RUNS_QUERY_KEY = 'pipeline-runs';

/**
 * Returns live pipeline runs. Uses SSE to apply incremental updates without
 * polling, keeping results up-to-date with minimal overhead.
 */
export function useLivePipelineRuns(limit = 20) {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: [PIPELINE_RUNS_QUERY_KEY, tenantId, limit],
    queryFn: () => listPipelineRuns(tenantId, limit),
    staleTime: 10_000,
  });

  // Subscribe to SSE updates and merge into query cache
  useEffect(() => {
    const sub = subscribeToAepStream(
      tenantId,
      (msg) => {
        const UPDATE_TYPES = new Set(['run_started', 'run_completed', 'run_failed', 'stage_failed']);
        if (!UPDATE_TYPES.has(msg.type)) return;

        queryClient.setQueryData<PipelineRun[]>(
          [PIPELINE_RUNS_QUERY_KEY, tenantId, limit],
          (prev = []) => {
            const run = msg.data as PipelineRun | undefined;
            if (!run) return prev;

            const idx = prev.findIndex((r) => r.id === run.id);
            if (idx === -1) {
              // New run — prepend and keep up to limit
              return [run, ...prev].slice(0, limit);
            }
            // Update existing run
            const next = [...prev];
            next[idx] = { ...next[idx], ...run };
            return next;
          },
        );
      },
      () => {
        // On SSE error, trigger a full refetch so data stays fresh
        queryClient.invalidateQueries({ queryKey: [PIPELINE_RUNS_QUERY_KEY, tenantId] });
      },
    );

    return () => sub.close();
  }, [tenantId, limit, queryClient]);

  return query;
}

/** Mutation to cancel a specific pipeline run. */
export function useCancelRun() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (runId: string) => cancelRun(runId, tenantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINE_RUNS_QUERY_KEY, tenantId] });
    },
  });
}
