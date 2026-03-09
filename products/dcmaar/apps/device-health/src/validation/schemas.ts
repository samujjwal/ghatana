/**
 * @fileoverview Zod Validation Schemas for DCMaar Extension
 *
 * Provides runtime validation for all data structures used in the extension.
 * Ensures type safety and data integrity for events, configurations, and messages.
 *
 * @module validation/schemas
 */

import { z } from 'zod';

/**
 * Event Schema
 * Validates browser events captured by the extension
 */
export const EventSchema = z.object({
  id: z.string().min(1, { message: 'Event ID is required' }),
  type: z.string().min(1, { message: 'Event type is required' }),
  timestamp: z.number().int().positive({ message: 'Timestamp must be positive' }),
  payload: z.record(z.string(), z.any()),
  metadata: z.record(z.string(), z.any()).optional(),
  correlationId: z.string().optional(),
});

export type ValidatedEvent = z.infer<typeof EventSchema>;

/**
 * Browser Event Source Configuration Schema
 */
export const BrowserEventSourceConfigSchema = z.object({
  id: z.string().min(1, { message: 'Source ID is required' }),
  events: z.array(z.enum(['tabs', 'navigation', 'performance', 'interactions', 'windows'])),

  sampling: z.object({
    rate: z.number().min(0).max(1, { message: 'Sampling rate must be between 0 and 1' }),
    strategy: z.enum(['uniform', 'adaptive']).optional().default('uniform'),
  }).optional(),

  filters: z.object({
    includePatterns: z.array(z.string()).optional(),
    excludePatterns: z.array(z.string()).optional(),
  }).optional(),

  batching: z.object({
    size: z.number().int().positive({ message: 'Batch size must be positive' }),
    flushIntervalMs: z.number().int().positive({ message: 'Flush interval must be positive' }),
  }).optional(),

  performance: z.object({
    captureWebVitals: z.boolean(),
    captureResourceTiming: z.boolean(),
    captureNavigationTiming: z.boolean(),
  }).optional(),

  throttling: z.object({
    tabUpdates: z.number().int().positive().optional(),
    navigation: z.number().int().positive().optional(),
    interactions: z.number().int().positive().optional(),
  }).optional(),
});

export type ValidatedBrowserEventSourceConfig = z.infer<typeof BrowserEventSourceConfigSchema>;

/**
 * IndexedDB Sink Configuration Schema
 */
export const IndexedDBSinkConfigSchema = z.object({
  id: z.string().min(1, { message: 'Sink ID is required' }),
  dbName: z.string().min(1, { message: 'Database name is required' }),
  storeName: z.string().min(1, { message: 'Store name is required' }),

  batchSize: z.number().int().positive().optional().default(50),
  flushIntervalMs: z.number().int().positive().optional().default(5000),

  maxEvents: z.number().int().positive().optional().default(10000),
  retentionDays: z.number().int().positive().optional().default(7),

  compression: z.boolean().optional().default(false),
  autoCleanup: z.boolean().optional().default(true),
  cleanupIntervalMs: z.number().int().positive().optional().default(3600000),
});

export type ValidatedIndexedDBSinkConfig = z.infer<typeof IndexedDBSinkConfigSchema>;

/**
 * Data Flow Configuration Schema
 */
export const DataFlowConfigSchema = z.object({
  source: BrowserEventSourceConfigSchema,
  sink: IndexedDBSinkConfigSchema,
  pipeline: z.object({
    processors: z.array(z.any()).optional().default([]),
  }).optional(),
  monitoring: z.boolean().optional().default(true),
});

export type ValidatedDataFlowConfig = z.infer<typeof DataFlowConfigSchema>;

/**
 * Tab Event Payload Schema
 */
export const TabEventPayloadSchema = z.object({
  tabId: z.number().int().optional(),
  url: z.string().url().optional(),
  title: z.string().optional(),
  active: z.boolean().optional(),
  windowId: z.number().int().optional(),
  changeInfo: z.record(z.string(), z.any()).optional(),
});

/**
 * Navigation Event Payload Schema
 */
export const NavigationEventPayloadSchema = z.object({
  tabId: z.number().int(),
  url: z.string().url(),
  frameId: z.number().int().optional(),
  timeStamp: z.number().optional(),
});

/**
 * Performance Metrics Payload Schema
 */
export const PerformanceMetricsPayloadSchema = z.object({
  tabId: z.number().int(),
  url: z.string().url(),
  metrics: z.object({
    navigation: z.object({
      domContentLoaded: z.number().optional(),
      loadComplete: z.number().optional(),
      domInteractive: z.number().optional(),
      domComplete: z.number().optional(),
    }).optional(),
    paint: z.array(z.object({
      name: z.string(),
      startTime: z.number(),
    })).optional(),
    memory: z.object({
      usedJSHeapSize: z.number().optional(),
      totalJSHeapSize: z.number().optional(),
    }).optional(),
  }),
  timestamp: z.number().int().positive(),
});

/**
 * Interaction Event Payload Schema
 */
export const InteractionEventPayloadSchema = z.object({
  tabId: z.number().int().optional(),
  url: z.string().optional(),
  action: z.enum(['click', 'scroll', 'mousemove', 'resize', 'pointermove', 'submit']),
  target: z.string().optional(),
  timestamp: z.number().int().positive(),
});

/**
 * Window Event Payload Schema
 */
export const WindowEventPayloadSchema = z.object({
  windowId: z.number().int(),
  focused: z.boolean(),
});

/**
 * Storage Item Schema (for IndexedDB)
 */
export const StorageItemSchema = z.object({
  id: z.string(),
  event: EventSchema,
  storedAt: z.number().int().positive(),
  expiresAt: z.number().int().positive().optional(),
});

export type ValidatedStorageItem = z.infer<typeof StorageItemSchema>;

/**
 * Extension Message Schema (for internal communication)
 */
export const ExtensionMessageSchema = z.object({
  type: z.string().min(1),
  payload: z.any(),
  sender: z.string().optional(),
  timestamp: z.number().int().positive().optional(),
  messageId: z.string().optional(),
});

export type ValidatedExtensionMessage = z.infer<typeof ExtensionMessageSchema>;

/**
 * Browser Runtime Message Schema
 */
export const RuntimeMessageSchema = z.object({
  type: z.string(),
  action: z.string().optional(),
  data: z.any().optional(),
  tabId: z.number().int().optional(),
  timestamp: z.number().int().optional(),
});

export type ValidatedRuntimeMessage = z.infer<typeof RuntimeMessageSchema>;

/**
 * Validation Helper Functions
 */

/**
 * Validate and parse data with a schema
 * @throws {z.ZodError} if validation fails
 */
export function validate<T>(schema: z.ZodSchema<T>, data: unknown): T {
  return schema.parse(data);
}

/**
 * Safely validate data and return result
 */
export function safeValidate<T>(
  schema: z.ZodSchema<T>,
  data: unknown
): { success: true; data: T } | { success: false; error: z.ZodError } {
  const result = schema.safeParse(data);

  if (result.success) {
    return { success: true, data: result.data };
  } else {
    return { success: false, error: result.error };
  }
}

/**
 * Validate an array of items
 */
export function validateArray<T>(schema: z.ZodSchema<T>, items: unknown[]): T[] {
  return items.map((item) => validate(schema, item));
}

/**
 * Create a validator function from a schema
 */
export function createValidator<T>(schema: z.ZodSchema<T>) {
  return (data: unknown): T => validate(schema, data);
}

/**
 * Create a safe validator function from a schema
 */
export function createSafeValidator<T>(schema: z.ZodSchema<T>) {
  return (data: unknown) => safeValidate(schema, data);
}

/**
 * Format Zod errors for logging
 */
export function formatZodError(error: z.ZodError): string {
  return error.issues
    .map((err) => `${err.path.join('.')}: ${err.message}`)
    .join(', ');
}

/**
 * Validate event before processing
 */
export function validateEvent(event: unknown): ValidatedEvent {
  try {
    return validate(EventSchema, event);
  } catch (error) {
    if (error instanceof z.ZodError) {
      throw new Error(`Event validation failed: ${formatZodError(error)}`);
    }
    throw error;
  }
}

/**
 * Validate configuration before use
 */
export function validateDataFlowConfig(config: unknown): ValidatedDataFlowConfig {
  try {
    return validate(DataFlowConfigSchema, config);
  } catch (error) {
    if (error instanceof z.ZodError) {
      throw new Error(`Configuration validation failed: ${formatZodError(error)}`);
    }
    throw error;
  }
}
