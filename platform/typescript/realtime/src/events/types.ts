/**
 * @ghatana/realtime/events — Platform event base types.
 *
 * This module re-exports all event types from the canonical @ghatana/events
 * package. It previously defined its own types, causing structural incompatibility.
 *
 * @ghatana/events is the single source of truth for:
 *   - PlatformEvent, EventSource, EventSourceType
 *   - EventSourceSchema, PlatformEventSchema
 *   - isPlatformEvent, isEventOfType
 *
 * @doc.type module
 * @doc.purpose Platform-level event base types, emitter, and subscription contracts
 * @doc.layer platform
 * @doc.pattern EventEmitter
 */

// Re-export canonical event types from @ghatana/events.
export type {
  EventSourceType,
  EventSource,
  PlatformEvent,
  EventHandler,
  EventFilter,
  SubscriptionToken,
} from "@ghatana/events";
export {
  EventSourceSchema,
  PlatformEventSchema,
  isPlatformEvent,
  isEventOfType,
} from "@ghatana/events";

// ---------------------------------------------------------------------------
// Realtime-specific contracts
// These are specific to the realtime transport layer (WebSocket / SSE / ActiveJ).
// ---------------------------------------------------------------------------

import type { PlatformEvent } from "@ghatana/events";

/** Handle returned when subscribing to an event stream. */
export interface EventSubscription {
  /** Remove the associated event listener. */
  unsubscribe(): void;
}

/**
 * Generic event emitter contract for the realtime transport layer.
 *
 * Implementors must conform to this interface to be usable by platform
 * tooling (testing utilities, middleware, metrics, etc.).
 */
export interface EventEmitter<T extends PlatformEvent = PlatformEvent> {
  /**
   * Subscribe to events of a given type.
   * @param type - Event type discriminator; "*" means all events.
   * @param handler - Callback invoked for each matching event.
   */
  on(type: string, handler: (event: T) => void): EventSubscription;

  /** Emit an event to all matching subscribers. */
  emit(event: T): void;
}
