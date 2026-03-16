import { ConfigService } from '../../services/ConfigService';
import { DashboardService } from '../../services/DashboardService';
import { FlowService } from '../../services/FlowService';
import { GoldenFlows } from '../../config/flows';

const configService = ConfigService.getInstance();
const dashboardService = DashboardService.getInstance();
const flowService = FlowService.getInstance();

/** Subset of the shared resolver context carrying the authenticated identity. */
interface ResolverContext {
    userId?: string;
    email?: string;
    role?: string;
}

/**
 * Extract the authenticated userId from the GraphQL resolver context.
 * Throws with a clear message when no user identity is present.
 */
function requireUserId(context: ResolverContext): string {
    if (!context?.userId) {
        throw new Error('Authentication required: no userId in resolver context');
    }
    return context.userId;
}

export const YappcCoreResolver = {
    Query: {
        personas: () => configService.getPersonas(),
        persona: (_: unknown, { id }: { id: string }) => configService.getPersona(id),
        domains: () => configService.getDomains(),
        domain: (_: unknown, { id }: { id: string }) => configService.getDomain(id),

        dashboard: async (_: unknown, { id }: { id: string }) => {
            return dashboardService.getDashboard(id);
        },
        dashboards: async (_: unknown, { domainId }: { domainId?: string }) => {
            if (domainId) {
                return dashboardService.getDashboardsByDomain(domainId);
            }
            return dashboardService.getDashboards();
        },

        flowState: async (_: unknown, { instanceId }: { instanceId: string }) => {
            const flow = await flowService.getFlow(instanceId);
            if (!flow) return null;

            return {
                ...flow,
                availableTransitions: await flowService.getAvailableTransitions(instanceId),
                activeTasks: await flowService.getActiveTasks(instanceId)
            };
        },

        // Policy queries
        policies: () => {
            // Mock policies - will be replaced with Java backend call
            return [
                {
                    id: 'prod-deploy-approval',
                    name: 'Production Deployment Approval',
                    description: 'Requires approval for production deployments',
                    appliesTo: {
                        domains: ['operations'],
                        personas: ['devops', 'sre'],
                        actions: ['deploy.execute'],
                        environments: ['production']
                    },
                    constraints: { maxDeploysPerDay: 3 },
                    approval: { required: true, minApprovers: 2, approverRoles: ['ADMIN', 'LEAD'] },
                    enabled: true
                }
            ];
        },
        policy: (_: unknown, { id }: { id: string }) => {
            // Mock - will delegate to Java
            if (id === 'prod-deploy-approval') {
                return {
                    id: 'prod-deploy-approval',
                    name: 'Production Deployment Approval',
                    description: 'Requires approval for production deployments',
                    appliesTo: { domains: ['operations'], actions: ['deploy.execute'] },
                    constraints: {},
                    approval: { required: true, minApprovers: 2, approverRoles: ['ADMIN'] },
                    enabled: true
                };
            }
            return null;
        },

        // Flow definition queries
        flowDefinitions: () => {
            return Object.values(GoldenFlows).map(flow => ({
                id: flow.id,
                initialState: flow.initialState,
                states: flow.states,
                guards: Object.values(flow.states)
                    .flatMap(s => s.guards || [])
                    .filter((v, i, a) => a.indexOf(v) === i)
            }));
        },
        flowDefinition: (_: unknown, { id }: { id: string }) => {
            const flow = GoldenFlows[id];
            if (!flow) return null;
            return {
                id: flow.id,
                initialState: flow.initialState,
                states: flow.states,
                guards: Object.values(flow.states)
                    .flatMap(s => s.guards || [])
                    .filter((v, i, a) => a.indexOf(v) === i)
            };
        }
    },
    Mutation: {
        startFlow: async (_: unknown, { flowId, input }: { flowId: string, input: Record<string, unknown> }, context: ResolverContext) => {
            const userId = requireUserId(context);
            const flow = await flowService.startFlow(flowId, input, userId);
            return {
                ...flow,
                availableTransitions: await flowService.getAvailableTransitions(flow.instanceId),
                activeTasks: await flowService.getActiveTasks(flow.instanceId)
            };
        },
        transitionFlow: async (_: unknown, { instanceId, event, payload }: { instanceId: string, event: string, payload: Record<string, unknown> }, context: ResolverContext) => {
            const userId = requireUserId(context);
            const flow = await flowService.transition(instanceId, event, payload, userId);
            return {
                ...flow,
                availableTransitions: await flowService.getAvailableTransitions(flow.instanceId),
                activeTasks: await flowService.getActiveTasks(flow.instanceId)
            };
        },
        cancelFlow: async (_: unknown, { instanceId, reason }: { instanceId: string, reason?: string }, context: ResolverContext) => {
            const userId = requireUserId(context);
            const flow = await flowService.cancel(instanceId, reason || 'User cancelled', userId);
            return {
                ...flow,
                availableTransitions: [],
                activeTasks: []
            };
        }
    },
    Domain: {
        personas: async (parent: unknown) => {
            // parent.personas is array of strings (ids)
            if (!parent.personas) return [];
            const promises = parent.personas.map((id: string) => configService.getPersona(id));
            const results = await Promise.all(promises);
            return results.filter(Boolean);
        },
        capabilities: async (parent: unknown) => {
            // parent.capabilities is array of capability IDs
            // Fetch full capability definitions from Java backend
            if (!parent.capabilities) return [];

            interface CapabilityData {
                id: string;
                name?: string;
                description?: string;
                prerequisites?: string[];
            }

            const allCaps = await configService.getCapabilities();
            const capMap = new Map((allCaps.capabilities || []).map((c: CapabilityData) => [c.id, c]));

            return parent.capabilities.map((id: string) => {
                const cap = capMap.get(id) as CapabilityData | undefined;
                if (cap) {
                    return {
                        id: cap.id,
                        domainId: parent.id,
                        display: {
                            label: cap.name || id,
                            description: cap.description || `Capability for ${id}`,
                            icon: 'extension'
                        },
                        requiredPermissions: cap.prerequisites || [],
                        defaultWidgets: [],
                        defaultActions: []
                    };
                }
                // Fallback if not found in Java backend
                return {
                    id,
                    domainId: parent.id,
                    display: {
                        label: id.split('-').map((s: string) => s.charAt(0).toUpperCase() + s.slice(1)).join(' '),
                        description: `Capability for ${id}`,
                        icon: 'extension'
                    },
                    requiredPermissions: [],
                    defaultWidgets: [],
                    defaultActions: []
                };
            });
        }
    }
};
