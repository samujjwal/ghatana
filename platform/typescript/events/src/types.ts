import { z } from "zod";

// ---------------------------------------------------------------------------
// Source types
// ---------------------------------------------------------------------------

/**
 * Identifies the origin of a platform event.
 * Discriminated by `type` for deterministic routing and filtering.
 */
export type EventSourceType =
  | "browser"
  | "server"
  | "client"
  | "extension"
  | "mobile"
  | "desktop";

export interface EventSource {
  /** Discriminated origin type. */
  readonly type: EventSourceType;
  /** Unique identifier of the emitting component (e.g. tab-id, service-id). */
  readonly id: string;
  /** Optional human-readable label for diagnostics. */
  readonly label?: string;
}

// ---------------------------------------------------------------------------
// Base event contract
// ---------------------------------------------------------------------------

/**
 * Platform-level base event. All events in the Ghatana event system must
 * structurally match this shape.
 *
 * @typeParam T - The domain-specific payload type.
 */
export interface PlatformEvent<T = unknown> {
  /** Unique event identifier (UUID v4 recommended). */
  readonly id: string;
  /**
   * Discriminated event type string.
   * Convention: `<domain>.<action>` (e.g. `tab.created`, `user.logged-in`).
   */
  readonly type: string;
  /** Unix millisecond timestamp of event creation. */
  readonly timestamp: number;
  /** Event origin descriptor. */
  readonly source: EventSource;
  /** Domain-specific payload. */
  readonly data: T;
  /** Optional correlation ID for distributed tracing. */
  readonly correlationId?: string;
  /** Optional schema version for compatibility handling. */
  readonly schemaVersion?: string;
}

// ---------------------------------------------------------------------------
// Handler and filter contracts
// ---------------------------------------------------------------------------

/**
 * A function that handles a platform event.
 *
 * @typeParam T - The concrete event type this handler accepts.
 */
export type EventHandler<T extends PlatformEvent = PlatformEvent> = (
  event: T
) => void | Promise<void>;

/**
 * A predicate that decides whether an event should be processed.
 *
 * @typeParam T - The concrete event type this filter operates on.
 */
export type EventFilter<T extends PlatformEvent = PlatformEvent> = (
  event: T
) => boolean;

// ---------------------------------------------------------------------------
// Subscription token
// ---------------------------------------------------------------------------

/**
 * Opaque token returned from dispatcher.subscribe(). Used to unsubscribe.
 * Treat as immutable.
 */
export interface SubscriptionToken {
  readonly id: string;
  readonly eventType: string;
}

// ---------------------------------------------------------------------------
// Zod schemas for runtime validation
// ---------------------------------------------------------------------------

export const EventSourceSchema: z.ZodType<EventSource> = z.object({
  type: z.enum(["browser", "server", "client", "extension", "mobile", "desktop"]),
  id: z.string().min(1),
  label: z.string().optional(),
});

export const PlatformEventSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  timestamp: z.number().int().positive(),
  source: EventSourceSchema,
  data: z.unknown(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

// ---------------------------------------------------------------------------
// Type guards
// ---------------------------------------------------------------------------

/**
 * Type guard: determines whether an unknown value is a valid `PlatformEvent`.
 */
export function isPlatformEvent(value: unknown): value is PlatformEvent {
  return PlatformEventSchema.safeParse(value).success;
}

/**
 * Type guard: determines whether an event matches a specific type discriminant.
 *
 * @example
 * ```ts
 * if (isEventOfType(event, 'tab.created')) { ... }
 * ```
 */
export function isEventOfType<T extends PlatformEvent>(
  event: PlatformEvent,
  type: T["type"]
): event is T {
  return event.type === type;
}
