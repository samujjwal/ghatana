/**
 * Agents Actions API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for agent actions and HITL (Human-in-the-Loop) operations
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/agents/actions - List pending agent actions
 * - GET /api/v1/agents/actions/:actionId - Get action details
 * - POST /api/v1/agents/actions/:actionId/approve - Approve action
 * - POST /api/v1/agents/actions/:actionId/reject - Reject action
 * - POST /api/v1/agents/actions/:actionId/defer - Defer action
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';

/** Agent action interface */
interface AgentAction {
    id: string;
    agentId: string;
    agentName: string;
    priority: 'p0' | 'p1' | 'p2';
    description: string;
    proposedAction: string;
    confidence: number;
    reasoning: string;
    affectedServices: string[];
    expectedImpact: string;
    riskLevel: 'low' | 'medium' | 'high';
    createdAt: string;
    status: 'pending' | 'approved' | 'rejected' | 'deferred' | 'expired';
    slaDeadline: string;
    incidentIds?: string[];
}

/** Mock data for agent actions */
const mockAgentActions: AgentAction[] = [
    {
        id: 'action-1',
        agentId: 'agent-1',
        agentName: 'SecurityMonitor',
        priority: 'p0',
        description: 'Suspicious login pattern detected',
        proposedAction: 'Block IP address 192.168.1.100 and trigger security audit',
        confidence: 0.95,
        reasoning: 'Multiple failed login attempts from unusual location followed by successful login',
        affectedServices: ['auth-service', 'user-management'],
        expectedImpact: 'Prevents potential security breach',
        riskLevel: 'high',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        status: 'pending',
        slaDeadline: new Date(Date.now() + 3600000).toISOString(),
        incidentIds: ['incident-1'],
    },
    {
        id: 'action-2',
        agentId: 'agent-2',
        agentName: 'PerformanceOptimizer',
        priority: 'p1',
        description: 'Database query performance degradation',
        proposedAction: 'Add index to user_profiles.last_login column',
        confidence: 0.88,
        reasoning: 'Query execution time increased by 300% over the last hour',
        affectedServices: ['database', 'user-service'],
        expectedImpact: 'Improves query performance by estimated 70%',
        riskLevel: 'low',
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        status: 'pending',
        slaDeadline: new Date(Date.now() + 7200000).toISOString(),
    },
    {
        id: 'action-3',
        agentId: 'agent-3',
        agentName: 'ResourceScaler',
        priority: 'p2',
        description: 'High CPU utilization on web servers',
        proposedAction: 'Scale web server fleet from 3 to 5 instances',
        confidence: 0.92,
        reasoning: 'Sustained CPU usage above 80% for 15 minutes',
        affectedServices: ['web-server', 'load-balancer'],
        expectedImpact: 'Reduces CPU utilization to acceptable levels',
        riskLevel: 'low',
        createdAt: new Date(Date.now() - 1800000).toISOString(),
        status: 'pending',
        slaDeadline: new Date(Date.now() + 5400000).toISOString(),
    },
];

/**
 * Register agents actions routes
 */
export default async function agentActionsRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/agents/actions
     * List all pending agent actions
     */
    fastify.get('/actions', async (request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching agent actions');

        // Return mock data for now
        const response = mockAgentActions;
        return reply.send(response);
    });

    /**
     * GET /api/v1/agents/actions/:actionId
     * Get specific action details
     */
    fastify.get<{ Params: { actionId: string } }>(
        '/actions/:actionId',
        async (request: FastifyRequest<{ Params: { actionId: string } }>, reply: FastifyReply) => {
            const { actionId } = request.params;
            fastify.log.debug({ actionId }, 'Fetching agent action');

            const action = mockAgentActions.find(a => a.id === actionId);
            if (!action) {
                return reply.status(404).send({ error: 'Action not found' });
            }

            return reply.send(action);
        }
    );

    /**
     * POST /api/v1/agents/actions/:actionId/approve
     * Approve an agent action
     */
    fastify.post<{ Params: { actionId: string }; Body: { approvedBy: string; reason?: string } }>(
        '/actions/:actionId/approve',
        async (request: FastifyRequest<{ Params: { actionId: string }; Body: { approvedBy: string; reason?: string } }>, reply: FastifyReply) => {
            const { actionId } = request.params;
            const { approvedBy, reason } = request.body;
            fastify.log.debug({ actionId, approvedBy }, 'Approving agent action');

            const action = mockAgentActions.find(a => a.id === actionId);
            if (!action) {
                return reply.status(404).send({ error: 'Action not found' });
            }

            // Update action status (in real implementation, this would be persisted)
            action.status = 'approved';

            return reply.send({
                status: 'approved',
                actionId,
                approvedBy,
                reason,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * POST /api/v1/agents/actions/:actionId/reject
     * Reject an agent action
     */
    fastify.post<{ Params: { actionId: string }; Body: { rejectedBy: string; reason?: string } }>(
        '/actions/:actionId/reject',
        async (request: FastifyRequest<{ Params: { actionId: string }; Body: { rejectedBy: string; reason?: string } }>, reply: FastifyReply) => {
            const { actionId } = request.params;
            const { rejectedBy, reason } = request.body;
            fastify.log.debug({ actionId, rejectedBy }, 'Rejecting agent action');

            const action = mockAgentActions.find(a => a.id === actionId);
            if (!action) {
                return reply.status(404).send({ error: 'Action not found' });
            }

            // Update action status (in real implementation, this would be persisted)
            action.status = 'rejected';

            return reply.send({
                status: 'rejected',
                actionId,
                rejectedBy,
                reason,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * POST /api/v1/agents/actions/:actionId/defer
     * Defer an agent action
     */
    fastify.post<{ Params: { actionId: string }; Body: { deferredBy: string; reason?: string; newDeadline?: string } }>(
        '/actions/:actionId/defer',
        async (request: FastifyRequest<{ Params: { actionId: string }; Body: { deferredBy: string; reason?: string; newDeadline?: string } }>, reply: FastifyReply) => {
            const { actionId } = request.params;
            const { deferredBy, reason, newDeadline } = request.body;
            fastify.log.debug({ actionId, deferredBy }, 'Deferring agent action');

            const action = mockAgentActions.find(a => a.id === actionId);
            if (!action) {
                return reply.status(404).send({ error: 'Action not found' });
            }

            // Update action status (in real implementation, this would be persisted)
            action.status = 'deferred';
            if (newDeadline) {
                action.slaDeadline = newDeadline;
            }

            return reply.send({
                status: 'deferred',
                actionId,
                deferredBy,
                reason,
                newDeadline,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * GET /api/v1/agents/marketplace
     * List all available agents in marketplace
     */
    fastify.get('/marketplace', async (request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching agents marketplace');

        try {
            // Import config loader dynamically to avoid circular dependencies
            const { getConfigLoader } = await import('../services/config-loader.service.js');
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
                icon: 'Bot',
                capabilities: agent.capabilities || [],
                skills: agent.capabilities || [],
                defaultConfig: {},
                pricing: {
                    type: 'free' as const,
                    creditsPerMonth: 0,
                },
                popularity: Math.floor(Math.random() * 1000) + 100,
                rating: 4.5,
                reviewCount: Math.floor(Math.random() * 50) + 5,
                isNew: false,
                isFeatured: false,
                createdAt: new Date().toISOString(),
                status: 'available',
                version: agent.version || '1.0.0',
                author: agent.owner || 'Internal',
                downloads: Math.floor(Math.random() * 1000) + 100,
                lastUpdated: new Date().toISOString(),
                tags: [
                    agent.department,
                    ...(Array.isArray(agent.capabilities) ? agent.capabilities : []),
                    ...(agent.integrations || []),
                ].filter(Boolean),
            }));

            return reply.send({
                data: marketplace,
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error loading agents marketplace');
            return reply.status(500).send({
                error: 'Failed to load agents marketplace',
                success: false,
                timestamp: new Date().toISOString(),
            });
        }
    });
}
