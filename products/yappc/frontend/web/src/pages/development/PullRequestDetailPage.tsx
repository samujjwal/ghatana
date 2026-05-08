import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';

type PRState = 'open' | 'closed' | 'merged' | 'draft';
type CheckStatus = 'success' | 'failure' | 'pending' | 'skipped';
type ReviewDecision = 'approved' | 'changes-requested' | 'pending' | 'commented';
type MergeMethod = 'merge' | 'squash' | 'rebase';

interface CICheck {
  id: string;
  name: string;
  status: CheckStatus;
  durationMs: number;
  url: string;
}

interface PRReviewer {
  login: string;
  avatarUrl: string;
  decision: ReviewDecision;
}

interface Label {
  name: string;
  color: string;
}

interface PullRequestData {
  id: string;
  number: number;
  title: string;
  body: string;
  state: PRState;
  author: string;
  authorAvatar: string;
  branch: string;
  baseBranch: string;
  createdAt: string;
  updatedAt: string;
  additions: number;
  deletions: number;
  changedFiles: number;
  commits: number;
  labels: Label[];
  checks: CICheck[];
  reviewers: PRReviewer[];
  mergeable: boolean;
  conflicting: boolean;
}

const STATE_STYLES: Record<PRState, { bg: string; text: string; label: string }> = {
  open: { bg: 'bg-success-bg', text: 'text-success-color', label: 'Open' },
  closed: { bg: 'bg-destructive-bg', text: 'text-destructive', label: 'Closed' },
  merged: { bg: 'bg-info-bg', text: 'text-info-color', label: 'Merged' },
  draft: { bg: 'bg-surface', text: 'text-fg-muted', label: 'Draft' },
};

const CHECK_STYLES: Record<CheckStatus, { text: string; icon: string }> = {
  success: { text: 'text-success-color', icon: '\u2713' },
  failure: { text: 'text-destructive', icon: '\u2717' },
  pending: { text: 'text-warning-color', icon: '\u25CB' },
  skipped: { text: 'text-fg-muted', icon: '\u2014' },
};

const REVIEW_STYLES: Record<ReviewDecision, { text: string; icon: string }> = {
  approved: { text: 'text-success-color', icon: '\u2713' },
  'changes-requested': { text: 'text-destructive', icon: '\u2717' },
  pending: { text: 'text-fg-muted', icon: '\u25CB' },
  commented: { text: 'text-info-color', icon: '\u2026' },
};

const formatDuration = (ms: number): string => {
  const s = Math.floor(ms / 1000);
  if (s < 60) return `${s}s`;
  return `${Math.floor(s / 60)}m ${s % 60}s`;
};

/**
 * PullRequestDetailPage — PR detail with title, author, CI checks, reviewers, and merge controls.
 *
 * @doc.type component
 * @doc.purpose Pull request detail view with CI checks and merge controls
 * @doc.layer product
 */
const PullRequestDetailPage: React.FC = () => {
  const { prId } = useParams<{ prId: string }>();
  const queryClient = useQueryClient();
  const [mergeMethod, setMergeMethod] = useState<MergeMethod>('squash');

  const { data: pr, isLoading, error } = useQuery<PullRequestData>({
    queryKey: ['pull-request', prId],
    queryFn: async () => {
      const res = await fetch(`/api/pull-requests/${prId}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load pull request'));
      }
      return parseJsonResponse<PullRequestData>(res, 'pull request detail');
    },
    enabled: !!prId,
  });

  const mergeMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`/api/pull-requests/${prId}/merge`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
        },
        body: JSON.stringify({ method: mergeMethod }),
      });
      if (!res.ok) throw new Error('Merge failed');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pull-request', prId] });
    },
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
        <div className="bg-destructive-bg border border-destructive-border rounded-lg p-4 text-destructive">
          {error instanceof Error ? error.message : 'Failed to load pull request'}
        </div>
      </div>
    );
  }

  const state = pr?.state ?? 'open';
  const stateStyle = STATE_STYLES[state];
  const checks = pr?.checks ?? [];
  const reviewers = pr?.reviewers ?? [];
  const allChecksPassed = checks.length > 0 && checks.every((c) => c.status === 'success' || c.status === 'skipped');
  const hasApproval = reviewers.some((r) => r.decision === 'approved');
  const canMerge = pr?.mergeable && !pr?.conflicting && state === 'open';

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/development/pull-requests" className="text-fg-muted hover:text-fg-muted transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-fg-muted">{pr?.title ?? 'Pull Request'}</h1>
              <span className="text-lg text-fg-muted">#{pr?.number ?? prId}</span>
              <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${stateStyle.bg} ${stateStyle.text}`}>
                {stateStyle.label}
              </span>
            </div>
            <div className="flex items-center gap-2 mt-1">
              {pr?.authorAvatar && <img src={pr.authorAvatar} alt={pr.author} className="w-5 h-5 rounded-full" />}
              <span className="text-sm text-fg-muted">
                <span className="text-fg-muted">{pr?.author ?? 'Unknown'}</span>
                {' '}wants to merge{' '}
                <span className="font-mono text-info-color">{pr?.branch ?? '—'}</span>
                {' '}into{' '}
                <span className="font-mono text-fg-muted">{pr?.baseBranch ?? 'main'}</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Labels */}
      {pr?.labels && pr.labels.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {pr.labels.map((l) => (
            <span
              key={l.name}
              className="px-2.5 py-0.5 text-xs font-medium rounded-full border"
              style={{ borderColor: `#${l.color}40`, color: `#${l.color}`, backgroundColor: `#${l.color}15` }}
            >
              {l.name}
            </span>
          ))}
        </div>
      )}

      {/* Stats Row */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-surface border border-border rounded-lg p-4 text-center">
          <p className="text-2xl font-bold text-fg-muted">{pr?.commits ?? 0}</p>
          <p className="text-xs text-fg-muted mt-1 uppercase">Commits</p>
        </div>
        <div className="bg-surface border border-border rounded-lg p-4 text-center">
          <p className="text-2xl font-bold text-fg-muted">{pr?.changedFiles ?? 0}</p>
          <p className="text-xs text-fg-muted mt-1 uppercase">Files Changed</p>
        </div>
        <div className="bg-surface border border-border rounded-lg p-4 text-center">
          <p className="text-2xl font-bold text-success-color">+{pr?.additions ?? 0}</p>
          <p className="text-xs text-fg-muted mt-1 uppercase">Additions</p>
        </div>
        <div className="bg-surface border border-border rounded-lg p-4 text-center">
          <p className="text-2xl font-bold text-destructive">-{pr?.deletions ?? 0}</p>
          <p className="text-xs text-fg-muted mt-1 uppercase">Deletions</p>
        </div>
      </div>

      {/* Description */}
      {pr?.body && (
        <div className="bg-surface border border-border rounded-lg p-5">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide mb-2">Description</h2>
          <p className="text-sm text-fg-muted leading-relaxed whitespace-pre-wrap">{pr.body}</p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-6">
        {/* CI Checks */}
        <div className="bg-surface border border-border rounded-lg">
          <div className="px-5 py-4 border-b border-border flex items-center justify-between">
            <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide">CI Checks ({checks.length})</h2>
            <span className={`text-xs font-medium ${allChecksPassed ? 'text-success-color' : 'text-warning-color'}`}>
              {allChecksPassed ? 'All passed' : 'In progress'}
            </span>
          </div>
          {checks.length === 0 ? (
            <div className="p-6 text-center text-fg-muted text-sm">No CI checks configured.</div>
          ) : (
            <div className="divide-y divide-zinc-800">
              {checks.map((check) => {
                const cs = CHECK_STYLES[check.status];
                return (
                  <div key={check.id} className="px-5 py-3 flex items-center justify-between hover:bg-surface/50 transition-colors">
                    <div className="flex items-center gap-3">
                      <span className={`text-sm font-bold ${cs.text}`}>{cs.icon}</span>
                      <span className="text-sm text-fg-muted">{check.name}</span>
                    </div>
                    <span className="text-xs text-fg-muted">{formatDuration(check.durationMs)}</span>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Reviewers */}
        <div className="bg-surface border border-border rounded-lg">
          <div className="px-5 py-4 border-b border-border flex items-center justify-between">
            <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide">Reviewers ({reviewers.length})</h2>
            {hasApproval && <span className="text-xs text-success-color font-medium">Approved</span>}
          </div>
          {reviewers.length === 0 ? (
            <div className="p-6 text-center text-fg-muted text-sm">No reviewers assigned.</div>
          ) : (
            <div className="divide-y divide-zinc-800">
              {reviewers.map((r) => {
                const rs = REVIEW_STYLES[r.decision];
                return (
                  <div key={r.login} className="px-5 py-3 flex items-center justify-between hover:bg-surface/50 transition-colors">
                    <div className="flex items-center gap-3">
                      <img src={r.avatarUrl} alt={r.login} className="w-6 h-6 rounded-full" />
                      <span className="text-sm text-fg-muted">{r.login}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`text-xs font-bold ${rs.text}`}>{rs.icon}</span>
                      <span className={`text-xs capitalize ${rs.text}`}>{r.decision.replace('-', ' ')}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Merge Section */}
      {state === 'open' && (
        <div className={`border rounded-lg p-5 ${pr?.conflicting ? 'bg-destructive-bg border-destructive-border' : 'bg-surface border-border'}`}>
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide mb-4">Merge</h2>

          {pr?.conflicting && (
            <div className="mb-4 p-3 bg-destructive-bg border border-destructive-border rounded-lg text-sm text-destructive">
              This branch has conflicts that must be resolved before merging.
            </div>
          )}

          <div className="flex items-center gap-4">
            <Select
              value={mergeMethod}
              onChange={(e) => setMergeMethod(e.target.value as MergeMethod)}
              size="sm"
              className="bg-surface border-border text-fg-muted text-sm rounded-lg px-3 py-2 focus:ring-blue-500"
            >
              <option value="squash">Squash and merge</option>
              <option value="merge">Create a merge commit</option>
              <option value="rebase">Rebase and merge</option>
            </Select>

            <Button
              onClick={() => mergeMutation.mutate()}
              disabled={!canMerge || mergeMutation.isPending}
              className={`px-6 py-2 text-sm font-medium rounded-lg transition-colors ${
                canMerge
                  ? 'bg-success-bg hover:bg-success-bg text-white'
                  : 'bg-surface text-fg-muted cursor-not-allowed'
              }`}
            >
              {mergeMutation.isPending ? 'Merging...' : 'Merge Pull Request'}
            </Button>

            {!allChecksPassed && (
              <span className="text-xs text-warning-color">Some checks have not passed</span>
            )}
            {!hasApproval && (
              <span className="text-xs text-warning-color">No approvals yet</span>
            )}
          </div>

          {mergeMutation.isError && (
            <div className="mt-3 p-3 bg-destructive-bg border border-destructive-border rounded-lg text-sm text-destructive">
              {mergeMutation.error instanceof Error ? mergeMutation.error.message : 'Merge failed'}
            </div>
          )}
        </div>
      )}

      {state === 'merged' && (
        <div className="bg-info-bg border border-info-border rounded-lg p-5 text-center">
          <p className="text-info-color font-medium">This pull request has been merged.</p>
        </div>
      )}
    </div>
  );
};

export default PullRequestDetailPage;
