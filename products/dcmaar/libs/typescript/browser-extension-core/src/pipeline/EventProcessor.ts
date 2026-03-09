/**
 * @fileoverview Event Processor Interface
 *
 * Defines the interface for event processors in the pipeline architecture.
 * Processors transform, enrich, filter, or validate events.
 *
 * @module pipeline/EventProcessor
 */

/**
 * Event Processor Interface
 *
 * Processors transform events as they flow through the pipeline.
 * They can enrich, filter, validate, or transform events.
 *
 * @example
 * ```typescript
 * class WebsiteCategorizerProcessor implements EventProcessor<PageViewEvent, CategorizedEvent> {
 *   name = 'categorizer';
 *
 *   canProcess(event: PageViewEvent): boolean {
 *     return !!event.url;
 *   }
 *
 *   async process(event: PageViewEvent): Promise<CategorizedEvent | null> {
 *     const category = this.categorize(event.url);
 *     return { ...event, category };
 *   }
 * }
 * ```
 */
export interface EventProcessor<TIn = unknown, TOut = unknown> {
  /**
   * Unique identifier for this processor
   */
  readonly name: string;

  /**
   * Process an event and return transformed event or null to filter out
   * @param event Input event
   * @returns Transformed event or null if event should be filtered
   */
  process(event: TIn): Promise<TOut | null>;

  /**
   * Check if this processor can handle the event
   * @param event Event to check
   * @returns true if processor can handle this event
   */
  canProcess(event: TIn): boolean;

  /**
   * Optional: Initialize processor (e.g., load config, connect to DB)
   */
  initialize?(): Promise<void>;

  /**
   * Optional: Cleanup resources
   */
  shutdown?(): Promise<void>;

  /**
   * Optional: Get processor statistics
   */
  getStats?(): ProcessorStats;
}

/**
 * Processor Statistics
 */
export interface ProcessorStats {
  processed: number;
  filtered: number;
  errors: number;
  avgProcessingTime?: number;
}

/**
 * Base Event Processor with common functionality
 */
export abstract class BaseEventProcessor<TIn = unknown, TOut = unknown>
  implements EventProcessor<TIn, TOut>
{
  abstract readonly name: string;

  protected stats: ProcessorStats = {
    processed: 0,
    filtered: 0,
    errors: 0,
  };

  abstract process(event: TIn): Promise<TOut | null>;
  abstract canProcess(event: TIn): boolean;

  async initialize(): Promise<void> {
    // Default: no initialization needed
  }

  async shutdown(): Promise<void> {
    // Default: no cleanup needed
  }

  getStats(): ProcessorStats {
    return { ...this.stats };
  }

  /**
   * Helper to track processing
   */
  protected async trackProcess<R>(fn: () => Promise<R>): Promise<R> {
    const startTime = performance.now();
    try {
      const result = await fn();
      this.stats.processed++;

      const duration = performance.now() - startTime;
      if (this.stats.avgProcessingTime === undefined) {
        this.stats.avgProcessingTime = duration;
      } else {
        this.stats.avgProcessingTime =
          (this.stats.avgProcessingTime * (this.stats.processed - 1) +
            duration) /
          this.stats.processed;
      }

      return result;
    } catch (error) {
      this.stats.errors++;
      throw error;
    }
  }

  /**
   * Helper to track filtering
   */
  protected trackFilter(): void {
    this.stats.filtered++;
  }
}
