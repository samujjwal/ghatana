/**
 * EntityBrowserPage — Browse and manage Data-Cloud entities with schema info.
 *
 * Provides unified entity CRUD browser backed by the DC entity store,
 * with schema inspection from the Schema Registry and pervasive AI/ML
 * suggestions for entity exploration (Workstream C — DC-E3).
 *
 * @doc.type component
 * @doc.purpose Entity browser for Data-Cloud entity store with AI suggestions
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';

// =============================================================================
// Types
// =============================================================================

interface EntitySchema {
  id: string;
  name: string;
  namespace: string;
  version: number;
  fields: SchemaField[];
}

interface SchemaField {
  name: string;
  type: string;
  required: boolean;
  description?: string;
}

interface Entity {
  id: string;
  tenantId: string;
  namespace: string;
  schemaId?: string;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  version: number;
}

interface EntityListResponse {
  entities: Entity[];
  total: number;
  hasMore: boolean;
}

/** AI suggestion returned by POST /api/v1/entities/:collection/suggest */
interface EntitySuggestionItem {
  type: 'explore_related' | 'anomaly_hint' | 'filter_optimization' | 'data_quality' | string;
  title: string;
  description: string;
  confidence: number;
  /** Machine-readable reason codes, e.g. ["low_cardinality", "frequent_nulls"] */
  reasons?: string[];
}

interface EntitySuggestResponse {
  data?: {
    suggestions?: EntitySuggestionItem[];
  };
  ai?: {
    confidence: number;
    model: string;
    fallback: boolean;
  };
}

// =============================================================================
// API helpers
// =============================================================================

async function listNamespaces(tenantId?: string): Promise<string[]> {
  return apiClient.get<string[]>('/dc/entities/namespaces', {
    params: tenantId ? { tenantId } : {},
  });
}

async function listEntities(namespace: string, tenantId?: string, limit = 20): Promise<EntityListResponse> {
  return apiClient.get<EntityListResponse>(`/dc/entities/${namespace}`, {
    params: { ...(tenantId ? { tenantId } : {}), limit },
  });
}

async function getEntitySchema(namespace: string, tenantId?: string): Promise<EntitySchema | null> {
  try {
    return await apiClient.get<EntitySchema>(`/dc/schemas/${namespace}`, {
      params: tenantId ? { tenantId } : {},
    });
  } catch {
    return null;
  }
}

async function deleteEntity(namespace: string, id: string, tenantId?: string): Promise<void> {
  await apiClient.delete(`/dc/entities/${namespace}/${id}`, {
    params: tenantId ? { tenantId } : {},
  });
}

/**
 * Fetch AI/ML suggestions for the given entity collection.
 *
 * Calls POST /api/v1/entities/:collection/suggest with an exploration context.
 * If the AI service is unavailable the backend returns a deterministic fallback
 * (confidence=0.2) — we display those suggestions but mark them as heuristic.
 *
 * Fails silently (returns null) on network errors so the page remains usable
 * without AI assistance.
 */
async function fetchEntitySuggestions(
  collection: string,
): Promise<EntitySuggestResponse | null> {
  try {
    return await apiClient.post<EntitySuggestResponse>(
      `/api/v1/entities/${encodeURIComponent(collection)}/suggest`,
      { context: `Explore collection: ${collection}`, limit: 5 },
    );
  } catch {
    return null;
  }
}

// =============================================================================
// Sub-components
// =============================================================================

/**
 * AI/ML suggestions panel (Workstream C — pervasive native AI/ML, DC-E3).
 *
 * Renders exploration hints, anomaly flags, and filter-optimization tips
 * returned by the AiAssistHandler backend for the selected collection.
 * Falls back gracefully when the AI service is offline or the confidence
 * is below usable thresholds.
 */
function AiSuggestionPanel({
  suggestions,
  isLoading,
  isFallback,
  onDismiss,
}: {
  suggestions: EntitySuggestionItem[];
  isLoading: boolean;
  isFallback: boolean;
  onDismiss: () => void;
}): React.ReactElement | null {
  const [expanded, setExpanded] = useState(true);

  if (!isLoading && suggestions.length === 0) return null;

  const typeIcon: Record<string, string> = {
    anomaly_hint: '⚠',
    explore_related: '🔗',
    filter_optimization: '⚡',
    data_quality: '🔍',
  };

  const confidenceLabel = (c: number): string =>
    c >= 0.8 ? 'High' : c >= 0.6 ? 'Medium' : 'Heuristic';

  const confidenceClass = (c: number): string =>
    c >= 0.8
      ? 'bg-green-50 text-green-700 border-green-200'
      : c >= 0.6
        ? 'bg-amber-50 text-amber-700 border-amber-200'
        : 'bg-gray-50 text-gray-500 border-gray-200';

  return (
    <div
      className="border-b border-indigo-100 bg-indigo-50"
      data-testid="ai-suggestion-panel"
      role="complementary"
      aria-label="AI exploration suggestions"
    >
      <div className="flex items-center gap-2 px-4 py-2 cursor-pointer select-none" onClick={() => setExpanded((v) => !v)}>
        <span className="text-indigo-500 text-sm">✨</span>
        <span className="text-xs font-semibold text-indigo-700">
          AI Suggestions
          {isFallback && (
            <span className="ml-1.5 text-indigo-400 font-normal">(heuristic)</span>
          )}
        </span>
        <span className="ml-auto text-gray-400 text-xs">{expanded ? '▲' : '▼'}</span>
        <button
          onClick={(e) => { e.stopPropagation(); onDismiss(); }}
          className="ml-2 text-gray-300 hover:text-gray-500 text-xs"
          aria-label="Dismiss AI suggestions"
        >
          ✕
        </button>
      </div>
      {expanded && (
        <div className="px-4 pb-3">
          {isLoading ? (
            <div className="flex items-center gap-2 text-xs text-indigo-400 py-1">
              <svg className="animate-spin h-3 w-3" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              Generating exploration hints…
            </div>
          ) : (
            <ul className="space-y-1.5" role="list">
              {suggestions.map((s, idx) => (
                <li
                  key={idx}
                  className={`flex items-start gap-2 rounded border px-3 py-2 text-xs ${confidenceClass(s.confidence)}`}
                >
                  <span className="text-base leading-none mt-0.5 select-none" aria-hidden>
                    {typeIcon[s.type] ?? '💡'}
                  </span>
                  <div className="min-w-0">
                    <p className="font-medium truncate">{s.title}</p>
                    <p className="text-gray-600 mt-0.5 leading-snug">{s.description}</p>
                  </div>
                  <span
                    className="ml-auto shrink-0 text-xs opacity-70 font-mono"
                    title={`Confidence: ${(s.confidence * 100).toFixed(0)}%`}
                  >
                    {confidenceLabel(s.confidence)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function SchemaPanel({ schema }: { schema: EntitySchema }): React.ReactElement {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-800">Schema</h3>
        <span className="text-xs text-gray-500">v{schema.version}</span>
      </div>
      <p className="text-xs text-gray-500 mb-3">{schema.namespace}</p>
      <table className="w-full text-xs">
        <thead>
          <tr className="text-gray-500 border-b border-gray-100">
            <th className="text-left py-1 font-medium">Field</th>
            <th className="text-left py-1 font-medium">Type</th>
            <th className="text-left py-1 font-medium">Required</th>
          </tr>
        </thead>
        <tbody>
          {schema.fields.map((f) => (
            <tr key={f.name} className="border-b border-gray-50">
              <td className="py-1 font-mono text-gray-700">{f.name}</td>
              <td className="py-1 text-indigo-600">{f.type}</td>
              <td className="py-1">{f.required ? '✓' : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function EntityRow({
  entity,
  selected,
  onClick,
  onDelete,
}: {
  entity: Entity;
  selected: boolean;
  onClick: () => void;
  onDelete: () => void;
}): React.ReactElement {
  const preview = Object.entries(entity.data)
    .slice(0, 3)
    .map(([k, v]) => `${k}: ${String(v)}`)
    .join(' · ');

  return (
    <tr
      className={`border-b border-gray-100 cursor-pointer hover:bg-gray-50 transition-colors ${selected ? 'bg-indigo-50' : ''}`}
      onClick={onClick}
    >
      <td className="px-4 py-2.5 font-mono text-xs text-gray-500">{entity.id}</td>
      <td className="px-4 py-2.5 text-sm text-gray-700 max-w-sm truncate">{preview || '—'}</td>
      <td className="px-4 py-2.5 text-xs text-gray-400">v{entity.version}</td>
      <td className="px-4 py-2.5 text-xs text-gray-400">
        {new Date(entity.updatedAt).toLocaleDateString()}
      </td>
      <td className="px-4 py-2.5">
        <button
          onClick={(e) => { e.stopPropagation(); onDelete(); }}
          className="text-xs text-red-400 hover:text-red-600 px-2 py-0.5 rounded hover:bg-red-50"
          aria-label="Delete entity"
        >
          Delete
        </button>
      </td>
    </tr>
  );
}

function EntityDetailPanel({
  entity,
  schema,
  onClose,
}: {
  entity: Entity;
  schema: EntitySchema | null;
  onClose: () => void;
}): React.ReactElement {
  return (
    <aside className="w-96 shrink-0 border-l border-gray-200 bg-white flex flex-col">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
        <h3 className="text-sm font-semibold text-gray-800">Entity Detail</h3>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600" aria-label="Close">
          ✕
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-4 text-sm">
        <dl className="space-y-1.5">
          {[
            ['ID', entity.id],
            ['Namespace', entity.namespace],
            ['Tenant', entity.tenantId],
            ['Version', String(entity.version)],
            ['Created', new Date(entity.createdAt).toLocaleString()],
            ['Updated', new Date(entity.updatedAt).toLocaleString()],
          ].map(([k, v]) => (
            <div key={k} className="flex gap-2">
              <dt className="w-24 shrink-0 text-gray-500 font-medium">{k}</dt>
              <dd className="font-mono text-xs text-gray-700 break-all">{v as string}</dd>
            </div>
          ))}
        </dl>
        <div>
          <p className="text-gray-500 font-medium mb-1">Data</p>
          <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-xs overflow-auto max-h-52">
            {JSON.stringify(entity.data, null, 2)}
          </pre>
        </div>
        {schema && <SchemaPanel schema={schema} />}
      </div>
    </aside>
  );
}

// =============================================================================
// Page
// =============================================================================

/**
 * EntityBrowserPage — browse data-cloud entities with inline schema.
 *
 * @doc.type component
 * @doc.purpose Unified entity browser for DC entity store
 * @doc.layer product
 * @doc.pattern Page
 */
export function EntityBrowserPage(): React.ReactElement {
  const qc = useQueryClient();
  const [namespace, setNamespace] = useState<string>('');
  const [selectedEntity, setSelectedEntity] = useState<Entity | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [aiDismissed, setAiDismissed] = useState(false);

  const { data: namespaces = [], isLoading: nsLoading } = useQuery({
    queryKey: ['dc', 'entities', 'namespaces'],
    queryFn: () => listNamespaces(),
    staleTime: 60_000,
  });

  // Auto-select first namespace
  React.useEffect(() => {
    if (namespaces.length > 0 && !namespace) {
      setNamespace(namespaces[0]);
    }
  }, [namespaces, namespace]);

  const { data: entityList, isLoading, error } = useQuery({
    queryKey: ['dc', 'entities', namespace],
    queryFn: () => listEntities(namespace),
    enabled: !!namespace,
    staleTime: 30_000,
  });

  const { data: schema } = useQuery({
    queryKey: ['dc', 'schema', namespace],
    queryFn: () => getEntitySchema(namespace),
    enabled: !!namespace,
    staleTime: 120_000,
  });

  // AI suggestions: fetched per-namespace, non-blocking (graceful fallback)
  const { data: suggestResponse, isFetching: suggestLoading } = useQuery({
    queryKey: ['dc', 'entity-suggest', namespace],
    queryFn: () => fetchEntitySuggestions(namespace),
    enabled: !!namespace && !aiDismissed,
    staleTime: 300_000, // suggestions are stable for 5 minutes
    retry: false,       // never retry — AI service unavailability is graceful
  });

  const suggestions: EntitySuggestionItem[] =
    suggestResponse?.data?.suggestions ?? [];
  const isFallback = suggestResponse?.ai?.fallback ?? false;

  const deleteMutation = useMutation({
    mutationFn: ({ ns, id }: { ns: string; id: string }) => deleteEntity(ns, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['dc', 'entities', namespace] });
      setSelectedEntity(null);
    },
  });

  const filteredEntities = searchQuery
    ? (entityList?.entities ?? []).filter(
        (e) =>
          e.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
          JSON.stringify(e.data).toLowerCase().includes(searchQuery.toLowerCase()),
      )
    : (entityList?.entities ?? []);

  const handleNamespaceChange = (ns: string): void => {
    setNamespace(ns);
    setSelectedEntity(null);
    setAiDismissed(false); // reset dismissal so suggestions reload for new namespace
  };

  return (
    <div className="flex flex-col h-full bg-white" data-testid="entity-browser-page">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200">
        <h1 className="text-xl font-semibold text-gray-900">Entity Browser</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          Browse entities stored in the Data-Cloud entity store with schema validation
        </p>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar — namespace list */}
        <nav className="w-48 shrink-0 border-r border-gray-200 bg-gray-50 overflow-y-auto">
          <div className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Namespaces
          </div>
          {nsLoading && (
            <div className="px-4 py-2 text-xs text-gray-400">Loading…</div>
          )}
          {namespaces.map((ns) => (
            <button
              key={ns}
              onClick={() => handleNamespaceChange(ns)}
              className={`w-full text-left px-4 py-2 text-sm truncate transition-colors ${
                namespace === ns
                  ? 'bg-indigo-50 text-indigo-700 font-medium'
                  : 'text-gray-700 hover:bg-white'
              }`}
            >
              {ns}
            </button>
          ))}
          {!nsLoading && namespaces.length === 0 && (
            <div className="px-4 py-2 text-xs text-gray-400">No namespaces</div>
          )}
        </nav>

        {/* Main — entity list */}
        <div className="flex flex-1 overflow-hidden">
          <div className="flex flex-col flex-1 overflow-hidden">
            {/* AI Suggestions panel — pervasive AI/ML (Workstream C) */}
            {namespace && !aiDismissed && (
              <AiSuggestionPanel
                suggestions={suggestions}
                isLoading={suggestLoading}
                isFallback={isFallback}
                onDismiss={() => setAiDismissed(true)}
              />
            )}

            {/* Toolbar */}
            <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 bg-white">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search entities…"
                className="px-3 py-1.5 text-sm border border-gray-300 rounded flex-1 max-w-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
                aria-label="Search entities"
              />
              <span className="text-sm text-gray-500 ml-auto">
                {filteredEntities.length} / {entityList?.total ?? 0} entities
              </span>
            </div>

            {/* Table */}
            <div className="flex-1 overflow-auto">
              {!namespace && (
                <div className="flex justify-center items-center h-32 text-gray-400 text-sm">
                  Select a namespace to browse entities
                </div>
              )}
              {isLoading && namespace && (
                <div className="flex justify-center items-center h-32 text-gray-400 text-sm">
                  Loading entities…
                </div>
              )}
              {error instanceof Error && (
                <div className="m-4 p-4 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
                  Failed to load entities: {error.message}
                </div>
              )}
              {!isLoading && filteredEntities.length > 0 && (
                <table className="w-full text-left text-sm" aria-label="Entity list">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="px-4 py-2 font-medium text-gray-500 text-xs">ID</th>
                      <th className="px-4 py-2 font-medium text-gray-500 text-xs">PREVIEW</th>
                      <th className="px-4 py-2 font-medium text-gray-500 text-xs">VER</th>
                      <th className="px-4 py-2 font-medium text-gray-500 text-xs">UPDATED</th>
                      <th className="px-4 py-2 font-medium text-gray-500 text-xs"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredEntities.map((entity) => (
                      <EntityRow
                        key={entity.id}
                        entity={entity}
                        selected={selectedEntity?.id === entity.id}
                        onClick={() => setSelectedEntity((prev) => prev?.id === entity.id ? null : entity)}
                        onDelete={() => deleteMutation.mutate({ ns: namespace, id: entity.id })}
                      />
                    ))}
                  </tbody>
                </table>
              )}
              {!isLoading && namespace && filteredEntities.length === 0 && (
                <div className="flex flex-col items-center justify-center h-32 text-gray-400 text-sm">
                  <p>No entities found in <strong>{namespace}</strong></p>
                </div>
              )}
            </div>
          </div>

          {selectedEntity && (
            <EntityDetailPanel
              entity={selectedEntity}
              schema={schema ?? null}
              onClose={() => setSelectedEntity(null)}
            />
          )}
        </div>
      </div>
    </div>
  );
}
