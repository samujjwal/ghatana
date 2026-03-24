import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

export interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
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

export function useTemplates(filters?: Record<string, unknown>) {
  return useQuery({
    queryKey: ["templates", filters],
    queryFn: async () => {
      const data = await studioFetch<{ data: Template[] }>("/templates");
      return data.data ?? (data as unknown as Template[]);
    },
  });
}

export function useTemplateById(id: string) {
  return useQuery({
    queryKey: ["template", id],
    queryFn: async () => {
      // Backend /templates returns the list — find by id client-side.
      const data = await studioFetch<{ data: Template[] }>("/templates");
      const list: Template[] = data.data ?? (data as unknown as Template[]);
      return list.find((t) => t.id === id) ?? null;
    },
    enabled: !!id,
  });
}

export function useCreateTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (template: Partial<Template>) => {
      // Templates are backed by experience creation; map to experience fields.
      const result = await studioFetch<{ data: Template }>("/experiences", {
        method: "POST",
        body: JSON.stringify({
          title: template.name ?? "Untitled Template",
          description: template.description ?? "",
          gradeRange: "grade_6_8",
          moduleId: undefined,
        }),
      });
      return result.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
  });
}

export function useDeleteTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await studioFetch(`/experiences/${id}`, { method: "DELETE" });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
  });
}

export function useApplyTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      templateId,
      params,
    }: {
      templateId: string;
      params?: Record<string, unknown>;
    }) => {
      const result = await studioFetch<{ data: unknown }>(
        `/templates/${templateId}/apply`,
        {
          method: "POST",
          body: JSON.stringify(params ?? {}),
        },
      );
      return result.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content"] });
    },
  });
}
