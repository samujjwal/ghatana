/**
 * @fileoverview Event transformation processor using JSONPath and templates.
 *
 * Transforms event payloads using JSONPath expressions and template strings.
 * Supports field mapping, renaming, extraction, and computed fields.
 *
 * @module processors/built-in/TransformProcessor
 * @since 1.1.0
 */

import { Processor, ProcessorContext } from '../types';
import { Event } from '../../types';

/**
 * Transformation operation type.
 */
export type TransformOperation =
  | { type: 'map'; source: string; target: string; default?: unknown }
  | { type: 'rename'; from: string; to: string }
  | { type: 'set'; path: string; value: unknown }
  | { type: 'delete'; path: string }
  | { type: 'copy'; from: string; to: string }
  | { type: 'template'; target: string; template: string };

/**
 * Configuration for TransformProcessor.
 */
export interface TransformProcessorConfig {
  /**
   * Array of transformation operations to apply.
   * Operations are applied in order.
   */
  operations: TransformOperation[];

  /**
   * Whether to fail on transformation errors.
   * If false, errors are logged and transformation continues.
   * @default true
   */
  strict?: boolean;

  /**
   * Whether to preserve original payload in metadata.
   * @default false
   */
  preserveOriginal?: boolean;
}

/**
 * Event transformation processor.
 *
 * Applies a series of transformation operations to event payloads.
 * Supports field mapping, renaming, computed fields, and templates.
 *
 * **Features**:
 * - Field mapping with JSONPath
 * - Field renaming
 * - Set static/computed values
 * - Delete fields
 * - Copy fields
 * - Template strings with variable substitution
 * - Nested object support
 * - Error handling (strict/lenient)
 * - Original payload preservation
 *
 * **Usage**:
 * ```typescript
 * const processor = new TransformProcessor({
 *   id: 'transform-user',
 *   type: 'transform',
 *   config: {
 *     operations: [
 *       { type: 'map', source: 'user.fullName', target: 'name' },
 *       { type: 'rename', from: 'user.emailAddress', to: 'email' },
 *       { type: 'set', path: 'timestamp', value: Date.now() },
 *       { type: 'template', target: 'greeting', template: 'Hello, {name}!' }
 *     ]
 *   }
 * });
 * ```
 */
export class TransformProcessor implements Processor {
  readonly id: string;
  readonly type = 'transform';
  readonly name?: string;

  private config: TransformProcessorConfig;

  constructor(processorConfig: {
    id: string;
    type: string;
    name?: string;
    config: TransformProcessorConfig;
  }) {
    this.id = processorConfig.id;
    this.name = processorConfig.name;
    this.config = {
      strict: true,
      preserveOriginal: false,
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

    // Validate each operation
    for (const op of config.operations) {
      if (!op.type) {
        return { valid: false, error: 'operation type is required' };
      }

      const validTypes = ['map', 'rename', 'set', 'delete', 'copy', 'template'];
      if (!validTypes.includes(op.type)) {
        return { valid: false, error: `invalid operation type: ${op.type}` };
      }
    }

    return { valid: true };
  }

  async process(event: Event, context: ProcessorContext): Promise<Event> {
    const startTime = Date.now();

    try {
      // Clone payload to avoid mutations
      let transformedPayload = JSON.parse(JSON.stringify(event.payload));
      const errors: string[] = [];

      // Preserve original if requested
      const metadata = this.config.preserveOriginal
        ? { ...event.metadata, originalPayload: event.payload }
        : event.metadata;

      // Apply each operation
      for (const operation of this.config.operations) {
        try {
          transformedPayload = this.applyOperation(transformedPayload, operation, event);
        } catch (error) {
          const errorMsg = `Operation ${operation.type} failed: ${error}`;
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

      context.metrics?.increment('processor.transform.success', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      if (errors.length > 0) {
        context.metrics?.increment('processor.transform.partial_errors', 1, {
          processorId: this.id,
          errorCount: errors.length.toString(),
        });
      }

      return {
        ...event,
        payload: transformedPayload,
        metadata: errors.length > 0 ? { ...metadata, transformErrors: errors } : metadata,
      };
    } catch (error) {
      context.metrics?.increment('processor.transform.failure', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
      throw error;
    } finally {
      const duration = Date.now() - startTime;
      context.metrics?.histogram('processor.transform.duration', duration, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
    }
  }

  /**
   * Applies a single transformation operation.
   */
  private applyOperation(payload: unknown, operation: TransformOperation, event: Event): unknown {
    switch (operation.type) {
      case 'map': {
        const value = this.getValueByPath(payload, operation.source) ?? operation.default;
        if (value !== undefined) {
          this.setValueByPath(payload, operation.target, value);
        }
        return payload;
      }

      case 'rename': {
        const value = this.getValueByPath(payload, operation.from);
        if (value !== undefined) {
          this.setValueByPath(payload, operation.to, value);
          this.deleteValueByPath(payload, operation.from);
        }
        return payload;
      }

      case 'set': {
        this.setValueByPath(payload, operation.path, operation.value);
        return payload;
      }

      case 'delete': {
        this.deleteValueByPath(payload, operation.path);
        return payload;
      }

      case 'copy': {
        const value = this.getValueByPath(payload, operation.from);
        if (value !== undefined) {
          this.setValueByPath(payload, operation.to, value);
        }
        return payload;
      }

      case 'template': {
        const rendered = this.renderTemplate(operation.template, payload, event);
        this.setValueByPath(payload, operation.target, rendered);
        return payload;
      }

      default:
        throw new Error(`Unknown operation type: ${(operation as any).type}`);
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
   * Creates nested objects as needed.
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
   * Deletes value from object by path (dot notation).
   */
  private deleteValueByPath(obj: unknown, path: string): void {
    const keys = path.split('.');
    const lastKey = keys.pop()!;
    let current = obj;

    for (const key of keys) {
      if (!(key in current)) return;
      current = current[key];
    }

    delete current[lastKey];
  }

  /**
   * Renders a template string with variable substitution.
   * Supports {path.to.value} syntax.
   */
  private renderTemplate(template: string, payload: unknown, event: Event): string {
    return template.replace(/\{([^}]+)\}/g, (match, path) => {
      // Check if it's an event property
      if (path.startsWith('event.')) {
        const eventPath = path.substring(6);
        const value = this.getValueByPath(event, eventPath);
        return value !== undefined ? String(value) : match;
      }

      // Otherwise, lookup in payload
      const value = this.getValueByPath(payload, path);
      return value !== undefined ? String(value) : match;
    });
  }
}
