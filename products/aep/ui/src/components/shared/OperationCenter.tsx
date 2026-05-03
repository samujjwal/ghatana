/**
 * OperationCenter — unified job/operation lifecycle UI.
 *
 * Shows active operations, retry controls, history, and audit links.
 * Designed for embedding in monitoring dashboards or as a standalone panel.
 *
 * @doc.type component
 * @doc.purpose Centralized operation lifecycle visibility with retry, history, audit
 * @doc.layer frontend
 * @doc.pattern Operations UI
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { Clock, CheckCircle2, XCircle, RotateCcw, AlertTriangle, ChevronRight } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { PageState } from './PageState';
import { listOperations, retryOperation, cancelOperation, type OperationRecord, type OperationStatus } from '@/api/aep.api';

interface OperationCenterProps {
  /** Override tenant; falls back to global tenant atom. */
  tenantId?: string;
  /** Maximum rows to display before truncation. */
  maxRows?: number;
  className?: string;
  onRetry?: (operationId: string) => void;
  onCancel?: (operationId: string) => void;
}

function statusIcon(status: OperationStatus) {
  switch (status) {
    case 'pending':
      return <Clock className="h-4 w-4 text-amber-500" aria-hidden />;
    case 'running':
      return <div className="animate-spin h-4 w-4 border-2 border-indigo-600 border-t-transparent rounded-full" aria-hidden />;
    case 'completed':
      return <CheckCircle2 className="h-4 w-4 text-green-600" aria-hidden />;
    case 'failed':
      return <XCircle className="h-4 w-4 text-red-600" aria-hidden />;
    case 'cancelled':
      return <AlertTriangle className="h-4 w-4 text-gray-500" aria-hidden />;
  }
}

function statusLabel(status: OperationStatus): string {
  switch (status) {
    case 'pending': return 'Pending';
    case 'running': return 'Running';
    case 'completed': return 'Completed';
    case 'failed': return 'Failed';
    case 'cancelled': return 'Cancelled';
  }
}

function statusColor(status: OperationStatus): string {
  switch (status) {
    case 'pending': return 'text-amber-700 dark:text-amber-300';
    case 'running': return 'text-indigo-700 dark:text-indigo-300';
    case 'completed': return 'text-green-700 dark:text-green-300';
    case 'failed': return 'text-red-700 dark:text-red-300';
    case 'cancelled': return 'text-gray-600 dark:text-gray-400';
  }
}

export function OperationCenter({
  tenantId: propTenantId,
  maxRows = 10,
  className = '',
  onRetry,
  onCancel,
}: OperationCenterProps): React.ReactElement {
  const atomTenantId = useAtomValue(tenantIdAtom);
  const tenantId = propTenantId ?? atomTenantId;
  const [filter, setFilter] = useState<OperationStatus | 'all'>('all');
  const queryClient = useQueryClient();

  const { data: operations = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'operations', tenantId],
    queryFn: () => listOperations(tenantId, 100),
    staleTime: 10_000,
    refetchInterval: 15_000,
  });

  const retryMutation = useMutation({
    mutationFn: (operationId: string) => retryOperation(operationId, tenantId),
    onSuccess: (result, operationId) => {
      if (result.retried) {
        refetch();
        onRetry?.(operationId);
      }
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (operationId: string) => cancelOperation(operationId, tenantId),
    onSuccess: (result, operationId) => {
      if (result.cancelled) {
        refetch();
        onCancel?.(operationId);
      }
    },
  });

  const filtered = filter === 'all' ? operations : operations.filter((o) => o.status === filter);
  const display = filtered.slice(0, maxRows);
  const activeCount = operations.filter((o) => o.status === 'pending' || o.status === 'running').length;
  const failedCount = operations.filter((o) => o.status === 'failed').length;

  return (
    <div className={['rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950 overflow-hidden', className].join(' ')}>
      <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Operation Center</h3>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
            {activeCount} active · {failedCount} failed · {operations.length} total
          </p>
        </div>
        <div className="flex gap-1">
          {(['all', 'pending', 'running', 'completed', 'failed'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={[
                'px-2.5 py-1 rounded-full text-[11px] font-medium capitalize transition-colors',
                filter === f
                  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                  : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 dark:text-gray-400',
              ].join(' ')}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {isLoading && (
        <PageState mode="loading" title="Loading operations…" description="Fetching operation history for this tenant." />
      )}
      {isError && (
        <PageState mode="unavailable" title="Failed to load operations" description="The operation service is not reachable." onRetry={() => void refetch()} />
      )}
      {!isLoading && !isError && display.length === 0 && (
        <PageState mode="empty" title="No operations" description="Active and historical operations will appear here once pipelines, agents, or policies start tasks." />
      )}
      {!isLoading && !isError && display.length > 0 && (
        <div className="divide-y divide-gray-100 dark:divide-gray-900">
          {display.map((op) => (
            <div key={op.id} className="px-5 py-3 flex items-start gap-3 hover:bg-gray-50 dark:hover:bg-gray-900/50 transition-colors">
              <div className="mt-0.5 flex-shrink-0">{statusIcon(op.status)}</div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-gray-900 dark:text-white truncate">{op.type}</span>
                  <span className={['text-xs font-semibold', statusColor(op.status)].join(' ')}>{statusLabel(op.status)}</span>
                </div>
                <div className="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
                  <span>Started {new Date(op.startedAt).toLocaleString()}</span>
                  {op.finishedAt && <span>Finished {new Date(op.finishedAt).toLocaleString()}</span>}
                  <span>Attempt {op.attempts}/{op.maxAttempts}</span>
                </div>
                {op.errorMessage && (
                  <p className="mt-1.5 text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/30 rounded px-2 py-1">
                    {op.errorMessage}
                  </p>
                )}
                {op.resourceId && (
                  <div className="mt-1.5 flex gap-2 flex-wrap">
                    <span className="text-[10px] font-mono text-gray-400 dark:text-gray-500 bg-gray-100 dark:bg-gray-800 rounded px-1.5 py-0.5">
                      {op.resourceType}: {op.resourceId}
                    </span>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-1 flex-shrink-0">
                {op.status === 'failed' && onRetry && (
                  <Button
                    onClick={() => retryMutation.mutate(op.id)}
                    variant="ghost"
                    className="p-1 text-gray-400 hover:text-indigo-600"
                    aria-label={`Retry operation ${op.id}`}
                    title="Retry"
                    disabled={retryMutation.isPending}
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                  </Button>
                )}
                {(op.status === 'pending' || op.status === 'running') && onCancel && (
                  <Button
                    onClick={() => cancelMutation.mutate(op.id)}
                    variant="ghost"
                    className="p-1 text-gray-400 hover:text-red-600"
                    aria-label={`Cancel operation ${op.id}`}
                    title="Cancel"
                    disabled={cancelMutation.isPending}
                  >
                    <XCircle className="h-3.5 w-3.5" />
                  </Button>
                )}
                {op.auditEntryId && (
                  <a
                    href={`/govern?tab=audit&entry=${op.auditEntryId}`}
                    className="p-1 text-gray-400 hover:text-indigo-600"
                    title="View audit"
                  >
                    <ChevronRight className="h-3.5 w-3.5" />
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
