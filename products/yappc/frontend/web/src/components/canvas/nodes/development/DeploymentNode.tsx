/**
 * Deployment Node Component
 *
 * Canvas node representing a deployment in the development phase.
 * Shows deployment status, environment, version, and pipeline progress.
 *
 * @doc.type component
 * @doc.purpose Deployment visualization in development canvas
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Rocket,
  Server,
  Cloud,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  RotateCcw,
  GitCommit,
  User,
  MoreHorizontal,
  ExternalLink,
  Play,
  Pause,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Terminal,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type DeploymentStatus = 'pending' | 'in_progress' | 'succeeded' | 'failed' | 'rolled_back';
export type DeploymentEnvironment = 'development' | 'staging' | 'production';

export interface PipelineStage {
  readonly id: string;
  readonly name: string;
  readonly status: 'pending' | 'running' | 'passed' | 'failed' | 'skipped';
  readonly duration?: number; // seconds
}

export interface DeploymentNodeData extends Record<string, unknown> {
  readonly label: string;
  readonly version: string;
  readonly environment: DeploymentEnvironment;
  readonly status: DeploymentStatus;
  readonly commitSha: string;
  readonly commitMessage?: string;
  readonly triggeredBy: {
    readonly name: string;
    readonly avatarUrl?: string;
  };
  readonly approvedBy?: {
    readonly name: string;
    readonly avatarUrl?: string;
  };
  readonly pipeline?: readonly PipelineStage[];
  readonly duration?: number; // seconds
  readonly startedAt: string;
  readonly completedAt?: string;
  readonly rollbackOf?: string;
  readonly nodeId?: string;
  // Callbacks
  readonly onViewLogs?: (nodeId: string) => void;
  readonly onApprove?: (nodeId: string) => void;
  readonly onCancel?: (nodeId: string) => void;
  readonly onRollback?: (nodeId: string) => void;
  readonly onRetry?: (nodeId: string) => void;
}

type DeploymentCanvasNode = Node<DeploymentNodeData>;

export interface DeploymentNodeProps extends NodeProps<DeploymentCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const STATUS_CONFIG: Record<DeploymentStatus, { label: string; icon: typeof Rocket; color: string; bgColor: string }> = {
  pending: {
    label: 'Pending',
    icon: Clock,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  in_progress: {
    label: 'In Progress',
    icon: Loader2,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  succeeded: {
    label: 'Succeeded',
    icon: CheckCircle2,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  failed: {
    label: 'Failed',
    icon: XCircle,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
  rolled_back: {
    label: 'Rolled Back',
    icon: RotateCcw,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
};

const ENVIRONMENT_CONFIG: Record<DeploymentEnvironment, { label: string; icon: typeof Server; color: string; bgColor: string }> = {
  development: {
    label: 'Development',
    icon: Server,
    color: 'text-fg-muted',
    bgColor: 'bg-muted',
  },
  staging: {
    label: 'Staging',
    icon: Server,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
  },
  production: {
    label: 'Production',
    icon: Cloud,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg',
  },
};

const STAGE_STATUS_CONFIG: Record<PipelineStage['status'], { color: string; bgColor: string }> = {
  pending: { color: 'text-fg-muted', bgColor: 'bg-muted' },
  running: { color: 'text-info-color', bgColor: 'bg-info-color' },
  passed: { color: 'text-success-color', bgColor: 'bg-success-color' },
  failed: { color: 'text-destructive', bgColor: 'bg-destructive' },
  skipped: { color: 'text-fg-muted', bgColor: 'bg-muted-foreground' },
};

// =============================================================================
// Helper Functions
// =============================================================================

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

// =============================================================================
// Sub-Components
// =============================================================================

interface PipelineProgressProps {
  stages: readonly PipelineStage[];
}

const PipelineProgress: React.FC<PipelineProgressProps> = memo(({ stages }) => (
  <div className="space-y-2">
    <div className="flex items-center gap-1">
      {stages.map((stage, index) => {
        const config = STAGE_STATUS_CONFIG[stage.status];
        const isRunning = stage.status === 'running';
        
        return (
          <React.Fragment key={stage.id}>
            <div
              className={cn(
                'flex-1 h-2 rounded-full transition-all',
                stage.status === 'pending' ? 'bg-muted' : config.bgColor,
                isRunning && 'animate-pulse'
              )}
              title={`${stage.name}: ${stage.status}${stage.duration ? ` (${formatDuration(stage.duration)})` : ''}`}
            />
            {index < stages.length - 1 && (
              <div className="w-1 h-1 rounded-full bg-muted-foreground" />
            )}
          </React.Fragment>
        );
      })}
    </div>
    <div className="flex items-center justify-between text-[10px] text-fg-muted">
      {stages.map((stage) => (
        <span key={stage.id} className="truncate max-w-[60px]" title={stage.name}>
          {stage.name}
        </span>
      ))}
    </div>
  </div>
));

PipelineProgress.displayName = 'PipelineProgress';

// =============================================================================
// Main Component
// =============================================================================

const DeploymentNodeComponent: React.FC<DeploymentNodeProps> = ({
  data,
  selected,
  dragging,
}) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const statusConfig = STATUS_CONFIG[data.status];
  const envConfig = ENVIRONMENT_CONFIG[data.environment];
  const StatusIcon = statusConfig.icon;
  const EnvIcon = envConfig.icon;

  const nodeId = data.nodeId || `deploy-${data.version}`;
  const isInProgress = data.status === 'in_progress';
  const isPending = data.status === 'pending';
  const canRollback = data.status === 'succeeded' && data.environment === 'production';
  const canRetry = data.status === 'failed';

  const handleToggleExpand = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setExpanded((prev) => !prev);
  }, []);

  const handleViewLogs = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onViewLogs?.(nodeId);
    },
    [data, nodeId]
  );

  const handleApprove = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onApprove?.(nodeId);
    },
    [data, nodeId]
  );

  const handleCancel = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onCancel?.(nodeId);
    },
    [data, nodeId]
  );

  const handleRollback = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onRollback?.(nodeId);
    },
    [data, nodeId]
  );

  const handleRetry = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onRetry?.(nodeId);
    },
    [data, nodeId]
  );

  return (
    <div
      className={cn(
        'deployment-node w-80 rounded-xl border shadow-sm transition-all duration-200',
        'bg-surface',
        statusConfig.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleViewLogs}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />

      {/* Header */}
      <div className="p-4 border-b border-border">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div className={cn('p-2 rounded-lg', statusConfig.bgColor)}>
              <Rocket className={cn('w-5 h-5', statusConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-fg-muted">{data.version}</span>
                <div className={cn('flex items-center gap-1 px-2 py-0.5 rounded-full', statusConfig.bgColor)}>
                  <StatusIcon className={cn('w-3 h-3', statusConfig.color, isInProgress && 'animate-spin')} />
                  <span className={cn('text-[10px] font-medium', statusConfig.color)}>
                    {statusConfig.label}
                  </span>
                </div>
              </div>
              <h4 className="font-semibold text-sm text-fg truncate">{data.label}</h4>
            </div>
          </div>

          {/* Actions */}
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1.5 rounded hover:bg-surface-muted text-fg-muted hover:text-fg"
            >
              <MoreHorizontal className="w-4 h-4" />
            </button>

            {showMenu && (
              <>
                <div
                  className="fixed inset-0 z-10"
                  onClick={(e) => {
                    e.stopPropagation();
                    setShowMenu(false);
                  }}
                />
                <div className="absolute right-0 top-full mt-1 w-40 bg-surface rounded-lg shadow-lg border border-border py-1 z-20">
                  <button
                    onClick={handleViewLogs}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-fg hover:bg-surface-muted"
                  >
                    <Terminal className="w-4 h-4" />
                    View Logs
                  </button>
                  {isPending && (
                    <button
                      onClick={handleApprove}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-success-color hover:bg-success-bg"
                    >
                      <Play className="w-4 h-4" />
                      Approve & Deploy
                    </button>
                  )}
                  {isInProgress && (
                    <button
                      onClick={handleCancel}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-destructive hover:bg-destructive-bg"
                    >
                      <Pause className="w-4 h-4" />
                      Cancel Deployment
                    </button>
                  )}
                  {canRollback && (
                    <button
                      onClick={handleRollback}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-warning-color hover:bg-warning-bg"
                    >
                      <RotateCcw className="w-4 h-4" />
                      Rollback
                    </button>
                  )}
                  {canRetry && (
                    <button
                      onClick={handleRetry}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-info-color hover:bg-info-bg"
                    >
                      <Play className="w-4 h-4" />
                      Retry Deployment
                    </button>
                  )}
                </div>
              </>
            )}
          </div>
        </div>

        {/* Environment badge */}
        <div className="mt-2 flex items-center gap-2">
          <div className={cn('flex items-center gap-1.5 px-2 py-1 rounded-full', envConfig.bgColor)}>
            <EnvIcon className={cn('w-3.5 h-3.5', envConfig.color)} />
            <span className={cn('text-xs font-medium', envConfig.color)}>
              {envConfig.label}
            </span>
          </div>
          {data.environment === 'production' && (
            <span title="Production deployment">
              <AlertTriangle className="w-4 h-4 text-warning-color" />
            </span>
          )}
        </div>

        {/* Commit info */}
        <div className="mt-3 flex items-center gap-2">
          <GitCommit className="w-3.5 h-3.5 text-fg-muted" />
          <span className="text-xs font-mono text-fg-muted">{data.commitSha.slice(0, 7)}</span>
          {data.commitMessage && (
            <span className="text-xs text-fg-muted truncate">{data.commitMessage}</span>
          )}
        </div>

        {/* Triggered by */}
        <div className="mt-2 flex items-center gap-2">
          {data.triggeredBy.avatarUrl ? (
            <img
              src={data.triggeredBy.avatarUrl}
              alt={data.triggeredBy.name}
              className="w-5 h-5 rounded-full"
            />
          ) : (
            <div className="w-5 h-5 rounded-full bg-muted flex items-center justify-center">
              <User className="w-3 h-3 text-fg-muted" />
            </div>
          )}
          <span className="text-xs text-fg-muted">
            Triggered by {data.triggeredBy.name}
          </span>
        </div>

        {/* Approved by (for prod) */}
        {data.approvedBy && (
          <div className="mt-1 flex items-center gap-2">
            <CheckCircle2 className="w-3.5 h-3.5 text-success-color" />
            <span className="text-xs text-fg-muted">
              Approved by {data.approvedBy.name}
            </span>
          </div>
        )}

        {/* Rollback indicator */}
        {data.rollbackOf && (
          <div className="mt-2 flex items-center gap-2 px-2 py-1 bg-info-bg rounded-lg">
            <RotateCcw className="w-3.5 h-3.5 text-info-color" />
            <span className="text-xs text-info-color">
              Rollback of {data.rollbackOf}
            </span>
          </div>
        )}
      </div>

      {/* Pipeline progress */}
      {data.pipeline && data.pipeline.length > 0 && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-fg-muted">Pipeline</span>
            {data.duration !== undefined && (
              <span className="text-xs text-fg-muted">
                <Clock className="w-3 h-3 inline mr-1" />
                {formatDuration(data.duration)}
              </span>
            )}
          </div>
          <PipelineProgress stages={data.pipeline} />
        </div>
      )}

      {/* Expandable content */}
      {expanded && data.pipeline && (
        <div className="p-4 space-y-2 border-t border-border">
          {data.pipeline.map((stage) => {
            const config = STAGE_STATUS_CONFIG[stage.status];
            return (
              <div
                key={stage.id}
                className="flex items-center justify-between p-2 bg-surface-muted rounded-lg"
              >
                <div className="flex items-center gap-2">
                  <div className={cn('w-2 h-2 rounded-full', config.bgColor)} />
                  <span className="text-xs text-fg">{stage.name}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={cn('text-xs capitalize', config.color)}>
                    {stage.status}
                  </span>
                  {stage.duration !== undefined && (
                    <span className="text-xs text-fg-muted">
                      {formatDuration(stage.duration)}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-3 border-t border-border flex items-center justify-between">
        <div className="text-xs text-fg-muted">
          Started {data.startedAt}
          {data.completedAt && ` • Completed ${data.completedAt}`}
        </div>

        {data.pipeline && data.pipeline.length > 0 && (
          <button
            onClick={handleToggleExpand}
            className="p-1 rounded hover:bg-surface-muted text-fg-muted hover:text-fg"
          >
            {expanded ? (
              <ChevronUp className="w-4 h-4" />
            ) : (
              <ChevronDown className="w-4 h-4" />
            )}
          </button>
        )}
      </div>
    </div>
  );
};

/**
 * Memoized Deployment Node
 *
 * Performance optimized with React.memo to prevent unnecessary re-renders
 * during canvas operations like panning and zooming.
 */
export const DeploymentNode = memo(DeploymentNodeComponent);
DeploymentNode.displayName = 'DeploymentNode';

export default DeploymentNode;
