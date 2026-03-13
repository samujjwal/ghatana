/**
 * RunTable — live paginated table of pipeline runs in the monitoring dashboard.
 *
 * @doc.type component
 * @doc.purpose Display pipeline execution runs with status and metrics
 * @doc.layer frontend
 */
import React from 'react';
import type { PipelineRun } from '@/api/aep.api';

interface RunTableProps {
  runs: PipelineRun[];
  onCancel?: (runId: string) => void;
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

export function RunTable({ runs, onCancel, className = '' }: RunTableProps) {
  if (runs.length === 0) {
    return (
      <p className={['text-sm text-gray-400', className].join(' ')}>No pipeline runs found.</p>
    );
  }

  return (
    <div className={['overflow-x-auto', className].join(' ')}>
      <table className="w-full text-sm border-collapse">
        <thead className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide bg-gray-50 dark:bg-gray-900">
          <tr>
            <th className="px-3 py-2 text-left font-medium">Pipeline</th>
            <th className="px-3 py-2 text-left font-medium">Status</th>
            <th className="px-3 py-2 text-left font-medium">Started</th>
            <th className="px-3 py-2 text-right font-medium">Events</th>
            <th className="px-3 py-2 text-right font-medium">Errors</th>
            {onCancel && <th className="px-3 py-2" />}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
          {runs.map((run) => (
            <tr
              key={run.id}
              className="hover:bg-gray-50 dark:hover:bg-gray-900 transition-colors"
            >
              <td className="px-3 py-2 font-medium text-gray-800 dark:text-gray-200 max-w-[14rem] truncate">
                {run.pipelineName}
              </td>
              <td className="px-3 py-2">
                <span
                  className={[
                    'inline-block px-2 py-0.5 rounded-full text-xs font-medium',
                    STATUS_STYLES[run.status],
                  ].join(' ')}
                >
                  {run.status}
                </span>
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
              {onCancel && (
                <td className="px-3 py-2 text-right">
                  {run.status === 'RUNNING' && (
                    <button
                      onClick={() => onCancel(run.id)}
                      className="text-xs text-red-500 hover:text-red-700 font-medium"
                    >
                      Cancel
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
