import { apiClient } from './index';

/**
 * HITL (Human-in-the-Loop) and Agent Actions API (Day 5).
 *
 * <p><b>Purpose</b><br>
 * API methods for fetching pending agent actions, approving/rejecting decisions,
 * and managing incident escalations.
 *
 * <p><b>Endpoints</b><br>
 * - GET /agents/actions: Pending actions queue
 * - POST /agents/actions/:id/approve: Approve action
 * - POST /agents/actions/:id/reject: Reject action
 * - POST /agents/actions/:id/defer: Defer action
 *
 * @doc.type service
 * @doc.purpose HITL and Agent Actions API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export type ActionPriority = 'p0' | 'p1' | 'p2';
export type ActionStatus = 'pending' | 'approved' | 'rejected' | 'deferred' | 'expired';

export interface AgentAction {
    id: string;
    agentId: string;
    agentName: string;
    priority: ActionPriority;
    description: string;
    proposedAction: string;
    confidence: number;
    reasoning: string;
    affectedServices: string[];
    expectedImpact: string;
    riskLevel: 'low' | 'medium' | 'high';
    createdAt: string;
    status: ActionStatus;
    slaDeadline: string;
    incidentIds?: string[];
}

export interface ActionApprovalPayload {
    actionId: string;
    approvedBy: string;
    reason?: string;
}

export interface ActionRejectionPayload {
    actionId: string;
    rejectedBy: string;
    reason: string;
}

export interface ActionDeferralPayload {
    actionId: string;
    deferredBy: string;
    reason: string;
    deferUntil: string;
}

// Global mock flag for agents API, aligned with KPI and app-creator config.
// Note: Vite requires static access to import.meta.env properties for SSR compatibility
const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

// Simple mock queue used when mocks are enabled. This prevents any network
// calls to /agents when the Java backend is not running, while still allowing
// the HITL UI to function.
const mockPendingActions: AgentAction[] = [
    {
        id: 'act-1',
        agentId: 'deploy-bot',
        agentName: 'Deploy Bot',
        priority: 'p0',
        description: 'Production deployment requires approval',
        proposedAction: 'Deploy version 1.4.2 to production cluster',
        confidence: 0.94,
        reasoning: 'All checks passed, canary metrics healthy, error rate stable.',
        affectedServices: ['api-gateway', 'payments'],
        expectedImpact: 'Zero-downtime rollout with improved latency.',
        riskLevel: 'medium',
        createdAt: new Date().toISOString(),
        status: 'pending',
        slaDeadline: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        incidentIds: ['inc-1234'],
    },
    {
        id: 'act-2',
        agentId: 'sec-guardian',
        agentName: 'Security Guardian',
        priority: 'p1',
        description: 'Security rule tuning suggestion',
        proposedAction: 'Relax WAF rule for /healthz endpoint to reduce false positives.',
        confidence: 0.81,
        reasoning: 'High volume of false positives detected from internal health checks.',
        affectedServices: ['observability'],
        expectedImpact: 'Fewer noisy alerts without reducing real threat detection.',
        riskLevel: 'low',
        createdAt: new Date().toISOString(),
        status: 'pending',
        slaDeadline: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    },
];

export const agentsApi = {
    /**
     * Get pending agent actions (HITL queue)
     */
    async getPendingActions(params?: {
        priority?: ActionPriority | 'all';
        status?: ActionStatus;
        limit?: number;
    }) {
        if (USE_MOCKS) {
            const priorityFilter = params?.priority && params.priority !== 'all' ? params.priority : undefined;
            const limited = mockPendingActions.filter((action) =>
                priorityFilter ? action.priority === priorityFilter : true
            );
            return params?.limit ? limited.slice(0, params.limit) : limited;
        }

        const response = await apiClient.get<AgentAction[]>('/agents/actions', { params });
        return response.data;
    },

    /**
     * Get single action details
     */
    async getActionDetails(actionId: string) {
        if (USE_MOCKS) {
            return mockPendingActions.find((action) => action.id === actionId) ?? null;
        }

        const response = await apiClient.get<AgentAction>(`/agents/actions/${actionId}`);
        return response.data;
    },

    /**
     * Approve an agent action
     */
    async approveAction(payload: ActionApprovalPayload) {
        if (USE_MOCKS) {
            return { status: 'approved', actionId: payload.actionId };
        }

        const response = await apiClient.post(`/agents/actions/${payload.actionId}/approve`, payload);
        return response.data;
    },

    /**
     * Reject an agent action
     */
    async rejectAction(payload: ActionRejectionPayload) {
        if (USE_MOCKS) {
            return { status: 'rejected', actionId: payload.actionId };
        }

        const response = await apiClient.post(`/agents/actions/${payload.actionId}/reject`, payload);
        return response.data;
    },

    /**
     * Defer an agent action
     */
    async deferAction(payload: ActionDeferralPayload) {
        if (USE_MOCKS) {
            return { status: 'deferred', actionId: payload.actionId };
        }

        const response = await apiClient.post(`/agents/actions/${payload.actionId}/defer`, payload);
        return response.data;
    },

    /**
     * Get action history
     */
    async getActionHistory(limit: number = 10) {
        if (USE_MOCKS) {
            return mockPendingActions.slice(0, limit);
        }

        const response = await apiClient.get<AgentAction[]>('/agents/actions/history', {
            params: { limit },
        });
        return response.data;
    },
};
