/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Lifecycle Components
 */

import React from 'react';

/**
 * Lifecycle Stage Definition
 * 
 * Maps to canonical stages from stages.yaml:
 * intent → context → plan → execute → verify → observe → learn → institutionalize
 */
export const LIFECYCLE_STAGES = [
  { id: 'intent', label: 'Intent', description: 'Define what needs to be built' },
  { id: 'context', label: 'Context', description: 'Gather requirements and constraints' },
  { id: 'plan', label: 'Plan', description: 'Design the solution approach' },
  { id: 'execute', label: 'Execute', description: 'Build and implement' },
  { id: 'verify', label: 'Verify', description: 'Test and validate' },
  { id: 'observe', label: 'Observe', description: 'Monitor in production' },
  { id: 'learn', label: 'Learn', description: 'Analyze and improve' },
  { id: 'institutionalize', label: 'Institutionalize', description: 'Document and standardize' },
] as const;

export type LifecycleStageId = typeof LIFECYCLE_STAGES[number]['id'];

export interface LifecycleStageProps {
  /** Current active stage */
  currentStage: LifecycleStageId;
  /** Completed stages (optional, calculated from current if not provided) */
  completedStages?: LifecycleStageId[];
  /** Click handler for stage navigation */
  onStageClick?: (stage: LifecycleStageId) => void;
  /** Disable navigation */
  readonly?: boolean;
  /** Compact mode for smaller spaces */
  compact?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/**
 * LifecycleStage Component
 * 
 * Visual indicator of project lifecycle progression with 8 canonical stages.
 * Shows current position, completed stages, and allows navigation between stages.
 * 
 * @example
 * ```tsx
 * <LifecycleStage 
 *   currentStage="execute" 
 *   onStageClick={(stage) => navigateToStage(stage)}
 * />
 * ```
 */
export function LifecycleStage({
  currentStage,
  completedStages,
  onStageClick,
  readonly = false,
  compact = false,
  className = '',
}: LifecycleStageProps) {
  const currentIndex = LIFECYCLE_STAGES.findIndex(s => s.id === currentStage);
  
  // Calculate completed stages if not explicitly provided
  const completed = completedStages ?? 
    LIFECYCLE_STAGES.slice(0, currentIndex).map(s => s.id);

  const isCompleted = (stageId: LifecycleStageId) => completed.includes(stageId);
  const isCurrent = (stageId: LifecycleStageId) => stageId === currentStage;
  const isPending = (stageId: LifecycleStageId) => {
    const stageIndex = LIFECYCLE_STAGES.findIndex(s => s.id === stageId);
    return stageIndex > currentIndex;
  };

  return (
    <div 
      className={`lifecycle-stage ${compact ? 'lifecycle-stage--compact' : ''} ${className}`}
      role="navigation"
      aria-label="Project lifecycle stages"
    >
      <div className="lifecycle-stage__track">
        {LIFECYCLE_STAGES.map((stage, index) => {
          const completed_stage = isCompleted(stage.id);
          const current = isCurrent(stage.id);
          const pending = isPending(stage.id);
          const clickable = !readonly && !pending && onStageClick;

          return (
            <div
              key={stage.id}
              className={`
                lifecycle-stage__node
                ${completed_stage ? 'lifecycle-stage__node--completed' : ''}
                ${current ? 'lifecycle-stage__node--current' : ''}
                ${pending ? 'lifecycle-stage__node--pending' : ''}
                ${clickable ? 'lifecycle-stage__node--clickable' : ''}
              `}
            >
              {/* Connector line */}
              {index > 0 && (
                <div 
                  className={`lifecycle-stage__connector ${completed_stage ? 'lifecycle-stage__connector--completed' : ''}`}
                />
              )}

              {/* Stage node */}
              <button
                type="button"
                disabled={!clickable}
                onClick={() => clickable && onStageClick?.(stage.id)}
                className="lifecycle-stage__button"
                aria-current={current ? 'step' : undefined}
                aria-label={`${stage.label}: ${stage.description}${current ? ' (current)' : ''}`}
              >
                {/* Icon based on state */}
                <span className="lifecycle-stage__icon">
                  {completed_stage ? (
                    <CheckIcon />
                  ) : current ? (
                    <CurrentIcon />
                  ) : (
                    <PendingIcon />
                  )}
                </span>

                {/* Label */}
                {!compact && (
                  <span className="lifecycle-stage__label">{stage.label}</span>
                )}

                {/* Compact tooltip */}
                {compact && (
                  <span className="lifecycle-stage__tooltip">
                    {stage.label}: {stage.description}
                  </span>
                )}
              </button>

              {/* Stage description (non-compact only) */}
              {!compact && current && (
                <span className="lifecycle-stage__description">
                  {stage.description}
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Progress summary */}
      <div className="lifecycle-stage__summary">
        <span className="lifecycle-stage__progress">
          Stage {currentIndex + 1} of {LIFECYCLE_STAGES.length}
        </span>
        <span className="lifecycle-stage__percentage">
          {Math.round((completed.length / LIFECYCLE_STAGES.length) * 100)}% complete
        </span>
      </div>
    </div>
  );
}

// Icon components
function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="lifecycle-stage__svg lifecycle-stage__svg--check">
      <path d="M20 6L9 17l-5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CurrentIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="lifecycle-stage__svg lifecycle-stage__svg--current">
      <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2" />
      <circle cx="12" cy="12" r="3" fill="currentColor" />
    </svg>
  );
}

function PendingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="lifecycle-stage__svg lifecycle-stage__svg--pending">
      <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}

// CSS styles (can be extracted to separate CSS file)
export const lifecycleStageStyles = `
.lifecycle-stage {
  --stage-color-completed: #22c55e;
  --stage-color-current: #3b82f6;
  --stage-color-pending: #d1d5db;
  --stage-connector-width: 2rem;
  
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.lifecycle-stage__track {
  display: flex;
  align-items: flex-start;
  gap: var(--stage-connector-width);
}

.lifecycle-stage__node {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex: 1;
}

.lifecycle-stage__connector {
  position: absolute;
  left: -50%;
  top: 1rem;
  width: var(--stage-connector-width);
  height: 2px;
  background: var(--stage-color-pending);
  transform: translateX(50%);
}

.lifecycle-stage__connector--completed {
  background: var(--stage-color-completed);
}

.lifecycle-stage__button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 0.5rem;
}

.lifecycle-stage__button:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.1);
}

.lifecycle-stage__button:disabled {
  cursor: default;
}

.lifecycle-stage__icon {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--stage-color-pending);
  color: white;
  transition: all 0.2s ease;
}

.lifecycle-stage__node--completed .lifecycle-stage__icon {
  background: var(--stage-color-completed);
}

.lifecycle-stage__node--current .lifecycle-stage__icon {
  background: var(--stage-color-current);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
}

.lifecycle-stage__svg {
  width: 1rem;
  height: 1rem;
}

.lifecycle-stage__label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #374151;
  text-align: center;
}

.lifecycle-stage__node--current .lifecycle-stage__label {
  color: var(--stage-color-current);
  font-weight: 600;
}

.lifecycle-stage__description {
  font-size: 0.625rem;
  color: #6b7280;
  text-align: center;
  max-width: 6rem;
  margin-top: 0.25rem;
}

.lifecycle-stage__summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 0.75rem;
  border-top: 1px solid #e5e7eb;
  font-size: 0.75rem;
  color: #6b7280;
}

.lifecycle-stage__progress {
  font-weight: 500;
}

.lifecycle-stage__percentage {
  font-weight: 600;
  color: var(--stage-color-current);
}

/* Compact mode */
.lifecycle-stage--compact .lifecycle-stage__track {
  gap: 0.5rem;
}

.lifecycle-stage--compact .lifecycle-stage__node {
  flex: none;
}

.lifecycle-stage--compact .lifecycle-stage__icon {
  width: 1.5rem;
  height: 1.5rem;
}

.lifecycle-stage--compact .lifecycle-stage__connector {
  width: 0.5rem;
  left: -25%;
  top: 0.75rem;
}

.lifecycle-stage__tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.5rem;
  background: #1f2937;
  color: white;
  font-size: 0.75rem;
  border-radius: 0.25rem;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
  z-index: 10;
}

.lifecycle-stage__button:hover .lifecycle-stage__tooltip {
  opacity: 1;
}
`;
