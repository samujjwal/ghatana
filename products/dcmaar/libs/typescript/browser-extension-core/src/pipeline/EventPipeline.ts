/**
 * @fileoverview Event Pipeline
 *
 * Orchestrates the flow of events from sources through processors to sinks.
 * Implements the Source → Processor → Sink architecture.
 *
 * @module pipeline/EventPipeline
 */

import type { EventSource } from "./EventSource";
import type { EventProcessor } from "./EventProcessor";
import type { EventSink } from "./EventSink";

/**
 * Pipeline Configuration
 */
export interface PipelineConfig {
  /**
   * Pipeline name for logging and debugging
   */
  name?: string;

  /**
   * Whether to continue processing on errors
   */
  continueOnError?: boolean;

  /**
   * Maximum concurrent events being processed
   */
  maxConcurrency?: number;

  /**
   * Error handler for pipeline errors
   */
  onError?: (error: Error, event: unknown) => void;
}

/**
 * Pipeline Statistics
 */
export interface PipelineStats {
  eventsReceived: number;
  eventsProcessed: number;
  eventsFiltered: number;
  eventsSent: number;
  errors: number;
  avgProcessingTime?: number;
}

/**
 * Event Pipeline
 *
 * Orchestrates event flow: Source → Processor(s) → Sink(s)
 *
 * @example
 * ```typescript
 * const pipeline = new EventPipeline({ name: 'guardian' });
 *
 * // Register components
 * pipeline.registerSource(new TabSwitchSource());
 * pipeline.registerProcessor(new WebsiteCategorizerProcessor());
 * pipeline.registerProcessor(new BlockingRuleProcessor());
 * pipeline.registerSink(new IndexedDBSink());
 * pipeline.registerSink(new BackendAPISink());
 *
 * // Start pipeline
 * await pipeline.start();
 *
 * // Events flow automatically:
 * // TabSwitch → Categorizer → BlockingRule → [IndexedDB, BackendAPI]
 * ```
 */
export class EventPipeline {
  private readonly config: Required<PipelineConfig>;
  private sources: EventSource[] = [];
  private processors: EventProcessor[] = [];
  private sinks: EventSink[] = [];
  private isRunning = false;

  private stats: PipelineStats = {
    eventsReceived: 0,
    eventsProcessed: 0,
    eventsFiltered: 0,
    eventsSent: 0,
    errors: 0,
  };

  constructor(config: PipelineConfig = {}) {
    this.config = {
      name: config.name || "pipeline",
      continueOnError: config.continueOnError ?? true,
      maxConcurrency: config.maxConcurrency || 100,
      onError:
        config.onError ||
        ((error) => console.error(`[${this.config.name}] Error:`, error)),
    };
  }

  /**
   * Register an event source
   */
  registerSource(source: EventSource): void {
    if (this.isRunning) {
      throw new Error("Cannot register source while pipeline is running");
    }
    this.sources.push(source);
  }

  /**
   * Register an event processor
   */
  registerProcessor(processor: EventProcessor): void {
    if (this.isRunning) {
      throw new Error("Cannot register processor while pipeline is running");
    }
    this.processors.push(processor);
  }

  /**
   * Register an event sink
   */
  registerSink(sink: EventSink): void {
    if (this.isRunning) {
      throw new Error("Cannot register sink while pipeline is running");
    }
    this.sinks.push(sink);
  }

  /**
   * Start the pipeline
   */
  async start(): Promise<void> {
    if (this.isRunning) {
      console.warn(`[${this.config.name}] Pipeline already running`);
      return;
    }

    console.log(`[${this.config.name}] Starting pipeline...`);

    try {
      // Initialize all sinks
      await Promise.all(this.sinks.map((sink) => sink.initialize()));
      console.log(
        `[${this.config.name}] Initialized ${this.sinks.length} sinks`
      );

      // Initialize all processors
      await Promise.all(
        this.processors.filter((p) => p.initialize).map((p) => p.initialize!())
      );
      console.log(
        `[${this.config.name}] Initialized ${this.processors.length} processors`
      );

      // Start all sources
      for (const source of this.sources) {
        source.onEvent(async (event) => {
          await this.handleEvent(event);
        });
        await source.start();
      }
      console.log(
        `[${this.config.name}] Started ${this.sources.length} sources`
      );

      this.isRunning = true;
      console.log(`[${this.config.name}] Pipeline started successfully`);
    } catch (error) {
      console.error(`[${this.config.name}] Failed to start pipeline:`, error);
      throw error;
    }
  }

  /**
   * Stop the pipeline
   */
  async stop(): Promise<void> {
    if (!this.isRunning) {
      return;
    }

    console.log(`[${this.config.name}] Stopping pipeline...`);

    try {
      // Stop all sources
      await Promise.all(this.sources.map((source) => source.stop()));
      console.log(
        `[${this.config.name}] Stopped ${this.sources.length} sources`
      );

      // Shutdown processors
      await Promise.all(
        this.processors.filter((p) => p.shutdown).map((p) => p.shutdown!())
      );
      console.log(
        `[${this.config.name}] Shutdown ${this.processors.length} processors`
      );

      // Shutdown sinks
      await Promise.all(this.sinks.map((sink) => sink.shutdown()));
      console.log(`[${this.config.name}] Shutdown ${this.sinks.length} sinks`);

      this.isRunning = false;
      console.log(`[${this.config.name}] Pipeline stopped`);
    } catch (error) {
      console.error(`[${this.config.name}] Error stopping pipeline:`, error);
      throw error;
    }
  }

  /**
   * Handle an event through the pipeline
   */
  private async handleEvent(event: unknown): Promise<void> {
    const startTime = performance.now();
    this.stats.eventsReceived++;

    try {
      let processedEvent: unknown = event;

      // Pass through processor chain
      for (const processor of this.processors) {
        if (!processor.canProcess(processedEvent)) {
          continue;
        }

        try {
          const result = await processor.process(processedEvent);

          if (result === null) {
            // Event filtered out
            this.stats.eventsFiltered++;
            return;
          }

          processedEvent = result;
        } catch (error) {
          this.stats.errors++;
          this.config.onError(
            error instanceof Error ? error : new Error(String(error)),
            processedEvent
          );

          if (!this.config.continueOnError) {
            throw error;
          }
          return;
        }
      }

      this.stats.eventsProcessed++;

      // Send to all sinks
      const sinkPromises = this.sinks.map(async (sink) => {
        try {
          await sink.send(processedEvent);
          this.stats.eventsSent++;
        } catch (error) {
          this.stats.errors++;
          this.config.onError(
            error instanceof Error ? error : new Error(String(error)),
            processedEvent
          );

          if (!this.config.continueOnError) {
            throw error;
          }
        }
      });

      await Promise.all(sinkPromises);

      // Update average processing time
      const duration = performance.now() - startTime;
      if (this.stats.avgProcessingTime === undefined) {
        this.stats.avgProcessingTime = duration;
      } else {
        this.stats.avgProcessingTime =
          (this.stats.avgProcessingTime * (this.stats.eventsProcessed - 1) +
            duration) /
          this.stats.eventsProcessed;
      }
    } catch (error) {
      this.stats.errors++;
      this.config.onError(
        error instanceof Error ? error : new Error(String(error)),
        event
      );

      if (!this.config.continueOnError) {
        throw error;
      }
    }
  }

  /**
   * Get pipeline statistics
   */
  getStats(): PipelineStats {
    return { ...this.stats };
  }

  /**
   * Reset pipeline statistics
   */
  resetStats(): void {
    this.stats = {
      eventsReceived: 0,
      eventsProcessed: 0,
      eventsFiltered: 0,
      eventsSent: 0,
      errors: 0,
    };
  }

  /**
   * Check if pipeline is running
   */
  getIsRunning(): boolean {
    return this.isRunning;
  }

  /**
   * Get pipeline configuration
   */
  getConfig(): Readonly<Required<PipelineConfig>> {
    return { ...this.config };
  }

  /**
   * Get registered components count
   */
  getComponentCounts(): { sources: number; processors: number; sinks: number } {
    return {
      sources: this.sources.length,
      processors: this.processors.length,
      sinks: this.sinks.length,
    };
  }
}
