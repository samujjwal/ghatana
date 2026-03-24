/**
 * DeploymentCard Component
 *
 * @description Card component for displaying deployment status, pipeline progress,
 * and environment information with rollback capabilities.
 *
 * @doc.phase 3
 * @doc.component DeploymentCard
 */

import React, { useCallback, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export type DeploymentStatus =
  | 'pending'
  | 'building'
  | 'testing'
  | 'deploying'
  | 'success'
  | 'failed'
  | 'rolled_back'
  | 'cancelled';

export type Environment = 'development' | 'staging' | 'production';

export interface PipelineStage {
  id: string;
  name: string;
  status: 'pending' | 'running' | 'success' | 'failed' | 'skipped';
  startedAt?: string;
  completedAt?: string;
  duration?: number;
  logs?: string;
}

export interface DeploymentChange {
  id: string;
  type: 'commit' | 'pr' | 'hotfix';
  title: string;
  author: string;
  sha?: string;
}

export interface Deployment {
  id: string;
  version: string;
  environment: Environment;
  status: DeploymentStatus;
  triggeredBy: {
    id: string;
    name: string;
    avatar?: string;
  };
  triggeredAt: string;
  completedAt?: string;
  duration?: number;
  pipeline: PipelineStage[];
  changes: DeploymentChange[];
  commitSha: string;
  branch: string;
  canRollback?: boolean;
  previousVersion?: string;
}

export interface DeploymentCardProps {
  deployment: Deployment;
  onClick?: (deployment: Deployment) => void;
  onRollback?: (deployment: Deployment) => void;
  onRetry?: (deployment: Deployment) => void;
  onCancel?: (deployment: Deployment) => void;
  onViewLogs?: (deployment: Deployment, stage: PipelineStage) => void;
  compact?: boolean;
}

// ============================================================================
// Constants
// ============================================================================

const STATUS_CONFIG: Record<
  DeploymentStatus,
  { label: string; color: string; bg: string; icon: string }
> = {
  pending: { label: 'Pending', color: '#6B7280', bg: '#F3F4F6', icon: '⏳' },
  building: { label: 'Building', color: '#3B82F6', bg: '#EFF6FF', icon: '🔨' },
  testing: { label: 'Testing', color: '#8B5CF6', bg: '#EDE9FE', icon: '🧪' },
  deploying: { label: 'Deploying', color: '#F59E0B', bg: '#FEF3C7', icon: '🚀' },
  success: { label: 'Success', color: '#10B981', bg: '#D1FAE5', icon: '✓' },
  failed: { label: 'Failed', color: '#EF4444', bg: '#FEE2E2', icon: '✕' },
  rolled_back: { label: 'Rolled Back', color: '#9333EA', bg: '#F3E8FF', icon: '↩' },
  cancelled: { label: 'Cancelled', color: '#9CA3AF', bg: '#F3F4F6', icon: '⊘' },
};

const ENV_CONFIG: Record<Environment, { label: string; color: string; bg: string }> = {
  development: { label: 'Development', color: '#6B7280', bg: '#F3F4F6' },
  staging: { label: 'Staging', color: '#F59E0B', bg: '#FEF3C7' },
  production: { label: 'Production', color: '#10B981', bg: '#D1FAE5' },
};

const STAGE_STATUS_ICONS: Record<PipelineStage['status'], string> = {
  pending: '○',
  running: '◐',
  success: '●',
  failed: '✕',
  skipped: '⊘',
};

// ============================================================================
// Utility Functions
// ============================================================================

const formatDuration = (ms: number): string => {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) return `${hours}h ${minutes % 60}m`;
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
  return `${seconds}s`;
};

const formatTimeAgo = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
};

// ============================================================================
// Main Component
// ============================================================================

export const DeploymentCard: React.FC<DeploymentCardProps> = ({
  deployment,
  onClick,
  onRollback,
  onRetry,
  onCancel,
  onViewLogs,
  compact = false,
}) => {
  const {
    version,
    environment,
    status,
    triggeredBy,
    triggeredAt,
    completedAt,
    duration,
    pipeline,
    changes,
    commitSha,
    branch,
    canRollback,
    previousVersion,
  } = deployment;

  const statusConfig = useMemo(() => STATUS_CONFIG[status], [status]);
  const envConfig = useMemo(() => ENV_CONFIG[environment], [environment]);

  const pipelineProgress = useMemo(() => {
    const completed = pipeline.filter(
      (s) => s.status === 'success' || s.status === 'failed' || s.status === 'skipped'
    ).length;
    return Math.round((completed / pipeline.length) * 100);
  }, [pipeline]);

  const isInProgress = useMemo(
    () => ['pending', 'building', 'testing', 'deploying'].includes(status),
    [status]
  );

  const handleClick = useCallback(() => {
    if (onClick) onClick(deployment);
  }, [onClick, deployment]);

  const handleRollback = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (onRollback) onRollback(deployment);
    },
    [onRollback, deployment]
  );

  const handleRetry = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (onRetry) onRetry(deployment);
    },
    [onRetry, deployment]
  );

  const handleCancel = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (onCancel) onCancel(deployment);
    },
    [onCancel, deployment]
  );

  const handleViewLogs = useCallback(
    (stage: PipelineStage) => {
      if (onViewLogs) onViewLogs(deployment, stage);
    },
    [onViewLogs, deployment]
  );

  return (
    <article
      className={`deployment-card ${compact ? 'deployment-card--compact' : ''}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      aria-label={`Deployment ${version} to ${environment}`}
    >
      {/* Header */}
      <header className="card-header">
        <div className="version-info">
          <span className="version">{version}</span>
          <span
            className="environment"
            style={{ color: envConfig.color, background: envConfig.bg }}
          >
            {envConfig.label}
          </span>
        </div>
        <span
          className="status"
          style={{ color: statusConfig.color, background: statusConfig.bg }}
        >
          {statusConfig.icon} {statusConfig.label}
        </span>
      </header>

      {/* Branch & Commit */}
      <div className="git-info">
        <span className="branch" title="Branch">
          🌿 {branch}
        </span>
        <code className="commit" title="Commit SHA">
          {commitSha.substring(0, 7)}
        </code>
      </div>

      {/* Pipeline Progress */}
      {!compact && (
        <div className="pipeline-section">
          <div className="pipeline-header">
            <span className="pipeline-label">Pipeline</span>
            <span className="pipeline-progress">{pipelineProgress}%</span>
          </div>
          <div className="pipeline-bar">
            <div
              className="pipeline-fill"
              style={{
                width: `${pipelineProgress}%`,
                background: status === 'failed' ? '#EF4444' : '#10B981',
              }}
            />
          </div>
          <div className="pipeline-stages">
            {pipeline.map((stage) => (
              <button
                key={stage.id}
                type="button"
                className={`stage stage--${stage.status}`}
                onClick={(e) => {
                  e.stopPropagation();
                  handleViewLogs(stage);
                }}
                title={`${stage.name}: ${stage.status}${
                  stage.duration ? ` (${formatDuration(stage.duration)})` : ''
                }`}
              >
                <span className="stage-icon">{STAGE_STATUS_ICONS[stage.status]}</span>
                <span className="stage-name">{stage.name}</span>
                {stage.duration && (
                  <span className="stage-duration">{formatDuration(stage.duration)}</span>
                )}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Changes Summary */}
      {!compact && changes.length > 0 && (
        <div className="changes-section">
          <span className="changes-label">
            {changes.length} change{changes.length > 1 ? 's' : ''}
          </span>
          <div className="changes-list">
            {changes.slice(0, 3).map((change) => (
              <div key={change.id} className="change">
                <span className="change-icon">
                  {change.type === 'commit' ? '📝' : change.type === 'pr' ? '🔀' : '🔥'}
                </span>
                <span className="change-title">{change.title}</span>
              </div>
            ))}
            {changes.length > 3 && (
              <span className="changes-more">+{changes.length - 3} more</span>
            )}
          </div>
        </div>
      )}

      {/* Footer */}
      <footer className="card-footer">
        <div className="trigger-info">
          {triggeredBy.avatar ? (
            <img
              src={triggeredBy.avatar}
              alt={triggeredBy.name}
              className="trigger-avatar"
            />
          ) : (
            <span className="trigger-avatar trigger-avatar--fallback">
              {triggeredBy.name.charAt(0).toUpperCase()}
            </span>
          )}
          <span className="trigger-name">{triggeredBy.name}</span>
        </div>
        <div className="timing-info">
          <span className="trigger-time">{formatTimeAgo(triggeredAt)}</span>
          {duration && !isInProgress && (
            <span className="duration">⏱ {formatDuration(duration)}</span>
          )}
        </div>
      </footer>

      {/* Actions */}
      <div className="card-actions">
        {isInProgress && (
          <button
            type="button"
            className="action-btn action-btn--cancel"
            onClick={handleCancel}
            title="Cancel Deployment"
          >
            ✕ Cancel
          </button>
        )}
        {status === 'failed' && (
          <button
            type="button"
            className="action-btn action-btn--retry"
            onClick={handleRetry}
            title="Retry Deployment"
          >
            ↻ Retry
          </button>
        )}
        {canRollback && status === 'success' && (
          <button
            type="button"
            className="action-btn action-btn--rollback"
            onClick={handleRollback}
            title={`Rollback to ${previousVersion}`}
          >
            ↩ Rollback
          </button>
        )}
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .deployment-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
          cursor: pointer;
          transition: all 0.15s ease;
          position: relative;
        }

        .deployment-card:hover {
          border-color: #3B82F6;
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
        }

        .deployment-card--compact {
          padding: 0.75rem;
        }

        .card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.75rem;
        }

        .version-info {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .version {
          font-size: 1rem;
          font-weight: 700;
          color: #111827;
        }

        .environment {
          padding: 0.125rem 0.5rem;
          border-radius: 4px;
          font-size: 0.6875rem;
          font-weight: 600;
        }

        .status {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          padding: 0.25rem 0.625rem;
          border-radius: 6px;
          font-size: 0.75rem;
          font-weight: 600;
        }

        .git-info {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          margin-bottom: 0.75rem;
          font-size: 0.75rem;
        }

        .branch {
          color: #374151;
        }

        .commit {
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border-radius: 4px;
          font-family: 'Monaco', 'Consolas', monospace;
          color: #6B7280;
        }

        .pipeline-section {
          margin-bottom: 0.75rem;
        }

        .pipeline-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.375rem;
        }

        .pipeline-label {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #374151;
          text-transform: uppercase;
        }

        .pipeline-progress {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #6B7280;
        }

        .pipeline-bar {
          height: 4px;
          background: #E5E7EB;
          border-radius: 2px;
          overflow: hidden;
          margin-bottom: 0.5rem;
        }

        .pipeline-fill {
          height: 100%;
          transition: width 0.3s ease;
        }

        .pipeline-stages {
          display: flex;
          gap: 0.5rem;
        }

        .stage {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          padding: 0.25rem 0.5rem;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 4px;
          font-size: 0.6875rem;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .stage:hover {
          background: #F3F4F6;
        }

        .stage--pending { color: #9CA3AF; }
        .stage--running { color: #3B82F6; }
        .stage--success { color: #10B981; }
        .stage--failed { color: #EF4444; }
        .stage--skipped { color: #9CA3AF; }

        .stage--running {
          animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }

        .stage-icon {
          font-size: 0.625rem;
        }

        .stage-name {
          font-weight: 500;
        }

        .stage-duration {
          color: #9CA3AF;
        }

        .changes-section {
          margin-bottom: 0.75rem;
          padding: 0.5rem;
          background: #F9FAFB;
          border-radius: 8px;
        }

        .changes-label {
          display: block;
          margin-bottom: 0.375rem;
          font-size: 0.6875rem;
          font-weight: 600;
          color: #6B7280;
        }

        .changes-list {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
        }

        .change {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          font-size: 0.75rem;
          color: #374151;
        }

        .change-icon {
          font-size: 0.625rem;
        }

        .change-title {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .changes-more {
          font-size: 0.6875rem;
          color: #6B7280;
          margin-left: 1rem;
        }

        .card-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .trigger-info {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .trigger-avatar {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          object-fit: cover;
        }

        .trigger-avatar--fallback {
          display: flex;
          align-items: center;
          justify-content: center;
          background: #E5E7EB;
          color: #374151;
          font-size: 0.625rem;
          font-weight: 600;
        }

        .trigger-name {
          font-size: 0.75rem;
          color: #374151;
        }

        .timing-info {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .trigger-time {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        .duration {
          font-size: 0.6875rem;
          color: #6B7280;
        }

        .card-actions {
          display: none;
          position: absolute;
          top: 0.75rem;
          right: 0.75rem;
          gap: 0.375rem;
        }

        .deployment-card:hover .card-actions {
          display: flex;
        }

        .action-btn {
          padding: 0.375rem 0.625rem;
          font-size: 0.6875rem;
          font-weight: 600;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .action-btn--cancel {
          background: #FEE2E2;
          color: #DC2626;
        }

        .action-btn--cancel:hover {
          background: #FECACA;
        }

        .action-btn--retry {
          background: #DBEAFE;
          color: #1D4ED8;
        }

        .action-btn--retry:hover {
          background: #BFDBFE;
        }

        .action-btn--rollback {
          background: #FEF3C7;
          color: #D97706;
        }

        .action-btn--rollback:hover {
          background: #FDE68A;
        }
      `}</style>
    </article>
  );
};

DeploymentCard.displayName = 'DeploymentCard';

export default DeploymentCard;
