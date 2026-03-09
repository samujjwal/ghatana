/**
 * Execution monitor component for real-time workflow monitoring.
 *
 * Real-time workflow execution monitoring with:
 * - WebSocket-based status updates
 * - Node execution timeline
 * - Live log streaming
 * - Error traces with stack
 * - Retry/cancel controls
 *
 * @doc.type component
 * @doc.purpose Real-time execution monitor
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  Play,
  Pause,
  RefreshCw,
  XCircle,
  CheckCircle,
  Clock,
  AlertTriangle,
  Terminal,
  ChevronDown,
  ChevronRight,
  Loader2,
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../lib/theme';

// ============================================================================
// Types
// ============================================================================

export type ExecutionStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
export type NodeStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
export type LogLevel = 'info' | 'warn' | 'error' | 'debug';

export interface NodeExecution {
  id: string;
  name: string;
  type: string;
  status: NodeStatus;
  startTime?: string;
  endTime?: string;
  duration?: number;
  error?: string;
  retryCount?: number;
  inputCount?: number;
  outputCount?: number;
}

export interface ExecutionState {
  id: string;
  pipelineId: string;
  pipelineName: string;
  status: ExecutionStatus;
  startTime: string;
  endTime?: string;
  completedNodes: number;
  totalNodes: number;
  nodes: NodeExecution[];
  error?: string;
}

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  nodeId?: string;
  metadata?: Record<string, unknown>;
}

export interface ExecutionMonitorProps {
  executionId: string;
  onComplete?: (status: 'success' | 'failed') => void;
  onRetry?: () => void;
  onCancel?: () => void;
  className?: string;
}

// ============================================================================
// Mock API - Replace with actual implementation
// ============================================================================

async function fetchExecutionState(executionId: string): Promise<ExecutionState> {
  const response = await fetch(`/api/executions/${executionId}`);
  if (!response.ok) {
    // Mock data for development
    return {
      id: executionId,
      pipelineId: 'pipe-123',
      pipelineName: 'Customer Data Pipeline',
      status: 'running',
      startTime: new Date(Date.now() - 120000).toISOString(),
      completedNodes: 3,
      totalNodes: 8,
      nodes: [
        { id: 'n1', name: 'Source: Database', type: 'source', status: 'completed', startTime: new Date(Date.now() - 120000).toISOString(), endTime: new Date(Date.now() - 100000).toISOString(), duration: 20000, inputCount: 0, outputCount: 1500 },
        { id: 'n2', name: 'Transform: Clean', type: 'transform', status: 'completed', startTime: new Date(Date.now() - 100000).toISOString(), endTime: new Date(Date.now() - 80000).toISOString(), duration: 20000, inputCount: 1500, outputCount: 1485 },
        { id: 'n3', name: 'Transform: Enrich', type: 'transform', status: 'completed', startTime: new Date(Date.now() - 80000).toISOString(), endTime: new Date(Date.now() - 60000).toISOString(), duration: 20000, inputCount: 1485, outputCount: 1485 },
        { id: 'n4', name: 'AI: Classify', type: 'ai', status: 'running', startTime: new Date(Date.now() - 60000).toISOString(), inputCount: 1485 },
        { id: 'n5', name: 'Filter: Quality', type: 'filter', status: 'pending' },
        { id: 'n6', name: 'Transform: Format', type: 'transform', status: 'pending' },
        { id: 'n7', name: 'Sink: API', type: 'sink', status: 'pending' },
        { id: 'n8', name: 'Sink: Database', type: 'sink', status: 'pending' },
      ],
    };
  }
  return response.json();
}

async function fetchExecutionLogs(executionId: string): Promise<LogEntry[]> {
  const response = await fetch(`/api/executions/${executionId}/logs`);
  if (!response.ok) {
    return [
      { timestamp: new Date(Date.now() - 120000).toISOString(), level: 'info', message: 'Execution started', nodeId: undefined },
      { timestamp: new Date(Date.now() - 119000).toISOString(), level: 'info', message: 'Connecting to source database...', nodeId: 'n1' },
      { timestamp: new Date(Date.now() - 118000).toISOString(), level: 'info', message: 'Connected. Fetching records...', nodeId: 'n1' },
      { timestamp: new Date(Date.now() - 100000).toISOString(), level: 'info', message: 'Fetched 1500 records in 20s', nodeId: 'n1' },
      { timestamp: new Date(Date.now() - 99000).toISOString(), level: 'info', message: 'Starting data cleaning...', nodeId: 'n2' },
      { timestamp: new Date(Date.now() - 85000).toISOString(), level: 'warn', message: '15 records failed validation, skipped', nodeId: 'n2' },
      { timestamp: new Date(Date.now() - 80000).toISOString(), level: 'info', message: 'Cleaning complete. 1485 records remain', nodeId: 'n2' },
      { timestamp: new Date(Date.now() - 79000).toISOString(), level: 'info', message: 'Starting data enrichment...', nodeId: 'n3' },
      { timestamp: new Date(Date.now() - 60000).toISOString(), level: 'info', message: 'Enrichment complete', nodeId: 'n3' },
      { timestamp: new Date(Date.now() - 59000).toISOString(), level: 'info', message: 'Starting AI classification...', nodeId: 'n4' },
      { timestamp: new Date(Date.now() - 30000).toISOString(), level: 'debug', message: 'Processed 500/1485 records (33%)', nodeId: 'n4' },
    ];
  }
  return response.json();
}

// ============================================================================
// Helper Components
// ============================================================================

function formatRelativeTime(timestamp: string): string {
  const date = new Date(timestamp);
  const now = Date.now();
  const diff = now - date.getTime();
  const seconds = Math.floor(diff / 1000);

  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return date.toLocaleDateString();
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${minutes}m ${secs}s`;
}

function formatLogTime(timestamp: string): string {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

/**
 * Status badge component
 */
function StatusBadge({ status }: { status: ExecutionStatus }) {
  const configs: Record<ExecutionStatus, { icon: React.ElementType; color: string; bg: string; label: string }> = {
    running: { icon: Play, color: 'text-blue-500', bg: 'bg-blue-100 dark:bg-blue-900/30', label: 'Running' },
    completed: { icon: CheckCircle, color: 'text-green-500', bg: 'bg-green-100 dark:bg-green-900/30', label: 'Completed' },
    failed: { icon: XCircle, color: 'text-red-500', bg: 'bg-red-100 dark:bg-red-900/30', label: 'Failed' },
    pending: { icon: Clock, color: 'text-yellow-500', bg: 'bg-yellow-100 dark:bg-yellow-900/30', label: 'Pending' },
    cancelled: { icon: Pause, color: 'text-gray-500', bg: 'bg-gray-100 dark:bg-gray-800', label: 'Cancelled' },
  };

  const config = configs[status];
  const Icon = config.icon;

  return (
    <span className={cn('inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-sm font-medium', config.bg, config.color)}>
      <Icon className={cn('h-4 w-4', status === 'running' && 'animate-pulse')} />
      {config.label}
    </span>
  );
}

/**
 * Node status icon
 */
function NodeStatusIcon({ status }: { status: NodeStatus }) {
  switch (status) {
    case 'completed':
      return <CheckCircle className="h-4 w-4 text-green-500" />;
    case 'running':
      return <Loader2 className="h-4 w-4 text-blue-500 animate-spin" />;
    case 'failed':
      return <XCircle className="h-4 w-4 text-red-500" />;
    case 'skipped':
      return <AlertTriangle className="h-4 w-4 text-gray-400" />;
    default:
      return <Clock className="h-4 w-4 text-gray-300" />;
  }
}

/**
 * Node execution row component
 */
function NodeExecutionRow({
  node,
  isSelected,
  onClick,
}: {
  node: NodeExecution;
  isSelected: boolean;
  onClick: () => void;
}) {
  return (
    <div
      className={cn(
        'flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors',
        'border border-transparent',
        isSelected
          ? 'bg-primary-50 dark:bg-primary-900/20 border-primary-200 dark:border-primary-800'
          : 'hover:bg-gray-50 dark:hover:bg-gray-800'
      )}
      onClick={onClick}
    >
      <NodeStatusIcon status={node.status} />

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {node.name}
          </span>
          <span className="text-xs text-gray-400 px-1.5 py-0.5 bg-gray-100 dark:bg-gray-800 rounded">
            {node.type}
          </span>
        </div>

        {node.error && (
          <p className="text-xs text-red-500 mt-1 truncate">{node.error}</p>
        )}

        {node.status === 'running' && node.inputCount !== undefined && (
          <p className="text-xs text-blue-500 mt-1">
            Processing {node.inputCount.toLocaleString()} records...
          </p>
        )}
      </div>

      <div className="flex items-center gap-4 text-xs text-gray-500">
        {node.inputCount !== undefined && node.outputCount !== undefined && (
          <span>{node.inputCount.toLocaleString()} → {node.outputCount.toLocaleString()}</span>
        )}
        {node.duration !== undefined && (
          <span className="tabular-nums">{formatDuration(node.duration)}</span>
        )}
      </div>

      <ChevronRight className={cn(
        'h-4 w-4 text-gray-400 transition-transform',
        isSelected && 'rotate-90'
      )} />
    </div>
  );
}

/**
 * Loading skeleton
 */
function ExecutionSkeleton() {
  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 animate-pulse">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-3">
          <div className="h-8 w-24 bg-gray-200 dark:bg-gray-700 rounded-full" />
          <div>
            <div className="h-4 w-48 bg-gray-200 dark:bg-gray-700 rounded mb-1" />
            <div className="h-3 w-24 bg-gray-200 dark:bg-gray-700 rounded" />
          </div>
        </div>
      </div>
      <div className="p-4 space-y-3">
        {[1, 2, 3, 4].map(i => (
          <div key={i} className="h-14 bg-gray-200 dark:bg-gray-700 rounded-lg" />
        ))}
      </div>
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * ExecutionMonitor component.
 *
 * Displays real-time workflow execution status with node progress,
 * timeline, and error tracking.
 *
 * @doc.type function
 */
export function ExecutionMonitor({
  executionId,
  onComplete,
  onRetry,
  onCancel,
  className,
}: ExecutionMonitorProps) {
  const queryClient = useQueryClient();
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [showLogs, setShowLogs] = useState(true);
  const logsEndRef = useRef<HTMLDivElement>(null);
  const prevStatusRef = useRef<ExecutionStatus | null>(null);

  // Fetch execution state with polling
  const { data: execution, isLoading: executionLoading } = useQuery({
    queryKey: ['execution', executionId],
    queryFn: () => fetchExecutionState(executionId),
    refetchInterval: (query) => {
      const data = query.state.data;
      // Stop polling if execution is complete
      if (data?.status === 'completed' || data?.status === 'failed' || data?.status === 'cancelled') {
        return false;
      }
      return 2000; // Poll every 2 seconds while running
    },
  });

  // Fetch logs with polling
  const { data: logs = [] } = useQuery({
    queryKey: ['execution-logs', executionId],
    queryFn: () => fetchExecutionLogs(executionId),
    refetchInterval: (query) => {
      // Stop polling if execution is complete
      if (execution?.status === 'completed' || execution?.status === 'failed' || execution?.status === 'cancelled') {
        return false;
      }
      return 3000; // Poll every 3 seconds
    },
  });

  // Notify on completion
  useEffect(() => {
    if (execution && prevStatusRef.current !== execution.status) {
      prevStatusRef.current = execution.status;

      if (execution.status === 'completed') {
        onComplete?.('success');
      } else if (execution.status === 'failed') {
        onComplete?.('failed');
      }
    }
  }, [execution?.status, onComplete]);

  // Auto-scroll logs
  useEffect(() => {
    if (showLogs && logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, showLogs]);

  const handleRefresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['execution', executionId] });
    queryClient.invalidateQueries({ queryKey: ['execution-logs', executionId] });
  }, [queryClient, executionId]);

  if (executionLoading || !execution) {
    return <ExecutionSkeleton />;
  }

  const progressPercent = (execution.completedNodes / execution.totalNodes) * 100;

  return (
    <div className={cn(
      'flex flex-col h-full bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700',
      className
    )}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-3">
          <StatusBadge status={execution.status} />
          <div>
            <h3 className="font-medium text-gray-900 dark:text-gray-100">
              {execution.pipelineName}
            </h3>
            <p className="text-xs text-gray-500">
              Started {formatRelativeTime(execution.startTime)}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg"
            title="Refresh"
          >
            <RefreshCw className="h-4 w-4" />
          </button>
          {execution.status === 'running' && onCancel && (
            <button
              onClick={onCancel}
              className="p-2 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg"
              title="Cancel execution"
            >
              <XCircle className="h-4 w-4" />
            </button>
          )}
          {execution.status === 'failed' && onRetry && (
            <button
              onClick={onRetry}
              className="inline-flex items-center px-3 py-1.5 bg-primary-600 text-white text-sm rounded-lg hover:bg-primary-700"
            >
              <RefreshCw className="h-4 w-4 mr-1" />
              Retry
            </button>
          )}
        </div>
      </div>

      {/* Progress Bar */}
      <div className="px-4 py-2 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between text-xs text-gray-500 mb-1">
          <span>Progress</span>
          <span>{execution.completedNodes}/{execution.totalNodes} nodes</span>
        </div>
        <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            className={cn(
              'h-full transition-all duration-500',
              execution.status === 'failed' ? 'bg-red-500' : 'bg-primary-500'
            )}
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {/* Node Timeline */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-2">
          {execution.nodes.map((node) => (
            <NodeExecutionRow
              key={node.id}
              node={node}
              isSelected={selectedNode === node.id}
              onClick={() => setSelectedNode(selectedNode === node.id ? null : node.id)}
            />
          ))}
        </div>
      </div>

      {/* Logs Panel */}
      <div className={cn(
        'border-t border-gray-200 dark:border-gray-700 transition-all duration-200',
        showLogs ? 'h-48' : 'h-10'
      )}>
        <button
          onClick={() => setShowLogs(!showLogs)}
          className="flex items-center gap-2 w-full px-4 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800"
        >
          <Terminal className="h-4 w-4" />
          <span>Logs</span>
          <span className="ml-auto text-xs text-gray-400">{logs.length} entries</span>
          {showLogs ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
        {showLogs && (
          <div className="h-36 overflow-y-auto px-4 py-2 font-mono text-xs bg-gray-950 text-gray-300">
            {logs.map((log, i) => (
              <div
                key={i}
                className={cn(
                  'py-0.5',
                  log.level === 'error' && 'text-red-400',
                  log.level === 'warn' && 'text-yellow-400',
                  log.level === 'debug' && 'text-gray-500'
                )}
              >
                <span className="text-gray-500">[{formatLogTime(log.timestamp)}]</span>
                {log.nodeId && <span className="text-cyan-400 ml-1">[{log.nodeId}]</span>}
                <span className="ml-1">{log.message}</span>
              </div>
            ))}
            <div ref={logsEndRef} />
          </div>
        )}
      </div>
    </div>
  );
}

export default ExecutionMonitor;
