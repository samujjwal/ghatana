import React from 'react';
import { OperationStatus, AILabel } from '@ghatana/design-system';
import type { OperationState } from '@ghatana/design-system';
import { cn } from '@/lib/utils';
import { AITypeChip, type AITypeChipProps } from './AITypeChip';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

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
  aiType?: AITypeChipProps['type'];
  aiConfidence?: AITypeChipProps['confidence'];
  aiRationale?: AITypeChipProps['rationale'];
  aiSources?: AITypeChipProps['sources'];
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
      className={cn('h-2 bg-surface-muted dark:bg-surface-muted rounded-full overflow-hidden', className)}
    >
      <div
        className="h-full bg-info-bg transition-all duration-300 ease-out"
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
  aiType,
  aiConfidence,
  aiRationale,
  aiSources,
}: AIStatusBarProps) {
  const { t } = useTranslation('common');
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
      aria-label={t('aiStatus.barAria')}
      className={cn(
        'fixed bottom-0 left-0 right-0 h-12 bg-white dark:bg-surface',
        'border-t border-border dark:border-border',
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
        {aiType && (
          <AITypeChip
            type={aiType}
            confidence={aiConfidence}
            rationale={aiRationale}
            sources={aiSources}
            size="sm"
          />
        )}
      </div>

      {/* Phase Progress */}
      <div className="relative flex items-center gap-2 min-w-[200px]">
        <span className="text-sm text-fg-muted dark:text-fg-muted">Current Phase: {currentPhase}</span>
        <Button
          variant="ghost"
          size="sm"
          className="sr-only"
          onClick={() => setShowPhaseSelector((v) => !v)}
          aria-haspopup="listbox"
          aria-label={t('aiStatus.selectPhase')}
        >
          {currentPhase}
        </Button>
        <ProgressBar value={phaseProgress} className="w-24" />
        <span className="text-sm text-fg-muted dark:text-fg-muted">
          {Math.round(phaseProgress)}%
        </span>
        {showPhaseSelector && (
          <ul
            data-testid="phase-selector"
            role="listbox"
            className="absolute bottom-full left-0 mb-1 bg-white border border-border rounded shadow-lg z-10"
          >
            {ALL_PHASES.map((phase) => (
              <li
                key={phase}
                role="option"
                aria-selected={phase === currentPhase}
                className="px-4 py-1.5 text-sm cursor-pointer hover:bg-surface-muted"
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
            className="text-sm font-medium text-fg dark:text-fg-muted cursor-pointer"
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
        <span className="ml-auto text-sm text-fg-muted dark:text-fg-muted">
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
  const [aiType, setAIType] = React.useState<AITypeChipProps['type']>();
  const [aiConfidence, setAIConfidence] = React.useState<AITypeChipProps['confidence']>();
  const [aiRationale, setAIRationale] = React.useState<AITypeChipProps['rationale']>();
  const [aiSources, setAISources] = React.useState<AITypeChipProps['sources']>();

  return {
    status,
    setStatus,
    currentPhase,
    setCurrentPhase,
    phaseProgress,
    setPhaseProgress,
    nextBestAction,
    setNextBestAction,
    aiType,
    setAIType,
    aiConfidence,
    setAIConfidence,
    aiRationale,
    setAIRationale,
    aiSources,
    setAISources,
  };
}
