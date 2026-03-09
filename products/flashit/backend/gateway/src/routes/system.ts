/**
 * System Health & Monitoring Routes
 * 
 * Provides endpoints for circuit breaker status and system health checks
 * 
 * @doc.type routes
 * @doc.purpose System monitoring and health checks
 * @doc.layer infrastructure
 * @doc.pattern Routes
 */

import { FastifyInstance } from 'fastify';
import { getAllCircuitBreakerStats } from '../lib/circuit-breaker';

export async function registerSystemRoutes(app: FastifyInstance) {
  /**
   * GET /api/system/circuit-breakers
   * Get status of all circuit breakers
   */
  app.get('/api/system/circuit-breakers', async () => {
    return {
      circuitBreakers: getAllCircuitBreakerStats(),
      timestamp: new Date().toISOString(),
    };
  });

  /**
   * GET /api/system/health/detailed
   * Detailed health check including circuit breaker states
   */
  app.get('/api/system/health/detailed', async () => {
    const circuitBreakers = getAllCircuitBreakerStats();
    
    // Check if any circuit breakers are open
    const openCircuits = Object.entries(circuitBreakers).filter(
      ([, stats]) => stats.state === 'OPEN'
    );

    const degraded = openCircuits.length > 0;

    return {
      status: degraded ? 'degraded' : 'healthy',
      service: 'flashit-web-api',
      timestamp: new Date().toISOString(),
      circuitBreakers,
      issues: openCircuits.map(([name, stats]) => ({
        service: name,
        state: stats.state,
        failures: stats.failures,
        nextAttempt: stats.nextAttemptTime 
          ? new Date(stats.nextAttemptTime).toISOString()
          : null,
      })),
    };
  });
}
