/**
 * Norms API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for organizational norms, policies, and standards
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/norms - List all norms with optional filtering
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getConfigLoader } from '../services/config-loader.service.js';

interface NormsQuerystring {
    tenantId?: string;
    category?: string;
}

interface Norm {
    id: string;
    name: string;
    category: string;
    description: string;
    enforcement: string;
    severity?: string;
    source?: string;
}

/**
 * Register norms routes
 */
export default async function normsRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/norms
     * List all organizational norms with optional filtering
     */
    fastify.get<{ Querystring: NormsQuerystring }>(
        '/',
        async (request: FastifyRequest<{ Querystring: NormsQuerystring }>, reply: FastifyReply) => {
            try {
                const { tenantId, category } = request.query;
                fastify.log.debug({ tenantId, category }, 'Fetching norms');

                // Return mock norms data for now
                let norms: Norm[] = [
                    {
                        id: 'compliance-gdpr',
                        name: 'GDPR Compliance',
                        category: 'compliance',
                        description: 'General Data Protection Regulation compliance requirements',
                        enforcement: 'mandatory',
                        severity: 'high',
                        source: 'compliance',
                    },
                    {
                        id: 'security-mfa',
                        name: 'Multi-Factor Authentication',
                        category: 'security',
                        description: 'All user accounts must use multi-factor authentication',
                        enforcement: 'mandatory',
                        severity: 'high',
                        source: 'security',
                    },
                    {
                        id: 'security-data-encryption',
                        name: 'Data Encryption at Rest',
                        category: 'security',
                        description: 'All sensitive data must be encrypted at rest',
                        enforcement: 'mandatory',
                        severity: 'high',
                        source: 'security',
                    },
                    {
                        id: 'hr-working-hours',
                        name: 'Standard Working Hours',
                        category: 'hr',
                        description: 'Core working hours are 10 AM to 4 PM local time',
                        enforcement: 'recommended',
                        source: 'hr',
                    },
                    {
                        id: 'hr-remote-work',
                        name: 'Remote Work Policy',
                        category: 'hr',
                        description: 'Employees can work remotely up to 3 days per week',
                        enforcement: 'recommended',
                        source: 'hr',
                    },
                    {
                        id: 'engineering-code-review',
                        name: 'Code Review Policy',
                        category: 'engineering',
                        description: 'All code changes must be reviewed by at least one other engineer',
                        enforcement: 'mandatory',
                        severity: 'medium',
                        source: 'engineering',
                    },
                    {
                        id: 'engineering-testing',
                        name: 'Test Coverage Requirements',
                        category: 'engineering',
                        description: 'Minimum 80% test coverage for all production code',
                        enforcement: 'recommended',
                        source: 'engineering',
                    },
                ];

                // Filter by category if specified
                if (category) {
                    norms = norms.filter(n => n.category === category);
                }

                return reply.send({
                    data: norms,
                    success: true,
                    timestamp: new Date().toISOString(),
                    count: norms.length,
                });
            } catch (error) {
                fastify.log.error({ err: error }, 'Error loading norms');
                return reply.status(500).send({
                    error: 'Failed to load norms',
                    success: false,
                    timestamp: new Date().toISOString(),
                });
            }
        }
    );

    /**
     * GET /api/v1/norms/:id
     * Get a specific norm by ID
     */
    fastify.get<{ Params: { id: string } }>(
        '/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const { id } = request.params;
                fastify.log.debug({ id }, 'Fetching norm by ID');

                // Mock norms data
                const norms: Norm[] = [
                    {
                        id: 'compliance-gdpr',
                        name: 'GDPR Compliance',
                        category: 'compliance',
                        description: 'General Data Protection Regulation compliance requirements',
                        enforcement: 'mandatory',
                        severity: 'high',
                        source: 'compliance',
                    },
                    {
                        id: 'security-mfa',
                        name: 'Multi-Factor Authentication',
                        category: 'security',
                        description: 'All user accounts must use multi-factor authentication',
                        enforcement: 'mandatory',
                        severity: 'high',
                        source: 'security',
                    },
                ];

                const norm = norms.find(n => n.id === id);

                if (!norm) {
                    return reply.status(404).send({
                        error: 'Norm not found',
                        success: false,
                        timestamp: new Date().toISOString(),
                    });
                }

                return reply.send({
                    data: norm,
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            } catch (error) {
                fastify.log.error({ err: error }, 'Error loading norm');
                return reply.status(500).send({
                    error: 'Failed to load norm',
                    success: false,
                    timestamp: new Date().toISOString(),
                });
            }
        }
    );
}
