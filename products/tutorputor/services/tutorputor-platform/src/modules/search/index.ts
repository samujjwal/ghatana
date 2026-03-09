import type { FastifyPluginAsync } from 'fastify';
import { searchRoutes } from './routes.js';

/**
 * Search module - consolidates:
 * - tutorputor-search
 *
 * @doc.type module
 * @doc.purpose Search functionality for finding modules, threads, paths etc.
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const searchModule: FastifyPluginAsync = async (app) => {
    await app.register(searchRoutes);

    app.get('/health', async () => {
        return {
            module: 'search',
            status: 'healthy',
        };
    });
};
