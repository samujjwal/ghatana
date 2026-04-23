/**
 * HitlReviewPage — Human-In-The-Loop review queue for AEP learning decisions.
 *
 * Features:
 *   - List pending review items with type, skill, confidence
 *   - Open detail view with proposed policy JSON diff
 *   - Approve (with optional note) / Reject (with required reason)
 *   - Color-coded confidence score via ConfidenceBadge
 *   - Live updates via SSE through useHitlQueue
 *
 * @doc.type page
 * @doc.purpose AEP HITL review queue
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { useHitlQueue, useApproveItem, useRejectItem } from '@/hooks/useHitlQueue';
import { ReviewCard } from '@/components/hitl/ReviewCard';
import type { ReviewItem } from '@/api/aep.api';
import { Button } from '@ghatana/design-system';
import { TextArea } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';

// ─── PolicyDiff ──────────────────────────────────────────────────────

/** Renders a proposed policy object as a structured key-value diff table. */
function PolicyDiff({ policy }: { policy: Record<string, unknown> }) {
  const entries = Object.entries(policy);

  if (entries.length === 0) {
    return (
      <p className="text-xs italic text-gray-400 dark:text-gray-600">No policy fields</p>
    );
  }

  return (
    <div className="rounded border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-800 overflow-hidden">
      {entries.map(([key, value]) => {
        const isNested = value !== null && typeof value === 'object' && !Array.isArray(value);
        const display = isNested
          ? JSON.stringify(value, null, 2)
          : Array.isArray(value)
            ? value.join(', ')
            : String(value ?? '—');
        return (
          <div key={key} className="flex items-start gap-2 px-3 py-1.5 bg-white dark:bg-gray-900">
            <span className="flex-shrink-0 w-32 text-xs font-mono text-gray-500 dark:text-gray-400 truncate pt-0.5">
              {key}
            </span>
            {isNested ? (
              <pre className="flex-1 text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-24">
                {display}
              </pre>
            ) : (
              <span className="flex-1 text-xs text-gray-800 dark:text-gray-200 break-all">{display}</span>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ─── Detail / Action Panel ───────────────────────────────────────────

function ReviewDetailPanel({ item, onClose }: { item: ReviewItem; onClose: () => void }) {
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [mode, setMode] = useState<'approve' | 'reject' | null>(null);

  const approveMut = useApproveItem();
  const rejectMut = useRejectItem();

  function handleApprove() {
    approveMut.mutate({ reviewId: item.reviewId, note: note || undefined }, { onSuccess: onClose });
  }

  function handleReject() {
    rejectMut.mutate({ reviewId: item.reviewId, reason }, { onSuccess: onClose });
  }

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
        <Button onClick={onClose} aria-label="Close" variant="ghost" className="text-gray-400 hover:text-gray-600 p-1">
          ✕
        </Button>
      </div>

      {/* Meta */}
      <div className="px-4 py-3 space-y-2 text-sm border-b border-gray-200 dark:border-gray-800">
        <div className="flex justify-between">
          <span className="text-gray-500">Type</span>
          <span className="font-medium">{item.itemType}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-500">Status</span>
          <span className="font-medium">{item.status}</span>
        </div>
        {item.confidenceScore != null && (
          <div className="flex justify-between">
            <span className="text-gray-500">Confidence</span>
            <span className="font-medium">{Math.round(item.confidenceScore * 100)}%</span>
          </div>
        )}
        <div className="flex justify-between">
          <span className="text-gray-500">Submitted</span>
          <span className="text-xs">{new Date(item.createdAt).toLocaleString()}</span>
        </div>
      </div>

      {/* Proposed policy — human-readable diff */}
      <div className="px-4 py-3 flex-1">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
          Proposed policy
        </p>
        <PolicyDiff policy={item.proposedVersion} />
      </div>

      {/* Actions */}
      {item.status === 'PENDING' && (
        <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-800 space-y-3">
          {mode === null && (
            <div className="flex gap-2">
              <Button
                onClick={() => setMode('approve')}
                variant="primary"
                className="flex-1 px-3 py-2 text-sm font-medium"
                style={{ backgroundColor: '#16a34a' }}
              >
                Approve
              </Button>
              <Button
                onClick={() => setMode('reject')}
                variant="primary"
                className="flex-1 px-3 py-2 text-sm font-medium"
                style={{ backgroundColor: '#dc2626' }}
              >
                Reject
              </Button>
            </div>
          )}

          {mode === 'approve' && (
            <div className="space-y-2">
              <label className="block text-xs text-gray-500">
                Note (optional)
                <TextArea
                  className="mt-1 block w-full text-sm"
                  rows={2}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Any reviewer notes…"
                />
              </label>
              <div className="flex gap-2">
                <Button
                  onClick={() => setMode(null)}
                  variant="secondary"
                  className="flex-1 px-3 py-1.5 text-sm"
                >
                  Back
                </Button>
                <Button
                  onClick={handleApprove}
                  disabled={approveMut.isPending}
                  variant="primary"
                  className="flex-1 px-3 py-1.5 text-sm"
                  style={{ backgroundColor: '#16a34a' }}
                >
                  {approveMut.isPending ? 'Approving…' : 'Confirm Approve'}
                </Button>
              </div>
            </div>
          )}

          {mode === 'reject' && (
            <div className="space-y-2">
              <label className="block text-xs text-gray-500">
                Reason <span className="text-red-500">*</span>
                <TextArea
                  className="mt-1 block w-full text-sm"
                  rows={2}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Why is this policy rejected?"
                />
              </label>
              <div className="flex gap-2">
                <Button
                  onClick={() => setMode(null)}
                  variant="secondary"
                  className="flex-1 px-3 py-1.5 text-sm"
                >
                  Back
                </Button>
                <Button
                  onClick={handleReject}
                  disabled={!reason || rejectMut.isPending}
                  variant="primary"
                  className="flex-1 px-3 py-1.5 text-sm"
                  style={{ backgroundColor: '#dc2626' }}
                >
                  {rejectMut.isPending ? 'Rejecting…' : 'Confirm Reject'}
                </Button>
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
  const { data: items = [], isLoading, isError } = useHitlQueue();
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
          {isLoading && (
            <EmptyState title="Loading review queue…" description="Fetching HITL items." />
          )}
          {isError && (
            <ErrorState
              title="Failed to load HITL queue"
              onRetry={() => window.location.reload()}
            />
          )}
          {!isLoading && !isError && (
            <div className="space-y-2">
              {items.length === 0 && (
                <EmptyState
                  title="Queue is empty"
                  description="No items are pending review at the moment."
                />
              )}
              {items.map((item) => (
                <ReviewCard
                  key={item.reviewId}
                  item={item}
                  isSelected={selected?.reviewId === item.reviewId}
                  onClick={() => setSelected(item)}
                />
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
