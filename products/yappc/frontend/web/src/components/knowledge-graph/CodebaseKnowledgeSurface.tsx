/**
 * CodebaseKnowledgeSurface
 *
 * Surfaces what the AI currently knows about the codebase:
 * knowledge-graph entity counts, semantic cache stats, and retrieval
 * explanation for the latest run — all in one overview panel.
 *
 * @doc.type component
 * @doc.purpose "What does AI know about my codebase" overview surface
 * @doc.layer product
 * @doc.pattern Data Display
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Brain, Database, Search, RefreshCw, AlertCircle, Loader2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { AIAssistLabel } from '../ai/AIAssistLabel';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface KgEntitySummary {
  type: string;
  count: number;
}

export interface CacheStats {
  totalEntries: number;
  hitRateLast24h: number;
  topQueryTypes: string[];
}

export interface CodebaseKnowledgeData {
  projectId: string;
  entitySummary: KgEntitySummary[];
  relationCount: number;
  cacheStats: CacheStats;
  lastIndexedAt: string;
  indexedFilesCount: number;
  source: 'model' | 'hybrid';
  confidence: number;
}

export interface CodebaseKnowledgeSurfaceProps {
  projectId: string;
  /** Latest AEP run for which to show retrieval explanation link */
  latestRunId?: string;
  className?: string;
}

// ── API ───────────────────────────────────────────────────────────────────────

const BASE =
  (import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } }).env?.DEV
    ? 'http://localhost:8080'
    : '';

async function fetchCodebaseKnowledge(projectId: string): Promise<CodebaseKnowledgeData> {
  const res = await fetch(`${BASE}/api/projects/${projectId}/ai-knowledge`, {
    credentials: 'include',
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<CodebaseKnowledgeData>;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function CodebaseKnowledgeSurface({
  projectId,
  latestRunId,
  className,
}: CodebaseKnowledgeSurfaceProps): React.ReactElement | null {
  if (!projectId) return null;

  const { t } = useTranslation('common');

  const { data, isLoading, isError, refetch } = useQuery<CodebaseKnowledgeData>({
    queryKey: ['codebase-knowledge', projectId],
    queryFn: () => fetchCodebaseKnowledge(projectId),
    staleTime: 60_000,
  });

  return (
    <div
      className={cn('rounded-xl border border-border bg-surface p-5', className)}
      data-testid="codebase-knowledge-surface"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-2">
          <Brain size={18} className="text-violet-400" />
          <h2 className="text-base font-semibold text-fg-muted">What the assistant knows about your codebase</h2>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => { void refetch(); }}
          className="p-1.5 rounded-lg hover:bg-surface text-fg-muted hover:text-fg-muted transition-colors"
          aria-label={t('knowledge.refresh')}
          data-testid="knowledge-refresh-btn"
        >
          <RefreshCw size={14} />
        </Button>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="flex items-center gap-2 text-fg-muted py-4" data-testid="knowledge-loading">
          <Loader2 size={16} className="animate-spin" />
          <span className="text-sm">Loading knowledge summary…</span>
        </div>
      )}

      {/* Error */}
      {isError && (
        <div className="flex items-center gap-2 text-destructive text-sm py-4" data-testid="knowledge-error">
          <AlertCircle size={16} />
          <span>Failed to load knowledge. Check indexing status.</span>
        </div>
      )}

      {/* Data */}
      {data && (
        <div className="space-y-5" data-testid="knowledge-content">
          {/* AI assist label */}
          <AIAssistLabel source={data.source} confidence={data.confidence} />

          {/* Knowledge-graph entities */}
          <section data-testid="knowledge-entities">
            <div className="flex items-center gap-2 mb-2">
              <Database size={14} className="text-fg-muted" />
              <span className="text-xs font-medium text-fg-muted uppercase tracking-wider">
                Knowledge Graph
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {data.entitySummary.map((e) => (
                <div
                  key={e.type}
                  className="rounded-lg bg-surface px-3 py-2"
                  data-testid={`entity-type-${e.type.toLowerCase()}`}
                >
                  <div className="text-lg font-semibold text-fg-muted">{e.count}</div>
                  <div className="text-xs text-fg-muted">{e.type}</div>
                </div>
              ))}
              <div className="rounded-lg bg-surface px-3 py-2" data-testid="relation-count">
                <div className="text-lg font-semibold text-fg-muted">{data.relationCount}</div>
                <div className="text-xs text-fg-muted">Relations</div>
              </div>
            </div>
          </section>

          {/* Semantic cache */}
          <section data-testid="knowledge-cache">
            <div className="flex items-center gap-2 mb-2">
              <Search size={14} className="text-fg-muted" />
              <span className="text-xs font-medium text-fg-muted uppercase tracking-wider">
                Semantic Cache
              </span>
            </div>
            <div className="rounded-lg bg-surface px-4 py-3 space-y-1">
              <div className="flex justify-between text-sm">
                <span className="text-fg-muted">Cached entries</span>
                <span className="text-fg-muted font-medium">{data.cacheStats.totalEntries}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-fg-muted">Hit rate (24 h)</span>
                <span className="text-fg-muted font-medium">
                  {Math.round(data.cacheStats.hitRateLast24h * 100)}%
                </span>
              </div>
              {data.cacheStats.topQueryTypes.length > 0 && (
                <div className="flex justify-between text-sm">
                  <span className="text-fg-muted">Top query types</span>
                  <span className="text-fg-muted font-medium">
                    {data.cacheStats.topQueryTypes.slice(0, 3).join(', ')}
                  </span>
                </div>
              )}
            </div>
          </section>

          {/* Index info */}
          <div className="text-xs text-fg-muted">
            {data.indexedFilesCount} files indexed · last updated{' '}
            {new Date(data.lastIndexedAt).toLocaleString()}
          </div>

          {/* Link to retrieval explanation if run available */}
          {latestRunId && (
            <div className="text-xs text-violet-400 underline cursor-pointer" data-testid="retrieval-explanation-link">
              View retrieval explanation for latest run →
            </div>
          )}
        </div>
      )}
    </div>
  );
}
