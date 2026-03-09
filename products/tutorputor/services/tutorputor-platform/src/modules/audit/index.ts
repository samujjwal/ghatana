import type { FastifyPluginAsync } from 'fastify';
import { auditRoutes } from './routes.js';

/**
 * Audit module - consolidates:
 * - tutorputor-audit
 *
 * @doc.type module
 * @doc.purpose Audit logging and analysis
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const auditModule: FastifyPluginAsync = async (app) => {
    await app.register(auditRoutes);

    app.get('/health', async () => {
        return {
            module: 'audit',
            status: 'healthy',
        };
    });
};
