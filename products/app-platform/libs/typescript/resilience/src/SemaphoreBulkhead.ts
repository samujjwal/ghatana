/**
 * Semaphore-based bulkhead that limits maximum concurrent async executions and
 * queues overflow requests up to a configurable limit (STORY-K18-005).
 *
 * Design:
 * - `maxConcurrent` — maximum number of async operations executing simultaneously
 * - `maxQueue`      — maximum number of callers waiting for a slot (0 = no queue)
 * - `queueTimeout`  — milliseconds a caller waits in the queue before timing out
 *
 * Inspiration: Google SRE / Netflix Hystrix thread-pool bulkhead pattern,
 * adapted for Node.js single-threaded async via Promise semaphore.
 *
 * @module resilience
 */

/** Configuration for {@link SemaphoreBulkhead}. */
export interface BulkheadOptions {
  /** Maximum concurrent executions allowed through the bulkhead. */
  maxConcurrent: number;
  /** Maximum number of queued callers waiting for a slot. 0 = reject immediately when full. */
  maxQueue?: number;
  /** Milliseconds a caller may wait in the queue before {@link BulkheadQueueTimeoutError} is thrown. */
  queueTimeoutMs?: number;
  /** Human-readable name used in error messages and logs. */
  name?: string;
}

export class BulkheadRejectedError extends Error {
  constructor(name: string) {
    super(`Bulkhead '${name}' is full — concurrent slots and queue are both at capacity`);
    this.name = 'BulkheadRejectedError';
  }
}

export class BulkheadQueueTimeoutError extends Error {
  constructor(name: string, timeoutMs: number) {
    super(`Bulkhead '${name}' queue timeout after ${timeoutMs}ms`);
    this.name = 'BulkheadQueueTimeoutError';
  }
}

/**
 * Async semaphore-based bulkhead that gates concurrent access to downstream resources.
 *
 * Usage:
 * ```ts
 * const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 10, maxQueue: 50, queueTimeoutMs: 1000 });
 * const result = await bulkhead.execute(() => fetch('https://payment-gateway/charge'));
 * ```
 */
export class SemaphoreBulkhead {
  private readonly name: string;
  private readonly maxConcurrent: number;
  private readonly maxQueue: number;
  private readonly queueTimeoutMs: number;

  private activeConcurrent = 0;
  private readonly waitingQueue: Array<{
    resolve: () => void;
    reject: (err: Error) => void;
    timer: ReturnType<typeof setTimeout> | null;
  }> = [];

  // Metrics (read-only access for dashboards)
  private _totalExecuted = 0;
  private _totalRejected = 0;
  private _totalQueueTimeouts = 0;

  constructor(options: BulkheadOptions) {
    if (options.maxConcurrent <= 0) {
      throw new RangeError('maxConcurrent must be > 0');
    }
    this.name = options.name ?? 'bulkhead';
    this.maxConcurrent = options.maxConcurrent;
    this.maxQueue = options.maxQueue ?? 0;
    this.queueTimeoutMs = options.queueTimeoutMs ?? 5000;
  }

  /**
   * Executes `operation` inside the bulkhead. Acquires a semaphore slot, runs
   * the operation, then releases the slot (even if the operation throws).
   *
   * @throws {BulkheadRejectedError}      when both semaphore and queue are full
   * @throws {BulkheadQueueTimeoutError}  when the caller times out in the queue
   */
  async execute<T>(operation: () => Promise<T>): Promise<T> {
    await this.acquire();
    try {
      this._totalExecuted++;
      return await operation();
    } finally {
      this.release();
    }
  }

  /** Returns the number of operations currently executing. */
  get concurrent(): number {
    return this.activeConcurrent;
  }

  /** Returns the number of callers currently waiting in the queue. */
  get queued(): number {
    return this.waitingQueue.length;
  }

  get metrics() {
    return {
      concurrent: this.activeConcurrent,
      queued: this.waitingQueue.length,
      totalExecuted: this._totalExecuted,
      totalRejected: this._totalRejected,
      totalQueueTimeouts: this._totalQueueTimeouts,
    };
  }

  // ── Private ─────────────────────────────────────────────────────────────────

  private acquire(): Promise<void> {
    if (this.activeConcurrent < this.maxConcurrent) {
      this.activeConcurrent++;
      return Promise.resolve();
    }

    // No slot available — check queue capacity
    if (this.maxQueue === 0 || this.waitingQueue.length >= this.maxQueue) {
      this._totalRejected++;
      return Promise.reject(new BulkheadRejectedError(this.name));
    }

    // Enqueue the caller with a timeout guard
    return new Promise<void>((resolve, reject) => {
      let timer: ReturnType<typeof setTimeout> | null = null;

      const entry = { resolve, reject, timer };
      this.waitingQueue.push(entry);

      timer = setTimeout(() => {
        const idx = this.waitingQueue.indexOf(entry);
        if (idx !== -1) {
          this.waitingQueue.splice(idx, 1);
          this._totalQueueTimeouts++;
          reject(new BulkheadQueueTimeoutError(this.name, this.queueTimeoutMs));
        }
      }, this.queueTimeoutMs);

      entry.timer = timer;
    });
  }

  private release(): void {
    const next = this.waitingQueue.shift();
    if (next) {
      if (next.timer !== null) clearTimeout(next.timer);
      next.resolve();
      // Slot stays occupied for the dequeued caller — no change to activeConcurrent
    } else {
      this.activeConcurrent--;
    }
  }
}
