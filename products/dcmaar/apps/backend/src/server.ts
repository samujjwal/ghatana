/**
 * Fastify HTTP server setup and middleware configuration.
 *
 * <p><b>Purpose</b><br>
 * Bootstraps Fastify HTTP server with security middleware (helmet, CORS),
 * logging, metrics, WebSocket support, and API route registration.
 * Provides centralized server configuration and lifecycle management.
 *
 * <p><b>Middleware Stack</b><br>
 * - Helmet: Security headers (CSP, X-Frame-Options, etc.)
 * - CORS: Cross-origin request handling with configurable origins
 * - Compression: gzip/brotli response compression
 * - Cookie Parser: Secure cookie parsing with JWT secret
 * - Rate Limiting: Request throttling (default: 100/60s)
 * - Sensible: Common Fastify utilities
 * - Socket.io: WebSocket support
 *
 * <p><b>API Routes Registered</b><br>
 * - /api/auth: Authentication endpoints (login, register, refresh)
 * - /api/policies: Policy CRUD operations
 * - /api/devices: Device registration and management
 * - /api/children: Child profile management
 * - /api/reports: Report generation and export
 * - /api/usage: Usage data collection
 * - /api/blocks: Block event tracking
 * - /api/heartbeat: Device health monitoring
 * - /api/recommendations: AI policy recommendations
 * - /api/analytics: Usage analytics and aggregation
 * - /api/risk: Risk scoring and insights
 * - /api/notifications: Parent notification management
 *
 * <p><b>Built-in Endpoints</b><br>
 * - GET /health: Health check (status, uptime, version)
 * - GET /metrics: Prometheus metrics export
 *
 * <p><b>Error Handling</b><br>
 * - 404 handler: Logs and returns not found responses
 * - Global error handler: Catches unhandled errors, logs with context
 * - Production mode: Suppresses error details to prevent leakage
 *
 * <p><b>Metrics & Logging</b><br>
 * - Structured JSON logging with Winston
 * - Prometheus metrics for all HTTP requests (latency, status codes)
 * - Sentry error tracking integration
 * - Request correlation ID for tracing
 *
 * <p><b>WebSocket Integration</b><br>
 * - Socket.io server attached to HTTP server
 * - CORS configuration inherited from HTTP CORS
 * - Event handlers for device updates, policy changes, alerts
 * - Room-based message broadcasting
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { buildApp, startServer } from './server';
 * 
 * // Build app
 * const app = await buildApp();
 * 
 * // Start listening
 * await startServer(app);
 * 
 * // Graceful shutdown
 * process.on('SIGTERM', async () => {
 *   await app.close();
 *   process.exit(0);
 * });
 * }</pre>
 *
 * <p><b>Configuration</b><br>
 * - PORT: Server port (default: 3001, env: PORT)
 * - HOST: Bind address (default: 0.0.0.0, env: HOST)
 * - NODE_ENV: Environment mode (development/production/test)
 * - CORS_ORIGIN: Allowed origins (default: http://localhost:3000)
 * - RATE_LIMIT_MAX_REQUESTS: Max requests per window (default: 100)
 * - RATE_LIMIT_WINDOW_MS: Rate limit window in ms (default: 60000)
 *
 * @doc.type utility
 * @doc.purpose Fastify HTTP server setup and middleware configuration
 * @doc.layer backend
 * @doc.pattern Server/Application Bootstrap
 */
import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import compress from '@fastify/compress';
import cookie from '@fastify/cookie';
import rateLimit from '@fastify/rate-limit';
import sensible from '@fastify/sensible';
import fastifySocketIO from 'fastify-socket.io';
import dotenv from 'dotenv';

// Load environment variables first
dotenv.config();

// Initialize observability BEFORE importing routes
import { logger, logHttp } from './utils/logger';
import { validateEnvironment } from './config/environment';
import { initSentry } from './utils/sentry';
import * as metrics from './utils/metrics';
import { pool, closePool } from './db';

// Validate environment configuration
validateEnvironment();

// Initialize Sentry error monitoring
initSentry();

// Import routes after environment is validated
import authRoutes from './routes/auth.routes';
import deviceAuthRoutes from './routes/device-auth.routes';
import policyRoutes from './routes/policy.routes';
import deviceRoutes from './routes/device.routes';
import childrenRoutes from './routes/children.routes';
import reportsRoutes from './routes/reports.routes';
import usageRoutes from './routes/usage.routes';
import blockRoutes from './routes/block.routes';
import heartbeatRoutes from './routes/heartbeat.routes';
import recommendationRoutes from './routes/recommendations.routes';
import analyticsRoutes from './routes/analytics.routes';
import eventsRoutes from './routes/events.routes';
import { riskRoutes } from './routes/risk.routes';
import { notificationRoutes } from './routes/notification.routes';

const PORT = parseInt(process.env.PORT || '3001');
const HOST = process.env.HOST || '0.0.0.0';

/**
 * Build Fastify application
 */
export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({
    logger: false, // Use Winston logger instead
    requestTimeout: 30000,
    bodyLimit: 10 * 1024 * 1024, // 10mb
    trustProxy: true,
  });

  // Register plugins (register must be awaited before using their methods)

  // Sensible utilities first (adds .httpErrors and more)
  // @ts-ignore - Fastify 5 plugin type compatibility
  await app.register(sensible);

  // Security: Helmet
  // @ts-ignore - Fastify 5 plugin type compatibility
  void app.register(helmet, {
    contentSecurityPolicy: false, // Disable for API
  });

  // CORS
  // @ts-ignore - Fastify 5 plugin type compatibility
  void app.register(cors, {
    origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000'],
    credentials: true,
  });

  // Compression
  // @ts-ignore - Fastify 5 plugin type compatibility
  void app.register(compress, {
    global: true,
  });

  // Cookie parsing
  // @ts-ignore - Fastify 5 plugin type compatibility
  void app.register(cookie, {
    secret: process.env.JWT_SECRET,
  });

  // Rate limiting (disabled in test environment to allow rapid sequential requests)
  if (process.env.NODE_ENV !== 'test') {
    // @ts-ignore - Fastify 5 plugin type compatibility
    void app.register(rateLimit, {
      max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '100'),
      timeWindow: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000'),
    });
  }

  // Socket.io integration
  // @ts-ignore - Fastify 5 plugin type compatibility
  void app.register(fastifySocketIO, {
    cors: {
      origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000'],
      credentials: true,
    },
  });

  // Enhanced request logging with metrics
  app.addHook('onRequest', async (request, _reply) => {
    request.startTime = Date.now();
  });

  app.addHook('onResponse', async (request, reply) => {
    const duration = Date.now() - (request.startTime || Date.now());

    // Log HTTP request
    logHttp(
      { method: request.method, path: request.url } as any,
      { statusCode: reply.statusCode } as any,
      duration
    );

    // Record metrics
    const route = request.routeOptions?.url || request.url;
    metrics.httpRequestDuration.observe(
      { method: request.method, route, status_code: reply.statusCode.toString() },
      duration
    );
    metrics.httpRequestTotal.inc({
      method: request.method,
      route,
      status_code: reply.statusCode.toString(),
    });
  });

  // Health check endpoint
  app.get('/health', async (_request, _reply) => {
    return {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      environment: process.env.NODE_ENV || 'development',
      version: process.env.npm_package_version || '1.0.0',
    };
  });

  // Prometheus metrics endpoint
  app.get('/metrics', async (request, reply) => {
    reply.type(metrics.register.contentType);
    return await metrics.register.metrics();
  });

  // Register API routes
  await app.register(authRoutes, { prefix: '/api/auth' });
  await app.register(deviceAuthRoutes, { prefix: '/api/auth' });
  await app.register(policyRoutes, { prefix: '/api/policies' });
  await app.register(deviceRoutes, { prefix: '/api/devices' });
  await app.register(childrenRoutes, { prefix: '/api/children' });
  await app.register(reportsRoutes, { prefix: '/api/reports' });
  await app.register(usageRoutes, { prefix: '/api/usage' });
  await app.register(blockRoutes, { prefix: '/api/blocks' });
  await app.register(heartbeatRoutes, { prefix: '/api/heartbeat' });
  await app.register(recommendationRoutes, { prefix: '/api/recommendations' });
  await app.register(analyticsRoutes, { prefix: '/api/analytics' });
  await app.register(eventsRoutes, { prefix: '/api/events' });
  await app.register(riskRoutes, { prefix: '/api/risk' });
  await app.register(notificationRoutes, { prefix: '/api/notifications' });

  // Setup WebSocket handlers
  app.ready().then(() => {
    logger.info('WebSocket initialized');

    app.io.on('connection', (socket: import('socket.io').Socket) => {
      logger.info('Client connected', { socketId: socket.id });

      socket.on('subscribe:devices', (userId: string) => {
        socket.join(`user:${userId}`);
        logger.info('User subscribed to devices', { userId, socketId: socket.id });
      });

      socket.on('subscribe:policies', (userId: string) => {
        socket.join(`user:${userId}:policies`);
        logger.info('User subscribed to policies', { userId, socketId: socket.id });
      });

      socket.on('disconnect', () => {
        logger.info('Client disconnected', { socketId: socket.id });
      });
    });
  });

  // 404 handler
  app.setNotFoundHandler((request, reply) => {
    logger.warn('404 Not Found', { path: request.url, method: request.method });
    return reply.code(404).send({ error: 'Not found' });
  });

  // Global error handler
  app.setErrorHandler((error, request, reply) => {
    const err = error as Error & { statusCode?: number };
    logger.error('Unhandled error', {
      error: err.message,
      stack: err.stack,
      method: request.method,
      url: request.url,
    });

    // Don't leak error details in production
    const isProduction = process.env.NODE_ENV === 'production';

    return reply.code(err.statusCode || 500).send({
      error: isProduction ? 'Internal server error' : err.message,
      ...(isProduction ? {} : { stack: err.stack }),
    });
  });

  return app;
}

/**
 * Start server
 */
async function startServer() {
  try {
    // Build Fastify app
    const app = await buildApp();

    // Test database connection
    await pool.query('SELECT NOW()');
    logger.info('Database connection successful');

    // Graceful shutdown
    const shutdown = async (signal: string) => {
      logger.info(`${signal} signal received - shutting down gracefully`);

      await app.close();
      await closePool();
      process.exit(0);
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));

    // Start listening
    await app.listen({ port: PORT, host: HOST });

    logger.info('Guardian Backend API started', {
      port: PORT,
      host: HOST,
      environment: process.env.NODE_ENV || 'development',
      version: process.env.npm_package_version || '1.0.0',
      healthCheck: `http://localhost:${PORT}/health`,
      metrics: `http://localhost:${PORT}/metrics`,
      websocket: `ws://localhost:${PORT}`,
    });
  } catch (error) {
    logger.error('Failed to start server', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    process.exit(1);
  }
}

// Start server if this is the main module
if (require.main === module) {
  startServer();
}

// Export for testing
export { buildApp as default };

// Extend FastifyRequest to include startTime
declare module 'fastify' {
  interface FastifyRequest {
    startTime?: number;
  }
  interface FastifyInstance {
    io: import('socket.io').Server;
  }
}
