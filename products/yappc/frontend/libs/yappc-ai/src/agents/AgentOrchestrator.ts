/**
 * Agent Orchestrator
 *
 * Coordinates multiple AI agents for complex, multi-step workflows.
 * Handles dependency resolution, parallel execution, and error propagation.
 *
 * @module ai/agents/AgentOrchestrator
 * @doc.type class
 * @doc.purpose Multi-agent workflow coordination
 * @doc.layer product
 * @doc.pattern Mediator
 */

import { v4 as uuidv4 } from 'uuid';
import type {
    AgentContext,
    AgentName,
    AgentResult,
    IAIAgent,
} from './types';

/**
 * Workflow step definition
 */
export interface WorkflowStep {
    id: string;
    agentName: AgentName;
    input: Record<string, unknown> | string; // String for references like '@predict'
    dependsOn?: string[];
    optional?: boolean;
    timeout?: number;
}

/**
 * Agent workflow definition
 */
export interface AgentWorkflow {
    name: string;
    description?: string;
    steps: WorkflowStep[];
    stopOnError?: boolean;
    timeout?: number;
}

/**
 * Workflow execution result
 */
export interface WorkflowResult {
    success: boolean;
    workflowName: string;
    results: Map<string, AgentResult<unknown>>;
    metrics: WorkflowMetrics;
    errors?: WorkflowError[];
}

/**
 * Workflow metrics
 */
export interface WorkflowMetrics {
    totalDurationMs: number;
    stepDurations: Record<string, number>;
    agentsExecuted: number;
    successCount: number;
    failureCount: number;
}

/**
 * Workflow error
 */
export interface WorkflowError {
    stepId: string;
    agentName: AgentName;
    error: string;
    recoverable: boolean;
}

/**
 * Execution stage (steps that can run in parallel)
 */
interface ExecutionStage {
    steps: WorkflowStep[];
}

/**
 * Agent Orchestrator for coordinating multi-agent workflows
 */
export class AgentOrchestrator {
    private agents: Map<AgentName, IAIAgent<unknown, unknown>>;

    constructor() {
        this.agents = new Map();
    }

    /**
     * Register an agent with the orchestrator
     */
    registerAgent<TInput, TOutput>(agent: IAIAgent<TInput, TOutput>): void {
        this.agents.set(agent.name, agent as IAIAgent<unknown, unknown>);
    }

    /**
     * Get a registered agent
     */
    getAgent<TInput, TOutput>(name: AgentName): IAIAgent<TInput, TOutput> | undefined {
        return this.agents.get(name) as IAIAgent<TInput, TOutput> | undefined;
    }

    /**
     * List all registered agents
     */
    listAgents(): AgentName[] {
        return Array.from(this.agents.keys());
    }

    /**
     * Execute a multi-agent workflow
     */
    async executeWorkflow(
        workflow: AgentWorkflow,
        context: AgentContext
    ): Promise<WorkflowResult> {
        const startTime = Date.now();
        const results = new Map<string, AgentResult<unknown>>();
        const stepDurations: Record<string, number> = {};
        const errors: WorkflowError[] = [];

        // Build execution plan (resolve dependencies into stages)
        const executionPlan = this.buildExecutionPlan(workflow);

        // Execute stages sequentially, steps within stage in parallel
        for (const stage of executionPlan) {
            const stageResults = await Promise.all(
                stage.steps.map(async (step) => {
                    const stepStart = Date.now();
                    try {
                        const result = await this.executeStep(step, results, context);
                        stepDurations[step.id] = Date.now() - stepStart;
                        results.set(step.id, result);
                        return { step, result, success: result.success };
                    } catch (error) {
                        stepDurations[step.id] = Date.now() - stepStart;
                        const errorResult: AgentResult<unknown> = {
                            success: false,
                            error: error instanceof Error ? error : new Error(String(error)),
                            metrics: { latencyMs: stepDurations[step.id], modelVersion: 'unknown' },
                            trace: {
                                agentName: step.agentName,
                                requestId: context.requestId,
                                timestamp: new Date(),
                                metadata: { stepId: step.id, error: true },
                            },
                        };
                        results.set(step.id, errorResult);
                        return { step, result: errorResult, success: false };
                    }
                })
            );

            // Check for failures
            const failures = stageResults.filter((r) => !r.success && !r.step.optional);
            for (const failure of failures) {
                errors.push({
                    stepId: failure.step.id,
                    agentName: failure.step.agentName,
                    error: failure.result.error?.message || 'Unknown error',
                    recoverable: false,
                });
            }

            // Stop if configured and failures occurred
            if (workflow.stopOnError && failures.length > 0) {
                break;
            }
        }

        const successCount = Array.from(results.values()).filter((r) => r.success).length;
        const failureCount = results.size - successCount;

        return {
            success: failureCount === 0,
            workflowName: workflow.name,
            results,
            metrics: {
                totalDurationMs: Date.now() - startTime,
                stepDurations,
                agentsExecuted: results.size,
                successCount,
                failureCount,
            },
            errors: errors.length > 0 ? errors : undefined,
        };
    }

    /**
     * Build execution plan from workflow
     */
    private buildExecutionPlan(workflow: AgentWorkflow): ExecutionStage[] {
        const stages: ExecutionStage[] = [];
        const completed = new Set<string>();
        const remaining = new Set(workflow.steps.map((s) => s.id));

        while (remaining.size > 0) {
            // Find steps whose dependencies are all completed
            const readySteps = workflow.steps.filter(
                (step) =>
                    remaining.has(step.id) &&
                    (step.dependsOn || []).every((dep) => completed.has(dep))
            );

            if (readySteps.length === 0 && remaining.size > 0) {
                throw new Error('Circular dependency detected in workflow');
            }

            stages.push({ steps: readySteps });

            for (const step of readySteps) {
                completed.add(step.id);
                remaining.delete(step.id);
            }
        }

        return stages;
    }

    /**
     * Execute a single workflow step
     */
    private async executeStep(
        step: WorkflowStep,
        previousResults: Map<string, AgentResult<unknown>>,
        context: AgentContext
    ): Promise<AgentResult<unknown>> {
        const agent = this.agents.get(step.agentName);
        if (!agent) {
            throw new Error(`Agent not found: ${step.agentName}`);
        }

        // Build input from step config and previous results
        const input = this.buildStepInput(step, previousResults);

        // Apply step-specific timeout
        const stepContext: AgentContext = {
            ...context,
            timeout: step.timeout || context.timeout,
            requestId: `${context.requestId}-${step.id}`,
        };

        return agent.execute(input, stepContext);
    }

    /**
     * Build step input by resolving references to previous results
     */
    private buildStepInput(
        step: WorkflowStep,
        previousResults: Map<string, AgentResult<unknown>>
    ): unknown {
        if (typeof step.input === 'string' && step.input.startsWith('@')) {
            // Reference to previous step result
            const refId = step.input.slice(1);
            const result = previousResults.get(refId);
            if (!result || !result.success) {
                throw new Error(`Referenced step '${refId}' not found or failed`);
            }
            return result.data;
        }

        if (typeof step.input !== 'object' || step.input === null) {
            return step.input;
        }

        // Deep resolve references in object
        return this.resolveReferences(step.input, previousResults);
    }

    /**
     * Resolve references in an object
     */
    private resolveReferences(
        obj: Record<string, unknown>,
        previousResults: Map<string, AgentResult<unknown>>
    ): Record<string, unknown> {
        const result: Record<string, unknown> = {};

        for (const [key, value] of Object.entries(obj)) {
            if (typeof value === 'string' && value.startsWith('@')) {
                const refId = value.slice(1);
                const refResult = previousResults.get(refId);
                if (refResult?.success) {
                    result[key] = refResult.data;
                } else {
                    result[key] = undefined;
                }
            } else if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
                result[key] = this.resolveReferences(
                    value as Record<string, unknown>,
                    previousResults
                );
            } else {
                result[key] = value;
            }
        }

        return result;
    }

    /**
     * Execute a predefined phase change workflow
     */
    async executePhaseChangeWorkflow(
        itemId: string,
        newPhase: string,
        context: AgentContext
    ): Promise<WorkflowResult> {
        const workflow: AgentWorkflow = {
            name: 'phase-change',
            description: 'AI-powered phase change validation and prediction',
            steps: [
                {
                    id: 'predict',
                    agentName: 'PredictionAgent',
                    input: { itemId, targetType: 'item', currentPhase: newPhase },
                },
                {
                    id: 'recommend',
                    agentName: 'RecommendationAgent',
                    input: {
                        userId: context.userId,
                        contextType: 'item',
                        currentContext: { itemId, newPhase, predictions: '@predict' },
                    },
                    dependsOn: ['predict'],
                },
            ],
            stopOnError: false,
        };

        return this.executeWorkflow(workflow, context);
    }

    /**
     * Execute a predefined risk assessment workflow
     */
    async executeRiskAssessmentWorkflow(
        targetId: string,
        targetType: 'item' | 'phase' | 'workflow',
        context: AgentContext
    ): Promise<WorkflowResult> {
        const workflow: AgentWorkflow = {
            name: 'risk-assessment',
            description: 'Comprehensive risk assessment using multiple agents',
            steps: [
                {
                    id: 'predict',
                    agentName: 'PredictionAgent',
                    input: { targetId, targetType },
                },
                {
                    id: 'anomaly',
                    agentName: 'AnomalyDetectorAgent',
                    input: {
                        metricType: 'velocity',
                        currentMetrics: [],
                    },
                },
                {
                    id: 'copilot-summary',
                    agentName: 'CopilotAgent',
                    input: {
                        query: `Summarize the risk assessment for ${targetType} ${targetId}`,
                        selectedItems: [targetId],
                    },
                    dependsOn: ['predict', 'anomaly'],
                    optional: true,
                },
            ],
            stopOnError: false,
        };

        return this.executeWorkflow(workflow, context);
    }

    /**
     * Health check for all registered agents
     */
    async healthCheck(): Promise<Record<AgentName, 'healthy' | 'degraded' | 'unhealthy'>> {
        const results: Record<string, 'healthy' | 'degraded' | 'unhealthy'> = {};

        await Promise.all(
            Array.from(this.agents.entries()).map(async ([name, agent]) => {
                try {
                    const health = await agent.healthCheck();
                    results[name] = health.healthy ? 'healthy' : 'degraded';
                } catch {
                    results[name] = 'unhealthy';
                }
            })
        );

        return results as Record<AgentName, 'healthy' | 'degraded' | 'unhealthy'>;
    }
}

/**
 * Create a default orchestrator with common workflows
 */
export function createDefaultOrchestrator(): AgentOrchestrator {
    return new AgentOrchestrator();
}
