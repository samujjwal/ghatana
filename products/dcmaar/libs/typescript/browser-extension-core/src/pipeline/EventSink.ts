/**
 * @fileoverview Event Sink Interface
 *
 * Defines the interface for event sinks in the pipeline architecture.
 * Sinks output processed events to various destinations.
 *
 * @module pipeline/EventSink
 */

/**
 * Event Sink Interface
 *
 * Sinks are responsible for outputting processed events to their
 * final destination (database, API, console, file, etc.).
 *
 * @example
 * ```typescript
 * class IndexedDBSink implements EventSink<UsageEvent> {
 *   name = 'indexeddb';
 *   private db?: IDBDatabase;
 *
 *   async initialize() {
 *     this.db = await openDatabase();
 *   }
 *
 *   async send(event: UsageEvent) {
 *     await this.db.add('events', event);
 *   }
 *
 *   async sendBatch(events: UsageEvent[]) {
 *     const tx = this.db.transaction('events', 'readwrite');
 *     for (const event of events) {
 *       await tx.objectStore('events').add(event);
 *     }
 *   }
 *
 *   async shutdown() {
 *     this.db?.close();
 *   }
 * }
 * ```
 */
export interface EventSink<T = unknown> {
  /**
   * Unique identifier for this sink
   */
  readonly name: string;

  /**
   * Initialize sink (e.g., open database connection, setup API client)
   */
  initialize(): Promise<void>;

  /**
   * Send single event to destination
   * @param event Event to send
   */
  send(event: T): Promise<void>;

  /**
   * Send batch of events (more efficient than individual sends)
   * @param events Events to send
   */
  sendBatch(events: T[]): Promise<void>;

  /**
   * Cleanup and flush any pending events
   */
  shutdown(): Promise<void>;

  /**
   * Optional: Check if sink is ready to receive events
   */
  isReady?(): boolean;

  /**
   * Optional: Get sink statistics
   */
  getStats?(): SinkStats;
}

/**
 * Sink Statistics
 */
export interface SinkStats {
  sent: number;
  batched: number;
  errors: number;
  pending?: number;
  avgSendTime?: number;
}

/**
 * Base Event Sink with common functionality
 */
export abstract class BaseEventSink<T = unknown> implements EventSink<T> {
  abstract readonly name: string;

  protected ready = false;
  protected stats: SinkStats = {
    sent: 0,
    batched: 0,
    errors: 0,
    pending: 0,
  };

  abstract initialize(): Promise<void>;
  abstract send(event: T): Promise<void>;
  abstract sendBatch(events: T[]): Promise<void>;
  abstract shutdown(): Promise<void>;

  isReady(): boolean {
    return this.ready;
  }

  getStats(): SinkStats {
    return { ...this.stats };
  }

  /**
   * Helper to track sending
   */
  protected async trackSend<R>(fn: () => Promise<R>): Promise<R> {
    const startTime = performance.now();
    try {
      this.stats.pending = (this.stats.pending || 0) + 1;
      const result = await fn();
      this.stats.sent++;
      this.stats.pending = (this.stats.pending || 1) - 1;

      const duration = performance.now() - startTime;
      if (this.stats.avgSendTime === undefined) {
        this.stats.avgSendTime = duration;
      } else {
        this.stats.avgSendTime =
          (this.stats.avgSendTime * (this.stats.sent - 1) + duration) /
          this.stats.sent;
      }

      return result;
    } catch (error) {
      this.stats.errors++;
      this.stats.pending = Math.max(0, (this.stats.pending || 1) - 1);
      throw error;
    }
  }

  /**
   * Helper to track batch sending
   */
  protected async trackBatchSend<R>(
    count: number,
    fn: () => Promise<R>
  ): Promise<R> {
    const startTime = performance.now();
    try {
      this.stats.pending = (this.stats.pending || 0) + count;
      const result = await fn();
      this.stats.batched += count;
      this.stats.sent += count;
      this.stats.pending = Math.max(0, (this.stats.pending || count) - count);

      const duration = performance.now() - startTime;
      const avgPerEvent = duration / count;
      if (this.stats.avgSendTime === undefined) {
        this.stats.avgSendTime = avgPerEvent;
      } else {
        const totalEvents = this.stats.sent;
        this.stats.avgSendTime =
          (this.stats.avgSendTime * (totalEvents - count) + duration) /
          totalEvents;
      }

      return result;
    } catch (error) {
      this.stats.errors++;
      this.stats.pending = Math.max(0, (this.stats.pending || count) - count);
      throw error;
    }
  }
}
