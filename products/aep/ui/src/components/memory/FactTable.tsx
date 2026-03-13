/**
 * FactTable — displays semantic memory facts (subject/predicate/object triples)
 * for a specific agent, with confidence scoring and validity status indicators.
 *
 * Facts are sourced from the SEMANTIC memory partition of dc_memory via
 * /api/v1/agents/:agentId/memory/facts.
 *
 * @doc.type component
 * @doc.purpose Display agent semantic facts with filtering and confidence visibility
 * @doc.layer frontend
 */
import React, { useState, useMemo } from 'react';
import type { AgentFact } from '@/api/aep.api';
import { ConfidenceBadge } from '@/components/shared/ConfidenceBadge';

// ─── Validity Status ──────────────────────────────────────────────────────────

const VALIDITY_STYLES: Record<string, string> = {
  ACTIVE:
    'bg-green-50 text-green-700 border-green-200 dark:bg-green-950 dark:text-green-300 dark:border-green-800',
  STALE:
    'bg-yellow-50 text-yellow-700 border-yellow-200 dark:bg-yellow-950 dark:text-yellow-300 dark:border-yellow-800',
  ARCHIVED:
    'bg-gray-100 text-gray-500 border-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-700',
  RETRACTED:
    'bg-red-50 text-red-600 border-red-200 dark:bg-red-950 dark:text-red-400 dark:border-red-800',
};

function ValidityBadge({ status }: { status?: string }) {
  const s = status ?? 'UNKNOWN';
  const cls = VALIDITY_STYLES[s] ?? VALIDITY_STYLES.ARCHIVED;
  return (
    <span
      className={[
        'inline-flex px-2 py-0.5 rounded-full text-xs font-medium border',
        cls,
      ].join(' ')}
    >
      {s}
    </span>
  );
}

// ─── Props ────────────────────────────────────────────────────────────────────

export interface FactTableProps {
  facts: AgentFact[];
  isLoading?: boolean;
  isError?: boolean;
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * Renders a filterable table of semantic memory facts.
 *
 * Each fact is a subject–predicate–object triple extracted by the agent's
 * reflect() phase. The table allows free-text search across subject, predicate,
 * and object fields.
 */
export function FactTable({ facts, isLoading = false, isError = false }: FactTableProps) {
  const [filter, setFilter] = useState('');

  const displayed = useMemo(() => {
    const q = filter.toLowerCase().trim();
    if (!q) return facts;
    return facts.filter(
      (f) =>
        f.subject?.toLowerCase().includes(q) ||
        f.predicate?.toLowerCase().includes(q) ||
        f.object?.toLowerCase().includes(q),
    );
  }, [facts, filter]);

  if (isLoading) {
    return (
      <p className="text-gray-400 text-center py-8 text-sm">Loading semantic facts…</p>
    );
  }

  if (isError) {
    return (
      <p className="text-red-500 text-center py-8 text-sm">
        Failed to load semantic facts. DataCloud may not be configured.
      </p>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 overflow-hidden">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-800 gap-3">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-white whitespace-nowrap">
          Semantic Facts
          {facts.length > 0 && (
            <span className="ml-2 text-xs font-normal text-gray-400">
              ({facts.length} total)
            </span>
          )}
        </h3>
        <input
          type="search"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filter by subject, predicate, or object…"
          className="flex-1 max-w-xs rounded-md border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-sm px-3 py-1.5 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          aria-label="Filter facts"
        />
      </div>

      {/* Empty state */}
      {facts.length === 0 ? (
        <p className="text-gray-400 text-center py-8 text-sm italic">
          No semantic facts extracted yet. Facts appear after the agent reflects on completed
          episodes.
        </p>
      ) : displayed.length === 0 ? (
        <p className="text-gray-400 text-center py-8 text-sm italic">
          No facts match &quot;{filter}&quot;
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm min-w-[640px]">
            <thead>
              <tr className="text-xs text-gray-500 uppercase tracking-wider border-b border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-950">
                <th className="px-4 py-2 text-left font-medium">Subject</th>
                <th className="px-4 py-2 text-left font-medium">Predicate</th>
                <th className="px-4 py-2 text-left font-medium">Object</th>
                <th className="px-4 py-2 text-left font-medium">Confidence</th>
                <th className="px-4 py-2 text-left font-medium">Status</th>
                <th className="px-4 py-2 text-left font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {displayed.map((fact) => (
                <tr
                  key={fact.id}
                  className="border-b border-gray-50 dark:border-gray-900 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <td className="px-4 py-2.5 text-gray-900 dark:text-white max-w-[160px] truncate font-medium">
                    {fact.subject ?? <span className="text-gray-300 italic">—</span>}
                  </td>
                  <td className="px-4 py-2.5 text-indigo-600 dark:text-indigo-400 font-mono text-xs">
                    {fact.predicate ?? <span className="text-gray-300 italic not-italic">—</span>}
                  </td>
                  <td className="px-4 py-2.5 text-gray-700 dark:text-gray-300 max-w-[200px] truncate">
                    {fact.object ?? <span className="text-gray-300 italic">—</span>}
                  </td>
                  <td className="px-4 py-2.5">
                    <ConfidenceBadge value={fact.confidence} />
                  </td>
                  <td className="px-4 py-2.5">
                    <ValidityBadge status={fact.validityStatus} />
                  </td>
                  <td className="px-4 py-2.5 text-gray-400 text-xs whitespace-nowrap">
                    {fact.createdAt
                      ? new Date(fact.createdAt).toLocaleString()
                      : <span className="italic">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
