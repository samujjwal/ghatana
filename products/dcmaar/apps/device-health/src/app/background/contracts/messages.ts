/**
 * @fileoverview Message Contracts for Extension Communication
 *
 * Defines canonical message types for communication between sources and the extension.
 * All messages from sources must conform to one of these contracts.
 *
 * Three primary message categories:
 * 1. SinkConfigMessage - Configuration for data sinks
 * 2. CommandMessage - Commands to execute
 * 3. ProcessMessage - Custom process definitions
 *
 * @see {@link https://github.com/dcmaar/connectors} for connector library contracts
 */

import { z } from 'zod';

/**
 * Base message envelope shared by all message types
 */
export const BaseMessageSchema = z.object({
  /** Unique message identifier */
  id: z.string().uuid(),
  /** Message type discriminator */
  type: z.enum(['sink-config', 'command', 'process'] as const),
  /** ISO 8601 timestamp */
  timestamp: z.string().datetime(),
  /** Source identifier (e.g., 'desktop-provider', 'agent-bridge') */
  sourceId: z.string().min(1),
  /** Correlation ID for tracing */
  correlationId: z.string().uuid().optional(),
  /** Message version for schema evolution */
  version: z.string().default('1.0.0'),
});

export type BaseMessage = z.infer<typeof BaseMessageSchema>;

/**
 * Sink Configuration Message
 *
 * Defines or updates a data sink configuration. Sinks are destinations where
 * events/data are sent. This message type allows sources to dynamically
 * configure where data should be routed.
 *
 * @example
 * ```typescript
 * const sinkConfig: SinkConfigMessage = {
 *   id: '550e8400-e29b-41d4-a716-446655440000',
 *   type: 'sink-config',
 *   timestamp: new Date().toISOString(),
 *   sourceId: 'desktop-provider',
 *   payload: {
 *     sinkId: 'http-sink-1',
 *     sinkType: 'http',
 *     endpoint: 'https://api.example.com/events',
 *     config: {
 *       method: 'POST',
 *       headers: { 'Content-Type': 'application/json' },
 *       auth: { type: 'bearer', token: 'secret' },
 *       timeout: 30000,
 *       retryPolicy: { maxRetries: 3, backoffMs: 1000 }
 *     },
 *     enabled: true
 *   }
 * };
 * ```
 */
export const SinkConfigPayloadSchema = z.object({
  /** Unique sink identifier */
  sinkId: z.string().min(1),
  /** Type of sink (http, websocket, file, etc.) */
  sinkType: z.enum(['http', 'websocket', 'file', 'memory', 'ipc', 'custom'] as const),
  /** Sink endpoint/destination */
  endpoint: z.string().min(1),
  /** Sink-specific configuration */
  config: z.record(z.string(), z.unknown()).optional(),
  /** Whether this sink is enabled */
  enabled: z.boolean().default(true),
  /** Optional metadata */
  metadata: z.record(z.string(), z.string()).optional(),
});

export type SinkConfigPayload = z.infer<typeof SinkConfigPayloadSchema>;

export const SinkConfigMessageSchema = BaseMessageSchema.extend({
  type: z.literal('sink-config'),
  payload: SinkConfigPayloadSchema,
});

export type SinkConfigMessage = z.infer<typeof SinkConfigMessageSchema>;

/**
 * Command Message
 *
 * Represents a command to be executed by the extension. Commands can control
 * extension behavior, trigger actions, or request information.
 *
 * @example
 * ```typescript
 * const command: CommandMessage = {
 *   id: '550e8400-e29b-41d4-a716-446655440001',
 *   type: 'command',
 *   timestamp: new Date().toISOString(),
 *   sourceId: 'desktop-provider',
 *   payload: {
 *     commandType: 'start-capture',
 *     args: {
 *       captureTypes: ['network', 'performance'],
 *       filters: { domains: ['example.com'] }
 *     },
 *     timeout: 5000,
 *     expectsResponse: true
 *   }
 * };
 * ```
 */
export const CommandPayloadSchema = z.object({
  /** Command type identifier */
  commandType: z.string().min(1),
  /** Command arguments */
  args: z.record(z.string(), z.unknown()).optional(),
  /** Timeout in milliseconds */
  timeout: z.number().int().positive().optional().default(30000),
  /** Whether a response is expected */
  expectsResponse: z.boolean().default(true),
  /** Optional metadata */
  metadata: z.record(z.string(), z.string()).optional(),
});

export type CommandPayload = z.infer<typeof CommandPayloadSchema>;

export const CommandMessageSchema = BaseMessageSchema.extend({
  type: z.literal('command'),
  payload: CommandPayloadSchema,
});

export type CommandMessage = z.infer<typeof CommandMessageSchema>;

/**
 * Process Message
 *
 * Defines a custom process to be executed by the extension. Processes are
 * long-running or scheduled operations with specific lifecycle management.
 *
 * @example
 * ```typescript
 * const process: ProcessMessage = {
 *   id: '550e8400-e29b-41d4-a716-446655440002',
 *   type: 'process',
 *   timestamp: new Date().toISOString(),
 *   sourceId: 'desktop-provider',
 *   payload: {
 *     processId: 'data-sync-1',
 *     processType: 'scheduled',
 *     action: 'start',
 *     config: {
 *       schedule: '0 * * * *',
 *       handler: 'syncData',
 *       retryPolicy: { maxRetries: 3 }
 *     },
 *     metadata: { priority: 'high' }
 *   }
 * };
 * ```
 */
export const ProcessPayloadSchema = z.object({
  /** Unique process identifier */
  processId: z.string().min(1),
  /** Type of process (scheduled, background, worker, etc.) */
  processType: z.enum(['scheduled', 'background', 'worker', 'custom'] as const),
  /** Process action (start, stop, restart, pause) */
  action: z.enum(['start', 'stop', 'restart', 'pause', 'resume'] as const),
  /** Process configuration */
  config: z.record(z.string(), z.unknown()).optional(),
  /** Optional metadata */
  metadata: z.record(z.string(), z.string()).optional(),
});

export type ProcessPayload = z.infer<typeof ProcessPayloadSchema>;

export const ProcessMessageSchema = BaseMessageSchema.extend({
  type: z.literal('process'),
  payload: ProcessPayloadSchema,
});

export type ProcessMessage = z.infer<typeof ProcessMessageSchema>;

/**
 * Union type for all message types
 */
export const AnyMessageSchema = z.discriminatedUnion('type', [
  SinkConfigMessageSchema,
  CommandMessageSchema,
  ProcessMessageSchema,
]);

export type AnyMessage = z.infer<typeof AnyMessageSchema>;

/**
 * Message validation result
 */
export interface MessageValidationResult {
  valid: boolean;
  error?: string;
  data?: AnyMessage;
}

/**
 * Validates a message against the appropriate schema
 *
 * @param message - The message to validate
 * @returns Validation result with parsed data or error
 */
export function validateMessage(message: unknown): MessageValidationResult {
  try {
    const data = AnyMessageSchema.parse(message);
    return { valid: true, data };
  } catch (error) {
    const errorMessage = error instanceof z.ZodError
      ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
      : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Type guard for SinkConfigMessage
 */
export function isSinkConfigMessage(message: AnyMessage): message is SinkConfigMessage {
  return message.type === 'sink-config';
}

/**
 * Type guard for CommandMessage
 */
export function isCommandMessage(message: AnyMessage): message is CommandMessage {
  return message.type === 'command';
}

/**
 * Type guard for ProcessMessage
 */
export function isProcessMessage(message: AnyMessage): message is ProcessMessage {
  return message.type === 'process';
}
