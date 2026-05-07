/**
 * Circuit Breaker Utility
 *
 * Provides circuit breaker pattern implementation for external services
 * to prevent cascade failures and provide fallback behavior.
 *
 * @doc.type utility
 * @doc.purpose Implement circuit breaker pattern for external service calls
 * @doc.layer product
 * @doc.pattern Circuit Breaker
 */

import CircuitBreaker from "opossum";
import type { Logger } from "pino";

export interface CircuitBreakerOptions {
  // Time in milliseconds that a circuit should remain open before transitioning to half-open
  resetTimeout?: number;
  // Percentage of requests that can fail before the circuit opens
  errorThresholdPercentage?: number;
  // Minimum number of requests before the circuit starts calculating error percentages
  rollingCountTimeout?: number;
  // Minimum number of requests before the circuit starts calculating error percentages
  rollingCountBuckets?: number;
}

export interface ServiceWrapper<T> {
  name: string;
  circuitBreaker: CircuitBreaker<unknown[], T>;
  execute: (...args: unknown[]) => Promise<T>;
  healthCheck: () => Promise<boolean>;
}

const DEFAULT_OPTIONS: CircuitBreakerOptions = {
  resetTimeout: 30000, // 30 seconds
  errorThresholdPercentage: 50, // 50% failure rate
  rollingCountTimeout: 60000, // 1 minute
  rollingCountBuckets: 12, // 12 buckets of 5 seconds each
};

/**
 * Create a circuit breaker for an external service
 */
export function createCircuitBreaker<T>(
  serviceName: string,
  action: (...args: unknown[]) => Promise<T>,
  options: CircuitBreakerOptions = {},
  logger?: Logger,
): ServiceWrapper<T> {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  const breakerOptions: CircuitBreakerOptions = {};
  if (opts.resetTimeout !== undefined) {
    breakerOptions.resetTimeout = opts.resetTimeout;
  }
  if (opts.errorThresholdPercentage !== undefined) {
    breakerOptions.errorThresholdPercentage = opts.errorThresholdPercentage;
  }
  if (opts.rollingCountTimeout !== undefined) {
    breakerOptions.rollingCountTimeout = opts.rollingCountTimeout;
  }
  if (opts.rollingCountBuckets !== undefined) {
    breakerOptions.rollingCountBuckets = opts.rollingCountBuckets;
  }

  const breaker = new CircuitBreaker<unknown[], T>(action, breakerOptions);

  // Setup event listeners for monitoring
  if (logger) {
    breaker.on("open", () => {
      logger.warn({ serviceName }, `Circuit breaker opened for ${serviceName}`);
    });

    breaker.on("halfOpen", () => {
      logger.info(
        { serviceName },
        `Circuit breaker half-open for ${serviceName}`,
      );
    });

    breaker.on("close", () => {
      logger.info({ serviceName }, `Circuit breaker closed for ${serviceName}`);
    });

    breaker.on("fallback", (result: unknown) => {
      logger.warn(
        {
          serviceName,
          result:
            typeof result === "object"
              ? JSON.stringify(result)
              : String(result),
        },
        `Fallback triggered for ${serviceName}`,
      );
    });

    breaker.on("reject", () => {
      logger.warn(
        { serviceName },
        `Request rejected by circuit breaker for ${serviceName}`,
      );
    });
  }

  return {
    name: serviceName,
    circuitBreaker: breaker,
    execute: async (...args: unknown[]): Promise<T> => {
      try {
        return await breaker.fire(...args);
      } catch (error) {
        // Re-throw the error after circuit breaker handles it
        throw error;
      }
    },
    healthCheck: async (): Promise<boolean> => {
      return breaker.opened === false;
    },
  };
}

/**
 * Create a circuit breaker with fallback for AI services
 */
export function createAICircuitBreaker<T>(
  serviceName: string,
  action: (...args: unknown[]) => Promise<T>,
  fallbackAction?: (...args: unknown[]) => Promise<T>,
  logger?: Logger,
): ServiceWrapper<T> {
  const wrapper = createCircuitBreaker<T>(
    serviceName,
    action,
    {
      resetTimeout: 60000, // AI services may need longer recovery time
      errorThresholdPercentage: 40, // More lenient for AI services
      rollingCountTimeout: 120000, // 2 minutes rolling window
    },
    logger,
  );

  // Add fallback if provided
  if (fallbackAction) {
    wrapper.circuitBreaker.fallback(fallbackAction);
  }

  return wrapper;
}

/**
 * Create a circuit breaker for payment services
 */
export function createPaymentCircuitBreaker<T>(
  serviceName: string,
  action: (...args: unknown[]) => Promise<T>,
  logger?: Logger,
): ServiceWrapper<T> {
  return createCircuitBreaker<T>(
    serviceName,
    action,
    {
      resetTimeout: 300000, // 5 minutes for payment services
      errorThresholdPercentage: 25, // Lower threshold for payment services
      rollingCountTimeout: 300000, // 5 minutes rolling window
    },
    logger,
  );
}

/**
 * Health check for all circuit breakers
 */
export async function checkAllCircuitBreakers(
  circuitBreakers: ServiceWrapper<unknown>[],
): Promise<Record<string, boolean>> {
  const results: Record<string, boolean> = {};

  await Promise.allSettled(
    circuitBreakers.map(async (wrapper) => {
      try {
        results[wrapper.name] = await wrapper.healthCheck();
      } catch (_error) {
        results[wrapper.name] = false;
      }
    }),
  );

  return results;
}

/**
 * Get circuit breaker statistics for monitoring
 */
export function getCircuitBreakerStats(wrapper: ServiceWrapper<unknown>): {
  name: string;
  state: string;
  totalRequests: number;
  totalFailures: number;
  totalSuccesses: number;
  totalTimeouts: number;
  averageResponseTime: number;
  percentFailure: number;
} {
  const stats = wrapper.circuitBreaker.stats as Record<string, number | undefined>;
  const state = wrapper.circuitBreaker.opened ? "open" : "closed";
  const totalRequests = stats.total ?? stats.fires ?? 0;
  const totalFailures = stats.failures ?? 0;
  const totalSuccesses = Math.max(0, (stats.fires ?? 0) - totalFailures);

  return {
    name: wrapper.name,
    state,
    totalRequests,
    totalFailures,
    totalSuccesses,
    totalTimeouts: stats.timeouts ?? 0,
    averageResponseTime: stats.mean ?? 0,
    percentFailure: stats.percentFailures ?? 0,
  };
}
