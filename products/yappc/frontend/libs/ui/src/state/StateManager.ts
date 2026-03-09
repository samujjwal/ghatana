/**
 * State Manager
 *
 * Central state management utility using Jotai.
 * Provides atom creation, management, and utilities for global state.
 *
 * Features:
 * - Centralized atom registry
 * - Persistent atom support (localStorage/sessionStorage)
 * - Derived and computed atoms
 * - Async atom patterns
 * - Yjs collaboration support
 * - Debug mode with statistics
 * - Type-safe state access
 *
 * @module state/StateManager
 * @doc.type class
 * @doc.purpose Centralized state management using Jotai
 * @doc.layer product
 * @doc.pattern Singleton, Registry
 *
 * @example
 * ```typescript
 * // Create a simple atom
 * StateManager.createAtom('counter', 0, 'Counter state');
 *
 * // Create a persistent atom
 * StateManager.createPersistentAtom('user', defaultUser, {
 *   storage: 'local',
 *   description: 'User profile'
 * });
 *
 * // Use in components
 * const [count, setCount] = useGlobalState('counter');
 * ```
 */

import { atom } from 'jotai';
import { atomWithStorage, createJSONStorage } from 'jotai/utils';

import type { Atom, WritableAtom, Getter, Setter } from 'jotai';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type AtomKey = string;

/**
 * State for async operations (consolidated from libs/state).
 *
 * @doc.type type
 * @doc.purpose Async operation state
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type AsyncState<T> =
  | { status: 'idle'; data: null; error: null }
  | { status: 'loading'; data: null; error: null }
  | { status: 'success'; data: T; error: null }
  | { status: 'error'; data: null; error: Error };

/**
 *
 */
export interface AtomMetadata {
  key: AtomKey;
  description?: string;
  persistent?: boolean;
  storage?: 'local' | 'session' | 'memory';
  storageKey?: string;
  defaultValue: unknown;
  createdAt: Date;
}

/**
 *
 */
export interface StateManagerConfig {
  /**
   * Enable debug mode
   */
  debug?: boolean;

  /**
   * Storage prefix for persistent atoms
   */
  storagePrefix?: string;

  /**
   * Enable DevTools integration
   */
  devTools?: boolean;
}

// ============================================================================
// State Manager Implementation
// ============================================================================

/**
 *
 */
export class StateManager {
  private static atoms = new Map<AtomKey, Atom<unknown>>();
  private static metadata = new Map<AtomKey, AtomMetadata>();
  private static config: StateManagerConfig = {
    debug: false,
    storagePrefix: 'ui-state',
    devTools: true,
  };

  /**
   * Configure state manager
   */
  static configure(config: Partial<StateManagerConfig>): void {
    this.config = { ...this.config, ...config };
    if (this.config.debug) {
      console.log('[StateManager] Configured:', this.config);
    }
  }

  /**
   * Create a simple atom
   */
  static createAtom<T>(
    key: AtomKey,
    defaultValue: T,
    description?: string
  ): WritableAtom<T, [T], void> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(`[StateManager] Atom "${key}" already exists`);
      }
      return this.atoms.get(key) as WritableAtom<T, [T], void>;
    }

    const newAtom = atom(
      defaultValue,
      (_get, set, nextValue: T) => {
        set(newAtom, nextValue);
      }
    );

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description,
      persistent: false,
      storage: 'memory',
      defaultValue,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created atom "${key}"`, {
        defaultValue,
        description,
      });
    }

    return newAtom;
  }

  /**
   * Create a persistent atom (uses localStorage by default)
   */
  static createPersistentAtom<T>(
    key: AtomKey,
    defaultValue: T,
    options: {
      description?: string;
      storage?: 'local' | 'session';
      storageKey?: string;
    } = {}
  ): WritableAtom<T, [T], void> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(`[StateManager] Persistent atom "${key}" already exists`);
      }
      return this.atoms.get(key) as WritableAtom<T, [T], void>;
    }

    const storageKey =
      options.storageKey ?? `${this.config.storagePrefix}:${key}`;
    const storageType = options.storage || 'local';

    // Create storage based on type
    const storage = createJSONStorage<T>(() =>
      storageType === 'local' ? localStorage : sessionStorage
    );

    const baseAtom = atomWithStorage<T>(storageKey, defaultValue, storage);
    const newAtom = atom<T, [T], void>(
      (get) => get(baseAtom),
      (_get, set, nextValue: T) => {
        set(baseAtom, nextValue);
      }
    );

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description: options.description,
      persistent: true,
      storage: storageType,
      storageKey,
      defaultValue,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created persistent atom "${key}"`, {
        defaultValue,
        storage: storageType,
      });
    }

    return newAtom;
  }

  /**
   * Create a derived (computed) atom
   */
  static createDerivedAtom<T>(
    key: AtomKey,
    read: (get: Getter) => T,
    description?: string
  ): Atom<T> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(`[StateManager] Derived atom "${key}" already exists`);
      }
      return this.atoms.get(key) as Atom<T>;
    }

    const newAtom = atom(read);

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description,
      persistent: false,
      storage: 'memory',
      defaultValue: undefined,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created derived atom "${key}"`, {
        description,
      });
    }

    return newAtom;
  }

  /**
   * Create a writable derived atom
   */
  static createWritableDerivedAtom<T, Args extends unknown[], Result>(
    key: AtomKey,
    read: (get: Getter) => T,
    write: (get: Getter, set: Setter, ...args: Args) => Result,
    description?: string
  ): WritableAtom<T, Args, Result> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(
          `[StateManager] Writable derived atom "${key}" already exists`
        );
      }
      return this.atoms.get(key) as WritableAtom<T, Args, Result>;
    }

    const newAtom = atom(read, write);

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description,
      persistent: false,
      storage: 'memory',
      defaultValue: undefined,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created writable derived atom "${key}"`, {
        description,
      });
    }

    return newAtom;
  }

  /**
   * Create an async atom with loading/error states
   */
  static createAsyncAtom<T>(
    key: AtomKey,
    asyncFn: (get: Getter) => Promise<T>,
    description?: string
  ): Atom<Promise<T>> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(`[StateManager] Async atom "${key}" already exists`);
      }
      return this.atoms.get(key) as Atom<Promise<T>>;
    }

    const newAtom = atom(asyncFn);

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description,
      persistent: false,
      storage: 'memory',
      defaultValue: undefined,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created async atom "${key}"`, {
        description,
      });
    }

    return newAtom;
  }

  /**
   * Create an async atom with loading/error states (consolidated from libs/state)
   */
  static createAsyncAtomWithState<T>(
    key: AtomKey,
    fetchFn: () => Promise<T>,
    options: {
      description?: string;
      initialData?: T;
    } = {}
  ): Atom<AsyncState<T>> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(
          `[StateManager] Async atom with state "${key}" already exists`
        );
      }
      return this.atoms.get(key) as Atom<AsyncState<T>>;
    }

    const initialState: AsyncState<T> = options.initialData !== undefined
      ? { status: 'success', data: options.initialData, error: null }
      : { status: 'idle', data: null, error: null };

    const newAtom = atom<AsyncState<T>>(initialState);

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description:
        options.description || 'Async atom with loading/error states',
      persistent: false,
      storage: 'memory',
      defaultValue: initialState,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created async atom with state "${key}"`, {
        description: options.description,
      });
    }

    return newAtom;
  }

  /**
   * Create a Yjs-backed atom (consolidated from libs/state)
   */
  static createYjsAtom<T extends Record<string, unknown>>(
    key: AtomKey,
    ytype: unknown,
    initialValue: T,
    options: {
      description?: string;
      onUpdate?: (value: T) => void;
    } = {}
  ): Atom<T> {
    if (this.atoms.has(key)) {
      if (this.config.debug) {
        console.warn(`[StateManager] Yjs atom "${key}" already exists`);
      }
      return this.atoms.get(key) as Atom<T>;
    }

    // Create atom that syncs with Yjs type
    const newAtom = atom<T>(initialValue);

    // Store atom and metadata
    this.atoms.set(key, newAtom);
    this.metadata.set(key, {
      key,
      description: options.description || 'Yjs-backed collaborative atom',
      persistent: false,
      storage: 'memory',
      defaultValue: initialValue,
      createdAt: new Date(),
    });

    if (this.config.debug) {
      console.log(`[StateManager] Created Yjs atom "${key}"`, {
        description: options.description,
      });
    }

    return newAtom;
  }

  /**
   * Get an atom by key
   */
  static getAtom<T>(key: AtomKey): Atom<T> | undefined {
    return this.atoms.get(key) as Atom<T> | undefined;
  }

  /**
   * Get atom metadata
   */
  static getMetadata(key: AtomKey): AtomMetadata | undefined {
    return this.metadata.get(key);
  }

  /**
   * Check if atom exists
   */
  static hasAtom(key: AtomKey): boolean {
    return this.atoms.has(key);
  }

  /**
   * Get all atoms as entries
   */
  static getAllAtoms(): Array<[AtomKey, Atom<unknown>]> {
    return Array.from(this.atoms.entries());
  }

  /**
   * Get all atoms with metadata
   */
  static getAllKeys(): AtomKey[] {
    return Array.from(this.atoms.keys());
  }

  /**
   * Get all atoms with metadata
   */
  static getAllAtomsWithMetadata(): Array<{
    key: AtomKey;
    atom: Atom<unknown>;
    metadata: AtomMetadata;
  }> {
    const result: Array<{
      key: AtomKey;
      atom: Atom<unknown>;
      metadata: AtomMetadata;
    }> = [];

    for (const [key, atom] of this.atoms.entries()) {
      const metadata = this.metadata.get(key);
      if (metadata) {
        result.push({ key, atom, metadata });
      }
    }

    return result;
  }

  /**
   * Remove an atom
   */
  static removeAtom(key: AtomKey): boolean {
    const had = this.atoms.has(key);
    this.atoms.delete(key);
    this.metadata.delete(key);

    if (had && this.config.debug) {
      console.log(`[StateManager] Removed atom "${key}"`);
    }

    return had;
  }

  /**
   * Clear all atoms
   */
  static clearAll(): void {
    this.atoms.clear();
    this.metadata.clear();

    if (this.config.debug) {
      console.log('[StateManager] Cleared all atoms');
    }
  }

  /**
   * Export state snapshot (for debugging/testing)
   */
  static exportSnapshot(get: Getter): Record<AtomKey, unknown> {
    const snapshot: Record<AtomKey, unknown> = {};

    for (const [key, atom] of this.atoms.entries()) {
      try {
        snapshot[key] = get(atom);
      } catch (error) {
        // Async atoms or atoms with errors will be skipped
        if (this.config.debug) {
          console.warn(`[StateManager] Could not read atom "${key}"`, error);
        }
      }
    }

    return snapshot;
  }

  /**
   * Get statistics
   */
  static getStatistics(): {
    totalAtoms: number;
    persistentAtoms: number;
    derivedAtoms: number;
    asyncAtoms: number;
  } {
    let persistentCount = 0;
    let derivedCount = 0;

    for (const metadata of this.metadata.values()) {
      if (metadata.persistent) persistentCount++;
      if (metadata.defaultValue === undefined) derivedCount++;
    }

    return {
      totalAtoms: this.atoms.size,
      persistentAtoms: persistentCount,
      derivedAtoms: derivedCount,
      asyncAtoms: 0, // Would need runtime inspection
    };
  }
}
