/**
 * usePipelineRuns — combined TanStack Query + SSE hook for live pipeline runs.
 *
 * 1. Initial data from REST: `GET /api/v1/runs`
 * 2. Live updates pushed via SSE: the canonical `run.update` event updates the
 *    local query cache without a full refetch. Legacy run_* event names are
 *    accepted temporarily for backward compatibility.
 *
 * @doc.type hook
 * @doc.purpose Provide live pipeline run data with initial REST load + SSE updates
 * @doc.layer frontend
 */
import { useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { tenantIdAtom } from "@/stores/tenant.store";
import {
  listPipelineRuns,
  cancelRun,
  normalizePipelineRun,
  type PipelineRunWire,
  type PipelineRun,
} from "@/api/aep.api";
import { subscribeToAepStream } from "@/api/sse";
import { useMutation } from "@tanstack/react-query";
import { useSSESubscription } from "@ghatana/realtime";

export const PIPELINE_RUNS_QUERY_KEY = "pipeline-runs";

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

  // Keep callbacks stable in refs so the subscription only restarts on tenantId/limit change.
  const queryClientRef = useRef(queryClient);
  queryClientRef.current = queryClient;
  const limitRef = useRef(limit);
  limitRef.current = limit;

  useSSESubscription(
    () =>
      subscribeToAepStream(
        tenantId,
        (msg) => {
          const UPDATE_TYPES = new Set([
            "run.update",
            "run_started",
            "run_completed",
            "run_failed",
            "stage_failed",
          ]);
          if (!UPDATE_TYPES.has(msg.type)) return;

          queryClientRef.current.setQueryData<PipelineRun[]>(
            [PIPELINE_RUNS_QUERY_KEY, tenantId, limitRef.current],
            (prev = []) => {
              if (!msg.data || typeof msg.data !== "object") return prev;
              const run = normalizePipelineRun(msg.data as PipelineRunWire);
              if (!run.id) return prev;

              const idx = prev.findIndex((r) => r.id === run.id);
              if (idx === -1) {
                return [run, ...prev].slice(0, limitRef.current);
              }
              const next = [...prev];
              next[idx] = { ...next[idx], ...run };
              return next;
            },
          );
        },
        () => {
          queryClientRef.current.invalidateQueries({
            queryKey: [PIPELINE_RUNS_QUERY_KEY, tenantId],
          });
        },
      ),
    [tenantId, limit],
  );

  return query;
}

/** Mutation to cancel a specific pipeline run. */
export function useCancelRun() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (runId: string) => cancelRun(runId, tenantId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [PIPELINE_RUNS_QUERY_KEY, tenantId],
      });
    },
  });
}
