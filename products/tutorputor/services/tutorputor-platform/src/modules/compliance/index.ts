import type { FastifyPluginAsync } from 'fastify';
import { complianceRoutes } from './routes.js';

/**
 * Compliance module - consolidates:
 * - tutorputor-compliance
 *
 * @doc.type module
 * @doc.purpose GDPR/CCPA compliance tools
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const complianceModule: FastifyPluginAsync = async (app) => {
    await app.register(complianceRoutes);

    app.get('/health', async () => {
        return {
            module: 'compliance',
            status: 'healthy',
        };
    });
};
