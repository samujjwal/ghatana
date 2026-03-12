/**
 * HitlReviewPage — Human-In-The-Loop review queue for AEP learning decisions.
 *
 * Features:
 *   - List pending review items with type, skill, confidence
 *   - Open detail view with proposed policy JSON diff
 *   - Approve (with optional note) / Reject (with required reason)
 *   - Color-coded confidence score meter
 *
 * @doc.type page
 * @doc.purpose AEP HITL review queue
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listPendingReviews,
  approveReview,
  rejectReview,
  type ReviewItem,
  type ReviewItemStatus,
} from '@/api/aep.api';

// ─── Confidence meter ────────────────────────────────────────────────

function ConfidenceMeter({ score }: { score?: number }) {
  if (score == null) return <span className="text-gray-400 text-xs">—</span>;
  const pct = Math.round(score * 100);
  const color =
    pct >= 80 ? 'bg-green-500' : pct >= 60 ? 'bg-yellow-500' : 'bg-red-500';
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-20 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div className={['h-full rounded-full', color].join(' ')} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500">{pct}%</span>
    </div>
  );
}

// ─── Status badge ────────────────────────────────────────────────────

const STATUS_COLORS: Record<ReviewItemStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  APPROVED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  REJECTED: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
};

// ─── Detail / Action Panel ───────────────────────────────────────────

function ReviewDetailPanel({
  item,
  onClose,
}: {
  item: ReviewItem;
  onClose: () => void;
}) {
  const tenantId = 'default';
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [mode, setMode] = useState<'approve' | 'reject' | null>(null);

  const approveMut = useMutation({
    mutationFn: () => approveReview(item.reviewId, { note, tenantId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'hitl'] });
      onClose();
    },
  });

  const rejectMut = useMutation({
    mutationFn: () => rejectReview(item.reviewId, { reason, tenantId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'hitl'] });
      onClose();
    },
  });

  return (
    <aside
      role="dialog"
      aria-label={`Review item ${item.reviewId}`}
      className="w-96 border-l border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex flex-col overflow-y-auto"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-800">
        <div>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">{item.skillId}</p>
          <p className="text-xs text-gray-400 font-mono">{item.reviewId}</p>
        </div>
        <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
          ✕
        </button>
      </div>

      {/* Meta */}
      <div className="px-4 py-3 space-y-2 text-sm border-b border-gray-200 dark:border-gray-800">
        <div className="flex justify-between">
          <span className="text-gray-500">Type</span>
          <span className="font-medium">{item.itemType}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-gray-500">Confidence</span>
          <ConfidenceMeter score={item.confidenceScore} />
        </div>
        <div className="flex justify-between">
          <span className="text-gray-500">Submitted</span>
          <span className="text-xs">{new Date(item.createdAt).toLocaleString()}</span>
        </div>
      </div>

      {/* Proposed policy JSON */}
      <div className="px-4 py-3 flex-1">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
          Proposed policy
        </p>
        <pre className="text-xs bg-gray-50 dark:bg-gray-900 rounded p-3 overflow-auto max-h-48 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-800 whitespace-pre-wrap">
          {JSON.stringify(item.proposedVersion, null, 2)}
        </pre>
      </div>

      {/* Actions */}
      {item.status === 'PENDING' && (
        <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-800 space-y-3">
          {mode === null && (
            <div className="flex gap-2">
              <button
                onClick={() => setMode('approve')}
                className="flex-1 px-3 py-2 text-sm rounded-md bg-green-600 hover:bg-green-700 text-white font-medium"
              >
                Approve
              </button>
              <button
                onClick={() => setMode('reject')}
                className="flex-1 px-3 py-2 text-sm rounded-md bg-red-600 hover:bg-red-700 text-white font-medium"
              >
                Reject
              </button>
            </div>
          )}

          {mode === 'approve' && (
            <div className="space-y-2">
              <label className="block text-xs text-gray-500">
                Note (optional)
                <textarea
                  className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
                  rows={2}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Any reviewer notes…"
                />
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => setMode(null)}
                  className="flex-1 px-3 py-1.5 text-sm rounded border border-gray-200 dark:border-gray-700"
                >
                  Back
                </button>
                <button
                  onClick={() => approveMut.mutate()}
                  disabled={approveMut.isPending}
                  className="flex-1 px-3 py-1.5 text-sm rounded bg-green-600 hover:bg-green-700 text-white disabled:opacity-50"
                >
                  {approveMut.isPending ? 'Approving…' : 'Confirm Approve'}
                </button>
              </div>
            </div>
          )}

          {mode === 'reject' && (
            <div className="space-y-2">
              <label className="block text-xs text-gray-500">
                Reason <span className="text-red-500">*</span>
                <textarea
                  className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
                  rows={2}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Why is this policy rejected?"
                />
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => setMode(null)}
                  className="flex-1 px-3 py-1.5 text-sm rounded border border-gray-200 dark:border-gray-700"
                >
                  Back
                </button>
                <button
                  onClick={() => rejectMut.mutate()}
                  disabled={!reason || rejectMut.isPending}
                  className="flex-1 px-3 py-1.5 text-sm rounded bg-red-600 hover:bg-red-700 text-white disabled:opacity-50"
                >
                  {rejectMut.isPending ? 'Rejecting…' : 'Confirm Reject'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {item.status !== 'PENDING' && (
        <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-800 text-sm text-center text-gray-400">
          This item has already been{' '}
          <span className={item.status === 'APPROVED' ? 'text-green-600' : 'text-red-600'}>
            {item.status.toLowerCase()}
          </span>
        </div>
      )}
    </aside>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function HitlReviewPage() {
  const tenantId = 'default';
  const { data: items = [], isLoading, isError } = useQuery({
    queryKey: ['aep', 'hitl', tenantId],
    queryFn: () => listPendingReviews(tenantId),
    refetchInterval: 30_000,
  });

  const [selected, setSelected] = useState<ReviewItem | null>(null);

  const pendingCount = items.filter((i) => i.status === 'PENDING').length;

  return (
    <div className="flex h-full overflow-hidden">
      {/* Main */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-3">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-white">HITL Review Queue</h1>
          {pendingCount > 0 && (
            <span className="px-2 py-0.5 rounded-full bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 text-xs font-medium">
              {pendingCount} pending
            </span>
          )}
        </div>

        {/* List */}
        <div className="flex-1 overflow-auto px-6 py-4">
          {isLoading && <p className="text-center text-gray-400 py-12">Loading review queue…</p>}
          {isError && <p className="text-center text-red-500 py-12">Failed to load HITL queue.</p>}
          {!isLoading && !isError && (
            <div className="space-y-2">
              {items.length === 0 && (
                <p className="text-center text-gray-400 italic py-12">
                  Queue is empty — no items pending review
                </p>
              )}
              {items.map((item) => (
                <button
                  key={item.reviewId}
                  onClick={() => setSelected(item)}
                  className={[
                    'w-full text-left flex items-center gap-4 p-4 rounded-lg border transition-colors',
                    selected?.reviewId === item.reviewId
                      ? 'border-indigo-400 bg-indigo-50 dark:bg-indigo-950'
                      : 'border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 hover:bg-gray-50 dark:hover:bg-gray-800',
                  ].join(' ')}
                >
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-gray-900 dark:text-white truncate">{item.skillId}</p>
                    <p className="text-xs text-gray-400 font-mono mt-0.5">{item.reviewId}</p>
                  </div>
                  <span className="text-xs text-gray-500">{item.itemType}</span>
                  <ConfidenceMeter score={item.confidenceScore} />
                  <span
                    className={[
                      'inline-flex px-2 py-0.5 rounded-full text-xs font-medium',
                      STATUS_COLORS[item.status],
                    ].join(' ')}
                  >
                    {item.status}
                  </span>
                  <span className="text-xs text-gray-400 flex-shrink-0">
                    {new Date(item.createdAt).toLocaleDateString()}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Detail */}
      {selected && (
        <ReviewDetailPanel item={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}
