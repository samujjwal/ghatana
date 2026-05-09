/**
 * CostOptimisationPanel — AI-Y8
 *
 * Displays per-model cost breakdown for an AI run and surfaces cheaper
 * alternative models when the backend has identified them.  This gives
 * users actionable insight into reducing AI spend without sacrificing quality.
 *
 * ## Data contract
 * `GET /api/runs/:runId/cost-analysis`
 * ```json
 * {
 *   "runId": "abc",
 *   "totalCostUsd": 0.042,
 *   "breakdown": [
 *     {
 *       "model": "gpt-4o",
 *       "calls": 3,
 *       "costUsd": 0.036,
 *       "cheaperAlternative": { "model": "gpt-4o-mini", "estimatedSavingUsd": 0.024 }
 *     },
 *     { "model": "text-embedding-3-small", "calls": 1, "costUsd": 0.006 }
 *   ]
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Show AI run cost breakdown and suggest cheaper model alternatives
 * @doc.layer product
 * @doc.pattern Data Display
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2, AlertCircle, DollarSign, TrendingDown } from 'lucide-react';
import { useI18n } from '../../i18n/I18nProvider';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface CheaperAlternative {
  model: string;
  estimatedSavingUsd: number;
}

export interface ModelCostBreakdown {
  model: string;
  calls: number;
  costUsd: number;
  cheaperAlternative?: CheaperAlternative;
}

export interface CostAnalysisData {
  runId: string;
  totalCostUsd: number;
  breakdown: ModelCostBreakdown[];
}

export interface CostOptimisationPanelProps {
  /** AEP run ID to fetch cost analysis for. */
  runId: string;
  /** Additional CSS class names. */
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildApiUrl(runId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/runs/${runId}/cost-analysis`;
}

async function fetchCostAnalysis(runId: string): Promise<CostAnalysisData> {
  const res = await fetch(buildApiUrl(runId), { credentials: 'include' });
  if (!res.ok) throw new Error(`Failed to fetch cost analysis: ${res.status}`);
  return res.json() as Promise<CostAnalysisData>;
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function formatUsd(amount: number): string {
  return `$${amount.toFixed(4)}`;
}

function ModelRow({ entry }: { entry: ModelCostBreakdown }) {
  return (
    <div
      data-testid={`model-row-${entry.model}`}
      className="rounded-lg border border-border bg-surface p-3 space-y-1"
    >
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-foreground font-mono">{entry.model}</span>
        <span className="text-sm text-muted">
          {entry.calls} call{entry.calls !== 1 ? 's' : ''} · {formatUsd(entry.costUsd)}
        </span>
      </div>

      {entry.cheaperAlternative && (
        <div
          data-testid={`cheaper-alt-${entry.model}`}
          className="flex items-center gap-1.5 rounded bg-success-bg px-2 py-1 text-xs text-success-color"
        >
          <TrendingDown className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>
            Switch to <strong>{entry.cheaperAlternative.model}</strong> to save ~
            {formatUsd(entry.cheaperAlternative.estimatedSavingUsd)}
          </span>
        </div>
      )}
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

/**
 * Displays AI model cost breakdown with cheaper-alternative suggestions.
 * Disabled (renders nothing) when `runId` is empty.
 */
export function CostOptimisationPanel({ runId, className }: CostOptimisationPanelProps) {
  const { t } = useI18n();
  const { data, isLoading, isError } = useQuery<CostAnalysisData>({
    queryKey: ['cost-analysis', runId],
    queryFn: () => fetchCostAnalysis(runId),
    enabled: runId.length > 0,
    staleTime: 60_000,
  });

  if (!runId) return null;

  if (isLoading) {
    return (
      <div data-testid="cost-loading" className="flex items-center gap-2 p-4 text-sm text-muted">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        Loading cost analysis…
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div data-testid="cost-error" className="flex items-center gap-2 p-4 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        Could not load cost analysis.
      </div>
    );
  }

  const totalSavings = data.breakdown.reduce(
    (acc, b) => acc + (b.cheaperAlternative?.estimatedSavingUsd ?? 0),
    0
  );

  return (
    <section
      data-testid="cost-panel"
      aria-label={t('ai.cost.panelLabel')}
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
          <DollarSign className="h-4 w-4" aria-hidden="true" />
          Cost analysis
        </div>
        <span className="text-sm text-muted">
          Total: <strong>{formatUsd(data.totalCostUsd)}</strong>
        </span>
      </div>

      {/* Potential savings summary */}
      {totalSavings > 0 && (
        <div
          data-testid="cost-savings-summary"
          className="rounded-lg border border-success-border bg-success-bg px-3 py-2 text-xs text-success-color"
        >
          Potential saving: <strong>{formatUsd(totalSavings)}</strong> by switching to cheaper models below.
        </div>
      )}

      {/* Breakdown */}
      <div className="space-y-2">
        {data.breakdown.map((entry) => (
          <ModelRow key={entry.model} entry={entry} />
        ))}
      </div>
    </section>
  );
}
