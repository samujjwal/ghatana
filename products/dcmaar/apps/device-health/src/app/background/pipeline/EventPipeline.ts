/**
 * @fileoverview Event Pipeline - Dynamic Processor Injection
 *
 * Provides flexible event processing pipeline with dynamic processor loading.
 * Processors can be added, removed, and reordered at runtime without restart.
 *
 * **Features**:
 * - Dynamic processor loading from configuration
 * - Hot-reload support without extension restart
 * - Ordered execution with priority support
 * - Array handling (fork/join patterns)
 * - Error handling and recovery
 * - Performance monitoring
 * - Processor lifecycle management
 *
 * **Usage**:
 * ```typescript
 * const pipeline = new EventPipeline({
 *   processors: [
 *     { id: 'validate', type: 'validate', order: 1, config: {...} },
 *     { id: 'transform', type: 'transform', order: 2, config: {...} },
 *     { id: 'enrich', type: 'enrich', order: 3, config: {...} }
 *   ]
 * });
 *
 * await pipeline.initialize();
 *
 * const result = await pipeline.process(event, context);
 * ```
 *
 * @module app/background/pipeline/EventPipeline
 */

import type { Event } from '@ghatana/dcmaar-connectors';

// TODO: Import from @ghatana/dcmaar-connectors when available
// import { ProcessorRegistry } from '@ghatana/dcmaar-connectors';
// import type { Processor, ProcessorConfig, ProcessorContext } from '@ghatana/dcmaar-connectors';

/**
 * Temporary processor types until @ghatana/dcmaar-connectors exports are available
 * FIXME: Replace with actual imports once ProcessorRegistry is exported
 */
export interface ProcessorConfig {
  id: string;
  type: string;
  config: Record<string, any>;
  enabled?: boolean;
  order?: number;
}

export interface ProcessorContext {
  connectorId: string;
  connectorType: string;
  metadata?: Record<string, any>;
  logger: {
    debug: (msg: string, meta?: any) => void;
    info: (msg: string, meta?: any) => void;
    warn: (msg: string, meta?: any) => void;
    error: (msg: string, meta?: any) => void;
  };
  metrics: {
    increment: (metric: string, value?: number, tags?: Record<string, string>) => void;
    gauge: (metric: string, value: number, tags?: Record<string, string>) => void;
    histogram: (metric: string, value: number, tags?: Record<string, string>) => void;
  };
}

export interface Processor {
  id: string;
  type: string;
  order?: number;
  process(event: Event, context?: ProcessorContext): Promise<Event | Event[] | null>;
}

/**
 * Temporary ProcessorRegistry implementation
 * FIXME: Remove once @ghatana/dcmaar-connectors exports ProcessorRegistry
 */
class ProcessorRegistry {
  private processors: Map<string, Processor> = new Map();

  async createProcessor(config: ProcessorConfig): Promise<Processor> {
    const processor: Processor = {
      id: config.id,
      type: config.type,
      order: config.order,
      async process(event: Event): Promise<Event> {
        // Pass-through until real implementation available
        return event;
      },
    };

    this.processors.set(config.id, processor);
    return processor;
  }

  async createProcessorChain(configs: ProcessorConfig[]): Promise<Processor[]> {
    const processors = await Promise.all(
      configs.filter(c => c.enabled !== false).map(c => this.createProcessor(c))
    );
    return processors.sort((a, b) => (a.order ?? 100) - (b.order ?? 100));
  }

  async initializeProcessor(_id: string, _context: ProcessorContext): Promise<void> {
    // No-op for now
  }

  async removeProcessor(id: string): Promise<void> {
    this.processors.delete(id);
  }

  getProcessor(id: string): Processor | undefined {
    return this.processors.get(id);
  }

  async clear(): Promise<void> {
    this.processors.clear();
  }
}

/**
 * Metric event emitted by the pipeline for external observation.
 */
export interface MetricEvent {
  type: 'increment' | 'gauge' | 'histogram';
  metric: string;
  value: number;
  tags?: Record<string, string>;
}

/**
 * Pipeline configuration
 */
export interface PipelineConfig {
  /** Processor configurations */
  processors: ProcessorConfig[];

  /** Enable performance monitoring */
  monitoring?: boolean;

  /** Error handling strategy */
  errorStrategy?: 'reject' | 'skip' | 'continue';

  /** Maximum processing time in milliseconds */
  timeout?: number;

  /** Optional callback for observing pipeline metrics */
  onMetric?: (event: MetricEvent) => void;
}

/**
 * Pipeline statistics
 */
export interface PipelineStats {
  /** Total events processed */
  totalProcessed: number;

  /** Total events filtered */
  totalFiltered: number;

  /** Total errors */
  totalErrors: number;

  /** Average processing time in milliseconds */
  avgProcessingTime: number;

  /** Processor statistics */
  processorStats: Record<string, {
    processed: number;
    filtered: number;
    errors: number;
    avgTime: number;
  }>;
}

/**
 * Event Pipeline
 *
 * Manages event processing through a chain of processors.
 * Supports dynamic processor injection and hot-reload.
 */
export class EventPipeline {
  private registry: ProcessorRegistry;
  private processors: Processor[] = [];
  private config: Required<Omit<PipelineConfig, 'onMetric'>>;
  private onMetric?: (event: MetricEvent) => void;
  private stats: PipelineStats;
  private initialized = false;

  constructor(config: PipelineConfig) {
    this.registry = new ProcessorRegistry();
    this.onMetric = config.onMetric;
    this.config = {
      processors: config.processors || [],
      monitoring: config.monitoring !== false,
      errorStrategy: config.errorStrategy || 'reject',
      timeout: config.timeout || 30000,
    };

    this.stats = {
      totalProcessed: 0,
      totalFiltered: 0,
      totalErrors: 0,
      avgProcessingTime: 0,
      processorStats: {},
    };
  }

  /**
   * Initialize pipeline and load processors
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return;
    }

    try {
      // Create processor chain from config
      this.processors = await this.registry.createProcessorChain(this.config.processors);

      // Initialize each processor
      const context = this.createContext('pipeline-init');
      for (const processor of this.processors) {
        await this.registry.initializeProcessor(processor.id, context);
        
        // Initialize stats for processor
        this.stats.processorStats[processor.id] = {
          processed: 0,
          filtered: 0,
          errors: 0,
          avgTime: 0,
        };
      }

      this.initialized = true;
    } catch (error) {
      throw new Error(`Failed to initialize pipeline: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Process an event through the pipeline
   *
   * @param event - Event to process
   * @param context - Processing context
   * @returns Processed event(s), null if filtered, or throws on error
   */
  async process(event: Event, context?: Partial<ProcessorContext>): Promise<Event | Event[] | null> {
    if (!this.initialized) {
      throw new Error('Pipeline not initialized');
    }

    const startTime = Date.now();
    let current: Event | Event[] | null = event;

    try {
      for (const processor of this.processors) {
        if (current === null) {
          // Event was filtered by previous processor
          this.recordFiltered(processor.id);
          break;
        }

        const processorStartTime = Date.now();

        try {
          if (Array.isArray(current)) {
            // Process each event in array
            const results = await Promise.all(
              current.map(e => this.processWithTimeout(processor, e, context))
            );
            
            // Flatten and filter nulls
            current = results.flat().filter(e => e !== null) as Event[];
            
            if (current.length === 0) {
              current = null;
            }
          } else {
            current = await this.processWithTimeout(processor, current, context);
          }

          // Record stats
          const processorTime = Date.now() - processorStartTime;
          this.recordProcessed(processor.id, processorTime);

        } catch (error) {
          this.recordError(processor.id);

          if (this.config.errorStrategy === 'reject') {
            throw error;
          } else if (this.config.errorStrategy === 'skip') {
            // Skip this processor, continue with original event
            continue;
          } else {
            // Continue with null (filter event)
            current = null;
            break;
          }
        }
      }

      // Record overall stats
      const totalTime = Date.now() - startTime;
      this.recordOverallStats(totalTime, current === null);

      return current;

    } catch (error) {
      this.stats.totalErrors++;
      throw error;
    }
  }

  /**
   * Process event with timeout
   */
  private async processWithTimeout(
    processor: Processor,
    event: Event,
    context?: Partial<ProcessorContext>
  ): Promise<Event | Event[] | null> {
    const processorContext = this.createContext(processor.id, context);

    return Promise.race([
      processor.process(event, processorContext),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error(`Processor ${processor.id} timed out`)), this.config.timeout)
      ),
    ]);
  }

  /**
   * Create processor context
   */
  private createContext(connectorId: string, override?: Partial<ProcessorContext>): ProcessorContext {
    return {
      connectorId,
      connectorType: 'sink',
      metadata: {},
      logger: {
        debug: (msg: string, meta?: any) => console.debug(`[Pipeline][${connectorId}]`, msg, meta),
        info: (msg: string, meta?: any) => console.info(`[Pipeline][${connectorId}]`, msg, meta),
        warn: (msg: string, meta?: any) => console.warn(`[Pipeline][${connectorId}]`, msg, meta),
        error: (msg: string, meta?: any) => console.error(`[Pipeline][${connectorId}]`, msg, meta),
      },
      metrics: {
        increment: (metric: string, value = 1, tags?: Record<string, string>) => {
          this.onMetric?.({ type: 'increment', metric, value, tags });
        },
        gauge: (metric: string, value: number, tags?: Record<string, string>) => {
          this.onMetric?.({ type: 'gauge', metric, value, tags });
        },
        histogram: (metric: string, value: number, tags?: Record<string, string>) => {
          this.onMetric?.({ type: 'histogram', metric, value, tags });
        },
      },
      ...override,
    };
  }

  /**
   * Add processor to pipeline
   */
  async addProcessor(config: ProcessorConfig): Promise<void> {
    const processor = await this.registry.createProcessor(config);
    
    // Initialize processor
    const context = this.createContext(processor.id);
    await this.registry.initializeProcessor(processor.id, context);

    // Add to processors array and sort by order
    this.processors.push(processor);
    this.processors.sort((a, b) => {
      const orderA = (a as any).order ?? 100;
      const orderB = (b as any).order ?? 100;
      return orderA - orderB;
    });

    // Initialize stats
    this.stats.processorStats[processor.id] = {
      processed: 0,
      filtered: 0,
      errors: 0,
      avgTime: 0,
    };
  }

  /**
   * Remove processor from pipeline
   */
  async removeProcessor(id: string): Promise<void> {
    await this.registry.removeProcessor(id);
    this.processors = this.processors.filter(p => p.id !== id);
    delete this.stats.processorStats[id];
  }

  /**
   * Update processor configuration
   */
  async updateProcessor(id: string, config: ProcessorConfig): Promise<void> {
    await this.removeProcessor(id);
    await this.addProcessor(config);
  }

  /**
   * Get processor by ID
   */
  getProcessor(id: string): Processor | undefined {
    return this.registry.getProcessor(id);
  }

  /**
   * List all processors
   */
  listProcessors(): Processor[] {
    return [...this.processors];
  }

  /**
   * Get pipeline statistics
   */
  getStats(): PipelineStats {
    return { ...this.stats };
  }

  /**
   * Reset statistics
   */
  resetStats(): void {
    this.stats = {
      totalProcessed: 0,
      totalFiltered: 0,
      totalErrors: 0,
      avgProcessingTime: 0,
      processorStats: {},
    };

    for (const processor of this.processors) {
      this.stats.processorStats[processor.id] = {
        processed: 0,
        filtered: 0,
        errors: 0,
        avgTime: 0,
      };
    }
  }

  /**
   * Record processed event
   */
  private recordProcessed(processorId: string, time: number): void {
    if (!this.config.monitoring) return;

    const stats = this.stats.processorStats[processorId];
    if (stats) {
      stats.processed++;
      stats.avgTime = (stats.avgTime * (stats.processed - 1) + time) / stats.processed;
    }
  }

  /**
   * Record filtered event
   */
  private recordFiltered(processorId: string): void {
    if (!this.config.monitoring) return;

    const stats = this.stats.processorStats[processorId];
    if (stats) {
      stats.filtered++;
    }
  }

  /**
   * Record error
   */
  private recordError(processorId: string): void {
    if (!this.config.monitoring) return;

    const stats = this.stats.processorStats[processorId];
    if (stats) {
      stats.errors++;
    }
  }

  /**
   * Record overall stats
   */
  private recordOverallStats(time: number, filtered: boolean): void {
    if (!this.config.monitoring) return;

    if (filtered) {
      this.stats.totalFiltered++;
    } else {
      this.stats.totalProcessed++;
    }

    const total = this.stats.totalProcessed + this.stats.totalFiltered;
    this.stats.avgProcessingTime = (this.stats.avgProcessingTime * (total - 1) + time) / total;
  }

  /**
   * Destroy pipeline and cleanup resources
   */
  async destroy(): Promise<void> {
    await this.registry.clear();
    this.processors = [];
    this.initialized = false;
  }
}
