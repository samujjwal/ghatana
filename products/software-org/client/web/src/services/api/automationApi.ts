/**
 * Workflow Automation API Client
 *
 * <p><b>Purpose</b><br>
 * Provides workflow automation features including task management, dependency handling,
 * conditional logic, and execution history tracking.
 *
 * <p><b>Features</b><br>
 * - Workflow template management
 * - Task execution and status tracking
 * - Conditional logic and branching
 * - Error handling and retry logic
 * - Execution history and audit trails
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const workflows = await automationApi.getWorkflows(tenantId);
 * const execution = await automationApi.executeWorkflow(workflowId, inputs);
 * const history = await automationApi.getExecutionHistory(workflowId);
 * ```
 *
 * @doc.type service
 * @doc.purpose Workflow automation API client
 * @doc.layer product
 * @doc.pattern API Client
 */

export interface WorkflowTask {
    id: string;
    name: string;
    type: 'action' | 'decision' | 'parallel' | 'delay';
    status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
    retryCount: number;
    maxRetries: number;
    timeout: number;
    dependencies: string[];
}

export interface WorkflowDefinition {
    id: string;
    name: string;
    description: string;
    version: number;
    tasks: WorkflowTask[];
    triggers: Array<{
        type: 'schedule' | 'event' | 'manual';
        config: Record<string, unknown>;
    }>;
    enabled: boolean;
    createdAt: Date;
    updatedAt: Date;
}

export interface WorkflowExecution {
    id: string;
    workflowId: string;
    status: 'pending' | 'running' | 'completed' | 'failed';
    progress: number;
    startTime: Date;
    endTime?: Date;
    duration?: number;
    inputs: Record<string, unknown>;
    outputs?: Record<string, unknown>;
    taskExecutions: Array<{
        taskId: string;
        status: string;
        result: unknown;
        error?: string;
    }>;
}

export interface AutomationTrigger {
    id: string;
    workflowId: string;
    type: 'schedule' | 'event' | 'webhook';
    config: Record<string, unknown>;
    lastTriggered?: Date;
    nextExecution?: Date;
    enabled: boolean;
}

/**
 * Get all workflow definitions for a tenant.
 * @param tenantId - Tenant identifier
 * @returns Array of workflow definitions
 */
export async function getWorkflows(tenantId: string): Promise<WorkflowDefinition[]> {
    const response = await fetch(`/api/v1/tenants/${tenantId}/workflows`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    if (!response.ok) throw new Error('Failed to fetch workflows');
    return response.json();
}

/**
 * Get detailed definition of a specific workflow.
 * @param workflowId - Workflow identifier
 * @returns WorkflowDefinition object
 */
export async function getWorkflow(workflowId: string): Promise<WorkflowDefinition> {
    const response = await fetch(`/api/v1/workflows/${workflowId}`);
    if (!response.ok) throw new Error('Failed to fetch workflow');
    return response.json();
}

/**
 * Create a new workflow definition.
 * @param tenantId - Tenant identifier
 * @param definition - Workflow definition with tasks and triggers
 * @returns Created WorkflowDefinition
 */
export async function createWorkflow(
    tenantId: string,
    definition: Omit<WorkflowDefinition, 'id' | 'createdAt' | 'updatedAt'>
): Promise<WorkflowDefinition> {
    const response = await fetch(`/api/v1/tenants/${tenantId}/workflows`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Tenant-ID': tenantId,
        },
        body: JSON.stringify(definition),
    });
    if (!response.ok) throw new Error('Failed to create workflow');
    return response.json();
}

/**
 * Update an existing workflow definition.
 * @param workflowId - Workflow identifier
 * @param updates - Partial workflow definition updates
 * @returns Updated WorkflowDefinition
 */
export async function updateWorkflow(
    workflowId: string,
    updates: Partial<WorkflowDefinition>
): Promise<WorkflowDefinition> {
    const response = await fetch(`/api/v1/workflows/${workflowId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates),
    });
    if (!response.ok) throw new Error('Failed to update workflow');
    return response.json();
}

/**
 * Execute a workflow with given inputs.
 * @param workflowId - Workflow identifier
 * @param inputs - Input parameters for workflow execution
 * @returns WorkflowExecution object
 */
export async function executeWorkflow(
    workflowId: string,
    inputs: Record<string, unknown>
): Promise<WorkflowExecution> {
    const response = await fetch(`/api/v1/workflows/${workflowId}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ inputs }),
    });
    if (!response.ok) throw new Error('Failed to execute workflow');
    return response.json();
}

/**
 * Get current execution status of a workflow run.
 * @param executionId - Execution identifier
 * @returns Current WorkflowExecution status
 */
export async function getExecutionStatus(executionId: string): Promise<WorkflowExecution> {
    const response = await fetch(`/api/v1/executions/${executionId}`);
    if (!response.ok) throw new Error('Failed to fetch execution status');
    return response.json();
}

/**
 * Cancel a running workflow execution.
 * @param executionId - Execution identifier
 * @returns Updated execution with status 'cancelled'
 */
export async function cancelExecution(executionId: string) {
    const response = await fetch(`/api/v1/executions/${executionId}/cancel`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to cancel execution');
    return response.json();
}

/**
 * Get execution history for a workflow with pagination.
 * @param workflowId - Workflow identifier
 * @param limit - Number of records to return
 * @param offset - Pagination offset
 * @returns Array of past executions
 */
export async function getExecutionHistory(
    workflowId: string,
    limit: number = 50,
    offset: number = 0
): Promise<WorkflowExecution[]> {
    const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
    const response = await fetch(`/api/v1/workflows/${workflowId}/executions?${params}`);
    if (!response.ok) throw new Error('Failed to fetch execution history');
    return response.json();
}

/**
 * Get execution details with task-level information.
 * @param executionId - Execution identifier
 * @returns Detailed WorkflowExecution with task results
 */
export async function getExecutionDetails(executionId: string): Promise<WorkflowExecution> {
    const response = await fetch(`/api/v1/executions/${executionId}/details`);
    if (!response.ok) throw new Error('Failed to fetch execution details');
    return response.json();
}

/**
 * Create or update an automation trigger for a workflow.
 * @param workflowId - Workflow identifier
 * @param trigger - Trigger configuration
 * @returns Created or updated AutomationTrigger
 */
export async function createTrigger(
    workflowId: string,
    trigger: Omit<AutomationTrigger, 'id' | 'workflowId'>
): Promise<AutomationTrigger> {
    const response = await fetch(`/api/v1/workflows/${workflowId}/triggers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(trigger),
    });
    if (!response.ok) throw new Error('Failed to create trigger');
    return response.json();
}

/**
 * Alias used by UI hooks: add a workflow trigger.
 */
export async function addWorkflowTrigger(
    workflowId: string,
    trigger: Omit<AutomationTrigger, 'id' | 'workflowId'>
): Promise<AutomationTrigger> {
    return createTrigger(workflowId, trigger);
}

/**
 * Update an existing trigger (alias expected by UI hooks).
 */
export async function updateWorkflowTrigger(triggerId: string, updates: Partial<AutomationTrigger>) {
    const response = await fetch(`/api/v1/triggers/${triggerId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates),
    });
    if (!response.ok) throw new Error('Failed to update trigger');
    return response.json();
}

/**
 * Alias used by UI hooks: remove a workflow trigger by id.
 */
export async function removeWorkflowTrigger(triggerId: string) {
    return deleteTrigger(triggerId);
}

/**
 * Get all triggers configured for a workflow.
 * @param workflowId - Workflow identifier
 * @returns Array of AutomationTrigger objects
 */
export async function getWorkflowTriggers(workflowId: string): Promise<AutomationTrigger[]> {
    const response = await fetch(`/api/v1/workflows/${workflowId}/triggers`);
    if (!response.ok) throw new Error('Failed to fetch triggers');
    return response.json();
}

/**
 * Delete a trigger from a workflow.
 * @param triggerId - Trigger identifier
 * @returns Deletion confirmation
 */
export async function deleteTrigger(triggerId: string) {
    const response = await fetch(`/api/v1/triggers/${triggerId}`, { method: 'DELETE' });
    if (!response.ok) throw new Error('Failed to delete trigger');
    return response.json();
}

/**
 * Get workflow execution statistics and metrics.
 * @param workflowId - Workflow identifier
 * @param timeRange - Time range for statistics
 * @returns Statistics object with success rate, avg duration, etc.
 */
export async function getWorkflowStats(
    workflowId: string,
    timeRange: { start: Date; end: Date }
) {
    const params = new URLSearchParams({
        startTime: timeRange.start.toISOString(),
        endTime: timeRange.end.toISOString(),
    });
    const response = await fetch(`/api/v1/workflows/${workflowId}/stats?${params}`);
    if (!response.ok) throw new Error('Failed to fetch workflow stats');
    return response.json();
}

export default {
    getWorkflows,
    getWorkflow,
    createWorkflow,
    updateWorkflow,
    executeWorkflow,
    getExecutionStatus,
    cancelExecution,
    getExecutionHistory,
    getExecutionDetails,
    createTrigger,
    getWorkflowTriggers,
    deleteTrigger,
    getWorkflowStats,
    addWorkflowTrigger,
    removeWorkflowTrigger,
    updateWorkflowTrigger,
};
