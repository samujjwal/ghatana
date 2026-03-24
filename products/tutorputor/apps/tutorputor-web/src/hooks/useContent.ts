import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

export interface ContentItem {
  id: string;
  title: string;
  type: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

const CONTENT_STUDIO_BASE = "/api/content-studio";

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function studioFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${CONTENT_STUDIO_BASE}${path}`, {
    ...options,
    headers: { ...getAuthHeaders(), ...(options?.headers ?? {}) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`Content Studio API error ${res.status}: ${body}`);
  }
  return res.json() as Promise<T>;
}

function buildQueryString(filters?: Record<string, unknown>): string {
  if (!filters) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null) {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
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
      const res = await studioFetch<ExperienceListResponse>(
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
      const res = await studioFetch<{ data: ContentItem }>(
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
      const res = await studioFetch<ExperienceListResponse>(
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
      const res = await studioFetch<ExperienceListResponse>(
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
      const res = await studioFetch<ExperienceListResponse>(
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
      await studioFetch(`/review-queue/${id}/decision`, {
        method: "POST",
        body: JSON.stringify({ decision: "approve" }),
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
      await studioFetch(`/review-queue/${id}/decision`, {
        method: "POST",
        body: JSON.stringify({ decision: "reject", reason }),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
      queryClient.invalidateQueries({ queryKey: ["pending-review"] });
      queryClient.invalidateQueries({ queryKey: ["content-metrics"] });
    },
  });
}
