/**
 * RunTable — live paginated table of pipeline runs in the monitoring dashboard.
 *
 * @doc.type component
 * @doc.purpose Display pipeline execution runs with status and metrics
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Link } from 'react-router';
import type { PipelineRun } from '@/api/aep.api';
import type { AiSuggestion } from './AiSuggestionsPanel';
import { Button } from '@ghatana/design-system';
import { Zap } from 'lucide-react';
import { ReviewDecisionDialog } from '@/components/shared/ReviewDecisionDialog';
import { ConfidenceExplanation } from '@/components/shared/ConfidenceExplanation';
import {
  getAiConfidenceTier,
  getAiRouting,
  getAiRoutingDescription,
  getAiRoutingLabel,
} from '@/lib/ai-assist';

interface RunTableProps {
  runs: PipelineRun[];
  onCancel?: (runId: string) => void;
  onApprove?: (runId: string) => void;
  onReject?: (runId: string, reason: string) => void;
  selectedIds?: Set<string>;
  onSelectToggle?: (runId: string) => void;
  onSelectAll?: () => void;
  isAllSelected?: boolean;
  isIndeterminate?: boolean;
  aiSuggestions?: AiSuggestion[];
  onMarkFalsePositive?: (suggestion: AiSuggestion) => void;
  className?: string;
}

const STATUS_STYLES: Record<PipelineRun['status'], string> = {
  RUNNING: 'text-blue-600 bg-blue-50 dark:text-blue-300 dark:bg-blue-950',
  SUCCEEDED: 'text-green-700 bg-green-50 dark:text-green-300 dark:bg-green-950',
  FAILED: 'text-red-700 bg-red-50 dark:text-red-300 dark:bg-red-950',
  CANCELLED: 'text-gray-500 bg-gray-100 dark:text-gray-400 dark:bg-gray-800',
};

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function AiSuggestionPill({
  suggestion,
  expanded,
  onToggle,
}: {
  suggestion: AiSuggestion;
  expanded: boolean;
  onToggle: () => void;
}) {
  const routing = getAiRouting(suggestion.confidence);
  return (
    <button
      type="button"
      onClick={onToggle}
      className={[
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium',
        suggestion.severity === 'critical' || suggestion.severity === 'high'
          ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'
          : 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
        expanded ? 'ring-1 ring-current/40' : '',
      ].join(' ')}
      aria-label={`${suggestion.type} suggestion: ${suggestion.message}. ${getAiRoutingLabel(routing)}`}
    >
      <Zap className="h-3 w-3" aria-hidden="true" />
      {suggestion.message.length > 40 ? suggestion.message.slice(0, 38) + '…' : suggestion.message}
    </button>
  );
}

export function RunTable({
  runs,
  onCancel,
  onApprove,
  onReject,
  selectedIds,
  onSelectToggle,
  onSelectAll,
  isAllSelected,
  isIndeterminate,
  aiSuggestions,
  onMarkFalsePositive,
  className,
}: RunTableProps) {
  const [rejectTarget, setRejectTarget] = useState<{ runId: string; pipelineName: string } | null>(null);
  const [expandedSuggestionId, setExpandedSuggestionId] = useState<string | null>(null);
  if (runs.length === 0) {
    return (
      <p className={['text-sm text-gray-400', className].join(' ')}>No pipeline runs found.</p>
    );
  }

  return (
    <div className={['overflow-x-auto', className].join(' ')}>
      <table className="w-full text-sm border-collapse" role="table" aria-label="Pipeline runs">
        <caption className="sr-only">Pipeline execution runs with status, metrics, and actions</caption>
        <thead className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide bg-gray-50 dark:bg-gray-900">
          <tr>
            {onSelectToggle && (
              <th className="px-3 py-2 w-10" scope="col">
                <input
                  type="checkbox"
                  checked={isAllSelected ?? false}
                  ref={(el) => {
                    if (el && isIndeterminate) {
                      el.indeterminate = true;
                    }
                  }}
                  onChange={onSelectAll}
                  className="rounded border-gray-300 dark:border-gray-600 text-indigo-600 focus:ring-indigo-500 dark:bg-gray-900"
                  aria-label="Select all runs"
                />
              </th>
            )}
            <th className="px-3 py-2 text-left font-medium" scope="col">Pipeline</th>
            <th className="px-3 py-2 text-left font-medium" scope="col">Status</th>
            <th className="px-3 py-2 text-left font-medium" scope="col">Started</th>
            <th className="px-3 py-2 text-right font-medium" scope="col">Events</th>
            <th className="px-3 py-2 text-right font-medium" scope="col">Errors</th>
            {(onApprove || onReject) && (
              <th className="px-3 py-2 text-right font-medium" scope="col">Actions</th>
            )}
            <th className="px-3 py-2" scope="col" aria-label="Details" />
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
          {runs.map((run) => (
            <tr
              key={run.id}
              className="hover:bg-gray-50 dark:hover:bg-gray-900 transition-colors"
            >
              {onSelectToggle && (
                <td className="px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selectedIds?.has(run.id) ?? false}
                    onChange={() => onSelectToggle(run.id)}
                    className="rounded border-gray-300 dark:border-gray-600 text-indigo-600 focus:ring-indigo-500 dark:bg-gray-900"
                    aria-label={`Select run ${run.id}`}
                  />
                </td>
              )}
              <td className="px-3 py-2 font-medium text-gray-800 dark:text-gray-200 max-w-[14rem] truncate">
                <Link
                  to={`/operate/runs/${run.id}`}
                  className="hover:text-indigo-600 dark:hover:text-indigo-400 hover:underline transition-colors"
                >
                  {run.pipelineName}
                </Link>
              </td>
              <td className="px-3 py-2">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span
                    className={[
                      'inline-block px-2 py-0.5 rounded-full text-xs font-medium',
                      STATUS_STYLES[run.status],
                    ].join(' ')}
                  >
                    {run.status}
                  </span>
                  {(() => {
                    const runSuggestions = (aiSuggestions ?? []).filter((s) => s.runId === run.id);
                    if (runSuggestions.length === 0) return null;
                    return (
                      <div className="flex flex-col gap-2">
                        <div className="flex flex-wrap gap-1">
                          {runSuggestions.slice(0, 2).map((suggestion) => (
                            <AiSuggestionPill
                              key={suggestion.id}
                              suggestion={suggestion}
                              expanded={expandedSuggestionId === suggestion.id}
                              onToggle={() =>
                                setExpandedSuggestionId((current) => current === suggestion.id ? null : suggestion.id)
                              }
                            />
                          ))}
                          {runSuggestions.length > 2 && (
                            <span className="text-[10px] text-gray-400 dark:text-gray-500">+{runSuggestions.length - 2}</span>
                          )}
                        </div>
                        {runSuggestions.map((suggestion) => {
                          if (expandedSuggestionId !== suggestion.id) {
                            return null;
                          }
                          const routing = getAiRouting(suggestion.confidence);
                          return (
                            <div
                              key={`${suggestion.id}-detail`}
                              className="rounded-lg border border-gray-200 bg-white p-3 text-left dark:border-gray-700 dark:bg-gray-900"
                            >
                              <div className="mb-2 flex flex-wrap items-center gap-2">
                                <span className="text-xs font-semibold text-gray-900 dark:text-gray-100">
                                  Assist narrative
                                </span>
                                <span className="rounded-full border border-gray-200 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-gray-600 dark:border-gray-700 dark:text-gray-300">
                                  {getAiRoutingLabel(routing)}
                                </span>
                              </div>
                              {suggestion.confidence !== undefined && (
                                <ConfidenceExplanation
                                  tier={getAiConfidenceTier(suggestion.confidence)}
                                  score={suggestion.confidence}
                                  reasoning={suggestion.rationale ?? getAiRoutingDescription(routing)}
                                  evidenceUrl={suggestion.sources?.find((source) => source.href)?.href}
                                />
                              )}
                              {suggestion.sources && suggestion.sources.length > 0 && (
                                <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-gray-500 dark:text-gray-400">
                                  {suggestion.sources.map((source) => (
                                    source.href ? (
                                      <a
                                        key={`${suggestion.id}-${source.label}`}
                                        href={source.href}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="rounded-full border border-gray-200 px-2 py-1 hover:underline dark:border-gray-700"
                                      >
                                        {source.label}
                                      </a>
                                    ) : (
                                      <span
                                        key={`${suggestion.id}-${source.label}`}
                                        className="rounded-full border border-gray-200 px-2 py-1 dark:border-gray-700"
                                      >
                                        {source.label}
                                      </span>
                                    )
                                  ))}
                                </div>
                              )}
                              {suggestion.type === 'anomaly' && suggestion.anomalyId && onMarkFalsePositive ? (
                                <div className="mt-3">
                                  <Button
                                    onClick={() => onMarkFalsePositive(suggestion)}
                                    variant="text"
                                    className="text-xs font-medium underline hover:opacity-75"
                                  >
                                    Mark as not an anomaly
                                  </Button>
                                </div>
                              ) : null}
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}
                </div>
              </td>
              <td className="px-3 py-2 text-gray-500 dark:text-gray-400 tabular-nums">
                {formatTime(run.startedAt)}
              </td>
              <td className="px-3 py-2 text-right tabular-nums text-gray-700 dark:text-gray-300">
                {run.eventsProcessed.toLocaleString()}
              </td>
              <td className="px-3 py-2 text-right tabular-nums">
                <span className={run.errorsCount > 0 ? 'text-red-600 font-medium' : 'text-gray-400'}>
                  {run.errorsCount}
                </span>
              </td>
              {(onApprove || onReject) && (
                <td className="px-3 py-2 text-right">
                  {(run.status === 'FAILED' || run.status === 'SUCCEEDED') && (
                    <div className="flex gap-2 justify-end">
                      {onApprove && (
                        <Button
                          onClick={() => onApprove(run.id)}
                          variant="text"
                          className="text-xs text-green-600 hover:text-green-700 font-medium"
                          aria-label={`Approve run ${run.id}`}
                        >
                          Approve
                        </Button>
                      )}
                      {onReject && (
                        <Button
                          onClick={() => setRejectTarget({ runId: run.id, pipelineName: run.pipelineName || run.pipelineId })}
                          variant="text"
                          className="text-xs text-red-500 hover:text-red-700 font-medium"
                          aria-label={`Reject run ${run.id}`}
                        >
                          Reject
                        </Button>
                      )}
                    </div>
                  )}
                </td>
              )}
              {onCancel && (
                <td className="px-3 py-2 text-right">
                  {run.status === 'RUNNING' && (
                    <Button
                      onClick={() => onCancel(run.id)}
                      variant="text"
                      className="text-xs text-red-500 hover:text-red-700 font-medium"
                      aria-label={`Cancel running pipeline ${run.pipelineName}`}
                    >
                      Cancel
                    </Button>
                  )}
                </td>
              )}
              <td className="px-3 py-2 text-right">
                <Link
                  to={`/operate/runs/${run.id}`}
                  className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                  aria-label={`View details for run ${run.id}`}
                >
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {rejectTarget && onReject && (
        <ReviewDecisionDialog
          open={!!rejectTarget}
          mode="reject"
          runId={rejectTarget.runId}
          onConfirm={(note) => {
            onReject(rejectTarget.runId, note);
            setRejectTarget(null);
          }}
          onCancel={() => setRejectTarget(null)}
        />
      )}
    </div>
  );
}
