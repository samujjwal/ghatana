import { useCallback, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import { startGeneration, getGenerationJob, listRecentJobs } from "@/api/contentApi";
import type { GenerationRequest } from "@/types/content";
import { activeJobIdAtom } from "@/stores/explorerStore";

// ─────────────────────────────────────────────────────────────────────────────
// Start generation + track active job
// ─────────────────────────────────────────────────────────────────────────────

export function useStartGeneration() {
  const [, setActiveJobId] = useAtom(activeJobIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: GenerationRequest) => startGeneration(request),
    onSuccess: (job) => {
      setActiveJobId(job.jobId);
      queryClient.invalidateQueries({ queryKey: ["generation", "recent"] });
    },
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Poll active generation job
// ─────────────────────────────────────────────────────────────────────────────

export function useActiveGenerationJob() {
  const [activeJobId, setActiveJobId] = useAtom(activeJobIdAtom);
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["generation", "job", activeJobId],
    queryFn: () => getGenerationJob(activeJobId!),
    enabled: activeJobId !== null,
    // Poll every 2 seconds while job is running
    refetchInterval: (data) => {
      const status = data.state.data?.status;
      return status === "running" || status === "queued" ? 2000 : false;
    },
  });

  // When job completes, invalidate content list to show new item
  const onJobComplete = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["content"] });
    setActiveJobId(null);
  }, [queryClient, setActiveJobId]);

  useEffect(() => {
    if (query.data?.status === "completed" || query.data?.status === "failed") {
      onJobComplete();
    }
  }, [query.data?.status, onJobComplete]);

  return { job: query.data, isLoading: query.isLoading, activeJobId };
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent generation jobs
// ─────────────────────────────────────────────────────────────────────────────

export function useRecentGenerationJobs() {
  return useQuery({
    queryKey: ["generation", "recent"],
    queryFn: listRecentJobs,
    staleTime: 1000 * 30,
    refetchInterval: 1000 * 60,
  });
}
