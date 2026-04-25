/**
 * Builder editor session atoms for YAPPC.
 *
 * Provides per-session Jotai state for the visual builder:
 * - active document ID
 * - autosave status
 * - AI review state (pending count)
 * - preview mode in use
 * - collab session info
 *
 * These atoms are consumed by yappc-ui editor shells and diagnostics panels.
 * They must NOT hold BuilderDocument by value — only IDs and derived metadata
 * to stay serializable and compatible with devtools.
 *
 * @module state/builderAtoms
 * @doc.type module
 * @doc.purpose Builder editor session state atoms
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import type { PreviewMode } from '@ghatana/ui-builder';

import {
  createAtom,
  createPersistentAtom,
} from '@ghatana/state';

// ============================================================================
// Active builder document
// ============================================================================

/**
 * ID of the builder document currently open in the editor.
 * Null when no document is loaded.
 */
export const builderActiveDocumentIdAtom = createPersistentAtom<
  string | null
>('builder:activeDocumentId', null, {
  storage: 'sessionStorage',
  storageKey: 'builder:activeDocumentId',
});

// ============================================================================
// Autosave
// ============================================================================

/**
 * Autosave status for the active builder document.
 * - 'idle': no pending changes
 * - 'pending': changes queued, save has not fired yet
 * - 'saving': save in flight
 * - 'saved': last save succeeded
 * - 'error': last save failed
 */
export type AutosaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'error';

export const builderAutosaveStatusAtom = createAtom<AutosaveStatus>(
  'builder:autosaveStatus',
  'idle',
  'Autosave status for the active builder document',
);

/** ISO timestamp of the most recent successful save. Null if never saved this session. */
export const builderLastSavedAtAtom = createAtom<string | null>(
  'builder:lastSavedAt',
  null,
  'Timestamp of the last successful autosave',
);

// ============================================================================
// AI review
// ============================================================================

/**
 * Count of AI action proposals that are in "pending" review state.
 * Surfaced in the editor top bar so users know when review is needed.
 */
export const builderAIPendingReviewCountAtom = createAtom<number>(
  'builder:aiPendingReviewCount',
  0,
  'Count of AI proposals awaiting review',
);

// ============================================================================
// Preview mode
// ============================================================================

/**
 * The preview mode currently in use for the active document.
 * Matches the canonical PreviewMode type from @ghatana/ui-builder.
 * Null when no preview is active.
 */
export const builderPreviewModeAtom = createAtom<PreviewMode | null>(
  'builder:previewMode',
  null,
  'Active preview trust mode'
);

// ============================================================================
// Collab session
// ============================================================================

export interface BuilderCollabSession {
  /** Platform session ID (maps to CollabPayload.sessionId). */
  readonly sessionId: string;
  /** Number of participants currently in the session. */
  readonly participantCount: number;
  /** Whether the local user is connected. */
  readonly connected: boolean;
}

/**
 * Active collaboration session for the builder editor.
 * Null when not in a collab session.
 */
export const builderCollabSessionAtom = createAtom<BuilderCollabSession | null>(
  'builder:collabSession',
  null,
  'Active collab session for the builder'
);

// ============================================================================
// Derived
// ============================================================================

/**
 * True when the editor has unsaved or in-flight changes.
 * Derived from autosave status.
 */
export const builderHasUnsavedChangesAtom = atom<boolean>(
  (get) => {
    const status = get(builderAutosaveStatusAtom);
    return status === 'pending' || status === 'saving' || status === 'error';
  },
);
