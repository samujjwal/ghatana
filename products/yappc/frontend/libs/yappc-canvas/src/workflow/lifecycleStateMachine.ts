/**
 * YAPPC Lifecycle State Machine
 *
 * Implements the Developer Lifecycle Model with 8 stages:
 * intent → context → plan → execute → verify → observe → learn → institutionalize
 *
 * Features:
 * - Stage transition validation with entry/exit criteria
 * - Forward, backward, and skip transitions
 * - Pre/post transition hooks
 * - Rollback support
 * - Audit trail for all transitions
 *
 * @module workflow/lifecycleStateMachine
 */

import type {
    LifecycleStage,
    LifecycleStageConfig,
    StageTransition,
    TaskExecution,
    WorkflowExecution,
} from '@ghatana/types/tasks';

// ============================================================================
// Types
// ============================================================================

/**
 * Transition type classification
 */
export type TransitionType = 'forward' | 'backward' | 'skip';

/**
 * Transition validation result
 */
export interface TransitionValidation {
    valid: boolean;
    errors: TransitionError[];
    warnings: TransitionWarning[];
}

/**
 * Transition error details
 */
export interface TransitionError {
    code: string;
    message: string;
    criterion?: string;
}

/**
 * Transition warning details
 */
export interface TransitionWarning {
    code: string;
    message: string;
    suggestion?: string;
}

/**
 * Transition hook context
 */
export interface TransitionContext {
    workflowId: string;
    fromStage: LifecycleStage;
    toStage: LifecycleStage;
    transitionType: TransitionType;
    userId: string;
    timestamp: Date;
    metadata?: Record<string, unknown>;
}

/**
 * Transition hook function signature
 */
export type TransitionHook = (context: TransitionContext) => Promise<void>;

/**
 * Stage transition configuration
 */
export interface StageTransitionConfig {
    from: LifecycleStage;
    to: LifecycleStage;
    type: TransitionType;
    allowed: boolean;
    requiresApproval?: boolean;
    preHooks?: string[];
    postHooks?: string[];
}

/**
 * Lifecycle state machine state
 */
export interface LifecycleState {
    currentStage: LifecycleStage;
    stageHistory: StageHistoryEntry[];
    stageStartedAt: Date;
    completedCriteria: Set<string>;
}

/**
 * Stage history entry for audit
 */
export interface StageHistoryEntry {
    stage: LifecycleStage;
    enteredAt: Date;
    exitedAt?: Date;
    transitionType: TransitionType;
    triggeredBy: string;
    reason?: string;
}

/**
 * State machine configuration
 */
export interface LifecycleStateMachineConfig {
    stages: LifecycleStageConfig[];
    transitions: StageTransitionConfig[];
    hooks: Map<string, TransitionHook>;
    enforceEntryCriteria: boolean;
    enforceExitCriteria: boolean;
    allowSkipTransitions: boolean;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Default stage order
 */
export const STAGE_ORDER: LifecycleStage[] = [
    'intent',
    'context',
    'plan',
    'execute',
    'verify',
    'observe',
    'learn',
    'institutionalize',
];

/**
 * Stage group mappings
 */
export const STAGE_GROUPS: Record<string, LifecycleStage[]> = {
    discovery: ['intent', 'context', 'plan'],
    delivery: ['execute', 'verify'],
    operations: ['observe', 'learn', 'institutionalize'],
};

// ============================================================================
// Lifecycle State Machine Class
// ============================================================================

/**
 * Lifecycle State Machine
 *
 * Manages stage transitions with validation, hooks, and audit trail.
 */
export class LifecycleStateMachine {
    private config: LifecycleStateMachineConfig;
    private state: LifecycleState;
    private transitionMap: Map<string, StageTransitionConfig>;

    constructor(config: LifecycleStateMachineConfig, initialStage: LifecycleStage = 'intent') {
        this.config = config;
        this.state = {
            currentStage: initialStage,
            stageHistory: [{
                stage: initialStage,
                enteredAt: new Date(),
                transitionType: 'forward',
                triggeredBy: 'system',
                reason: 'Initial state',
            }],
            stageStartedAt: new Date(),
            completedCriteria: new Set(),
        };

        // Build transition lookup map
        this.transitionMap = new Map();
        for (const transition of config.transitions) {
            const key = `${transition.from}->${transition.to}`;
            this.transitionMap.set(key, transition);
        }
    }

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Get current lifecycle stage
     */
    getCurrentStage(): LifecycleStage {
        return this.state.currentStage;
    }

    /**
     * Get current stage configuration
     */
    getCurrentStageConfig(): LifecycleStageConfig | undefined {
        return this.config.stages.find((s) => s.id === this.state.currentStage);
    }

    /**
     * Get full state history
     */
    getHistory(): StageHistoryEntry[] {
        return [...this.state.stageHistory];
    }

    /**
     * Get allowed transitions from current stage
     */
    getAllowedTransitions(): StageTransitionConfig[] {
        return this.config.transitions.filter(
            (t) => t.from === this.state.currentStage && t.allowed,
        );
    }

    /**
     * Check if a specific transition is allowed
     */
    canTransitionTo(targetStage: LifecycleStage): TransitionValidation {
        const transitionKey = `${this.state.currentStage}->${targetStage}`;
        const transitionConfig = this.transitionMap.get(transitionKey);

        const errors: TransitionError[] = [];
        const warnings: TransitionWarning[] = [];

        // Check if transition exists
        if (!transitionConfig) {
            errors.push({
                code: 'TRANSITION_NOT_DEFINED',
                message: `No transition defined from ${this.state.currentStage} to ${targetStage}`,
            });
            return { valid: false, errors, warnings };
        }

        // Check if transition is allowed
        if (!transitionConfig.allowed) {
            errors.push({
                code: 'TRANSITION_NOT_ALLOWED',
                message: `Transition from ${this.state.currentStage} to ${targetStage} is not allowed`,
            });
            return { valid: false, errors, warnings };
        }

        // Check skip transitions
        if (transitionConfig.type === 'skip' && !this.config.allowSkipTransitions) {
            errors.push({
                code: 'SKIP_NOT_ALLOWED',
                message: 'Skip transitions are not enabled in this workflow',
            });
            return { valid: false, errors, warnings };
        }

        // Check exit criteria for current stage
        if (this.config.enforceExitCriteria) {
            const currentStageConfig = this.getCurrentStageConfig();
            if (currentStageConfig) {
                for (const criterion of currentStageConfig.exitCriteria) {
                    if (!this.state.completedCriteria.has(criterion)) {
                        errors.push({
                            code: 'EXIT_CRITERION_NOT_MET',
                            message: `Exit criterion not met: ${criterion}`,
                            criterion,
                        });
                    }
                }
            }
        }

        // Check entry criteria for target stage
        if (this.config.enforceEntryCriteria) {
            const targetStageConfig = this.config.stages.find((s) => s.id === targetStage);
            if (targetStageConfig) {
                for (const criterion of targetStageConfig.entryCriteria) {
                    if (!this.state.completedCriteria.has(criterion)) {
                        warnings.push({
                            code: 'ENTRY_CRITERION_NOT_MET',
                            message: `Entry criterion not met: ${criterion}`,
                            suggestion: `Complete ${criterion} before proceeding`,
                        });
                    }
                }
            }
        }

        // Add warning for backward transitions
        if (transitionConfig.type === 'backward') {
            warnings.push({
                code: 'BACKWARD_TRANSITION',
                message: 'Moving to a previous stage. Progress in current stage may be affected.',
            });
        }

        return {
            valid: errors.length === 0,
            errors,
            warnings,
        };
    }

    /**
     * Execute transition to a new stage
     */
    async transitionTo(
        targetStage: LifecycleStage,
        userId: string,
        reason?: string,
    ): Promise<TransitionResult> {
        // Validate transition
        const validation = this.canTransitionTo(targetStage);
        if (!validation.valid) {
            return {
                success: false,
                errors: validation.errors,
                warnings: validation.warnings,
            };
        }

        const transitionKey = `${this.state.currentStage}->${targetStage}`;
        const transitionConfig = this.transitionMap.get(transitionKey)!;
        const previousStage = this.state.currentStage;
        const now = new Date();

        // Create transition context
        const context: TransitionContext = {
            workflowId: '', // Set by caller
            fromStage: previousStage,
            toStage: targetStage,
            transitionType: transitionConfig.type,
            userId,
            timestamp: now,
        };

        try {
            // Execute pre-hooks
            if (transitionConfig.preHooks) {
                for (const hookName of transitionConfig.preHooks) {
                    const hook = this.config.hooks.get(hookName);
                    if (hook) {
                        await hook(context);
                    }
                }
            }

            // Update history - mark current stage as exited
            const currentHistoryEntry = this.state.stageHistory[this.state.stageHistory.length - 1];
            if (currentHistoryEntry) {
                currentHistoryEntry.exitedAt = now;
            }

            // Perform transition
            this.state.currentStage = targetStage;
            this.state.stageStartedAt = now;
            this.state.stageHistory.push({
                stage: targetStage,
                enteredAt: now,
                transitionType: transitionConfig.type,
                triggeredBy: userId,
                reason,
            });

            // Execute post-hooks
            if (transitionConfig.postHooks) {
                for (const hookName of transitionConfig.postHooks) {
                    const hook = this.config.hooks.get(hookName);
                    if (hook) {
                        await hook(context);
                    }
                }
            }

            return {
                success: true,
                previousStage,
                currentStage: targetStage,
                transitionType: transitionConfig.type,
                warnings: validation.warnings,
            };
        } catch (error) {
            // Rollback on error
            this.state.currentStage = previousStage;
            this.state.stageHistory.pop();
            if (currentHistoryEntry) {
                currentHistoryEntry.exitedAt = undefined;
            }

            return {
                success: false,
                errors: [{
                    code: 'TRANSITION_FAILED',
                    message: error instanceof Error ? error.message : 'Unknown error',
                }],
                warnings: validation.warnings,
            };
        }
    }

    /**
     * Mark a criterion as completed
     */
    completeCriterion(criterion: string): void {
        this.state.completedCriteria.add(criterion);
    }

    /**
     * Check if a criterion is completed
     */
    isCriterionCompleted(criterion: string): boolean {
        return this.state.completedCriteria.has(criterion);
    }

    /**
     * Get all completed criteria
     */
    getCompletedCriteria(): string[] {
        return Array.from(this.state.completedCriteria);
    }

    /**
     * Get stage index (0-based)
     */
    getStageIndex(stage: LifecycleStage = this.state.currentStage): number {
        return STAGE_ORDER.indexOf(stage);
    }

    /**
     * Check if workflow is in discovery phase
     */
    isInDiscovery(): boolean {
        return STAGE_GROUPS.discovery.includes(this.state.currentStage);
    }

    /**
     * Check if workflow is in delivery phase
     */
    isInDelivery(): boolean {
        return STAGE_GROUPS.delivery.includes(this.state.currentStage);
    }

    /**
     * Check if workflow is in operations phase
     */
    isInOperations(): boolean {
        return STAGE_GROUPS.operations.includes(this.state.currentStage);
    }

    /**
     * Get progress as percentage (0-100)
     */
    getProgress(): number {
        const currentIndex = this.getStageIndex();
        return Math.round((currentIndex / (STAGE_ORDER.length - 1)) * 100);
    }

    /**
     * Get time spent in current stage
     */
    getTimeInCurrentStage(): number {
        return Date.now() - this.state.stageStartedAt.getTime();
    }

    /**
     * Serialize state for persistence
     */
    serialize(): SerializedLifecycleState {
        return {
            currentStage: this.state.currentStage,
            stageHistory: this.state.stageHistory.map((entry) => ({
                ...entry,
                enteredAt: entry.enteredAt.toISOString(),
                exitedAt: entry.exitedAt?.toISOString(),
            })),
            stageStartedAt: this.state.stageStartedAt.toISOString(),
            completedCriteria: Array.from(this.state.completedCriteria),
        };
    }

    /**
     * Restore state from serialized data
     */
    static deserialize(
        config: LifecycleStateMachineConfig,
        data: SerializedLifecycleState,
    ): LifecycleStateMachine {
        const machine = new LifecycleStateMachine(config, data.currentStage);
        machine.state = {
            currentStage: data.currentStage,
            stageHistory: data.stageHistory.map((entry) => ({
                ...entry,
                enteredAt: new Date(entry.enteredAt),
                exitedAt: entry.exitedAt ? new Date(entry.exitedAt) : undefined,
            })),
            stageStartedAt: new Date(data.stageStartedAt),
            completedCriteria: new Set(data.completedCriteria),
        };
        return machine;
    }
}

// ============================================================================
// Supporting Types
// ============================================================================

/**
 * Transition result
 */
export interface TransitionResult {
    success: boolean;
    previousStage?: LifecycleStage;
    currentStage?: LifecycleStage;
    transitionType?: TransitionType;
    errors?: TransitionError[];
    warnings?: TransitionWarning[];
}

/**
 * Serialized lifecycle state for persistence
 */
export interface SerializedLifecycleState {
    currentStage: LifecycleStage;
    stageHistory: Array<{
        stage: LifecycleStage;
        enteredAt: string;
        exitedAt?: string;
        transitionType: TransitionType;
        triggeredBy: string;
        reason?: string;
    }>;
    stageStartedAt: string;
    completedCriteria: string[];
}

// ============================================================================
// Factory Function
// ============================================================================

/**
 * Create a lifecycle state machine from configuration files
 */
export function createLifecycleStateMachine(
    stages: LifecycleStageConfig[],
    transitions: StageTransitionConfig[],
    options?: {
        enforceEntryCriteria?: boolean;
        enforceExitCriteria?: boolean;
        allowSkipTransitions?: boolean;
        hooks?: Map<string, TransitionHook>;
    },
): LifecycleStateMachine {
    const config: LifecycleStateMachineConfig = {
        stages,
        transitions,
        hooks: options?.hooks || new Map(),
        enforceEntryCriteria: options?.enforceEntryCriteria ?? true,
        enforceExitCriteria: options?.enforceExitCriteria ?? true,
        allowSkipTransitions: options?.allowSkipTransitions ?? false,
    };

    return new LifecycleStateMachine(config);
}

// ============================================================================
// Default Transition Definitions
// ============================================================================

/**
 * Generate default transitions for the 8-stage lifecycle
 */
export function generateDefaultTransitions(): StageTransitionConfig[] {
    const transitions: StageTransitionConfig[] = [];

    // Forward transitions (each stage to next)
    for (let i = 0; i < STAGE_ORDER.length - 1; i++) {
        transitions.push({
            from: STAGE_ORDER[i],
            to: STAGE_ORDER[i + 1],
            type: 'forward',
            allowed: true,
        });
    }

    // Backward transitions (each stage to previous)
    for (let i = 1; i < STAGE_ORDER.length; i++) {
        transitions.push({
            from: STAGE_ORDER[i],
            to: STAGE_ORDER[i - 1],
            type: 'backward',
            allowed: true,
        });
    }

    // Skip transitions (execute → verify, verify → observe)
    const skipTransitions: [LifecycleStage, LifecycleStage][] = [
        ['plan', 'execute'],
        ['execute', 'observe'],
    ];

    for (const [from, to] of skipTransitions) {
        transitions.push({
            from,
            to,
            type: 'skip',
            allowed: true,
            requiresApproval: true,
        });
    }

    return transitions;
}

export default LifecycleStateMachine;
