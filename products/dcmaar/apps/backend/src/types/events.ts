/**
 * Event validation schemas for desktop telemetry events
 *
 * <p><b>Purpose</b><br>
 * Defines Joi validation schemas for all event types that can be ingested
 * from agent-desktop. Ensures data integrity and consistent structure.
 *
 * <p><b>Supported Event Types</b><br>
 * - WINDOW_FOCUS: User focused a window
 * - PROCESS_START: New process started
 * - PROCESS_END: Process terminated
 * - IDLE_CHANGED: Idle state changed
 * - SESSION_START: Session/device startup
 * - SESSION_END: Session/device shutdown
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { eventValidationSchema } from '../types/events';
 * 
 * const event = { type: 'WINDOW_FOCUS', ... };
 * const validated = eventValidationSchema.validate(event);
 * if (validated.error) {
 *   console.error('Invalid event:', validated.error);
 * }
 * }</pre>
 *
 * @doc.type utility
 * @doc.purpose Event validation schemas
 * @doc.layer backend
 * @doc.pattern Data Validation
 */

import { z } from 'zod';

/**
 * Event types enum
 */
export enum EventType {
  WINDOW_FOCUS = 'WINDOW_FOCUS',
  PROCESS_START = 'PROCESS_START',
  PROCESS_END = 'PROCESS_END',
  IDLE_CHANGED = 'IDLE_CHANGED',
  SESSION_START = 'SESSION_START',
  SESSION_END = 'SESSION_END',
}

/**
 * Base event schema - all events must have these fields
 */
const baseEventSchema = z.object({
  type: z.enum([
    'WINDOW_FOCUS',
    'PROCESS_START',
    'PROCESS_END',
    'IDLE_CHANGED',
    'SESSION_START',
    'SESSION_END',
  ]),
  timestamp: z.number().positive().int(),
  sessionId: z.string().uuid().optional(),
  data: z.record(z.string(), z.any()).optional(),
});

/**
 * WINDOW_FOCUS event - user focused a window
 */
const windowFocusEventSchema = baseEventSchema.extend({
  type: z.literal('WINDOW_FOCUS'),
  windowTitle: z.string().max(500).optional(),
  processName: z.string().max(255).optional(),
  processPath: z.string().max(500).optional(),
  appCategory: z.string().max(100).optional(),
});

/**
 * PROCESS_START event - new process started
 */
const processStartEventSchema = baseEventSchema.extend({
  type: z.literal('PROCESS_START'),
  processName: z.string().max(255),
  processPath: z.string().max(500),
  appCategory: z.string().max(100).optional(),
});

/**
 * PROCESS_END event - process terminated
 */
const processEndEventSchema = baseEventSchema.extend({
  type: z.literal('PROCESS_END'),
  processName: z.string().max(255),
  processPath: z.string().max(500),
});

/**
 * IDLE_CHANGED event - idle state changed
 */
const idleChangedEventSchema = baseEventSchema.extend({
  type: z.literal('IDLE_CHANGED'),
  isIdle: z.boolean(),
  idleSeconds: z.number().int().positive().optional(),
});

/**
 * SESSION_START event - session/device startup
 */
const sessionStartEventSchema = baseEventSchema.extend({
  type: z.literal('SESSION_START'),
  sessionId: z.string().uuid(),
});

/**
 * SESSION_END event - session/device shutdown
 */
const sessionEndEventSchema = baseEventSchema.extend({
  type: z.literal('SESSION_END'),
  sessionId: z.string().uuid(),
});

/**
 * Combined schema accepting any valid event type
 */
export const eventValidationSchema = z.discriminatedUnion('type', [
  windowFocusEventSchema,
  processStartEventSchema,
  processEndEventSchema,
  idleChangedEventSchema,
  sessionStartEventSchema,
  sessionEndEventSchema,
]);

/**
 * Batch events schema for EVENTS message
 */
export const batchEventsSchema = z.object({
  type: z.literal('EVENTS'),
  events: z.array(eventValidationSchema).min(1).max(1000),
});

/**
 * IDENTIFY message schema
 */
export const identifyMessageSchema = z.object({
  type: z.literal('IDENTIFY'),
  token: z.string(),
});

/**
 * Validate event by type
 */
export function validateEvent(event: any): { valid: boolean; error?: string } {
  const result = eventValidationSchema.safeParse(event);
  if (!result.success) {
    return { valid: false, error: result.error.issues[0]?.message };
  }
  return { valid: true };
}

/**
 * Validate batch of events
 */
export function validateEventBatch(batch: any): { valid: boolean; error?: string } {
  const result = batchEventsSchema.safeParse(batch);
  if (!result.success) {
    return { valid: false, error: result.error.issues[0]?.message };
  }
  return { valid: true };
}

// Export types
export type Event = z.infer<typeof eventValidationSchema>;
export type BatchEvents = z.infer<typeof batchEventsSchema>;
export type IdentifyMessage = z.infer<typeof identifyMessageSchema>;
