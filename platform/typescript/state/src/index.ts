/**
 * @ghatana/state
 *
 * Platform-level state management for Ghatana applications.
 *
 * @module @ghatana/state
 */

// Types
export type {
  Loadable,
  Transition,
  StateMachineContext,
  StateMachine,
  PersistenceStorage,
  PersistenceOptions,
  AtomMetadata,
} from "./types";

// AsyncState is both a type alias and a value object — single re-export handles both
export { AsyncState } from "./types";

// Atoms
export {
  createAtom,
  createPersistentAtom,
  createDerivedAtom,
  createWritableDerivedAtom,
  createAsyncAtom,
  createAsyncAtomWithState,
  createYjsAtom,
  createWritableAtom,
  getRegisteredAtoms,
} from "./atoms";

// State machine
export { createStateMachine } from "./machine";

// Persistence
export {
  readFromStorage,
  writeToStorage,
  removeFromStorage,
  clearMemoryStore,
} from "./persistence";

// React hooks (only available in React contexts)
export {
  useStateAtom,
  useStateValue,
  useStateSetter,
  useAsyncStateAtom,
  useBooleanAtom,
} from "./hooks";

// Platform-shell atoms (authentication, notifications, tenant)
export * from "./platform-shell-atoms";
