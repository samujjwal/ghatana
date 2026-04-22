/**
 * BuilderDiagnosticsPanel
 *
 * Developer diagnostics panel for the YAPPC UI builder. Shows live builder
 * state including autosave status, AI review count, collaboration session info,
 * preview policy, and document/governance status. Intended for dev-only panels
 * and debug overlays — not for production-facing UI.
 *
 * All state is derived from builder atoms in `@yappc/state`.
 *
 * @doc.type component
 * @doc.purpose Developer diagnostics panel surfacing live builder state
 * @doc.layer product
 * @doc.pattern Diagnostic Component
 */

import React from 'react';
import { useAtomValue } from 'jotai';

import {
  builderActiveDocumentIdAtom,
  builderAutosaveStatusAtom,
  builderLastSavedAtAtom,
  builderAIPendingReviewCountAtom,
  builderPreviewModeAtom,
  builderCollabSessionAtom,
  builderHasUnsavedChangesAtom,
  type AutosaveStatus,
} from '@yappc/state';

import { BuilderPreviewPolicyChip } from './BuilderPreviewPolicyChip';

// ─── Row helpers ─────────────────────────────────────────────────────────────

interface DiagRowProps {
  label: string;
  value: React.ReactNode;
}

const DiagRow: React.FC<DiagRowProps> = ({ label, value }) => (
  <div className="flex items-start gap-2 py-1 border-b border-dashed border-gray-700 last:border-0">
    <span className="min-w-[140px] text-[11px] text-gray-400 font-mono">{label}</span>
    <span className="text-[11px] text-gray-100 font-mono break-all">{value}</span>
  </div>
);

// ─── Status badge ─────────────────────────────────────────────────────────────

const AUTOSAVE_COLORS: Record<AutosaveStatus, string> = {
  idle: '#6b7280',
  pending: '#d97706',
  saving: '#3b82f6',
  saved: '#16a34a',
  error: '#dc2626',
};

interface AutosaveBadgeProps {
  status: AutosaveStatus;
  hasUnsaved: boolean;
}

const AutosaveBadge: React.FC<AutosaveBadgeProps> = ({ status, hasUnsaved }) => (
  <span
    style={{ color: AUTOSAVE_COLORS[status] }}
    className="font-mono"
  >
    {status}{hasUnsaved ? ' (dirty)' : ''}
  </span>
);

// ─── Panel ────────────────────────────────────────────────────────────────────

export interface BuilderDiagnosticsPanelProps {
  /** Additional class name applied to the panel root. */
  className?: string;
  /** Panel title shown in the header. Defaults to "Builder Diagnostics". */
  title?: string;
}

/**
 * BuilderDiagnosticsPanel renders a dark-themed diagnostics card showing live
 * builder atom state. Intended for use inside developer overlays, dev-mode
 * sidebars, or the YAPPC debug shell. Reads all state from `@yappc/state`
 * builder atoms — no props required for data.
 */
export const BuilderDiagnosticsPanel: React.FC<BuilderDiagnosticsPanelProps> = ({
  className,
  title = 'Builder Diagnostics',
}) => {
  const activeDocumentId = useAtomValue(builderActiveDocumentIdAtom);
  const autosaveStatus = useAtomValue(builderAutosaveStatusAtom);
  const lastSavedAt = useAtomValue(builderLastSavedAtAtom);
  const aiPendingCount = useAtomValue(builderAIPendingReviewCountAtom);
  const previewMode = useAtomValue(builderPreviewModeAtom);
  const collabSession = useAtomValue(builderCollabSessionAtom);
  const hasUnsaved = useAtomValue(builderHasUnsavedChangesAtom);

  return (
    <aside
      data-testid="builder-diagnostics-panel"
      role="complementary"
      aria-label={title}
      className={[
        'rounded-md border border-gray-700 bg-gray-900 p-3 text-gray-100',
        className ?? '',
      ].join(' ').trim()}
    >
      {/* Header */}
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">
          {title}
        </span>
        <span className="text-[10px] text-gray-600 font-mono">dev only</span>
      </div>

      {/* Document */}
      <DiagRow
        label="Active document"
        value={activeDocumentId ?? <span className="text-gray-600">none</span>}
      />

      {/* Autosave */}
      <DiagRow
        label="Autosave"
        value={<AutosaveBadge status={autosaveStatus} hasUnsaved={hasUnsaved} />}
      />
      <DiagRow
        label="Last saved at"
        value={
          lastSavedAt
            ? new Date(lastSavedAt).toLocaleTimeString()
            : <span className="text-gray-600">—</span>
        }
      />

      {/* AI review */}
      <DiagRow
        label="AI pending reviews"
        value={
          <span style={{ color: aiPendingCount > 0 ? '#d97706' : undefined }}>
            {aiPendingCount}
          </span>
        }
      />

      {/* Preview policy */}
      <DiagRow
        label="Preview policy"
        value={
          previewMode
            ? <BuilderPreviewPolicyChip showDescription />
            : <span className="text-gray-600">none</span>
        }
      />

      {/* Collaboration */}
      <DiagRow
        label="Collab session"
        value={
          collabSession
            ? (
              <span>
                {collabSession.sessionId.slice(0, 8)}…{' '}
                <span style={{ color: collabSession.connected ? '#16a34a' : '#d97706' }}>
                  {collabSession.connected ? `online (${collabSession.participantCount})` : 'reconnecting'}
                </span>
              </span>
            )
            : <span className="text-gray-600">none</span>
        }
      />
    </aside>
  );
};

BuilderDiagnosticsPanel.displayName = 'BuilderDiagnosticsPanel';
