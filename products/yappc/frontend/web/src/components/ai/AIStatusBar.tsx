import React from 'react';
import { cn } from '@/lib/utils';

/**
 * AI Status Bar component.
 *
 * Always-visible bar showing AI state and proactive suggestions.
 * Located at bottom of canvas, provides next best action recommendations.
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
  className?: string;
}

function AIStatusIcon({ status }: { status: AIStatus }) {
  const statusConfig = {
    ready: {
      icon: '●',
      color: 'text-green-500',
      animate: false,
    },
    thinking: {
      icon: '◐',
      color: 'text-blue-500',
      animate: true,
    },
    suggesting: {
      icon: '✦',
      color: 'text-purple-500',
      animate: true,
    },
    error: {
      icon: '⚠',
      color: 'text-red-500',
      animate: false,
    },
  };

  const config = statusConfig[status];

  return (
    <span
      className={cn('text-lg', config.color, config.animate && 'animate-pulse')}
    >
      {config.icon}
    </span>
  );
}

function ProgressBar({
  value,
  className,
}: {
  value: number;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden',
        className
      )}
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
  className,
}: AIStatusBarProps) {
  const statusText = {
    ready: 'AI Ready',
    thinking: 'AI Thinking...',
    suggesting: 'AI has suggestions',
    error: 'AI Error',
  };

  return (
    <div
      className={cn(
        'fixed bottom-0 left-0 right-0 h-12 bg-white dark:bg-gray-900',
        'border-t border-gray-200 dark:border-gray-800',
        'flex items-center px-4 gap-4 z-50',
        'shadow-lg',
        className
      )}
    >
      {/* AI Status Indicator */}
      <div className="flex items-center gap-2 min-w-[120px]">
        <AIStatusIcon status={status} />
        <span className="text-sm text-gray-600 dark:text-gray-400">
          {statusText[status]}
        </span>
      </div>

      {/* Phase Progress */}
      <div className="flex items-center gap-2 min-w-[200px]">
        <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {currentPhase}
        </span>
        <ProgressBar value={phaseProgress} className="w-24" />
        <span className="text-sm text-gray-600 dark:text-gray-400">
          {Math.round(phaseProgress)}%
        </span>
      </div>

      {/* Next Best Action */}
      {nextBestAction && (
        <div className="ml-auto flex items-center gap-3">
          <div className="flex flex-col items-end">
            <span className="text-xs text-gray-500 dark:text-gray-500">
              Next:
            </span>
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {nextBestAction.title}
            </span>
          </div>
          <button
            onClick={nextBestAction.action}
            className={cn(
              'px-4 py-1.5 rounded-md text-sm font-medium',
              'bg-blue-500 hover:bg-blue-600 text-white',
              'transition-colors duration-200',
              'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2'
            )}
          >
            Go →
          </button>
        </div>
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
