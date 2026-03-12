/**
 * MemoryPlaneViewerPage — Agent memory plane browser.
 *
 * Browse and search episodic, semantic, procedural, and preference
 * memory items stored in the Data-Cloud memory plane.
 *
 * @doc.type component
 * @doc.purpose Agent memory plane inspection UI
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  memoryService,
  type MemoryItem,
  type MemoryType,
} from '../api/memory.service';

// =============================================================================
// Constants
// =============================================================================

const MEMORY_TYPES: MemoryType[] = ['EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'PREFERENCE'];

const TYPE_COLORS: Record<MemoryType, string> = {
  EPISODIC: 'bg-purple-100 text-purple-800',
  SEMANTIC: 'bg-blue-100 text-blue-800',
  PROCEDURAL: 'bg-green-100 text-green-800',
  PREFERENCE: 'bg-amber-100 text-amber-800',
};

const TYPE_DESCRIPTIONS: Record<MemoryType, string> = {
  EPISODIC: 'Past experiences and events',
  SEMANTIC: 'Facts and general knowledge',
  PROCEDURAL: 'Skills and how-to knowledge',
  PREFERENCE: 'User and domain preferences',
};

// =============================================================================
// Sub-components
// =============================================================================

function MemoryTypeBadge({ type }: { type: MemoryType }): React.ReactElement {
  return (
    <span className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ${TYPE_COLORS[type]}`}>
      {type}
    </span>
  );
}

function SalienceMeter({ value }: { value: number }): React.ReactElement {
  const pct = Math.round(value * 100);
  const color = pct >= 70 ? 'bg-green-500' : pct >= 40 ? 'bg-yellow-500' : 'bg-red-500';
  return (
    <div className="flex items-center gap-2">
      <div className="w-20 h-1.5 bg-gray-200 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500">{pct}%</span>
    </div>
  );
}

function MemoryCard({
  item,
  onDelete,
}: {
  item: MemoryItem;
  onDelete: (id: string) => void;
}): React.ReactElement {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="border border-gray-200 rounded-lg p-4 hover:border-indigo-300 transition-colors bg-white">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <MemoryTypeBadge type={item.type} />
          <span className="font-mono text-xs text-gray-400 truncate">{item.id}</span>
        </div>
        <button
          onClick={() => onDelete(item.id)}
          className="shrink-0 text-xs text-red-400 hover:text-red-600 px-2 py-0.5 rounded hover:bg-red-50"
          aria-label="Delete memory item"
        >
          Delete
        </button>
      </div>

      <p className="mt-2 text-sm text-gray-700 line-clamp-2">{item.content}</p>

      <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
        <span>Agent: <strong className="text-gray-700">{item.agentId}</strong></span>
        <span>Salience: <SalienceMeter value={item.salience} /></span>
        <span>{new Date(item.createdAt).toLocaleDateString()}</span>
      </div>

      {item.tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {item.tags.map((tag) => (
            <span key={tag} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
              {tag}
            </span>
          ))}
        </div>
      )}

      <button
        onClick={() => setExpanded((e) => !e)}
        className="mt-2 text-xs text-indigo-500 hover:underline"
      >
        {expanded ? 'Hide metadata ▲' : 'Show metadata ▼'}
      </button>

      {expanded && (
        <pre className="mt-2 bg-gray-50 border border-gray-200 rounded p-2 text-xs overflow-auto max-h-32">
          {JSON.stringify(item.metadata, null, 2)}
        </pre>
      )}
    </div>
  );
}

// =============================================================================
// Page
// =============================================================================

/**
 * MemoryPlaneViewerPage — browse agent memory items by type.
 *
 * @doc.type component
 * @doc.purpose Memory plane viewer with tab navigation and search
 * @doc.layer product
 * @doc.pattern Page
 */
export function MemoryPlaneViewerPage(): React.ReactElement {
  const qc = useQueryClient();
  const [activeType, setActiveType] = useState<MemoryType>('EPISODIC');
  const [search, setSearch] = useState('');
  const [agentFilter, setAgentFilter] = useState('');

  const { data: items = [], isLoading, error } = useQuery({
    queryKey: ['dc', 'memory', activeType, agentFilter],
    queryFn: () =>
      memoryService.listMemoryItems({
        type: activeType,
        agentId: agentFilter || undefined,
        query: search || undefined,
      }),
    staleTime: 30_000,
  });

  const { data: consolidation } = useQuery({
    queryKey: ['dc', 'memory', 'consolidation'],
    queryFn: () => memoryService.getConsolidationStatus(),
    refetchInterval: 60_000,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => memoryService.deleteMemoryItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dc', 'memory'] }),
  });

  const filteredItems = search
    ? items.filter(
        (i) =>
          i.content.toLowerCase().includes(search.toLowerCase()) ||
          i.tags.some((t) => t.toLowerCase().includes(search.toLowerCase())),
      )
    : items;

  return (
    <div className="flex flex-col h-full bg-gray-50" data-testid="memory-plane-viewer">
      {/* Header */}
      <div className="px-6 py-4 bg-white border-b border-gray-200">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Memory Plane Viewer</h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Browse agent memory items across episodic, semantic, procedural, and preference tiers
            </p>
          </div>
          {consolidation && (
            <div className="text-right text-xs text-gray-500 space-y-0.5">
              <p>
                Last consolidation:{' '}
                <strong>
                  {consolidation.lastRun
                    ? new Date(consolidation.lastRun).toLocaleString()
                    : 'Never'}
                </strong>
              </p>
              <p>
                Episodes processed: <strong>{consolidation.episodesProcessed}</strong>
                {' | '}
                Policies extracted: <strong>{consolidation.policiesExtracted}</strong>
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Type Tabs */}
      <div className="bg-white border-b border-gray-200 px-6">
        <nav className="flex gap-1" role="tablist" aria-label="Memory type">
          {MEMORY_TYPES.map((type) => (
            <button
              key={type}
              role="tab"
              aria-selected={activeType === type}
              onClick={() => setActiveType(type)}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                activeType === type
                  ? 'border-indigo-600 text-indigo-700'
                  : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {type}
              <span className="ml-1 text-xs text-gray-400">
                ({TYPE_DESCRIPTIONS[type].split(' ')[0]})
              </span>
            </button>
          ))}
        </nav>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 px-6 py-3 bg-white border-b border-gray-200">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search content or tags…"
          className="px-3 py-1.5 text-sm border border-gray-300 rounded flex-1 max-w-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          aria-label="Search memory items"
        />
        <input
          type="text"
          value={agentFilter}
          onChange={(e) => setAgentFilter(e.target.value)}
          placeholder="Filter by agent ID…"
          className="px-3 py-1.5 text-sm border border-gray-300 rounded w-52 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          aria-label="Filter by agent"
        />
        <span className="text-sm text-gray-500 ml-auto">
          {filteredItems.length} item{filteredItems.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {isLoading && (
          <div className="flex justify-center items-center h-32 text-gray-400">
            Loading memory items…
          </div>
        )}
        {error instanceof Error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
            Failed to load memory: {error.message}
          </div>
        )}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          {filteredItems.map((item) => (
            <MemoryCard
              key={item.id}
              item={item}
              onDelete={(id) => deleteMutation.mutate(id)}
            />
          ))}
        </div>
        {!isLoading && filteredItems.length === 0 && (
          <div className="flex flex-col items-center justify-center h-32 text-gray-400 text-sm">
            <p>No {activeType.toLowerCase()} memory items found</p>
            {search && <p className="text-xs mt-1">Try clearing the search filter</p>}
          </div>
        )}
      </div>
    </div>
  );
}
