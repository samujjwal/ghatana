/**
 * YAPPC Task Execution Coordinator
 *
 * Bridges the task system with the agent orchestrator.
 * Handles task dispatch, input validation, output validation,
 * error handling, and audit trail creation.
 *
 * @module workflow/taskExecutionCoordinator
 */

import type {
    TaskDefinition,
    TaskExecution,
    TaskDomainId,
    AuditArtifactType,
    LifecycleStage,
} from '@ghatana/types/tasks';

// ============================================================================
// Types
// ============================================================================

/**
 * Execution request for a task
 */
export interface TaskExecutionRequest {
    taskId: string;
    input: Record<string, unknown>;
    userId: string;
    workflowInstanceId?: string;
    stepId?: string;
    priority?: ExecutionPriority;
    metadata?: Record<string, unknown>;
}

/**
 * Execution priority levels
 */
export type ExecutionPriority = 'low' | 'normal' | 'high' | 'critical';

/**
 * Execution mode
 */
export type ExecutionMode = 'sync' | 'async' | 'batch';

/**
 * Agent mapping configuration
 */
export interface AgentMapping {
    domainMappings: Map<TaskDomainId, DomainAgentConfig>;
    taskOverrides: Map<string, TaskAgentConfig>;
    agents: Map<string, AgentConfig>;
}

/**
 * Domain-level agent configuration
 */
export interface DomainAgentConfig {
    primaryAgent: string;
    fallbackAgents: string[];
    executionMode: ExecutionMode;
}

/**
 * Task-specific agent configuration override
 */
export interface TaskAgentConfig {
    primaryAgent: string;
    capabilityRequirements?: string[];
    executionMode?: ExecutionMode;
    priority?: ExecutionPriority;
}

/**
 * Agent configuration
 */
export interface AgentConfig {
    id: string;
    name: string;
    capabilities: string[];
    status: 'active' | 'inactive' | 'busy';
    maxConcurrent: number;
    currentLoad: number;
}

/**
 * Execution result
 */
export interface ExecutionResult {
    success: boolean;
    executionId: string;
    taskId: string;
    status: TaskExecution['status'];
    output?: Record<string, unknown>;
    artifacts?: ExecutionArtifact[];
    error?: ExecutionError;
    metrics: ExecutionMetrics;
}

/**
 * Execution artifact
 */
export interface ExecutionArtifact {
    type: AuditArtifactType;
    content?: string;
    path?: string;
    timestamp: Date;
    metadata?: Record<string, unknown>;
}

/**
 * Execution error
 */
export interface ExecutionError {
    code: string;
    message: string;
    details?: unknown;
    retryable: boolean;
}

/**
 * Execution metrics
 */
export interface ExecutionMetrics {
    startedAt: Date;
    completedAt?: Date;
    durationMs?: number;
    agentId: string;
    retryCount: number;
    inputValidationMs: number;
    agentExecutionMs: number;
    outputValidationMs: number;
}

/**
 * Validation result
 */
export interface ValidationResult {
    valid: boolean;
    errors: ValidationError[];
}

/**
 * Validation error
 */
export interface ValidationError {
    field: string;
    message: string;
    code: string;
}

/**
 * Task registry interface (for dependency injection)
 */
export interface TaskRegistry {
    getTask(taskId: string): TaskDefinition | undefined;
}

/**
 * Agent orchestrator interface (for dependency injection)
 */
export interface AgentOrchestrator {
    executeTask(
        agentId: string,
        taskId: string,
        input: Record<string, unknown>,
        options?: { timeout?: number; priority?: string },
    ): Promise<AgentExecutionResult>;

    getAgentStatus(agentId: string): AgentStatus | undefined;
}

/**
 * Agent execution result from orchestrator
 */
export interface AgentExecutionResult {
    success: boolean;
    output?: Record<string, unknown>;
    error?: { code: string; message: string };
    artifacts?: Array<{ type: string; content?: string; path?: string }>;
}

/**
 * Agent status
 */
export interface AgentStatus {
    id: string;
    status: 'idle' | 'busy' | 'error';
    currentTasks: number;
    maxTasks: number;
}

/**
 * Execution event for audit trail
 */
export interface ExecutionEvent {
    id: string;
    executionId: string;
    type: ExecutionEventType;
    timestamp: Date;
    data: Record<string, unknown>;
}

/**
 * Execution event types
 */
export type ExecutionEventType =
    | 'execution_started'
    | 'input_validated'
    | 'agent_selected'
    | 'agent_execution_started'
    | 'agent_execution_completed'
    | 'output_validated'
    | 'artifacts_created'
    | 'execution_completed'
    | 'execution_failed'
    | 'execution_retried';

// ============================================================================
// Task Execution Coordinator
// ============================================================================

/**
 * Task Execution Coordinator
 *
 * Orchestrates task execution by:
 * 1. Validating input against task schema
 * 2. Selecting appropriate agent based on mappings
 * 3. Dispatching to agent orchestrator
 * 4. Validating output against task schema
 * 5. Creating audit artifacts
 * 6. Handling errors and retries
 */
export class TaskExecutionCoordinator {
    private taskRegistry: TaskRegistry;
    private agentOrchestrator: AgentOrchestrator;
    private agentMapping: AgentMapping;
    private activeExecutions: Map<string, TaskExecution>;
    private executionEvents: Map<string, ExecutionEvent[]>;

    constructor(
        taskRegistry: TaskRegistry,
        agentOrchestrator: AgentOrchestrator,
        agentMapping: AgentMapping,
    ) {
        this.taskRegistry = taskRegistry;
        this.agentOrchestrator = agentOrchestrator;
        this.agentMapping = agentMapping;
        this.activeExecutions = new Map();
        this.executionEvents = new Map();
    }

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Execute a task
     */
    async execute(request: TaskExecutionRequest): Promise<ExecutionResult> {
        const executionId = this.generateExecutionId();
        const startedAt = new Date();

        // Initialize metrics
        const metrics: ExecutionMetrics = {
            startedAt,
            agentId: '',
            retryCount: 0,
            inputValidationMs: 0,
            agentExecutionMs: 0,
            outputValidationMs: 0,
        };

        try {
            // Get task definition
            const task = this.taskRegistry.getTask(request.taskId);
            if (!task) {
                return this.createErrorResult(
                    executionId,
                    request.taskId,
                    {
                        code: 'TASK_NOT_FOUND',
                        message: `Task not found: ${request.taskId}`,
                        retryable: false,
                    },
                    metrics,
                );
            }

            // Create execution record
            const execution: TaskExecution = {
                id: executionId,
                taskId: request.taskId,
                status: 'pending',
                input: request.input,
                startedAt,
                userId: request.userId,
                workflowInstanceId: request.workflowInstanceId,
            };
            this.activeExecutions.set(executionId, execution);

            // Emit started event
            this.emitEvent(executionId, 'execution_started', {
                taskId: request.taskId,
                userId: request.userId,
            });

            // Step 1: Validate input
            const inputValidationStart = Date.now();
            const inputValidation = this.validateInput(task, request.input);
            metrics.inputValidationMs = Date.now() - inputValidationStart;

            if (!inputValidation.valid) {
                this.emitEvent(executionId, 'execution_failed', {
                    reason: 'input_validation',
                    errors: inputValidation.errors,
                });
                return this.createErrorResult(
                    executionId,
                    request.taskId,
                    {
                        code: 'INPUT_VALIDATION_FAILED',
                        message: 'Input validation failed',
                        details: inputValidation.errors,
                        retryable: false,
                    },
                    metrics,
                );
            }
            this.emitEvent(executionId, 'input_validated', {});

            // Step 2: Select agent
            const agentId = this.selectAgent(task, request.priority);
            if (!agentId) {
                return this.createErrorResult(
                    executionId,
                    request.taskId,
                    {
                        code: 'NO_AGENT_AVAILABLE',
                        message: 'No agent available for this task',
                        retryable: true,
                    },
                    metrics,
                );
            }
            metrics.agentId = agentId;
            this.emitEvent(executionId, 'agent_selected', { agentId });

            // Step 3: Execute with agent
            const agentExecutionStart = Date.now();
            this.emitEvent(executionId, 'agent_execution_started', { agentId });

            const agentResult = await this.executeWithAgent(
                agentId,
                task,
                request.input,
                request.priority,
            );
            metrics.agentExecutionMs = Date.now() - agentExecutionStart;

            if (!agentResult.success) {
                this.emitEvent(executionId, 'execution_failed', {
                    reason: 'agent_execution',
                    error: agentResult.error,
                });
                return this.createErrorResult(
                    executionId,
                    request.taskId,
                    {
                        code: agentResult.error?.code || 'AGENT_EXECUTION_FAILED',
                        message: agentResult.error?.message || 'Agent execution failed',
                        retryable: true,
                    },
                    metrics,
                );
            }
            this.emitEvent(executionId, 'agent_execution_completed', {
                hasOutput: !!agentResult.output,
            });

            // Step 4: Validate output
            const outputValidationStart = Date.now();
            const outputValidation = this.validateOutput(task, agentResult.output || {});
            metrics.outputValidationMs = Date.now() - outputValidationStart;

            // Output validation failures are warnings, not errors
            if (!outputValidation.valid) {
                this.emitEvent(executionId, 'output_validated', {
                    valid: false,
                    errors: outputValidation.errors,
                });
            } else {
                this.emitEvent(executionId, 'output_validated', { valid: true });
            }

            // Step 5: Create artifacts
            const artifacts = this.createArtifacts(
                task,
                request.input,
                agentResult.output || {},
                agentResult.artifacts,
            );
            this.emitEvent(executionId, 'artifacts_created', {
                count: artifacts.length,
            });

            // Complete execution
            const completedAt = new Date();
            metrics.completedAt = completedAt;
            metrics.durationMs = completedAt.getTime() - startedAt.getTime();

            execution.status = 'completed';
            execution.output = agentResult.output;
            execution.completedAt = completedAt;
            execution.artifacts = artifacts;

            this.emitEvent(executionId, 'execution_completed', {
                durationMs: metrics.durationMs,
            });

            return {
                success: true,
                executionId,
                taskId: request.taskId,
                status: 'completed',
                output: agentResult.output,
                artifacts,
                metrics,
            };
        } catch (error) {
            this.emitEvent(executionId, 'execution_failed', {
                reason: 'unexpected_error',
                error: error instanceof Error ? error.message : 'Unknown error',
            });

            return this.createErrorResult(
                executionId,
                request.taskId,
                {
                    code: 'UNEXPECTED_ERROR',
                    message: error instanceof Error ? error.message : 'Unexpected error',
                    retryable: true,
                },
                metrics,
            );
        }
    }

    /**
     * Get execution status
     */
    getExecutionStatus(executionId: string): TaskExecution | undefined {
        return this.activeExecutions.get(executionId);
    }

    /**
     * Get execution events (audit trail)
     */
    getExecutionEvents(executionId: string): ExecutionEvent[] {
        return this.executionEvents.get(executionId) || [];
    }

    /**
     * Cancel an execution
     */
    async cancelExecution(executionId: string): Promise<boolean> {
        const execution = this.activeExecutions.get(executionId);
        if (!execution) return false;

        if (execution.status === 'completed' || execution.status === 'failed') {
            return false;
        }

        execution.status = 'cancelled';
        this.emitEvent(executionId, 'execution_failed', {
            reason: 'cancelled',
        });

        return true;
    }

    // ==========================================================================
    // Private Methods
    // ==========================================================================

    /**
     * Validate input against task schema
     */
    private validateInput(
        task: TaskDefinition,
        input: Record<string, unknown>,
    ): ValidationResult {
        const errors: ValidationError[] = [];
        const schema = task.inputSchema;

        if (!schema || typeof schema !== 'object') {
            return { valid: true, errors: [] };
        }

        const properties = (schema as { properties?: Record<string, unknown> }).properties || {};
        const required = (schema as { required?: string[] }).required || [];

        // Check required fields
        for (const field of required) {
            if (input[field] === undefined || input[field] === null) {
                errors.push({
                    field,
                    message: `Required field '${field}' is missing`,
                    code: 'REQUIRED_FIELD_MISSING',
                });
            }
        }

        // Check field types (basic validation)
        for (const [field, value] of Object.entries(input)) {
            const fieldSchema = properties[field] as { type?: string } | undefined;
            if (fieldSchema && fieldSchema.type) {
                const expectedType = fieldSchema.type;
                const actualType = Array.isArray(value) ? 'array' : typeof value;

                if (expectedType === 'integer' && typeof value === 'number') {
                    if (!Number.isInteger(value)) {
                        errors.push({
                            field,
                            message: `Field '${field}' must be an integer`,
                            code: 'INVALID_TYPE',
                        });
                    }
                } else if (expectedType !== 'integer' && actualType !== expectedType) {
                    errors.push({
                        field,
                        message: `Field '${field}' expected ${expectedType}, got ${actualType}`,
                        code: 'INVALID_TYPE',
                    });
                }
            }
        }

        return { valid: errors.length === 0, errors };
    }

    /**
     * Validate output against task schema
     */
    private validateOutput(
        task: TaskDefinition,
        output: Record<string, unknown>,
    ): ValidationResult {
        // Similar to input validation
        const errors: ValidationError[] = [];
        const schema = task.outputSchema;

        if (!schema || typeof schema !== 'object') {
            return { valid: true, errors: [] };
        }

        const properties = (schema as { properties?: Record<string, unknown> }).properties || {};
        const required = (schema as { required?: string[] }).required || [];

        for (const field of required) {
            if (output[field] === undefined) {
                errors.push({
                    field,
                    message: `Required output field '${field}' is missing`,
                    code: 'REQUIRED_FIELD_MISSING',
                });
            }
        }

        return { valid: errors.length === 0, errors };
    }

    /**
     * Select appropriate agent for task
     */
    private selectAgent(
        task: TaskDefinition,
        priority?: ExecutionPriority,
    ): string | undefined {
        // Check for task-specific override
        const taskOverride = this.agentMapping.taskOverrides.get(task.id);
        if (taskOverride) {
            const agent = this.agentMapping.agents.get(taskOverride.primaryAgent);
            if (agent && this.isAgentAvailable(agent)) {
                return agent.id;
            }
        }

        // Get domain mapping
        const domainConfig = this.agentMapping.domainMappings.get(task.domain);
        if (!domainConfig) {
            return undefined;
        }

        // Try primary agent
        const primaryAgent = this.agentMapping.agents.get(domainConfig.primaryAgent);
        if (primaryAgent && this.isAgentAvailable(primaryAgent)) {
            return primaryAgent.id;
        }

        // Try fallback agents
        for (const fallbackId of domainConfig.fallbackAgents) {
            const fallbackAgent = this.agentMapping.agents.get(fallbackId);
            if (fallbackAgent && this.isAgentAvailable(fallbackAgent)) {
                return fallbackAgent.id;
            }
        }

        return undefined;
    }

    /**
     * Check if agent is available
     */
    private isAgentAvailable(agent: AgentConfig): boolean {
        if (agent.status !== 'active') return false;
        if (agent.currentLoad >= agent.maxConcurrent) return false;
        return true;
    }

    /**
     * Execute task with selected agent
     */
    private async executeWithAgent(
        agentId: string,
        task: TaskDefinition,
        input: Record<string, unknown>,
        priority?: ExecutionPriority,
    ): Promise<AgentExecutionResult> {
        const timeout = this.getTimeout(task, priority);

        return this.agentOrchestrator.executeTask(agentId, task.id, input, {
            timeout,
            priority,
        });
    }

    /**
     * Get execution timeout based on task and priority
     */
    private getTimeout(task: TaskDefinition, priority?: ExecutionPriority): number {
        const baseTimeout = 30000; // 30 seconds default

        // Higher priority = longer timeout
        const priorityMultiplier: Record<ExecutionPriority, number> = {
            low: 0.5,
            normal: 1,
            high: 2,
            critical: 5,
        };

        return baseTimeout * (priorityMultiplier[priority || 'normal'] || 1);
    }

    /**
     * Create audit artifacts
     */
    private createArtifacts(
        task: TaskDefinition,
        input: Record<string, unknown>,
        output: Record<string, unknown>,
        agentArtifacts?: Array<{ type: string; content?: string; path?: string }>,
    ): ExecutionArtifact[] {
        const artifacts: ExecutionArtifact[] = [];
        const now = new Date();

        // Input snapshot
        artifacts.push({
            type: 'InputSnapshot',
            content: JSON.stringify(input),
            timestamp: now,
        });

        // Output snapshot
        artifacts.push({
            type: 'OutputSnapshot',
            content: JSON.stringify(output),
            timestamp: now,
        });

        // Add agent-generated artifacts
        if (agentArtifacts) {
            for (const artifact of agentArtifacts) {
                artifacts.push({
                    type: artifact.type as AuditArtifactType,
                    content: artifact.content,
                    path: artifact.path,
                    timestamp: now,
                });
            }
        }

        return artifacts;
    }

    /**
     * Create error result
     */
    private createErrorResult(
        executionId: string,
        taskId: string,
        error: ExecutionError,
        metrics: ExecutionMetrics,
    ): ExecutionResult {
        const execution = this.activeExecutions.get(executionId);
        if (execution) {
            execution.status = 'failed';
        }

        metrics.completedAt = new Date();
        metrics.durationMs = metrics.completedAt.getTime() - metrics.startedAt.getTime();

        return {
            success: false,
            executionId,
            taskId,
            status: 'failed',
            error,
            metrics,
        };
    }

    /**
     * Generate unique execution ID
     */
    private generateExecutionId(): string {
        return `exec-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }

    /**
     * Emit execution event
     */
    private emitEvent(
        executionId: string,
        type: ExecutionEventType,
        data: Record<string, unknown>,
    ): void {
        const event: ExecutionEvent = {
            id: `evt-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`,
            executionId,
            type,
            timestamp: new Date(),
            data,
        };

        let events = this.executionEvents.get(executionId);
        if (!events) {
            events = [];
            this.executionEvents.set(executionId, events);
        }
        events.push(event);
    }
}

export default TaskExecutionCoordinator;
