/**
 * Health Check API Routes
 * Week 14 Day 66-67 - Reliability sweep with performance monitoring
 * 
 * @doc.type route
 * @doc.purpose Health checks, service status, and performance metrics
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { performHealthChecks, circuitBreakers } from '../lib/resilience.js';
import {
  performanceMonitor,
  getResourceUsage,
  getDatabaseStats,
  checkMemoryLeak,
} from '../lib/performance.js';
import { prisma } from '../lib/prisma.js';
import { requireAuth, type JwtPayload } from '../lib/auth.js';
import { requireMinRole } from '../middleware/require-role.js';

export default async function healthRoutes(fastify: FastifyInstance) {
  /**
   * Basic health check
   * GET /api/health
   */
  fastify.get('/health', async () => {
    return { status: 'ok', timestamp: new Date().toISOString() };
  });

  /**
   * Comprehensive health check with dependencies
   * GET /api/health/detailed
   */
  fastify.get('/health/detailed', async (request, reply) => {
    const health = await performHealthChecks();

    const statusCode = health.status === 'healthy' ? 200 : health.status === 'degraded' ? 207 : 503;

    return reply.status(statusCode).send(health);
  });

  /**
   * Readiness check (is service ready to accept traffic?)
   * GET /api/health/ready
   */
  fastify.get('/health/ready', async (request, reply) => {
    try {
      // Check database connection
      await prisma.$queryRaw`SELECT 1`;

      // Check if any circuit breakers are OPEN
      const openCircuits = Object.entries(circuitBreakers)
        .filter(([, breaker]) => breaker.getState().state === 'OPEN')
        .map(([name]) => name);

      if (openCircuits.length > 0) {
        return reply.status(503).send({
          ready: false,
          reason: `Circuit breakers open: ${openCircuits.join(', ')}`,
        });
      }

      return { ready: true };
    } catch (error) {
      return reply.status(503).send({
        ready: false,
        reason: (error as Error).message,
      });
    }
  });

  /**
   * Liveness check (is service alive?)
   * GET /api/health/live
   */
  fastify.get('/health/live', async (request, reply) => {
    // Simple check - if we can respond, we're alive
    return { alive: true };
  });

  /**
   * Circuit breaker status
   * GET /api/health/circuits
   */
  fastify.get('/health/circuits', async (request, reply) => {
    const circuits = Object.entries(circuitBreakers).map(([name, breaker]) => ({
      name,
      ...breaker.getState(),
    }));

    return { circuits, timestamp: new Date().toISOString() };
  });

  /**
   * Reset a circuit breaker (requires OPERATOR or above)
   * POST /api/health/circuits/:name/reset
   */
  fastify.post('/circuits/:name/reset', {
    preHandler: [requireAuth, requireMinRole('OPERATOR')],
  }, async (request, reply) => {
    const { name } = request.params as { name: string };

    const breaker = circuitBreakers[name as keyof typeof circuitBreakers];
    if (!breaker) {
      return reply.status(404).send({ error: 'Circuit breaker not found' });
    }

    breaker.reset();

    return {
      message: `Circuit breaker [${name}] reset successfully`,
      state: breaker.getState(),
    };
  });

  /**
   * Get performance metrics summary
   * GET /api/health/performance
   */
  fastify.get('/performance', async (request, reply) => {
    const { operation } = request.query as { operation?: string };

    if (operation) {
      return {
        operation,
        metrics: performanceMonitor.getSummary(operation),
        timestamp: new Date().toISOString(),
      };
    }

    return {
      operations: performanceMonitor.getAllSummaries(),
      slowOperations: performanceMonitor.getSlowOperations(1000),
      timestamp: new Date().toISOString(),
    };
  });

  /**
   * Get resource usage (memory, CPU)
   * GET /api/health/resources
   */
  fastify.get('/resources', async (request, reply) => {
    const usage = getResourceUsage();
    const memoryCheck = checkMemoryLeak();

    return {
      ...usage,
      memoryLeak: memoryCheck,
      timestamp: new Date().toISOString(),
    };
  });

  /**
   * Get database statistics
   * GET /api/health/database
   */
  fastify.get('/database', async (request, reply) => {
    const stats = await getDatabaseStats();

    if (!stats) {
      return reply.status(500).send({
        error: 'Failed to retrieve database stats',
      });
    }

    return {
      ...stats,
      timestamp: new Date().toISOString(),
    };
  });
}
