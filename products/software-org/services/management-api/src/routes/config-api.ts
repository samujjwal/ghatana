import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { getConfigLoader } from '../services/config-loader.service.js';
import { ConfigSyncService } from '../services/config-sync.service.js';

interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    timestamp: string;
}

function successResponse<T>(data: T, message?: string): ApiResponse<T> {
    return {
        data,
        success: true,
        message,
        timestamp: new Date().toISOString(),
    };
}

function errorResponse(message: string): ApiResponse<null> {
    return {
        data: null,
        success: false,
        message,
        timestamp: new Date().toISOString(),
    };
}

export default async function configRoutes(fastify: FastifyInstance): Promise<void> {
    // Full configuration
    fastify.get('/', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const orgConfig = await configLoader.loadOrgConfig();
            return reply.send(successResponse(orgConfig));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading org config');
            return reply.code(500).send(errorResponse('Failed to load organization configuration'));
        }
    });

    fastify.post('/reload', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            configLoader.clearCache();
            const orgConfig = await configLoader.loadOrgConfig();
            return reply.send(successResponse(orgConfig, 'Configuration reloaded'));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error reloading config');
            return reply.code(500).send(errorResponse('Failed to reload configuration'));
        }
    });

    // Sync config from YAML to DB
    fastify.post<{ Body: { path?: string } }>('/sync', async (request, reply) => {
        try {
            const { path } = request.body || {};
            const syncService = ConfigSyncService.getInstance();
            await syncService.syncFromConfig(path);
            return reply.send(successResponse(null, 'Configuration synced to database'));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error syncing config');
            return reply.code(500).send(errorResponse('Failed to sync configuration'));
        }
    });

    // Export config from DB to YAML
    fastify.post<{ Body: { path: string } }>('/export', async (request, reply) => {
        try {
            const { path } = request.body;
            if (!path) {
                return reply.code(400).send(errorResponse('Export path is required'));
            }
            const syncService = ConfigSyncService.getInstance();
            await syncService.exportToConfig(path);
            return reply.send(successResponse(null, `Configuration exported to ${path}`));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error exporting config');
            return reply.code(500).send(errorResponse('Failed to export configuration'));
        }
    });

    // Departments
    fastify.get('/departments', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const departments = await configLoader.loadAllDepartments();
            return reply.send(successResponse(departments));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading departments');
            return reply.code(500).send(errorResponse('Failed to load departments'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/departments/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const department = await configLoader.loadDepartmentConfig(request.params.id);

                if (!department) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Department not found: ${request.params.id}`));
                }

                return reply.send(successResponse(department));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading department');
                return reply.code(500).send(errorResponse('Failed to load department'));
            }
        }
    );

    // Personas
    fastify.get('/personas', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const personas = await configLoader.loadAllPersonas();
            return reply.send(successResponse(personas));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading personas');
            return reply.code(500).send(errorResponse('Failed to load personas'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/personas/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const persona = await configLoader.loadPersonaConfig(request.params.id);

                if (!persona) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Persona not found: ${request.params.id}`));
                }

                return reply.send(successResponse(persona));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading persona');
                return reply.code(500).send(errorResponse('Failed to load persona'));
            }
        }
    );

    // Phases
    fastify.get('/phases', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const phases = await configLoader.loadPhases();
            return reply.send(successResponse(phases));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading phases');
            return reply.code(500).send(errorResponse('Failed to load phases'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/phases/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const phases = await configLoader.loadPhases();
                const phase = phases.find((p) => p.id === request.params.id);

                if (!phase) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Phase not found: ${request.params.id}`));
                }

                return reply.send(successResponse(phase));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading phase');
                return reply.code(500).send(errorResponse('Failed to load phase'));
            }
        }
    );

    // Stages
    fastify.get('/stages', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const stages = await configLoader.loadStageMappings();
            return reply.send(successResponse(stages));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading stages');
            return reply.code(500).send(errorResponse('Failed to load stages'));
        }
    });

    // Services
    fastify.get('/services', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const services = await configLoader.loadAllServices();
            return reply.send(successResponse(services));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading services');
            return reply.code(500).send(errorResponse('Failed to load services'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/services/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const service = await configLoader.loadServiceConfig(request.params.id);

                if (!service) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Service not found: ${request.params.id}`));
                }

                return reply.send(successResponse(service));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading service');
                return reply.code(500).send(errorResponse('Failed to load service'));
            }
        }
    );

    // Integrations
    fastify.get('/integrations', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const integrations = await configLoader.loadAllIntegrations();
            return reply.send(successResponse(integrations));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading integrations');
            return reply.code(500).send(errorResponse('Failed to load integrations'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/integrations/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const integration = await configLoader.loadIntegrationConfig(request.params.id);

                if (!integration) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Integration not found: ${request.params.id}`));
                }

                return reply.send(successResponse(integration));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading integration');
                return reply.code(500).send(errorResponse('Failed to load integration'));
            }
        }
    );

    // Interactions
    fastify.get('/interactions', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const interactions = await configLoader.loadInteractions();
            return reply.send(successResponse(interactions));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading interactions');
            return reply.code(500).send(errorResponse('Failed to load interactions'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/interactions/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const interaction = await configLoader.loadInteraction(request.params.id);

                if (!interaction) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Interaction not found: ${request.params.id}`));
                }

                return reply.send(successResponse(interaction));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading interaction');
                return reply.code(500).send(errorResponse('Failed to load interaction'));
            }
        }
    );

    // Flows
    fastify.get('/flows', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const flows = await configLoader.loadFlows();
            return reply.send(successResponse(flows));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading flows');
            return reply.code(500).send(errorResponse('Failed to load flows'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/flows/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const flow = await configLoader.loadFlow(request.params.id);

                if (!flow) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Flow not found: ${request.params.id}`));
                }

                return reply.send(successResponse(flow));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading flow');
                return reply.code(500).send(errorResponse('Failed to load flow'));
            }
        }
    );

    // Operators
    fastify.get('/operators', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const operators = await configLoader.loadOperators();
            return reply.send(successResponse(operators));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading operators');
            return reply.code(500).send(errorResponse('Failed to load operators'));
        }
    });

    fastify.get<{ Params: { id: string } }>(
        '/operators/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const operator = await configLoader.loadOperator(request.params.id);

                if (!operator) {
                    return reply
                        .code(404)
                        .send(errorResponse(`Operator not found: ${request.params.id}`));
                }

                return reply.send(successResponse(operator));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading operator');
                return reply.code(500).send(errorResponse('Failed to load operator'));
            }
        }
    );

    // Aggregated endpoints
    fastify.get('/agents', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const agents = await configLoader.loadAllAgents();
            return reply.send(successResponse(agents));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading agents');
            return reply.code(500).send(errorResponse('Failed to load agents'));
        }
    });

    fastify.get('/workflows', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const workflows = await configLoader.loadAllWorkflows();
            return reply.send(successResponse(workflows));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading workflows');
            return reply.code(500).send(errorResponse('Failed to load workflows'));
        }
    });

    fastify.get('/kpis', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const kpis = await configLoader.loadAllKpis();
            return reply.send(successResponse(kpis));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading KPIs');
            return reply.code(500).send(errorResponse('Failed to load KPIs'));
        }
    });

    // Norms
    fastify.get<{ Querystring: { tenantId?: string; category?: string } }>(
        '/norms',
        async (request: FastifyRequest<{ Querystring: { tenantId?: string; category?: string } }>, reply: FastifyReply) => {
            try {
                const configLoader = getConfigLoader();
                const orgConfig = await configLoader.loadOrgConfig();

                // Extract norms from various sources
                let norms: Array<{
                    id: string;
                    name: string;
                    category: string;
                    description: string;
                    enforcement: string;
                    severity?: string;
                }> = [];

                // From compliance policies
                if (orgConfig.policies?.compliance) {
                    for (const policy of orgConfig.policies.compliance) {
                        norms.push({
                            id: policy.id || `compliance-${policy.name}`,
                            name: policy.name,
                            category: 'compliance',
                            description: policy.description || policy.name,
                            enforcement: policy.enforcement || 'mandatory',
                            severity: policy.severity,
                        });
                    }
                }

                // From security policies
                if (orgConfig.policies?.security) {
                    for (const policy of orgConfig.policies.security) {
                        norms.push({
                            id: policy.id || `security-${policy.name}`,
                            name: policy.name,
                            category: 'security',
                            description: policy.description || policy.name,
                            enforcement: policy.enforcement || 'mandatory',
                            severity: policy.severity,
                        });
                    }
                }

                // From HR policies
                if (orgConfig.policies?.hr) {
                    for (const policy of orgConfig.policies.hr) {
                        norms.push({
                            id: policy.id || `hr-${policy.name}`,
                            name: policy.name,
                            category: 'hr',
                            description: policy.description || policy.name,
                            enforcement: policy.enforcement || 'recommended',
                        });
                    }
                }

                // Filter by category if specified
                if (request.query.category) {
                    norms = norms.filter(n => n.category === request.query.category);
                }

                return reply.send(successResponse(norms));
            } catch (error) {
                fastify.log.error({ err: error }, '[Config API] Error loading norms');
                return reply.code(500).send(errorResponse('Failed to load norms'));
            }
        }
    );

    // Agents Marketplace
    fastify.get('/agents/marketplace', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const agents = await configLoader.loadAllAgents();

            // Transform agents to marketplace format with additional metadata
            const marketplace = agents.map(agent => ({
                id: agent.id,
                name: agent.name,
                description: agent.description,
                department: agent.department,
                role: agent.role,
                category: agent.department || 'general',
                capabilities: agent.capabilities || [],
                status: 'available',
                version: agent.version || '1.0.0',
                author: agent.owner || 'Internal',
                rating: 4.5,
                downloads: Math.floor(Math.random() * 1000) + 100,
                lastUpdated: new Date().toISOString(),
                tags: [
                    agent.department,
                    ...(Array.isArray(agent.capabilities) ? agent.capabilities : []),
                    ...(agent.integrations || []),
                ].filter(Boolean),
            }));

            return reply.send(successResponse(marketplace));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error loading agents marketplace');
            return reply.code(500).send(errorResponse('Failed to load agents marketplace'));
        }
    });

    // Graph data
    fastify.get('/graph', async (_request: FastifyRequest, reply: FastifyReply) => {
        try {
            const configLoader = getConfigLoader();
            const orgConfig = await configLoader.loadOrgConfig();

            const nodes: Array<{
                id: string;
                type: string;
                label: string;
                data: unknown;
            }> = [];

            const edges: Array<{
                id: string;
                source: string;
                target: string;
                type: string;
            }> = [];

            // Departments
            for (const dept of orgConfig.departments) {
                nodes.push({
                    id: dept.id,
                    type: 'department',
                    label: dept.name,
                    data: dept,
                });
            }

            // Services and dependencies
            for (const svc of orgConfig.services) {
                nodes.push({
                    id: svc.id,
                    type: 'service',
                    label: svc.name,
                    data: svc,
                });

                edges.push({
                    id: `edge-${svc.department_id}-${svc.id}`,
                    source: svc.department_id,
                    target: svc.id,
                    type: 'owns',
                });

                if (svc.dependencies) {
                    for (const depId of svc.dependencies) {
                        edges.push({
                            id: `edge-${svc.id}-${depId}`,
                            source: svc.id,
                            target: depId,
                            type: 'depends-on',
                        });
                    }
                }
            }

            // Integrations
            for (const int of orgConfig.integrations) {
                nodes.push({
                    id: int.id,
                    type: 'integration',
                    label: int.name,
                    data: int,
                });
            }

            // Personas
            for (const persona of orgConfig.personas) {
                nodes.push({
                    id: `persona-${persona.id}`,
                    type: 'persona',
                    label: persona.display_name,
                    data: persona,
                });
            }

            return reply.send(successResponse({ nodes, edges }));
        } catch (error) {
            fastify.log.error({ err: error }, '[Config API] Error generating graph');
            return reply.code(500).send(errorResponse('Failed to generate organization graph'));
        }
    });
}
