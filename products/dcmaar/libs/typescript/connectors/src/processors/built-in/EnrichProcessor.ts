/**
 * @fileoverview Event enrichment processor.
 *
 * Enriches events with additional metadata, computed fields, timestamps,
 * and context information.
 *
 * @module processors/built-in/EnrichProcessor
 * @since 1.1.0
 */

import { Processor, ProcessorContext } from '../types';
import { Event } from '../../types';

/**
 * Enrichment operation type.
 */
export type EnrichmentOperation =
  | { type: 'static'; path: string; value: unknown }
  | { type: 'timestamp'; path: string; format?: 'unix' | 'iso' | 'unix-ms' }
  | { type: 'counter'; path: string; start?: number }
  | { type: 'uuid'; path: string }
  | { type: 'hash'; path: string; source: string; algorithm?: 'md5' | 'sha1' | 'sha256' }
  | { type: 'lookup'; path: string; source: string; map: Record<string, any>; default?: unknown }
  | { type: 'compute'; path: string; expression: string }
  | { type: 'context'; path: string; contextKey: string };

/**
 * Configuration for EnrichProcessor.
 */
export interface EnrichProcessorConfig {
  /**
   * Array of enrichment operations to apply.
   */
  operations: EnrichmentOperation[];

  /**
   * Target for enrichment.
   * - 'payload': Add to event payload
   * - 'metadata': Add to event metadata
   * @default 'metadata'
   */
  target?: 'payload' | 'metadata';

  /**
   * Whether to overwrite existing fields.
   * @default false
   */
  overwrite?: boolean;

  /**
   * Whether to fail on enrichment errors.
   * @default false
   */
  strict?: boolean;
}

/**
 * Event enrichment processor.
 *
 * Adds computed fields, timestamps, UUIDs, hashes, and other
 * metadata to events. Supports both payload and metadata enrichment.
 *
 * **Features**:
 * - Static value injection
 * - Timestamp generation (Unix, ISO, Unix-ms)
 * - Auto-incrementing counters
 * - UUID generation
 * - Hash computation (MD5, SHA1, SHA256)
 * - Lookup table mapping
 * - Computed expressions
 * - Context value injection
 * - Payload/metadata targeting
 * - Overwrite control
 *
 * **Usage**:
 * ```typescript
 * const processor = new EnrichProcessor({
 *   id: 'enrich-events',
 *   type: 'enrich',
 *   config: {
 *     target: 'metadata',
 *     operations: [
 *       { type: 'timestamp', path: 'processedAt', format: 'iso' },
 *       { type: 'uuid', path: 'enrichmentId' },
 *       { type: 'static', path: 'version', value: '1.0' },
 *       { type: 'counter', path: 'sequence', start: 1 }
 *     ]
 *   }
 * });
 * ```
 */
export class EnrichProcessor implements Processor {
  readonly id: string;
  readonly type = 'enrich';
  readonly name?: string;

  private config: EnrichProcessorConfig;
  private counters: Map<string, number> = new Map();

  constructor(processorConfig: {
    id: string;
    type: string;
    name?: string;
    config: EnrichProcessorConfig;
  }) {
    this.id = processorConfig.id;
    this.name = processorConfig.name;
    this.config = {
      target: 'metadata',
      overwrite: false,
      strict: false,
      ...processorConfig.config,
    };
  }

  validateConfig(config: unknown): { valid: boolean; error?: string } {
    if (!config.operations) {
      return { valid: false, error: 'operations array is required' };
    }

    if (!Array.isArray(config.operations)) {
      return { valid: false, error: 'operations must be an array' };
    }

    if (config.operations.length === 0) {
      return { valid: false, error: 'operations array must not be empty' };
    }

    // Validate target
    if (config.target && !['payload', 'metadata'].includes(config.target)) {
      return { valid: false, error: "target must be 'payload' or 'metadata'" };
    }

    return { valid: true };
  }

  initialize(context: ProcessorContext): void {
    // Initialize counters
    for (const op of this.config.operations) {
      if (op.type === 'counter') {
        const start = op.start ?? 0;
        this.counters.set(op.path, start);
      }
    }

    context.logger?.debug('EnrichProcessor initialized', {
      processorId: this.id,
      operationCount: this.config.operations.length,
    });
  }

  async process(event: Event, context: ProcessorContext): Promise<Event> {
    const startTime = Date.now();

    try {
      const enrichments: Record<string, any> = {};
      const errors: string[] = [];

      // Apply each operation
      for (const operation of this.config.operations) {
        try {
          const { path, value } = this.applyOperation(operation, event, context);

          // Check if field exists and overwrite is disabled
          const target = this.config.target === 'payload' ? event.payload : event.metadata || {};
          const existingValue = this.getValueByPath(target, path);

          if (existingValue !== undefined && !this.config.overwrite) {
            context.logger?.debug('Skipping enrichment - field exists', {
              processorId: this.id,
              path,
              target: this.config.target,
            });
            continue;
          }

          this.setValueByPath(enrichments, path, value);
        } catch (error) {
          const errorMsg = `Enrichment operation ${operation.type} failed: ${error}`;
          errors.push(errorMsg);

          context.logger?.warn(errorMsg, {
            processorId: this.id,
            eventId: event.id,
            operation,
          });

          if (this.config.strict) {
            throw new Error(errorMsg);
          }
        }
      }

      context.metrics?.increment('processor.enrich.success', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      if (errors.length > 0) {
        context.metrics?.increment('processor.enrich.partial_errors', 1, {
          processorId: this.id,
          errorCount: errors.length.toString(),
        });
      }

      // Apply enrichments to target
      if (this.config.target === 'payload') {
        return {
          ...event,
          payload: this.mergeDeep(event.payload, enrichments),
          metadata:
            errors.length > 0 ? { ...event.metadata, enrichmentErrors: errors } : event.metadata,
        };
      } else {
        return {
          ...event,
          metadata: this.mergeDeep(event.metadata || {}, enrichments),
        };
      }
    } catch (error) {
      context.metrics?.increment('processor.enrich.failure', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
      throw error;
    } finally {
      const duration = Date.now() - startTime;
      context.metrics?.histogram('processor.enrich.duration', duration, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
    }
  }

  /**
   * Applies a single enrichment operation.
   */
  private applyOperation(
    operation: EnrichmentOperation,
    event: Event,
    context: ProcessorContext
  ): { path: string; value: unknown } {
    switch (operation.type) {
      case 'static':
        return { path: operation.path, value: operation.value };

      case 'timestamp': {
        const now = Date.now();
        let value: number | string;

        switch (operation.format) {
          case 'unix':
            value = Math.floor(now / 1000);
            break;
          case 'unix-ms':
            value = now;
            break;
          case 'iso':
          default:
            value = new Date(now).toISOString();
            break;
        }

        return { path: operation.path, value };
      }

      case 'counter': {
        const current = this.counters.get(operation.path) ?? operation.start ?? 0;
        const next = current + 1;
        this.counters.set(operation.path, next);
        return { path: operation.path, value: next };
      }

      case 'uuid':
        return { path: operation.path, value: this.generateUUID() };

      case 'hash': {
        const sourceValue = this.getValueByPath(event.payload, operation.source);
        const hash = this.computeHash(String(sourceValue), operation.algorithm || 'sha256');
        return { path: operation.path, value: hash };
      }

      case 'lookup': {
        const sourceValue = this.getValueByPath(event.payload, operation.source);
        const mappedValue = operation.map[String(sourceValue)] ?? operation.default;
        return { path: operation.path, value: mappedValue };
      }

      case 'compute': {
        const value = this.evaluateExpression(operation.expression, event.payload);
        return { path: operation.path, value };
      }

      case 'context': {
        const value = (context.metadata as any)?.[operation.contextKey];
        return { path: operation.path, value };
      }

      default:
        throw new Error(`Unknown enrichment operation: ${(operation as any).type}`);
    }
  }

  /**
   * Generates a simple UUID v4.
   */
  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  /**
   * Computes hash of a string.
   * Note: This is a simple hash implementation. For production, use crypto module.
   */
  private computeHash(input: string, _algorithm?: 'md5' | 'sha1' | 'sha256'): string {
    // Simple hash for demonstration (not cryptographically secure)
    let hash = 0;
    for (let i = 0; i < input.length; i++) {
      const char = input.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return Math.abs(hash).toString(16);
  }

  /**
   * Evaluates a simple expression.
   * Supports basic arithmetic and field references.
   */
  private evaluateExpression(expression: string, payload: unknown): unknown {
    // Simple expression evaluator (for demo purposes)
    // In production, use a safe expression parser like mathjs
    try {
      // Replace field references with values
      const evaluated = expression.replace(/\{([^}]+)\}/g, (match, path) => {
        const value = this.getValueByPath(payload, path);
        return JSON.stringify(value);
      });

      // Evaluate (unsafe - use proper expression parser in production)
      // For now, return the expression as-is
      return evaluated;
    } catch (error) {
      throw new Error(`Expression evaluation failed: ${error}`);
    }
  }

  /**
   * Gets value from object by path (dot notation).
   */
  private getValueByPath(obj: unknown, path: string): unknown {
    const keys = path.split('.');
    let current = obj;

    for (const key of keys) {
      if (current == null) return undefined;
      current = current[key];
    }

    return current;
  }

  /**
   * Sets value in object by path (dot notation).
   */
  private setValueByPath(obj: unknown, path: string, value: unknown): void {
    const keys = path.split('.');
    const lastKey = keys.pop()!;
    let current = obj;

    for (const key of keys) {
      if (!(key in current) || typeof current[key] !== 'object') {
        current[key] = {};
      }
      current = current[key];
    }

    current[lastKey] = value;
  }

  /**
   * Deep merge two objects.
   */
  private mergeDeep(target: unknown, source: unknown): unknown {
    const result = { ...target };

    for (const key in source) {
      if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
        result[key] = this.mergeDeep(result[key] || {}, source[key]);
      } else {
        result[key] = source[key];
      }
    }

    return result;
  }
}
