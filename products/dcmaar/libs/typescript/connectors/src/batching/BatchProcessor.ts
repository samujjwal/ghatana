/**
 * @fileoverview Batch processing for efficient bulk operations
 *
 * This module provides intelligent batching of individual items into groups
 * for efficient bulk processing. Automatically manages batch size, timing,
 * and concurrency to optimize throughput while maintaining responsiveness.
 *
 * **Key Benefits:**
 * - Reduces per-item overhead by processing in batches
 * - Configurable batch size and timing
 * - Concurrent batch processing
 * - Order preservation option
 * - Automatic error handling
 *
 * **Use Cases:**
 * - Database bulk inserts/updates
 * - API batch requests
 * - Message queue batching
 * - Log aggregation
 * - Analytics event batching
 *
 * @module batching/BatchProcessor
 * @since 1.0.0
 */

import { EventEmitter } from 'events';

/**
 * Configuration options for the BatchProcessor.
 *
 * These settings control batching behavior, timing, and processing strategy.
 * Proper configuration balances throughput with latency.
 *
 * @interface BatchProcessorConfig
 * @template T - Type of items to batch
 * @template R - Type of processing results
 */
export interface BatchProcessorConfig<T, R> {
  /**
   * Maximum number of items in a batch.
   *
   * **Why this exists:**
   * Limits batch size to prevent memory issues and ensure reasonable
   * processing times. Batch is processed when this limit is reached.
   *
   * **Tuning guidance:**
   * - Small (10-50): Lower latency, more overhead
   * - Medium (50-500): Balanced for most use cases
   * - Large (500+): Maximum throughput, higher latency
   *
   * @type {number}
   * @example 100 // Process in batches of 100
   */
  maxBatchSize: number;

  /**
   * Maximum time to wait before processing a batch (milliseconds).
   *
   * **Why this exists:**
   * Ensures items don't wait indefinitely for a full batch.
   * Batch is processed when this timeout expires, even if not full.
   *
   * **Tuning guidance:**
   * - Short (10-100ms): Low latency, smaller batches
   * - Medium (100-1000ms): Balanced
   * - Long (1000ms+): Maximum batching, higher latency
   *
   * @type {number}
   * @example 1000 // Wait up to 1 second
   */
  maxWaitTime: number;

  /**
   * Function to process a batch of items.
   *
   * **Why this exists:**
   * Abstracts the actual processing logic, making the batcher generic
   * and reusable for any batch operation.
   *
   * **Implementation requirements:**
   * - Must return array of results matching input order
   * - Should handle partial failures appropriately
   * - Should be idempotent if possible
   *
   * @type {(items: T[]) => Promise<R[]>}
   * @example
   * async (items) => {
   *   return await db.bulkInsert(items);
   * }
   */
  processBatch: (items: T[]) => Promise<R[]>;

  /**
   * Function to handle batch processing errors.
   *
   * **Why this exists:**
   * Provides custom error handling for batch failures.
   * Can implement retry logic, logging, or alerting.
   *
   * @type {(error: Error, items: T[]) => void}
   * @default Emits 'error' event
   * @example
   * (error, items) => {
   *   logger.error('Batch failed', { error, count: items.length });
   *   // Optionally retry or move to DLQ
   * }
   */
  onError?: (error: Error, items: T[]) => void;

  /**
   * Concurrency limit for batch processing.
   *
   * **Why this exists:**
   * Controls how many batches can be processed simultaneously.
   * Prevents overwhelming downstream systems.
   *
   * **Tuning guidance:**
   * - Low (1-2): Sequential processing, predictable load
   * - Medium (2-5): Balanced concurrency
   * - High (5+): Maximum throughput, requires capacity
   *
   * @type {number}
   * @default 1
   * @example 3 // Process up to 3 batches concurrently
   */
  concurrency?: number;

  /**
   * Whether to preserve order of results.
   *
   * **Why this exists:**
   * Some use cases require results in the same order as inputs.
   * Disabling can improve performance when order doesn't matter.
   *
   * **When to disable:**
   * - Results are independent
   * - Order doesn't affect correctness
   * - Performance is critical
   *
   * @type {boolean}
   * @default true
   * @example false // Allow out-of-order processing
   */
  preserveOrder?: boolean;
}

/**
 * Internal wrapper for queued items with promise callbacks.
 *
 * **Why this interface exists:**
 * Tracks individual items with their promise resolution callbacks,
 * enabling async/await API while batching internally.
 *
 * @interface BatchItem
 * @template T - Type of item
 * @template R - Type of result
 * @private
 */
interface BatchItem<T, R> {
  /** The item to process */
  item: T;
  /** Promise resolve function */
  resolve: (result: R) => void;
  /** Promise reject function */
  reject: (error: Error) => void;
  /** The promise exposed to callers */
  promise: Promise<R>;
  /** Unix timestamp when item was queued */
  timestamp: number;
}

/**
 * Intelligent batch processor for efficient bulk operations.
 *
 * Automatically batches individual items based on size and timing constraints,
 * processes them in bulk, and returns individual results. Supports concurrent
 * batch processing and order preservation.
 *
 * **How it works:**
 * 1. Items are queued as they arrive
 * 2. Batch is processed when size limit reached OR timeout expires
 * 3. Multiple batches can process concurrently (configurable)
 * 4. Results are mapped back to individual promises
 *
 * **Key Features:**
 * - Automatic batching based on size and time
 * - Concurrent batch processing
 * - Order preservation (optional)
 * - Individual promise resolution
 * - Graceful shutdown
 *
 * **When to use:**
 * - Database bulk operations
 * - API batch requests
 * - Message queue batching
 * - Log/event aggregation
 * - Any scenario with per-item overhead
 *
 * **Performance characteristics:**
 * - Latency: maxWaitTime (worst case)
 * - Throughput: Depends on batch size and concurrency
 * - Memory: O(queue size + batch size * concurrency)
 *
 * **Events emitted:**
 * - `itemAdded`: When item is queued
 * - `batchProcessed`: When batch completes
 * - `error`: When batch processing fails
 * - `destroy`: When processor is destroyed
 *
 * @class BatchProcessor
 * @extends EventEmitter
 * @template T - Type of items to batch
 * @template R - Type of processing results (defaults to T)
 *
 * @example
 * ```typescript
 * // Database bulk insert
 * const batcher = new BatchProcessor({
 *   maxBatchSize: 100,
 *   maxWaitTime: 1000,
 *   processBatch: async (items) => {
 *     return await db.bulkInsert(items);
 *   }
 * });
 *
 * // Add items individually
 * const result = await batcher.add(item);
 * ```
 *
 * @example
 * ```typescript
 * // API batch requests with concurrency
 * const batcher = new BatchProcessor({
 *   maxBatchSize: 50,
 *   maxWaitTime: 500,
 *   concurrency: 3,
 *   processBatch: async (ids) => {
 *     const response = await api.batchGet(ids);
 *     return response.data;
 *   }
 * });
 *
 * // Process many items
 * const results = await batcher.addMany(userIds);
 * ```
 *
 * @example
 * ```typescript
 * // Log aggregation with monitoring
 * const batcher = new BatchProcessor({
 *   maxBatchSize: 1000,
 *   maxWaitTime: 5000,
 *   processBatch: async (logs) => {
 *     await logService.sendBatch(logs);
 *     return logs.map(() => ({ sent: true }));
 *   },
 *   onError: (error, logs) => {
 *     console.error('Failed to send logs:', error);
 *     // Move to DLQ or retry
 *   }
 * });
 *
 * batcher.on('batchProcessed', ({ batchSize, duration }) => {
 *   metrics.histogram('batch.size', batchSize);
 *   metrics.histogram('batch.duration', duration);
 * });
 * ```
 *
 * @see {@link ConnectionPool}
 * @see {@link CircuitBreaker}
 */
export class BatchProcessor<T, R = T> extends EventEmitter {
  /**
   * The processor configuration with all optional fields resolved.
   * @private
   */
  private config: Required<BatchProcessorConfig<T, R>>;

  /**
   * Queue of pending items waiting to be batched.
   * @private
   */
  private queue: BatchItem<T, R>[] = [];

  /**
   * Timer for maxWaitTime timeout.
   * @private
   */
  private timer: NodeJS.Timeout | null = null;

  /**
   * Number of batches currently being processed.
   * @private
   */
  private processing: number = 0;

  /**
   * Active batch promises currently being processed.
   * @private
   */
  private activeBatches: Set<Promise<void>> = new Set();

  /**
   * Whether the processor has been destroyed.
   * @private
   */
  private isDestroyed: boolean = false;

  /**
   * Creates a new BatchProcessor instance.
   *
   * **Initialization:**
   * - Validates configuration
   * - Sets up defaults for optional fields
   * - Ready to accept items immediately
   *
   * @param {BatchProcessorConfig<T, R>} config - Processor configuration
   * @throws {Error} If configuration is invalid
   *
   * @example
   * const batcher = new BatchProcessor({
   *   maxBatchSize: 100,
   *   maxWaitTime: 1000,
   *   processBatch: async (items) => await process(items)
   * });
   */
  constructor(config: BatchProcessorConfig<T, R>) {
    super();
    this.config = {
      maxBatchSize: config.maxBatchSize,
      maxWaitTime: config.maxWaitTime,
      processBatch: config.processBatch,
      onError:
        config.onError ??
        ((error) => {
          if (this.listenerCount('error') > 0) {
            this.emit('error', error);
          }
        }),
      concurrency: config.concurrency ?? 1,
      preserveOrder: config.preserveOrder ?? true,
    };

    if (this.config.maxBatchSize < 1) {
      throw new Error('maxBatchSize must be >= 1');
    }
    if (this.config.maxWaitTime < 0) {
      throw new Error('maxWaitTime must be >= 0');
    }
    if (this.config.concurrency < 1) {
      throw new Error('concurrency must be >= 1');
    }
  }

  /**
   * Adds an item to the batch queue for processing.
   *
   * **How it works:**
   * 1. Queues item with promise callbacks
   * 2. Starts timer if not already running
   * 3. Triggers immediate processing if batch full
   * 4. Returns promise that resolves with result
   *
   * **Why this method exists:**
   * Provides async/await API for individual items while batching internally.
   *
   * @param {T} item - Item to process
   * @returns {Promise<R>} Promise resolving to processing result
   * @throws {Error} If processor is destroyed
   * @fires BatchProcessor#itemAdded
   *
   * @example
   * // Add single item
   * const result = await batcher.add({ id: 1, data: 'test' });
   *
   * @example
   * // Add multiple items individually
   * const results = await Promise.all([
   *   batcher.add(item1),
   *   batcher.add(item2),
   *   batcher.add(item3)
   * ]);
   */
  async add(item: T): Promise<R> {
    if (this.isDestroyed) {
      throw new Error('BatchProcessor has been destroyed');
    }

    let queueEntry: {
      item: T;
      resolve: (value: R) => void;
      reject: (reason?: Error) => void;
      promise: Promise<R>;
      timestamp: number;
    };

    const promise = new Promise<R>((resolve, reject) => {
      queueEntry = {
        item,
        resolve,
        reject,
        promise: null as unknown as Promise<R>, // Will be assigned immediately after
        timestamp: Date.now(),
      };

      this.queue.push(queueEntry);

      this.emit('itemAdded', {
        queueSize: this.queue.length,
        timestamp: Date.now(),
      });

      // Start timer if not already running
      if (!this.timer) {
        this.timer = setTimeout(() => {
          this.processBatches().catch((error) => {
            if (this.listenerCount('error') > 0) {
              this.emit('error', error);
            }
          });
        }, this.config.maxWaitTime);
      }

      // Process immediately if batch is full
      if (this.queue.length >= this.config.maxBatchSize) {
        this.processBatches().catch((error) => {
          if (this.listenerCount('error') > 0) {
            this.emit('error', error);
          }
        });
      }
    });

    // Store promise reference for later cleanup
    queueEntry!.promise = promise;

    // Prevent unhandled rejections if caller drops the promise
    promise.catch(() => {});
    return promise;
  }

  /**
   * Adds multiple items to the batch queue.
   *
   * **How it works:**
   * Maps each item to add() call and awaits all results.
   *
   * **Why this method exists:**
   * Convenience method for adding multiple items at once.
   *
   * @param {T[]} items - Array of items to process
   * @returns {Promise<R[]>} Promise resolving to array of results
   *
   * @example
   * const items = [{ id: 1 }, { id: 2 }, { id: 3 }];
   * const results = await batcher.addMany(items);
   */
  async addMany(items: T[]): Promise<R[]> {
    return Promise.all(items.map((item) => this.add(item)));
  }

  /**
   * Forces immediate processing of all queued items.
   *
   * **How it works:**
   * 1. Cancels wait timer
   * 2. Processes all queued items immediately
   * 3. Waits for processing to complete
   *
   * **Why this method exists:**
   * Allows manual control over batch timing, useful for
   * shutdown or when immediate processing is needed.
   *
   * @returns {Promise<void>}
   *
   * @example
   * // Flush before shutdown
   * await batcher.flush();
   * await batcher.destroy();
   *
   * @example
   * // Flush at end of request
   * await batcher.addMany(items);
   * await batcher.flush(); // Ensure all processed
   */
  async flush(): Promise<void> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }

    await this.processBatches();

    if (this.activeBatches.size > 0) {
      await Promise.allSettled(Array.from(this.activeBatches));
    }
  }

  /**
   * Gets current processor statistics.
   *
   * **Why this method exists:**
   * Provides visibility into queue depth and processing state
   * for monitoring and capacity planning.
   *
   * **Metrics returned:**
   * - queueSize: Items waiting to be processed
   * - processing: Number of batches currently processing
   * - maxBatchSize: Configured batch size limit
   * - maxWaitTime: Configured wait timeout
   * - concurrency: Configured concurrency limit
   *
   * @returns {BatchStats} Current statistics
   *
   * @example
   * const stats = batcher.getStats();
   * console.log(`Queue: ${stats.queueSize}, Processing: ${stats.processing}`);
   *
   * @example
   * // Alert on queue buildup
   * setInterval(() => {
   *   const stats = batcher.getStats();
   *   if (stats.queueSize > 1000) {
   *     alert('Batch queue backing up');
   *   }
   * }, 5000);
   */
  getStats() {
    return {
      queueSize: this.queue.length,
      processing: this.processing,
      maxBatchSize: this.config.maxBatchSize,
      maxWaitTime: this.config.maxWaitTime,
      concurrency: this.config.concurrency,
    };
  }

  /**
   * Destroys the batch processor and cleans up resources.
   *
   * **How it works:**
   * 1. Marks processor as destroyed
   * 2. Cancels timer
   * 3. Rejects all pending items
   * 4. Waits for ongoing processing to complete
   * 5. Removes event listeners
   *
   * **Why this method exists:**
   * Enables graceful shutdown with proper cleanup.
   *
   * **Idempotent:** Safe to call multiple times.
   *
   * @returns {Promise<void>}
   * @fires BatchProcessor#destroy
   *
   * @example
   * // Graceful shutdown
   * await batcher.flush(); // Process remaining items
   * await batcher.destroy();
   *
   * @example
   * // Cleanup in tests
   * afterEach(async () => {
   *   await batcher.destroy();
   * });
   */
  async destroy(): Promise<void> {
    if (this.isDestroyed) {
      return;
    }

    this.isDestroyed = true;

    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }

    while (this.processing > 0 || this.activeBatches.size > 0) {
      if (this.activeBatches.size > 0) {
        await Promise.allSettled(Array.from(this.activeBatches));
      } else {
        await new Promise((resolve) => setTimeout(resolve, 10));
      }
    }

    // Reject any items that never started processing
    const pendingItems = this.queue.splice(0, this.queue.length);
    const destroyError = new Error('BatchProcessor is being destroyed');
    for (const item of pendingItems) {
      item.promise?.catch(() => {});
      try {
        item.reject(destroyError);
      } catch {
        // Ignore if the consumer already awaited/rejected the promise.
      }
    }

    this.emit('destroy');
    this.removeAllListeners();
  }

  /**
   * Processes queued items in batches.
   *
   * **How it works:**
   * 1. Cancels timer
   * 2. Creates batches up to concurrency limit
   * 3. Processes each batch asynchronously
   * 4. Restarts timer if items remain
   *
   * **Why this method exists:**
   * Core batching logic that manages batch creation and concurrency.
   *
   * @returns {Promise<void>}
   * @private
   */
  private async processBatches(): Promise<void> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }

    while (this.queue.length > 0 && this.processing < this.config.concurrency) {
      const batchSize = Math.min(this.config.maxBatchSize, this.queue.length);
      const batch = this.queue.splice(0, batchSize);

      this.processing++;
      const batchPromise = this.processBatch(batch).catch((error) => {
        if (this.listenerCount('error') > 0) {
          this.emit('error', error);
        }
      });

      this.activeBatches.add(batchPromise);

      batchPromise.finally(() => {
        this.processing--;
        this.activeBatches.delete(batchPromise);
      });
    }

    // Restart timer if there are still items in the queue
    if (this.queue.length > 0 && !this.timer) {
      this.timer = setTimeout(() => {
        this.processBatches().catch((error) => {
          this.emit('error', error);
        });
      }, this.config.maxWaitTime);
    }
  }

  /**
   * Processes a single batch of items.
   *
   * **How it works:**
   * 1. Extracts items from batch wrappers
   * 2. Calls configured processBatch function
   * 3. Validates result count matches input count
   * 4. Resolves individual promises with results
   * 5. On error, rejects all promises and calls error handler
   *
   * **Why this method exists:**
   * Handles individual batch processing with proper error handling
   * and promise resolution.
   *
   * @param {BatchItem<T, R>[]} batch - Batch of items to process
   * @returns {Promise<void>}
   * @fires BatchProcessor#batchStart
   * @fires BatchProcessor#batchComplete
   * @fires BatchProcessor#batchError
   * @private
   */
  private async processBatch(batch: BatchItem<T, R>[]): Promise<void> {
    const startTime = Date.now();
    const items = batch.map((b) => b.item);

    this.emit('batchStart', {
      batchSize: batch.length,
      timestamp: startTime,
    });

    try {
      const results = await this.config.processBatch(items);

      if (results.length !== batch.length) {
        throw new Error(
          `Batch processor returned ${results.length} results for ${batch.length} items`
        );
      }

      // Resolve all promises
      for (let i = 0; i < batch.length; i++) {
        batch[i].resolve(results[i]);
      }

      const duration = Date.now() - startTime;
      this.emit('batchComplete', {
        batchSize: batch.length,
        duration,
        timestamp: Date.now(),
      });
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));

      this.emit('batchError', {
        batchSize: batch.length,
        error: err,
        timestamp: Date.now(),
      });

      // Call error handler
      this.config.onError(err, items);

      // Reject all promises
      for (const item of batch) {
        item.reject(err);
      }
    }
  }
}

/**
 * Creates a batch processor with the given configuration.
 *
 * Helper function for functional API. Equivalent to `new BatchProcessor(config)`.
 *
 * **Why this function exists:**
 * Provides functional alternative to constructor.
 *
 * @template T - Type of items to batch
 * @template R - Type of processing results
 * @param {BatchProcessorConfig<T, R>} config - Processor configuration
 * @returns {BatchProcessor<T, R>} New batch processor instance
 *
 * @example
 * const batcher = createBatchProcessor({
 *   maxBatchSize: 100,
 *   maxWaitTime: 1000,
 *   processBatch: async (items) => await process(items)
 * });
 */
export function createBatchProcessor<T, R = T>(
  config: BatchProcessorConfig<T, R>
): BatchProcessor<T, R> {
  return new BatchProcessor(config);
}

/**
 * Creates a simple batch processor with minimal configuration.
 *
 * Convenience function for common use cases where input and output
 * types are the same and defaults are acceptable.
 *
 * **Why this function exists:**
 * Simplifies creation for common scenarios.
 *
 * @template T - Type of items (input and output)
 * @param {number} maxBatchSize - Maximum batch size
 * @param {number} maxWaitTime - Maximum wait time in milliseconds
 * @param {(items: T[]) => Promise<T[]>} processBatch - Batch processing function
 * @returns {BatchProcessor<T>} New batch processor instance
 *
 * @example
 * // Simple database bulk insert
 * const batcher = createSimpleBatchProcessor(
 *   100,
 *   1000,
 *   async (items) => await db.bulkInsert(items)
 * );
 */
export function createSimpleBatchProcessor<T>(
  maxBatchSize: number,
  maxWaitTime: number,
  processBatch: (items: T[]) => Promise<T[]>
): BatchProcessor<T> {
  return new BatchProcessor({
    maxBatchSize,
    maxWaitTime,
    processBatch,
  });
}
