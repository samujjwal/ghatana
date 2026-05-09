import React from 'react';
import { Link } from 'react-router-dom';
import type { AiActionLogEntry } from '@/types/ai-action';
import { DashboardWidgetCard } from './DashboardWidgetCard';

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
}) => {
  const state = isLoading ? 'loading' : isError ? 'error' : 'ready';

  return (
    <div className="sm:col-span-2 lg:col-span-4">
      <DashboardWidgetCard
        testId="ai-action-log-widget"
        title="Recent AI Actions"
        state={state}
        message="Failed to load action log."
        stateMessageTestId={state === 'loading' ? 'ai-action-log-loading' : 'ai-action-log-error'}
        footer={(
          <Link
            to={`/workspaces/${workspaceId}/ai-actions`}
            data-testid="ai-action-log-link"
            className="block mt-3 text-xs text-blue-600 hover:underline"
          >
            View timeline
          </Link>
        )}
      >
        {entries.length === 0 ? (
          <p data-testid="ai-action-log-empty" className="text-xs text-gray-500">
            No AI actions recorded yet.
          </p>
        ) : (
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
      </DashboardWidgetCard>
    </div>
  );
};
