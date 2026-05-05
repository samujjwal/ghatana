// ============================================================================
// ConfigurationWizardNode - Canvas node for multi-step initialization wizard
//
// Features:
// - Step-by-step wizard progress visualization
// - Configuration summary for each step
// - Provider selection indicators
// - Cost estimate display
// - Navigation controls
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import {
  Settings,
  Check,
  Circle,
  ChevronRight,
  Database,
  Globe,
  GitBranch,
  Server,
  HardDrive,
  Shield,
  DollarSign,
  ArrowLeft,
  ArrowRight,
  Play,
  Pause,
  RotateCcw,
  Loader2,
} from 'lucide-react';
import { cn } from '../../utils/cn';

type InitializationStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'ROLLED_BACK';

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

interface Initialization {
  status: InitializationStatus;
}

export interface WizardStep {
  id: string;
  name: string;
  resourceType: ResourceType;
  isConfigured: boolean;
  isOptional: boolean;
  description?: string;
}

export interface ConfigurationWizardNodeData extends Record<string, unknown> {
  initialization?: Initialization;
  currentStepIndex: number;
  steps: WizardStep[];
  estimatedMonthlyCost?: number;
  estimatedSetupTime?: number;
  onStepChange?: (stepIndex: number) => void;
  onStart?: () => void;
  onPause?: () => void;
  onResume?: () => void;
  onReset?: () => void;
}

type ConfigurationWizardCanvasNode = Node<ConfigurationWizardNodeData, 'configuration-wizard'>;

const statusConfig: Record<
  InitializationStatus,
  { color: string; bgColor: string; label: string; icon: typeof Circle }
> = {
  NOT_STARTED: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20', label: 'Not Started', icon: Circle },
  IN_PROGRESS: { color: 'text-info-color', bgColor: 'bg-info-bg/20', label: 'In Progress', icon: Loader2 },
  PAUSED: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', label: 'Paused', icon: Pause },
  COMPLETED: { color: 'text-success-color', bgColor: 'bg-success-bg0/20', label: 'Completed', icon: Check },
  FAILED: { color: 'text-destructive', bgColor: 'bg-destructive-bg0/20', label: 'Failed', icon: Circle },
  ROLLED_BACK: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', label: 'Rolled Back', icon: RotateCcw },
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

function ConfigurationWizardNode({ data }: NodeProps<ConfigurationWizardCanvasNode>) {
  const {
    initialization,
    currentStepIndex,
    steps,
    estimatedMonthlyCost,
    estimatedSetupTime,
    onStepChange,
    onStart,
    onPause,
    onResume,
    onReset,
  } = data;

  const status: InitializationStatus = initialization?.status ?? 'NOT_STARTED';
  const statusInfo = statusConfig[status];
  const StatusIcon = statusInfo.icon;

  const canNavigatePrev = currentStepIndex > 0 && status === 'NOT_STARTED';
  const canNavigateNext = currentStepIndex < steps.length - 1 && status === 'NOT_STARTED';
  const canStart = status === 'NOT_STARTED' && steps.some((step) => step.isConfigured);
  const canPause = status === 'IN_PROGRESS';
  const canResume = status === 'PAUSED';
  const canReset = status === 'COMPLETED' || status === 'FAILED' || status === 'ROLLED_BACK';

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xl min-w-[420px] max-w-[500px]">
      <Handle type="target" position={Position.Left} className="!bg-info-bg" />
      <Handle type="source" position={Position.Right} className="!bg-info-bg" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-gradient-to-br from-blue-500/20 to-purple-500/20 rounded-lg">
            <Settings className="w-5 h-5 text-info-color" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Configuration Wizard</h3>
            <p className="text-xs text-fg-muted">
              Step {currentStepIndex + 1} of {steps.length}
            </p>
          </div>
        </div>
        <div className={cn('flex items-center gap-1.5 px-2 py-1 rounded-full text-xs', statusInfo.bgColor)}>
          <StatusIcon className={cn('w-3 h-3', statusInfo.color, status === 'IN_PROGRESS' && 'animate-spin')} />
          <span className={statusInfo.color}>{statusInfo.label}</span>
        </div>
      </div>

      {/* Step Progress Bar */}
      <div className="px-4 py-3 border-b border-border">
        <div className="flex items-center justify-between mb-2">
          {steps.map((step, index) => {
            const StepIcon = resourceIcons[step.resourceType];
            const isActive = index === currentStepIndex;
            const isCompleted = step.isConfigured;
            const isPast = index < currentStepIndex;

            return (
              <div key={step.id} className="flex items-center">
                <button
                  onClick={() => onStepChange?.(index)}
                  disabled={status !== 'NOT_STARTED'}
                  className={cn(
                    'relative flex items-center justify-center w-8 h-8 rounded-full transition-all',
                    isActive && 'ring-2 ring-primary ring-offset-2 ring-offset-surface',
                    isCompleted ? 'bg-success-bg0/20' : isPast ? 'bg-muted' : 'bg-surface-muted',
                    status === 'NOT_STARTED' && 'hover:bg-muted cursor-pointer'
                  )}
                >
                  {isCompleted ? (
                    <Check className="w-4 h-4 text-success-color" />
                  ) : (
                    <StepIcon
                      className={cn(
                        'w-4 h-4',
                        isActive ? 'text-info-color' : 'text-fg-muted'
                      )}
                    />
                  )}
                  {step.isOptional && (
                    <span className="absolute -top-1 -right-1 w-2 h-2 bg-warning-bg0 rounded-full" />
                  )}
                </button>
                {index < steps.length - 1 && (
                  <div
                    className={cn(
                      'w-8 h-0.5 mx-1',
                      isCompleted ? 'bg-success-bg0/40' : 'bg-muted'
                    )}
                  />
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Current Step Details */}
      {steps[currentStepIndex] && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-start gap-3">
            {(() => {
              const CurrentStepIcon = resourceIcons[steps[currentStepIndex].resourceType];
              return (
                <div className="p-2 bg-surface-muted rounded-lg">
                  <CurrentStepIcon className="w-5 h-5 text-fg" />
                </div>
              );
            })()}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-medium text-white">{steps[currentStepIndex].name}</h4>
                {steps[currentStepIndex].isOptional && (
                  <span className="text-xs text-warning-color bg-warning-bg0/10 px-1.5 py-0.5 rounded">
                    Optional
                  </span>
                )}
              </div>
              {steps[currentStepIndex].description && (
                <p className="text-xs text-fg-muted mt-1">
                  {steps[currentStepIndex].description}
                </p>
              )}
              <div className="flex items-center gap-2 mt-2">
                {steps[currentStepIndex].isConfigured ? (
                  <span className="text-xs text-success-color flex items-center gap-1">
                    <Check className="w-3 h-3" /> Configured
                  </span>
                ) : (
                  <span className="text-xs text-fg-muted flex items-center gap-1">
                    <Circle className="w-3 h-3" /> Not configured
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Summary Stats */}
      <div className="px-4 py-3 grid grid-cols-2 gap-3 border-b border-border">
        <div className="bg-surface-muted rounded-lg p-2">
          <div className="flex items-center gap-2 mb-1">
            <DollarSign className="w-3.5 h-3.5 text-success-color" />
            <span className="text-xs text-fg-muted">Est. Monthly Cost</span>
          </div>
          <span className="text-sm font-medium text-white">
            {estimatedMonthlyCost !== undefined ? `$${estimatedMonthlyCost.toFixed(2)}` : '—'}
          </span>
        </div>
        <div className="bg-surface-muted rounded-lg p-2">
          <div className="flex items-center gap-2 mb-1">
            <Settings className="w-3.5 h-3.5 text-info-color" />
            <span className="text-xs text-fg-muted">Setup Time</span>
          </div>
          <span className="text-sm font-medium text-white">
            {estimatedSetupTime !== undefined ? `~${estimatedSetupTime}min` : '—'}
          </span>
        </div>
      </div>

      {/* Configuration Summary */}
      <div className="px-4 py-3 border-b border-border">
        <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
          Configuration Summary
        </h4>
        <div className="space-y-1">
          {steps.map((step) => {
            const StepIcon = resourceIcons[step.resourceType];
            return (
              <div
                key={step.id}
                className="flex items-center justify-between text-xs py-1"
              >
                <div className="flex items-center gap-2">
                  <StepIcon className="w-3.5 h-3.5 text-fg-muted" />
                  <span className="text-fg">{step.name}</span>
                </div>
                <span
                  className={cn(
                    step.isConfigured ? 'text-success-color' : 'text-fg-muted'
                  )}
                >
                  {step.isConfigured ? 'Ready' : step.isOptional ? 'Skipped' : 'Pending'}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Navigation & Actions */}
      <div className="px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button
            onClick={() => onStepChange?.(currentStepIndex - 1)}
            disabled={!canNavigatePrev}
            className={cn(
              'p-2 rounded-lg transition-colors',
              canNavigatePrev
                ? 'bg-surface-muted hover:bg-muted text-white'
                : 'bg-surface text-fg-muted cursor-not-allowed'
            )}
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <button
            onClick={() => onStepChange?.(currentStepIndex + 1)}
            disabled={!canNavigateNext}
            className={cn(
              'p-2 rounded-lg transition-colors',
              canNavigateNext
                ? 'bg-surface-muted hover:bg-muted text-white'
                : 'bg-surface text-fg-muted cursor-not-allowed'
            )}
          >
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>

        <div className="flex items-center gap-2">
          {canReset && (
            <button
              onClick={onReset}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-surface-muted hover:bg-muted rounded-lg text-xs text-fg transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset
            </button>
          )}
          {canStart && (
            <button
              onClick={onStart}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-primary hover:bg-info-bg rounded-lg text-xs text-white transition-colors"
            >
              <Play className="w-3.5 h-3.5" />
              Start Setup
            </button>
          )}
          {canPause && (
            <button
              onClick={onPause}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-warning-color hover:bg-warning-bg0 rounded-lg text-xs text-white transition-colors"
            >
              <Pause className="w-3.5 h-3.5" />
              Pause
            </button>
          )}
          {canResume && (
            <button
              onClick={onResume}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-success-color hover:bg-success-bg0 rounded-lg text-xs text-white transition-colors"
            >
              <Play className="w-3.5 h-3.5" />
              Resume
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default memo(ConfigurationWizardNode);
