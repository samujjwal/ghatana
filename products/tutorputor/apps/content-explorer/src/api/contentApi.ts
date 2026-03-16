/**
 * Content Explorer API client.
 *
 * All requests proxy through Vite's `/api` → port 3200 (TutorPutor API Gateway).
 * Throws on HTTP error responses so TanStack Query can propagate them correctly.
 */

import type {
  ContentDetail,
  ContentFilters,
  ContentItem,
  ContentItemWithReport,
  ContentMetrics,
  GenerationJob,
  GenerationRequest,
  QualityReport,
} from "@/types/content";

const BASE = "/api/content-explorer";

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`API ${res.status}: ${body || res.statusText}`);
  }
  return res.json() as Promise<T>;
}

function buildQuery(filters: Partial<ContentFilters>): string {
  const params = new URLSearchParams();
  if (filters.search) params.set("search", filters.search);
  if (filters.types?.length) params.set("types", filters.types.join(","));
  if (filters.statuses?.length) params.set("statuses", filters.statuses.join(","));
  if (filters.subjects?.length) params.set("subjects", filters.subjects.join(","));
  if (filters.gradeLevels?.length) params.set("gradeLevels", filters.gradeLevels.join(","));
  if (filters.difficulties?.length) params.set("difficulties", filters.difficulties.join(","));
  if (filters.aiGeneratedOnly) params.set("aiGeneratedOnly", "true");
  if (filters.minQualityScore != null)
    params.set("minQualityScore", String(filters.minQualityScore));
  return params.toString() ? `?${params.toString()}` : "";
}

// ─────────────────────────────────────────────────────────────────────────────
// Content catalogue
// ─────────────────────────────────────────────────────────────────────────────

export interface PagedContentResponse {
  items: ContentItem[];
  total: number;
  page: number;
  pageSize: number;
}

export function listContent(
  filters: Partial<ContentFilters> = {},
  page = 1,
  pageSize = 24,
): Promise<PagedContentResponse> {
  const query = buildQuery(filters);
  const paging = query
    ? `${query}&page=${page}&pageSize=${pageSize}`
    : `?page=${page}&pageSize=${pageSize}`;
  return apiFetch<PagedContentResponse>(`/items${paging}`);
}

export function getContent(id: string): Promise<ContentDetail> {
  return apiFetch<ContentDetail>(`/items/${encodeURIComponent(id)}`);
}

export function exportContent(id: string, format: "pdf" | "html" | "json"): Promise<Blob> {
  return fetch(`${BASE}/items/${encodeURIComponent(id)}/export?format=${format}`, {
    method: "GET",
  }).then((res) => {
    if (!res.ok) throw new Error(`Export failed: ${res.status}`);
    return res.blob();
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Content Generation
// ─────────────────────────────────────────────────────────────────────────────

export function startGeneration(request: GenerationRequest): Promise<GenerationJob> {
  return apiFetch<GenerationJob>("/generate", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function getGenerationJob(jobId: string): Promise<GenerationJob> {
  return apiFetch<GenerationJob>(`/generate/${encodeURIComponent(jobId)}`);
}

export function listRecentJobs(): Promise<GenerationJob[]> {
  return apiFetch<GenerationJob[]>("/generate/recent");
}

// ─────────────────────────────────────────────────────────────────────────────
// Quality evaluation
// ─────────────────────────────────────────────────────────────────────────────

export function getQualityReport(contentId: string): Promise<QualityReport> {
  return apiFetch<QualityReport>(`/items/${encodeURIComponent(contentId)}/quality`);
}

export function triggerQualityEvaluation(contentId: string): Promise<{ jobId: string }> {
  return apiFetch<{ jobId: string }>(
    `/items/${encodeURIComponent(contentId)}/quality/evaluate`,
    { method: "POST" },
  );
}

/** Returns all pending-review items with their quality reports pre-fetched (avoids N+1 in review queue UI). */
export function listPendingReview(): Promise<ContentItemWithReport[]> {
  return apiFetch<ContentItemWithReport[]>("/review/pending");
}

export function approveContent(contentId: string): Promise<void> {
  return apiFetch<void>(`/items/${encodeURIComponent(contentId)}/approve`, { method: "POST" });
}

export function rejectContent(contentId: string, reason: string): Promise<void> {
  return apiFetch<void>(`/items/${encodeURIComponent(contentId)}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Analytics and metrics
// ─────────────────────────────────────────────────────────────────────────────

export function getMetrics(): Promise<ContentMetrics> {
  return apiFetch<ContentMetrics>("/metrics");
}
