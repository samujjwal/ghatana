import 'dotenv/config';

import { createYoga, createSchema } from 'graphql-yoga';
import fastify from 'fastify';
import { randomUUID } from 'node:crypto';
import cors from '@fastify/cors';
import fastifyWebsocket from '@fastify/websocket';
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
import telemetryRoutes from './routes/telemetry';
import aiRoutes from './routes/ai';
import { devAuthBypass } from './middleware/devAuth';
import {
  assertDevAuthBypassAllowed,
  isDevAuthBypassEnabled,
} from './middleware/dev-auth-config';
import { authMiddleware } from './middleware/auth.middleware';
import { auditMiddleware } from './middleware/audit.middleware';
import { authRoutes } from './routes/auth';
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

function registerApiPrefixes(
  app: FastifyInstance,
  route: Parameters<FastifyInstance['register']>[0]
) {
  app.register(route, { prefix: '/api' });
  app.register(route, { prefix: '/v1' });
  app.register(route, { prefix: '/api/v1' });
}

export async function createApp(
  options: CreateAppOptions = {}
): Promise<FastifyInstance> {
  if (options.jwtSecret) {
    process.env.JWT_ACCESS_SECRET = options.jwtSecret;
  }

  const app = fastify({ logger: true });

  instrumentFastify(app);

  const yoga = createYoga({
    schema,
    logging: {
      debug: (...args) => args.forEach((arg) => app.log.debug(arg)),
      info: (...args) => args.forEach((arg) => app.log.info(arg)),
      warn: (...args) => args.forEach((arg) => app.log.warn(arg)),
      error: (...args) => args.forEach((arg) => app.log.error(arg)),
    },
    graphiql: true,
  });

  // @ts-ignore - CORS plugin type issue
  app.register(cors, {
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  });

  await app.register(fastifyWebsocket);

  app.addHook('onRequest', async (request) => {
    const headerValue = request.headers['x-correlation-id'];
    const correlationId =
      typeof headerValue === 'string' && headerValue.trim().length > 0
        ? headerValue
        : randomUUID();

    request.correlationId = correlationId;
    request.log = request.log.child({ correlationId });
    request.headers['x-correlation-id'] = correlationId;

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

  app.get('/health', async () => {
    return {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      environment: process.env.NODE_ENV || 'development',
    };
  });

  app.get('/metrics', async (_request, reply) => {
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
  registerApiPrefixes(app, canvasRoutes);
  registerApiPrefixes(app, lifecycleRoutes);
  registerApiPrefixes(app, telemetryRoutes);
  registerApiPrefixes(app, aiRoutes);

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
