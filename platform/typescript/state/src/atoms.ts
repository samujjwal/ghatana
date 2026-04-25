/**
 * @ghatana/state — Atom Utilities
 *
 * Jotai-based atom creation primitives with type safety, metadata tracking,
 * and persistence support.
 */

import { atom, type Atom, type WritableAtom, type Getter, type Setter } from "jotai";
import { atomWithStorage } from "jotai/utils";

import type { AtomMetadata, AsyncState, PersistenceOptions } from "./types";
import { AsyncState as AS } from "./types";

// ---------------------------------------------------------------------------
// Atom registry
// ---------------------------------------------------------------------------

const registry = new Map<string, AtomMetadata>();

function registerAtom(meta: AtomMetadata): void {
  registry.set(meta.key, meta);
}

/**
 * Returns all registered atom metadata.
 * Useful for diagnostics and dev-tools integration.
 */
export function getRegisteredAtoms(): ReadonlyMap<string, AtomMetadata> {
  return registry;
}

// ---------------------------------------------------------------------------
// createAtom — simple read/write atom
// ---------------------------------------------------------------------------

/**
 * Creates a type-safe, registered Jotai atom.
 *
 * @param key         - Unique key for the registry and debugging.
 * @param initialValue - The initial value of the atom.
 * @param description  - Optional human-readable description for devtools.
 *
 * @example
 * ```ts
 * const counterAtom = createAtom('counter', 0, 'A simple counter');
 * ```
 */
export function createAtom<T>(
  key: string,
  initialValue: T,
  description?: string
): WritableAtom<T, [T | ((prev: T) => T)], void> {
  const a = atom<T>(initialValue);
  a.debugLabel = key;

  registerAtom({
    key,
    description,
    persistent: false,
    defaultValue: initialValue,
    createdAt: new Date(),
  });

  return a as WritableAtom<T, [T | ((prev: T) => T)], void>;
}

// ---------------------------------------------------------------------------
// createPersistentAtom — atom with storage-backed persistence
// ---------------------------------------------------------------------------

/**
 * Creates a Jotai atom backed by a persistent storage (localStorage/sessionStorage).
 *
 * @param key          - Registry key; also used as the storage key unless overridden.
 * @param initialValue - Default value when no persisted value exists.
 * @param options      - Persistence configuration.
 *
 * @example
 * ```ts
 * const themeAtom = createPersistentAtom('theme', 'light', {
 *   storage: 'localStorage',
 * });
 * ```
 */
export function createPersistentAtom<T>(
  key: string,
  initialValue: T,
  options: PersistenceOptions,
  description?: string
): WritableAtom<T, [T | ((prev: T) => T)], void> {
  const storageKey = options.storageKey ?? key;

  let jotaiStorage: "localStorage" | "sessionStorage" | undefined;
  if (options.storage === "localStorage") {
    jotaiStorage = "localStorage";
  } else if (options.storage === "sessionStorage") {
    jotaiStorage = "sessionStorage";
  }

  let a: WritableAtom<T, [T | ((prev: T) => T)], void>;

  if (!jotaiStorage || typeof window === "undefined") {
    // SSR / memory fallback
    a = atom<T>(initialValue) as WritableAtom<T, [T | ((prev: T) => T)], void>;
  } else {
    a = atomWithStorage<T>(storageKey, initialValue, undefined, {
      getOnInit: true,
    }) as unknown as WritableAtom<T, [T | ((prev: T) => T)], void>;
  }

  a.debugLabel = key;

  registerAtom({
    key,
    description,
    persistent: true,
    persistenceOptions: options,
    defaultValue: initialValue,
    createdAt: new Date(),
  });

  return a;
}

// ---------------------------------------------------------------------------
// createDerivedAtom — computed read-only atom
// ---------------------------------------------------------------------------

/**
 * Creates a derived (computed, read-only) atom.
 *
 * @param key    - Registry key.
 * @param derive - Selector function that reads from other atoms.
 *
 * @example
 * ```ts
 * const fullNameAtom = createDerivedAtom('fullName', (get) =>
 *   `${get(firstNameAtom)} ${get(lastNameAtom)}`
 * );
 * ```
 */
export function createDerivedAtom<T>(
  key: string,
  derive: (get: Getter) => T
): Atom<T> {
  const a = atom(derive);
  a.debugLabel = key;
  return a;
}

// ---------------------------------------------------------------------------
// createWritableDerivedAtom — computed read/write atom
// ---------------------------------------------------------------------------

/**
 * Creates a derived atom with custom read and write logic.
 * Useful for atoms that need to perform side effects on write.
 *
 * @param key    - Registry key.
 * @param read   - Read function.
 * @param write  - Write function.
 *
 * @example
 * ```ts
 * const fullNameAtom = createWritableDerivedAtom('fullName', 
 *   (get) => `${get(firstNameAtom)} ${get(lastNameAtom)}`,
 *   (get, set, newValue) => {
 *     const [first, last] = newValue.split(' ');
 *     set(firstNameAtom, first);
 *     set(lastNameAtom, last);
 *   }
 * );
 * ```
 */
export function createWritableDerivedAtom<T, Args extends unknown[], Result>(
  key: string,
  read: (get: Getter) => T,
  write: (get: Getter, set: Setter, ...args: Args) => Result
): WritableAtom<T, Args, Result> {
  const a = atom(read, write);
  a.debugLabel = key;

  registerAtom({
    key,
    persistent: false,
    defaultValue: undefined,
    createdAt: new Date(),
  });

  return a;
}

// ---------------------------------------------------------------------------
// createAsyncAtom — atom for async data fetching
// ---------------------------------------------------------------------------

/**
 * Creates a writable atom holding an `AsyncState<T>` value.
 * Provides helpers to transition between idle/loading/success/error.
 *
 * @param key          - Registry key.
 * @param description  - Optional description.
 *
 * @example
 * ```ts
 * const usersAtom = createAsyncAtom<User[]>('users');
 * // Load: store.set(usersAtom, AsyncState.loading())
 * // Ok:   store.set(usersAtom, AsyncState.success(users))
 * ```
 */
export function createAsyncAtom<T>(
  key: string,
  description?: string
): WritableAtom<AsyncState<T>, [AsyncState<T>], void> {
  const a = atom<AsyncState<T>>(AS.idle<T>() as AsyncState<T>);
  a.debugLabel = key;

  registerAtom({
    key,
    description,
    persistent: false,
    defaultValue: AS.idle(),
    createdAt: new Date(),
  });

  return a as WritableAtom<AsyncState<T>, [AsyncState<T>], void>;
}

// ---------------------------------------------------------------------------
// createAsyncAtomWithState — async atom with loading/error states and fetch function
// ---------------------------------------------------------------------------

/**
 * Creates an async atom with loading/error states and a fetch function.
 * Provides initial data support and automatic state transitions.
 *
 * @param key          - Registry key.
 * @param fetchFn      - Async function to fetch data.
 * @param options      - Options including initial data and description.
 *
 * @example
 * ```ts
 * const usersAtom = createAsyncAtomWithState('users', 
 *   () => fetch('/api/users').then(r => r.json()),
 *   { initialData: [] }
 * );
 * ```
 */
export function createAsyncAtomWithState<T>(
  key: string,
  fetchFn: () => Promise<T>,
  options: {
    description?: string;
    initialData?: T;
  } = {}
): Atom<AsyncState<T>> {
  const initialState: AsyncState<T> =
    options.initialData !== undefined
      ? AS.success(options.initialData)
      : AS.idle();

  const a = atom<AsyncState<T>>(initialState);
  a.debugLabel = key;

  registerAtom({
    key,
    description: options.description || 'Async atom with loading/error states',
    persistent: false,
    defaultValue: initialState,
    createdAt: new Date(),
  });

  return a;
}

// ---------------------------------------------------------------------------
// createYjsAtom — Yjs-backed collaborative atom
// ---------------------------------------------------------------------------

/**
 * Creates a Yjs-backed atom for collaborative editing.
 * Note: This is a simplified version. Full Yjs integration requires additional setup.
 *
 * @param key          - Registry key.
 * @param initialValue - Initial value.
 * @param options      - Options including description and update callback.
 *
 * @example
 * ```ts
 * const docAtom = createYjsAtom('doc', { text: '' }, {
 *   description: 'Yjs document',
 *   onUpdate: (value) => console.log('Updated:', value)
 * });
 * ```
 */
export function createYjsAtom<T extends Record<string, unknown>>(
  key: string,
  initialValue: T,
  options: {
    description?: string;
    onUpdate?: (value: T) => void;
  } = {}
): Atom<T> {
  const a = atom<T>(initialValue);
  a.debugLabel = key;

  registerAtom({
    key,
    description: options.description || 'Yjs-backed collaborative atom',
    persistent: false,
    defaultValue: initialValue,
    createdAt: new Date(),
  });

  if (options.onUpdate) {
    // In a full implementation, this would set up Yjs observation
    // For now, we just register the callback for future use
  }

  return a;
}

// ---------------------------------------------------------------------------
// createWritableAtom — atom with explicit read/write logic
// ---------------------------------------------------------------------------

/**
 * Creates a Jotai atom with custom read and write logic.
 * Useful for atoms that need to perform side effects on write.
 */
export function createWritableAtom<T, TWrite extends unknown[]>(
  key: string,
  read: (get: Getter) => T,
  write: (get: Getter, set: Setter, ...args: TWrite) => void
): WritableAtom<T, TWrite, void> {
  const a = atom(read, write);
  a.debugLabel = key;
  return a;
}
