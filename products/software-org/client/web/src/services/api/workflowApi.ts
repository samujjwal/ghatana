/**
 * Workflow API client.
 */

import { WorkflowNode } from '@/features/workflows/components/WorkflowNodeDetail';

/**
 * Workflow interface.
 */
export interface Workflow {
    id: string;
    name: string;
    description?: string;
    status: 'active' | 'paused' | 'archived';
    createdAt: Date;
    updatedAt: Date;
    executionCount: number;
    successRate: number;
}

/**
 * Workflow execution interface.
 */
export interface WorkflowExecution {
    id: string;
    workflowId: string;
    startedAt: Date;
    completedAt?: Date;
    status: 'running' | 'completed' | 'failed';
    nodeExecutions: Array<{
        nodeId: string;
        nodeName: string;
        status: 'idle' | 'running' | 'completed' | 'failed';
        startedAt: Date;
        completedAt?: Date;
        executionTime?: number;
    }>;
}

/**
 * Workflow API client.
 *
 * <p><b>Purpose</b><br>
 * Provides workflow data retrieval and management operations.
 *
 * <p><b>Methods</b><br>
 * - getWorkflows: Get all workflows
 * - getWorkflowById: Get workflow details
 * - getWorkflowExecutions: Get execution history
 * - getWorkflowNodes: Get workflow node DAG
 * - getNodeDetails: Get node detailed information
 *
 * @doc.type service
 * @doc.purpose Workflow API operations
 * @doc.layer product
 * @doc.pattern API Client
 */
export const workflowApi = {
    /**
     * Get all workflows.
     *
     * @param tenantId - Tenant ID
     * @returns Promise resolving to workflow array
     */
    async getWorkflows(tenantId: string): Promise<Workflow[]> {
        try {
            const response = await fetch(`/api/v1/workflows?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[workflowApi.getWorkflows] API unavailable:', error);
            return [];
        }
    },

    /**
     * Get workflow by ID.
     *
     * @param workflowId - Workflow ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to workflow details
     */
    async getWorkflowById(workflowId: string, tenantId: string): Promise<Workflow | null> {
        try {
            const response = await fetch(`/api/v1/workflows/${workflowId}?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[workflowApi.getWorkflowById] API unavailable:', error);
            return null;
        }
    },

    /**
     * Get workflow execution history.
     *
     * @param workflowId - Workflow ID
     * @param tenantId - Tenant ID
     * @param limit - Maximum results
     * @returns Promise resolving to execution array
     */
    async getWorkflowExecutions(
        workflowId: string,
        tenantId: string,
        limit: number = 50
    ): Promise<WorkflowExecution[]> {
        try {
            const response = await fetch(
                `/api/v1/workflows/${workflowId}/executions?tenantId=${tenantId}&limit=${limit}`
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[workflowApi.getWorkflowExecutions] API unavailable:', error);
            return [];
        }
    },

    /**
     * Get workflow node DAG.
     *
     * @param workflowId - Workflow ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to nodes and edges
     */
    async getWorkflowNodes(
        workflowId: string,
        tenantId: string
    ): Promise<{
        nodes: WorkflowNode[];
        edges: Array<{ from: string; to: string }>;
    }> {
        try {
            const response = await fetch(`/api/v1/workflows/${workflowId}/nodes?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[workflowApi.getWorkflowNodes] API unavailable:', error);
            return { nodes: [], edges: [] };
        }
    },

    /**
     * Get node detailed information including inputs/outputs/execution.
     *
     * @param workflowId - Workflow ID
     * @param nodeId - Node ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to node details
     */
    async getNodeDetails(workflowId: string, nodeId: string, tenantId: string): Promise<{
        node: WorkflowNode;
        inputs: Array<{ name: string; type: string; value: unknown }>;
        outputs: Array<{ name: string; type: string; value: unknown }>;
        lastExecution?: {
            startedAt: Date;
            completedAt?: Date;
            duration: number;
            status: string;
        };
    } | null> {
        try {
            const response = await fetch(
                `/api/v1/workflows/${workflowId}/nodes/${nodeId}?tenantId=${tenantId}`
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[workflowApi.getNodeDetails] API unavailable:', error);
            return null;
        }
    },
};

export default workflowApi;
