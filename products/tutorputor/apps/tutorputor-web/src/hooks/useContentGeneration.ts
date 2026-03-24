import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export interface ContentGenerationRequest {
  topic: string;
  targetAudience: string;
  learningObjectives?: string[];
  contentType?: "module" | "claim" | "example" | "simulation";
}

export interface ContentGenerationResult {
  id: string;
  status: "pending" | "processing" | "completed" | "failed";
  result?: {
    content: string;
    metadata: Record<string, unknown>;
  };
  error?: string;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    "X-Tenant-ID": tenantId,
    "X-Correlation-ID": crypto.randomUUID(),
  };
  if (token)
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  return headers;
}

async function contentStudioFetch<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(`/api/content-studio${path}`, {
    ...options,
    headers: { ...getAuthHeaders(), ...options?.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const err = new Error(
      body?.error || `HTTP ${response.status}: ${response.statusText}`,
    ) as Error & { statusCode: number };
    err.statusCode = response.status;
    throw err;
  }
  return response.json() as Promise<T>;
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export function useGenerateContent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (
      request: ContentGenerationRequest,
    ): Promise<ContentGenerationResult> => {
      // Step 1: Create the experience record
      const created = await contentStudioFetch<{
        data: { id: string; status: string };
      }>("/experiences", {
        method: "POST",
        body: JSON.stringify({
          title: request.topic,
          description: `AI-generated content for ${request.targetAudience}`,
          targetAudience: request.targetAudience,
          learningObjectives: request.learningObjectives ?? [],
          contentType: request.contentType ?? "module",
        }),
      });

      // Step 2: Kick off claim generation (fire-and-forget; server may queue it)
      contentStudioFetch(`/experiences/${created.data.id}/generate-claims`, {
        method: "POST",
        body: JSON.stringify({ topic: request.topic }),
      }).catch(() => {
        // Non-fatal: generation may start via background event hooks
      });

      return { id: created.data.id, status: "pending" };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
      queryClient.invalidateQueries({ queryKey: ["recent-generations"] });
    },
  });
}

export function useContentGenerationStatus(id: string) {
  return useQuery({
    queryKey: ["content-generation", id],
    queryFn: async (): Promise<ContentGenerationResult> => {
      const data = await contentStudioFetch<{
        status?: string;
        progress?: number;
        error?: string;
      }>(`/experiences/${id}/progress`);

      return {
        id,
        status: (data.status as ContentGenerationResult["status"]) ?? "pending",
        error: data.error,
      };
    },
    enabled: !!id,
    refetchInterval: (query) => {
      const data = query.state.data as ContentGenerationResult | undefined;
      if (data?.status === "pending" || data?.status === "processing") {
        return 2000; // Poll every 2 seconds while in-flight
      }
      return false;
    },
  });
}

export function useContentGeneration(params: ContentGenerationRequest) {
  const generate = useGenerateContent();

  const query = useQuery({
    queryKey: ["content-generation", params],
    queryFn: () => generate.mutateAsync(params),
    enabled: false,
  });

  return {
    ...query,
    isGenerating: generate.isPending,
  };
}

export function useStartGeneration() {
  return useGenerateContent();
}

export function useActiveGenerationJob() {
  return useQuery({
    queryKey: ["active-generation"],
    queryFn: async (): Promise<ContentGenerationResult | null> => {
      const data = await contentStudioFetch<{
        data: Array<{ id: string; status: string }>;
      }>("/experiences?status=processing&limit=1");
      if (!data.data?.length) return null;
      return {
        id: data.data[0].id,
        status: data.data[0].status as ContentGenerationResult["status"],
      };
    },
    refetchInterval: (query) => {
      const data = query.state.data as
        | ContentGenerationResult
        | null
        | undefined;
      if (data?.status === "pending" || data?.status === "processing")
        return 3000;
      return false;
    },
  });
}

export function useRecentGenerationJobs() {
  return useQuery({
    queryKey: ["recent-generations"],
    queryFn: async (): Promise<ContentGenerationResult[]> => {
      const data = await contentStudioFetch<{
        data: Array<{ id: string; status: string }>;
      }>("/experiences?limit=10");
      return (data.data ?? []).map((exp) => ({
        id: exp.id,
        status: exp.status as ContentGenerationResult["status"],
      }));
    },
  });
}
