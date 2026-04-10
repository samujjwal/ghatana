/**
 * @ghatana/state — Core Type Definitions
 *
 * Framework-agnostic base types for platform state management.
 */

// ---------------------------------------------------------------------------
// Async state discriminated union
// ---------------------------------------------------------------------------

/**
 * Represents the lifecycle of an async operation.
 * Always start with 'idle', move to 'loading', resolve to 'success' or 'error'.
 *
 * @typeParam T - The success data type.
 *
 * @example
 * ```ts
 * type UserState = AsyncState<User>;
 * ```
 */
export type AsyncState<T> =
  | { readonly status: "idle"; readonly data: null; readonly error: null }
  | { readonly status: "loading"; readonly data: null; readonly error: null }
  | { readonly status: "success"; readonly data: T; readonly error: null }
  | { readonly status: "error"; readonly data: null; readonly error: Error };

/** Convenience factory for AsyncState. */
export const AsyncState = {
  idle: <T>(): AsyncState<T> => ({ status: "idle", data: null, error: null }),
  loading: <T>(): AsyncState<T> => ({
    status: "loading",
    data: null,
    error: null,
  }),
  success: <T>(data: T): AsyncState<T> => ({
    status: "success",
    data,
    error: null,
  }),
  error: <T>(error: Error): AsyncState<T> => ({
    status: "error",
    data: null,
    error,
  }),
} as const;

// ---------------------------------------------------------------------------
// Loadable pattern  
// ---------------------------------------------------------------------------

/** Represents a value that may or may not yet be loaded. */
export type Loadable<T> =
  | { readonly state: "loading" }
  | { readonly state: "hasData"; readonly data: T }
  | { readonly state: "hasError"; readonly error: unknown };

// ---------------------------------------------------------------------------
// State machine types
// ---------------------------------------------------------------------------

/**
 * A transition in a finite state machine.
 *
 * @typeParam TState - String literal type of the possible states.
 * @typeParam TEvent - String literal type of the events that trigger transitions.
 */
export interface Transition<TState extends string, TEvent extends string> {
  readonly from: TState;
  readonly to: TState;
  readonly on: TEvent;
  /** Optional guard — if false, the transition is rejected. */
  readonly guard?: (context: StateMachineContext) => boolean;
  /** Optional side-effect after transitioning. */
  readonly effect?: (context: StateMachineContext) => void;
}

/** Opaque context passed to transition guards and effects. */
export type StateMachineContext = Readonly<Record<string, unknown>>;

/**
 * Type-safe finite state machine.
 *
 * @typeParam TState - String literal union of states.
 * @typeParam TEvent - String literal union of events.
 */
export interface StateMachine<
  TState extends string,
  TEvent extends string,
> {
  readonly currentState: TState;
  /** Returns true if the event is allowed from the current state. */
  canTransition(event: TEvent): boolean;
  /** Applies the event; returns the new state or throws if disallowed. */
  transition(event: TEvent): StateMachine<TState, TEvent>;
  /** Returns all valid events from current state. */
  validEvents(): TEvent[];
}

// ---------------------------------------------------------------------------
// Persistence metadata
// ---------------------------------------------------------------------------

export type PersistenceStorage = "localStorage" | "sessionStorage" | "memory";

export interface PersistenceOptions {
  /** Where to persist the value. */
  readonly storage: PersistenceStorage;
  /** Key to use in the storage backend (defaults to atom key). */
  readonly storageKey?: string;
  /** Schema version for migration support. */
  readonly version?: number;
  /** Migration function called when version mismatch is detected. */
  readonly migrate?: (
    stored: unknown,
    fromVersion: number
  ) => unknown;
}

// ---------------------------------------------------------------------------
// Atom metadata (for registries)
// ---------------------------------------------------------------------------

export interface AtomMetadata {
  readonly key: string;
  readonly description?: string;
  readonly persistent: boolean;
  readonly persistenceOptions?: PersistenceOptions;
  readonly defaultValue: unknown;
  readonly createdAt: Date;
}
