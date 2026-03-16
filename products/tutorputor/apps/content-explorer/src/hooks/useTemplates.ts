/**
 * React hooks for the Content Template system.
 *
 * All hooks use TanStack Query for cache management so data is shared across
 * the component tree without prop-drilling.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { TemplateStore, templateToGenerationRequest } from "@/types/templates";
import type { ContentTemplate, TemplateFormValues } from "@/types/templates";
import type { ContentType } from "@/types/content";
import type { GenerationRequest } from "@/types/content";

const store = TemplateStore.getInstance();

/** Return all templates (built-in + user). */
export function useTemplates(filter?: {
  domain?: string;
  contentType?: ContentType;
  gradeLevel?: string;
}) {
  return useQuery({
    queryKey: ["templates", filter],
    queryFn: () =>
      filter ? store.filter(filter) : store.getAll(),
    staleTime: 5 * 60 * 1000, // 5 min — templates rarely change
    placeholderData: (prev) => prev,
  });
}

/** Return a single template by id. */
export function useTemplate(id: string | undefined) {
  return useQuery({
    queryKey: ["templates", id],
    queryFn: () => (id ? store.getById(id) ?? null : null),
    enabled: !!id,
  });
}

/** Save (create or update) a user template. */
export function useSaveTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (values: TemplateFormValues): ContentTemplate =>
      store.save(values),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["templates"] });
    },
  });
}

/** Delete a user-created template. */
export function useDeleteTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string): void => store.delete(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["templates"] });
    },
  });
}

/**
 * Convert a template to a generation request and record usage.
 * Returns a callback that accepts the topic string.
 */
export function useApplyTemplate(onApply: (req: Partial<GenerationRequest>) => void) {
  return (template: ContentTemplate, topic: string = "") => {
    store.recordUsage(template.id);
    onApply(templateToGenerationRequest(template, topic));
  };
}
