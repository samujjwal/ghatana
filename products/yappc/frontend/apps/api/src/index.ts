import 'dotenv/config';

import { createYoga, createSchema } from 'graphql-yoga';
import fastify from 'fastify';
import { randomUUID } from 'node:crypto';
import cors from '@fastify/cors';
import fastifyWebsocket from '@fastify/websocket';
import { apiRateLimitMiddleware, aiRateLimitMiddleware } from './middleware/RateLimitMiddleware.js';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import type { FastifyInstance } from 'fastify';

// Import Observability
import {
  startTracing,
  shutdownTracing,
  instrumentFastify,
} from './utils/tracing';
import {
  register,
  httpRequestDuration,
  httpRequestTotal,
} from './utils/metrics';

// Import Resolvers
import { createSimpleResolvers } from './graphql/resolvers';

// Import REST Routes
import workspaceRoutes from './routes/workspaces';
import projectRoutes from './routes/projects';
import devsecopsRoutes from './routes/devsecops';
import canvasRoutes from './routes/canvas';
import lifecycleRoutes from './routes/lifecycle';
import lifecycleExecutionRoutes from './routes/lifecycle-execution';
import telemetryRoutes from './routes/telemetry';
import aiRoutes from './routes/ai';
import planningRoutes from './routes/planning';
import { devAuthBypass } from './middleware/devAuth';
import {
  assertDevAuthBypassAllowed,
  isDevAuthBypassEnabled,
} from './middleware/dev-auth-config';
import { authMiddleware } from './middleware/auth.middleware';
import { auditMiddleware } from './middleware/audit.middleware';
import { authRoutes } from './routes/auth';
import { securityScanRoutes } from './routes/security-scans';
import {
  applyVersionHeaders,
  buildVersionErrorBody,
  isSupportedVersion,
  isVersionedApiPath,
  resolveRequestedVersion,
} from './middleware/apiVersioning';

// Import WebSocket Service
import { RealTimeService } from './services/RealTimeService';

// Initialize OpenTelemetry tracing
startTracing();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectRoot = process.cwd();

const mainSchemaPathCandidates = [
  path.join(__dirname, 'graphql/schema.graphql'),
  path.join(projectRoot, 'src/graphql/schema.graphql'),
];

const mainSchemaPath = mainSchemaPathCandidates.find((p) => fs.existsSync(p));
if (!mainSchemaPath) {
  throw new Error(
    `GraphQL schema not found. Tried: ${mainSchemaPathCandidates.join(', ')}`
  );
}

let typeDefs = fs.readFileSync(mainSchemaPath, 'utf8');

// Load additional schemas
const schemasDirCandidates = [
  path.join(__dirname, 'graphql/schemas'),
  path.join(projectRoot, 'src/graphql/schemas'),
];

const schemasDir = schemasDirCandidates.find((p) => fs.existsSync(p));
if (schemasDir) {
  const schemaFiles = fs
    .readdirSync(schemasDir)
    .filter((file) => file.endsWith('.graphql'));
  for (const file of schemaFiles) {
    const filePath = path.join(schemasDir, file);
    typeDefs += '\n' + fs.readFileSync(filePath, 'utf8');
  }
}

// Merge Resolvers (Simple merge for now)
const resolvers = createSimpleResolvers();

const schema = createSchema({
  typeDefs,
  resolvers,
});

export interface CreateAppOptions {
  jwtSecret?: string;
}

/**
 * Canonical API prefix configuration
 * - `/api/v1` is the canonical prefix
 * - `/api` and `/v1` are deprecated and will be removed
 */
function registerApiPrefixes(
  app: FastifyInstance,
  route: Parameters<FastifyInstance['register']>[0]
) {
  // Canonical prefix: /api/v1
  app.register(route, { prefix: '/api/v1' });

  // Deprecated: /api - redirect to canonical with deprecation header
  app.register(async (instance, opts) => {
    instance.addHook('onSend', async (request, reply, payload) => {
      reply.header('Deprecation', 'true');
      reply.header('Sunset', new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()); // 90 days
      reply.header('Link', '</api/v1>; rel="canonical"');
      return payload;
    });
    instance.register(route, opts);
  }, { prefix: '/api' });

  // Deprecated: /v1 - redirect to canonical with deprecation header
  app.register(async (instance, opts) => {
    instance.addHook('onSend', async (request, reply, payload) => {
      reply.header('Deprecation', 'true');
      reply.header('Sunset', new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()); // 90 days
      reply.header('Link', '</api/v1>; rel="canonical"');
      return payload;
    });
    instance.register(route, opts);
  }, { prefix: '/v1' });
}

export async function createApp(
  options: CreateAppOptions = {}
): Promise<FastifyInstance> {
  // Validate required environment variables before initialization
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL environment variable is required but not set. Aborting startup.');
  }

  if (!process.env.JWT_ACCESS_SECRET) {
    throw new Error('JWT_ACCESS_SECRET environment variable is required but not set. Aborting startup.');
  }

  // In production, require secure configuration - no defaults
  const isProduction = process.env.NODE_ENV === 'production';
  if (isProduction) {
    if (!process.env.COOKIE_SECRET) {
      throw new Error('COOKIE_SECRET environment variable is required in production. Aborting startup.');
    }

    if (!process.env.JWT_REFRESH_SECRET) {
      throw new Error('JWT_REFRESH_SECRET environment variable is required in production. Aborting startup.');
    }

    if (!process.env.JAVA_BACKEND_API_KEY) {
      throw new Error('JAVA_BACKEND_API_KEY environment variable is required in production. Aborting startup.');
    }

    if (process.env.COOKIE_SECRET === 'change-me-in-production') {
      throw new Error('Default COOKIE_SECRET detected in production. Set a secure secret. Aborting startup.');
    }
  }

  if (options.jwtSecret) {
    process.env.JWT_ACCESS_SECRET = options.jwtSecret;
  }

  const app = fastify({ logger: true });

  instrumentFastify(app);

  // GraphQL configuration - GraphiQL only in non-production
  const enableGraphiql = process.env.NODE_ENV !== 'production';

  const yoga = createYoga({
    schema,
    logging: {
      debug: (...args) => args.forEach((arg) => app.log.debug(arg)),
      info: (...args) => args.forEach((arg) => app.log.info(arg)),
      warn: (...args) => args.forEach((arg) => app.log.warn(arg)),
      error: (...args) => args.forEach((arg) => app.log.error(arg)),
    },
    graphiql: enableGraphiql,
    context: ({ request }) => {
      // Require authentication for GraphQL in production
      if (process.env.NODE_ENV === 'production' && !request.headers.authorization) {
        throw new Error('Authentication required');
      }
      return {
        userId: (request as unknown as { user?: { userId: string } }).user?.userId,
        role: (request as unknown as { user?: { role: string } }).user?.role,
      };
    },
  });

  // @ts-ignore - CORS plugin type issue
  const allowedOrigins = (process.env.ALLOWED_ORIGINS ?? 'http://localhost:5173')
    .split(',')
    .map((o) => o.trim());

  app.register(cors, {
    origin: (origin, cb) => {
      if (!origin || allowedOrigins.includes(origin)) {
        cb(null, true);
      } else {
        cb(new Error('Not allowed by CORS'), false);
      }
    },
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    credentials: true,
  });

  await app.register(fastifyWebsocket);

  // Register rate limit middleware
  app.addHook('onRequest', (request, reply, done) => {
    // Skip rate limiting for health and metrics endpoints
    if (request.url === '/health' || request.url === '/metrics') {
      done();
      return;
    }

    // Apply AI-specific rate limiting to AI endpoints
    if (request.url.startsWith('/api/v1/ai') || request.url.startsWith('/api/v1/copilot')) {
      aiRateLimitMiddleware(request as any, reply as any, done);
    } else {
      apiRateLimitMiddleware(request as any, reply as any, done);
    }
  });

  app.addHook('onRequest', async (request) => {
    const headerValue = request.headers['x-correlation-id'];
    const correlationId =
      typeof headerValue === 'string' && headerValue.trim().length > 0
        ? headerValue
        : null;

    // In production, X-Correlation-ID is mandatory
    if (process.env.NODE_ENV === 'production' && !correlationId) {
      throw new Error('X-Correlation-ID header is required in production');
    }

    // Generate correlation ID if not provided (development only)
    const finalCorrelationId = correlationId || randomUUID();

    request.correlationId = finalCorrelationId;
    request.log = request.log.child({ correlationId: finalCorrelationId });
    request.headers['x-correlation-id'] = finalCorrelationId;

    request.startTime = Date.now();
  });

  app.addHook('preHandler', async (request, reply) => {
    if (!isVersionedApiPath(request.url)) {
      return;
    }

    const requestedVersion = resolveRequestedVersion(request);
    if (requestedVersion && !isSupportedVersion(requestedVersion)) {
      return reply.code(406).send(buildVersionErrorBody(requestedVersion));
    }
  });

  app.addHook('onSend', async (request, reply, payload) => {
    if (isVersionedApiPath(request.url)) {
      applyVersionHeaders(reply);
    }
    return payload;
  });

  app.addHook('onResponse', async (request, reply) => {
    reply.header('x-correlation-id', request.correlationId);

    const duration = (Date.now() - request.startTime!) / 1000;
    const route = (request as unknown as { routerPath?: string }).routerPath || request.url;
    const method = request.method;
    const statusCode = reply.statusCode.toString();

    httpRequestDuration.labels(method, route, statusCode).observe(duration);
    httpRequestTotal.labels(method, route, statusCode).inc();
  });

  /**
   * Health check endpoint
   * Returns current status but remains available for load balancers
   * even when degraded. Returns 503 only if database is down.
   */
  app.get('/health', async (request, reply) => {
    const checks: Record<string, 'ok' | 'degraded' | 'down'> = {};

    // Check database connectivity
    try {
      const prisma = await import('./database/client.js').then(m => m.getPrismaClient());
      await prisma.$queryRaw`SELECT 1`;
      checks.database = 'ok';
    } catch {
      checks.database = 'down';
    }

    // Check Java backend reachability
    const javaBackendUrl = process.env.JAVA_BACKEND_URL ?? 'http://localhost:7003';
    try {
      const res = await fetch(`${javaBackendUrl}/health`, { signal: AbortSignal.timeout(2000) });
      checks.javaBackend = res.ok ? 'ok' : 'degraded';
    } catch {
      checks.javaBackend = 'down';
    }

    const overallStatus = Object.values(checks).every((v) => v === 'ok') ? 'healthy' : 'degraded';
    const httpStatus = checks.database === 'down' ? 503 : 200;

    return reply.status(httpStatus).send({
      status: overallStatus,
      checks,
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      environment: process.env.NODE_ENV ?? 'development',
    });
  });

  /**
   * Liveness probe - always returns 200 if the process is running
   */
  app.get('/live', async (_request, reply) => {
    return reply.status(200).send({
      status: 'alive',
      timestamp: new Date().toISOString(),
    });
  });

  /**
   * Readiness probe - strict semantics for deployment decisions
   * Returns non-200 if any critical dependency is unavailable.
   * Used by orchestrators to determine if the service should receive traffic.
   */
  app.get('/ready', async (request, reply) => {
    const checks: Record<string, { status: 'ok' | 'down'; message?: string }> = {};
    let allReady = true;

    // Check database connectivity (critical)
    try {
      const prisma = await import('./database/client.js').then(m => m.getPrismaClient());
      await prisma.$queryRaw`SELECT 1`;
      checks.database = { status: 'ok' };
    } catch (error) {
      checks.database = {
        status: 'down',
        message: error instanceof Error ? error.message : 'Database connection failed',
      };
      allReady = false;
    }

    // Check Java backend (critical dependency)
    const javaBackendUrl = process.env.JAVA_BACKEND_URL ?? 'http://localhost:7003';
    try {
      const res = await fetch(`${javaBackendUrl}/health`, { signal: AbortSignal.timeout(2000) });
      if (res.ok) {
        checks.javaBackend = { status: 'ok' };
      } else {
        checks.javaBackend = {
          status: 'down',
          message: `Java backend returned ${res.status}`,
        };
        allReady = false;
      }
    } catch (error) {
      checks.javaBackend = {
        status: 'down',
        message: error instanceof Error ? error.message : 'Java backend unreachable',
      };
      allReady = false;
    }

    const httpStatus = allReady ? 200 : 503;
    const status = allReady ? 'ready' : 'not_ready';

    return reply.status(httpStatus).send({
      status,
      checks,
      timestamp: new Date().toISOString(),
    });
  });

  /**
   * Secure /metrics endpoint
   * - In production: requires authentication or internal network
   * - In development: accessible without auth
   */
  app.get('/metrics', async (request, reply) => {
    const isProduction = process.env.NODE_ENV === 'production';

    if (isProduction) {
      // Check if request has valid authentication
      const hasAuth = request.user && request.user.userId;

      // Check if request is from internal/private network
      const clientIp = request.ip;
      const isInternalNetwork =
        clientIp === '127.0.0.1' ||
        clientIp === '::1' ||
        clientIp?.startsWith('10.') ||
        clientIp?.startsWith('172.16.') ||
        clientIp?.startsWith('172.17.') ||
        clientIp?.startsWith('172.18.') ||
        clientIp?.startsWith('172.19.') ||
        clientIp?.startsWith('172.20.') ||
        clientIp?.startsWith('172.21.') ||
        clientIp?.startsWith('172.22.') ||
        clientIp?.startsWith('172.23.') ||
        clientIp?.startsWith('172.24.') ||
        clientIp?.startsWith('172.25.') ||
        clientIp?.startsWith('172.26.') ||
        clientIp?.startsWith('172.27.') ||
        clientIp?.startsWith('172.28.') ||
        clientIp?.startsWith('172.29.') ||
        clientIp?.startsWith('172.30.') ||
        clientIp?.startsWith('172.31.') ||
        clientIp?.startsWith('192.168.');

      // Check for metrics API key header
      const metricsApiKey = request.headers['x-metrics-api-key'];
      const validMetricsKey = process.env.METRICS_API_KEY;
      const hasValidApiKey =
        validMetricsKey &&
        metricsApiKey === validMetricsKey;

      if (!hasAuth && !isInternalNetwork && !hasValidApiKey) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Metrics endpoint requires authentication, internal network access, or valid API key',
        });
      }
    }

    reply.type(register.contentType);
    return register.metrics();
  });

  const realTimeService = new RealTimeService();
  realTimeService.registerRoutes(app);

  await authMiddleware(app);
  await auditMiddleware(app);

  assertDevAuthBypassAllowed();
  if (isDevAuthBypassEnabled()) {
    await devAuthBypass(app);
    app.log.warn('Development auth bypass is enabled. Do not use in production.');
  }

  registerApiPrefixes(app, authRoutes);
  registerApiPrefixes(app, workspaceRoutes);
  registerApiPrefixes(app, projectRoutes);
  registerApiPrefixes(app, devsecopsRoutes);
  registerApiPrefixes(app, securityScanRoutes);
  registerApiPrefixes(app, canvasRoutes);
  registerApiPrefixes(app, lifecycleRoutes);
  registerApiPrefixes(app, lifecycleExecutionRoutes);
  registerApiPrefixes(app, telemetryRoutes);
  registerApiPrefixes(app, aiRoutes);
  registerApiPrefixes(app, planningRoutes);

async function readResponseText(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to read upstream response body: ${detail}`);
  }
}

// Catch-all route for Java backend proxying
// Handles /api/rail, /api/agents, and other Java backend routes
  app.all<{ Params: { '*': string } }>('/api/*', async (request, reply) => {
    const javaBackendUrl =
      process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
    const targetUrl = new URL(javaBackendUrl + request.url);

    try {
      const response = await fetch(targetUrl.toString(), {
        method: request.method as unknown,
        headers: {
          ...request.headers,
          host: targetUrl.hostname,
          'X-Correlation-ID': request.correlationId,
          Authorization: `Bearer ${process.env.JAVA_BACKEND_API_KEY}`,
        },
        body:
          request.method !== 'GET' && request.method !== 'HEAD'
            ? JSON.stringify((request as unknown as { body?: unknown }).body ?? {})
            : undefined,
      } as unknown);

      const contentType = response.headers.get('content-type');
      const body = await readResponseText(response);

      reply.code(response.status);
      if (contentType) {
        reply.header('content-type', contentType);
      }
      reply.send(body);
    } catch (error) {
      console.error('[Gateway] Proxy error:', error);
      reply.status(503).send({
        error: 'Service Unavailable',
        message: 'Could not reach backend service',
        correlationId: request.correlationId,
      });
    }
  });

  app.all<{ Params: { '*': string } }>('/v1/*', async (request, reply) => {
    const javaBackendUrl =
      process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
    const javaPath = request.url.replace(/^\/v1\//, '/api/');
    const targetUrl = new URL(javaBackendUrl + javaPath);

    try {
      const response = await fetch(targetUrl.toString(), {
        method: request.method as unknown,
        headers: {
          ...request.headers,
          host: targetUrl.hostname,
          'X-Correlation-ID': request.correlationId,
          Authorization: `Bearer ${process.env.JAVA_BACKEND_API_KEY}`,
        },
        body:
          request.method !== 'GET' && request.method !== 'HEAD'
            ? JSON.stringify((request as unknown as { body?: unknown }).body ?? {})
            : undefined,
      } as unknown);

      const contentType = response.headers.get('content-type');
      const body = await readResponseText(response);

      reply.code(response.status);
      if (contentType) {
        reply.header('content-type', contentType);
      }
      reply.send(body);
    } catch (error) {
      console.error('[Gateway] Proxy error:', error);
      reply.status(503).send({
        error: 'Service Unavailable',
        message: 'Could not reach backend service',
        correlationId: request.correlationId,
      });
    }
  });

  app.all<{ Params: { '*': string } }>('/api/v1/*', async (request, reply) => {
    const javaBackendUrl =
      process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
    const javaPath = request.url.replace(/^\/api\/v1\//, '/api/');
    const targetUrl = new URL(javaBackendUrl + javaPath);

    try {
      const response = await fetch(targetUrl.toString(), {
        method: request.method as unknown,
        headers: {
          ...request.headers,
          host: targetUrl.hostname,
          'X-Correlation-ID': request.correlationId,
          Authorization: `Bearer ${process.env.JAVA_BACKEND_API_KEY}`,
        },
        body:
          request.method !== 'GET' && request.method !== 'HEAD'
            ? JSON.stringify((request as unknown as { body?: unknown }).body ?? {})
            : undefined,
      } as unknown);

      const contentType = response.headers.get('content-type');
      const body = await readResponseText(response);

      reply.code(response.status);
      if (contentType) {
        reply.header('content-type', contentType);
      }
      reply.send(body);
    } catch (error) {
      console.error('[Gateway] Proxy error:', error);
      reply.status(503).send({
        error: 'Service Unavailable',
        message: 'Could not reach backend service',
        correlationId: request.correlationId,
      });
    }
  });

// Bind GraphQL Yoga to Fastify (pass auth context from Fastify to GraphQL resolvers)
  app.all('/graphql', async (req, reply) => {
    const serverContext = {
      userId: req.user?.userId,
      email: req.user?.email,
      role: req.user?.role,
    };

  // Third arg to yoga.fetch is the serverContext — merged into resolver context
    const response = await yoga.fetch(
      req.url,
      {
        method: req.method,
        headers: req.headers as HeadersInit,
        body:
          req.method === 'POST' || req.method === 'PUT' || req.method === 'PATCH'
            ? JSON.stringify(req.body)
            : undefined,
      },
      serverContext
    );

    try {
      response.headers.forEach((value, key) => {
        reply.header(key, value);
      });

      reply.status(response.status);
      reply.send(await readResponseText(response));
    } catch (error) {
      req.log.error(error, 'Failed to proxy GraphQL response body');
      reply.status(502).send({
        error: 'Bad Gateway',
        message: 'Could not read GraphQL upstream response',
      });
    }
  });

  app.addHook('onClose', async () => {
    realTimeService.shutdown();
  });

  return app;
}

const PORT = parseInt(process.env.PORT || '7002', 10);

const isMainModule = process.argv[1] === fileURLToPath(import.meta.url);

if (isMainModule) {
  const app = await createApp();
  app.listen({ port: PORT, host: '0.0.0.0' }, (err, address) => {
    if (err) {
      app.log.error(err);
      process.exit(1);
    }
    app.log.info(`API Server listening at ${address}`);
    app.log.info(`GraphQL endpoint: ${address}/graphql`);
    app.log.info(
      `WebSocket endpoint: ws://${address.replace('http://', '')}/canvas/:projectId`
    );
    app.log.info(
      `WebSocket (compat) endpoint: ws://${address.replace('http://', '')}/ws/canvas/:projectId`
    );
  });
}

// Graceful shutdown
process.on('SIGTERM', async () => {
  await shutdownTracing();
  process.exit(0);
});

// Type extensions for request tracking
declare module 'fastify' {
  interface FastifyRequest {
    startTime?: number;
    correlationId: string;
  }
}
