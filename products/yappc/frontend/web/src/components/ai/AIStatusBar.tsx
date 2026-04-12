import React from 'react';
import { OperationStatus, AILabel } from '@ghatana/design-system';
import type { OperationState } from '@ghatana/design-system';
import { cn } from '@/lib/utils';

/**
 * AI Status Bar component.
 *
 * Always-visible bar showing AI state and proactive suggestions.
 * Located at bottom of canvas, provides next best action recommendations.
 *
 * Uses @ghatana/design-system OperationStatus and AILabel for status rendering.
 *
 * @doc.type component
 * @doc.purpose AI status and next action display
 * @doc.layer ui
 */

export type AIStatus = 'ready' | 'thinking' | 'suggesting' | 'error';

export type LifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'BUILD'
  | 'RUN'
  | 'IMPROVE';

export interface NextBestAction {
  title: string;
  description: string;
  action: () => void;
}

export interface AIStatusBarProps {
  status: AIStatus;
  currentPhase: LifecyclePhase;
  phaseProgress: number;
  nextBestAction?: NextBestAction | null;
  onPhaseChange?: (phase: LifecyclePhase) => void;
  className?: string;
}

/** Map product AIStatus values to platform OperationState. */
function toOperationState(status: AIStatus): OperationState {
  switch (status) {
    case 'ready': return 'idle';
    case 'thinking': return 'running';
    case 'suggesting': return 'running';
    case 'error': return 'failed';
  }
}

const STATUS_LABEL: Record<AIStatus, string> = {
  ready: 'Ready',
  thinking: 'Thinking...',
  suggesting: 'Suggesting',
  error: 'Error',
};

const ALL_PHASES: LifecyclePhase[] = [
  'INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'BUILD', 'RUN', 'IMPROVE',
];

function ProgressBar({ value, className }: { value: number; className?: string }) {
  return (
    <div
      className={cn('h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden', className)}
    >
      <div
        className="h-full bg-blue-500 transition-all duration-300 ease-out"
        style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
      />
    </div>
  );
}

export function AIStatusBar({
  status,
  currentPhase,
  phaseProgress,
  nextBestAction,
  onPhaseChange,
  className,
}: AIStatusBarProps) {
  const [showPhaseSelector, setShowPhaseSelector] = React.useState(false);
  const { setCurrentPhase } = useAIStatusBar();
  const prefersReducedMotion = React.useMemo(
    () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
    []
  );

  const handlePhaseSelect = (phase: LifecyclePhase) => {
    if (onPhaseChange) {
      onPhaseChange(phase);
    } else {
      setCurrentPhase(phase);
    }
    setShowPhaseSelector(false);
  };

  return (
    <div
      data-testid="ai-status-bar"
      aria-label="AI Status and next actions"
      className={cn(
        'fixed bottom-0 left-0 right-0 h-12 bg-white dark:bg-gray-900',
        'border-t border-gray-200 dark:border-gray-800',
        'flex items-center px-4 gap-4 z-50',
        'shadow-lg',
        className
      )}
      style={prefersReducedMotion ? { animation: 'none', transition: 'none' } : undefined}
    >
      {/* AI Status Indicator — delegates rendering to platform OperationStatus */}
      <div className="flex items-center gap-2 min-w-[140px]">
        <AILabel variant="badge" size="sm" isSuggestion={status === 'suggesting'} />
        <OperationStatus
          state={toOperationState(status)}
          label={`AI Status: ${STATUS_LABEL[status]}`}
          size="sm"
        />
      </div>

      {/* Phase Progress */}
      <div className="relative flex items-center gap-2 min-w-[200px]">
        <span className="text-sm text-gray-600 dark:text-gray-400">Current Phase: {currentPhase}</span>
        <button
          className="sr-only"
          onClick={() => setShowPhaseSelector((v) => !v)}
          aria-haspopup="listbox"
          aria-label="Select phase"
        >
          {currentPhase}
        </button>
        <ProgressBar value={phaseProgress} className="w-24" />
        <span className="text-sm text-gray-600 dark:text-gray-400">
          {Math.round(phaseProgress)}%
        </span>
        {showPhaseSelector && (
          <ul
            data-testid="phase-selector"
            role="listbox"
            className="absolute bottom-full left-0 mb-1 bg-white border border-gray-200 rounded shadow-lg z-10"
          >
            {ALL_PHASES.map((phase) => (
              <li
                key={phase}
                role="option"
                aria-selected={phase === currentPhase}
                className="px-4 py-1.5 text-sm cursor-pointer hover:bg-gray-100"
                onClick={() => handlePhaseSelect(phase)}
              >
                {phase}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Next Best Action */}
      {nextBestAction ? (
        <div className="ml-auto flex items-center gap-3">
          <span
            className="text-sm font-medium text-gray-900 dark:text-gray-100 cursor-pointer"
            tabIndex={0}
            role="button"
            onClick={nextBestAction.action}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') nextBestAction.action();
            }}
          >
            {nextBestAction.title}
          </span>
        </div>
      ) : (
        <span className="ml-auto text-sm text-gray-500 dark:text-gray-500">
          No suggestions available
        </span>
      )}
    </div>
  );
}

/**
 * Hook for managing AI status bar state.
 *
 * @doc.type hook
 * @doc.purpose AI status bar state management
 */
export function useAIStatusBar() {
  const [status, setStatus] = React.useState<AIStatus>('ready');
  const [currentPhase, setCurrentPhase] =
    React.useState<LifecyclePhase>('INTENT');
  const [phaseProgress, setPhaseProgress] = React.useState(0);
  const [nextBestAction, setNextBestAction] =
    React.useState<NextBestAction | null>(null);

  return {
    status,
    setStatus,
    currentPhase,
    setCurrentPhase,
    phaseProgress,
    setPhaseProgress,
    nextBestAction,
    setNextBestAction,
  };
}
