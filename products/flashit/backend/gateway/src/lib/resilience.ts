/**
 * Resilience Utilities
 * Week 14 Day 66 - Reliability sweep for error handling & recovery
 * 
 * @doc.type library
 * @doc.purpose Provide retry logic, circuit breakers, and graceful degradation
 * @doc.layer product
 * @doc.pattern ResiliencePattern
 */

import { prisma } from './prisma.js';

// ============================================================================
// Types
// ============================================================================

export interface RetryOptions {
  maxRetries: number;
  initialDelay: number; // milliseconds
  maxDelay: number; // milliseconds
  backoffMultiplier: number;
  shouldRetry?: (error: Error) => boolean;
}

export interface CircuitBreakerOptions {
  failureThreshold: number; // Number of failures before opening
  successThreshold: number; // Number of successes to close
  timeout: number; // milliseconds to wait in open state
}

export enum CircuitState {
  CLOSED = 'CLOSED', // Normal operation
  OPEN = 'OPEN', // Failing, rejecting calls
  HALF_OPEN = 'HALF_OPEN', // Testing if service recovered
}

// ============================================================================
// Retry with Exponential Backoff
// ============================================================================

const DEFAULT_RETRY_OPTIONS: RetryOptions = {
  maxRetries: 3,
  initialDelay: 1000, // 1 second
  maxDelay: 30000, // 30 seconds
  backoffMultiplier: 2,
  shouldRetry: (error: Error) => {
    // Retry on network errors, timeouts, 5xx errors
    return (
      error.message.includes('timeout') ||
      error.message.includes('ECONNREFUSED') ||
      error.message.includes('ETIMEDOUT') ||
      error.message.includes('5')
    );
  },
};

/**
 * Retry an async operation with exponential backoff
 * 
 * @example
 * const result = await withRetry(
 *   () => openai.chat.completions.create(...),
 *   { maxRetries: 3, initialDelay: 1000 }
 * );
 */
export async function withRetry<T>(
  operation: () => Promise<T>,
  options: Partial<RetryOptions> = {}
): Promise<T> {
  const opts = { ...DEFAULT_RETRY_OPTIONS, ...options };
  let lastError: Error;
  let delay = opts.initialDelay;

  for (let attempt = 0; attempt <= opts.maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error as Error;

      // Don't retry if we've exhausted attempts
      if (attempt === opts.maxRetries) {
        break;
      }

      // Check if we should retry this error
      if (opts.shouldRetry && !opts.shouldRetry(lastError)) {
        break;
      }

      // Log retry attempt
      fastify?.log.warn({
        msg: 'Operation failed, retrying...',
        attempt: attempt + 1,
        maxRetries: opts.maxRetries,
        delay,
        error: lastError.message,
      });

      // Wait before retrying
      await sleep(delay);

      // Increase delay for next attempt (exponential backoff)
      delay = Math.min(delay * opts.backoffMultiplier, opts.maxDelay);
    }
  }

  throw lastError!;
}

/**
 * Sleep for specified milliseconds
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ============================================================================
// Circuit Breaker
// ============================================================================

export class CircuitBreaker {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount = 0;
  private successCount = 0;
  private nextAttempt = Date.now();
  private readonly options: CircuitBreakerOptions;
  private readonly name: string;

  constructor(name: string, options: Partial<CircuitBreakerOptions> = {}) {
    this.name = name;
    this.options = {
      failureThreshold: options.failureThreshold ?? 5,
      successThreshold: options.successThreshold ?? 2,
      timeout: options.timeout ?? 60000, // 1 minute
    };
  }

  /**
   * Execute operation with circuit breaker protection
   */
  async execute<T>(operation: () => Promise<T>): Promise<T> {
    // Check if circuit is open
    if (this.state === CircuitState.OPEN) {
      if (Date.now() < this.nextAttempt) {
        throw new CircuitBreakerOpenError(
          `Circuit breaker [${this.name}] is OPEN. Try again in ${Math.round(
            (this.nextAttempt - Date.now()) / 1000
          )}s`
        );
      }
      // Transition to half-open to test service
      this.state = CircuitState.HALF_OPEN;
      this.successCount = 0;
      fastify?.log.info({
        msg: 'Circuit breaker transitioning to HALF_OPEN',
        name: this.name,
      });
    }

    try {
      const result = await operation();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  /**
   * Handle successful operation
   */
  private onSuccess(): void {
    this.failureCount = 0;

    if (this.state === CircuitState.HALF_OPEN) {
      this.successCount++;

      if (this.successCount >= this.options.successThreshold) {
        this.state = CircuitState.CLOSED;
        fastify?.log.info({
          msg: 'Circuit breaker CLOSED (service recovered)',
          name: this.name,
        });
      }
    }
  }

  /**
   * Handle failed operation
   */
  private onFailure(): void {
    this.failureCount++;
    this.successCount = 0;

    if (
      this.state === CircuitState.HALF_OPEN ||
      this.failureCount >= this.options.failureThreshold
    ) {
      this.state = CircuitState.OPEN;
      this.nextAttempt = Date.now() + this.options.timeout;
      fastify?.log.error({
        msg: 'Circuit breaker OPEN (too many failures)',
        name: this.name,
        failureCount: this.failureCount,
        nextAttempt: new Date(this.nextAttempt).toISOString(),
      });
    }
  }

  /**
   * Get current circuit state
   */
  getState(): { state: CircuitState; failureCount: number; successCount: number } {
    return {
      state: this.state,
      failureCount: this.failureCount,
      successCount: this.successCount,
    };
  }

  /**
   * Manually reset circuit to CLOSED
   */
  reset(): void {
    this.state = CircuitState.CLOSED;
    this.failureCount = 0;
    this.successCount = 0;
    this.nextAttempt = Date.now();
    fastify?.log.info({
      msg: 'Circuit breaker manually reset',
      name: this.name,
    });
  }
}

export class CircuitBreakerOpenError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CircuitBreakerOpenError';
  }
}

// ============================================================================
// Graceful Degradation Helpers
// ============================================================================

/**
 * Try primary operation, fallback to secondary if it fails
 * 
 * @example
 * const embedding = await withFallback(
 *   () => openai.embeddings.create(...),
 *   () => localEmbedding(text)
 * );
 */
export async function withFallback<T>(
  primary: () => Promise<T>,
  fallback: () => Promise<T>
): Promise<T> {
  try {
    return await primary();
  } catch (error) {
    fastify?.log.warn({
      msg: 'Primary operation failed, using fallback',
      error: (error as Error).message,
    });
    return await fallback();
  }
}

/**
 * Try operation with timeout
 * 
 * @example
 * const result = await withTimeout(
 *   () => slowApiCall(),
 *   5000 // 5 second timeout
 * );
 */
export async function withTimeout<T>(
  operation: () => Promise<T>,
  timeoutMs: number
): Promise<T> {
  return Promise.race([
    operation(),
    new Promise<T>((_, reject) =>
      setTimeout(() => reject(new TimeoutError(`Operation timed out after ${timeoutMs}ms`)), timeoutMs)
    ),
  ]);
}

export class TimeoutError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TimeoutError';
  }
}

// ============================================================================
// Global Circuit Breakers for AI Services
// ============================================================================

export const circuitBreakers = {
  openai: new CircuitBreaker('OpenAI', {
    failureThreshold: 5,
    successThreshold: 2,
    timeout: 60000, // 1 minute
  }),
  whisper: new CircuitBreaker('Whisper', {
    failureThreshold: 3,
    successThreshold: 2,
    timeout: 120000, // 2 minutes
  }),
  embeddings: new CircuitBreaker('Embeddings', {
    failureThreshold: 5,
    successThreshold: 2,
    timeout: 60000,
  }),
};

// ============================================================================
// Health Check Utilities
// ============================================================================

export interface HealthStatus {
  status: 'healthy' | 'degraded' | 'unhealthy';
  checks: {
    [key: string]: {
      status: 'pass' | 'fail';
      message?: string;
      responseTime?: number;
    };
  };
  timestamp: string;
}

/**
 * Perform health checks on critical services
 */
export async function performHealthChecks(): Promise<HealthStatus> {
  const checks: HealthStatus['checks'] = {};

  // Check database
  const dbStart = Date.now();
  try {
    const { prisma } = await import('../lib/prisma.js');
    await prisma.$queryRaw`SELECT 1`;
    checks.database = {
      status: 'pass',
      responseTime: Date.now() - dbStart,
    };
  } catch (error) {
    checks.database = {
      status: 'fail',
      message: (error as Error).message,
      responseTime: Date.now() - dbStart,
    };
  }

  // Check circuit breakers
  for (const [name, breaker] of Object.entries(circuitBreakers)) {
    const state = breaker.getState();
    checks[`circuit_${name}`] = {
      status: state.state === CircuitState.OPEN ? 'fail' : 'pass',
      message: `State: ${state.state}, Failures: ${state.failureCount}`,
    };
  }

  // Determine overall status
  const failedChecks = Object.values(checks).filter((c) => c.status === 'fail');
  let status: HealthStatus['status'];
  if (failedChecks.length === 0) {
    status = 'healthy';
  } else if (failedChecks.length <= 1) {
    status = 'degraded';
  } else {
    status = 'unhealthy';
  }

  return {
    status,
    checks,
    timestamp: new Date().toISOString(),
  };
}
