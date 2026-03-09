/**
 * HITL API Client
 *
 * <p><b>Purpose</b><br>
 * Client for interacting with the Human-in-the-Loop (HITL) API backend.
 * Provides type-safe methods for all HITL operations.
 *
 * <p><b>Features</b><br>
 * - Submit actions for approval
 * - Approve/reject actions
 * - Get action status
 * - List pending actions
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const result = await hitlApi.submitAction(action);
 * const status = await hitlApi.getActionStatus(actionId);
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose HITL API client
 * @doc.layer product
 * @doc.pattern Service
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface HitlAction {
    agentId: string;
    actionType: string;
    description: string;
    confidence: number;
    context: Record<string, any>;
}

export interface HitlResult {
    actionId: string;
    status: string;
    message: string;
    requiresApproval: boolean;
}

export interface ActionStatus {
    actionId: string;
    state: string;
    submittedAt: string;
    submittedBy: string;
    decidedAt?: string;
    decidedBy?: string;
    decision?: string;
    comment?: string;
}

export interface ApprovalRequest {
    approverId: string;
    comment?: string;
}

export interface RejectionRequest {
    rejectorId: string;
    reason: string;
}

class HitlApiClient {
    private baseUrl: string;

    constructor(baseUrl: string = API_BASE_URL) {
        this.baseUrl = baseUrl;
    }

    /**
     * Submit an action for approval
     */
    async submitAction(action: HitlAction): Promise<HitlResult> {
        const response = await fetch(`${this.baseUrl}/api/v1/hitl/actions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(action),
        });
        if (!response.ok) {
            throw new Error(`Failed to submit action: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * Get action status
     */
    async getActionStatus(actionId: string): Promise<ActionStatus> {
        const response = await fetch(`${this.baseUrl}/api/v1/hitl/actions/${actionId}`);
        if (!response.ok) {
            throw new Error(`Failed to get action status: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * Approve an action
     */
    async approveAction(actionId: string, request: ApprovalRequest): Promise<HitlResult> {
        const response = await fetch(`${this.baseUrl}/api/v1/hitl/actions/${actionId}/approve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            throw new Error(`Failed to approve action: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * Reject an action
     */
    async rejectAction(actionId: string, request: RejectionRequest): Promise<HitlResult> {
        const response = await fetch(`${this.baseUrl}/api/v1/hitl/actions/${actionId}/reject`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            throw new Error(`Failed to reject action: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * List pending actions
     */
    async listPendingActions(): Promise<ActionStatus[]> {
        const response = await fetch(`${this.baseUrl}/api/v1/hitl/pending`);
        if (!response.ok) {
            throw new Error(`Failed to list pending actions: ${response.statusText}`);
        }
        return response.json();
    }
}

export const hitlApi = new HitlApiClient();
export default hitlApi;
