/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Lifecycle Stage Navigation
 */

import { LifecycleStage, LifecycleStageId, LIFECYCLE_STAGES } from './LifecycleStage';

export interface StageNavigationProps {
  projectId: string;
  projectName: string;
  currentStage: LifecycleStageId;
  availableStages: LifecycleStageId[];
  blockedStages?: LifecycleStageId[];
  completedStages?: LifecycleStageId[];
  onNavigate: (stage: LifecycleStageId) => void;
  onAdvance?: () => void;
  onGoBack?: () => void;
  canAdvance: boolean;
  canGoBack: boolean;
  advanceReason?: string;
  blockReason?: string;
  className?: string;
}

/**
 * StageNavigation Component
 * 
 * Provides lifecycle stage navigation with advance/go-back controls,
 * stage-specific actions, and gate information.
 * 
 * @example
 * ```tsx
 * <StageNavigation
 *   projectId="proj-123"
 *   currentStage="execute"
 *   availableStages={['plan', 'execute', 'verify']}
 *   canAdvance={true}
 *   canGoBack={true}
 *   onNavigate={(stage) => switchToStage(stage)}
 *   onAdvance={() => advanceProject()}
 * />
 * ```
 */
export function StageNavigation({
  projectId,
  projectName,
  currentStage,
  availableStages,
  blockedStages = [],
  completedStages = [],
  onNavigate,
  onAdvance,
  onGoBack,
  canAdvance,
  canGoBack,
  advanceReason,
  blockReason,
  className = '',
}: StageNavigationProps) {
  const currentIndex = LIFECYCLE_STAGES.findIndex(s => s.id === currentStage);
  const nextStage = LIFECYCLE_STAGES[currentIndex + 1]?.id;
  const prevStage = LIFECYCLE_STAGES[currentIndex - 1]?.id;

  const stageInfo = LIFECYCLE_STAGES.find(s => s.id === currentStage);

  return (
    <div className={`stage-navigation ${className}`}>
      {/* Header */}
      <div className="stage-navigation__header">
        <h2 className="stage-navigation__title">{projectName}</h2>
        <span className="stage-navigation__id">{projectId}</span>
      </div>

      {/* Stage Indicator */}
      <div className="stage-navigation__indicator">
        <LifecycleStage
          currentStage={currentStage}
          completedStages={completedStages}
          onStageClick={(stage) => {
            if (availableStages.includes(stage)) {
              onNavigate(stage);
            }
          }}
          readonly={availableStages.length === 0}
        />
      </div>

      {/* Current Stage Details */}
      <div className="stage-navigation__details">
        <div className="stage-navigation__current">
          <span className="stage-navigation__current-label">Current Stage</span>
          <span className="stage-navigation__current-value">{stageInfo?.label}</span>
          <p className="stage-navigation__current-description">{stageInfo?.description}</p>
        </div>

        {/* Stage Progress */}
        <div className="stage-navigation__progress">
          <div className="stage-navigation__progress-bar">
            <div 
              className="stage-navigation__progress-fill"
              style={{ width: `${((currentIndex + 1) / LIFECYCLE_STAGES.length) * 100}%` }}
            />
          </div>
          <span className="stage-navigation__progress-text">
            {currentIndex + 1} of {LIFECYCLE_STAGES.length} stages
          </span>
        </div>
      </div>

      {/* Navigation Controls */}
      <div className="stage-navigation__controls">
        {/* Go Back Button */}
        {prevStage && (
          <button
            className="stage-navigation__btn stage-navigation__btn--back"
            onClick={onGoBack}
            disabled={!canGoBack}
            title={!canGoBack ? 'Cannot go back from current stage' : `Return to ${prevStage}`}
          >
            <BackIcon />
            <span>Back to {prevStage}</span>
          </button>
        )}

        {/* Spacer */}
        <div className="stage-navigation__spacer" />

        {/* Advance Button */}
        {nextStage && (
          <button
            className={`stage-navigation__btn stage-navigation__btn--advance ${
              canAdvance ? '' : 'stage-navigation__btn--blocked'
            }`}
            onClick={onAdvance}
            disabled={!canAdvance}
          >
            <span>Advance to {nextStage}</span>
            <ForwardIcon />
          </button>
        )}
      </div>

      {/* Status Messages */}
      {canAdvance && advanceReason && (
        <div className="stage-navigation__message stage-navigation__message--success">
          <CheckIcon />
          <span>{advanceReason}</span>
        </div>
      )}

      {!canAdvance && blockReason && (
        <div className="stage-navigation__message stage-navigation__message--blocked">
          <BlockIcon />
          <span>{blockReason}</span>
        </div>
      )}

      {/* Blocked Stages */}
      {blockedStages.length > 0 && (
        <div className="stage-navigation__blocked">
          <h4 className="stage-navigation__blocked-title">Blocked Stages</h4>
          <ul className="stage-navigation__blocked-list">
            {blockedStages.map(stage => (
              <li key={stage} className="stage-navigation__blocked-item">
                <BlockIcon />
                <span>{LIFECYCLE_STAGES.find(s => s.id === stage)?.label || stage}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Quick Actions */}
      <div className="stage-navigation__actions">
        <h4 className="stage-navigation__actions-title">Stage Actions</h4>
        <div className="stage-navigation__actions-grid">
          <StageAction 
            icon="📋"
            label="View Tasks"
            count={12}
            onClick={() => {}}
          />
          <StageAction 
            icon="📊"
            label="Metrics"
            onClick={() => {}}
          />
          <StageAction 
            icon="📄"
            label="Artifacts"
            count={5}
            onClick={() => {}}
          />
          <StageAction 
            icon="🔄"
            label="History"
            onClick={() => {}}
          />
        </div>
      </div>
    </div>
  );
}

// Stage Action Button
interface StageActionProps {
  icon: string;
  label: string;
  count?: number;
  onClick: () => void;
}

function StageAction({ icon, label, count, onClick }: StageActionProps) {
  return (
    <button className="stage-action" onClick={onClick}>
      <span className="stage-action__icon">{icon}</span>
      <span className="stage-action__label">{label}</span>
      {count !== undefined && (
        <span className="stage-action__count">{count}</span>
      )}
    </button>
  );
}

// Icons
function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="stage-navigation__icon">
      <path d="M19 12H5M12 19l-7-7 7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function ForwardIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="stage-navigation__icon">
      <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="stage-navigation__icon stage-navigation__icon--success">
      <path d="M20 6L9 17l-5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function BlockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="stage-navigation__icon stage-navigation__icon--blocked">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
      <path d="M4.93 4.93l14.14 14.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

// CSS styles
export const stageNavigationStyles = `
.stage-navigation {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1.5rem;
  background: white;
  border-radius: 0.75rem;
  border: 1px solid #e5e7eb;
}

.stage-navigation__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1rem;
  border-bottom: 1px solid #f3f4f6;
}

.stage-navigation__title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.stage-navigation__id {
  font-size: 0.75rem;
  color: #9ca3af;
  font-family: monospace;
}

.stage-navigation__indicator {
  padding: 1rem 0;
}

.stage-navigation__details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 0.5rem;
}

.stage-navigation__current {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.stage-navigation__current-label {
  font-size: 0.75rem;
  color: #6b7280;
  text-transform: uppercase;
  font-weight: 500;
}

.stage-navigation__current-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #111827;
}

.stage-navigation__current-description {
  margin: 0;
  color: #6b7280;
  font-size: 0.875rem;
}

.stage-navigation__progress {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.stage-navigation__progress-bar {
  flex: 1;
  height: 8px;
  background: #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}

.stage-navigation__progress-fill {
  height: 100%;
  background: #3b82f6;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.stage-navigation__progress-text {
  font-size: 0.875rem;
  color: #6b7280;
  white-space: nowrap;
}

.stage-navigation__controls {
  display: flex;
  gap: 1rem;
}

.stage-navigation__spacer {
  flex: 1;
}

.stage-navigation__btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 0.5rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.stage-navigation__btn--back {
  background: #f3f4f6;
  color: #374151;
}

.stage-navigation__btn--back:hover:not(:disabled) {
  background: #e5e7eb;
}

.stage-navigation__btn--advance {
  background: #3b82f6;
  color: white;
}

.stage-navigation__btn--advance:hover:not(:disabled) {
  background: #2563eb;
}

.stage-navigation__btn--blocked {
  background: #fee2e2;
  color: #ef4444;
  cursor: not-allowed;
}

.stage-navigation__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.stage-navigation__icon {
  width: 1.25rem;
  height: 1.25rem;
}

.stage-navigation__icon--success {
  color: #22c55e;
}

.stage-navigation__icon--blocked {
  color: #ef4444;
}

.stage-navigation__message {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  border-radius: 0.5rem;
  font-size: 0.875rem;
}

.stage-navigation__message--success {
  background: #dcfce7;
  color: #166534;
}

.stage-navigation__message--blocked {
  background: #fee2e2;
  color: #991b1b;
}

.stage-navigation__blocked {
  padding: 1rem;
  background: #fef2f2;
  border-radius: 0.5rem;
  border: 1px solid #fecaca;
}

.stage-navigation__blocked-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #991b1b;
  margin: 0 0 0.5rem 0;
}

.stage-navigation__blocked-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.stage-navigation__blocked-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #b91c1c;
}

.stage-navigation__actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.stage-navigation__actions-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
}

.stage-navigation__actions-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}

.stage-action {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
}

.stage-action:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.stage-action__icon {
  font-size: 1.25rem;
}

.stage-action__label {
  flex: 1;
  font-size: 0.875rem;
  color: #374151;
}

.stage-action__count {
  padding: 0.125rem 0.5rem;
  background: #e5e7eb;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
  color: #374151;
}
`;
