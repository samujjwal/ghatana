import React from 'react';
import { Link } from 'react-router-dom';
import type { AiActionLogEntry } from '@/types/ai-action';

interface AiActionLogWidgetProps {
  workspaceId: string;
  entries: AiActionLogEntry[];
  isLoading: boolean;
  isError: boolean;
}

export const AiActionLogWidget: React.FC<AiActionLogWidgetProps> = ({
  workspaceId,
  entries,
  isLoading,
  isError,
}) => (
  <article
    aria-labelledby="ai-actions-title"
    data-testid="ai-action-log-widget"
    className="border rounded-lg p-4 space-y-2 sm:col-span-2 lg:col-span-4"
  >
    <div className="flex items-center justify-between">
      <h2 id="ai-actions-title" className="text-sm font-semibold text-gray-700">
        Recent AI Actions
      </h2>
      <Link
        to={`/workspaces/${workspaceId}/ai-actions`}
        data-testid="ai-action-log-link"
        className="text-xs text-blue-600 hover:underline"
      >
        View timeline
      </Link>
    </div>

    {isLoading && (
      <p data-testid="ai-action-log-loading" className="text-xs text-gray-400">
        Loading recent actions…
      </p>
    )}

    {isError && (
      <p data-testid="ai-action-log-error" role="alert" className="text-xs text-red-600">
        Failed to load action log.
      </p>
    )}

    {!isLoading && !isError && entries.length === 0 && (
      <p data-testid="ai-action-log-empty" className="text-xs text-gray-500">
        No AI actions recorded yet.
      </p>
    )}

    {!isLoading && !isError && entries.length > 0 && (
      <ul className="space-y-2">
        {entries.slice(0, 3).map((entry) => (
          <li
            key={entry.actionId}
            data-testid={`ai-action-row-${entry.actionId}`}
            className="text-xs border rounded p-2"
          >
            <div className="font-medium text-gray-700">{entry.summary}</div>
            <div className="text-gray-500">
              {entry.status} · {entry.actionType} · {new Date(entry.occurredAt).toLocaleString()}
            </div>
          </li>
        ))}
      </ul>
    )}
  </article>
);
