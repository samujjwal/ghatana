import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import sensible from '@fastify/sensible';
import compress from '@fastify/compress';
import rateLimit from '@fastify/rate-limit';
import fastifySocketIO from 'fastify-socket.io';
import { appConfig } from './config/index.js';
import { prisma } from './db/client.js';
import personaRoutes from './routes/personas.js';
import workspaceRoutes from './routes/workspaces.js';
import kpiRoutes from './routes/kpis.js';
import modelRoutes from './routes/models.js';
import reportRoutes from './routes/reports.js';
import executionRoutes from './routes/executions.js';
import auditRoutes from './routes/audit.js';
import tenantRoutes from './routes/tenants.js';
import metricsRoutes from './routes/metrics.js';
import configRoutes from './routes/config-api.js';
import bulkRoutes from './routes/bulk.js';
import agentActionsRoutes from './routes/agent-actions.js';
import orgRoutes from './routes/org.js';
import adminRoutes from './routes/admin.js';
import buildRoutes from './routes/build.js';
import { observeRoutes } from './routes/observe.js';
import { operateRoutes } from './routes/operate.js';
import devsecopsRoutes from './routes/devsecops.js';
import approvalsRoutes from './routes/approvals.js';
import timeOffRoutes from './routes/time-off.js';
import growthPlanRoutes from './routes/growth-plans.js';
import performanceReviewRoutes from './routes/performance-reviews.js';
import budgetRoutes from './routes/budgets.js';
import normsRoutes from './routes/norms.js';
import { knowledgeBaseRoutes } from './routes/knowledge-base.js';
import { innovationRoutes } from './routes/innovation.js';
import { skillsRoutes } from './routes/skills.js';
import { rootRoutes } from './routes/root.js';
import { setupPersonaSync } from './websocket/persona-sync.js';
import { setupAlertsStreaming } from './websocket/alerts-streaming.js';
import { setupLogsStreaming } from './websocket/logs-streaming.js';
import { setupDevSecOpsStreaming } from './websocket/devsecops-streaming.js';
import { ConfigSyncService } from './services/config-sync.service.js';

const fastify = Fastify({
    logger: {
        level: appConfig.logging.level,
        transport: appConfig.isDevelopment
            ? {
                target: 'pino-pretty',
                options: {
                    translateTime: 'HH:MM:ss Z',
                    ignore: 'pid,hostname',
                },
            }
            : undefined,
    },
});

// Register plugins
await fastify.register(cors, appConfig.cors);
await fastify.register(helmet, {
    contentSecurityPolicy: appConfig.isDevelopment ? false : undefined,
});
await fastify.register(sensible);
await fastify.register(compress);
await fastify.register(rateLimit, {
    max: 100,
    timeWindow: '1 minute',
});

// Register Socket.IO for real-time persona sync
await fastify.register(fastifySocketIO, {
    cors: {
        origin: appConfig.cors.origin,
        credentials: true,
    },
    // Socket.IO configuration
    transports: ['websocket', 'polling'],
    serveClient: false,
    // Connection timeout and ping settings
    connectTimeout: 45000,
    pingTimeout: 30000,
    pingInterval: 25000,
});

// Setup WebSocket handlers (after server is ready)
fastify.ready().then(async () => {
    // Add global authentication middleware - allows all connections in dev
    // In production, implement proper JWT validation here
    fastify.io.use((socket, next) => {
        const isDev = !process.env.NODE_ENV || process.env.NODE_ENV === 'development';

        if (isDev) {
            // Development: allow all connections
            return next();
        }

        // Production: validate token
        const token = socket.handshake.auth?.token;
        if (!token) {
            return next(new Error('Authentication token required'));
        }

        // TODO: Validate JWT token here
        next();
    });

    setupPersonaSync(fastify.io, fastify);
    setupAlertsStreaming(fastify.io, fastify);
    setupLogsStreaming(fastify.io, fastify);
    setupDevSecOpsStreaming(fastify.io, fastify);

    // Auto-seed config on boot
    try {
        const syncService = ConfigSyncService.getInstance();
        await syncService.syncFromConfig();
        fastify.log.info('✅ Configuration synced successfully on boot');
    } catch (error) {
        fastify.log.error({ err: error }, '❌ Failed to sync configuration on boot');
        // Don't crash the server if config sync fails, just log it
    }
});

// Health check endpoint
fastify.get('/health', async () => {
    try {
        await prisma.$queryRaw`SELECT 1`;
        return { status: 'ok', timestamp: new Date().toISOString() };
    } catch (error) {
        fastify.log.error(error, 'Health check failed');
        throw fastify.httpErrors.serviceUnavailable('Database connection failed');
    }
});

// Root endpoint
fastify.get('/', async () => {
    return {
        name: '@ghatana/software-org-backend',
        version: '1.0.0',
        environment: appConfig.env,
    };
});

// Register API routes
await fastify.register(personaRoutes, { prefix: '/api/personas' });
await fastify.register(workspaceRoutes, { prefix: '/api/workspaces' });
await fastify.register(configRoutes, { prefix: '/api/v1/config' });

// Register v1 API routes (matching MSW contracts)
await fastify.register(kpiRoutes, { prefix: '/api/v1/kpis' });
await fastify.register(modelRoutes, { prefix: '/api/v1/models' });
await fastify.register(reportRoutes, { prefix: '/api/v1/reports' });
await fastify.register(executionRoutes, { prefix: '/api/v1' });
await fastify.register(auditRoutes, { prefix: '/api/v1/audit' });
await fastify.register(tenantRoutes, { prefix: '/api/v1' });
await fastify.register(metricsRoutes, { prefix: '/api/v1/metrics' });
await fastify.register(bulkRoutes, { prefix: '/api/v1/bulk' });
await fastify.register(agentActionsRoutes, { prefix: '/api/v1/agents' });
await fastify.register(orgRoutes, { prefix: '/api/v1/org' });
await fastify.register(adminRoutes, { prefix: '/api/v1' });
await fastify.register(buildRoutes, { prefix: '/api/v1' });
await fastify.register(observeRoutes, { prefix: '/api/v1' });
await fastify.register(operateRoutes, { prefix: '/api/v1' });
await fastify.register(devsecopsRoutes, { prefix: '/api/v1' });
await fastify.register(approvalsRoutes, { prefix: '/api/v1' });
await fastify.register(timeOffRoutes, { prefix: '/api/v1' });
await fastify.register(growthPlanRoutes, { prefix: '/api/v1' });
await fastify.register(performanceReviewRoutes, { prefix: '/api/v1' });
await fastify.register(budgetRoutes, { prefix: '/api/v1' });
await fastify.register(normsRoutes, { prefix: '/api/v1/norms' });
await fastify.register(knowledgeBaseRoutes, { prefix: '/api/v1/knowledge' });
await fastify.register(innovationRoutes, { prefix: '/api/v1/innovation' });
await fastify.register(skillsRoutes, { prefix: '/api/v1/skills' });
await fastify.register(rootRoutes, { prefix: '/api/v1/root' });

// Graceful shutdown
const shutdown = async () => {
    fastify.log.info('Shutting down gracefully...');
    await prisma.$disconnect();
    await fastify.close();
    process.exit(0);
};

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

// Start server
try {
    const address = await fastify.listen({
        port: appConfig.server.port,
        host: appConfig.server.host,
    });
    fastify.log.info(`Server listening on ${address}`);
} catch (err) {
    fastify.log.error(err);
    process.exit(1);
}
