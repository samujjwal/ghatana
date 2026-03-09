/**
 * RunbookCard Component
 *
 * @description Displays a runbook with details, execution status,
 * and quick actions for the operations runbook library.
 *
 * @doc.type component
 * @doc.purpose Runbook display and execution
 * @doc.layer presentation
 * @doc.phase 4
 */

import React from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type RunbookStatus = 'draft' | 'published' | 'deprecated';
export type RunbookExecutionStatus = 'idle' | 'running' | 'completed' | 'failed' | 'cancelled';
export type StepType = 'manual' | 'automated' | 'approval' | 'conditional';

export interface RunbookStep {
  id: string;
  order: number;
  name: string;
  description?: string;
  type: StepType;
  estimatedDuration?: number;
  commands?: string[];
}

export interface RunbookExecution {
  id: string;
  status: RunbookExecutionStatus;
  currentStep?: number;
  startedAt?: string;
  completedAt?: string;
  executedBy?: string;
}

export interface Runbook {
  id: string;
  name: string;
  description: string;
  status: RunbookStatus;
  category: string;
  tags: string[];
  steps: RunbookStep[];
  estimatedDuration?: number;
  lastExecuted?: string;
  executionCount?: number;
  author: string;
  updatedAt: string;
}

export interface RunbookCardProps {
  runbook: Runbook;
  execution?: RunbookExecution;
  onExecute?: (runbookId: string) => void;
  onViewDetails?: (runbookId: string) => void;
  onEdit?: (runbookId: string) => void;
  compact?: boolean;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getStatusConfig = (status: RunbookStatus) => {
  const configs: Record<RunbookStatus, { label: string; color: string; bg: string }> = {
    draft: { label: 'Draft', color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' },
    published: { label: 'Published', color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' },
    deprecated: { label: 'Deprecated', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' },
  };
  return configs[status];
};

const getExecutionStatusConfig = (status: RunbookExecutionStatus) => {
  const configs: Record<RunbookExecutionStatus, { label: string; color: string; icon: string }> = {
    idle: { label: 'Ready', color: '#6B7280', icon: '⏸️' },
    running: { label: 'Running', color: '#3B82F6', icon: '▶️' },
    completed: { label: 'Completed', color: '#10B981', icon: '✅' },
    failed: { label: 'Failed', color: '#EF4444', icon: '❌' },
    cancelled: { label: 'Cancelled', color: '#6B7280', icon: '⛔' },
  };
  return configs[status];
};

const getStepTypeConfig = (type: StepType) => {
  const configs: Record<StepType, { icon: string; label: string }> = {
    manual: { icon: '👤', label: 'Manual' },
    automated: { icon: '🤖', label: 'Automated' },
    approval: { icon: '✋', label: 'Approval' },
    conditional: { icon: '🔀', label: 'Conditional' },
  };
  return configs[type];
};

const formatDuration = (minutes?: number): string => {
  if (!minutes) return 'Unknown';
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
};

const formatRelativeTime = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
  return date.toLocaleDateString();
};

// ============================================================================
// Step Progress Sub-component
// ============================================================================

interface StepProgressProps {
  steps: RunbookStep[];
  currentStep?: number;
  status: RunbookExecutionStatus;
}

const StepProgress: React.FC<StepProgressProps> = ({ steps, currentStep, status }) => {
  return (
    <div className="step-progress">
      {steps.map((step, index) => {
        const stepConfig = getStepTypeConfig(step.type);
        const isCompleted = currentStep !== undefined && index < currentStep;
        const isCurrent = currentStep !== undefined && index === currentStep;
        const isFailed = status === 'failed' && isCurrent;

        return (
          <div
            key={step.id}
            className={cn(
              'step-item',
              isCompleted && 'step-item--completed',
              isCurrent && 'step-item--current',
              isFailed && 'step-item--failed'
            )}
          >
            <div className="step-indicator">
              {isCompleted ? '✓' : isFailed ? '✕' : index + 1}
            </div>
            {index < steps.length - 1 && (
              <div className={cn('step-connector', isCompleted && 'step-connector--completed')} />
            )}
          </div>
        );
      })}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const RunbookCard: React.FC<RunbookCardProps> = ({
  runbook,
  execution,
  onExecute,
  onViewDetails,
  onEdit,
  compact = false,
  className,
}) => {
  const statusConfig = getStatusConfig(runbook.status);
  const execStatusConfig = execution ? getExecutionStatusConfig(execution.status) : null;

  const canExecute = runbook.status === 'published' && (!execution || execution.status === 'idle');
  const isRunning = execution?.status === 'running';

  // Count step types
  const stepTypeCounts = runbook.steps.reduce(
    (acc, step) => {
      acc[step.type] = (acc[step.type] || 0) + 1;
      return acc;
    },
    {} as Record<StepType, number>
  );

  return (
    <div
      className={cn(
        'runbook-card',
        compact && 'runbook-card--compact',
        isRunning && 'runbook-card--running',
        className
      )}
      onClick={() => onViewDetails?.(runbook.id)}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && onViewDetails) {
          e.preventDefault();
          onViewDetails(runbook.id);
        }
      }}
      role={onViewDetails ? 'button' : undefined}
      tabIndex={onViewDetails ? 0 : undefined}
    >
      {/* Header */}
      <div className="runbook-header">
        <div className="runbook-title-row">
          <span className="runbook-icon">📋</span>
          <h4 className="runbook-name">{runbook.name}</h4>
        </div>
        <div className="runbook-badges">
          <span
            className="status-badge"
            style={{ color: statusConfig.color, backgroundColor: statusConfig.bg }}
          >
            {statusConfig.label}
          </span>
          {execStatusConfig && (
            <span
              className="exec-status-badge"
              style={{ color: execStatusConfig.color }}
            >
              {execStatusConfig.icon} {execStatusConfig.label}
            </span>
          )}
        </div>
      </div>

      {/* Description */}
      {!compact && (
        <p className="runbook-description">{runbook.description}</p>
      )}

      {/* Execution Progress */}
      {isRunning && execution && (
        <div className="runbook-progress">
          <StepProgress
            steps={runbook.steps}
            currentStep={execution.currentStep}
            status={execution.status}
          />
          <span className="progress-label">
            Step {(execution.currentStep || 0) + 1} of {runbook.steps.length}
          </span>
        </div>
      )}

      {/* Stats */}
      <div className="runbook-stats">
        <div className="stat-item">
          <span className="stat-icon">📊</span>
          <span className="stat-value">{runbook.steps.length}</span>
          <span className="stat-label">Steps</span>
        </div>
        <div className="stat-item">
          <span className="stat-icon">⏱️</span>
          <span className="stat-value">{formatDuration(runbook.estimatedDuration)}</span>
          <span className="stat-label">Est. Time</span>
        </div>
        {runbook.executionCount !== undefined && (
          <div className="stat-item">
            <span className="stat-icon">🔄</span>
            <span className="stat-value">{runbook.executionCount}</span>
            <span className="stat-label">Runs</span>
          </div>
        )}
      </div>

      {/* Step Types */}
      {!compact && (
        <div className="step-types">
          {Object.entries(stepTypeCounts).map(([type, count]) => {
            const config = getStepTypeConfig(type as StepType);
            return (
              <span key={type} className="step-type-badge">
                {config.icon} {count} {config.label}
              </span>
            );
          })}
        </div>
      )}

      {/* Tags */}
      {!compact && runbook.tags.length > 0 && (
        <div className="runbook-tags">
          {runbook.tags.map((tag) => (
            <span key={tag} className="tag">
              {tag}
            </span>
          ))}
        </div>
      )}

      {/* Footer */}
      <div className="runbook-footer">
        <div className="runbook-meta">
          {runbook.lastExecuted && (
            <span className="meta-item">
              Last run: {formatRelativeTime(runbook.lastExecuted)}
            </span>
          )}
          <span className="meta-item">
            Updated: {formatRelativeTime(runbook.updatedAt)}
          </span>
        </div>

        {/* Actions */}
        <div className="runbook-actions">
          {onEdit && (
            <button
              type="button"
              className="action-btn action-btn--edit"
              onClick={(e) => {
                e.stopPropagation();
                onEdit(runbook.id);
              }}
              aria-label="Edit runbook"
            >
              ✏️
            </button>
          )}
          {onExecute && canExecute && (
            <button
              type="button"
              className="action-btn action-btn--execute"
              onClick={(e) => {
                e.stopPropagation();
                onExecute(runbook.id);
              }}
            >
              ▶️ Execute
            </button>
          )}
          {isRunning && (
            <span className="running-indicator">
              <span className="running-dot" />
              Running...
            </span>
          )}
        </div>
      </div>

      <style>{`
        .runbook-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .runbook-card:hover {
          border-color: #D1D5DB;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
        }

        .runbook-card--compact {
          padding: 0.75rem;
        }

        .runbook-card--running {
          border-color: #3B82F6;
          box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2);
        }

        .runbook-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 0.5rem;
        }

        .runbook-title-row {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .runbook-icon {
          font-size: 1.25rem;
        }

        .runbook-name {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
        }

        .runbook-badges {
          display: flex;
          gap: 0.375rem;
        }

        .status-badge {
          font-size: 0.6875rem;
          font-weight: 600;
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
        }

        .exec-status-badge {
          font-size: 0.6875rem;
          font-weight: 500;
        }

        .runbook-description {
          margin: 0 0 0.75rem 0;
          font-size: 0.8125rem;
          color: #6B7280;
          line-height: 1.4;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .runbook-progress {
          margin-bottom: 0.75rem;
          padding: 0.75rem;
          background: #EFF6FF;
          border-radius: 8px;
        }

        .step-progress {
          display: flex;
          align-items: center;
          margin-bottom: 0.5rem;
        }

        .step-item {
          display: flex;
          align-items: center;
        }

        .step-indicator {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          background: #E5E7EB;
          color: #6B7280;
          font-size: 0.75rem;
          font-weight: 600;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .step-item--completed .step-indicator {
          background: #10B981;
          color: #fff;
        }

        .step-item--current .step-indicator {
          background: #3B82F6;
          color: #fff;
          animation: pulse 1.5s infinite;
        }

        .step-item--failed .step-indicator {
          background: #EF4444;
          color: #fff;
        }

        @keyframes pulse {
          0%, 100% { transform: scale(1); }
          50% { transform: scale(1.1); }
        }

        .step-connector {
          width: 20px;
          height: 2px;
          background: #E5E7EB;
        }

        .step-connector--completed {
          background: #10B981;
        }

        .progress-label {
          font-size: 0.75rem;
          color: #3B82F6;
          font-weight: 500;
        }

        .runbook-stats {
          display: flex;
          gap: 1rem;
          margin-bottom: 0.75rem;
        }

        .stat-item {
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }

        .stat-icon {
          font-size: 0.75rem;
        }

        .stat-value {
          font-size: 0.8125rem;
          font-weight: 600;
          color: #111827;
        }

        .stat-label {
          font-size: 0.75rem;
          color: #9CA3AF;
        }

        .step-types {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
          margin-bottom: 0.75rem;
        }

        .step-type-badge {
          font-size: 0.6875rem;
          color: #6B7280;
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border-radius: 4px;
        }

        .runbook-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
          margin-bottom: 0.75rem;
        }

        .tag {
          font-size: 0.6875rem;
          color: #4B5563;
          padding: 0.125rem 0.375rem;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 4px;
        }

        .runbook-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .runbook-meta {
          display: flex;
          flex-direction: column;
          gap: 0.125rem;
        }

        .meta-item {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        .runbook-actions {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .action-btn {
          font-size: 0.75rem;
          font-weight: 500;
          padding: 0.375rem 0.625rem;
          border-radius: 6px;
          border: none;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .action-btn--edit {
          background: #F3F4F6;
          color: #374151;
        }

        .action-btn--edit:hover {
          background: #E5E7EB;
        }

        .action-btn--execute {
          background: #10B981;
          color: #fff;
        }

        .action-btn--execute:hover {
          background: #059669;
        }

        .running-indicator {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          font-size: 0.75rem;
          color: #3B82F6;
        }

        .running-dot {
          width: 6px;
          height: 6px;
          background: #3B82F6;
          border-radius: 50%;
          animation: pulse 1s infinite;
        }

        @media (prefers-color-scheme: dark) {
          .runbook-card {
            background: #1F2937;
            border-color: #374151;
          }

          .runbook-card:hover {
            border-color: #4B5563;
          }

          .runbook-card--running {
            border-color: #60A5FA;
          }

          .runbook-name {
            color: #F9FAFB;
          }

          .runbook-description {
            color: #9CA3AF;
          }

          .runbook-progress {
            background: rgba(59, 130, 246, 0.1);
          }

          .stat-value {
            color: #F9FAFB;
          }

          .step-type-badge {
            background: #374151;
            color: #9CA3AF;
          }

          .tag {
            background: #111827;
            border-color: #374151;
            color: #D1D5DB;
          }

          .runbook-footer {
            border-top-color: #374151;
          }

          .action-btn--edit {
            background: #374151;
            color: #D1D5DB;
          }

          .action-btn--edit:hover {
            background: #4B5563;
          }
        }
      `}</style>
    </div>
  );
};

RunbookCard.displayName = 'RunbookCard';

export default RunbookCard;
