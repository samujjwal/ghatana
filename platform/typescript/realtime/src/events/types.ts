/**
 * Platform event base types for @ghatana/realtime.
 *
 * Provides the canonical event model that domain-specific event systems
 * (e.g. DCMAAR browser-extension events) must extend. Consumers MUST NOT
 * reinvent these abstractions — extend them instead.
 *
 * @doc.type module
 * @doc.purpose Platform-level event base types, emitter, and subscription contracts
 * @doc.layer platform
 * @doc.pattern EventEmitter
 */
import { z } from 'zod';

// ---------------------------------------------------------------------------
// Core value types
// ---------------------------------------------------------------------------

/** Canonical event source categories. */
export type EventSourceType = 'browser' | 'server' | 'client' | 'extension';

/** Identifies who emitted an event. */
export interface EventSource {
  readonly type: EventSourceType;
  /** Unique identifier of the emitting instance (tab id, service id, etc.) */
  readonly id: string;
}

// ---------------------------------------------------------------------------
// Base event
// ---------------------------------------------------------------------------

/**
 * Canonical platform event shape.
 *
 * All domain-specific event interfaces MUST extend this type:
 * ```ts
 * import type { PlatformEvent, EventSource } from '@ghatana/realtime/events';
 *
 * export interface BrowserTabEvent extends PlatformEvent<TabEventData> {
 *   source: EventSource & { type: 'browser' };
 * }
 * ```
 */
export interface PlatformEvent<T = unknown> {
  /** Unique event identifier (UUID v4 recommended). */
  readonly id: string;
  /** Discriminated event type string (e.g. 'tab.created'). */
  readonly type: string;
  /** Unix millisecond timestamp of event creation. */
  readonly timestamp: number;
  /** Origin of this event. */
  readonly source: EventSource;
  /** Domain-specific payload. */
  readonly data: T;
  /** Optional correlation id for tracing across service boundaries. */
  readonly correlationId?: string;
}

// ---------------------------------------------------------------------------
// Subscription
// ---------------------------------------------------------------------------

/** Handle returned when subscribing to an event stream. */
export interface EventSubscription {
  /** Remove the associated event listener. */
  unsubscribe(): void;
}

// ---------------------------------------------------------------------------
// Emitter
// ---------------------------------------------------------------------------

/**
 * Generic event emitter contract.
 *
 * Implementors must conform to this interface to be usable by platform
 * tooling (testing utilities, middleware, etc.).
 */
export interface EventEmitter<T extends PlatformEvent = PlatformEvent> {
  /**
   * Subscribe to events of a given type.
   * @param type - Event type discriminator; '*' means all events.
   * @param handler - Callback invoked for each matching event.
   */
  on(type: string, handler: (event: T) => void): EventSubscription;

  /**
   * Emit an event to all matching subscribers.
   */
  emit(event: T): void;
}

// ---------------------------------------------------------------------------
// Zod schema (runtime validation at serialization boundary)
// ---------------------------------------------------------------------------

/** Zod schema for EventSource — validates incoming serialized event sources. */
export const EventSourceSchema = z.object({
  type: z.enum(['browser', 'server', 'client', 'extension']),
  id: z.string().min(1),
});

/**
 * Zod schema for PlatformEvent<unknown>.
 *
 * Use this at deserialization boundaries (WebSocket messages, storage reads)
 * to validate an incoming payload before processing it.
 *
 * ```ts
 * const event = PlatformEventSchema.parse(rawPayload);
 * ```
 */
export const PlatformEventSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  timestamp: z.number().int().positive(),
  source: EventSourceSchema,
  data: z.unknown(),
  correlationId: z.string().min(1).optional(),
});

export type PlatformEventFromSchema = z.infer<typeof PlatformEventSchema>;
