/**
 * @doc.type enum
 * @doc.purpose Plugin lifecycle state enumeration
 * @doc.layer product
 * @doc.pattern State Machine
 * 
 * Represents the different states a plugin can be in during its lifecycle.
 * 
 * State Transitions:
 * - NOT_LOADED → LOADING → RUNNING
 * - RUNNING → STOPPING → STOPPED
 * - Any state → ERROR
 * 
 * @see {@link PluginLifecycleManager}
 */
export enum PluginLifecycleState {
  /** Plugin not yet loaded */
  NOT_LOADED = 'NOT_LOADED',
  /** Plugin currently loading */
  LOADING = 'LOADING',
  /** Plugin successfully initialized and running */
  RUNNING = 'RUNNING',
  /** Plugin currently stopping */
  STOPPING = 'STOPPING',
  /** Plugin stopped and resources cleaned up */
  STOPPED = 'STOPPED',
  /** Plugin encountered an error */
  ERROR = 'ERROR',
}

/**
 * @doc.type interface
 * @doc.purpose Plugin lifecycle state transition event
 * @doc.layer product
 * @doc.pattern Event
 * 
 * Event emitted when a plugin changes lifecycle states.
 */
export interface PluginLifecycleEvent {
  /** Plugin ID */
  pluginId: string;
  /** Previous lifecycle state */
  previousState: PluginLifecycleState;
  /** New lifecycle state */
  newState: PluginLifecycleState;
  /** Timestamp of state change */
  timestamp: Date;
  /** Error details if state is ERROR */
  error?: Error;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * @doc.type interface
 * @doc.purpose Plugin lifecycle manager interface
 * @doc.layer product
 * @doc.pattern Manager
 * 
 * Manages plugin state transitions and enforces valid state machine transitions.
 * Handles plugin initialization, shutdown, and error conditions.
 */
export interface IPluginLifecycleManager {
  /**
   * Get current lifecycle state of a plugin
   * 
   * @param pluginId - Plugin identifier
   * @returns Current lifecycle state, or null if plugin not tracked
   */
  getState(pluginId: string): PluginLifecycleState | null;

  /**
   * Transition plugin to a new state
   * 
   * @param pluginId - Plugin identifier
   * @param newState - Target lifecycle state
   * @param error - Error details if transitioning to ERROR state
   * @param metadata - Additional metadata for state transition
   * @throws Error if transition is invalid according to state machine rules
   */
  transitionState(
    pluginId: string,
    newState: PluginLifecycleState,
    error?: Error,
    metadata?: Record<string, unknown>,
  ): void;

  /**
   * Subscribe to lifecycle state changes for a plugin
   * 
   * @param pluginId - Plugin identifier (or '*' for all plugins)
   * @param callback - Function called when state changes
   * @returns Unsubscribe function
   */
  onStateChange(
    pluginId: string,
    callback: (event: PluginLifecycleEvent) => void,
  ): () => void;

  /**
   * Get all plugins in a specific state
   * 
   * @param state - Lifecycle state to filter by
   * @returns Array of plugin IDs in that state
   */
  getPluginsByState(state: PluginLifecycleState): string[];

  /**
   * Reset lifecycle state of a plugin
   * 
   * @param pluginId - Plugin identifier
   */
  reset(pluginId: string): void;

  /**
   * Check if a state transition is valid
   * 
   * @param fromState - Current state
   * @param toState - Target state
   * @returns true if transition is allowed
   */
  isValidTransition(
    fromState: PluginLifecycleState,
    toState: PluginLifecycleState,
  ): boolean;
}

/**
 * @doc.type class
 * @doc.purpose Default plugin lifecycle manager implementation
 * @doc.layer product
 * @doc.pattern State Machine
 * 
 * Implements a strict state machine for plugin lifecycle management.
 * 
 * Valid transitions:
 * - NOT_LOADED → LOADING
 * - LOADING → RUNNING, ERROR
 * - RUNNING → STOPPING, ERROR
 * - STOPPING → STOPPED, ERROR
 * - STOPPED → LOADING (restart)
 * - (any) → ERROR (error recovery)
 * 
 * Usage:
 * ```typescript
 * const manager = new PluginLifecycleManager();
 * manager.transitionState('my-plugin', PluginLifecycleState.LOADING);
 * manager.onStateChange('my-plugin', (event) => {
 *   console.log(`Plugin ${event.pluginId}: ${event.previousState} → ${event.newState}`);
 * });
 * manager.transitionState('my-plugin', PluginLifecycleState.RUNNING);
 * ```
 */
export class PluginLifecycleManager implements IPluginLifecycleManager {
  private states: Map<string, PluginLifecycleState> = new Map();
  private listeners: Map<
    string,
    Array<(event: PluginLifecycleEvent) => void>
  > = new Map();

  /**
   * Define valid state transitions for the state machine
   * 
   * Maps current state to allowed next states
   */
  private static readonly VALID_TRANSITIONS: Map<string, Set<string>> = new Map([
    [
      PluginLifecycleState.NOT_LOADED,
      new Set([PluginLifecycleState.LOADING, PluginLifecycleState.ERROR]),
    ],
    [
      PluginLifecycleState.LOADING,
      new Set([
        PluginLifecycleState.RUNNING,
        PluginLifecycleState.ERROR,
        PluginLifecycleState.STOPPED,
      ]),
    ],
    [
      PluginLifecycleState.RUNNING,
      new Set([PluginLifecycleState.STOPPING, PluginLifecycleState.ERROR]),
    ],
    [
      PluginLifecycleState.STOPPING,
      new Set([PluginLifecycleState.STOPPED, PluginLifecycleState.ERROR]),
    ],
    [
      PluginLifecycleState.STOPPED,
      new Set([PluginLifecycleState.LOADING, PluginLifecycleState.ERROR]),
    ],
    [
      PluginLifecycleState.ERROR,
      new Set([
        PluginLifecycleState.LOADING,
        PluginLifecycleState.STOPPED,
        PluginLifecycleState.NOT_LOADED,
      ]),
    ],
  ]);

  getState(pluginId: string): PluginLifecycleState | null {
    return this.states.get(pluginId) ?? null;
  }

  transitionState(
    pluginId: string,
    newState: PluginLifecycleState,
    error?: Error,
    metadata?: Record<string, unknown>,
  ): void {
    const currentState =
      this.states.get(pluginId) ?? PluginLifecycleState.NOT_LOADED;

    // Validate transition
    if (!this.isValidTransition(currentState, newState)) {
      throw new Error(
        `Invalid state transition for plugin '${pluginId}': ${currentState} → ${newState}`,
      );
    }

    // Update state
    this.states.set(pluginId, newState);

    // Emit event
    const event: PluginLifecycleEvent = {
      pluginId,
      previousState: currentState,
      newState,
      timestamp: new Date(),
      error,
      metadata,
    };

    this.emitStateChange(event);
  }

  onStateChange(
    pluginId: string,
    callback: (event: PluginLifecycleEvent) => void,
  ): () => void {
    if (!this.listeners.has(pluginId)) {
      this.listeners.set(pluginId, []);
    }

    const callbacks = this.listeners.get(pluginId)!;
    callbacks.push(callback);

    // Return unsubscribe function
    return () => {
      const index = callbacks.indexOf(callback);
      if (index > -1) {
        callbacks.splice(index, 1);
      }
    };
  }

  getPluginsByState(state: PluginLifecycleState): string[] {
    return Array.from(this.states.entries())
      .filter(([_, s]) => s === state)
      .map(([id, _]) => id);
  }

  reset(pluginId: string): void {
    this.states.delete(pluginId);
    this.listeners.delete(pluginId);
  }

  isValidTransition(
    fromState: PluginLifecycleState,
    toState: PluginLifecycleState,
  ): boolean {
    if (fromState === toState) {
      return false; // No self-transitions
    }

    const validStates =
      PluginLifecycleManager.VALID_TRANSITIONS.get(fromState);
    return validStates ? validStates.has(toState) : false;
  }

  /**
   * Emit state change event to all listeners
   * 
   * @private
   */
  private emitStateChange(event: PluginLifecycleEvent): void {
    // Notify plugin-specific listeners
    const pluginListeners = this.listeners.get(event.pluginId) ?? [];
    pluginListeners.forEach(cb => cb(event));

    // Notify wildcard listeners
    const wildcardListeners = this.listeners.get('*') ?? [];
    wildcardListeners.forEach(cb => cb(event));
  }
}
