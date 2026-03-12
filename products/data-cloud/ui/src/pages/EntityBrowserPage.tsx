/**
 * EntityBrowserPage — Browse and manage Data-Cloud entities with schema info.
 *
 * Provides unified entity CRUD browser backed by the DC entity store,
 * with schema inspection from the Schema Registry.
 *
 * @doc.type component
 * @doc.purpose Entity browser for Data-Cloud entity store
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';

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

// =============================================================================
// API helpers
// =============================================================================

const DC_BASE = import.meta.env.VITE_DC_API_URL ?? '/api';
const dc = axios.create({ baseURL: DC_BASE, headers: { 'Content-Type': 'application/json' } });

async function listNamespaces(tenantId?: string): Promise<string[]> {
  const { data } = await dc.get<string[]>('/dc/entities/namespaces', {
    params: tenantId ? { tenantId } : {},
  });
  return data;
}

async function listEntities(namespace: string, tenantId?: string, limit = 20): Promise<EntityListResponse> {
  const { data } = await dc.get<EntityListResponse>(`/dc/entities/${namespace}`, {
    params: { ...(tenantId ? { tenantId } : {}), limit },
  });
  return data;
}

async function getEntitySchema(namespace: string, tenantId?: string): Promise<EntitySchema | null> {
  try {
    const { data } = await dc.get<EntitySchema>(`/dc/schemas/${namespace}`, {
      params: tenantId ? { tenantId } : {},
    });
    return data;
  } catch {
    return null;
  }
}

async function deleteEntity(namespace: string, id: string, tenantId?: string): Promise<void> {
  await dc.delete(`/dc/entities/${namespace}/${id}`, {
    params: tenantId ? { tenantId } : {},
  });
}

// =============================================================================
// Sub-components
// =============================================================================

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
              onClick={() => { setNamespace(ns); setSelectedEntity(null); }}
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
