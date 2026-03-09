/**
 * @fileoverview Processor registry for managing event transformation processors.
 *
 * Provides centralized registration, lookup, and lifecycle management for processors.
 * Supports both built-in processors and custom user-defined processors.
 *
 * @module processors/ProcessorRegistry
 * @since 1.1.0
 */

import { EventEmitter } from 'events';
import { Processor, ProcessorConfig, ProcessorFactory, ProcessorContext } from './types';
import {
  ValidateProcessor,
  TransformProcessor,
  FilterProcessor,
  EnrichProcessor,
  RedactionProcessor,
} from './built-in';

/**
 * Processor registry for managing event transformation processors.
 *
 * The registry maintains a catalog of processor factories and provides
 * methods to create, register, and manage processor instances.
 *
 * **Features**:
 * - Built-in processor types (validate, transform, filter, etc.)
 * - Custom processor registration
 * - Factory-based instantiation
 * - Lifecycle management (init, destroy)
 * - Configuration validation
 * - Type-safe processor lookup
 *
 * **Usage**:
 * ```typescript
 * const registry = new ProcessorRegistry();
 *
 * // Register custom processor
 * registry.registerFactory('custom', (config) => new CustomProcessor(config));
 *
 * // Create processor from config
 * const processor = await registry.createProcessor({
 *   id: 'validate-1',
 *   type: 'validate',
 *   config: { schema: {...} }
 * });
 *
 * // Use in processor chain
 * const result = await processor.process(event, context);
 * ```
 *
 * @class ProcessorRegistry
 * @extends EventEmitter
 */
export class ProcessorRegistry extends EventEmitter {
  /**
   * Map of processor type to factory function.
   */
  private factories: Map<string, ProcessorFactory> = new Map();

  /**
   * Map of processor ID to processor instance.
   */
  private processors: Map<string, Processor> = new Map();

  /**
   * Map of processor ID to initialization state.
   */
  private initialized: Map<string, boolean> = new Map();

  /**
   * Creates a new ProcessorRegistry and registers built-in processors.
   */
  constructor() {
    super();
    this.registerBuiltInProcessors();
  }

  /**
   * Registers built-in processor factories.
   *
   * Built-in processors:
   * - validate: JSON schema validation
   * - transform: JSONPath transformations and field mapping
   * - filter: Conditional filtering with complex rules
   * - enrich: Add metadata, timestamps, UUIDs, hashes
   * - redact: PII redaction with pattern matching
   *
   * @private
   */
  private registerBuiltInProcessors(): void {
    // Register validate processor
    this.factories.set('validate', (config) => {
      return new ValidateProcessor(config as any);
    });

    // Register transform processor
    this.factories.set('transform', (config) => {
      return new TransformProcessor(config as any);
    });

    // Register filter processor
    this.factories.set('filter', (config) => {
      return new FilterProcessor(config as any);
    });

    // Register enrich processor
    this.factories.set('enrich', (config) => {
      return new EnrichProcessor(config as any);
    });

    // Register redact processor
    this.factories.set('redact', (config) => {
      return new RedactionProcessor(config as any);
    });
  }

  /**
   * Registers a processor factory for a given type.
   *
   * Factories are used to create processor instances from configuration.
   * A factory can be a simple constructor or complex initialization logic.
   *
   * @param type - Processor type identifier
   * @param factory - Factory function to create processor instances
   * @throws Error if type is already registered
   *
   * @example
   * ```typescript
   * registry.registerFactory('custom', (config) => {
   *   return new CustomProcessor(config.config);
   * });
   * ```
   */
  registerFactory(type: string, factory: ProcessorFactory): void {
    if (this.factories.has(type)) {
      throw new Error(`Processor type '${type}' is already registered`);
    }

    this.factories.set(type, factory);
    this.emit('processor-registered', { type });
  }

  /**
   * Unregisters a processor factory.
   *
   * @param type - Processor type to unregister
   * @returns true if processor was unregistered, false if not found
   */
  unregisterFactory(type: string): boolean {
    const removed = this.factories.delete(type);
    if (removed) {
      this.emit('processor-unregistered', { type });
    }
    return removed;
  }

  /**
   * Creates a processor instance from configuration.
   *
   * **Process**:
   * 1. Validate configuration
   * 2. Lookup factory by type
   * 3. Create processor instance
   * 4. Validate processor config (if supported)
   * 5. Store in registry
   * 6. Return processor
   *
   * @param config - Processor configuration
   * @returns Processor instance
   * @throws Error if type not registered or creation fails
   *
   * @example
   * ```typescript
   * const processor = await registry.createProcessor({
   *   id: 'validate-schema',
   *   type: 'validate',
   *   config: {
   *     schema: { type: 'object', properties: {...} }
   *   }
   * });
   * ```
   */
  async createProcessor(config: ProcessorConfig): Promise<Processor> {
    // Validate configuration
    if (!config.id) {
      throw new Error('Processor id is required');
    }
    if (!config.type) {
      throw new Error('Processor type is required');
    }

    // Check if processor already exists
    if (this.processors.has(config.id)) {
      throw new Error(`Processor with id '${config.id}' already exists`);
    }

    // Lookup factory
    const factory = this.factories.get(config.type);
    if (!factory) {
      throw new Error(`No factory registered for processor type '${config.type}'`);
    }

    try {
      // Create processor instance
      const processor = await factory(config);

      // Validate processor config if supported
      if (processor.validateConfig) {
        const validation = processor.validateConfig(config.config);
        if (!validation.valid) {
          throw new Error(`Invalid processor config: ${validation.error}`);
        }
      }

      // Store processor
      this.processors.set(config.id, processor);
      this.initialized.set(config.id, false);

      return processor;
    } catch (error) {
      this.emit('processor-error', { config, error });
      throw error;
    }
  }

  /**
   * Gets a processor by ID.
   *
   * @param id - Processor identifier
   * @returns Processor instance or undefined if not found
   */
  getProcessor(id: string): Processor | undefined {
    return this.processors.get(id);
  }

  /**
   * Checks if a processor type is registered.
   *
   * @param type - Processor type
   * @returns true if type is registered
   */
  hasType(type: string): boolean {
    return this.factories.has(type);
  }

  /**
   * Checks if a processor instance exists.
   *
   * @param id - Processor identifier
   * @returns true if processor exists
   */
  hasProcessor(id: string): boolean {
    return this.processors.has(id);
  }

  /**
   * Lists all registered processor types.
   *
   * @returns Array of processor type names
   */
  listTypes(): string[] {
    return Array.from(this.factories.keys());
  }

  /**
   * Lists all processor instances.
   *
   * @returns Array of processor instances
   */
  listProcessors(): Processor[] {
    return Array.from(this.processors.values());
  }

  /**
   * Initializes a processor.
   *
   * Calls the processor's initialize() hook if defined.
   * Marks processor as initialized to prevent duplicate initialization.
   *
   * @param id - Processor identifier
   * @param context - Processor context
   * @throws Error if processor not found or initialization fails
   */
  async initializeProcessor(id: string, context: ProcessorContext): Promise<void> {
    const processor = this.processors.get(id);
    if (!processor) {
      throw new Error(`Processor '${id}' not found`);
    }

    // Skip if already initialized
    if (this.initialized.get(id)) {
      return;
    }

    try {
      if (processor.initialize) {
        await processor.initialize(context);
      }
      this.initialized.set(id, true);
    } catch (error) {
      this.emit('processor-error', { processorId: id, error });
      throw error;
    }
  }

  /**
   * Removes a processor from the registry.
   *
   * Calls the processor's destroy() hook if defined.
   *
   * @param id - Processor identifier
   * @returns true if processor was removed
   */
  async removeProcessor(id: string): Promise<boolean> {
    const processor = this.processors.get(id);
    if (!processor) {
      return false;
    }

    try {
      if (processor.destroy) {
        await processor.destroy();
      }
    } catch (error) {
      this.emit('processor-error', { processorId: id, error });
    }

    this.processors.delete(id);
    this.initialized.delete(id);
    this.emit('processor-unregistered', { id });

    return true;
  }

  /**
   * Removes all processors and cleans up.
   *
   * Calls destroy() on all processors with cleanup hooks.
   */
  async clear(): Promise<void> {
    const processorIds = Array.from(this.processors.keys());

    await Promise.all(processorIds.map((id) => this.removeProcessor(id)));
  }

  /**
   * Creates a processor chain from configuration array.
   *
   * Creates multiple processors and returns them in execution order.
   * Processors are sorted by order field (lower numbers first).
   *
   * @param configs - Array of processor configurations
   * @returns Array of processor instances in execution order
   *
   * @example
   * ```typescript
   * const chain = await registry.createProcessorChain([
   *   { id: 'validate', type: 'validate', order: 1, config: {...} },
   *   { id: 'transform', type: 'transform', order: 2, config: {...} },
   *   { id: 'enrich', type: 'enrich', order: 3, config: {...} }
   * ]);
   * ```
   */
  async createProcessorChain(configs: ProcessorConfig[]): Promise<Processor[]> {
    // Sort by order (ascending)
    const sortedConfigs = [...configs].sort((a, b) => {
      const orderA = a.order ?? 100;
      const orderB = b.order ?? 100;
      return orderA - orderB;
    });

    // Create all processors
    const processors = await Promise.all(
      sortedConfigs.filter((c) => c.enabled !== false).map((config) => this.createProcessor(config))
    );

    return processors;
  }

  /**
   * Gets statistics about the registry.
   *
   * @returns Registry statistics
   */
  getStats() {
    return {
      registeredTypes: this.factories.size,
      processorInstances: this.processors.size,
      initializedProcessors: Array.from(this.initialized.values()).filter(Boolean).length,
      types: this.listTypes(),
    };
  }
}
