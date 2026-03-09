import { createYoga, createSchema } from 'graphql-yoga';
import fastify from 'fastify';
import cors from '@fastify/cors';
import fastifyWebsocket from '@fastify/websocket';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import fetch from 'node-fetch';

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
import { devAuthBypass } from './middleware/devAuth';

// Import WebSocket Service
import { RealTimeService } from './services/RealTimeService';

// Initialize OpenTelemetry tracing
startTracing();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = fastify({ logger: true });

// Instrument Fastify with OpenTelemetry (manual instrumentation to avoid ESM/CommonJS issues)
instrumentFastify(app);

// Load Schema
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

// Register WebSocket support
await app.register(fastifyWebsocket);

// Request tracking middleware
app.addHook('onRequest', async (request, reply) => {
  request.startTime = Date.now();
});

app.addHook('onResponse', async (request, reply) => {
  const duration = (Date.now() - request.startTime!) / 1000;
  const route = (request as unknown).routerPath || request.url;
  const method = request.method;
  const statusCode = reply.statusCode.toString();

  httpRequestDuration.labels(method, route, statusCode).observe(duration);
  httpRequestTotal.labels(method, route, statusCode).inc();
});

// Health check endpoint
app.get('/health', async () => {
  return {
    status: 'healthy',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    environment: process.env.NODE_ENV || 'development',
  };
});

// Prometheus metrics endpoint
app.get('/metrics', async (request, reply) => {
  reply.type(register.contentType);
  return register.metrics();
});

// Initialize Real-Time Service
const realTimeService = new RealTimeService();
realTimeService.registerRoutes(app);

// Register dev auth bypass FIRST (development only)
// This must be registered before routes to apply to all contexts
await devAuthBypass(app);

// Register REST API routes
app.register(workspaceRoutes, { prefix: '/api' });
app.register(projectRoutes, { prefix: '/api' });
app.register(devsecopsRoutes, { prefix: '/api' });
app.register(canvasRoutes, { prefix: '/api' });
app.register(lifecycleRoutes, { prefix: '/api' });

// Compatibility aliases (legacy docs): /v1/* maps to the same handlers as /api/*
app.register(workspaceRoutes, { prefix: '/v1' });
app.register(projectRoutes, { prefix: '/v1' });
app.register(devsecopsRoutes, { prefix: '/v1' });
app.register(canvasRoutes, { prefix: '/v1' });
app.register(lifecycleRoutes, { prefix: '/v1' });

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
          ? JSON.stringify((request as unknown).body ?? {})
          : undefined,
    } as unknown);

    const contentType = response.headers.get('content-type');
    const body = await response.text();

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
    });
  }
});

// Compatibility proxy: /v1/* is forwarded to Java backend as /api/*
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
          ? JSON.stringify((request as unknown).body ?? {})
          : undefined,
    } as unknown);

    const contentType = response.headers.get('content-type');
    const body = await response.text();

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
    });
  }
});

// Bind GraphQL Yoga to Fastify (pass auth context from Fastify to GraphQL resolvers)
app.all('/graphql', async (req, reply) => {
  // Build server context from authenticated Fastify request
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

  response.headers.forEach((value, key) => {
    reply.header(key, value);
  });

  reply.status(response.status);
  reply.send(await response.text());
});

const PORT = parseInt(process.env.PORT || '7002', 10);

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

// Graceful shutdown
process.on('SIGTERM', async () => {
  app.log.info('SIGTERM received, shutting down gracefully');
  realTimeService.shutdown();
  await shutdownTracing();
  app.close(() => {
    app.log.info('Server closed');
    process.exit(0);
  });
});

// Type extensions for request tracking
declare module 'fastify' {
  interface FastifyRequest {
    startTime?: number;
  }
}
