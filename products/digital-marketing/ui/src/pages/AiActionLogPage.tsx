import React from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import { useAiActionDetail, useAiActionLog } from '@/hooks/useAiActionLog';

export function AiActionLogPage(): React.ReactElement {
  const { workspaceId, actionId } = useParams<{ workspaceId: string; actionId?: string }>();
  const { isAuthenticated } = useAuth();

  const { entries, isLoading, isError, error } = useAiActionLog(workspaceId ?? null);
  const detail = useAiActionDetail(workspaceId ?? null, actionId ?? null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main data-testid="ai-action-log-page" className="max-w-6xl mx-auto px-4 py-8 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">AI Action Log</h1>
        <Link
          to={`/workspaces/${workspaceId}/dashboard`}
          className="text-sm text-blue-600 hover:underline"
        >
          Back to dashboard
        </Link>
      </div>

      {isLoading && <p data-testid="ai-action-log-page-loading" className="text-sm text-gray-400">Loading…</p>}
      {isError && (
        <p data-testid="ai-action-log-page-error" role="alert" className="text-sm text-red-600">
          {error instanceof Error ? error.message : 'Failed to load action log'}
        </p>
      )}

      {!isLoading && !isError && entries.length === 0 && (
        <p data-testid="ai-action-log-page-empty" className="text-sm text-gray-500">
          No actions recorded.
        </p>
      )}

      {!isLoading && !isError && entries.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <section className="border rounded-lg p-4">
            <h2 className="text-sm font-semibold text-gray-700 mb-2">Timeline</h2>
            <ul className="space-y-2">
              {entries.map((entry) => (
                <li key={entry.actionId} data-testid={`ai-action-log-item-${entry.actionId}`} className="border rounded p-2 text-sm">
                  <div className="font-medium">{entry.summary}</div>
                  <div className="text-xs text-gray-500">
                    {entry.status} · {entry.actionType} · correlation {entry.correlationId}
                  </div>
                  <Link
                    to={`/workspaces/${workspaceId}/ai-actions/${entry.actionId}`}
                    data-testid={`ai-action-log-detail-link-${entry.actionId}`}
                    className="text-xs text-blue-600 hover:underline"
                  >
                    View details
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <section className="border rounded-lg p-4" data-testid="ai-action-log-detail-panel">
            <h2 className="text-sm font-semibold text-gray-700 mb-2">Details</h2>
            {!actionId && <p className="text-xs text-gray-500">Select an action from the timeline.</p>}
            {detail.isLoading && <p className="text-xs text-gray-400">Loading detail…</p>}
            {detail.isError && <p className="text-xs text-red-600">Failed to load detail.</p>}
            {detail.entry && (
              <div className="text-xs space-y-2">
                <div><strong>Summary:</strong> {detail.entry.summary}</div>
                <div><strong>Details:</strong> {detail.entry.details}</div>
                <div><strong>Actor:</strong> {detail.entry.actor}</div>
                <div><strong>Status:</strong> {detail.entry.status}</div>
                <div><strong>Correlation:</strong> {detail.entry.correlationId}</div>
                <div><strong>Evidence links:</strong> {detail.entry.evidenceLinks.length}</div>
              </div>
            )}
          </section>
        </div>
      )}
    </main>
  );
}
