import { EventEmitter } from 'events';

/**
 * @fileoverview Retry policy with exponential backoff, jitter, and timeouts.
 *
 * Provides reusable retry semantics for connectors and processors. Combine with `HttpConnector`
 * or `GrpcConnector` to handle transient network failures uniformly while publishing lifecycle
 * events for observability.
 *
 * @see {@link RetryPolicy}
 * @see {@link withRetry}
 * @see {@link ../connectors/HttpConnector.HttpConnector | HttpConnector}
 */

/**
 * Configuration options for `RetryPolicy`.
 */
export interface RetryConfig {
  /** Maximum number of retry attempts (default 3). */
  maxAttempts?: number;
  /** Initial delay (ms) before first retry (default 1000). */
  initialDelay?: number;
  /** Maximum backoff delay (ms) (default 30000). */
  maxDelay?: number;
  /** Backoff multiplier applied each retry (default 2). */
  backoffMultiplier?: number;
  /** Whether to apply jitter to delays (default true). */
  jitter?: boolean;
  /** Optional custom retryable predicate. */
  isRetryable?: (error: Error) => boolean;
  /** Optional timeout per attempt (ms). */
  timeout?: number;
}

/**
 * Executes asynchronous operations with configurable retry semantics.
 *
 * **Example (wrapping a connector send):**
 * ```ts
 * const retry = new RetryPolicy({ maxAttempts: 5, jitter: true, timeout: 2000 });
 * await retry.execute(() => httpConnector.send(payload));
 * ```
 *
 * **Example (custom retryable predicate):**
 * ```ts
 * const retry = new RetryPolicy({
 *   isRetryable: error => error instanceof HttpError && error.status >= 500,
 * });
 * await retry.execute(doWork);
 * ```
 */
export class RetryPolicy extends EventEmitter {
  private config: Required<Omit<RetryConfig, 'isRetryable' | 'timeout'>> & Pick<RetryConfig, 'isRetryable' | 'timeout'>;

  /**
   * @param {RetryConfig} config - Retry behavior configuration
   */
  constructor(config: RetryConfig = {}) {
    super();
    this.config = {
      maxAttempts: config.maxAttempts ?? 3,
      initialDelay: config.initialDelay ?? 1000,
      maxDelay: config.maxDelay ?? 30000,
      backoffMultiplier: config.backoffMultiplier ?? 2,
      jitter: config.jitter ?? true,
      isRetryable: config.isRetryable,
      timeout: config.timeout,
    };
  }

  /**
   * Executes provided function with retry/backoff logic.
   *
   * Emits lifecycle events (`attempt`, `retry`, `success`, `error`, `exhausted`) to surface metrics
   * or trigger alerts when retries exceed thresholds.
   *
   * @template T
   * @param {() => Promise<T>} fn - Operation to execute
   * @returns {Promise<T>} Result of successful attempt
   * @throws {Error} Last encountered error when retries exhausted
   * @fires RetryPolicy#attempt
   * @fires RetryPolicy#success
   * @fires RetryPolicy#error
   * @fires RetryPolicy#retry
   * @fires RetryPolicy#exhausted
   */
  async execute<T>(fn: () => Promise<T>): Promise<T> {
    let lastError: Error | undefined;
    let attempt = 0;

    while (attempt < this.config.maxAttempts) {
      attempt++;

      try {
        this.emit('attempt', { attempt, maxAttempts: this.config.maxAttempts });

        const result = this.config.timeout
          ? await this._executeWithTimeout(fn, this.config.timeout)
          : await fn();

        this.emit('success', { attempt });
        return result;
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));

        this.emit('error', {
          attempt,
          error: lastError,
          willRetry: attempt < this.config.maxAttempts && this._isRetryable(lastError),
        });

        // Check if error is retryable
        if (!this._isRetryable(lastError)) {
          throw lastError;
        }

        // Don't delay after the last attempt
        if (attempt < this.config.maxAttempts) {
          const delay = this._calculateDelay(attempt);
          this.emit('retry', { attempt, delay, error: lastError });
          await this._sleep(delay);
        }
      }
    }

    this.emit('exhausted', { attempts: attempt, error: lastError });
    throw lastError || new Error('Max retry attempts reached');
  }

  /**
   * Calculates backoff delay for given attempt.
   *
   * Applies exponential backoff capped at `maxDelay` and adds +/-10% jitter when enabled to prevent
   * thundering herds.
   */
  private _calculateDelay(attempt: number): number {
    let delay = this.config.initialDelay * Math.pow(this.config.backoffMultiplier, attempt - 1);
    
    // Cap at max delay
    delay = Math.min(delay, this.config.maxDelay);

    // Add jitter if enabled
    if (this.config.jitter) {
      const jitterAmount = delay * 0.1; // 10% jitter
      delay = delay + (Math.random() * jitterAmount * 2 - jitterAmount);
    }

    return Math.floor(delay);
  }

  /**
   * Determines if error should trigger another retry.
   *
   * Consults custom predicate when provided; otherwise defaults to common transient network codes.
   */
  private _isRetryable(error: Error): boolean {
    if (this.config.isRetryable) {
      return this.config.isRetryable(error);
    }

    // Default retryable errors
    const retryableErrors = [
      'ECONNRESET',
      'ECONNREFUSED',
      'ETIMEDOUT',
      'ENOTFOUND',
      'ENETUNREACH',
      'EAI_AGAIN',
    ];

    return retryableErrors.some(code => 
      error.message.includes(code) || (error as any).code === code
    );
  }

  /**
   * Executes function with per-attempt timeout guard.
   *
   * Guards each call with `Promise.race`, ensuring the retry loop observes timeouts even when the
   * underlying function never resolves.
   */
  private async _executeWithTimeout<T>(fn: () => Promise<T>, timeout: number): Promise<T> {
    return Promise.race([
      fn(),
      new Promise<never>((_, reject) => {
        setTimeout(() => {
          reject(new Error(`Operation timed out after ${timeout}ms`));
        }, timeout);
      }),
    ]);
  }

  /**
   * Sleeps asynchronously for given milliseconds.
   *
   * Wrapped to simplify unit testing and enable dependency injection if deterministic timing is
   * required.
   */
  private _sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Returns clone of current configuration.
   *
   * Exposes effective config with defaults applied so callers can render diagnostics or telemetry.
   */
  getConfig(): RetryConfig {
    return { ...this.config };
  }

  /**
   * Updates policy configuration at runtime.
   *
   * Useful for live tuning via admin APIs—changes take effect on the next retry attempt.
   */
  updateConfig(config: Partial<RetryConfig>): void {
    this.config = {
      ...this.config,
      ...config,
    };
  }
}

/**
 * Decorates async function with retry policy execution.
 */
export function withRetry<T extends (...args: unknown[]) => Promise<unknown>>(
  fn: T,
  config?: RetryConfig
): T {
  const retryPolicy = new RetryPolicy(config);
  
  return (async (...args: unknown[]) => {
    return retryPolicy.execute(() => fn(...args));
  }) as T;
}

/**
 * Common retry configuration presets.
 */
export const RetryPresets = {
  /**
   * Quick retry for fast operations
   */
  quick: {
    maxAttempts: 3,
    initialDelay: 100,
    maxDelay: 1000,
    backoffMultiplier: 2,
  },

  /**
   * Standard retry for most operations
   */
  standard: {
    maxAttempts: 3,
    initialDelay: 1000,
    maxDelay: 10000,
    backoffMultiplier: 2,
  },

  /**
   * Aggressive retry for critical operations
   */
  aggressive: {
    maxAttempts: 5,
    initialDelay: 500,
    maxDelay: 30000,
    backoffMultiplier: 2,
  },

  /**
   * Patient retry for long-running operations
   */
  patient: {
    maxAttempts: 10,
    initialDelay: 2000,
    maxDelay: 60000,
    backoffMultiplier: 1.5,
  },
};
