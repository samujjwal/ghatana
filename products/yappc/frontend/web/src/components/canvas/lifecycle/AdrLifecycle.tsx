/**
 * ADR Lifecycle Manager
 *
 * Implements a full lifecycle state machine for Architecture Decision Records:
 *   DRAFT → IN_REVIEW → ACCEPTED → SUPERSEDED
 *
 * Each transition is recorded in an immutable audit trail.
 * Intended to accompany AdrPanel in the canvas SHAPE phase.
 *
 * @doc.type component
 * @doc.purpose ADR lifecycle state machine with audit trail
 * @doc.layer product
 * @doc.pattern Lifecycle Component
 */

import React, { useCallback, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import {
  Check,
  ChevronRight,
  FilePen,
  History,
  RefreshCw,
  Search,
} from 'lucide-react';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type AdrLifecycleStatus = 'DRAFT' | 'IN_REVIEW' | 'ACCEPTED' | 'SUPERSEDED';

export interface AdrAuditEntry {
  id: string;
  fromStatus: AdrLifecycleStatus;
  toStatus: AdrLifecycleStatus;
  performedBy: string;
  timestamp: string;
  note?: string;
}

export interface AdrLifecycleRecord {
  id: string;
  title: string;
  status: AdrLifecycleStatus;
  auditTrail: AdrAuditEntry[];
}

export interface AdrTransitionHandler {
  /**
   * Called when user transitions an ADR to a new lifecycle status.
   * Implementations should persist to the backend and return the updated record.
   */
  onTransition: (
    adrId: string,
    toStatus: AdrLifecycleStatus,
    note: string
  ) => Promise<AdrLifecycleRecord>;
}

export interface AdrLifecycleProps extends AdrTransitionHandler {
  adr: AdrLifecycleRecord;
  currentUser: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const STATUS_ORDER: AdrLifecycleStatus[] = ['DRAFT', 'IN_REVIEW', 'ACCEPTED', 'SUPERSEDED'];

/** Forward transitions allowed from each status */
const ALLOWED_TRANSITIONS: Record<AdrLifecycleStatus, AdrLifecycleStatus[]> = {
  DRAFT: ['IN_REVIEW'],
  IN_REVIEW: ['ACCEPTED', 'DRAFT'],
  ACCEPTED: ['SUPERSEDED'],
  SUPERSEDED: [],
};

const STATUS_LABEL: Record<AdrLifecycleStatus, string> = {
  DRAFT: 'Draft',
  IN_REVIEW: 'In Review',
  ACCEPTED: 'Accepted',
  SUPERSEDED: 'Superseded',
};

const STATUS_COLOR: Record<AdrLifecycleStatus, string> = {
  DRAFT: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-200',
  IN_REVIEW: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
  ACCEPTED: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-200',
  SUPERSEDED: 'bg-surface-muted text-fg-muted dark:bg-surface dark:text-fg-muted line-through',
};

const STATUS_ICON: Record<AdrLifecycleStatus, React.ReactElement> = {
  DRAFT: <FilePen className="h-3.5 w-3.5" aria-hidden="true" />,
  IN_REVIEW: <Search className="h-3.5 w-3.5" aria-hidden="true" />,
  ACCEPTED: <Check className="h-3.5 w-3.5" aria-hidden="true" />,
  SUPERSEDED: <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />,
};

const TRANSITION_BUTTON_LABEL: Record<string, string> = {
  'DRAFT→IN_REVIEW': 'Submit for Review',
  'IN_REVIEW→ACCEPTED': 'Accept Decision',
  'IN_REVIEW→DRAFT': 'Send Back to Draft',
  'ACCEPTED→SUPERSEDED': 'Mark Superseded',
};

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

interface StatusStepperProps {
  current: AdrLifecycleStatus;
}

const StatusStepper: React.FC<StatusStepperProps> = ({ current }) => {
  const forwardSteps: AdrLifecycleStatus[] = ['DRAFT', 'IN_REVIEW', 'ACCEPTED', 'SUPERSEDED'];
  const currentIndex = STATUS_ORDER.indexOf(current);
  return (
    <Box
      className="flex items-center gap-1 overflow-x-auto pb-1"
      role="progressbar"
      aria-label="ADR lifecycle progress"
      aria-valuenow={currentIndex}
      aria-valuemin={0}
      aria-valuemax={STATUS_ORDER.length - 1}
    >
      {forwardSteps.map((step, idx) => {
        const stepIndex = STATUS_ORDER.indexOf(step);
        const isActive = step === current;
        const isPast = stepIndex < currentIndex;
        const stateClass = isActive
          ? 'bg-primary text-white shadow'
          : isPast
          ? 'bg-emerald-500 text-white'
          : 'bg-grey-200 text-grey-500 dark:bg-grey-700 dark:text-grey-400';
        return (
          <React.Fragment key={step}>
            <Box
              className={[
                'flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap transition-colors',
                stateClass,
              ].join(' ')}
            >
              {STATUS_ICON[step]}
              {STATUS_LABEL[step]}
            </Box>
            {idx < forwardSteps.length - 1 && (
              <ChevronRight
                className="h-3.5 w-3.5 shrink-0 text-grey-400"
                aria-hidden="true"
              />
            )}
          </React.Fragment>
        );
      })}
    </Box>
  );
};

interface TransitionFormProps {
  toStatus: AdrLifecycleStatus;
  onConfirm: (note: string) => void;
  onCancel: () => void;
  isBusy: boolean;
}

const TransitionForm: React.FC<TransitionFormProps> = ({
  toStatus,
  onConfirm,
  onCancel,
  isBusy,
}) => {
  const [note, setNote] = useState('');
  return (
    <Box
      className="space-y-2 rounded-md border border-divider bg-grey-50 dark:bg-grey-900 p-3"
      data-testid="adr-transition-form"
    >
      <Typography className="text-sm font-semibold">
        Transition to{' '}
        <Chip
          label={STATUS_LABEL[toStatus]}
          size="sm"
          className={STATUS_COLOR[toStatus]}
        />
      </Typography>
      <label className="block text-xs text-text-secondary" htmlFor="adr-transition-note">
        Note (optional)
      </label>
      <textarea
        id="adr-transition-note"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        rows={2}
        className="w-full rounded border border-divider bg-white dark:bg-grey-800 px-2 py-1 text-sm text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary resize-none"
        placeholder="Reason for this transition…"
        aria-label="Transition note"
      />
      <Box className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isBusy}
          className="rounded px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-grey-200 dark:hover:bg-grey-700 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => onConfirm(note)}
          disabled={isBusy}
          className="rounded bg-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-primary/90 disabled:opacity-50"
          aria-busy={isBusy}
        >
          {isBusy ? 'Saving…' : 'Confirm'}
        </button>
      </Box>
    </Box>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AdrLifecycle
 *
 * Manages ADR lifecycle state with explicit transitions and an immutable audit trail.
 *
 * @example
 * ```tsx
 * <AdrLifecycle
 *   adr={adrRecord}
 *   currentUser="alice"
 *   onTransition={async (id, toStatus, note) => { ... return updatedRecord; }}
 * />
 * ```
 */
export const AdrLifecycle: React.FC<AdrLifecycleProps> = ({
  adr: initialAdr,
  currentUser,
  onTransition,
}) => {
  const [adr, setAdr] = useState<AdrLifecycleRecord>(initialAdr);
  const [pendingTransition, setPendingTransition] = useState<AdrLifecycleStatus | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showAudit, setShowAudit] = useState(false);

  const allowedNext = ALLOWED_TRANSITIONS[adr.status];

  const handleConfirmTransition = useCallback(
    async (note: string) => {
      if (!pendingTransition) return;
      setIsBusy(true);
      setError(null);
      try {
        const updated = await onTransition(adr.id, pendingTransition, note);
        setAdr(updated);
        setPendingTransition(null);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Transition failed';
        setError(message);
      } finally {
        setIsBusy(false);
      }
    },
    [adr.id, onTransition, pendingTransition]
  );

  return (
    <Card data-testid="adr-lifecycle">
      <CardContent className="space-y-4 p-4">
        {/* Header */}
        <Box className="flex items-center justify-between gap-2 flex-wrap">
          <Box className="flex items-center gap-2">
            <Typography className="font-semibold">{adr.title}</Typography>
            <Chip
              label={STATUS_LABEL[adr.status]}
              size="sm"
              className={STATUS_COLOR[adr.status]}
            />
          </Box>
          <button
            type="button"
            onClick={() => setShowAudit((prev) => !prev)}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
            aria-expanded={showAudit}
            aria-label="Toggle audit trail"
          >
            <History className="h-3.5 w-3.5" aria-hidden="true" />
            Audit ({adr.auditTrail.length})
          </button>
        </Box>

        {/* Stepper */}
        <StatusStepper current={adr.status} />

        {/* Transition buttons */}
        {allowedNext.length > 0 && !pendingTransition && (
          <Box className="flex flex-wrap gap-2">
            {allowedNext.map((target) => {
              const key = `${adr.status}→${target}` as keyof typeof TRANSITION_BUTTON_LABEL;
              const label = TRANSITION_BUTTON_LABEL[key] ?? `Move to ${STATUS_LABEL[target]}`;
              return (
                <button
                  key={target}
                  type="button"
                  onClick={() => setPendingTransition(target)}
                  className="rounded border border-divider px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
                  data-testid={`adr-transition-${target}`}
                >
                  {label}
                </button>
              );
            })}
          </Box>
        )}

        {/* Transition form */}
        {pendingTransition && (
          <TransitionForm
            toStatus={pendingTransition}
            onConfirm={handleConfirmTransition}
            onCancel={() => setPendingTransition(null)}
            isBusy={isBusy}
          />
        )}

        {/* Error */}
        {error && (
          <Typography className="text-xs text-destructive dark:text-destructive" role="alert">
            {error}
          </Typography>
        )}

        {/* Audit trail */}
        {showAudit && (
          <Box className="space-y-2" data-testid="adr-audit-trail">
            <Typography className="text-sm font-semibold text-text-secondary">
              Audit Trail
            </Typography>
            {adr.auditTrail.length === 0 && (
              <Typography className="text-xs text-text-secondary">
                No transitions recorded yet.
              </Typography>
            )}
            {[...adr.auditTrail].reverse().map((entry) => (
              <Box
                key={entry.id}
                className="rounded-md border border-divider bg-grey-50 dark:bg-grey-900 px-3 py-2 text-xs"
              >
                <Box className="flex items-center gap-2 flex-wrap">
                  <Chip
                    label={STATUS_LABEL[entry.fromStatus]}
                    size="sm"
                    className={STATUS_COLOR[entry.fromStatus]}
                  />
                  <span aria-hidden="true">→</span>
                  <Chip
                    label={STATUS_LABEL[entry.toStatus]}
                    size="sm"
                    className={STATUS_COLOR[entry.toStatus]}
                  />
                  <Typography className="ml-auto text-text-secondary">
                    {entry.performedBy} · {new Date(entry.timestamp).toLocaleString()}
                  </Typography>
                </Box>
                {entry.note && (
                  <Typography className="mt-1 text-text-secondary italic">
                    "{entry.note}"
                  </Typography>
                )}
              </Box>
            ))}
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

export default AdrLifecycle;
