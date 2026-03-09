/**
 * Workflow Agent API Client
 *
 * HTTP client for interacting with the Java backend workflow agent endpoints.
 * Provides typed methods for executing, cancelling, and monitoring agent tasks.
 *
 * @doc.type class
 * @doc.purpose HTTP client for workflow agent API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type { DashboardApiConfig, ApiResponse } from './types';

// ============================================================================
// Types
// ============================================================================

/**
 * Workflow agent role types matching Java WorkflowAgentRole enum
 */
export type WorkflowAgentRole =
    | 'task-manager'
    | 'code-reviewer'
    | 'test-writer'
    | 'documentation'
    | 'security-scanner'
    | 'performance-optimizer'
    | 'release-manager'
    | 'incident-responder'
    | 'infrastructure'
    | 'compliance-auditor'
    | 'general';

/**
 * Execution priority levels
 */
export type ExecutionPriority = 'low' | 'normal' | 'high' | 'critical';

/**
 * Execution status
 */
export type ExecutionStatus = 'pending' | 'running' | 'success' | 'failed' | 'cancelled';

/**
 * Request to execute an agent task
 */
export interface ExecuteAgentRequest {
    agentId: string;
    role: WorkflowAgentRole;
    itemId: string;
    input?: Record<string, unknown>;
    priority?: ExecutionPriority;
}

/**
 * Batch execution request
 */
export interface ExecuteBatchRequest {
    requests: ExecuteAgentRequest[];
}

/**
 * Execution metrics
 */
export interface ExecutionMetrics {
    durationMs: number;
    tokensUsed: number;
    cost: number;
}

/**
 * Agent execution result
 */
export interface AgentExecutionResult {
    id: string;
    requestId: string;
    agentId: string;
    status: ExecutionStatus;
    output: Record<string, unknown>;
    confidence: number;
    error?: string;
    metrics?: ExecutionMetrics;
    startedAt: string;
    completedAt?: string;
}

/**
 * Batch execution response
 */
export interface BatchExecutionResponse {
    results: AgentExecutionResult[];
}

/**
 * Agent metadata
 */
export interface AgentInfo {
    id: string;
    name: string;
    role: WorkflowAgentRole;
    enabled: boolean;
    registeredAt: string;
}

/**
 * List agents response
 */
export interface ListAgentsResponse {
    agents: AgentInfo[];
}

/**
 * Agents by role response
 */
export interface AgentsByRoleResponse {
    role: WorkflowAgentRole;
    agents: AgentInfo[];
}

/**
 * Agent health information
 */
export interface AgentHealthInfo {
    agentId: string;
    status: 'healthy' | 'degraded' | 'unhealthy' | 'unknown';
    lastActive: string;
    successRate: number;
    avgResponseTimeMs: number;
}

/**
 * Execution status response
 */
export interface ExecutionStatusResponse {
    requestId: string;
    status: ExecutionStatus;
}

/**
 * Cancellation response
 */
export interface CancellationResponse {
    cancelled: boolean;
    requestId: string;
}

// ============================================================================
// Client Implementation
// ============================================================================

/**
 * Workflow Agent API Client
 *
 * Provides methods for:
 * - Executing agent tasks on work items
 * - Batch execution of multiple tasks
 * - Cancelling pending executions
 * - Monitoring execution status
 * - Listing registered agents
 * - Checking agent health
 */
export class WorkflowAgentApiClient extends BaseDashboardApiClient {
    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
    }

    // ========== Execution Operations ==========

    /**
     * Execute a single agent task
     *
     * @param request The execution request
     * @returns Promise of execution result
     */
    async executeAgent(request: ExecuteAgentRequest): Promise<ApiResponse<AgentExecutionResult>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                id: `result-${Date.now()}`,
                requestId: `req-${Date.now()}`,
                agentId: request.agentId,
                status: 'success' as ExecutionStatus,
                output: { message: 'Mock execution result' },
                confidence: 0.95,
                metrics: { durationMs: 1000, tokensUsed: 500, cost: 0.01 },
                startedAt: new Date().toISOString(),
                completedAt: new Date().toISOString(),
            });
        }

        return this.post<AgentExecutionResult>('/agents/execute', request);
    }

    /**
     * Execute multiple agent tasks in batch
     *
     * @param requests The batch execution requests
     * @returns Promise of batch results
     */
    async executeBatch(requests: ExecuteAgentRequest[]): Promise<ApiResponse<BatchExecutionResponse>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                results: requests.map((req, i) => ({
                    id: `result-${Date.now()}-${i}`,
                    requestId: `req-${Date.now()}-${i}`,
                    agentId: req.agentId,
                    status: 'success' as ExecutionStatus,
                    output: { message: 'Mock batch result' },
                    confidence: 0.9,
                    metrics: { durationMs: 800, tokensUsed: 400, cost: 0.008 },
                    startedAt: new Date().toISOString(),
                    completedAt: new Date().toISOString(),
                })),
            });
        }

        return this.post<BatchExecutionResponse>('/agents/execute/batch', { requests });
    }

    /**
     * Cancel a pending execution
     *
     * @param requestId The execution request ID to cancel
     * @returns Promise of cancellation result
     */
    async cancelExecution(requestId: string): Promise<ApiResponse<CancellationResponse>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                cancelled: true,
                requestId,
            });
        }

        return this.delete<CancellationResponse>(`/agents/execute/${requestId}`);
    }

    /**
     * Get execution status
     *
     * @param requestId The execution request ID
     * @returns Promise of execution status
     */
    async getExecutionStatus(requestId: string): Promise<ApiResponse<ExecutionStatusResponse>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                requestId,
                status: 'success' as ExecutionStatus,
            });
        }

        return this.get<ExecutionStatusResponse>(`/agents/execute/${requestId}`);
    }

    // ========== Agent Management ==========

    /**
     * List all registered agents
     *
     * @returns Promise of agents list
     */
    async listAgents(): Promise<ApiResponse<ListAgentsResponse>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                agents: [
                    { id: 'agent-1', name: 'Code Reviewer', role: 'code-reviewer' as WorkflowAgentRole, enabled: true, registeredAt: new Date().toISOString() },
                    { id: 'agent-2', name: 'Test Writer', role: 'test-writer' as WorkflowAgentRole, enabled: true, registeredAt: new Date().toISOString() },
                    { id: 'agent-3', name: 'Security Scanner', role: 'security-scanner' as WorkflowAgentRole, enabled: true, registeredAt: new Date().toISOString() },
                ],
            });
        }

        return this.get<ListAgentsResponse>('/agents');
    }

    /**
     * Get agents by role
     *
     * @param role The agent role to filter by
     * @returns Promise of agents for the role
     */
    async getAgentsByRole(role: WorkflowAgentRole): Promise<ApiResponse<AgentsByRoleResponse>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                role,
                agents: [
                    { id: `agent-${role}`, name: `${role} Agent`, role, enabled: true, registeredAt: new Date().toISOString() },
                ],
            });
        }

        return this.get<AgentsByRoleResponse>(`/agents/role/${role}`);
    }

    /**
     * Get agent health information
     *
     * @param agentId The agent ID
     * @returns Promise of health info
     */
    async getAgentHealth(agentId: string): Promise<ApiResponse<AgentHealthInfo>> {
        if (this.mode === 'mock') {
            return this.createMockResponse({
                agentId,
                status: 'healthy' as const,
                lastActive: new Date().toISOString(),
                successRate: 0.95,
                avgResponseTimeMs: 1200,
            });
        }

        return this.get<AgentHealthInfo>(`/agents/${agentId}/health`);
    }

    // ========== Helper Methods ==========

    /**
     * Create a mock response with proper ApiResponse structure
     */
    private createMockResponse<T>(data: T): ApiResponse<T> {
        return {
            success: true,
            data,
            timestamp: new Date().toISOString(),
        };
    }
}
