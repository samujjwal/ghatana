/**
 * YAPPC Workflow Template Engine
 *
 * Manages workflow templates, instantiation, and task resolution.
 * Converts workflow definitions from config into executable workflow instances.
 *
 * Features:
 * - Template loading and validation
 * - Workflow instantiation with parameter substitution
 * - Task resolution from task registry
 * - Phase/step dependency management
 * - Workflow execution coordination
 *
 * @module workflow/workflowTemplateEngine
 */

import type {
    WorkflowDefinition,
    WorkflowExecution,
    WorkflowPhase,
    TaskDefinition,
    TaskExecution,
    LifecycleStage,
} from '@ghatana/types/tasks';
import {
    LifecycleStateMachine,
    createLifecycleStateMachine,
    type StageTransitionConfig,
    type LifecycleStageConfig,
} from './lifecycleStateMachine';

// ============================================================================
// Types
// ============================================================================

/**
 * Workflow instance status
 */
export type WorkflowStatus =
    | 'pending'
    | 'running'
    | 'paused'
    | 'completed'
    | 'failed'
    | 'cancelled';

/**
 * Workflow phase status
 */
export type PhaseStatus =
    | 'pending'
    | 'in-progress'
    | 'completed'
    | 'skipped'
    | 'failed';

/**
 * Workflow instance - an instantiated workflow template
 */
export interface WorkflowInstance {
    id: string;
    workflowId: string;
    templateId: string;
    name: string;
    description: string;
    status: WorkflowStatus;
    currentPhaseIndex: number;
    phases: WorkflowPhaseInstance[];
    lifecycleState: LifecycleStateMachine;
    createdAt: Date;
    startedAt?: Date;
    completedAt?: Date;
    createdBy: string;
    parameters: Record<string, unknown>;
    metadata: Record<string, unknown>;
}

/**
 * Instantiated workflow phase
 */
export interface WorkflowPhaseInstance {
    id: string;
    name: string;
    description: string;
    order: number;
    status: PhaseStatus;
    lifecycleStage: LifecycleStage;
    steps: WorkflowStepInstance[];
    startedAt?: Date;
    completedAt?: Date;
}

/**
 * Instantiated workflow step
 */
export interface WorkflowStepInstance {
    id: string;
    taskId: string;
    task?: TaskDefinition;
    order: number;
    optional: boolean;
    status: PhaseStatus;
    execution?: TaskExecution;
    dependsOn: string[];
    startedAt?: Date;
    completedAt?: Date;
}

/**
 * Template instantiation parameters
 */
export interface InstantiationParams {
    workflowId: string;
    name?: string;
    createdBy: string;
    parameters?: Record<string, unknown>;
    metadata?: Record<string, unknown>;
}

/**
 * Task registry interface for dependency injection
 */
export interface TaskRegistry {
    getTask(taskId: string): TaskDefinition | undefined;
    getTasksByDomain(domainId: string): TaskDefinition[];
    searchTasks(query: string): TaskDefinition[];
}

// ============================================================================
// Workflow Template Engine
// ============================================================================

/**
 * Workflow Template Engine
 *
 * Manages workflow templates and creates executable workflow instances.
 */
export class WorkflowTemplateEngine {
    private templates: Map<string, WorkflowDefinition>;
    private instances: Map<string, WorkflowInstance>;
    private taskRegistry: TaskRegistry;
    private stageConfigs: LifecycleStageConfig[];
    private transitionConfigs: StageTransitionConfig[];

    constructor(
        taskRegistry: TaskRegistry,
        stageConfigs: LifecycleStageConfig[],
        transitionConfigs: StageTransitionConfig[],
    ) {
        this.templates = new Map();
        this.instances = new Map();
        this.taskRegistry = taskRegistry;
        this.stageConfigs = stageConfigs;
        this.transitionConfigs = transitionConfigs;
    }

    // ==========================================================================
    // Template Management
    // ==========================================================================

    /**
     * Register a workflow template
     */
    registerTemplate(template: WorkflowDefinition): void {
        this.templates.set(template.id, template);
    }

    /**
     * Register multiple templates
     */
    registerTemplates(templates: WorkflowDefinition[]): void {
        for (const template of templates) {
            this.registerTemplate(template);
        }
    }

    /**
     * Get a template by ID
     */
    getTemplate(templateId: string): WorkflowDefinition | undefined {
        return this.templates.get(templateId);
    }

    /**
     * Get all templates
     */
    getAllTemplates(): WorkflowDefinition[] {
        return Array.from(this.templates.values());
    }

    /**
     * Get templates by category
     */
    getTemplatesByCategory(category: string): WorkflowDefinition[] {
        return this.getAllTemplates().filter((t) => t.category === category);
    }

    // ==========================================================================
    // Workflow Instantiation
    // ==========================================================================

    /**
     * Create a new workflow instance from a template
     */
    instantiate(
        templateId: string,
        params: InstantiationParams,
    ): WorkflowInstance {
        const template = this.templates.get(templateId);
        if (!template) {
            throw new Error(`Workflow template not found: ${templateId}`);
        }

        // Create unique instance ID
        const instanceId = params.workflowId || generateId();

        // Create lifecycle state machine
        const lifecycleState = createLifecycleStateMachine(
            this.stageConfigs,
            this.transitionConfigs,
            {
                enforceEntryCriteria: true,
                enforceExitCriteria: true,
                allowSkipTransitions: false,
            },
        );

        // Instantiate phases
        const phases = this.instantiatePhases(template.phases, params.parameters);

        // Create instance
        const instance: WorkflowInstance = {
            id: instanceId,
            workflowId: instanceId,
            templateId,
            name: params.name || template.name,
            description: template.description,
            status: 'pending',
            currentPhaseIndex: 0,
            phases,
            lifecycleState,
            createdAt: new Date(),
            createdBy: params.createdBy,
            parameters: params.parameters || {},
            metadata: params.metadata || {},
        };

        // Store instance
        this.instances.set(instanceId, instance);

        return instance;
    }

    /**
     * Instantiate workflow phases from template
     */
    private instantiatePhases(
        templatePhases: WorkflowPhase[],
        parameters?: Record<string, unknown>,
    ): WorkflowPhaseInstance[] {
        return templatePhases.map((phase, index) => ({
            id: `phase-${generateId()}`,
            name: substituteParameters(phase.name, parameters),
            description: substituteParameters(phase.description || '', parameters),
            order: index,
            status: 'pending',
            lifecycleStage: phase.lifecycleStage,
            steps: this.instantiateSteps(phase.tasks, parameters),
        }));
    }

    /**
     * Instantiate workflow steps from task references
     */
    private instantiateSteps(
        taskRefs: string[],
        parameters?: Record<string, unknown>,
    ): WorkflowStepInstance[] {
        return taskRefs.map((taskRef, index) => {
            // Parse task reference (format: "domain/task-id" or just "task-id")
            const taskId = substituteParameters(taskRef, parameters);
            const task = this.taskRegistry.getTask(taskId);

            return {
                id: `step-${generateId()}`,
                taskId,
                task,
                order: index,
                optional: false, // Could be parsed from taskRef
                status: 'pending',
                dependsOn: [], // Could be specified in template
            };
        });
    }

    // ==========================================================================
    // Instance Management
    // ==========================================================================

    /**
     * Get a workflow instance by ID
     */
    getInstance(instanceId: string): WorkflowInstance | undefined {
        return this.instances.get(instanceId);
    }

    /**
     * Get all workflow instances
     */
    getAllInstances(): WorkflowInstance[] {
        return Array.from(this.instances.values());
    }

    /**
     * Get instances by status
     */
    getInstancesByStatus(status: WorkflowStatus): WorkflowInstance[] {
        return this.getAllInstances().filter((i) => i.status === status);
    }

    /**
     * Get active instances (running or paused)
     */
    getActiveInstances(): WorkflowInstance[] {
        return this.getAllInstances().filter(
            (i) => i.status === 'running' || i.status === 'paused',
        );
    }

    // ==========================================================================
    // Workflow Execution
    // ==========================================================================

    /**
     * Start a workflow instance
     */
    startWorkflow(instanceId: string): void {
        const instance = this.instances.get(instanceId);
        if (!instance) {
            throw new Error(`Workflow instance not found: ${instanceId}`);
        }

        if (instance.status !== 'pending') {
            throw new Error(`Cannot start workflow in status: ${instance.status}`);
        }

        instance.status = 'running';
        instance.startedAt = new Date();

        // Start first phase
        if (instance.phases.length > 0) {
            this.startPhase(instance, 0);
        }
    }

    /**
     * Start a specific phase
     */
    private startPhase(instance: WorkflowInstance, phaseIndex: number): void {
        const phase = instance.phases[phaseIndex];
        if (!phase) return;

        phase.status = 'in-progress';
        phase.startedAt = new Date();
        instance.currentPhaseIndex = phaseIndex;

        // Transition lifecycle to phase's stage
        const lifecycleResult = instance.lifecycleState.canTransitionTo(phase.lifecycleStage);
        if (lifecycleResult.valid) {
            instance.lifecycleState.transitionTo(
                phase.lifecycleStage,
                'system',
                `Starting phase: ${phase.name}`,
            );
        }
    }

    /**
     * Complete a task step
     */
    completeStep(
        instanceId: string,
        stepId: string,
        execution: TaskExecution,
    ): void {
        const instance = this.instances.get(instanceId);
        if (!instance) {
            throw new Error(`Workflow instance not found: ${instanceId}`);
        }

        // Find the step
        let targetPhase: WorkflowPhaseInstance | undefined;
        let targetStep: WorkflowStepInstance | undefined;

        for (const phase of instance.phases) {
            for (const step of phase.steps) {
                if (step.id === stepId) {
                    targetPhase = phase;
                    targetStep = step;
                    break;
                }
            }
            if (targetStep) break;
        }

        if (!targetStep || !targetPhase) {
            throw new Error(`Step not found: ${stepId}`);
        }

        // Update step
        targetStep.status = execution.status === 'completed' ? 'completed' : 'failed';
        targetStep.execution = execution;
        targetStep.completedAt = new Date();

        // Check if phase is complete
        const allStepsComplete = targetPhase.steps.every(
            (s) => s.status === 'completed' || s.status === 'skipped' || s.optional,
        );

        if (allStepsComplete) {
            this.completePhase(instance, targetPhase);
        }
    }

    /**
     * Complete a phase and advance to next
     */
    private completePhase(
        instance: WorkflowInstance,
        phase: WorkflowPhaseInstance,
    ): void {
        phase.status = 'completed';
        phase.completedAt = new Date();

        // Find next phase
        const nextPhaseIndex = instance.currentPhaseIndex + 1;

        if (nextPhaseIndex < instance.phases.length) {
            // Start next phase
            this.startPhase(instance, nextPhaseIndex);
        } else {
            // All phases complete
            this.completeWorkflow(instance);
        }
    }

    /**
     * Complete the workflow
     */
    private completeWorkflow(instance: WorkflowInstance): void {
        instance.status = 'completed';
        instance.completedAt = new Date();
    }

    /**
     * Pause a workflow
     */
    pauseWorkflow(instanceId: string): void {
        const instance = this.instances.get(instanceId);
        if (!instance) {
            throw new Error(`Workflow instance not found: ${instanceId}`);
        }

        if (instance.status !== 'running') {
            throw new Error(`Cannot pause workflow in status: ${instance.status}`);
        }

        instance.status = 'paused';
    }

    /**
     * Resume a paused workflow
     */
    resumeWorkflow(instanceId: string): void {
        const instance = this.instances.get(instanceId);
        if (!instance) {
            throw new Error(`Workflow instance not found: ${instanceId}`);
        }

        if (instance.status !== 'paused') {
            throw new Error(`Cannot resume workflow in status: ${instance.status}`);
        }

        instance.status = 'running';
    }

    /**
     * Cancel a workflow
     */
    cancelWorkflow(instanceId: string): void {
        const instance = this.instances.get(instanceId);
        if (!instance) {
            throw new Error(`Workflow instance not found: ${instanceId}`);
        }

        if (instance.status === 'completed' || instance.status === 'cancelled') {
            throw new Error(`Cannot cancel workflow in status: ${instance.status}`);
        }

        instance.status = 'cancelled';
    }

    // ==========================================================================
    // Progress & Metrics
    // ==========================================================================

    /**
     * Get workflow progress (0-100)
     */
    getProgress(instanceId: string): number {
        const instance = this.instances.get(instanceId);
        if (!instance) return 0;

        let totalSteps = 0;
        let completedSteps = 0;

        for (const phase of instance.phases) {
            for (const step of phase.steps) {
                if (!step.optional) {
                    totalSteps++;
                    if (step.status === 'completed') {
                        completedSteps++;
                    }
                }
            }
        }

        return totalSteps > 0 ? Math.round((completedSteps / totalSteps) * 100) : 0;
    }

    /**
     * Get remaining tasks in current phase
     */
    getRemainingTasks(instanceId: string): WorkflowStepInstance[] {
        const instance = this.instances.get(instanceId);
        if (!instance) return [];

        const currentPhase = instance.phases[instance.currentPhaseIndex];
        if (!currentPhase) return [];

        return currentPhase.steps.filter(
            (step) => step.status === 'pending' || step.status === 'in-progress',
        );
    }

    /**
     * Get next available task
     */
    getNextTask(instanceId: string): WorkflowStepInstance | undefined {
        const remainingTasks = this.getRemainingTasks(instanceId);

        // Find first task with all dependencies satisfied
        for (const step of remainingTasks) {
            if (step.status !== 'pending') continue;

            const dependenciesMet = step.dependsOn.every((depId) => {
                const depStep = this.findStep(instanceId, depId);
                return depStep?.status === 'completed';
            });

            if (dependenciesMet) {
                return step;
            }
        }

        return undefined;
    }

    /**
     * Find a step by ID
     */
    private findStep(
        instanceId: string,
        stepId: string,
    ): WorkflowStepInstance | undefined {
        const instance = this.instances.get(instanceId);
        if (!instance) return undefined;

        for (const phase of instance.phases) {
            for (const step of phase.steps) {
                if (step.id === stepId) {
                    return step;
                }
            }
        }

        return undefined;
    }

    // ==========================================================================
    // Serialization
    // ==========================================================================

    /**
     * Serialize instance for persistence
     */
    serializeInstance(instanceId: string): SerializedWorkflowInstance | undefined {
        const instance = this.instances.get(instanceId);
        if (!instance) return undefined;

        return {
            id: instance.id,
            workflowId: instance.workflowId,
            templateId: instance.templateId,
            name: instance.name,
            description: instance.description,
            status: instance.status,
            currentPhaseIndex: instance.currentPhaseIndex,
            phases: instance.phases.map((phase) => ({
                ...phase,
                startedAt: phase.startedAt?.toISOString(),
                completedAt: phase.completedAt?.toISOString(),
                steps: phase.steps.map((step) => ({
                    ...step,
                    task: undefined, // Don't serialize full task definition
                    execution: step.execution, // Serialize execution
                    startedAt: step.startedAt?.toISOString(),
                    completedAt: step.completedAt?.toISOString(),
                })),
            })),
            lifecycleState: instance.lifecycleState.serialize(),
            createdAt: instance.createdAt.toISOString(),
            startedAt: instance.startedAt?.toISOString(),
            completedAt: instance.completedAt?.toISOString(),
            createdBy: instance.createdBy,
            parameters: instance.parameters,
            metadata: instance.metadata,
        };
    }
}

// ============================================================================
// Supporting Types & Utilities
// ============================================================================

/**
 * Serialized workflow instance for persistence
 */
export interface SerializedWorkflowInstance {
    id: string;
    workflowId: string;
    templateId: string;
    name: string;
    description: string;
    status: WorkflowStatus;
    currentPhaseIndex: number;
    phases: Array<{
        id: string;
        name: string;
        description: string;
        order: number;
        status: PhaseStatus;
        lifecycleStage: LifecycleStage;
        steps: Array<{
            id: string;
            taskId: string;
            order: number;
            optional: boolean;
            status: PhaseStatus;
            execution?: TaskExecution;
            dependsOn: string[];
            startedAt?: string;
            completedAt?: string;
        }>;
        startedAt?: string;
        completedAt?: string;
    }>;
    lifecycleState: unknown;
    createdAt: string;
    startedAt?: string;
    completedAt?: string;
    createdBy: string;
    parameters: Record<string, unknown>;
    metadata: Record<string, unknown>;
}

/**
 * Generate a unique ID
 */
function generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Substitute parameters in a string
 */
function substituteParameters(
    template: string,
    parameters?: Record<string, unknown>,
): string {
    if (!parameters) return template;

    return template.replace(/\{\{(\w+)\}\}/g, (_, key) => {
        const value = parameters[key];
        return value !== undefined ? String(value) : `{{${key}}}`;
    });
}

export default WorkflowTemplateEngine;
