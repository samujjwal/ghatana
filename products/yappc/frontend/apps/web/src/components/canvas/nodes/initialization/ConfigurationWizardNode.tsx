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
import type { NodeProps } from '@xyflow/react';
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
import type {
  Initialization,
  InitializationConfig,
  InitializationStatus,
  ResourceType,
  InfrastructureTier,
} from '@ghatana/yappc-api';

export interface WizardStep {
  id: string;
  name: string;
  resourceType: ResourceType;
  isConfigured: boolean;
  isOptional: boolean;
  description?: string;
}

export interface ConfigurationWizardNodeData {
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

const statusConfig: Record<
  InitializationStatus,
  { color: string; bgColor: string; label: string; icon: typeof Circle }
> = {
  NOT_STARTED: { color: 'text-gray-400', bgColor: 'bg-gray-500/20', label: 'Not Started', icon: Circle },
  IN_PROGRESS: { color: 'text-blue-400', bgColor: 'bg-blue-500/20', label: 'In Progress', icon: Loader2 },
  PAUSED: { color: 'text-yellow-400', bgColor: 'bg-yellow-500/20', label: 'Paused', icon: Pause },
  COMPLETED: { color: 'text-green-400', bgColor: 'bg-green-500/20', label: 'Completed', icon: Check },
  FAILED: { color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Failed', icon: Circle },
  ROLLED_BACK: { color: 'text-orange-400', bgColor: 'bg-orange-500/20', label: 'Rolled Back', icon: RotateCcw },
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

function ConfigurationWizardNode({ data }: NodeProps<ConfigurationWizardNodeData>) {
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

  const status = initialization?.status ?? 'NOT_STARTED';
  const statusInfo = statusConfig[status];
  const StatusIcon = statusInfo.icon;

  const canNavigatePrev = currentStepIndex > 0 && status === 'NOT_STARTED';
  const canNavigateNext = currentStepIndex < steps.length - 1 && status === 'NOT_STARTED';
  const canStart = status === 'NOT_STARTED' && steps.some((s) => s.isConfigured);
  const canPause = status === 'IN_PROGRESS';
  const canResume = status === 'PAUSED';
  const canReset = status === 'COMPLETED' || status === 'FAILED' || status === 'ROLLED_BACK';

  return (
    <div className="bg-slate-800 rounded-lg border border-slate-700 shadow-xl min-w-[420px] max-w-[500px]">
      <Handle type="target" position={Position.Left} className="!bg-blue-500" />
      <Handle type="source" position={Position.Right} className="!bg-blue-500" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-gradient-to-br from-blue-500/20 to-purple-500/20 rounded-lg">
            <Settings className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Configuration Wizard</h3>
            <p className="text-xs text-slate-400">
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
      <div className="px-4 py-3 border-b border-slate-700">
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
                    isActive && 'ring-2 ring-blue-500 ring-offset-2 ring-offset-slate-800',
                    isCompleted ? 'bg-green-500/20' : isPast ? 'bg-slate-600' : 'bg-slate-700',
                    status === 'NOT_STARTED' && 'hover:bg-slate-600 cursor-pointer'
                  )}
                >
                  {isCompleted ? (
                    <Check className="w-4 h-4 text-green-400" />
                  ) : (
                    <StepIcon
                      className={cn(
                        'w-4 h-4',
                        isActive ? 'text-blue-400' : 'text-slate-400'
                      )}
                    />
                  )}
                  {step.isOptional && (
                    <span className="absolute -top-1 -right-1 w-2 h-2 bg-yellow-500 rounded-full" />
                  )}
                </button>
                {index < steps.length - 1 && (
                  <div
                    className={cn(
                      'w-8 h-0.5 mx-1',
                      isCompleted ? 'bg-green-500/40' : 'bg-slate-600'
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
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-start gap-3">
            {(() => {
              const CurrentStepIcon = resourceIcons[steps[currentStepIndex].resourceType];
              return (
                <div className="p-2 bg-slate-700 rounded-lg">
                  <CurrentStepIcon className="w-5 h-5 text-slate-300" />
                </div>
              );
            })()}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-medium text-white">{steps[currentStepIndex].name}</h4>
                {steps[currentStepIndex].isOptional && (
                  <span className="text-xs text-yellow-400 bg-yellow-500/10 px-1.5 py-0.5 rounded">
                    Optional
                  </span>
                )}
              </div>
              {steps[currentStepIndex].description && (
                <p className="text-xs text-slate-400 mt-1">
                  {steps[currentStepIndex].description}
                </p>
              )}
              <div className="flex items-center gap-2 mt-2">
                {steps[currentStepIndex].isConfigured ? (
                  <span className="text-xs text-green-400 flex items-center gap-1">
                    <Check className="w-3 h-3" /> Configured
                  </span>
                ) : (
                  <span className="text-xs text-slate-400 flex items-center gap-1">
                    <Circle className="w-3 h-3" /> Not configured
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Summary Stats */}
      <div className="px-4 py-3 grid grid-cols-2 gap-3 border-b border-slate-700">
        <div className="bg-slate-700/50 rounded-lg p-2">
          <div className="flex items-center gap-2 mb-1">
            <DollarSign className="w-3.5 h-3.5 text-green-400" />
            <span className="text-xs text-slate-400">Est. Monthly Cost</span>
          </div>
          <span className="text-sm font-medium text-white">
            {estimatedMonthlyCost !== undefined ? `$${estimatedMonthlyCost.toFixed(2)}` : '—'}
          </span>
        </div>
        <div className="bg-slate-700/50 rounded-lg p-2">
          <div className="flex items-center gap-2 mb-1">
            <Settings className="w-3.5 h-3.5 text-blue-400" />
            <span className="text-xs text-slate-400">Setup Time</span>
          </div>
          <span className="text-sm font-medium text-white">
            {estimatedSetupTime !== undefined ? `~${estimatedSetupTime}min` : '—'}
          </span>
        </div>
      </div>

      {/* Configuration Summary */}
      <div className="px-4 py-3 border-b border-slate-700">
        <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
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
                  <StepIcon className="w-3.5 h-3.5 text-slate-400" />
                  <span className="text-slate-300">{step.name}</span>
                </div>
                <span
                  className={cn(
                    step.isConfigured ? 'text-green-400' : 'text-slate-500'
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
                ? 'bg-slate-700 hover:bg-slate-600 text-white'
                : 'bg-slate-800 text-slate-600 cursor-not-allowed'
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
                ? 'bg-slate-700 hover:bg-slate-600 text-white'
                : 'bg-slate-800 text-slate-600 cursor-not-allowed'
            )}
          >
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>

        <div className="flex items-center gap-2">
          {canReset && (
            <button
              onClick={onReset}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-700 hover:bg-slate-600 rounded-lg text-xs text-slate-300 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset
            </button>
          )}
          {canStart && (
            <button
              onClick={onStart}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-xs text-white transition-colors"
            >
              <Play className="w-3.5 h-3.5" />
              Start Setup
            </button>
          )}
          {canPause && (
            <button
              onClick={onPause}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-yellow-600 hover:bg-yellow-500 rounded-lg text-xs text-white transition-colors"
            >
              <Pause className="w-3.5 h-3.5" />
              Pause
            </button>
          )}
          {canResume && (
            <button
              onClick={onResume}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-green-600 hover:bg-green-500 rounded-lg text-xs text-white transition-colors"
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
