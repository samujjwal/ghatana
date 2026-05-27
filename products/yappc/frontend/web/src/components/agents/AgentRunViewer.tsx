/**
 * Agent Run Viewer
 *
 * @doc.type component
 * @doc.purpose Timeline view for AI agent execution visibility
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Button, Card, CardContent, Chip, Typography } from '@ghatana/design-system';
import { RefreshCw, CirclePlay, CircleCheck, CircleX, ListChecks } from 'lucide-react';

import { RunLineage } from '@/components/ai/RunLineage';
import { EmptyState } from '@/components/common/EmptyState';
import { fetchAepRunLineage } from '@/services/ai/aepRunLineageApi';

export type AgentRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface AgentRunRecord {
  id: string;
  agentName: string;
  status: AgentRunStatus;
  stage: string;
  retryCount: number;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
}

export interface AgentRunViewerProps {
  runs: AgentRunRecord[];
  onRetryRun?: (runId: string) => void;
  className?: string;
}

const STATUS_CLASSNAME: Record<AgentRunStatus, string> = {
  QUEUED: 'bg-surface-muted text-fg',
  RUNNING: 'bg-info-bg text-info-color',
  SUCCEEDED: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-destructive-bg text-destructive',
  CANCELLED: 'bg-surface-muted text-fg',
};

const STATUS_ICON: Record<AgentRunStatus, React.ReactNode> = {
  QUEUED: <CirclePlay className="h-4 w-4" />,
  RUNNING: <CirclePlay className="h-4 w-4" />,
  SUCCEEDED: <CircleCheck className="h-4 w-4" />,
  FAILED: <CircleX className="h-4 w-4" />,
  CANCELLED: <CircleX className="h-4 w-4" />,
};

function formatTimestamp(value?: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

export const AgentRunViewer: React.FC<AgentRunViewerProps> = ({
  runs,
  onRetryRun,
  className = '',
}) => {
  const failedCount = runs.filter((run) => run.status === 'FAILED').length;
  const runningCount = runs.filter((run) => run.status === 'RUNNING').length;

  return (
    <Box className={`space-y-3 ${className}`}>
      <Box className="flex items-center justify-between">
        <Typography className="text-lg font-semibold">Agent Run Viewer</Typography>
        <Typography className="text-sm text-fg-muted">
          {runningCount} running · {failedCount} failed
        </Typography>
      </Box>

      {runs.length === 0 && (
        <Card>
          <CardContent className="p-4">
            <EmptyState
              variant="compact"
              className="rounded-lg border border-dashed border-border bg-surface-muted/40"
              icon={<ListChecks className="h-full w-full" aria-hidden="true" />}
              title="No agent runs available"
              description="Agent run history will appear here after governed agent execution starts."
            />
          </CardContent>
        </Card>
      )}

      {runs.map((run) => (
        <Card key={run.id}>
          <CardContent className="space-y-2 p-4">
            <Box className="flex items-center justify-between gap-2">
              <Typography className="font-medium">{run.agentName}</Typography>
              <Chip
                label={run.status}
                size="sm"
                className={STATUS_CLASSNAME[run.status]}
                icon={STATUS_ICON[run.status]}
              />
            </Box>

            <Typography className="text-xs text-fg-muted">Stage: {run.stage}</Typography>
            <Typography className="text-xs text-fg-muted">Created: {formatTimestamp(run.createdAt)}</Typography>
            <Typography className="text-xs text-fg-muted">Started: {formatTimestamp(run.startedAt)}</Typography>
            <Typography className="text-xs text-fg-muted">Completed: {formatTimestamp(run.completedAt)}</Typography>
            <Typography className="text-xs text-fg-muted">Retries: {run.retryCount}</Typography>

            {run.errorMessage && (
              <Typography className="text-sm text-destructive">Error: {run.errorMessage}</Typography>
            )}

            {/* AEP run lineage — shown for SUCCEEDED and FAILED runs (C-Y4 / F-Y009) */}
            {(run.status === 'SUCCEEDED' || run.status === 'FAILED') && (
              <Box className="mt-2 border-t pt-2">
                <Typography className="mb-1 text-xs font-medium text-fg-muted">
                  AEP run lineage
                </Typography>
                <RunLineage runId={run.id} fetchLineage={fetchAepRunLineage} />
              </Box>
            )}

            {run.status === 'FAILED' && onRetryRun && (
              <Button size="sm" variant="outlined" onClick={() => onRetryRun(run.id)}>
                <RefreshCw className="mr-1 h-4 w-4" />
                Retry
              </Button>
            )}
          </CardContent>
        </Card>
      ))}
    </Box>
  );
};

export default AgentRunViewer;
