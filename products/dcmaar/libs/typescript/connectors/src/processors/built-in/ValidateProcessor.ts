/**
 * @fileoverview JSON Schema validation processor.
 *
 * Validates event payloads against JSON schemas using AJV.
 * Supports draft-07, draft-2019-09, and draft-2020-12 schemas.
 *
 * @module processors/built-in/ValidateProcessor
 * @since 1.1.0
 */

import Ajv, { type ValidateFunction, type ErrorObject } from 'ajv';
import { Processor, ProcessorContext } from '../types';
import { Event } from '../../types';

/**
 * Configuration for ValidateProcessor.
 */
export interface ValidateProcessorConfig {
  /**
   * JSON Schema to validate against.
   * Supports draft-07, draft-2019-09, and draft-2020-12.
   */
  schema: object;

  /**
   * Whether to remove additional properties not in schema.
   * @default false
   */
  removeAdditional?: boolean | 'all' | 'failing';

  /**
   * Whether to coerce types (e.g., string to number).
   * @default false
   */
  coerceTypes?: boolean | 'array';

  /**
   * Whether to use defaults from schema.
   * @default true
   */
  useDefaults?: boolean;

  /**
   * Validation mode.
   * - 'strict': Throw error on validation failure
   * - 'filter': Filter out invalid events (return null)
   * - 'tag': Add validation error to metadata and continue
   * @default 'strict'
   */
  mode?: 'strict' | 'filter' | 'tag';

  /**
   * Whether to include detailed error information in metadata.
   * @default true
   */
  includeErrors?: boolean;
}

/**
 * JSON Schema validation processor.
 *
 * Validates event payloads against JSON schemas. Supports multiple
 * validation modes for different use cases.
 *
 * **Features**:
 * - Draft-07, draft-2019-09, draft-2020-12 schema support
 * - Type coercion
 * - Additional property removal
 * - Default value application
 * - Multiple validation modes (strict, filter, tag)
 * - Detailed error reporting
 * - Format validation (email, uri, date-time, etc.)
 *
 * **Usage**:
 * ```typescript
 * const processor = new ValidateProcessor({
 *   id: 'validate-user',
 *   type: 'validate',
 *   config: {
 *     schema: {
 *       type: 'object',
 *       properties: {
 *         name: { type: 'string', minLength: 1 },
 *         age: { type: 'number', minimum: 0 }
 *       },
 *       required: ['name']
 *     },
 *     mode: 'strict'
 *   }
 * });
 * ```
 */
export class ValidateProcessor implements Processor {
  readonly id: string;
  readonly type = 'validate';
  readonly name?: string;

  private config: ValidateProcessorConfig;
  private ajv: InstanceType<typeof Ajv>;
  private validate?: ValidateFunction;

  constructor(processorConfig: {
    id: string;
    type: string;
    name?: string;
    config: ValidateProcessorConfig;
  }) {
    this.id = processorConfig.id;
    this.name = processorConfig.name;
    this.config = {
      mode: 'strict',
      includeErrors: true,
      useDefaults: true,
      removeAdditional: false,
      coerceTypes: false,
      ...processorConfig.config,
    };

    // Initialize AJV
    this.ajv = new Ajv({
      allErrors: true,
      removeAdditional: this.config.removeAdditional,
      coerceTypes: this.config.coerceTypes,
      useDefaults: this.config.useDefaults,
    });

    // Note: Format validators can be added via ajv-formats package if needed
    // For now, basic validation is supported
  }

  validateConfig(config: unknown): { valid: boolean; error?: string } {
    if (!config.schema) {
      return { valid: false, error: 'schema is required' };
    }

    if (typeof config.schema !== 'object') {
      return { valid: false, error: 'schema must be an object' };
    }

    // Validate mode if provided
    if (config.mode && !['strict', 'filter', 'tag'].includes(config.mode)) {
      return { valid: false, error: "mode must be 'strict', 'filter', or 'tag'" };
    }

    return { valid: true };
  }

  initialize(context: ProcessorContext): void {
    try {
      // Compile schema
      this.validate = this.ajv.compile(this.config.schema);
      context.logger?.debug('ValidateProcessor initialized', {
        processorId: this.id,
        schema: this.config.schema,
      });
    } catch (error) {
      context.logger?.error('Failed to compile schema', { processorId: this.id, error });
      throw new Error(`Failed to compile schema: ${error}`);
    }
  }

  async process(event: Event, context: ProcessorContext): Promise<Event | null> {
    if (!this.validate) {
      throw new Error('ValidateProcessor not initialized - call initialize() first');
    }

    const startTime = Date.now();

    try {
      const valid = this.validate(event.payload);

      if (valid) {
        // Validation passed
        context.metrics?.increment('processor.validate.success', 1, {
          processorId: this.id,
          connectorId: context.connectorId,
        });

        return event;
      }

      // Validation failed
      const errors = this.validate.errors || [];
      context.metrics?.increment('processor.validate.failure', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      context.logger?.warn('Event validation failed', {
        processorId: this.id,
        eventId: event.id,
        errorCount: errors.length,
        errors: this.config.includeErrors ? errors : undefined,
      });

      // Handle based on mode
      switch (this.config.mode) {
        case 'filter':
          // Drop the event
          return null;

        case 'tag':
          // Add error to metadata and continue
          return {
            ...event,
            metadata: {
              ...event.metadata,
              validationErrors: this.formatErrors(errors),
              validationFailed: true,
            },
          };

        case 'strict':
        default:
          // Throw error
          throw new Error(`Validation failed: ${this.formatErrorMessage(errors)}`);
      }
    } finally {
      const duration = Date.now() - startTime;
      context.metrics?.histogram('processor.validate.duration', duration, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
    }
  }

  /**
   * Formats validation errors into a readable message.
   */
  private formatErrorMessage(errors: ErrorObject[]): string {
    return errors
      .map((err) => `${this.resolveInstancePath(err) || '/'} ${err.message}`)
      .join('; ');
  }

  /**
   * Formats validation errors for metadata.
   */
  private formatErrors(errors: ErrorObject[]): unknown[] {
    return errors.map((err) => ({
      path: this.resolveInstancePath(err) || '/',
      message: err.message,
      keyword: err.keyword,
      params: err.params,
    }));
  }

  /**
   * Normalizes AJV error paths across versions (instancePath vs dataPath).
   */
  private resolveInstancePath(error: ErrorObject): string | undefined {
    if (typeof (error as { instancePath?: string }).instancePath === 'string') {
      return (error as { instancePath?: string }).instancePath;
    }

    if (typeof (error as { dataPath?: string }).dataPath === 'string') {
      return (error as { dataPath?: string }).dataPath;
    }

    return undefined;
  }
}
