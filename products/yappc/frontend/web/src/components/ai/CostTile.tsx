/**
 * CostTile — YAPPC Web.
 *
 * Displays a summary of AI agent run counts and estimated cost for a project.
 * Implements audit requirement F-Y044: every project dashboard must surface
 * the AI cost accumulated so far, so product owners can make informed decisions.
 *
 * Data source: `GET /api/projects/:projectId/ai-cost` (indicative rates).
 * The `costBasis` field advertises whether the figure is exact or estimated.
 *
 * @doc.type component
 * @doc.purpose Per-project AI cost visibility tile
 * @doc.layer product
 * @doc.pattern UI Widget
 */

import React from 'react';
import { useProjectAiCost } from '@/hooks/useProjectAiCost';
import { useTranslation } from '@ghatana/i18n';

// ── Props ──────────────────────────────────────────────────────────────────────

export interface CostTileProps {
  projectId: string;
  /** Optional CSS class applied to the outer container. */
  className?: string;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatCost(usd: number): string {
  if (usd === 0) return '$0.00';
  if (usd < 0.01) return `< $0.01`;
  return `$${usd.toFixed(2)}`;
}

// ── Component ──────────────────────────────────────────────────────────────────

const CostTile: React.FC<CostTileProps> = ({ projectId, className }) => {
  const { t } = useTranslation('common');
  const { data, isLoading, isError } = useProjectAiCost(projectId);

  if (isLoading) {
    return (
      <div
        role="status"
        aria-label={t('ai.cost.loading')}
        className={`rounded-lg border border-neutral-200 bg-white p-4 shadow-sm ${className ?? ''}`}
      >
        <div className="h-4 w-24 animate-pulse rounded bg-neutral-200" />
        <div className="mt-2 h-7 w-16 animate-pulse rounded bg-neutral-200" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div
        role="alert"
        className={`rounded-lg border border-destructive-border bg-destructive-bg p-4 text-sm text-destructive ${className ?? ''}`}
      >
        Could not load AI cost data.
      </div>
    );
  }

  const topAgents = Object.entries(data.byAgent)
    .sort(([, a], [, b]) => b.count - a.count)
    .slice(0, 3);

  return (
    <div
      className={`rounded-lg border border-neutral-200 bg-white p-4 shadow-sm ${className ?? ''}`}
      aria-label={t('ai.cost.tile')}
    >
      {/* Header */}
      <div className="mb-1 flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500">
          AI Cost
        </span>
        {data.costBasis === 'indicative' && (
          <span
            title="Cost is estimated based on a flat per-run rate. Actual token costs will be shown once AIMetric is project-scoped."
            className="cursor-help rounded-full bg-warning-bg px-2 py-0.5 text-[10px] font-medium text-warning-color"
          >
            Estimate
          </span>
        )}
      </div>

      {/* Primary metric */}
      <p className="text-2xl font-bold text-neutral-900">
        {formatCost(data.estimatedCostUSD)}
        <span className="ml-1 text-sm font-normal text-neutral-500">{data.currency}</span>
      </p>

      {/* Secondary metrics */}
      <div className="mt-2 flex gap-4 text-xs text-neutral-500">
        <span>
          <span className="font-medium text-neutral-700">{data.totalRuns}</span> runs total
        </span>
        <span>
          <span className="font-medium text-neutral-700">{data.succeededRuns}</span> succeeded
        </span>
      </div>

      {/* Top agent breakdown */}
      {topAgents.length > 0 && (
        <ul className="mt-3 space-y-1 border-t border-neutral-100 pt-3" aria-label={t('ai.cost.topAgents')}>
          {topAgents.map(([agentName, entry]) => (
            <li key={agentName} className="flex items-center justify-between text-xs">
              <span className="truncate text-neutral-600" title={agentName}>
                {agentName}
              </span>
              <span className="ml-2 shrink-0 font-medium text-neutral-700">
                {entry.count}×&nbsp;{formatCost(entry.estimatedCostUSD)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export { CostTile };
export default CostTile;
