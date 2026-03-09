/**
 * @fileoverview Extension Configuration Schema
 *
 * Defines the configuration structure for the extension, integrating with
 * the connectors library contracts for sources and sinks.
 *
 * Configuration is loaded at startup and must be validated before any
 * services are initialized.
 *
 * @see {@link ../messages.ts} for message contracts
 */

import { z } from 'zod';
// TODO: Fix import issue with @ghatana/dcmaar-connectors - Vite can't resolve CommonJS exports
// import { ConnectionOptionsSchema } from '@ghatana/dcmaar-connectors';

// Temporary inline schema until build issue is resolved
const ConnectionOptionsSchema = z.object({
  id: z.string().min(1, 'ID is required'),
  type: z.string().min(1, 'Type is required'),
  maxRetries: z.number().int().nonnegative().optional().default(3),
  timeout: z.number().int().positive().optional().default(30000),
  secure: z.boolean().optional().default(true),
  headers: z.record(z.string(), z.string()).optional(),
  auth: z.any().optional(),
  debug: z.boolean().optional().default(false),
});

/**
 * Source Configuration
 *
 * Defines where the extension receives configuration and messages from.
 * A source can be a desktop app, agent, or other control plane.
 */
export const SourceConfigSchema = z.object({
  /** Unique source identifier */
  sourceId: z.string().min(1),
  /** Source type (ipc, http, websocket, etc.) */
  sourceType: z.enum(['ipc', 'http', 'websocket', 'custom'] as const),
  /** Connection options (reuses connector library schema) */
  connectionOptions: ConnectionOptionsSchema,
  /** Whether to auto-reconnect on disconnect */
  autoReconnect: z.boolean().default(true),
  /** Reconnection delay in milliseconds */
  reconnectDelayMs: z.number().int().positive().default(5000),
  /** Maximum reconnection attempts */
  maxReconnectAttempts: z.number().int().positive().default(10),
});

export type SourceConfig = z.infer<typeof SourceConfigSchema>;

/**
 * Sink Configuration
 *
 * Defines where the extension sends events/data to.
 * Multiple sinks can be configured for different data types.
 */
export const SinkConfigSchema = z.object({
  /** Unique sink identifier */
  sinkId: z.string().min(1),
  /** Sink type (http, websocket, file, etc.) */
  sinkType: z.enum(['http', 'websocket', 'file', 'memory', 'ipc', 'custom'] as const),
  /** Connection options (reuses connector library schema) */
  connectionOptions: ConnectionOptionsSchema,
  /** Whether this sink is enabled */
  enabled: z.boolean().default(true),
  /** Batch configuration for this sink */
  batch: z
    .object({
      size: z.number().int().positive().default(20),
      flushIntervalMs: z.number().int().positive().default(5000),
    })
    .optional(),
  /** Retry policy for failed sends */
  retryPolicy: z
    .object({
      maxRetries: z.number().int().nonnegative().default(3),
      backoffMs: z.number().int().positive().default(1000),
      maxBackoffMs: z.number().int().positive().default(30000),
    })
    .optional(),
});

export type SinkConfig = z.infer<typeof SinkConfigSchema>;

/**
 * Bootstrap Configuration
 *
 * Minimal configuration needed to start the extension and connect to sources.
 * This is loaded first and determines where to fetch runtime configuration.
 */
export const BootstrapConfigSchema = z.object({
  /** Bootstrap version */
  version: z.string().default('1.0.0'),
  /** Primary source configuration */
  source: SourceConfigSchema,
  /** Optional fallback sources */
  fallbackSources: z.array(SourceConfigSchema).optional(),
  /** Whether to wait for source connection before starting services */
  waitForSourceConnection: z.boolean().default(true),
  /** Timeout for source connection in milliseconds */
  sourceConnectionTimeoutMs: z.number().int().positive().default(30000),
});

export type BootstrapConfig = z.infer<typeof BootstrapConfigSchema>;

/**
 * Runtime Configuration
 *
 * Full configuration loaded from the source after bootstrap.
 * Includes all sinks, processes, and extension-specific settings.
 */
export const RuntimeConfigSchema = z.object({
  /** Runtime config version */
  version: z.string().default('1.0.0'),
  /** Array of configured sinks */
  sinks: z.array(SinkConfigSchema),
  /** Extension-specific settings */
  extension: z
    .object({
      /** Enable/disable data capture */
      captureEnabled: z.boolean().default(true),
      /** Data capture types to enable */
      captureTypes: z
        .array(z.enum(['network', 'performance', 'user-interaction', 'custom'] as const))
        .default(['network', 'performance']),
      /** Sampling rate (0.0 to 1.0) */
      samplingRate: z.number().min(0).max(1).default(1.0),
      /** Privacy/redaction rules */
      redactionRules: z
        .array(
          z.object({
            pattern: z.string(),
            replacement: z.string().default('[REDACTED]'),
          })
        )
        .optional(),
    })
    .optional(),
  /** Monitoring and observability settings */
  monitoring: z
    .object({
      enabled: z.boolean().default(true),
      logLevel: z.enum(['debug', 'info', 'warn', 'error'] as const).default('info'),
      metricsEnabled: z.boolean().default(true),
      tracingEnabled: z.boolean().default(false),
    })
    .optional(),
  /** Security settings */
  security: z
    .object({
      tlsEnabled: z.boolean().default(true),
      tlsVerify: z.boolean().default(true),
      allowedDomains: z.array(z.string()).optional(),
    })
    .optional(),
});

export type RuntimeConfig = z.infer<typeof RuntimeConfigSchema>;

/**
 * Full Extension Configuration
 *
 * Combines bootstrap and runtime configuration.
 */
export const ExtensionConfigSchema = z.object({
  bootstrap: BootstrapConfigSchema,
  runtime: RuntimeConfigSchema.optional(),
});

export type ExtensionConfig = z.infer<typeof ExtensionConfigSchema>;

/**
 * Configuration validation result
 */
export interface ConfigValidationResult {
  valid: boolean;
  error?: string;
  data?: ExtensionConfig;
}

/**
 * Validates bootstrap configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result
 */
export function validateBootstrapConfig(config: unknown): ConfigValidationResult {
  try {
    const data = BootstrapConfigSchema.parse(config);
    return { valid: true, data: { bootstrap: data } };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Validates runtime configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result
 */
export function validateRuntimeConfig(config: unknown): ConfigValidationResult {
  try {
    const data = RuntimeConfigSchema.parse(config);
    return { valid: true, data: { bootstrap: {} as BootstrapConfig, runtime: data } };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Validates full extension configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result
 */
export function validateExtensionConfig(config: unknown): ConfigValidationResult {
  try {
    const data = ExtensionConfigSchema.parse(config);
    return { valid: true, data };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}
