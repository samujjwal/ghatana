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
import { RefreshCw, CirclePlay, CircleCheck, CircleX } from 'lucide-react';

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
  QUEUED: 'bg-slate-100 text-slate-700',
  RUNNING: 'bg-blue-100 text-blue-700',
  SUCCEEDED: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-gray-200 text-gray-700',
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
        <Typography className="text-sm text-gray-500">
          {runningCount} running · {failedCount} failed
        </Typography>
      </Box>

      {runs.length === 0 && (
        <Card>
          <CardContent className="p-4">
            <Typography className="text-sm text-gray-600">No agent runs available.</Typography>
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

            <Typography className="text-xs text-gray-500">Stage: {run.stage}</Typography>
            <Typography className="text-xs text-gray-500">Created: {formatTimestamp(run.createdAt)}</Typography>
            <Typography className="text-xs text-gray-500">Started: {formatTimestamp(run.startedAt)}</Typography>
            <Typography className="text-xs text-gray-500">Completed: {formatTimestamp(run.completedAt)}</Typography>
            <Typography className="text-xs text-gray-500">Retries: {run.retryCount}</Typography>

            {run.errorMessage && (
              <Typography className="text-sm text-red-700">Error: {run.errorMessage}</Typography>
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
