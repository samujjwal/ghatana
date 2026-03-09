/**
 * CodeReviewDetailPage
 *
 * @description PR detail view with unified/split diff viewer, inline comments,
 * review actions, CI status, and approval workflow.
 *
 * @doc.phase 3
 * @doc.route /projects/:projectId/reviews/:prId
 */

import React, { useCallback, useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Spinner as LoadingSpinner } from '@ghatana/ui';
import { ErrorBoundary } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

type ReviewStatus = 'open' | 'approved' | 'changes_requested' | 'merged' | 'closed';
type CIStatus = 'pending' | 'running' | 'passed' | 'failed' | 'cancelled';
type ReviewAction = 'approve' | 'request_changes' | 'comment';

interface User {
  id: string;
  name: string;
  avatar?: string;
}

interface FileChange {
  id: string;
  path: string;
  status: 'added' | 'modified' | 'deleted' | 'renamed';
  additions: number;
  deletions: number;
  hunks: DiffHunk[];
}

interface DiffHunk {
  id: string;
  oldStart: number;
  oldLines: number;
  newStart: number;
  newLines: number;
  lines: DiffLine[];
}

interface DiffLine {
  type: 'context' | 'addition' | 'deletion';
  oldLineNum?: number;
  newLineNum?: number;
  content: string;
}

interface InlineComment {
  id: string;
  author: User;
  content: string;
  createdAt: string;
  resolved: boolean;
  lineNumber: number;
  filePath: string;
}

interface Review {
  id: string;
  author: User;
  status: 'approved' | 'changes_requested' | 'commented';
  body?: string;
  createdAt: string;
}

interface CICheck {
  id: string;
  name: string;
  status: CIStatus;
  url?: string;
  duration?: number;
}

interface PullRequest {
  id: string;
  number: number;
  title: string;
  description: string;
  status: ReviewStatus;
  author: User;
  sourceBranch: string;
  targetBranch: string;
  createdAt: string;
  updatedAt: string;
  mergedAt?: string;
  reviewers: User[];
  reviews: Review[];
  ciChecks: CICheck[];
  files: FileChange[];
  inlineComments: InlineComment[];
  commentsCount: number;
  additions: number;
  deletions: number;
  linkedStories: Array<{ id: string; title: string }>;
}

// ============================================================================
// API Functions
// ============================================================================

const fetchPullRequest = async (projectId: string, prId: string): Promise<PullRequest> => {
  const response = await fetch(`/api/projects/${projectId}/reviews/${prId}`);
  if (!response.ok) throw new Error('Failed to fetch pull request');
  return response.json();
};

const submitReview = async (
  projectId: string,
  prId: string,
  action: ReviewAction,
  body?: string
): Promise<Review> => {
  const response = await fetch(`/api/projects/${projectId}/reviews/${prId}/reviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, body }),
  });
  if (!response.ok) throw new Error('Failed to submit review');
  return response.json();
};

const addInlineComment = async (
  projectId: string,
  prId: string,
  filePath: string,
  lineNumber: number,
  content: string
): Promise<InlineComment> => {
  const response = await fetch(
    `/api/projects/${projectId}/reviews/${prId}/comments`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath, lineNumber, content }),
    }
  );
  if (!response.ok) throw new Error('Failed to add comment');
  return response.json();
};

const resolveComment = async (
  projectId: string,
  prId: string,
  commentId: string
): Promise<void> => {
  const response = await fetch(
    `/api/projects/${projectId}/reviews/${prId}/comments/${commentId}/resolve`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error('Failed to resolve comment');
};

const mergePullRequest = async (
  projectId: string,
  prId: string,
  method: 'merge' | 'squash' | 'rebase'
): Promise<void> => {
  const response = await fetch(`/api/projects/${projectId}/reviews/${prId}/merge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ method }),
  });
  if (!response.ok) throw new Error('Failed to merge pull request');
};

// ============================================================================
// Utility Functions
// ============================================================================

const getStatusConfig = (status: ReviewStatus) => {
  const configs: Record<ReviewStatus, { icon: string; color: string; bg: string; label: string }> = {
    open: { icon: '🔵', color: '#3B82F6', bg: '#EFF6FF', label: 'Open' },
    approved: { icon: '✅', color: '#10B981', bg: '#D1FAE5', label: 'Approved' },
    changes_requested: { icon: '🔄', color: '#F59E0B', bg: '#FEF3C7', label: 'Changes Requested' },
    merged: { icon: '🟣', color: '#8B5CF6', bg: '#EDE9FE', label: 'Merged' },
    closed: { icon: '⚫', color: '#6B7280', bg: '#F3F4F6', label: 'Closed' },
  };
  return configs[status];
};

const getCIStatusConfig = (status: CIStatus) => {
  const configs: Record<CIStatus, { icon: string; color: string }> = {
    pending: { icon: '⏳', color: '#9CA3AF' },
    running: { icon: '🔄', color: '#3B82F6' },
    passed: { icon: '✅', color: '#10B981' },
    failed: { icon: '❌', color: '#EF4444' },
    cancelled: { icon: '⚪', color: '#6B7280' },
  };
  return configs[status];
};

const getFileStatusConfig = (status: FileChange['status']) => {
  const configs: Record<FileChange['status'], { icon: string; color: string }> = {
    added: { icon: '+', color: '#10B981' },
    modified: { icon: '●', color: '#F59E0B' },
    deleted: { icon: '−', color: '#EF4444' },
    renamed: { icon: '→', color: '#3B82F6' },
  };
  return configs[status];
};

const formatTimeAgo = (dateStr: string): string => {
  const date = new Date(dateStr);
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
// Sub-Components
// ============================================================================

interface DiffViewerProps {
  files: FileChange[];
  comments: InlineComment[];
  selectedFile: string | null;
  onSelectFile: (path: string) => void;
  onAddComment: (filePath: string, lineNumber: number, content: string) => Promise<void>;
  viewMode: 'unified' | 'split';
}

const DiffViewer: React.FC<DiffViewerProps> = ({
  files,
  comments,
  selectedFile,
  onSelectFile,
  onAddComment,
  viewMode,
}) => {
  const [commentingLine, setCommentingLine] = useState<{
    filePath: string;
    lineNumber: number;
  } | null>(null);
  const [commentText, setCommentText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const activeFile = files.find((f) => f.path === selectedFile) || files[0];

  const handleSubmitComment = async () => {
    if (!commentingLine || !commentText.trim()) return;
    
    setIsSubmitting(true);
    try {
      await onAddComment(commentingLine.filePath, commentingLine.lineNumber, commentText);
      setCommentText('');
      setCommentingLine(null);
    } catch (err) {
      console.error('Failed to add comment:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const fileComments = comments.filter((c) => c.filePath === activeFile?.path);

  return (
    <div className="diff-viewer">
      {/* File List Sidebar */}
      <div className="file-list">
        <h4 className="file-list-title">Files Changed ({files.length})</h4>
        <ul className="file-items">
          {files.map((file) => {
            const statusConfig = getFileStatusConfig(file.status);
            const fileCommentsCount = comments.filter((c) => c.filePath === file.path).length;
            return (
              <li key={file.id}>
                <button
                  type="button"
                  className={`file-item ${file.path === selectedFile ? 'file-item--active' : ''}`}
                  onClick={() => onSelectFile(file.path)}
                >
                  <span className="file-status" style={{ color: statusConfig.color }}>
                    {statusConfig.icon}
                  </span>
                  <span className="file-name" title={file.path}>
                    {file.path.split('/').pop()}
                  </span>
                  <span className="file-stats">
                    <span className="stat-add">+{file.additions}</span>
                    <span className="stat-del">-{file.deletions}</span>
                  </span>
                  {fileCommentsCount > 0 && (
                    <span className="file-comments">{fileCommentsCount}</span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Diff Content */}
      <div className="diff-content">
        {activeFile && (
          <>
            <div className="diff-header">
              <span className="diff-path">{activeFile.path}</span>
              <span className="diff-stats">
                <span className="diff-add">+{activeFile.additions}</span>
                <span className="diff-del">-{activeFile.deletions}</span>
              </span>
            </div>

            <div className={`diff-hunks diff-hunks--${viewMode}`}>
              {activeFile.hunks.map((hunk) => (
                <div key={hunk.id} className="diff-hunk">
                  <div className="hunk-header">
                    @@ -{hunk.oldStart},{hunk.oldLines} +{hunk.newStart},{hunk.newLines} @@
                  </div>
                  {hunk.lines.map((line, idx) => {
                    const lineNum = line.newLineNum || line.oldLineNum || 0;
                    const lineComments = fileComments.filter(
                      (c) => c.lineNumber === lineNum
                    );
                    const isCommenting =
                      commentingLine?.filePath === activeFile.path &&
                      commentingLine?.lineNumber === lineNum;

                    return (
                      <React.Fragment key={`${hunk.id}-${idx}`}>
                        <div
                          className={`diff-line diff-line--${line.type}`}
                          onClick={() => {
                            if (line.type !== 'deletion') {
                              setCommentingLine({
                                filePath: activeFile.path,
                                lineNumber: lineNum,
                              });
                            }
                          }}
                        >
                          {viewMode === 'split' ? (
                            <>
                              <span className="line-num line-num--old">
                                {line.oldLineNum || ''}
                              </span>
                              <span className="line-num line-num--new">
                                {line.newLineNum || ''}
                              </span>
                            </>
                          ) : (
                            <span className="line-num">
                              {line.oldLineNum || ''} {line.newLineNum || ''}
                            </span>
                          )}
                          <span className="line-marker">
                            {line.type === 'addition' ? '+' : line.type === 'deletion' ? '-' : ' '}
                          </span>
                          <span className="line-content">
                            <code>{line.content}</code>
                          </span>
                          <button type="button" className="add-comment-btn" title="Add comment">
                            💬
                          </button>
                        </div>

                        {/* Inline Comments */}
                        {lineComments.map((comment) => (
                          <div key={comment.id} className="inline-comment">
                            <div className="inline-comment-header">
                              <span className="comment-author">{comment.author.name}</span>
                              <span className="comment-time">
                                {formatTimeAgo(comment.createdAt)}
                              </span>
                              {comment.resolved && (
                                <span className="comment-resolved">✅ Resolved</span>
                              )}
                            </div>
                            <p className="inline-comment-body">{comment.content}</p>
                          </div>
                        ))}

                        {/* Comment Input */}
                        {isCommenting && (
                          <div className="comment-input-row">
                            <textarea
                              value={commentText}
                              onChange={(e) => setCommentText(e.target.value)}
                              placeholder="Leave a comment..."
                              className="comment-textarea"
                              rows={3}
                              autoFocus
                            />
                            <div className="comment-actions">
                              <button
                                type="button"
                                className="cancel-btn"
                                onClick={() => {
                                  setCommentingLine(null);
                                  setCommentText('');
                                }}
                              >
                                Cancel
                              </button>
                              <button
                                type="button"
                                className="submit-btn"
                                onClick={handleSubmitComment}
                                disabled={!commentText.trim() || isSubmitting}
                              >
                                {isSubmitting ? 'Adding...' : 'Add Comment'}
                              </button>
                            </div>
                          </div>
                        )}
                      </React.Fragment>
                    );
                  })}
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const CodeReviewDetailPage: React.FC = () => {
  const { projectId, prId } = useParams<{
    projectId: string;
    prId: string;
  }>();
  const navigate = useNavigate();

  // State
  const [pr, setPr] = useState<PullRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'conversation' | 'files' | 'checks'>('files');
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'unified' | 'split'>('unified');
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [showMergeModal, setShowMergeModal] = useState(false);
  const [reviewAction, setReviewAction] = useState<ReviewAction>('comment');
  const [reviewBody, setReviewBody] = useState('');
  const [mergeMethod, setMergeMethod] = useState<'merge' | 'squash' | 'rebase'>('squash');
  const [isSubmittingReview, setIsSubmittingReview] = useState(false);
  const [isMerging, setIsMerging] = useState(false);

  // Load PR
  useEffect(() => {
    const loadPR = async () => {
      if (!projectId || !prId) return;

      setLoading(true);
      setError(null);

      try {
        const data = await fetchPullRequest(projectId, prId);
        setPr(data);
        if (data.files.length > 0) {
          setSelectedFile(data.files[0].path);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load pull request');
      } finally {
        setLoading(false);
      }
    };

    loadPR();
  }, [projectId, prId]);

  // Handlers
  const handleAddInlineComment = useCallback(
    async (filePath: string, lineNumber: number, content: string) => {
      if (!projectId || !prId) return;

      const comment = await addInlineComment(projectId, prId, filePath, lineNumber, content);
      setPr((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          inlineComments: [...prev.inlineComments, comment],
          commentsCount: prev.commentsCount + 1,
        };
      });
    },
    [projectId, prId]
  );

  const handleResolveComment = useCallback(
    async (commentId: string) => {
      if (!projectId || !prId) return;

      await resolveComment(projectId, prId, commentId);
      setPr((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          inlineComments: prev.inlineComments.map((c) =>
            c.id === commentId ? { ...c, resolved: true } : c
          ),
        };
      });
    },
    [projectId, prId]
  );

  const handleSubmitReview = useCallback(async () => {
    if (!projectId || !prId) return;

    setIsSubmittingReview(true);
    try {
      const review = await submitReview(projectId, prId, reviewAction, reviewBody);
      setPr((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          reviews: [...prev.reviews, review],
          status:
            reviewAction === 'approve'
              ? 'approved'
              : reviewAction === 'request_changes'
              ? 'changes_requested'
              : prev.status,
        };
      });
      setShowReviewModal(false);
      setReviewBody('');
    } catch (err) {
      console.error('Failed to submit review:', err);
    } finally {
      setIsSubmittingReview(false);
    }
  }, [projectId, prId, reviewAction, reviewBody]);

  const handleMerge = useCallback(async () => {
    if (!projectId || !prId) return;

    setIsMerging(true);
    try {
      await mergePullRequest(projectId, prId, mergeMethod);
      setPr((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          status: 'merged',
          mergedAt: new Date().toISOString(),
        };
      });
      setShowMergeModal(false);
    } catch (err) {
      console.error('Failed to merge:', err);
    } finally {
      setIsMerging(false);
    }
  }, [projectId, prId, mergeMethod]);

  // Computed
  const ciOverallStatus = useMemo((): CIStatus => {
    if (!pr?.ciChecks.length) return 'pending';
    if (pr.ciChecks.some((c) => c.status === 'failed')) return 'failed';
    if (pr.ciChecks.some((c) => c.status === 'running')) return 'running';
    if (pr.ciChecks.some((c) => c.status === 'pending')) return 'pending';
    if (pr.ciChecks.every((c) => c.status === 'passed')) return 'passed';
    return 'pending';
  }, [pr?.ciChecks]);

  const unresolvedComments = useMemo(() => {
    return pr?.inlineComments.filter((c) => !c.resolved).length || 0;
  }, [pr?.inlineComments]);

  const canMerge = useMemo(() => {
    if (!pr) return false;
    if (pr.status === 'merged' || pr.status === 'closed') return false;
    if (ciOverallStatus === 'failed') return false;
    return pr.status === 'approved' || pr.reviews.some((r) => r.status === 'approved');
  }, [pr, ciOverallStatus]);

  if (loading) {
    return (
      <div className="code-review-detail-page code-review-detail-page--loading">
        <LoadingSpinner message="Loading pull request..." />
      </div>
    );
  }

  if (error || !pr) {
    return (
      <div className="code-review-detail-page code-review-detail-page--error">
        <div className="error-container">
          <h2>Failed to load pull request</h2>
          <p>{error || 'Pull request not found'}</p>
          <button onClick={() => navigate(`/projects/${projectId}/reviews`)}>
            Back to Reviews
          </button>
        </div>
      </div>
    );
  }

  const statusConfig = getStatusConfig(pr.status);
  const ciConfig = getCIStatusConfig(ciOverallStatus);

  return (
    <ErrorBoundary>
      <div className="code-review-detail-page">
        {/* Header */}
        <header className="pr-header">
          <div className="pr-header-top">
            <Link to={`/projects/${projectId}/reviews`} className="back-link">
              ← Back to Reviews
            </Link>
            <div className="pr-status">
              <span
                className="status-badge"
                style={{ color: statusConfig.color, background: statusConfig.bg }}
              >
                {statusConfig.icon} {statusConfig.label}
              </span>
            </div>
          </div>

          <h1 className="pr-title">
            <span className="pr-number">#{pr.number}</span> {pr.title}
          </h1>

          <div className="pr-meta">
            <span className="pr-author">
              {pr.author.avatar ? (
                <img src={pr.author.avatar} alt={pr.author.name} className="author-avatar" />
              ) : (
                <span className="author-avatar author-avatar--placeholder">
                  {pr.author.name.charAt(0)}
                </span>
              )}
              <strong>{pr.author.name}</strong>
            </span>
            <span className="pr-branches">
              wants to merge{' '}
              <code className="branch-name">{pr.sourceBranch}</code>
              {' → '}
              <code className="branch-name">{pr.targetBranch}</code>
            </span>
            <span className="pr-time">{formatTimeAgo(pr.createdAt)}</span>
          </div>

          <div className="pr-stats">
            <span className="stat">
              <span className="stat-add">+{pr.additions}</span>
              <span className="stat-del">-{pr.deletions}</span>
            </span>
            <span className="stat">
              📁 {pr.files.length} files
            </span>
            <span className="stat">
              💬 {pr.commentsCount} comments
              {unresolvedComments > 0 && (
                <span className="unresolved"> ({unresolvedComments} unresolved)</span>
              )}
            </span>
            <span className="stat">
              {ciConfig.icon} CI: {ciOverallStatus}
            </span>
          </div>

          {/* Actions */}
          <div className="pr-actions">
            {pr.status !== 'merged' && pr.status !== 'closed' && (
              <>
                <button
                  type="button"
                  className="action-btn"
                  onClick={() => setShowReviewModal(true)}
                >
                  📝 Review
                </button>
                <button
                  type="button"
                  className={`action-btn action-btn--primary ${!canMerge ? 'action-btn--disabled' : ''}`}
                  onClick={() => canMerge && setShowMergeModal(true)}
                  disabled={!canMerge}
                >
                  🔀 Merge
                </button>
              </>
            )}
          </div>
        </header>

        {/* Tabs */}
        <div className="tabs-bar">
          <button
            type="button"
            className={`tab-btn ${activeTab === 'conversation' ? 'tab-btn--active' : ''}`}
            onClick={() => setActiveTab('conversation')}
          >
            💬 Conversation
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'files' ? 'tab-btn--active' : ''}`}
            onClick={() => setActiveTab('files')}
          >
            📁 Files Changed ({pr.files.length})
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'checks' ? 'tab-btn--active' : ''}`}
            onClick={() => setActiveTab('checks')}
          >
            {ciConfig.icon} Checks ({pr.ciChecks.length})
          </button>
          {activeTab === 'files' && (
            <div className="view-toggle">
              <button
                type="button"
                className={`toggle-btn ${viewMode === 'unified' ? 'toggle-btn--active' : ''}`}
                onClick={() => setViewMode('unified')}
              >
                Unified
              </button>
              <button
                type="button"
                className={`toggle-btn ${viewMode === 'split' ? 'toggle-btn--active' : ''}`}
                onClick={() => setViewMode('split')}
              >
                Split
              </button>
            </div>
          )}
        </div>

        {/* Tab Content */}
        <div className="tab-content">
          {activeTab === 'conversation' && (
            <div className="conversation-tab">
              {/* Description */}
              <div className="pr-description">
                <h3>Description</h3>
                <p>{pr.description || 'No description provided.'}</p>
              </div>

              {/* Linked Stories */}
              {pr.linkedStories.length > 0 && (
                <div className="linked-stories">
                  <h4>Linked Stories</h4>
                  <ul>
                    {pr.linkedStories.map((story) => (
                      <li key={story.id}>
                        <Link to={`/projects/${projectId}/stories/${story.id}`}>
                          {story.id}: {story.title}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Reviewers */}
              <div className="reviewers-section">
                <h4>Reviewers</h4>
                <div className="reviewers-list">
                  {pr.reviewers.map((reviewer) => {
                    const review = pr.reviews.find((r) => r.author.id === reviewer.id);
                    return (
                      <div key={reviewer.id} className="reviewer-item">
                        {reviewer.avatar ? (
                          <img
                            src={reviewer.avatar}
                            alt={reviewer.name}
                            className="reviewer-avatar"
                          />
                        ) : (
                          <span className="reviewer-avatar reviewer-avatar--placeholder">
                            {reviewer.name.charAt(0)}
                          </span>
                        )}
                        <span className="reviewer-name">{reviewer.name}</span>
                        {review && (
                          <span
                            className={`review-badge review-badge--${review.status}`}
                          >
                            {review.status === 'approved'
                              ? '✅'
                              : review.status === 'changes_requested'
                              ? '🔄'
                              : '💬'}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Reviews Timeline */}
              <div className="reviews-timeline">
                <h4>Reviews</h4>
                {pr.reviews.length === 0 ? (
                  <p className="empty-text">No reviews yet.</p>
                ) : (
                  <ul className="timeline-list">
                    {pr.reviews.map((review) => (
                      <li key={review.id} className="timeline-item">
                        <div className="timeline-header">
                          <span className="timeline-author">{review.author.name}</span>
                          <span
                            className={`timeline-status timeline-status--${review.status}`}
                          >
                            {review.status === 'approved'
                              ? '✅ Approved'
                              : review.status === 'changes_requested'
                              ? '🔄 Requested changes'
                              : '💬 Commented'}
                          </span>
                          <span className="timeline-time">
                            {formatTimeAgo(review.createdAt)}
                          </span>
                        </div>
                        {review.body && (
                          <p className="timeline-body">{review.body}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}

          {activeTab === 'files' && (
            <DiffViewer
              files={pr.files}
              comments={pr.inlineComments}
              selectedFile={selectedFile}
              onSelectFile={setSelectedFile}
              onAddComment={handleAddInlineComment}
              viewMode={viewMode}
            />
          )}

          {activeTab === 'checks' && (
            <div className="checks-tab">
              {pr.ciChecks.length === 0 ? (
                <p className="empty-text">No CI checks configured.</p>
              ) : (
                <ul className="checks-list">
                  {pr.ciChecks.map((check) => {
                    const checkConfig = getCIStatusConfig(check.status);
                    return (
                      <li key={check.id} className="check-item">
                        <span className="check-icon">{checkConfig.icon}</span>
                        <span className="check-name">{check.name}</span>
                        <span
                          className="check-status"
                          style={{ color: checkConfig.color }}
                        >
                          {check.status}
                        </span>
                        {check.duration !== undefined && (
                          <span className="check-duration">
                            {Math.round(check.duration / 1000)}s
                          </span>
                        )}
                        {check.url && (
                          <a
                            href={check.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="check-link"
                          >
                            View →
                          </a>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Review Modal */}
        {showReviewModal && (
          <div className="modal-overlay">
            <div className="modal">
              <h3 className="modal-title">Submit Review</h3>
              <div className="review-actions-select">
                <label className={`review-option ${reviewAction === 'comment' ? 'review-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="reviewAction"
                    value="comment"
                    checked={reviewAction === 'comment'}
                    onChange={() => setReviewAction('comment')}
                  />
                  <span className="option-icon">💬</span>
                  <span className="option-label">Comment</span>
                </label>
                <label className={`review-option ${reviewAction === 'approve' ? 'review-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="reviewAction"
                    value="approve"
                    checked={reviewAction === 'approve'}
                    onChange={() => setReviewAction('approve')}
                  />
                  <span className="option-icon">✅</span>
                  <span className="option-label">Approve</span>
                </label>
                <label className={`review-option ${reviewAction === 'request_changes' ? 'review-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="reviewAction"
                    value="request_changes"
                    checked={reviewAction === 'request_changes'}
                    onChange={() => setReviewAction('request_changes')}
                  />
                  <span className="option-icon">🔄</span>
                  <span className="option-label">Request Changes</span>
                </label>
              </div>
              <textarea
                value={reviewBody}
                onChange={(e) => setReviewBody(e.target.value)}
                placeholder="Leave a comment (optional for approve/request changes)..."
                className="review-textarea"
                rows={5}
              />
              <div className="modal-actions">
                <button
                  type="button"
                  className="modal-btn"
                  onClick={() => setShowReviewModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="modal-btn modal-btn--primary"
                  onClick={handleSubmitReview}
                  disabled={isSubmittingReview}
                >
                  {isSubmittingReview ? 'Submitting...' : 'Submit Review'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Merge Modal */}
        {showMergeModal && (
          <div className="modal-overlay">
            <div className="modal">
              <h3 className="modal-title">Merge Pull Request</h3>
              <div className="merge-method-select">
                <label className={`merge-option ${mergeMethod === 'squash' ? 'merge-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="mergeMethod"
                    value="squash"
                    checked={mergeMethod === 'squash'}
                    onChange={() => setMergeMethod('squash')}
                  />
                  <span className="option-label">Squash and merge</span>
                  <span className="option-desc">Combine all commits into one</span>
                </label>
                <label className={`merge-option ${mergeMethod === 'merge' ? 'merge-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="mergeMethod"
                    value="merge"
                    checked={mergeMethod === 'merge'}
                    onChange={() => setMergeMethod('merge')}
                  />
                  <span className="option-label">Create a merge commit</span>
                  <span className="option-desc">All commits will be added</span>
                </label>
                <label className={`merge-option ${mergeMethod === 'rebase' ? 'merge-option--selected' : ''}`}>
                  <input
                    type="radio"
                    name="mergeMethod"
                    value="rebase"
                    checked={mergeMethod === 'rebase'}
                    onChange={() => setMergeMethod('rebase')}
                  />
                  <span className="option-label">Rebase and merge</span>
                  <span className="option-desc">Commits will be rebased</span>
                </label>
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  className="modal-btn"
                  onClick={() => setShowMergeModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="modal-btn modal-btn--merge"
                  onClick={handleMerge}
                  disabled={isMerging}
                >
                  {isMerging ? 'Merging...' : '🔀 Confirm Merge'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* CSS-in-JS Styles */}
        <style>{`
          .code-review-detail-page {
            min-height: 100vh;
            background: #0D1117;
            color: #C9D1D9;
          }

          .code-review-detail-page--loading,
          .code-review-detail-page--error {
            display: flex;
            align-items: center;
            justify-content: center;
          }

          .error-container {
            text-align: center;
            padding: 2rem;
          }

          .error-container h2 {
            margin: 0 0 0.5rem;
            color: #F0F6FC;
          }

          .error-container p {
            margin: 0 0 1rem;
            color: #8B949E;
          }

          .error-container button {
            padding: 0.5rem 1rem;
            background: #238636;
            color: #fff;
            border: none;
            border-radius: 6px;
            cursor: pointer;
          }

          .pr-header {
            padding: 1.5rem 2rem;
            border-bottom: 1px solid #30363D;
          }

          .pr-header-top {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.75rem;
          }

          .back-link {
            color: #58A6FF;
            text-decoration: none;
            font-size: 0.875rem;
          }

          .back-link:hover {
            text-decoration: underline;
          }

          .status-badge {
            display: inline-flex;
            align-items: center;
            gap: 0.25rem;
            padding: 0.25rem 0.75rem;
            border-radius: 20px;
            font-size: 0.8125rem;
            font-weight: 600;
          }

          .pr-title {
            margin: 0 0 0.75rem;
            font-size: 1.5rem;
            font-weight: 600;
            color: #F0F6FC;
          }

          .pr-number {
            color: #8B949E;
            font-weight: 400;
          }

          .pr-meta {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 0.5rem;
            font-size: 0.875rem;
            color: #8B949E;
            margin-bottom: 0.75rem;
          }

          .pr-author {
            display: flex;
            align-items: center;
            gap: 0.375rem;
          }

          .author-avatar {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            object-fit: cover;
          }

          .author-avatar--placeholder {
            background: #30363D;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 0.6875rem;
            font-weight: 600;
            color: #C9D1D9;
          }

          .branch-name {
            background: rgba(56, 139, 253, 0.15);
            color: #58A6FF;
            padding: 0.125rem 0.375rem;
            border-radius: 6px;
            font-size: 0.8125rem;
          }

          .pr-stats {
            display: flex;
            flex-wrap: wrap;
            gap: 1rem;
            font-size: 0.875rem;
            color: #8B949E;
            margin-bottom: 1rem;
          }

          .stat {
            display: flex;
            align-items: center;
            gap: 0.25rem;
          }

          .stat-add {
            color: #3FB950;
          }

          .stat-del {
            color: #F85149;
          }

          .unresolved {
            color: #D29922;
          }

          .pr-actions {
            display: flex;
            gap: 0.5rem;
          }

          .action-btn {
            padding: 0.5rem 1rem;
            background: #21262D;
            border: 1px solid #30363D;
            border-radius: 6px;
            color: #C9D1D9;
            font-size: 0.875rem;
            cursor: pointer;
          }

          .action-btn:hover {
            background: #30363D;
          }

          .action-btn--primary {
            background: #238636;
            border-color: #238636;
            color: #fff;
          }

          .action-btn--primary:hover {
            background: #2EA043;
          }

          .action-btn--disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          .tabs-bar {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0 2rem;
            border-bottom: 1px solid #30363D;
          }

          .tab-btn {
            padding: 0.75rem 1rem;
            background: transparent;
            border: none;
            border-bottom: 2px solid transparent;
            color: #8B949E;
            font-size: 0.875rem;
            cursor: pointer;
          }

          .tab-btn:hover {
            color: #F0F6FC;
          }

          .tab-btn--active {
            color: #F0F6FC;
            border-bottom-color: #F78166;
          }

          .view-toggle {
            margin-left: auto;
            display: flex;
            gap: 0;
          }

          .toggle-btn {
            padding: 0.375rem 0.75rem;
            background: #21262D;
            border: 1px solid #30363D;
            color: #8B949E;
            font-size: 0.75rem;
            cursor: pointer;
          }

          .toggle-btn:first-child {
            border-radius: 6px 0 0 6px;
          }

          .toggle-btn:last-child {
            border-radius: 0 6px 6px 0;
            border-left: none;
          }

          .toggle-btn--active {
            background: #30363D;
            color: #F0F6FC;
          }

          .tab-content {
            padding: 1.5rem 2rem;
          }

          /* Conversation Tab */
          .conversation-tab {
            max-width: 900px;
          }

          .pr-description {
            margin-bottom: 1.5rem;
            padding: 1rem;
            background: #161B22;
            border: 1px solid #30363D;
            border-radius: 8px;
          }

          .pr-description h3 {
            margin: 0 0 0.5rem;
            font-size: 0.875rem;
            color: #F0F6FC;
          }

          .pr-description p {
            margin: 0;
            font-size: 0.875rem;
            color: #8B949E;
          }

          .linked-stories {
            margin-bottom: 1.5rem;
          }

          .linked-stories h4 {
            margin: 0 0 0.5rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #8B949E;
          }

          .linked-stories ul {
            list-style: none;
            padding: 0;
            margin: 0;
          }

          .linked-stories a {
            color: #58A6FF;
            text-decoration: none;
            font-size: 0.875rem;
          }

          .linked-stories a:hover {
            text-decoration: underline;
          }

          .reviewers-section {
            margin-bottom: 1.5rem;
          }

          .reviewers-section h4 {
            margin: 0 0 0.5rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #8B949E;
          }

          .reviewers-list {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
          }

          .reviewer-item {
            display: flex;
            align-items: center;
            gap: 0.375rem;
            padding: 0.375rem 0.75rem;
            background: #21262D;
            border-radius: 20px;
          }

          .reviewer-avatar {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            object-fit: cover;
          }

          .reviewer-avatar--placeholder {
            background: #30363D;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 0.6875rem;
            font-weight: 600;
          }

          .reviewer-name {
            font-size: 0.8125rem;
            color: #C9D1D9;
          }

          .review-badge {
            font-size: 0.75rem;
          }

          .reviews-timeline h4 {
            margin: 0 0 0.75rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #8B949E;
          }

          .empty-text {
            font-size: 0.875rem;
            color: #8B949E;
            font-style: italic;
          }

          .timeline-list {
            list-style: none;
            padding: 0;
            margin: 0;
          }

          .timeline-item {
            padding: 0.75rem;
            border-left: 2px solid #30363D;
            margin-left: 0.5rem;
            padding-left: 1rem;
            margin-bottom: 0.5rem;
          }

          .timeline-header {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
            align-items: center;
            margin-bottom: 0.25rem;
          }

          .timeline-author {
            font-size: 0.8125rem;
            font-weight: 600;
            color: #F0F6FC;
          }

          .timeline-status {
            font-size: 0.75rem;
          }

          .timeline-status--approved {
            color: #3FB950;
          }

          .timeline-status--changes_requested {
            color: #D29922;
          }

          .timeline-status--commented {
            color: #8B949E;
          }

          .timeline-time {
            font-size: 0.75rem;
            color: #8B949E;
          }

          .timeline-body {
            margin: 0;
            font-size: 0.875rem;
            color: #C9D1D9;
          }

          /* Diff Viewer */
          .diff-viewer {
            display: grid;
            grid-template-columns: 280px 1fr;
            gap: 1rem;
          }

          .file-list {
            background: #161B22;
            border: 1px solid #30363D;
            border-radius: 8px;
            overflow: hidden;
          }

          .file-list-title {
            margin: 0;
            padding: 0.75rem 1rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #F0F6FC;
            border-bottom: 1px solid #30363D;
          }

          .file-items {
            list-style: none;
            padding: 0;
            margin: 0;
            max-height: 500px;
            overflow-y: auto;
          }

          .file-item {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            width: 100%;
            padding: 0.5rem 1rem;
            background: transparent;
            border: none;
            color: #C9D1D9;
            font-size: 0.8125rem;
            text-align: left;
            cursor: pointer;
          }

          .file-item:hover {
            background: #21262D;
          }

          .file-item--active {
            background: #30363D;
          }

          .file-status {
            font-size: 0.75rem;
            font-weight: 700;
            width: 12px;
          }

          .file-name {
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .file-stats {
            display: flex;
            gap: 0.25rem;
            font-size: 0.6875rem;
          }

          .file-comments {
            background: #58A6FF;
            color: #0D1117;
            padding: 0 0.375rem;
            border-radius: 10px;
            font-size: 0.6875rem;
            font-weight: 600;
          }

          .diff-content {
            background: #161B22;
            border: 1px solid #30363D;
            border-radius: 8px;
            overflow: hidden;
          }

          .diff-header {
            display: flex;
            justify-content: space-between;
            padding: 0.75rem 1rem;
            border-bottom: 1px solid #30363D;
            background: #0D1117;
          }

          .diff-path {
            font-size: 0.8125rem;
            font-family: monospace;
            color: #C9D1D9;
          }

          .diff-stats {
            display: flex;
            gap: 0.5rem;
            font-size: 0.75rem;
          }

          .diff-add {
            color: #3FB950;
          }

          .diff-del {
            color: #F85149;
          }

          .diff-hunks {
            overflow-x: auto;
          }

          .diff-hunk {
            border-bottom: 1px solid #30363D;
          }

          .diff-hunk:last-child {
            border-bottom: none;
          }

          .hunk-header {
            padding: 0.5rem 1rem;
            background: rgba(56, 139, 253, 0.1);
            color: #8B949E;
            font-size: 0.75rem;
            font-family: monospace;
          }

          .diff-line {
            display: flex;
            font-family: monospace;
            font-size: 0.8125rem;
            line-height: 1.5;
            cursor: pointer;
          }

          .diff-line:hover {
            background: rgba(56, 139, 253, 0.1);
          }

          .diff-line:hover .add-comment-btn {
            opacity: 1;
          }

          .diff-line--context {
            background: transparent;
          }

          .diff-line--addition {
            background: rgba(63, 185, 80, 0.15);
          }

          .diff-line--deletion {
            background: rgba(248, 81, 73, 0.15);
          }

          .line-num {
            min-width: 50px;
            padding: 0 0.5rem;
            text-align: right;
            color: #484F58;
            border-right: 1px solid #30363D;
            user-select: none;
          }

          .line-num--old,
          .line-num--new {
            min-width: 40px;
          }

          .line-marker {
            width: 20px;
            text-align: center;
            color: #8B949E;
            user-select: none;
          }

          .diff-line--addition .line-marker {
            color: #3FB950;
          }

          .diff-line--deletion .line-marker {
            color: #F85149;
          }

          .line-content {
            flex: 1;
            padding: 0 1rem;
            white-space: pre;
          }

          .line-content code {
            color: inherit;
            font-family: inherit;
          }

          .add-comment-btn {
            opacity: 0;
            background: #238636;
            border: none;
            color: #fff;
            padding: 0 0.375rem;
            margin-right: 0.5rem;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.6875rem;
          }

          .inline-comment {
            margin: 0.5rem 1rem;
            padding: 0.75rem;
            background: #21262D;
            border: 1px solid #30363D;
            border-radius: 8px;
          }

          .inline-comment-header {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            margin-bottom: 0.25rem;
          }

          .comment-author {
            font-size: 0.8125rem;
            font-weight: 600;
            color: #F0F6FC;
          }

          .comment-time {
            font-size: 0.75rem;
            color: #8B949E;
          }

          .comment-resolved {
            font-size: 0.6875rem;
            color: #3FB950;
          }

          .inline-comment-body {
            margin: 0;
            font-size: 0.8125rem;
            color: #C9D1D9;
          }

          .comment-input-row {
            margin: 0.5rem 1rem;
            padding: 0.75rem;
            background: #21262D;
            border: 1px solid #30363D;
            border-radius: 8px;
          }

          .comment-textarea {
            width: 100%;
            padding: 0.5rem;
            background: #0D1117;
            border: 1px solid #30363D;
            border-radius: 6px;
            color: #C9D1D9;
            font-size: 0.8125rem;
            resize: vertical;
          }

          .comment-textarea:focus {
            outline: none;
            border-color: #58A6FF;
          }

          .comment-actions {
            display: flex;
            justify-content: flex-end;
            gap: 0.5rem;
            margin-top: 0.5rem;
          }

          .cancel-btn {
            padding: 0.375rem 0.75rem;
            background: transparent;
            border: 1px solid #30363D;
            border-radius: 6px;
            color: #C9D1D9;
            font-size: 0.75rem;
            cursor: pointer;
          }

          .submit-btn {
            padding: 0.375rem 0.75rem;
            background: #238636;
            border: none;
            border-radius: 6px;
            color: #fff;
            font-size: 0.75rem;
            cursor: pointer;
          }

          .submit-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          /* Checks Tab */
          .checks-tab {
            max-width: 700px;
          }

          .checks-list {
            list-style: none;
            padding: 0;
            margin: 0;
          }

          .check-item {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            padding: 0.75rem 1rem;
            border: 1px solid #30363D;
            border-radius: 8px;
            margin-bottom: 0.5rem;
          }

          .check-icon {
            font-size: 1rem;
          }

          .check-name {
            flex: 1;
            font-size: 0.875rem;
            color: #F0F6FC;
          }

          .check-status {
            font-size: 0.75rem;
            font-weight: 600;
          }

          .check-duration {
            font-size: 0.75rem;
            color: #8B949E;
          }

          .check-link {
            color: #58A6FF;
            text-decoration: none;
            font-size: 0.75rem;
          }

          .check-link:hover {
            text-decoration: underline;
          }

          /* Modals */
          .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.7);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
          }

          .modal {
            background: #161B22;
            border: 1px solid #30363D;
            border-radius: 12px;
            padding: 1.5rem;
            width: 100%;
            max-width: 500px;
          }

          .modal-title {
            margin: 0 0 1rem;
            font-size: 1.125rem;
            color: #F0F6FC;
          }

          .review-actions-select,
          .merge-method-select {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            margin-bottom: 1rem;
          }

          .review-option,
          .merge-option {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.75rem;
            background: #21262D;
            border: 2px solid transparent;
            border-radius: 8px;
            cursor: pointer;
          }

          .review-option--selected,
          .merge-option--selected {
            border-color: #58A6FF;
            background: rgba(56, 139, 253, 0.1);
          }

          .review-option input,
          .merge-option input {
            display: none;
          }

          .option-icon {
            font-size: 1rem;
          }

          .option-label {
            font-size: 0.875rem;
            font-weight: 600;
            color: #F0F6FC;
          }

          .option-desc {
            font-size: 0.75rem;
            color: #8B949E;
            margin-left: auto;
          }

          .review-textarea {
            width: 100%;
            padding: 0.75rem;
            background: #0D1117;
            border: 1px solid #30363D;
            border-radius: 8px;
            color: #C9D1D9;
            font-size: 0.875rem;
            resize: vertical;
            margin-bottom: 1rem;
          }

          .review-textarea:focus {
            outline: none;
            border-color: #58A6FF;
          }

          .modal-actions {
            display: flex;
            justify-content: flex-end;
            gap: 0.5rem;
          }

          .modal-btn {
            padding: 0.5rem 1rem;
            background: #21262D;
            border: 1px solid #30363D;
            border-radius: 6px;
            color: #C9D1D9;
            font-size: 0.875rem;
            cursor: pointer;
          }

          .modal-btn:hover {
            background: #30363D;
          }

          .modal-btn--primary {
            background: #238636;
            border-color: #238636;
            color: #fff;
          }

          .modal-btn--primary:hover {
            background: #2EA043;
          }

          .modal-btn--merge {
            background: #8B5CF6;
            border-color: #8B5CF6;
            color: #fff;
          }

          .modal-btn--merge:hover {
            background: #7C3AED;
          }

          .modal-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          @media (max-width: 1024px) {
            .diff-viewer {
              grid-template-columns: 1fr;
            }

            .file-list {
              max-height: 200px;
            }
          }
        `}</style>
      </div>
    </ErrorBoundary>
  );
};

CodeReviewDetailPage.displayName = 'CodeReviewDetailPage';

export default CodeReviewDetailPage;
