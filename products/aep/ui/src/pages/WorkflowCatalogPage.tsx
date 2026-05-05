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
import { Button } from '@ghatana/design-system';
import { TextField } from '@ghatana/design-system';
import {
  getEditPipelineUrl,
  getPipelineListUrl,
} from '@/lib/routes';
import { EmptyState } from '@ghatana/design-system';
import { ErrorState } from '@/components/core/ErrorState';

// ─── Helpers ─────────────────────────────────────────────────────────────────

// eslint-disable-next-line ghatana/no-duplicate-utilities
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
      <Button
        type="button"
        disabled={isInstantiating}
        onClick={() => onInstantiate(template.id)}
        variant="primary"
        className="mt-4 w-full px-3 py-1.5 text-sm font-medium"
      >
        {isInstantiating ? 'Instantiating…' : 'Instantiate'}
      </Button>
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
  const [instantiatedPipeline, setInstantiatedPipeline] = useState<{ name: string; id: string } | null>(null);

  const {
    data: templates = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
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
      queryClient.invalidateQueries({ queryKey: ['aep', 'pipelines', tenantId] });
      const template = templates?.find((t) => t.id === templateId);
      setInstantiatedPipeline({
        name: template?.name ?? 'Untitled',
        id: result.pipelineId,
      });
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
            Browse and instantiate reusable orchestration templates
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
        <TextField
          type="search"
          placeholder="Search by name or tag…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full max-w-sm text-sm"
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
          <ErrorState
            title="Failed to load templates"
            message={error instanceof Error ? error.message : 'Unknown error'}
            onRetry={() => void refetch()}
          />
        )}

        {!isLoading && !isError && filtered.length === 0 && (
          <EmptyState
            title={templates?.length === 0 ? 'No workflow templates registered' : 'No templates match your search'}
            description={templates?.length === 0 ? 'Register templates for this tenant to get started.' : 'Try adjusting your search terms.'}
            action={search ? <Button onClick={() => setSearch('')} variant="secondary">Clear search</Button> : undefined}
          />
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

      {/* Success confirmation modal */}
      {instantiatedPipeline && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-xl p-6 max-w-sm w-full mx-4 shadow-xl">
            <h2 className="font-semibold text-gray-900 dark:text-white mb-2">
              Pipeline created
            </h2>
            <p className="text-sm text-gray-500 mb-4">
              “{instantiatedPipeline.name}” was instantiated successfully.
            </p>
            <div className="flex gap-2 justify-end">
              <Button
                onClick={() => {
                  setInstantiatedPipeline(null);
                  navigate(getPipelineListUrl());
                }}
                variant="secondary"
              >
                Go to list
              </Button>
              <Button
                onClick={() => {
                  const id = instantiatedPipeline.id;
                  setInstantiatedPipeline(null);
                  navigate(getEditPipelineUrl(id));
                }}
                variant="primary"
              >
                Open in builder
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
