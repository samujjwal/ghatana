/**
 * WorkflowCatalogPage — browse and instantiate AEP workflow templates.
 *
 * Features:
 *   - List all workflow templates for the active tenant
 *   - Search by name or tag
 *   - Show operator count, version, and last-updated date per template
 *   - Instantiate a template → creates a pipeline and navigates to Pipeline Builder
 *
 * @doc.type page
 * @doc.purpose AEP workflow template catalog
 * @doc.layer frontend
 */
import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  listWorkflowTemplates,
  instantiateTemplate,
  type WorkflowTemplate,
} from '@/api/aep.api';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString([], {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

// ─── Template card ────────────────────────────────────────────────────────────

interface TemplateCardProps {
  template: WorkflowTemplate;
  onInstantiate: (id: string) => void;
  isInstantiating: boolean;
}

function TemplateCard({ template, onInstantiate, isInstantiating }: TemplateCardProps) {
  return (
    <div className="flex flex-col justify-between rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm hover:shadow-md transition-shadow">
      <div className="space-y-3">
        {/* Name + version */}
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 leading-snug">
            {template.name}
          </h3>
          <span className="shrink-0 inline-flex items-center rounded bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs font-mono text-gray-600 dark:text-gray-400">
            v{template.version}
          </span>
        </div>

        {/* Description */}
        {template.description && (
          <p className="text-xs text-gray-500 dark:text-gray-400 line-clamp-2">
            {template.description}
          </p>
        )}

        {/* Operator count badge */}
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-gray-500 dark:text-gray-400">Operators:</span>
          <span className="inline-flex items-center rounded-full bg-indigo-50 dark:bg-indigo-950 px-2 py-0.5 text-xs font-medium text-indigo-700 dark:text-indigo-300">
            {template.operatorCount}
          </span>
        </div>

        {/* Tags */}
        {template.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {template.tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex rounded bg-blue-50 dark:bg-blue-950 px-1.5 py-0.5 text-xs text-blue-700 dark:text-blue-300"
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Updated at */}
        <p className="text-xs text-gray-400 dark:text-gray-500">
          Updated {formatDate(template.updatedAt)}
        </p>
      </div>

      {/* Action */}
      <button
        type="button"
        disabled={isInstantiating}
        onClick={() => onInstantiate(template.id)}
        className="mt-4 w-full rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        {isInstantiating ? 'Instantiating…' : 'Instantiate'}
      </button>
    </div>
  );
}

// ─── Loading skeleton ─────────────────────────────────────────────────────────

function SkeletonCard() {
  return (
    <div className="animate-pulse rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
      <div className="space-y-3">
        <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4" />
        <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-full" />
        <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-1/2" />
        <div className="flex gap-1">
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-10" />
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-10" />
        </div>
      </div>
      <div className="mt-4 h-7 bg-gray-200 dark:bg-gray-700 rounded" />
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function WorkflowCatalogPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [instantiatingIds, setInstantiatingIds] = useState<Set<string>>(new Set());

  const { data: templates, isLoading, isError, error } = useQuery({
    queryKey: ['workflow-templates', tenantId],
    queryFn: () => listWorkflowTemplates(tenantId),
    staleTime: 60_000,
  });

  const instantiateMutation = useMutation({
    mutationFn: (templateId: string) => instantiateTemplate(templateId, tenantId),
    onMutate: (templateId) => {
      setInstantiatingIds((prev) => new Set(prev).add(templateId));
    },
    onSuccess: (result, templateId) => {
      queryClient.invalidateQueries({ queryKey: ['pipelines', tenantId] });
      navigate(`/pipelines/${result.pipelineId}`);
    },
    onError: (_err, templateId) => {
      setInstantiatingIds((prev) => {
        const next = new Set(prev);
        next.delete(templateId);
        return next;
      });
    },
    onSettled: (_data, _error, templateId) => {
      setInstantiatingIds((prev) => {
        const next = new Set(prev);
        next.delete(templateId);
        return next;
      });
    },
  });

  const filtered = useMemo<WorkflowTemplate[]>(() => {
    if (!templates) return [];
    if (!search.trim()) return templates;
    const q = search.toLowerCase();
    return templates.filter(
      (t) =>
        t.name.toLowerCase().includes(q) ||
        t.description?.toLowerCase().includes(q) ||
        t.tags.some((tag) => tag.toLowerCase().includes(q)),
    );
  }, [templates, search]);

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shrink-0">
        <div>
          <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Workflow Templates
          </h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
            Browse and instantiate reusable pipeline templates
          </p>
        </div>
        {templates && (
          <span className="text-xs text-gray-500 dark:text-gray-400">
            {filtered.length}{templates.length !== filtered.length ? ` of ${templates.length}` : ''} template{filtered.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {/* Search bar */}
      <div className="px-6 py-3 border-b border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 shrink-0">
        <input
          type="search"
          placeholder="Search by name or tag…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full max-w-sm rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-1.5 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto px-6 py-5">
        {isLoading && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        )}

        {isError && (
          <div className="flex items-center justify-center h-40">
            <p className="text-sm text-red-600 dark:text-red-400">
              Failed to load templates:{' '}
              {error instanceof Error ? error.message : 'Unknown error'}
            </p>
          </div>
        )}

        {!isLoading && !isError && filtered.length === 0 && (
          <div className="flex flex-col items-center justify-center h-40 gap-2">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {templates?.length === 0
                ? 'No workflow templates registered for this tenant.'
                : 'No templates match your search.'}
            </p>
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                Clear search
              </button>
            )}
          </div>
        )}

        {!isLoading && !isError && filtered.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {filtered.map((template) => (
              <TemplateCard
                key={template.id}
                template={template}
                onInstantiate={(id) => instantiateMutation.mutate(id)}
                isInstantiating={instantiatingIds.has(template.id)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
