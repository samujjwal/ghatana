/**
 * Test App Helper
 *
 * Creates Fastify app instance for testing without starting server
 */

import Fastify, { FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import cookie from '@fastify/cookie';

// Import routes
import authRoutes from '../../routes/auth.routes';
import policyRoutes from '../../routes/policy.routes';
import deviceRoutes from '../../routes/device.routes';
import childrenRoutes from '../../routes/children.routes';
import reportsRoutes from '../../routes/reports.routes';
import usageRoutes from '../../routes/usage.routes';
import blockRoutes from '../../routes/block.routes';
import heartbeatRoutes from '../../routes/heartbeat.routes';
import recommendationRoutes from '../../routes/recommendations.routes';
import analyticsRoutes from '../../routes/analytics.routes';

/**
 * Create Fastify app for testing
 */
export async function createTestApp(): Promise<FastifyInstance> {
  const app = Fastify({
    logger: false,
  });

  // CORS
  await app.register(cors, {
    origin: 'http://localhost:3000',
    credentials: true,
  });

  // Cookie parser
  await app.register(cookie);

  // Mount routes
  await app.register(authRoutes, { prefix: '/api/auth' });
  await app.register(policyRoutes, { prefix: '/api/policies' });
  await app.register(deviceRoutes, { prefix: '/api/devices' });
  await app.register(childrenRoutes, { prefix: '/api/children' });
  await app.register(reportsRoutes, { prefix: '/api/reports' });
  await app.register(usageRoutes, { prefix: '/api/usage' });
  await app.register(blockRoutes, { prefix: '/api/blocks' });
  await app.register(heartbeatRoutes, { prefix: '/api/heartbeat' });
  await app.register(recommendationRoutes, { prefix: '/api/recommendations' });
  await app.register(analyticsRoutes, { prefix: '/api/analytics' });

  await app.ready();

  return app;
}
