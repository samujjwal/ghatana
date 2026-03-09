/**
 * Agent Service Circuit Breaker
 * 
 * Implements circuit breaker pattern with exponential backoff for Java agent calls
 * 
 * @doc.type service
 * @doc.purpose Resilient agent service communication
 * @doc.layer infrastructure
 * @doc.pattern CircuitBreaker
 */

import { systemLogger as logger } from '../lib/logger';

export enum CircuitState {
  CLOSED = 'CLOSED',       // Normal operation
  OPEN = 'OPEN',          // Failing, rejecting requests
  HALF_OPEN = 'HALF_OPEN' // Testing if service recovered
}

export interface CircuitBreakerConfig {
  failureThreshold: number;      // Number of failures before opening
  successThreshold: number;      // Number of successes to close from half-open
  timeout: number;               // Request timeout in ms
  resetTimeout: number;          // Time to wait before trying half-open
  monitoringWindow: number;      // Time window for counting failures
}

export interface CircuitBreakerStats {
  state: CircuitState;
  failures: number;
  successes: number;
  rejections: number;
  lastFailureTime: number | null;
  lastSuccessTime: number | null;
  nextAttemptTime: number | null;
}

const DEFAULT_CONFIG: CircuitBreakerConfig = {
  failureThreshold: 5,
  successThreshold: 2,
  timeout: 30000,
  resetTimeout: 60000,
  monitoringWindow: 120000,
};

export class CircuitBreaker {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount: number = 0;
  private successCount: number = 0;
  private rejectionCount: number = 0;
  private lastFailureTime: number | null = null;
  private lastSuccessTime: number | null = null;
  private nextAttemptTime: number | null = null;
  private recentFailures: number[] = [];

  constructor(
    private readonly name: string,
    private readonly config: CircuitBreakerConfig = DEFAULT_CONFIG
  ) {}

  /**
   * Execute function with circuit breaker protection
   */
  async execute<T>(fn: () => Promise<T>, fallback?: () => Promise<T>): Promise<T> {
    if (this.shouldReject()) {
      this.rejectionCount++;
      logger.warn(`Circuit breaker ${this.name} OPEN - request rejected`, {
        service: this.name,
        state: this.state,
        nextAttempt: this.nextAttemptTime,
      });

      if (fallback) {
        return fallback();
      }

      throw new Error(`Circuit breaker ${this.name} is OPEN - service unavailable`);
    }

    const startTime = Date.now();

    try {
      // Wrap with timeout
      const result = await this.withTimeout(fn(), this.config.timeout);
      
      this.onSuccess(Date.now() - startTime);
      return result;
    } catch (error) {
      this.onFailure(error, Date.now() - startTime);

      if (fallback) {
        logger.info(`Using fallback for ${this.name} after failure`, {
          service: this.name,
          error: error instanceof Error ? error.message : String(error),
        });
        return fallback();
      }

      throw error;
    }
  }

  /**
   * Check if request should be rejected
   */
  private shouldReject(): boolean {
    const now = Date.now();

    switch (this.state) {
      case CircuitState.CLOSED:
        return false;

      case CircuitState.OPEN:
        if (this.nextAttemptTime && now >= this.nextAttemptTime) {
          logger.info(`Circuit breaker ${this.name} transitioning to HALF_OPEN`, {
            service: this.name,
          });
          this.state = CircuitState.HALF_OPEN;
          this.successCount = 0;
          return false;
        }
        return true;

      case CircuitState.HALF_OPEN:
        // Only allow one request at a time in half-open
        return false;

      default:
        return false;
    }
  }

  /**
   * Handle successful execution
   */
  private onSuccess(duration: number): void {
    this.lastSuccessTime = Date.now();

    logger.debug(`Circuit breaker ${this.name} - successful call`, {
      service: this.name,
      state: this.state,
      duration,
    });

    if (this.state === CircuitState.HALF_OPEN) {
      this.successCount++;

      if (this.successCount >= this.config.successThreshold) {
        logger.info(`Circuit breaker ${this.name} transitioning to CLOSED`, {
          service: this.name,
          successCount: this.successCount,
        });
        this.state = CircuitState.CLOSED;
        this.failureCount = 0;
        this.recentFailures = [];
      }
    } else if (this.state === CircuitState.CLOSED) {
      // Reset failure count on success
      this.cleanupOldFailures();
      if (this.failureCount > 0) {
        this.failureCount = Math.max(0, this.failureCount - 1);
      }
    }
  }

  /**
   * Handle failed execution
   */
  private onFailure(error: unknown, duration: number): void {
    this.lastFailureTime = Date.now();
    this.recentFailures.push(this.lastFailureTime);
    this.cleanupOldFailures();

    logger.error(`Circuit breaker ${this.name} - failed call`, error, {
      service: this.name,
      state: this.state,
      duration,
      failureCount: this.failureCount + 1,
    });

    if (this.state === CircuitState.HALF_OPEN) {
      // Immediate reopen on any failure in half-open
      this.openCircuit();
    } else if (this.state === CircuitState.CLOSED) {
      this.failureCount++;

      if (this.failureCount >= this.config.failureThreshold) {
        this.openCircuit();
      }
    }
  }

  /**
   * Open the circuit breaker
   */
  private openCircuit(): void {
    this.state = CircuitState.OPEN;
    this.nextAttemptTime = Date.now() + this.config.resetTimeout;
    
    logger.error(`Circuit breaker ${this.name} OPENED`, undefined, {
      service: this.name,
      failureCount: this.failureCount,
      nextAttempt: new Date(this.nextAttemptTime).toISOString(),
    });
  }

  /**
   * Remove failures outside monitoring window
   */
  private cleanupOldFailures(): void {
    const cutoff = Date.now() - this.config.monitoringWindow;
    this.recentFailures = this.recentFailures.filter(t => t > cutoff);
    this.failureCount = this.recentFailures.length;
  }

  /**
   * Wrap promise with timeout
   */
  private withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
    return Promise.race([
      promise,
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs)
      ),
    ]);
  }

  /**
   * Get current circuit breaker statistics
   */
  getStats(): CircuitBreakerStats {
    return {
      state: this.state,
      failures: this.failureCount,
      successes: this.successCount,
      rejections: this.rejectionCount,
      lastFailureTime: this.lastFailureTime,
      lastSuccessTime: this.lastSuccessTime,
      nextAttemptTime: this.nextAttemptTime,
    };
  }

  /**
   * Manually reset circuit breaker (for testing/admin)
   */
  reset(): void {
    logger.info(`Circuit breaker ${this.name} manually reset`, {
      service: this.name,
    });
    
    this.state = CircuitState.CLOSED;
    this.failureCount = 0;
    this.successCount = 0;
    this.rejectionCount = 0;
    this.recentFailures = [];
    this.nextAttemptTime = null;
  }
}

// Global circuit breaker instances
const circuitBreakers = new Map<string, CircuitBreaker>();

/**
 * Get or create circuit breaker for a service
 */
export function getCircuitBreaker(
  serviceName: string,
  config?: Partial<CircuitBreakerConfig>
): CircuitBreaker {
  if (!circuitBreakers.has(serviceName)) {
    circuitBreakers.set(
      serviceName,
      new CircuitBreaker(serviceName, { ...DEFAULT_CONFIG, ...config })
    );
  }
  return circuitBreakers.get(serviceName)!;
}

/**
 * Get stats for all circuit breakers
 */
export function getAllCircuitBreakerStats(): Record<string, CircuitBreakerStats> {
  const stats: Record<string, CircuitBreakerStats> = {};
  
  for (const [name, breaker] of circuitBreakers.entries()) {
    stats[name] = breaker.getStats();
  }
  
  return stats;
}
