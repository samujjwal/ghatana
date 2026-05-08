import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { Button } from '../../components/ui/Button';

type ReviewDecision = 'approved' | 'changes-requested' | 'pending' | 'commented';

interface ChangedFile {
  path: string;
  additions: number;
  deletions: number;
  status: 'added' | 'modified' | 'deleted' | 'renamed';
}

interface Reviewer {
  login: string;
  avatarUrl: string;
  decision: ReviewDecision;
  reviewedAt: string | null;
}

interface ReviewComment {
  id: string;
  author: string;
  avatarUrl: string;
  body: string;
  file: string | null;
  line: number | null;
  createdAt: string;
  resolved: boolean;
}

interface CodeReviewData {
  id: string;
  title: string;
  author: string;
  authorAvatar: string;
  branch: string;
  baseBranch: string;
  createdAt: string;
  totalAdditions: number;
  totalDeletions: number;
  files: ChangedFile[];
  reviewers: Reviewer[];
  comments: ReviewComment[];
}

const FILE_STATUS_STYLE: Record<ChangedFile['status'], string> = {
  added: 'text-success-color',
  modified: 'text-warning-color',
  deleted: 'text-destructive',
  renamed: 'text-info-color',
};

const FILE_STATUS_LABEL: Record<ChangedFile['status'], string> = {
  added: 'A',
  modified: 'M',
  deleted: 'D',
  renamed: 'R',
};

const DECISION_STYLE: Record<ReviewDecision, { text: string; bg: string; icon: string }> = {
  approved: { text: 'text-success-color', bg: 'bg-success-bg/20', icon: '\u2713' },
  'changes-requested': { text: 'text-destructive', bg: 'bg-destructive-bg/20', icon: '\u2717' },
  pending: { text: 'text-fg-muted', bg: 'bg-surface', icon: '\u25CB' },
  commented: { text: 'text-info-color', bg: 'bg-info-bg/20', icon: '\u2026' },
};

/**
 * CodeReviewPage — Displays file list, diff stats, reviewer status, and comments.
 *
 * @doc.type component
 * @doc.purpose Code review detail view with diff stats and reviewer tracking
 * @doc.layer product
 */
const CodeReviewPage: React.FC = () => {
  const { reviewId } = useParams<{ reviewId: string }>();
  const [activeTab, setActiveTab] = useState<'files' | 'comments'>('files');

  const { data: review, isLoading, error } = useQuery<CodeReviewData>({
    queryKey: ['code-review', reviewId],
    queryFn: async () => {
      const res = await fetch(`/api/code-reviews/${reviewId}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load code review'));
      }
      return parseJsonResponse<CodeReviewData>(res, 'code review page');
    },
    enabled: !!reviewId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
          {error instanceof Error ? error.message : 'Failed to load code review'}
        </div>
      </div>
    );
  }

  const files = review?.files ?? [];
  const reviewers = review?.reviewers ?? [];
  const comments = review?.comments ?? [];
  const unresolvedCount = comments.filter((c) => !c.resolved).length;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/development/reviews" className="text-fg-muted hover:text-fg-muted transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-fg-muted">{review?.title ?? 'Code Review'}</h1>
            <p className="text-sm text-fg-muted mt-1">
              <span className="text-fg-muted">{review?.author ?? 'Unknown'}</span>
              {' '}wants to merge{' '}
              <span className="font-mono text-info-color">{review?.branch ?? '—'}</span>
              {' '}into{' '}
              <span className="font-mono text-fg-muted">{review?.baseBranch ?? 'main'}</span>
            </p>
          </div>
        </div>
        <Button className="px-4 py-2 bg-success-bg hover:bg-success-bg text-white text-sm font-medium rounded-lg transition-colors">Approve</Button>
      </div>

      {/* Diff Stats Bar */}
      <div className="bg-surface border border-border rounded-lg p-4 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <span className="text-sm text-fg-muted">
            <span className="font-semibold text-fg-muted">{files.length}</span> files changed
          </span>
          <span className="text-sm text-success-color">+{review?.totalAdditions ?? 0}</span>
          <span className="text-sm text-destructive">-{review?.totalDeletions ?? 0}</span>
        </div>
        <div className="flex items-center gap-2">
          {unresolvedCount > 0 && (
            <span className="text-xs bg-warning-bg/30 text-warning-color px-2.5 py-1 rounded-full">
              {unresolvedCount} unresolved
            </span>
          )}
          <span className="text-xs text-fg-muted">{comments.length} comments</span>
        </div>
      </div>

      {/* Reviewers */}
      <div className="bg-surface border border-border rounded-lg p-5">
        <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide mb-3">Reviewers</h2>
        <div className="flex flex-wrap gap-3">
          {reviewers.length === 0 ? (
            <p className="text-sm text-fg-muted">No reviewers assigned.</p>
          ) : (
            reviewers.map((r) => {
              const ds = DECISION_STYLE[r.decision];
              return (
                <div key={r.login} className={`flex items-center gap-2 px-3 py-2 rounded-lg border border-border ${ds.bg}`}>
                  <img src={r.avatarUrl} alt={r.login} className="w-6 h-6 rounded-full" />
                  <span className="text-sm text-fg-muted">{r.login}</span>
                  <span className={`text-xs font-bold ${ds.text}`}>{ds.icon}</span>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-border pb-1">
        <Button
          onClick={() => setActiveTab('files')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${activeTab === 'files' ? 'bg-surface-muted text-fg-muted' : 'text-fg-muted hover:text-fg-muted hover:bg-surface'}`}
        >
          Files ({files.length})
        </Button>
        <Button
          onClick={() => setActiveTab('comments')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${activeTab === 'comments' ? 'bg-surface-muted text-fg-muted' : 'text-fg-muted hover:text-fg-muted hover:bg-surface'}`}
        >
          Comments ({comments.length})
        </Button>
      </div>

      {/* Files Tab */}
      {activeTab === 'files' && (
        <div className="bg-surface border border-border rounded-lg divide-y divide-zinc-800">
          {files.length === 0 ? (
            <div className="p-8 text-center text-fg-muted text-sm">No files changed.</div>
          ) : (
            files.map((f) => (
              <div key={f.path} className="px-5 py-3 flex items-center justify-between hover:bg-surface/50 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <span className={`text-xs font-bold w-4 text-center ${FILE_STATUS_STYLE[f.status]}`}>{FILE_STATUS_LABEL[f.status]}</span>
                  <span className="text-sm font-mono text-fg-muted truncate">{f.path}</span>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0 text-xs font-mono">
                  <span className="text-success-color">+{f.additions}</span>
                  <span className="text-destructive">-{f.deletions}</span>
                  {/* Mini diff bar */}
                  <div className="flex h-2 w-16 rounded-full overflow-hidden bg-surface">
                    {f.additions + f.deletions > 0 && (
                      <>
                        <div className="bg-success-bg h-full" style={{ width: `${(f.additions / (f.additions + f.deletions)) * 100}%` }} />
                        <div className="bg-destructive-bg h-full" style={{ width: `${(f.deletions / (f.additions + f.deletions)) * 100}%` }} />
                      </>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Comments Tab */}
      {activeTab === 'comments' && (
        <div className="space-y-3">
          {comments.length === 0 ? (
            <div className="bg-surface border border-border rounded-lg p-8 text-center text-fg-muted text-sm">No comments yet.</div>
          ) : (
            comments.map((c) => (
              <div key={c.id} className={`bg-surface border rounded-lg p-4 ${c.resolved ? 'border-border opacity-60' : 'border-border'}`}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <img src={c.avatarUrl} alt={c.author} className="w-5 h-5 rounded-full" />
                    <span className="text-sm font-medium text-fg-muted">{c.author}</span>
                    <span className="text-xs text-fg-muted">{new Date(c.createdAt).toLocaleString()}</span>
                  </div>
                  {c.resolved && <span className="text-xs text-success-color font-medium">Resolved</span>}
                </div>
                {c.file && (
                  <p className="text-xs font-mono text-fg-muted mb-1">
                    {c.file}{c.line !== null ? `:${c.line}` : ''}
                  </p>
                )}
                <p className="text-sm text-fg-muted">{c.body}</p>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default CodeReviewPage;
