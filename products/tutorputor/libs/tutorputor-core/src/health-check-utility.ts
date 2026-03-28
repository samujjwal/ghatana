/**
 * Standardized Health Check Utility
 * 
 * Provides consistent health check patterns for all services
 * with database, Redis, external API, and composite checks.
 * 
 * @doc.type utility
 * @doc.purpose Standardized health check implementations
 * @doc.layer platform
 */

import { createStandaloneLogger } from './logger';

const logger = createStandaloneLogger({ component: 'HealthCheckUtility' });

export interface HealthCheckResult {
  healthy: boolean;
  timestamp: string;
  checks: Record<string, ComponentHealth>;
  duration: number;
}

export interface ComponentHealth {
  status: 'healthy' | 'unhealthy' | 'degraded';
  message?: string;
  latency?: number;
  details?: Record<string, unknown>;
}

export interface HealthCheckConfig {
  timeout?: number;
  includeDetails?: boolean;
}

const DEFAULT_CONFIG: HealthCheckConfig = {
  timeout: 5000,
  includeDetails: true,
};

/**
 * Creates a database health check function
 */
export function createDatabaseHealthCheck(
  prisma: any,
  config: HealthCheckConfig = {},
): () => Promise<ComponentHealth> {
  const { timeout } = { ...DEFAULT_CONFIG, ...config };

  return async () => {
    const start = Date.now();
    try {
      await Promise.race([
        prisma.$queryRaw`SELECT 1`,
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Database health check timeout')), timeout)
        ),
      ]);

      const latency = Date.now() - start;
      return {
        status: latency < 100 ? 'healthy' : 'degraded',
        message: latency < 100 ? 'Database responsive' : 'Database slow',
        latency,
      };
    } catch (error) {
      logger.error({
        message: 'Database health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'Database connection failed',
        latency: Date.now() - start,
      };
    }
  };
}

/**
 * Creates a Redis health check function
 */
export function createRedisHealthCheck(
  redis: any,
  config: HealthCheckConfig = {},
): () => Promise<ComponentHealth> {
  const { timeout } = { ...DEFAULT_CONFIG, ...config };

  return async () => {
    const start = Date.now();
    try {
      await Promise.race([
        redis.ping(),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Redis health check timeout')), timeout)
        ),
      ]);

      const latency = Date.now() - start;
      return {
        status: latency < 50 ? 'healthy' : 'degraded',
        message: latency < 50 ? 'Redis responsive' : 'Redis slow',
        latency,
      };
    } catch (error) {
      logger.error({
        message: 'Redis health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'Redis connection failed',
        latency: Date.now() - start,
      };
    }
  };
}

/**
 * Creates an HTTP endpoint health check function
 */
export function createHttpHealthCheck(
  url: string,
  config: HealthCheckConfig = {},
): () => Promise<ComponentHealth> {
  const { timeout } = { ...DEFAULT_CONFIG, ...config };

  return async () => {
    const start = Date.now();
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), timeout);

      const response = await fetch(url, {
        method: 'GET',
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      const latency = Date.now() - start;
      const healthy = response.ok;

      return {
        status: healthy ? (latency < 200 ? 'healthy' : 'degraded') : 'unhealthy',
        message: healthy
          ? `Endpoint responsive (${response.status})`
          : `Endpoint error (${response.status})`,
        latency,
        details: {
          statusCode: response.status,
          statusText: response.statusText,
        },
      };
    } catch (error) {
      logger.error({
        message: 'HTTP health check failed',
        url,
        error: error instanceof Error ? error.message : String(error),
      });
      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'HTTP request failed',
        latency: Date.now() - start,
      };
    }
  };
}

/**
 * Creates a composite health check from multiple checks
 */
export function createCompositeHealthCheck(
  checks: Record<string, () => Promise<ComponentHealth>>,
  config: HealthCheckConfig = {},
): () => Promise<HealthCheckResult> {
  const { includeDetails } = { ...DEFAULT_CONFIG, ...config };

  return async () => {
    const start = Date.now();
    const results: Record<string, ComponentHealth> = {};

    // Run all checks in parallel
    await Promise.all(
      Object.entries(checks).map(async ([name, check]) => {
        try {
          results[name] = await check();
        } catch (error) {
          logger.error({
            message: 'Health check failed',
            component: name,
            error: error instanceof Error ? error.message : String(error),
          });
          results[name] = {
            status: 'unhealthy',
            message: error instanceof Error ? error.message : 'Check failed',
          };
        }
      })
    );

    // Determine overall health
    const statuses = Object.values(results).map(r => r.status);
    const healthy =
      statuses.every(s => s === 'healthy') ||
      (statuses.some(s => s === 'healthy') && statuses.every(s => s !== 'unhealthy'));

    return {
      healthy,
      timestamp: new Date().toISOString(),
      checks: includeDetails ? results : {},
      duration: Date.now() - start,
    };
  };
}

/**
 * Creates a service health check with common dependencies
 */
export function createServiceHealthCheck(
  serviceName: string,
  dependencies: {
    prisma?: any;
    redis?: any;
    externalApis?: Record<string, string>;
  },
  config: HealthCheckConfig = {},
): () => Promise<HealthCheckResult> {
  const checks: Record<string, () => Promise<ComponentHealth>> = {};

  // Add database check if provided
  if (dependencies.prisma) {
    checks.database = createDatabaseHealthCheck(dependencies.prisma, config);
  }

  // Add Redis check if provided
  if (dependencies.redis) {
    checks.redis = createRedisHealthCheck(dependencies.redis, config);
  }

  // Add external API checks if provided
  if (dependencies.externalApis) {
    Object.entries(dependencies.externalApis).forEach(([name, url]) => {
      checks[`api_${name}`] = createHttpHealthCheck(url, config);
    });
  }

  return createCompositeHealthCheck(checks, config);
}

/**
 * Creates a simple health check that always returns healthy
 * Useful for services with no external dependencies
 */
export function createSimpleHealthCheck(): () => Promise<ComponentHealth> {
  return async () => ({
    status: 'healthy',
    message: 'Service operational',
    latency: 0,
  });
}

/**
 * Creates a custom health check from a function
 */
export function createCustomHealthCheck(
  checkFn: () => Promise<boolean>,
  healthyMessage = 'Check passed',
  unhealthyMessage = 'Check failed',
): () => Promise<ComponentHealth> {
  return async () => {
    const start = Date.now();
    try {
      const result = await checkFn();
      return {
        status: result ? 'healthy' : 'unhealthy',
        message: result ? healthyMessage : unhealthyMessage,
        latency: Date.now() - start,
      };
    } catch (error) {
      logger.error({
        message: 'Custom health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : unhealthyMessage,
        latency: Date.now() - start,
      };
    }
  };
}

/**
 * Creates a health check with circuit breaker pattern
 */
export function createCircuitBreakerHealthCheck(
  check: () => Promise<ComponentHealth>,
  options: {
    failureThreshold?: number;
    resetTimeout?: number;
  } = {},
): () => Promise<ComponentHealth> {
  const { failureThreshold = 5, resetTimeout = 60000 } = options;

  let failures = 0;
  let lastFailureTime = 0;
  let circuitOpen = false;

  return async () => {
    // Check if circuit should be reset
    if (circuitOpen && Date.now() - lastFailureTime > resetTimeout) {
      circuitOpen = false;
      failures = 0;
      logger.info({ message: 'Circuit breaker reset' });
    }

    // If circuit is open, return unhealthy immediately
    if (circuitOpen) {
      return {
        status: 'unhealthy',
        message: 'Circuit breaker open',
        details: {
          failures,
          lastFailureTime: new Date(lastFailureTime).toISOString(),
        },
      };
    }

    // Execute health check
    try {
      const result = await check();

      if (result.status === 'unhealthy') {
        failures++;
        lastFailureTime = Date.now();

        if (failures >= failureThreshold) {
          circuitOpen = true;
          logger.warn({
            message: 'Circuit breaker opened',
            failures,
            threshold: failureThreshold,
          });
        }
      } else {
        // Reset on success
        failures = 0;
      }

      return result;
    } catch (error) {
      failures++;
      lastFailureTime = Date.now();

      if (failures >= failureThreshold) {
        circuitOpen = true;
      }

      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'Health check failed',
      };
    }
  };
}

/**
 * Helper to format health check results for HTTP responses
 */
export function formatHealthCheckResponse(
  result: HealthCheckResult,
  serviceName: string,
  version: string,
): {
  status: number;
  body: {
    service: string;
    version: string;
    status: 'healthy' | 'unhealthy' | 'degraded';
    timestamp: string;
    duration: number;
    checks?: Record<string, ComponentHealth>;
  };
} {
  const overallStatus = result.healthy
    ? 'healthy'
    : Object.values(result.checks).some(c => c.status === 'degraded')
    ? 'degraded'
    : 'unhealthy';

  return {
    status: result.healthy ? 200 : 503,
    body: {
      service: serviceName,
      version,
      status: overallStatus,
      timestamp: result.timestamp,
      duration: result.duration,
      checks: result.checks,
    },
  };
}
