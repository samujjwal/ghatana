// ============================================================================
// ProvisioningProgressNode - Canvas node for live provisioning progress
//
// Features:
// - Real-time progress tracking
// - Step-by-step status display
// - Resource provisioning indicators
// - Error handling and retry options
// - Time estimates
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import {
  Loader2,
  Check,
  X,
  AlertCircle,
  SkipForward,
  Clock,
  RefreshCw,
  Database,
  Globe,
  GitBranch,
  Server,
  HardDrive,
  Shield,
  Settings,
  PlayCircle,
  PauseCircle,
  StopCircle,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';

type InitializationStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'ROLLED_BACK';

type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

type ResourceType =
  | 'REPOSITORY'
  | 'HOSTING'
  | 'DATABASE'
  | 'CACHE'
  | 'STORAGE'
  | 'CDN'
  | 'DOMAIN'
  | 'SSL'
  | 'CI_CD'
  | 'SECRETS'
  | 'MONITORING'
  | 'LOGGING';

type ProvisioningStatus = 'COMPLETED' | 'FAILED' | 'IN_PROGRESS';

interface CurrentProvisioningStep {
  name: string;
  resourceType: ResourceType;
  progress: number;
}

interface InitializationProgress {
  status: InitializationStatus;
  totalSteps: number;
  overallProgress: number;
  estimatedTimeRemaining?: number;
  resourcesProvisioned: number;
  resourcesFailed: number;
  currentStep?: CurrentProvisioningStep;
}

interface InitializationStep {
  id: string;
  name: string;
  status: StepStatus;
  resourceType: ResourceType;
  errorMessage?: string;
  canRollback?: boolean;
}

interface ProvisionedResource {
  id: string;
  name: string;
  type: ResourceType;
  status: ProvisioningStatus;
}

export interface ProvisioningProgressNodeData extends Record<string, unknown> {
  progress: InitializationProgress;
  steps: InitializationStep[];
  resources?: ProvisionedResource[];
  expanded?: boolean;
  onRetryStep?: (stepId: string) => void;
  onSkipStep?: (stepId: string) => void;
  onToggleExpand?: () => void;
  onPause?: () => void;
  onResume?: () => void;
  onCancel?: () => void;
}

type ProvisioningProgressCanvasNode = Node<ProvisioningProgressNodeData, 'provisioning-progress'>;

const statusConfig: Record<
  InitializationStatus,
  { color: string; bgColor: string; label: string; icon: typeof Loader2 }
> = {
  NOT_STARTED: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20', label: 'Not Started', icon: PlayCircle },
  IN_PROGRESS: { color: 'text-info-color', bgColor: 'bg-info-bg/20', label: 'In Progress', icon: Loader2 },
  PAUSED: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', label: 'Paused', icon: PauseCircle },
  COMPLETED: { color: 'text-success-color', bgColor: 'bg-success-bg0/20', label: 'Completed', icon: Check },
  FAILED: { color: 'text-destructive', bgColor: 'bg-destructive-bg0/20', label: 'Failed', icon: X },
  ROLLED_BACK: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', label: 'Rolled Back', icon: RefreshCw },
};

const stepStatusConfig: Record<
  StepStatus,
  { color: string; bgColor: string; icon: typeof Loader2 }
> = {
  PENDING: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20', icon: Clock },
  IN_PROGRESS: { color: 'text-info-color', bgColor: 'bg-info-bg/20', icon: Loader2 },
  COMPLETED: { color: 'text-success-color', bgColor: 'bg-success-bg0/20', icon: Check },
  FAILED: { color: 'text-destructive', bgColor: 'bg-destructive-bg0/20', icon: X },
  SKIPPED: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', icon: SkipForward },
};

const resourceIcons: Record<ResourceType, typeof Database> = {
  REPOSITORY: GitBranch,
  HOSTING: Globe,
  DATABASE: Database,
  CACHE: Server,
  STORAGE: HardDrive,
  CDN: Globe,
  DOMAIN: Globe,
  SSL: Shield,
  CI_CD: Settings,
  SECRETS: Shield,
  MONITORING: Settings,
  LOGGING: Settings,
};

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  if (minutes < 60) {
    return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${remainingMinutes}m`;
}

function ProvisioningProgressNode({ data }: NodeProps<ProvisioningProgressCanvasNode>) {
  const {
    progress,
    steps,
    resources = [],
    expanded = false,
    onRetryStep,
    onSkipStep,
    onToggleExpand,
    onPause,
    onResume,
    onCancel,
  } = data;

  const statusInfo = statusConfig[progress.status];
  const StatusIcon = statusInfo.icon;

  const completedSteps = steps.filter((step) => step.status === 'COMPLETED').length;
  const failedSteps = steps.filter((step) => step.status === 'FAILED').length;
  const progressPercent = Math.round(progress.overallProgress * 100);

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xl min-w-[400px] max-w-[480px]">
      <Handle type="target" position={Position.Left} className="!bg-success-bg0" />
      <Handle type="source" position={Position.Right} className="!bg-success-bg0" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          <div
            className={cn(
              'p-2 rounded-lg',
              progress.status === 'IN_PROGRESS' && 'animate-pulse',
              statusInfo.bgColor
            )}
          >
            <StatusIcon
              className={cn(
                'w-5 h-5',
                statusInfo.color,
                progress.status === 'IN_PROGRESS' && 'animate-spin'
              )}
            />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Provisioning Progress</h3>
            <p className="text-xs text-fg-muted">
              {completedSteps} of {progress.totalSteps} steps complete
            </p>
          </div>
        </div>
        <div className={cn('px-2 py-1 rounded-full text-xs', statusInfo.bgColor, statusInfo.color)}>
          {statusInfo.label}
        </div>
      </div>

      {/* Progress Bar */}
      <div className="px-4 py-3 border-b border-border">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-white">{progressPercent}%</span>
          {progress.estimatedTimeRemaining !== undefined && progress.estimatedTimeRemaining > 0 && (
            <span className="text-xs text-fg-muted flex items-center gap-1">
              <Clock className="w-3 h-3" />
              ~{formatDuration(progress.estimatedTimeRemaining)} remaining
            </span>
          )}
        </div>
        <div className="h-2 bg-surface-muted rounded-full overflow-hidden">
          <div
            className={cn(
              'h-full rounded-full transition-all duration-500',
              progress.status === 'COMPLETED'
                ? 'bg-success-bg0'
                : progress.status === 'FAILED'
                ? 'bg-destructive-bg0'
                : 'bg-info-bg'
            )}
            style={{ width: `${progressPercent}%` }}
          />
        </div>
        <div className="flex items-center justify-between mt-2 text-xs text-fg-muted">
          <span>{progress.resourcesProvisioned} resources provisioned</span>
          {progress.resourcesFailed > 0 && (
            <span className="text-destructive">{progress.resourcesFailed} failed</span>
          )}
        </div>
      </div>

      {/* Current Step */}
      {progress.currentStep && progress.status === 'IN_PROGRESS' && (
        <div className="px-4 py-3 border-b border-border bg-info-bg/5">
          <div className="flex items-center gap-3">
            {(() => {
              const CurrentIcon = resourceIcons[progress.currentStep.resourceType];
              return (
                <div className="p-2 bg-info-bg/20 rounded-lg animate-pulse">
                  <CurrentIcon className="w-4 h-4 text-info-color" />
                </div>
              );
            })()}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-white">{progress.currentStep.name}</span>
                <span className="text-xs text-info-color">{progress.currentStep.progress}%</span>
              </div>
              <div className="h-1 bg-surface-muted rounded-full mt-1.5 overflow-hidden">
                <div
                  className="h-full bg-info-bg rounded-full transition-all duration-300"
                  style={{ width: `${progress.currentStep.progress}%` }}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Steps List (Collapsible) */}
      <div className="border-b border-border">
        <Button variant="ghost" size="sm"
          onClick={onToggleExpand}
          className="w-full px-4 py-2 flex items-center justify-between hover:bg-surface-muted transition-colors"
        >
          <span className="text-xs font-medium text-fg-muted uppercase tracking-wider">
            Steps ({completedSteps}/{steps.length})
            {failedSteps > 0 && <span className="text-destructive ml-1">• {failedSteps} failed</span>}
          </span>
          {expanded ? (
            <ChevronUp className="w-4 h-4 text-fg-muted" />
          ) : (
            <ChevronDown className="w-4 h-4 text-fg-muted" />
          )}
        </Button>
        {expanded && (
          <div className="px-4 pb-3 space-y-2">
            {steps.map((step) => {
              const StepIcon = resourceIcons[step.resourceType];
              const stepStatusInfo = stepStatusConfig[step.status];
              const StepStatusIcon = stepStatusInfo.icon;

              return (
                <div
                  key={step.id}
                  className={cn(
                    'flex items-center gap-3 p-2 rounded-lg',
                    step.status === 'IN_PROGRESS' && 'bg-info-bg/10',
                    step.status === 'FAILED' && 'bg-destructive-bg0/10'
                  )}
                >
                  <div className={cn('p-1.5 rounded', stepStatusInfo.bgColor)}>
                    <StepIcon className={cn('w-3.5 h-3.5', stepStatusInfo.color)} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-fg">{step.name}</span>
                      <div className="flex items-center gap-1">
                        <StepStatusIcon
                          className={cn(
                            'w-3 h-3',
                            stepStatusInfo.color,
                            step.status === 'IN_PROGRESS' && 'animate-spin'
                          )}
                        />
                      </div>
                    </div>
                    {step.status === 'FAILED' && step.errorMessage && (
                      <div className="flex items-center justify-between mt-1">
                        <span className="text-xs text-destructive truncate max-w-[200px]">
                          {step.errorMessage}
                        </span>
                        <div className="flex items-center gap-1">
                          {onRetryStep && (
                            <Button variant="ghost" size="sm"
                              onClick={() => onRetryStep(step.id)}
                              className="p-1 hover:bg-muted rounded text-fg-muted hover:text-white"
                            >
                              <RefreshCw className="w-3 h-3" />
                            </Button>
                          )}
                          {onSkipStep && step.canRollback && (
                            <Button variant="ghost" size="sm"
                              onClick={() => onSkipStep(step.id)}
                              className="p-1 hover:bg-muted rounded text-fg-muted hover:text-white"
                            >
                              <SkipForward className="w-3 h-3" />
                            </Button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Provisioned Resources Summary */}
      {resources.length > 0 && (
        <div className="px-4 py-3 border-b border-border">
          <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
            Provisioned Resources
          </h4>
          <div className="flex flex-wrap gap-1.5">
            {resources.map((resource) => {
              const ResourceIcon = resourceIcons[resource.type];
              const isCompleted = resource.status === 'COMPLETED';
              const isFailed = resource.status === 'FAILED';

              return (
                <div
                  key={resource.id}
                  className={cn(
                    'flex items-center gap-1.5 px-2 py-1 rounded text-xs',
                    isCompleted && 'bg-success-bg0/20 text-success-color',
                    isFailed && 'bg-destructive-bg0/20 text-destructive',
                    !isCompleted && !isFailed && 'bg-surface-muted text-fg'
                  )}
                >
                  <ResourceIcon className="w-3 h-3" />
                  <span>{resource.name}</span>
                  {isCompleted && <Check className="w-3 h-3" />}
                  {isFailed && <X className="w-3 h-3" />}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Controls */}
      {(onPause || onResume || onCancel) && (
        <div className="px-4 py-3 flex items-center justify-end gap-2">
          {progress.status === 'IN_PROGRESS' && onPause && (
            <Button variant="ghost" size="sm"
              onClick={onPause}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-warning-color hover:bg-warning-bg0 rounded-lg text-xs text-white transition-colors"
            >
              <PauseCircle className="w-3.5 h-3.5" />
              Pause
            </Button>
          )}
          {progress.status === 'PAUSED' && onResume && (
            <Button variant="ghost" size="sm"
              onClick={onResume}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-success-color hover:bg-success-bg0 rounded-lg text-xs text-white transition-colors"
            >
              <PlayCircle className="w-3.5 h-3.5" />
              Resume
            </Button>
          )}
          {(progress.status === 'IN_PROGRESS' || progress.status === 'PAUSED') && onCancel && (
            <Button variant="ghost" size="sm"
              onClick={onCancel}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-destructive hover:bg-destructive-bg0 rounded-lg text-xs text-white transition-colors"
            >
              <StopCircle className="w-3.5 h-3.5" />
              Cancel
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

export default memo(ProvisioningProgressNode);
