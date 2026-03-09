/**
 * Java Service Client
 *
 * @doc.type module
 * @doc.purpose HTTP client for calling Java core services (ApprovalEngine, etc.)
 * @doc.layer infrastructure
 * @doc.pattern Client Adapter
 *
 * This client bridges the TypeScript API layer with the Java core services.
 * It handles HTTP communication, serialization, and error handling.
 */

interface ApprovalRequest {
    requestId: string;
    type: 'TIME_OFF' | 'EXPENSE' | 'HIRE' | 'PROMOTION' | 'PURCHASE';
    requesterId: string;
    approvers: string[];
    metadata: Record<string, unknown>;
}

interface ApprovalDecision {
    approverId: string;
    approved: boolean;
    comments?: string;
}

interface ApprovalStatus {
    requestId: string;
    status: 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';
}

interface PendingApprovals {
    approverId: string;
    pending: ApprovalRequest[];
    count: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function getErrorField(payload: unknown): string | undefined {
    if (!isRecord(payload)) return undefined;
    const err = payload.error;
    return typeof err === 'string' ? err : undefined;
}

function isApprovalType(value: unknown): value is ApprovalRequest['type'] {
    return value === 'TIME_OFF' || value === 'EXPENSE' || value === 'HIRE' || value === 'PROMOTION' || value === 'PURCHASE';
}

function isApprovalRequest(payload: unknown): payload is ApprovalRequest {
    if (!isRecord(payload)) return false;
    return (
        typeof payload.requestId === 'string' &&
        isApprovalType(payload.type) &&
        typeof payload.requesterId === 'string' &&
        Array.isArray(payload.approvers) &&
        payload.approvers.every((a) => typeof a === 'string') &&
        isRecord(payload.metadata)
    );
}

function isPendingApprovals(payload: unknown): payload is PendingApprovals {
    if (!isRecord(payload)) return false;
    return (
        typeof payload.approverId === 'string' &&
        Array.isArray(payload.pending) &&
        payload.pending.every(isApprovalRequest) &&
        typeof payload.count === 'number'
    );
}

function isApprovalStatus(payload: unknown): payload is ApprovalStatus {
    if (!isRecord(payload)) return false;
    return (
        typeof payload.requestId === 'string' &&
        (payload.status === 'PENDING' || payload.status === 'IN_PROGRESS' || payload.status === 'APPROVED' || payload.status === 'REJECTED')
    );
}

/**
 * Client for interacting with Java approval service.
 */
export class JavaServiceClient {
    private baseUrl: string;

    constructor(baseUrl: string = process.env.JAVA_SERVICE_URL || 'http://localhost:8080') {
        this.baseUrl = baseUrl;
    }

    /**
     * Submit a new approval request to the Java approval engine.
     */
    async submitApproval(request: ApprovalRequest): Promise<{ requestId: string; status: string }> {
        const response = await fetch(`${this.baseUrl}/api/v1/approvals`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        });

        if (!response.ok) {
            const errorPayload: unknown = await response.json().catch(() => ({ error: 'Unknown error' }) as unknown);
            throw new Error(`Failed to submit approval: ${getErrorField(errorPayload) || response.statusText}`);
        }

        const payload: unknown = await response.json();
        if (!isRecord(payload) || typeof payload.requestId !== 'string' || typeof payload.status !== 'string') {
            throw new Error('Invalid response from Java approval service (submitApproval)');
        }
        return { requestId: payload.requestId, status: payload.status };
    }

    /**
     * Get approval request details by ID.
     */
    async getApproval(requestId: string): Promise<ApprovalRequest | null> {
        const response = await fetch(`${this.baseUrl}/api/v1/approvals/${requestId}`);

        if (response.status === 404) {
            return null;
        }

        if (!response.ok) {
            const errorPayload: unknown = await response.json().catch(() => ({ error: 'Unknown error' }) as unknown);
            throw new Error(`Failed to get approval: ${getErrorField(errorPayload) || response.statusText}`);
        }

        const payload: unknown = await response.json();
        if (!isApprovalRequest(payload)) {
            throw new Error('Invalid response from Java approval service (getApproval)');
        }
        return payload;
    }

    /**
     * Record an approval or rejection decision.
     */
    async recordDecision(requestId: string, decision: ApprovalDecision): Promise<ApprovalRequest> {
        const response = await fetch(`${this.baseUrl}/api/v1/approvals/${requestId}/decision`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(decision),
        });

        if (!response.ok) {
            const errorPayload: unknown = await response.json().catch(() => ({ error: 'Unknown error' }) as unknown);
            throw new Error(`Failed to record decision: ${getErrorField(errorPayload) || response.statusText}`);
        }

        const payload: unknown = await response.json();
        if (!isApprovalRequest(payload)) {
            throw new Error('Invalid response from Java approval service (recordDecision)');
        }
        return payload;
    }

    /**
     * Get pending approvals for a specific approver.
     */
    async getPendingApprovals(approverId: string): Promise<PendingApprovals> {
        const response = await fetch(`${this.baseUrl}/api/v1/approvals/pending/${approverId}`);

        if (!response.ok) {
            const errorPayload: unknown = await response.json().catch(() => ({ error: 'Unknown error' }) as unknown);
            throw new Error(`Failed to get pending approvals: ${getErrorField(errorPayload) || response.statusText}`);
        }

        const payload: unknown = await response.json();
        if (!isPendingApprovals(payload)) {
            throw new Error('Invalid response from Java approval service (getPendingApprovals)');
        }
        return payload;
    }

    /**
     * Get approval status by request ID.
     */
    async getApprovalStatus(requestId: string): Promise<ApprovalStatus | null> {
        const response = await fetch(`${this.baseUrl}/api/v1/approvals/status/${requestId}`);

        if (response.status === 404) {
            return null;
        }

        if (!response.ok) {
            const errorPayload: unknown = await response.json().catch(() => ({ error: 'Unknown error' }) as unknown);
            throw new Error(`Failed to get approval status: ${getErrorField(errorPayload) || response.statusText}`);
        }

        const payload: unknown = await response.json();
        if (!isApprovalStatus(payload)) {
            throw new Error('Invalid response from Java approval service (getApprovalStatus)');
        }
        return payload;
    }

    /**
     * Health check for Java service.
     */
    async healthCheck(): Promise<boolean> {
        try {
            const response = await fetch(`${this.baseUrl}/health`, {
                method: 'GET',
                signal: AbortSignal.timeout(5000), // 5 second timeout
            });
            return response.ok;
        } catch {
            return false;
        }
    }
}

// Singleton instance
let javaServiceClient: JavaServiceClient | null = null;

/**
 * Get or create the Java service client singleton.
 */
export function getJavaServiceClient(): JavaServiceClient {
    if (!javaServiceClient) {
        javaServiceClient = new JavaServiceClient();
    }
    return javaServiceClient;
}
