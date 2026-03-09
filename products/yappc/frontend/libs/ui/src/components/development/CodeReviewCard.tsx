/**
 * CodeReviewCard Component
 *
 * @description Card component for displaying pull request review information
 * with CI status, AI analysis score, and reviewer details.
 *
 * @doc.phase 3
 * @doc.component CodeReviewCard
 */

import React, { useCallback, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export type PRStatus = 'open' | 'merged' | 'closed' | 'draft';
export type CIStatus = 'pending' | 'running' | 'success' | 'failure' | 'cancelled';
export type ReviewDecision = 'approved' | 'changes_requested' | 'commented' | 'pending';

export interface PRReviewer {
  id: string;
  name: string;
  avatar?: string;
  decision: ReviewDecision;
  reviewedAt?: string;
}

export interface PRLabel {
  id: string;
  name: string;
  color: string;
}

export interface AIAnalysis {
  score: number; // 0-100
  riskLevel: 'low' | 'medium' | 'high';
  suggestions: number;
  securityIssues: number;
  performanceIssues: number;
}

export interface PullRequest {
  id: string;
  number: number;
  title: string;
  description?: string;
  author: {
    id: string;
    name: string;
    avatar?: string;
  };
  status: PRStatus;
  sourceBranch: string;
  targetBranch: string;
  ciStatus: CIStatus;
  reviewers: PRReviewer[];
  labels: PRLabel[];
  linesAdded: number;
  linesDeleted: number;
  filesChanged: number;
  comments: number;
  createdAt: string;
  updatedAt: string;
  aiAnalysis?: AIAnalysis;
  storyId?: string;
  storyTitle?: string;
}

export interface CodeReviewCardProps {
  pullRequest: PullRequest;
  onClick?: (pr: PullRequest) => void;
  onApprove?: (pr: PullRequest) => void;
  onRequestChanges?: (pr: PullRequest) => void;
  selected?: boolean;
}

// ============================================================================
// Utility Functions
// ============================================================================

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

const getStatusConfig = (status: PRStatus) => {
  const configs = {
    open: { label: 'Open', color: '#10B981', bg: '#D1FAE5' },
    merged: { label: 'Merged', color: '#8B5CF6', bg: '#EDE9FE' },
    closed: { label: 'Closed', color: '#EF4444', bg: '#FEE2E2' },
    draft: { label: 'Draft', color: '#6B7280', bg: '#F3F4F6' },
  };
  return configs[status];
};

const getCIStatusConfig = (status: CIStatus) => {
  const configs = {
    pending: { icon: '⏳', label: 'Pending', color: '#6B7280' },
    running: { icon: '🔄', label: 'Running', color: '#F59E0B' },
    success: { icon: '✓', label: 'Passed', color: '#10B981' },
    failure: { icon: '✕', label: 'Failed', color: '#EF4444' },
    cancelled: { icon: '⊘', label: 'Cancelled', color: '#9CA3AF' },
  };
  return configs[status];
};

const getDecisionConfig = (decision: ReviewDecision) => {
  const configs = {
    approved: { icon: '✓', color: '#10B981' },
    changes_requested: { icon: '⟳', color: '#F59E0B' },
    commented: { icon: '💬', color: '#3B82F6' },
    pending: { icon: '○', color: '#9CA3AF' },
  };
  return configs[decision];
};

// ============================================================================
// Main Component
// ============================================================================

export const CodeReviewCard: React.FC<CodeReviewCardProps> = ({
  pullRequest,
  onClick,
  onApprove,
  onRequestChanges,
  selected = false,
}) => {
  const {
    number,
    title,
    author,
    status,
    sourceBranch,
    targetBranch,
    ciStatus,
    reviewers,
    labels,
    linesAdded,
    linesDeleted,
    filesChanged,
    comments,
    createdAt,
    updatedAt,
    aiAnalysis,
    storyTitle,
  } = pullRequest;

  const statusConfig = useMemo(() => getStatusConfig(status), [status]);
  const ciConfig = useMemo(() => getCIStatusConfig(ciStatus), [ciStatus]);

  const reviewSummary = useMemo(() => {
    const approved = reviewers.filter((r) => r.decision === 'approved').length;
    const requested = reviewers.filter(
      (r) => r.decision === 'changes_requested'
    ).length;
    return { approved, requested, total: reviewers.length };
  }, [reviewers]);

  const handleClick = useCallback(() => {
    if (onClick) onClick(pullRequest);
  }, [onClick, pullRequest]);

  const handleApprove = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (onApprove) onApprove(pullRequest);
    },
    [onApprove, pullRequest]
  );

  const handleRequestChanges = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (onRequestChanges) onRequestChanges(pullRequest);
    },
    [onRequestChanges, pullRequest]
  );

  return (
    <article
      className={`code-review-card ${selected ? 'code-review-card--selected' : ''}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      aria-label={`Pull request #${number}: ${title}`}
    >
      {/* Header */}
      <header className="card-header">
        <div className="pr-info">
          <span
            className="pr-status"
            style={{ color: statusConfig.color, background: statusConfig.bg }}
          >
            {statusConfig.label}
          </span>
          <span className="pr-number">#{number}</span>
        </div>
        <span
          className="ci-status"
          style={{ color: ciConfig.color }}
          title={`CI: ${ciConfig.label}`}
        >
          {ciConfig.icon} {ciConfig.label}
        </span>
      </header>

      {/* Title */}
      <h3 className="card-title">{title}</h3>

      {/* Branch Info */}
      <div className="branch-info">
        <span className="branch">{sourceBranch}</span>
        <span className="branch-arrow">→</span>
        <span className="branch">{targetBranch}</span>
      </div>

      {/* Story Link */}
      {storyTitle && (
        <div className="story-link">
          <span className="story-icon">📋</span>
          <span className="story-title">{storyTitle}</span>
        </div>
      )}

      {/* Labels */}
      {labels.length > 0 && (
        <div className="labels">
          {labels.map((label) => (
            <span
              key={label.id}
              className="label"
              style={{ background: label.color + '20', color: label.color }}
            >
              {label.name}
            </span>
          ))}
        </div>
      )}

      {/* Stats Row */}
      <div className="stats-row">
        <span className="stat" title="Lines added">
          <span className="stat-value stat-value--added">+{linesAdded}</span>
        </span>
        <span className="stat" title="Lines deleted">
          <span className="stat-value stat-value--deleted">-{linesDeleted}</span>
        </span>
        <span className="stat" title="Files changed">
          📄 {filesChanged}
        </span>
        <span className="stat" title="Comments">
          💬 {comments}
        </span>
      </div>

      {/* AI Analysis */}
      {aiAnalysis && (
        <div className="ai-analysis">
          <div className="ai-header">
            <span className="ai-label">🤖 AI Analysis</span>
            <span
              className={`ai-score ai-score--${aiAnalysis.riskLevel}`}
              title={`Risk: ${aiAnalysis.riskLevel}`}
            >
              {aiAnalysis.score}/100
            </span>
          </div>
          <div className="ai-details">
            {aiAnalysis.suggestions > 0 && (
              <span className="ai-detail">💡 {aiAnalysis.suggestions}</span>
            )}
            {aiAnalysis.securityIssues > 0 && (
              <span className="ai-detail ai-detail--security">
                🔒 {aiAnalysis.securityIssues}
              </span>
            )}
            {aiAnalysis.performanceIssues > 0 && (
              <span className="ai-detail ai-detail--perf">
                ⚡ {aiAnalysis.performanceIssues}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Reviewers */}
      <div className="reviewers-section">
        <div className="reviewers-header">
          <span className="reviewers-label">Reviewers</span>
          <span className="review-summary">
            {reviewSummary.approved}/{reviewSummary.total} approved
          </span>
        </div>
        <div className="reviewers-list">
          {reviewers.map((reviewer) => {
            const decisionConfig = getDecisionConfig(reviewer.decision);
            return (
              <div
                key={reviewer.id}
                className="reviewer"
                title={`${reviewer.name}: ${reviewer.decision.replace('_', ' ')}`}
              >
                {reviewer.avatar ? (
                  <img
                    src={reviewer.avatar}
                    alt={reviewer.name}
                    className="reviewer-avatar"
                  />
                ) : (
                  <span className="reviewer-avatar reviewer-avatar--fallback">
                    {reviewer.name.charAt(0).toUpperCase()}
                  </span>
                )}
                <span
                  className="reviewer-decision"
                  style={{
                    background: decisionConfig.color,
                    color: '#fff',
                  }}
                >
                  {decisionConfig.icon}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Footer */}
      <footer className="card-footer">
        <div className="author-info">
          {author.avatar ? (
            <img
              src={author.avatar}
              alt={author.name}
              className="author-avatar"
            />
          ) : (
            <span className="author-avatar author-avatar--fallback">
              {author.name.charAt(0).toUpperCase()}
            </span>
          )}
          <span className="author-name">{author.name}</span>
        </div>
        <span className="timestamps" title={`Updated ${formatTimeAgo(updatedAt)}`}>
          {formatTimeAgo(createdAt)}
        </span>
      </footer>

      {/* Quick Actions */}
      {status === 'open' && (
        <div className="quick-actions">
          <button
            type="button"
            className="action-btn action-btn--approve"
            onClick={handleApprove}
            title="Approve"
          >
            ✓ Approve
          </button>
          <button
            type="button"
            className="action-btn action-btn--request"
            onClick={handleRequestChanges}
            title="Request Changes"
          >
            ⟳ Request
          </button>
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .code-review-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
          cursor: pointer;
          transition: all 0.15s ease;
          position: relative;
        }

        .code-review-card:hover {
          border-color: #3B82F6;
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
        }

        .code-review-card--selected {
          border-color: #3B82F6;
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }

        .card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.75rem;
        }

        .pr-info {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .pr-status {
          padding: 0.125rem 0.5rem;
          border-radius: 4px;
          font-size: 0.6875rem;
          font-weight: 600;
          text-transform: uppercase;
        }

        .pr-number {
          font-size: 0.75rem;
          font-weight: 600;
          color: #6B7280;
        }

        .ci-status {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          font-size: 0.75rem;
          font-weight: 500;
        }

        .card-title {
          margin: 0 0 0.5rem;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
          line-height: 1.4;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .branch-info {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-bottom: 0.5rem;
          font-family: 'Monaco', 'Consolas', monospace;
          font-size: 0.6875rem;
        }

        .branch {
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border-radius: 4px;
          color: #374151;
          max-width: 120px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .branch-arrow {
          color: #9CA3AF;
        }

        .story-link {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-bottom: 0.5rem;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .story-icon {
          font-size: 0.625rem;
        }

        .story-title {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .labels {
          display: flex;
          flex-wrap: wrap;
          gap: 0.25rem;
          margin-bottom: 0.75rem;
        }

        .label {
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
          font-size: 0.625rem;
          font-weight: 500;
        }

        .stats-row {
          display: flex;
          gap: 0.75rem;
          margin-bottom: 0.75rem;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .stat-value--added {
          color: #10B981;
        }

        .stat-value--deleted {
          color: #EF4444;
        }

        .ai-analysis {
          padding: 0.5rem;
          background: #F9FAFB;
          border-radius: 8px;
          margin-bottom: 0.75rem;
        }

        .ai-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.375rem;
        }

        .ai-label {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #374151;
        }

        .ai-score {
          font-size: 0.75rem;
          font-weight: 700;
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
        }

        .ai-score--low { background: #D1FAE5; color: #059669; }
        .ai-score--medium { background: #FEF3C7; color: #D97706; }
        .ai-score--high { background: #FEE2E2; color: #DC2626; }

        .ai-details {
          display: flex;
          gap: 0.5rem;
        }

        .ai-detail {
          font-size: 0.6875rem;
          color: #6B7280;
        }

        .ai-detail--security { color: #DC2626; }
        .ai-detail--perf { color: #D97706; }

        .reviewers-section {
          margin-bottom: 0.75rem;
        }

        .reviewers-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.5rem;
        }

        .reviewers-label {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #374151;
        }

        .review-summary {
          font-size: 0.6875rem;
          color: #6B7280;
        }

        .reviewers-list {
          display: flex;
          gap: 0.5rem;
        }

        .reviewer {
          position: relative;
        }

        .reviewer-avatar {
          width: 28px;
          height: 28px;
          border-radius: 50%;
          object-fit: cover;
        }

        .reviewer-avatar--fallback {
          display: flex;
          align-items: center;
          justify-content: center;
          background: #E5E7EB;
          color: #374151;
          font-size: 0.75rem;
          font-weight: 600;
        }

        .reviewer-decision {
          position: absolute;
          bottom: -2px;
          right: -2px;
          width: 14px;
          height: 14px;
          border-radius: 50%;
          font-size: 0.5rem;
          display: flex;
          align-items: center;
          justify-content: center;
          border: 2px solid #fff;
        }

        .card-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .author-info {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .author-avatar {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          object-fit: cover;
        }

        .author-avatar--fallback {
          display: flex;
          align-items: center;
          justify-content: center;
          background: #E5E7EB;
          color: #374151;
          font-size: 0.625rem;
          font-weight: 600;
        }

        .author-name {
          font-size: 0.75rem;
          color: #374151;
        }

        .timestamps {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        .quick-actions {
          display: none;
          position: absolute;
          bottom: 0.75rem;
          right: 0.75rem;
          gap: 0.375rem;
        }

        .code-review-card:hover .quick-actions {
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

        .action-btn--approve {
          background: #10B981;
          color: #fff;
        }

        .action-btn--approve:hover {
          background: #059669;
        }

        .action-btn--request {
          background: #F59E0B;
          color: #fff;
        }

        .action-btn--request:hover {
          background: #D97706;
        }
      `}</style>
    </article>
  );
};

CodeReviewCard.displayName = 'CodeReviewCard';

export default CodeReviewCard;
