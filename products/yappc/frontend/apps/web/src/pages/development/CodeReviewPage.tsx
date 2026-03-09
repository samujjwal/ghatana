import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

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
  added: 'text-green-400',
  modified: 'text-yellow-400',
  deleted: 'text-red-400',
  renamed: 'text-blue-400',
};

const FILE_STATUS_LABEL: Record<ChangedFile['status'], string> = {
  added: 'A',
  modified: 'M',
  deleted: 'D',
  renamed: 'R',
};

const DECISION_STYLE: Record<ReviewDecision, { text: string; bg: string; icon: string }> = {
  approved: { text: 'text-green-400', bg: 'bg-green-900/20', icon: '\u2713' },
  'changes-requested': { text: 'text-red-400', bg: 'bg-red-900/20', icon: '\u2717' },
  pending: { text: 'text-zinc-500', bg: 'bg-zinc-800', icon: '\u25CB' },
  commented: { text: 'text-blue-400', bg: 'bg-blue-900/20', icon: '\u2026' },
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
      if (!res.ok) throw new Error('Failed to load code review');
      return res.json() as Promise<CodeReviewData>;
    },
    enabled: !!reviewId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
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
          <Link to="/development/reviews" className="text-zinc-400 hover:text-zinc-200 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-zinc-100">{review?.title ?? 'Code Review'}</h1>
            <p className="text-sm text-zinc-400 mt-1">
              <span className="text-zinc-300">{review?.author ?? 'Unknown'}</span>
              {' '}wants to merge{' '}
              <span className="font-mono text-blue-400">{review?.branch ?? '—'}</span>
              {' '}into{' '}
              <span className="font-mono text-zinc-300">{review?.baseBranch ?? 'main'}</span>
            </p>
          </div>
        </div>
        <button className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors">Approve</button>
      </div>

      {/* Diff Stats Bar */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <span className="text-sm text-zinc-400">
            <span className="font-semibold text-zinc-200">{files.length}</span> files changed
          </span>
          <span className="text-sm text-green-400">+{review?.totalAdditions ?? 0}</span>
          <span className="text-sm text-red-400">-{review?.totalDeletions ?? 0}</span>
        </div>
        <div className="flex items-center gap-2">
          {unresolvedCount > 0 && (
            <span className="text-xs bg-yellow-900/30 text-yellow-400 px-2.5 py-1 rounded-full">
              {unresolvedCount} unresolved
            </span>
          )}
          <span className="text-xs text-zinc-500">{comments.length} comments</span>
        </div>
      </div>

      {/* Reviewers */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-5">
        <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wide mb-3">Reviewers</h2>
        <div className="flex flex-wrap gap-3">
          {reviewers.length === 0 ? (
            <p className="text-sm text-zinc-500">No reviewers assigned.</p>
          ) : (
            reviewers.map((r) => {
              const ds = DECISION_STYLE[r.decision];
              return (
                <div key={r.login} className={`flex items-center gap-2 px-3 py-2 rounded-lg border border-zinc-700 ${ds.bg}`}>
                  <img src={r.avatarUrl} alt={r.login} className="w-6 h-6 rounded-full" />
                  <span className="text-sm text-zinc-200">{r.login}</span>
                  <span className={`text-xs font-bold ${ds.text}`}>{ds.icon}</span>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-zinc-800 pb-1">
        <button
          onClick={() => setActiveTab('files')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${activeTab === 'files' ? 'bg-zinc-700 text-zinc-100' : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'}`}
        >
          Files ({files.length})
        </button>
        <button
          onClick={() => setActiveTab('comments')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${activeTab === 'comments' ? 'bg-zinc-700 text-zinc-100' : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'}`}
        >
          Comments ({comments.length})
        </button>
      </div>

      {/* Files Tab */}
      {activeTab === 'files' && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg divide-y divide-zinc-800">
          {files.length === 0 ? (
            <div className="p-8 text-center text-zinc-500 text-sm">No files changed.</div>
          ) : (
            files.map((f) => (
              <div key={f.path} className="px-5 py-3 flex items-center justify-between hover:bg-zinc-800/50 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <span className={`text-xs font-bold w-4 text-center ${FILE_STATUS_STYLE[f.status]}`}>{FILE_STATUS_LABEL[f.status]}</span>
                  <span className="text-sm font-mono text-zinc-300 truncate">{f.path}</span>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0 text-xs font-mono">
                  <span className="text-green-400">+{f.additions}</span>
                  <span className="text-red-400">-{f.deletions}</span>
                  {/* Mini diff bar */}
                  <div className="flex h-2 w-16 rounded-full overflow-hidden bg-zinc-800">
                    {f.additions + f.deletions > 0 && (
                      <>
                        <div className="bg-green-500 h-full" style={{ width: `${(f.additions / (f.additions + f.deletions)) * 100}%` }} />
                        <div className="bg-red-500 h-full" style={{ width: `${(f.deletions / (f.additions + f.deletions)) * 100}%` }} />
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
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 text-center text-zinc-500 text-sm">No comments yet.</div>
          ) : (
            comments.map((c) => (
              <div key={c.id} className={`bg-zinc-900 border rounded-lg p-4 ${c.resolved ? 'border-zinc-800 opacity-60' : 'border-zinc-700'}`}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <img src={c.avatarUrl} alt={c.author} className="w-5 h-5 rounded-full" />
                    <span className="text-sm font-medium text-zinc-200">{c.author}</span>
                    <span className="text-xs text-zinc-500">{new Date(c.createdAt).toLocaleString()}</span>
                  </div>
                  {c.resolved && <span className="text-xs text-green-500 font-medium">Resolved</span>}
                </div>
                {c.file && (
                  <p className="text-xs font-mono text-zinc-500 mb-1">
                    {c.file}{c.line !== null ? `:${c.line}` : ''}
                  </p>
                )}
                <p className="text-sm text-zinc-300">{c.body}</p>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default CodeReviewPage;
