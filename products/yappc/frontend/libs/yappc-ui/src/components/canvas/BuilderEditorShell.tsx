/**
 * BuilderEditorShell
 *
 * Product-facing editor shell for the YAPPC UI builder. Wraps the canvas
 * editing surface and surfaces save state, sync state, AI activity,
 * collab session info, and preview mode in a persistent status bar.
 *
 * Consumes builder atoms from `@yappc/state`. Does not own the document model
 * itself — callers supply the editing surface as `children`.
 *
 * @doc.type component
 * @doc.purpose YAPPC builder editor shell with integrated status bar
 * @doc.layer product
 * @doc.pattern Wrapper Component
 */

import {
  Cloud,
  CloudOff,
  CloudUpload,
  Users,
  Cpu,
  Eye,
  AlertCircle,
  CheckCircle2,
  Clock,
  Loader2,
} from 'lucide-react';
import { useAtomValue } from 'jotai';
import React from 'react';

import { cn } from '@ghatana/platform-utils';

import {
  builderAutosaveStatusAtom,
  builderAIPendingReviewCountAtom,
  builderPreviewModeAtom,
  builderCollabSessionAtom,
  builderHasUnsavedChangesAtom,
  type AutosaveStatus,
  type BuilderCollabSession,
} from '@yappc/state';
import type { PreviewMode } from '@ghatana/ui-builder';

// ─── Status chip ──────────────────────────────────────────────────────────────

interface StatusChipProps {
  label: string;
  title: string;
  icon: React.ReactElement;
  variant?: 'default' | 'warning' | 'error';
  className?: string;
}

const StatusChip: React.FC<StatusChipProps> = ({
  label,
  title,
  icon,
  variant = 'default',
  className,
}) => (
  <span
    title={title}
    aria-label={title}
    className={cn(
      'inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium',
      variant === 'error' && 'bg-red-100 text-red-700',
      variant === 'warning' && 'bg-yellow-100 text-yellow-700',
      variant === 'default' && 'bg-secondary text-secondary-foreground',
      className,
    )}
  >
    {icon}
    {label}
  </span>
);

// ─── Autosave indicator ──────────────────────────────────────────────────────

interface AutosaveIndicatorProps {
  status: AutosaveStatus;
}

const AUTOSAVE_CONFIG: Record<
  AutosaveStatus,
  { label: string; icon: React.ReactElement; variant: 'default' | 'warning' | 'error' }
> = {
  idle: {
    label: 'Saved',
    icon: <CheckCircle2 size={12} aria-hidden />,
    variant: 'default',
  },
  pending: {
    label: 'Unsaved changes',
    icon: <Clock size={12} aria-hidden />,
    variant: 'warning',
  },
  saving: {
    label: 'Saving…',
    icon: <Loader2 size={12} className="animate-spin" aria-hidden />,
    variant: 'default',
  },
  saved: {
    label: 'Saved',
    icon: <CheckCircle2 size={12} aria-hidden />,
    variant: 'default',
  },
  error: {
    label: 'Save failed',
    icon: <AlertCircle size={12} aria-hidden />,
    variant: 'error',
  },
};

const AutosaveIndicator: React.FC<AutosaveIndicatorProps> = ({ status }) => {
  const config = AUTOSAVE_CONFIG[status];
  return (
    <StatusChip
      label={config.label}
      title={`Autosave: ${config.label}`}
      icon={config.icon}
      variant={config.variant}
    />
  );
};

// ─── Collab indicator ────────────────────────────────────────────────────────

interface CollabIndicatorProps {
  session: BuilderCollabSession | null;
}

const CollabIndicator: React.FC<CollabIndicatorProps> = ({ session }) => {
  if (!session) {
    return (
      <StatusChip
        label="Offline"
        title="Collaboration offline"
        icon={<CloudOff size={12} aria-hidden />}
      />
    );
  }

  if (!session.connected) {
    return (
      <StatusChip
        label="Reconnecting"
        title="Reconnecting to collaboration session"
        icon={<CloudUpload size={12} className="animate-pulse" aria-hidden />}
        variant="warning"
      />
    );
  }

  const count = session.participantCount;
  return (
    <StatusChip
      label={String(count)}
      title={`${count} collaborator${count !== 1 ? 's' : ''} online`}
      icon={
        <>
          <Cloud size={12} aria-hidden />
          <Users size={12} aria-hidden />
        </>
      }
    />
  );
};

// ─── AI review indicator ─────────────────────────────────────────────────────

interface AIReviewIndicatorProps {
  pendingCount: number;
}

const AIReviewIndicator: React.FC<AIReviewIndicatorProps> = ({ pendingCount }) => {
  if (pendingCount === 0) return null;
  return (
    <StatusChip
      label={`${pendingCount} pending`}
      title={`${pendingCount} AI suggestion${pendingCount !== 1 ? 's' : ''} pending review`}
      icon={<Cpu size={12} aria-hidden />}
      variant="warning"
    />
  );
};

// ─── Preview mode indicator ──────────────────────────────────────────────────

interface PreviewModeIndicatorProps {
  mode: PreviewMode | null;
}

const PREVIEW_MODE_LABELS: Record<PreviewMode, string> = {
  'trusted-local': 'Trusted (local)',
  'semi-trusted': 'Semi-trusted',
  'untrusted-sandbox': 'Sandboxed',
};

const PreviewModeIndicator: React.FC<PreviewModeIndicatorProps> = ({ mode }) => {
  if (!mode) return null;
  return (
    <StatusChip
      label={PREVIEW_MODE_LABELS[mode]}
      title={`Preview policy: ${PREVIEW_MODE_LABELS[mode]}`}
      icon={<Eye size={12} aria-hidden />}
      variant={mode === 'untrusted-sandbox' ? 'error' : 'default'}
    />
  );
};

// ─── Status bar ──────────────────────────────────────────────────────────────

const BuilderStatusBar: React.FC = () => {
  const autosaveStatus = useAtomValue(builderAutosaveStatusAtom);
  const aiPendingCount = useAtomValue(builderAIPendingReviewCountAtom);
  const previewMode = useAtomValue(builderPreviewModeAtom);
  const collabSession = useAtomValue(builderCollabSessionAtom);
  const _hasUnsavedChanges = useAtomValue(builderHasUnsavedChangesAtom);

  return (
    <div
      role="status"
      aria-label="Editor status"
      className="flex items-center gap-2 px-3 py-1.5 border-t bg-muted/50 text-muted-foreground"
    >
      <AutosaveIndicator status={autosaveStatus} />
      <CollabIndicator session={collabSession} />
      <AIReviewIndicator pendingCount={aiPendingCount} />
      <PreviewModeIndicator mode={previewMode} />
    </div>
  );
};

// ─── Shell ───────────────────────────────────────────────────────────────────

export interface BuilderEditorShellProps {
  /** The editing surface to render — typically a CanvasEditor or page composer. */
  children: React.ReactNode;
  /** Additional CSS classes for the outer container. */
  className?: string;
  /** Suppress the status bar (e.g. in embedded / full-screen mode). */
  hideStatusBar?: boolean;
}

/**
 * BuilderEditorShell wraps the canvas editing surface and adds a persistent
 * status bar surfacing save state, collaboration presence, AI review count,
 * and current preview policy. State is driven by builder atoms in `@yappc/state`.
 */
export const BuilderEditorShell: React.FC<BuilderEditorShellProps> = ({
  children,
  className,
  hideStatusBar = false,
}) => {
  return (
    <div
      className={cn('flex flex-col h-full w-full overflow-hidden', className)}
      data-testid="builder-editor-shell"
    >
      <div className="flex-1 min-h-0 overflow-hidden">{children}</div>
      {!hideStatusBar && <BuilderStatusBar />}
    </div>
  );
};

BuilderEditorShell.displayName = 'BuilderEditorShell';
