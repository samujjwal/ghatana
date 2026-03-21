import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import {
  listContent,
  getContent,
  getMetrics,
  listPendingReview,
  approveContent,
  rejectContent,
} from "@/api/contentApi";
import { filtersAtom, currentPageAtom, pageSizeAtom } from "@/stores/explorerStore";

// ─────────────────────────────────────────────────────────────────────────────
// Content list with pagination + filters
// ─────────────────────────────────────────────────────────────────────────────

export function useContentList() {
  const [filters] = useAtom(filtersAtom);
  const [page] = useAtom(currentPageAtom);
  const [pageSize] = useAtom(pageSizeAtom);

  return useQuery({
    queryKey: ["content", "list", filters, page, pageSize],
    queryFn: () => listContent(filters, page, pageSize),
    staleTime: 1000 * 30, // 30 seconds
    placeholderData: (prev) => prev,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Single content item detail
// ─────────────────────────────────────────────────────────────────────────────

export function useContentDetail(id: string | null) {
  return useQuery({
    queryKey: ["content", "detail", id],
    queryFn: () => getContent(id!),
    enabled: id !== null,
    staleTime: 1000 * 60, // 1 minute
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard metrics
// ─────────────────────────────────────────────────────────────────────────────

export function useContentMetrics() {
  return useQuery({
    queryKey: ["content", "metrics"],
    queryFn: getMetrics,
    staleTime: 1000 * 60, // 1 minute
    refetchInterval: 1000 * 60 * 5, // refresh every 5 minutes
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Review queue
// ─────────────────────────────────────────────────────────────────────────────

export function usePendingReview() {
  return useQuery({
    queryKey: ["content", "review", "pending"],
    queryFn: () => listPendingReview(),
    staleTime: 1000 * 30,
    refetchInterval: 1000 * 60, // auto-refresh pending queue every minute
  });
}

export function useApproveContent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: string }) => approveContent(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
    },
  });
}

export function useRejectContent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectContent(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
    },
  });
}
