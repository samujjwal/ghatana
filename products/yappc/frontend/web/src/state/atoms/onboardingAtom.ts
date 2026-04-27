/**
 * Onboarding and feature discovery state atoms.
 *
 * Persists dismissed tour IDs and hint IDs per user+workspace combination
 * in localStorage so hints do not resurface after the user dismisses them.
 *
 * @doc.type module
 * @doc.purpose Onboarding & feature discovery state persistence
 * @doc.layer state
 * @doc.pattern Jotai atom
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// ---------------------------------------------------------------------------
// Storage key helpers
// ---------------------------------------------------------------------------

const storageKey = (userId: string, workspaceId: string): string =>
  `yappc:onboarding:dismissed:${userId}:${workspaceId}`;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Identifier of a guided tour step sequence */
export type TourId = string;

/** Identifier of an inline feature hint */
export type HintId = string;

/** Persisted record of dismissed tours and hints for one user+workspace */
export interface DismissedOnboarding {
  /** Tour IDs the user has completed or explicitly dismissed */
  tours: TourId[];
  /** Hint IDs the user has dismissed */
  hints: HintId[];
}

// ---------------------------------------------------------------------------
// Derived atoms – created per user+workspace at runtime
// ---------------------------------------------------------------------------

/**
 * Creates a storage-backed atom for dismissed onboarding state tied to a
 * specific user and workspace.  Call this inside a component or hook once the
 * user/workspace IDs are known; cache the result externally if needed.
 */
export function createDismissedOnboardingAtom(userId: string, workspaceId: string) {
  return atomWithStorage<DismissedOnboarding>(
    storageKey(userId, workspaceId),
    { tours: [], hints: [] },
  );
}

// ---------------------------------------------------------------------------
// Transient (session-only) atoms
// ---------------------------------------------------------------------------

/**
 * Currently active tour ID.  `null` means no tour is running.
 */
export const activeTourIdAtom = atom<TourId | null>(null);

/**
 * Current step index within the active tour.
 */
export const activeTourStepAtom = atom<number>(0);

/**
 * Whether the feature discovery overlay is visible.
 */
export const featureDiscoveryOpenAtom = atom<boolean>(false);

/**
 * Feature ID the discovery tooltip is currently pointing at.
 */
export const activeFeatureIdAtom = atom<string | null>(null);

// ---------------------------------------------------------------------------
// Derived write atoms
// ---------------------------------------------------------------------------

/**
 * Write atom: start a tour by ID.
 */
export const startTourAtom = atom(
  null,
  (_get, set, tourId: TourId) => {
    set(activeTourIdAtom, tourId);
    set(activeTourStepAtom, 0);
  },
);

/**
 * Write atom: advance to the next step or end the tour when steps are exhausted.
 */
export const advanceTourStepAtom = atom(
  null,
  (get, set, totalSteps: number) => {
    const current = get(activeTourStepAtom);
    if (current + 1 >= totalSteps) {
      set(activeTourIdAtom, null);
      set(activeTourStepAtom, 0);
    } else {
      set(activeTourStepAtom, current + 1);
    }
  },
);

/**
 * Write atom: cancel the active tour without completing it.
 */
export const cancelTourAtom = atom(
  null,
  (_get, set) => {
    set(activeTourIdAtom, null);
    set(activeTourStepAtom, 0);
  },
);
