import { z } from "zod";

import {
  PlatformEvent,
  PlatformEventSchema,
  EventSourceSchema,
  type EventSource,
} from "./types";

// ---------------------------------------------------------------------------
// Serialization format
// ---------------------------------------------------------------------------

/**
 * Wire format for serialized platform events.
 * Adds `_v` (schema version) to signal deserialization strategy.
 */
export interface SerializedPlatformEvent {
  readonly _v: 1;
  readonly id: string;
  readonly type: string;
  readonly timestamp: number;
  readonly source: EventSource;
  readonly data: unknown;
  readonly correlationId?: string;
  readonly schemaVersion?: string;
}

const SerializedEventSchema: z.ZodType<SerializedPlatformEvent> = z.object({
  _v: z.literal(1),
  id: z.string().min(1),
  type: z.string().min(1),
  timestamp: z.number().int().positive(),
  source: EventSourceSchema,
  data: z.unknown(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

// ---------------------------------------------------------------------------
// Serialize
// ---------------------------------------------------------------------------

/**
 * Serializes a PlatformEvent to a JSON string.
 * Adds `_v: 1` envelope for versioned deserialization.
 */
export function serializeEvent(event: PlatformEvent): string {
  const envelope: SerializedPlatformEvent = {
    _v: 1,
    id: event.id,
    type: event.type,
    timestamp: event.timestamp,
    source: event.source,
    data: event.data,
    ...(event.correlationId !== undefined && {
      correlationId: event.correlationId,
    }),
    ...(event.schemaVersion !== undefined && {
      schemaVersion: event.schemaVersion,
    }),
  };
  return JSON.stringify(envelope);
}

// ---------------------------------------------------------------------------
// Deserialize
// ---------------------------------------------------------------------------

export class EventDeserializationError extends Error {
  constructor(
    message: string,
    public readonly cause?: unknown
  ) {
    super(message);
    this.name = "EventDeserializationError";
  }
}

/**
 * Deserializes a JSON string back into a typed PlatformEvent.
 *
 * @throws EventDeserializationError if the JSON is malformed or fails schema validation.
 */
export function deserializeEvent<T = unknown>(json: string): PlatformEvent<T> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json) as unknown;
  } catch (err) {
    throw new EventDeserializationError("Failed to parse event JSON", err);
  }

  const envelopeResult = SerializedEventSchema.safeParse(parsed);
  if (!envelopeResult.success) {
    throw new EventDeserializationError(
      `Invalid serialized event: ${envelopeResult.error.message}`,
      envelopeResult.error
    );
  }

  const envelope = envelopeResult.data;
  return {
    id: envelope.id,
    type: envelope.type,
    timestamp: envelope.timestamp,
    source: envelope.source,
    data: envelope.data as T,
    ...(envelope.correlationId !== undefined && {
      correlationId: envelope.correlationId,
    }),
    ...(envelope.schemaVersion !== undefined && {
      schemaVersion: envelope.schemaVersion,
    }),
  };
}

// ---------------------------------------------------------------------------
// Validation helper
// ---------------------------------------------------------------------------

/**
 * Validates a raw PlatformEvent object against the schema.
 *
 * @throws EventDeserializationError if validation fails.
 */
export function validateEvent(event: unknown): PlatformEvent {
  const result = PlatformEventSchema.safeParse(event);
  if (!result.success) {
    throw new EventDeserializationError(
      `Event validation failed: ${result.error.message}`,
      result.error
    );
  }
  return result.data as PlatformEvent;
}
