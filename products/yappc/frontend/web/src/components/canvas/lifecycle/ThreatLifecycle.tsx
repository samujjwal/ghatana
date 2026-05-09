/**
 * Threat Lifecycle Manager
 *
 * Adds an action lifecycle for each threat identified in the STRIDE threat model.
 * Allowed actions per threat:
 *   IDENTIFIED → MITIGATED | ACCEPTED | TRANSFERRED | AVOIDED
 *
 * Each state transition emits an immutable audit entry.
 *
 * @doc.type component
 * @doc.purpose Threat-model action lifecycle with audit trail
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
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronUp,
  History,
  ShieldCheck,
  ShieldOff,
  ShieldAlert,
  Shuffle,
} from 'lucide-react';
import { Button } from '../../ui/Button';
import { Textarea } from '../../ui/Textarea';
import { useI18n } from '../../../i18n/I18nProvider';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type ThreatDispositionStatus =
  | 'IDENTIFIED'
  | 'MITIGATED'
  | 'ACCEPTED'
  | 'TRANSFERRED'
  | 'AVOIDED';

export type ThreatSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ThreatStrideCategory =
  | 'spoofing'
  | 'tampering'
  | 'repudiation'
  | 'info_disclosure'
  | 'denial_of_service'
  | 'elevation';

export interface ThreatAuditEntry {
  id: string;
  fromStatus: ThreatDispositionStatus;
  toStatus: ThreatDispositionStatus;
  performedBy: string;
  timestamp: string;
  note?: string;
}

export interface ThreatRecord {
  id: string;
  asset: string;
  category: ThreatStrideCategory;
  description: string;
  severity: ThreatSeverity;
  status: ThreatDispositionStatus;
  auditTrail: ThreatAuditEntry[];
}

export interface ThreatLifecycleProps {
  threats: ThreatRecord[];
  currentUser: string;
  /**
   * Called when a threat's disposition changes.
   * Implementations should persist and return the updated threat record.
   */
  onDispose: (
    threatId: string,
    toStatus: ThreatDispositionStatus,
    note: string
  ) => Promise<ThreatRecord>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const FINAL_STATUSES: ThreatDispositionStatus[] = [
  'MITIGATED',
  'ACCEPTED',
  'TRANSFERRED',
  'AVOIDED',
];

const STATUS_LABEL: Record<ThreatDispositionStatus, string> = {
  IDENTIFIED: 'Identified',
  MITIGATED: 'Mitigated',
  ACCEPTED: 'Accepted',
  TRANSFERRED: 'Transferred',
  AVOIDED: 'Avoided',
};

const STATUS_COLOR: Record<ThreatDispositionStatus, string> = {
  IDENTIFIED: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
  MITIGATED: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
  ACCEPTED: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
  TRANSFERRED: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
  AVOIDED: 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted',
};

const STATUS_ICON: Record<ThreatDispositionStatus, React.ReactElement> = {
  IDENTIFIED: <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />,
  MITIGATED: <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />,
  ACCEPTED: <Check className="h-3.5 w-3.5" aria-hidden="true" />,
  TRANSFERRED: <Shuffle className="h-3.5 w-3.5" aria-hidden="true" />,
  AVOIDED: <ShieldOff className="h-3.5 w-3.5" aria-hidden="true" />,
};

const SEVERITY_COLOR: Record<ThreatSeverity, string> = {
  LOW: 'bg-surface-muted text-fg-muted',
  MEDIUM: 'bg-warning-bg text-warning-color',
  HIGH: 'bg-warning-bg text-warning-color',
  CRITICAL: 'bg-destructive-bg text-destructive',
};

const STRIDE_ABBREV: Record<ThreatStrideCategory, string> = {
  spoofing: 'SP',
  tampering: 'TA',
  repudiation: 'RE',
  info_disclosure: 'ID',
  denial_of_service: 'DOS',
  elevation: 'EP',
};

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

interface ThreatDispositionFormProps {
  threatId: string;
  onConfirm: (toStatus: ThreatDispositionStatus, note: string) => void;
  onCancel: () => void;
  isBusy: boolean;
}

const ThreatDispositionForm: React.FC<ThreatDispositionFormProps> = ({
  threatId,
  onConfirm,
  onCancel,
  isBusy,
}) => {
  const { t } = useI18n();
  const [selected, setSelected] = useState<ThreatDispositionStatus>('MITIGATED');
  const [note, setNote] = useState('');

  return (
    <Box
      className="mt-2 space-y-2 rounded-md border border-divider bg-surface-muted dark:bg-surface-muted p-3"
      data-testid={`threat-disposition-form-${threatId}`}
    >
      <Typography className="text-xs font-semibold">Dispose as:</Typography>
      <Box className="flex flex-wrap gap-2">
        {FINAL_STATUSES.map((status) => (
          <Button
            key={status}
            type="button"
            variant="ghost"
            size="small"
            onClick={() => setSelected(status)}
            className={[
              'min-h-0 gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium transition-all',
              selected === status
                ? STATUS_COLOR[status] + ' ring-2 ring-info-border ring-offset-1'
                : 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted hover:bg-surface-muted',
            ].join(' ')}
            aria-pressed={selected === status}
          >
            {STATUS_ICON[status]}
            {STATUS_LABEL[status]}
          </Button>
        ))}
      </Box>
      <label htmlFor={`threat-note-${threatId}`} className="block text-xs text-text-secondary">
        Justification (optional)
      </label>
      <Textarea
        id={`threat-note-${threatId}`}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        rows={2}
        fullWidth
        resize="none"
        className="w-full rounded border border-divider bg-bg-paper dark:bg-bg-paper px-2 py-1 text-sm text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-info-border resize-none"
        placeholder={t('canvas.threatLifecycle.note')}
      />
      <Box className="flex justify-end gap-2">
        <Button
          type="button"
          variant="ghost"
          size="small"
          onClick={onCancel}
          disabled={isBusy}
          className="min-h-0 rounded px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-muted dark:hover:bg-surface-muted disabled:opacity-50"
        >
          Cancel
        </Button>
        <Button
          type="button"
          variant="solid"
          size="small"
          onClick={() => onConfirm(selected, note)}
          disabled={isBusy}
          className="min-h-0 rounded bg-info-color px-3 py-1.5 text-xs font-semibold text-white hover:bg-info-color/90 disabled:opacity-50"
          aria-busy={isBusy}
        >
          {isBusy ? 'Saving…' : 'Confirm'}
        </Button>
      </Box>
    </Box>
  );
};

interface ThreatRowProps {
  threat: ThreatRecord;
  onDispose: ThreatLifecycleProps['onDispose'];
  currentUser: string;
}

const ThreatRow: React.FC<ThreatRowProps> = ({ threat: initialThreat, onDispose, currentUser: _currentUser }) => {
  const [threat, setThreat] = useState<ThreatRecord>(initialThreat);
  const [isDisposing, setIsDisposing] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [showAudit, setShowAudit] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOpen = threat.status === 'IDENTIFIED';

  const handleConfirm = useCallback(
    async (toStatus: ThreatDispositionStatus, note: string) => {
      setIsBusy(true);
      setError(null);
      try {
        const updated = await onDispose(threat.id, toStatus, note);
        setThreat(updated);
        setIsDisposing(false);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Disposition failed');
      } finally {
        setIsBusy(false);
      }
    },
    [onDispose, threat.id]
  );

  return (
    <Box
      className="rounded-md border border-divider overflow-hidden"
      data-testid={`threat-row-${threat.id}`}
    >
      {/* Row header */}
      <Box className="flex items-start gap-3 px-3 py-2.5">
        <Box className="mt-0.5 shrink-0">
          <Chip
            label={STRIDE_ABBREV[threat.category]}
            size="sm"
            className="bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color font-mono text-xs"
            title={threat.category}
          />
        </Box>
        <Box className="flex-1 min-w-0">
          <Typography className="text-sm font-medium line-clamp-2">{threat.description}</Typography>
          <Box className="mt-1 flex items-center gap-2 flex-wrap">
            <Chip label={threat.asset} size="sm" className="bg-surface-muted text-fg-muted text-xs" />
            <Chip
              label={threat.severity}
              size="sm"
              className={SEVERITY_COLOR[threat.severity] + ' text-xs'}
            />
          </Box>
        </Box>
        <Box className="shrink-0 flex items-center gap-2">
          <Box className={['flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium', STATUS_COLOR[threat.status]].join(' ')}>
            {STATUS_ICON[threat.status]}
            {STATUS_LABEL[threat.status]}
          </Box>
          {threat.auditTrail.length > 0 && (
            <Button
              type="button"
              variant="ghost"
              size="small"
              onClick={() => setShowAudit((prev) => !prev)}
              className="min-h-0 rounded p-1 text-text-secondary hover:bg-surface-muted dark:hover:bg-surface-muted transition-colors"
              aria-label={t('canvas.threatLifecycle.auditTrail')}
              aria-expanded={showAudit}
            >
              <History className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          )}
          {showAudit ? (
            <ChevronUp className="h-3.5 w-3.5 text-text-secondary" aria-hidden="true" />
          ) : null}
        </Box>
      </Box>

      {/* Dispose button (only for open threats) */}
      {isOpen && !isDisposing && (
        <Box className="border-t border-divider bg-surface-muted dark:bg-surface-muted px-3 py-2">
          <Button
            type="button"
            variant="ghost"
            size="small"
            onClick={() => setIsDisposing(true)}
            className="min-h-0 gap-1 px-0 py-0 text-xs font-medium text-info-color hover:underline"
            data-testid={`threat-dispose-btn-${threat.id}`}
          >
            <ShieldAlert className="h-3.5 w-3.5" aria-hidden="true" />
            Record disposition
          </Button>
        </Box>
      )}

      {/* Disposition form */}
      {isOpen && isDisposing && (
        <Box className="border-t border-divider px-3 pb-3">
          <ThreatDispositionForm
            threatId={threat.id}
            onConfirm={handleConfirm}
            onCancel={() => setIsDisposing(false)}
            isBusy={isBusy}
          />
        </Box>
      )}

      {/* Error */}
      {error && (
        <Box className="border-t border-divider px-3 py-2">
          <Typography className="text-xs text-destructive dark:text-destructive" role="alert">
            {error}
          </Typography>
        </Box>
      )}

      {/* Audit trail */}
      {showAudit && threat.auditTrail.length > 0 && (
        <Box className="space-y-1 border-t border-divider bg-surface-muted dark:bg-surface-muted px-3 py-2" data-testid={`threat-audit-${threat.id}`}>
          {[...threat.auditTrail].reverse().map((entry) => (
            <Box key={entry.id} className="text-xs text-text-secondary">
              <Box className="flex items-center gap-1 flex-wrap">
                <span className={['rounded-full px-1.5 py-0.5', STATUS_COLOR[entry.fromStatus]].join(' ')}>{STATUS_LABEL[entry.fromStatus]}</span>
                <span aria-hidden="true">→</span>
                <span className={['rounded-full px-1.5 py-0.5', STATUS_COLOR[entry.toStatus]].join(' ')}>{STATUS_LABEL[entry.toStatus]}</span>
                <span className="ml-auto">
                  {entry.performedBy} · {new Date(entry.timestamp).toLocaleString()}
                </span>
              </Box>
              {entry.note && <Typography className="mt-0.5 italic">"{entry.note}"</Typography>}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ThreatLifecycle
 *
 * Renders a list of identified threats with individual disposition controls.
 * Each threat can be independently mitigated, accepted, transferred, or avoided.
 *
 * @example
 * ```tsx
 * <ThreatLifecycle
 *   threats={threatRecords}
 *   currentUser="alice"
 *   onDispose={async (id, status, note) => { ... return updatedThreat; }}
 * />
 * ```
 */
export const ThreatLifecycle: React.FC<ThreatLifecycleProps> = ({
  threats,
  currentUser,
  onDispose,
}) => {
  const openCount = threats.filter((t) => t.status === 'IDENTIFIED').length;

  return (
    <Card data-testid="threat-lifecycle">
      <CardContent className="space-y-3 p-4">
        <Box className="flex items-center justify-between gap-2">
          <Typography className="font-semibold">Threat Lifecycle</Typography>
          <Box className="flex gap-2">
            {openCount > 0 && (
              <Chip
                label={`${openCount} open`}
                size="sm"
                className="bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive"
              />
            )}
            <Chip
              label={`${threats.length} total`}
              size="sm"
              className="bg-surface-muted text-fg-muted"
            />
          </Box>
        </Box>

        {threats.length === 0 && (
          <Typography className="text-sm text-text-secondary">
            No threats identified yet.
          </Typography>
        )}

        <Box className="space-y-2">
          {threats.map((threat) => (
            <ThreatRow
              key={threat.id}
              threat={threat}
              onDispose={onDispose}
              currentUser={currentUser}
            />
          ))}
        </Box>
      </CardContent>
    </Card>
  );
};

export default ThreatLifecycle;
