import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { contentStudioFetch } from "../lib/contentStudioClient";

export interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export function useTemplates(filters?: Record<string, unknown>) {
  return useQuery({
    queryKey: ["templates", filters],
    queryFn: async () => {
      const data = await contentStudioFetch<{ data: Template[] }>("/templates");
      return data.data ?? (data as unknown as Template[]);
    },
  });
}

export function useTemplateById(id: string) {
  return useQuery({
    queryKey: ["template", id],
    queryFn: async () => {
      // Backend /templates returns the list — find by id client-side.
      const data = await contentStudioFetch<{ data: Template[] }>("/templates");
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
      const result = await contentStudioFetch<{ data: Template }>("/experiences", {
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
      await contentStudioFetch(`/experiences/${id}`, { method: "DELETE" });
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
      const result = await contentStudioFetch<{ data: unknown }>(
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
