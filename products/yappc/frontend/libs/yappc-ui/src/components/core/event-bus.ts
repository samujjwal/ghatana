/**
 * Event Bus System
 *
 * A global event communication system for component-to-component messaging
 * with support for typed events, middleware, and React hooks integration.
 *
 * @packageDocumentation
 */
/* eslint-disable @typescript-eslint/no-explicit-any */

// ============================================================================
// Types
// ============================================================================

/**
 * Event bus core implementation and React hook integration.
 */
export type EventHandler<TPayload = unknown> = (
  payload: TPayload
) => void | Promise<void>;

/**
 *
 */
export type EventMiddleware = (
  eventName: string,
  payload: AnyPayload,
  next: () => void | Promise<void>
) => void | Promise<void>;

/**
 * Central alias for any payload used across the event bus. We keep this
 * narrow and explicit so the rest of the codebase can track places where
 * typed payloads would be preferred.
 */
export type AnyPayload = unknown;

/**
 * Represents a subscription returned by event bus handlers.
 */
export interface EventSubscription {
  /**
   * Unsubscribe from the event
   */
  unsubscribe: () => void;
}

/**
 * Options for configuring EventBus behavior.
 */
export interface EventBusOptions {
  /**
   * Enable debug logging
   * @default false
   */
  debug?: boolean;

  /**
   * Maximum number of listeners per event
   * @default 100
   */
  maxListeners?: number;

  /**
   * Enable event history tracking
   * @default false
   */
  trackHistory?: boolean;

  /**
   * Maximum history size
   * @default 50
   */
  historySize?: number;
}

/**
 * Metadata stored for each event when history tracking is enabled.
 */
export interface EventMetadata {
  eventName: string;
  timestamp: number;
  payload: unknown;
}

// ============================================================================
// Event Bus Implementation
// ============================================================================

/**
 * Concrete EventBus implementation with listener, middleware and history support.
 */
class EventBusImpl {
  private listeners = new Map<string, Set<EventHandler>>();
  private onceListeners = new Map<string, Set<EventHandler>>();
  private middleware: EventMiddleware[] = [];
  private options: Required<EventBusOptions>;
  private history: EventMetadata[] = [];

  /**
   * Create a new EventBus instance with optional configuration.
   *
   * @param options - configuration for debug, listener limits and history
   */
  constructor(options: EventBusOptions = {}) {
    this.options = {
      debug: options.debug ?? false,
      maxListeners: options.maxListeners ?? 100,
      trackHistory: options.trackHistory ?? false,
      historySize: options.historySize ?? 50,
    };
  }

  /**
   * Subscribe to an event
   */
  on<TPayload = unknown>(
    eventName: string,
    handler: EventHandler<TPayload>
  ): EventSubscription {
    let handlers = this.listeners.get(eventName);
    if (!handlers) {
      handlers = new Set<EventHandler>();
      this.listeners.set(eventName, handlers);
    }

    // Check max listeners
    if (handlers.size >= this.options.maxListeners) {
      console.warn(
        `EventBus: Maximum listeners (${this.options.maxListeners}) reached for event "${eventName}"`
      );
    }

    handlers.add(handler as EventHandler);

    if (this.options.debug) {
      console.warn(
        `EventBus: Subscribed to "${eventName}" (${handlers.size} listeners)`
      );
    }

    return {
      unsubscribe: () => this.off(eventName, handler),
    };
  }

  /**
   * Subscribe to an event once (auto-unsubscribe after first emit)
   */
  once<TPayload = unknown>(
    eventName: string,
    handler: EventHandler<TPayload>
  ): EventSubscription {
    let handlers = this.onceListeners.get(eventName);
    if (!handlers) {
      handlers = new Set<EventHandler>();
      this.onceListeners.set(eventName, handlers);
    }
    handlers.add(handler as EventHandler);

    if (this.options.debug) {
      console.warn(`EventBus: Subscribed once to "${eventName}"`);
    }

    return {
      unsubscribe: () => {
        handlers.delete(handler as EventHandler);
      },
    };
  }

  /**
   * Unsubscribe from an event
   */
  off<TPayload = unknown>(
    eventName: string,
    handler: EventHandler<TPayload>
  ): void {
    const handlers = this.listeners.get(eventName);
    if (handlers) {
      handlers.delete(handler as EventHandler);
      if (handlers.size === 0) {
        this.listeners.delete(eventName);
      }

      if (this.options.debug) {
        console.warn(
          `EventBus: Unsubscribed from "${eventName}" (${handlers.size} remaining)`
        );
      }
    }

    const onceHandlers = this.onceListeners.get(eventName);
    if (onceHandlers) {
      onceHandlers.delete(handler as EventHandler);
      if (onceHandlers.size === 0) {
        this.onceListeners.delete(eventName);
      }
    }
  }

  /**
   * Emit an event
   */
  async emit<TPayload = unknown>(
    eventName: string,
    payload?: TPayload
  ): Promise<void> {
    if (this.options.debug) {
      console.warn(`EventBus: Emitting "${eventName}"`);
    }

    // Track history
    if (this.options.trackHistory) {
      this.history.push({
        eventName,
        timestamp: Date.now(),
        payload,
      });

      // Trim history
      if (this.history.length > this.options.historySize) {
        this.history = this.history.slice(-this.options.historySize);
      }
    }

    // Execute middleware chain
    const executeHandlers = async () => {
      // Regular listeners
      const handlers = this.listeners.get(eventName);
      if (handlers) {
        const promises = Array.from(handlers).map((handler) => {
          try {
            return Promise.resolve(handler(payload));
          } catch (error) {
            console.error(
              `EventBus: Error in handler for "${eventName}"`,
              error
            );
            return Promise.resolve();
          }
        });
        await Promise.all(promises);
      }

      // Once listeners
      const onceHandlers = this.onceListeners.get(eventName);
      if (onceHandlers) {
        const onceArray = Array.from(onceHandlers);
        // Clear once listeners before execution
        this.onceListeners.delete(eventName);

        const promises = onceArray.map((handler) => {
          try {
            return Promise.resolve(handler(payload));
          } catch (error) {
            console.error(
              `EventBus: Error in once handler for "${eventName}"`,
              error
            );
            return Promise.resolve();
          }
        });
        await Promise.all(promises);
      }
    };

    // Execute through middleware chain
    if (this.middleware.length > 0) {
      await this.executeMiddleware(0, eventName, payload, executeHandlers);
    } else {
      await executeHandlers();
    }
  }

  /**
   * Execute middleware chain
   */
  private async executeMiddleware(
    _index: number,
    eventName: string,
    _payload: unknown,
    finalHandler: () => Promise<void>
  ): Promise<void> {
    // Iterate over middleware sequentially. Each middleware receives a next
    // function which, when called, continues to the next middleware. We
    // implement this by chaining async calls.
    const mws = [...this.middleware];

    let idx = 0;
    const runner = async (): Promise<void> => {
      if (idx >= mws.length) {
        await finalHandler();
        return;
      }

      const mw = mws[idx++];
      await mw(eventName, _payload, runner);
    };

    await runner();
  }

  /**
   * Add middleware
   */
  use(middleware: EventMiddleware): void {
    this.middleware.push(middleware);

    if (this.options.debug) {
      console.warn(
        `EventBus: Added middleware (${this.middleware.length} total)`
      );
    }
  }

  /**
   * Remove middleware
   */
  removeMiddleware(middleware: EventMiddleware): void {
    const index = this.middleware.indexOf(middleware);
    if (index !== -1) {
      this.middleware.splice(index, 1);
    }
  }

  /**
   * Remove all listeners for an event
   */
  removeAllListeners(eventName?: string): void {
    if (eventName) {
      this.listeners.delete(eventName);
      this.onceListeners.delete(eventName);
      if (this.options.debug) {
        console.warn(`EventBus: Removed all listeners for "${eventName}"`);
      }
    } else {
      this.listeners.clear();
      this.onceListeners.clear();
      if (this.options.debug) {
        console.warn(`EventBus: Removed all listeners`);
      }
    }
  }

  /**
   * Get listener count for an event
   */
  listenerCount(eventName: string): number {
    const count =
      (this.listeners.get(eventName)?.size || 0) +
      (this.onceListeners.get(eventName)?.size || 0);
    return count;
  }

  /**
   * Get all event names
   */
  eventNames(): string[] {
    const names = new Set([
      ...this.listeners.keys(),
      ...this.onceListeners.keys(),
    ]);
    return Array.from(names);
  }

  /**
   * Get event history
   */
  getHistory(): EventMetadata[] {
    return [...this.history];
  }

  /**
   * Clear event history
   */
  clearHistory(): void {
    this.history = [];
  }

  /**
   * Wait for an event to be emitted
   */
  async waitFor<TPayload = unknown>(
    eventName: string,
    timeout?: number
  ): Promise<TPayload> {
    return new Promise((resolve, reject) => {
      let timeoutId: NodeJS.Timeout | undefined;

      const handler = (payload: TPayload) => {
        if (timeoutId) clearTimeout(timeoutId);
        resolve(payload);
      };

      this.once(eventName, handler);

      if (timeout) {
        timeoutId = setTimeout(() => {
          this.off(eventName, handler);
          reject(
            new Error(`EventBus: Timeout waiting for event "${eventName}"`)
          );
        }, timeout);
      }
    });
  }
}

// ============================================================================
// Global Event Bus Instance
// ============================================================================

export const eventBus = new EventBusImpl({
  debug: process.env.NODE_ENV === 'development',
  trackHistory: process.env.NODE_ENV === 'development',
});

// ============================================================================
// React Hook
// ============================================================================

import { useEffect, useCallback, useRef } from 'react';

/**
 * React hook for subscribing to event bus events
 */
export function useEventBus<TPayload = unknown>(
  eventName: string,
  handler: EventHandler<TPayload>,
  deps: React.DependencyList = []
): void {
  const handlerRef = useRef(handler);

  // Update handler ref when it changes
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    const wrappedHandler = (payload: TPayload) => {
      handlerRef.current(payload);
    };

    const subscription = eventBus.on(eventName, wrappedHandler);

    return () => {
      subscription.unsubscribe();
    };
  }, [eventName, ...deps]);
}

/**
 * React hook for emitting events
 */
export function useEventEmitter<TPayload = unknown>(
  eventName: string
): (payload?: TPayload) => Promise<void> {
  return useCallback(
    (payload?: TPayload) => {
      return eventBus.emit(eventName, payload);
    },
    [eventName]
  );
}

/**
 * React hook for one-time event subscription
 */
export function useEventBusOnce<TPayload = unknown>(
  eventName: string,
  handler: EventHandler<TPayload>,
  deps: React.DependencyList = []
): void {
  const handlerRef = useRef(handler);

  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    const wrappedHandler = (payload: TPayload) => {
      handlerRef.current(payload);
    };

    const subscription = eventBus.once(eventName, wrappedHandler);

    return () => {
      subscription.unsubscribe();
    };
  }, [eventName, ...deps]);
}

// ============================================================================
// Built-in Middleware
// ============================================================================

/**
 * Logger middleware
 */
export const loggerMiddleware: EventMiddleware = (eventName, payload, next) => {
  // Use a single warn entry to satisfy eslint no-console restrictions while
  // retaining the debug information.
  console.warn(`🔔 Event: ${eventName}`, {
    payload,
    timestamp: new Date().toISOString(),
  });
  next();
};

/**
 * Throttle middleware factory
 */
export function createThrottleMiddleware(delay: number): EventMiddleware {
  const lastEmit = new Map<string, number>();

  return (eventName, _payload, next) => {
    const now = Date.now();
    const last = lastEmit.get(eventName) || 0;

    if (now - last >= delay) {
      lastEmit.set(eventName, now);
      next();
    }
  };
}

/**
 * Debounce middleware factory
 */
export function createDebounceMiddleware(delay: number): EventMiddleware {
  const timeouts = new Map<string, NodeJS.Timeout>();

  return (eventName, _payload, next) => {
    const existingTimeout = timeouts.get(eventName);
    if (existingTimeout) {
      clearTimeout(existingTimeout);
    }

    const timeout = setTimeout(() => {
      next();
      timeouts.delete(eventName);
    }, delay);

    timeouts.set(eventName, timeout);
  };
}

/**
 * Filter middleware factory
 */
export function createFilterMiddleware(
  predicate: (eventName: string, payload: unknown) => boolean
): EventMiddleware {
  return (eventName, payload, next) => {
    if (predicate(eventName, payload)) {
      next();
    }
  };
}

/**
 * Transform middleware factory
 */
export function createTransformMiddleware(
  transformer: (eventName: string, payload: unknown) => any
): EventMiddleware {
  return (eventName, payload, next) => {
    // Call transformer for side-effects. The current EventBus implementation
    // does not propagate a transformed payload to handlers. If a payload
    // transformation is desired, update EventBusImpl.emit to use the
    // transformer result when invoking handlers.
    void transformer(eventName, payload);
    next();
  };
}

// ============================================================================
// Typed Event Definitions
// ============================================================================

/**
 * Define typed events for better type safety
 */
export interface TypedEventMap {
  // User events
  'user:login': { userId: string; username: string };
  'user:logout': { userId: string };
  'user:update': { userId: string; data: Record<string, unknown> };

  // UI events
  'ui:theme-change': { theme: 'light' | 'dark' };
  'ui:notification': {
    message: string;
    type: 'success' | 'error' | 'warning' | 'info';
  };
  'ui:modal-open': { modalId: string };
  'ui:modal-close': { modalId: string };

  // Data events
  'data:refresh': { source: string };
  'data:update': { entityType: string; entityId: string; data: Record<string, unknown> };
  'data:delete': { entityType: string; entityId: string };

  // Form events
  'form:submit': { formId: string; data: Record<string, unknown> };
  'form:validate': {
    formId: string;
    isValid: boolean;
    errors: Record<string, string>;
  };
  'form:reset': { formId: string };
}

/**
 * Type-safe event emitter
 */
export function emit<K extends keyof TypedEventMap>(
  eventName: K,
  payload: TypedEventMap[K]
): Promise<void> {
  return eventBus.emit(eventName, payload);
}

/**
 * Type-safe event subscriber
 */
export function on<K extends keyof TypedEventMap>(
  eventName: K,
  handler: EventHandler<TypedEventMap[K]>
): EventSubscription {
  return eventBus.on(eventName, handler);
}

export default eventBus;
