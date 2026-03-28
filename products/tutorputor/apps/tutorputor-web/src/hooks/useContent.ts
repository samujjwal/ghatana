import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  contentStudioFetch,
  buildQueryString,
} from "../lib/contentStudioClient";

export interface ContentItem {
  id: string;
  title: string;
  type: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface ExperienceListResponse {
  data: ContentItem[];
  pagination?: { total: number };
}

export function useContent(filters?: Record<string, unknown>) {
  return useQuery({
    queryKey: ["content", filters],
    queryFn: async () => {
      const qs = buildQueryString({ limit: 50, ...filters });
      const res = await contentStudioFetch<ExperienceListResponse>(
        `/experiences${qs}`,
      );
      return res.data ?? [];
    },
  });
}

export function useContentById(id: string) {
  return useQuery({
    queryKey: ["content", id],
    queryFn: async () => {
      const res = await contentStudioFetch<{ data: ContentItem }>(
        `/experiences/${id}`,
      );
      return res.data ?? null;
    },
    enabled: !!id,
  });
}

export function useContentList(filters?: Record<string, unknown>) {
  return useQuery({
    queryKey: ["content-list", filters],
    queryFn: async () => {
      const qs = buildQueryString({ limit: 50, ...filters });
      const res = await contentStudioFetch<ExperienceListResponse>(
        `/experiences${qs}`,
      );
      return res.data ?? [];
    },
  });
}

export function useContentMetrics() {
  return useQuery({
    queryKey: ["content-metrics"],
    queryFn: async () => {
      const res = await contentStudioFetch<ExperienceListResponse>(
        "/experiences?limit=500",
      );
      const items = res.data ?? [];
      return {
        total: res.pagination?.total ?? items.length,
        published: items.filter((i) => i.status === "published").length,
        draft: items.filter((i) => i.status === "draft").length,
        archived: items.filter((i) => i.status === "archived").length,
      };
    },
  });
}

export function usePendingReview() {
  return useQuery({
    queryKey: ["pending-review"],
    queryFn: async () => {
      const res = await contentStudioFetch<ExperienceListResponse>(
        "/experiences?status=review&limit=50",
      );
      return res.data ?? [];
    },
  });
}

export function useApproveContent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await contentStudioFetch(`/experiences/${id}/publish`, {
        method: "POST",
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
      queryClient.invalidateQueries({ queryKey: ["pending-review"] });
      queryClient.invalidateQueries({ queryKey: ["content-metrics"] });
    },
  });
}

export function useRejectContent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason?: string }) => {
      await contentStudioFetch(`/experiences/${id}/unpublish`, {
        method: "POST",
        body: JSON.stringify(reason ? { reason } : {}),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
      queryClient.invalidateQueries({ queryKey: ["pending-review"] });
      queryClient.invalidateQueries({ queryKey: ["content-metrics"] });
    },
  });
}
