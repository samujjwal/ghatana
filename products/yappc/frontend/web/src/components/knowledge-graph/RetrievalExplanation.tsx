/**
 * RetrievalExplanation — AI-Y7 — Knowledge-graph retrieval explanation
 *
 * Shows WHY specific knowledge-graph nodes were retrieved for a given
 * AI prompt or run. Renders the retrieval reason, similarity score, and
 * the matched context for each retrieved node.
 *
 * Fetches from `GET /api/runs/:runId/retrieval-explanation`
 *
 * @doc.type component
 * @doc.purpose Explain why knowledge-graph nodes were retrieved for an AI run
 * @doc.layer product
 * @doc.pattern AI Explainability
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { HelpCircle, Database } from 'lucide-react';
import { useTranslation } from '@ghatana/i18n';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface RetrievedNodeExplanation {
  nodeId: string;
  nodeLabel: string;
  nodeType: string;
  /** Cosine-similarity score [0, 1] */
  score: number;
  /** Snippet from the node that matched the query */
  matchedContext: string;
  /** Human-readable reason why this node was selected */
  retrievalReason: string;
}

export interface RetrievalExplanationData {
  runId: string;
  query: string;
  nodes: RetrievedNodeExplanation[];
}

export interface RetrievalExplanationProps {
  runId: string;
  /** Optional CSS class */
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

const importMetaEnv = import.meta as ImportMeta & {
  env?: { DEV?: boolean; VITE_API_ORIGIN?: string };
};

function apiBase(): string {
  return importMetaEnv.env?.DEV
    ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
    : '/api';
}

async function fetchRetrievalExplanation(runId: string): Promise<RetrievalExplanationData> {
  const res = await fetch(`${apiBase()}/runs/${encodeURIComponent(runId)}/retrieval-explanation`, {
    credentials: 'include',
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch retrieval explanation: ${res.status}`);
  }
  return res.json() as Promise<RetrievalExplanationData>;
}

// ── Score chip ────────────────────────────────────────────────────────────────

function ScoreChip({ score }: { score: number }) {
  const pct = Math.round(score * 100);
  const colour =
    pct >= 80 ? 'text-success-color bg-success-bg' :
    pct >= 60 ? 'text-warning-color bg-warning-bg' :
                'text-text-secondary bg-bg-paper';
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${colour}`}>
      {pct}% match
    </span>
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function RetrievalExplanation({ runId, className }: RetrievalExplanationProps) {
  const { t } = useTranslation('common');
  const { data, isLoading, isError } = useQuery({
    queryKey: ['retrieval-explanation', runId],
    queryFn: () => fetchRetrievalExplanation(runId),
    staleTime: 60_000,
    enabled: Boolean(runId),
  });

  if (isLoading) {
    return (
      <div
        className={['text-sm text-text-secondary', className].filter(Boolean).join(' ')}
        data-testid="retrieval-loading"
      >
        Loading retrieval explanation…
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div
        className={['text-sm text-error-color', className].filter(Boolean).join(' ')}
        data-testid="retrieval-error"
        role="alert"
      >
        Could not load retrieval explanation.
      </div>
    );
  }

  if (data.nodes.length === 0) {
    return (
      <div
        className={['text-sm text-text-secondary', className].filter(Boolean).join(' ')}
        data-testid="retrieval-empty"
      >
        No knowledge-graph nodes retrieved for this run.
      </div>
    );
  }

  return (
    <div
      className={['space-y-3', className].filter(Boolean).join(' ')}
      data-testid="retrieval-explanation"
    >
      <div className="flex items-center gap-2">
        <Database className="h-4 w-4 text-text-secondary" aria-hidden="true" />
        <span className="text-sm font-semibold text-text-primary">Retrieved knowledge</span>
        <HelpCircle className="h-3.5 w-3.5 text-text-secondary" aria-label={t('knowledgeGraph.retrievalWhy')} />
      </div>

      {data.nodes.map((node) => (
        <div
          key={node.nodeId}
          className="rounded-md border border-divider bg-bg-paper p-3 space-y-1"
          data-testid={`retrieval-node-${node.nodeId}`}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-medium text-text-primary">{node.nodeLabel}</span>
            <ScoreChip score={node.score} />
          </div>
          <p className="text-xs text-text-secondary">{node.retrievalReason}</p>
          {node.matchedContext && (
            <blockquote className="mt-1 border-l-2 border-primary pl-2 text-xs text-text-secondary italic">
              {node.matchedContext}
            </blockquote>
          )}
        </div>
      ))}
    </div>
  );
}
