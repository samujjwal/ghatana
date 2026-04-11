/**
 * @yappc/state — State Management Library
 *
 * Complete Jotai-based state management system with type-safe hooks and persistence.
 *
 * Platform-level state primitives are re-exported from `@ghatana/state` so consumers
 * only need to import from one place.  YAPPC-specific atoms (workspace, project, AI,
 * CRDT) live in this package alongside the generic primitives.
 *
 * @module state
 */

// ─── Platform state primitives (from @ghatana/state) ──────────────────────
// Provides: createAtom, createPersistentAtom, createDerivedAtom, createAsyncAtom,
// createWritableAtom, getRegisteredAtoms, createStateMachine,
// readFromStorage, writeToStorage, removeFromStorage, clearMemoryStore,
// useStateAtom, useStateValue, useStateSetter, useAsyncStateAtom, useBooleanAtom,
// platform-shell atoms (authAtom, notificationAtom, tenantAtom)
export * from '@ghatana/state';

// Re-export store modules (YAPPC-specific atoms and state management)
export * from './store';

// Re-export hooks
export * from './hooks';

// Re-export config
export * from './config';
