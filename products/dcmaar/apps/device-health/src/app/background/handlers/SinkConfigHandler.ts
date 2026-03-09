/**
 * @fileoverview Sink Configuration Handler
 *
 * Processes SinkConfigMessage to configure data sinks (destinations where
 * events are sent). Manages sink registry, validates configurations, and
 * initializes sink connectors.
 *
 * Responsibilities:
 * - Validate sink configuration against schema
 * - Create/update sinks in registry
 * - Initialize sink connectors
 * - Handle errors gracefully
 *
 * @example
 * ```typescript
 * const handler = new SinkConfigHandler();
 * const result = await handler.handle(sinkConfigMessage);
 * if (result.success) {
 *   console.log('Sink configured:', result.data);
 * } else {
 *   console.error('Failed to configure sink:', result.error);
 * }
 * ```
 */

import { devLog } from '../../../shared/utils/dev-logger';

import type { ISinkConfigHandler, HandlerResult } from '../contracts/handlers';
import type { SinkConfigMessage } from '../contracts/messages';

/**
 * Sink registry entry
 */
interface SinkEntry {
  sinkId: string;
  sinkType: string;
  endpoint: string;
  config?: Record<string, unknown>;
  enabled: boolean;
  metadata?: Record<string, string>;
  createdAt: number;
  updatedAt: number;
  status: 'active' | 'inactive' | 'error';
  errorMessage?: string;
}

/**
 * Sink Configuration Handler
 *
 * Handles sink configuration messages and manages the sink registry.
 * Each sink represents a destination where events can be sent.
 *
 * Supported sink types:
 * - http: HTTP/REST endpoint
 * - websocket: WebSocket endpoint
 * - file: Local file storage
 * - memory: In-memory storage (testing)
 * - ipc: Inter-process communication
 * - custom: Custom sink implementation
 */
export class SinkConfigHandler implements ISinkConfigHandler {
  private readonly contextName = 'SinkConfigHandler';
  private sinkRegistry: Map<string, SinkEntry> = new Map();

  /**
   * Handle sink configuration message
   *
   * Validates the sink configuration and updates the sink registry.
   * If the sink already exists, it will be updated. Otherwise, a new
   * sink will be created.
   *
   * @param message - Sink configuration message
   * @returns Handler result with success status and data
   */
  async handle(message: SinkConfigMessage): Promise<HandlerResult> {
    try {
      const { sinkId, sinkType, endpoint, config, enabled, metadata } = message.payload;

      devLog.debug(`[${this.contextName}] Processing sink config`, {
        sinkId,
        sinkType,
        endpoint,
        enabled,
        messageId: message.id,
      });

      // Validate sink configuration
      const validation = this.validateSinkConfig(message.payload);
      if (!validation.valid) {
        devLog.warn(`[${this.contextName}] Sink config validation failed`, {
          sinkId,
          error: validation.error,
        });
        return {
          success: false,
          error: `Sink config validation failed: ${validation.error}`,
        };
      }

      // Check if sink already exists
      const existingSink = this.sinkRegistry.get(sinkId);
      const isUpdate = !!existingSink;

      // Create or update sink entry
      const sinkEntry: SinkEntry = {
        sinkId,
        sinkType,
        endpoint,
        config,
        enabled,
        metadata,
        createdAt: existingSink?.createdAt || Date.now(),
        updatedAt: Date.now(),
        status: 'active',
      };

      // Store in registry
      this.sinkRegistry.set(sinkId, sinkEntry);

      devLog.info(`[${this.contextName}] Sink ${isUpdate ? 'updated' : 'created'}`, {
        sinkId,
        sinkType,
        endpoint,
        enabled,
      });

      return {
        success: true,
        data: {
          sinkId,
          sinkType,
          endpoint,
          enabled,
          isUpdate,
          status: 'active',
        },
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      devLog.error(`[${this.contextName}] Handler error`, {
        error: errorMessage,
        messageId: (message as any).id,
      });
      return {
        success: false,
        error: errorMessage,
      };
    }
  }

  /**
   * Validate sink configuration
   *
   * Checks that the sink configuration is valid and complete.
   *
   * @param config - Sink configuration to validate
   * @returns Validation result
   */
  private validateSinkConfig(config: {
    sinkId: string;
    sinkType: string;
    endpoint: string;
    config?: Record<string, unknown>;
    enabled: boolean;
    metadata?: Record<string, string>;
  }): { valid: boolean; error?: string } {
    // Validate sinkId
    if (!config.sinkId || config.sinkId.trim().length === 0) {
      return { valid: false, error: 'sinkId is required and cannot be empty' };
    }

    // Validate sinkType
    const validSinkTypes = ['http', 'websocket', 'file', 'memory', 'ipc', 'custom'];
    if (!validSinkTypes.includes(config.sinkType)) {
      return {
        valid: false,
        error: `sinkType must be one of: ${validSinkTypes.join(', ')}`,
      };
    }

    // Validate endpoint
    if (!config.endpoint || config.endpoint.trim().length === 0) {
      return { valid: false, error: 'endpoint is required and cannot be empty' };
    }

    // Validate endpoint format based on sink type
    const endpointValidation = this.validateEndpoint(config.sinkType, config.endpoint);
    if (!endpointValidation.valid) {
      return endpointValidation;
    }

    // Validate enabled flag
    if (typeof config.enabled !== 'boolean') {
      return { valid: false, error: 'enabled must be a boolean' };
    }

    return { valid: true };
  }

  /**
   * Validate endpoint format based on sink type
   *
   * @param sinkType - Type of sink
   * @param endpoint - Endpoint to validate
   * @returns Validation result
   */
  private validateEndpoint(sinkType: string, endpoint: string): { valid: boolean; error?: string } {
    switch (sinkType) {
      case 'http':
      case 'websocket':
        // Validate URL format
        try {
          new URL(endpoint);
          return { valid: true };
        } catch {
          return { valid: false, error: `Invalid URL for ${sinkType} sink: ${endpoint}` };
        }

      case 'file':
        // Validate file path (basic check)
        if (!endpoint.includes('/') && !endpoint.includes('\\')) {
          return { valid: false, error: 'File path must be valid' };
        }
        return { valid: true };

      case 'memory':
      case 'ipc':
      case 'custom':
        // Accept any endpoint for these types
        return { valid: true };

      default:
        return { valid: false, error: `Unknown sink type: ${sinkType}` };
    }
  }

  /**
   * Get a sink from the registry
   *
   * @param sinkId - ID of sink to retrieve
   * @returns Sink entry or undefined if not found
   */
  getSink(sinkId: string): SinkEntry | undefined {
    return this.sinkRegistry.get(sinkId);
  }

  /**
   * Get all sinks from the registry
   *
   * @returns Array of all sink entries
   */
  getAllSinks(): SinkEntry[] {
    return Array.from(this.sinkRegistry.values());
  }

  /**
   * Get enabled sinks
   *
   * @returns Array of enabled sink entries
   */
  getEnabledSinks(): SinkEntry[] {
    return Array.from(this.sinkRegistry.values()).filter((sink) => sink.enabled);
  }

  /**
   * Remove a sink from the registry
   *
   * @param sinkId - ID of sink to remove
   * @returns True if sink was removed, false if not found
   */
  removeSink(sinkId: string): boolean {
    const existed = this.sinkRegistry.has(sinkId);
    if (existed) {
      this.sinkRegistry.delete(sinkId);
      devLog.info(`[${this.contextName}] Sink removed`, { sinkId });
    }
    return existed;
  }

  /**
   * Clear all sinks from the registry
   */
  clearAllSinks(): void {
    const count = this.sinkRegistry.size;
    this.sinkRegistry.clear();
    devLog.info(`[${this.contextName}] All sinks cleared`, { count });
  }

  /**
   * Get sink registry status
   *
   * @returns Status object with sink counts
   */
  getStatus(): {
    totalSinks: number;
    enabledSinks: number;
    disabledSinks: number;
    sinksByType: Record<string, number>;
  } {
    const sinks = Array.from(this.sinkRegistry.values());
    const sinksByType: Record<string, number> = {};

    sinks.forEach((sink) => {
      sinksByType[sink.sinkType] = (sinksByType[sink.sinkType] || 0) + 1;
    });

    return {
      totalSinks: sinks.length,
      enabledSinks: sinks.filter((s) => s.enabled).length,
      disabledSinks: sinks.filter((s) => !s.enabled).length,
      sinksByType,
    };
  }
}
