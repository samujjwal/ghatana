/**
 * AsyncOperationPanel — lifecycle, retry, history, and audit handoff UI.
 *
 * @doc.type component
 * @doc.purpose Track async operation lifecycle with retry controls and audit links
 * @doc.layer frontend
 * @doc.pattern Operations UI
 */
import React, { useState } from 'react';
import { Clock, CheckCircle2, XCircle, RotateCcw, AlertTriangle, ChevronRight, ChevronDown } from 'lucide-react';
import { Button } from '@ghatana/design-system';

export type AsyncOperationStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

export interface AsyncOperationRecord {
  id: string;
  label: string;
  status: AsyncOperationStatus;
  startedAt: string;
  finishedAt?: string;
  attempts: number;
  maxAttempts: number;
  errorMessage?: string;
  auditEntryId?: string;
  resourceType?: 'pipeline' | 'run' | 'agent' | 'policy' | 'operation';
  resourceId?: string;
}

interface AsyncOperationPanelProps {
  operations: AsyncOperationRecord[];
  onRetry?: (operationId: string) => void;
  onCancel?: (operationId: string) => void;
  className?: string;
}

function statusIcon(status: AsyncOperationStatus) {
  switch (status) {
    case 'pending': return <Clock className="h-4 w-4 text-amber-500" aria-hidden />;
    case 'running': return <div className="animate-spin h-4 w-4 border-2 border-indigo-600 border-t-transparent rounded-full" aria-hidden />;
    case 'completed': return <CheckCircle2 className="h-4 w-4 text-green-600" aria-hidden />;
    case 'failed': return <XCircle className="h-4 w-4 text-red-600" aria-hidden />;
    case 'cancelled': return <AlertTriangle className="h-4 w-4 text-gray-500" aria-hidden />;
  }
}

function statusLabel(status: AsyncOperationStatus): string {
  switch (status) {
    case 'pending': return 'Pending';
    case 'running': return 'Running';
    case 'completed': return 'Completed';
    case 'failed': return 'Failed';
    case 'cancelled': return 'Cancelled';
  }
}

function statusColor(status: AsyncOperationStatus): string {
  switch (status) {
    case 'pending': return 'text-amber-700 dark:text-amber-300';
    case 'running': return 'text-indigo-700 dark:text-indigo-300';
    case 'completed': return 'text-green-700 dark:text-green-300';
    case 'failed': return 'text-red-700 dark:text-red-300';
    case 'cancelled': return 'text-gray-600 dark:text-gray-400';
  }
}

export function AsyncOperationPanel({
  operations,
  onRetry,
  onCancel,
  className = '',
}: AsyncOperationPanelProps): React.ReactElement {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <div className={['space-y-2', className].join(' ')}>
      {operations.map((op) => {
        const expanded = expandedId === op.id;
        return (
          <div
            key={op.id}
            className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950"
          >
            <div className="px-4 py-3 flex items-start gap-3">
              <div className="mt-0.5 flex-shrink-0">{statusIcon(op.status)}</div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-gray-900 dark:text-white truncate">{op.label}</span>
                  <span className={['text-xs font-semibold', statusColor(op.status)].join(' ')}>
                    {statusLabel(op.status)}
                  </span>
                </div>
                <div className="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
                  <span>Started {new Date(op.startedAt).toLocaleString()}</span>
                  {op.finishedAt && <span>Finished {new Date(op.finishedAt).toLocaleString()}</span>}
                  <span>Attempt {op.attempts}/{op.maxAttempts}</span>
                </div>
                {(op.errorMessage || op.auditEntryId || op.resourceId) && (
                  <Button
                    type="button"
                    onClick={() => setExpandedId(expanded ? null : op.id)}
                    variant="text"
                    className="mt-1.5 flex items-center gap-1 text-xs"
                    aria-expanded={expanded}
                  >
                    {expanded ? (
                      <>
                        <ChevronDown className="h-3.5 w-3.5" /> Hide details
                      </>
                    ) : (
                      <>
                        <ChevronRight className="h-3.5 w-3.5" /> Show details
                      </>
                    )}
                  </Button>
                )}
                {expanded && (
                  <div className="mt-2 space-y-2 text-xs text-gray-600 dark:text-gray-400">
                    {op.errorMessage && (
                      <p className="rounded bg-red-50 dark:bg-red-950/30 px-2 py-1.5 text-red-700 dark:text-red-300">
                        {op.errorMessage}
                      </p>
                    )}
                    {op.resourceId && (
                      <p className="font-mono text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 rounded px-2 py-1">
                        {op.resourceType}: {op.resourceId}
                      </p>
                    )}
                    {op.auditEntryId && (
                      <a
                        href={`/govern?tab=audit&entry=${op.auditEntryId}`}
                        className="inline-flex items-center gap-1 text-indigo-600 dark:text-indigo-400 hover:underline"
                      >
                        View audit entry <ChevronRight className="h-3 w-3" />
                      </a>
                    )}
                  </div>
                )}
              </div>
              <div className="flex items-center gap-1 flex-shrink-0">
                {op.status === 'failed' && onRetry && (
                  <Button
                    onClick={() => onRetry(op.id)}
                    variant="ghost"
                    className="p-1 text-gray-400 hover:text-indigo-600"
                    aria-label={`Retry ${op.label}`}
                    title="Retry"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                  </Button>
                )}
                {(op.status === 'pending' || op.status === 'running') && onCancel && (
                  <Button
                    onClick={() => onCancel(op.id)}
                    variant="ghost"
                    className="p-1 text-gray-400 hover:text-red-600"
                    aria-label={`Cancel ${op.label}`}
                    title="Cancel"
                  >
                    <XCircle className="h-3.5 w-3.5" />
                  </Button>
                )}
              </div>
            </div>
          </div>
        );
      })}
      {operations.length === 0 && (
        <div className="text-center text-xs text-gray-500 dark:text-gray-400 py-4">
          No active or recent operations.
        </div>
      )}
    </div>
  );
}
