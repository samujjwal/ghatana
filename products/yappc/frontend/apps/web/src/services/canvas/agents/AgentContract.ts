/**
 * Agent Execution Contracts
 * 
 * Defines contracts for autonomous agents with safety guarantees
 * 
 * @doc.type types
 * @doc.purpose Define agent execution contracts and guarantees
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import { LifecyclePhase } from '../../../types/lifecycle';
import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';

// ============================================================================
// Agent Types
// ============================================================================

export type AgentType =
    | 'IntentAgent'          // Helps understand user intent
    | 'ShapeAgent'           // Suggests architectural patterns
    | 'ValidationAgent'      // Validates design completeness
    | 'GenerationAgent'      // Generates code artifacts
    | 'ImprovementAgent';    // Suggests optimizations

export type AgentAction =
    | 'suggest_node'         // Suggest adding a node
    | 'suggest_connection'   // Suggest adding a connection
    | 'detect_pattern'       // Detect architectural pattern
    | 'identify_gap'         // Identify missing component
    | 'identify_risk'        // Identify potential risk
    | 'suggest_optimization' // Suggest performance optimization
    | 'validate_design'      // Validate design completeness
    | 'generate_code'        // Generate code artifact
    | 'analyze_metrics';     // Analyze observability metrics

export type RollbackStrategy =
    | 'VERSION'   // Roll back to previous version
    | 'UNDO'      // Undo specific actions
    | 'MANUAL';   // Manual rollback required

export type AuditLevel =
    | 'NONE'      // No audit logging
    | 'SUMMARY'   // Log summary only
    | 'FULL';     // Log all actions

// ============================================================================
// Agent Guarantees
// ============================================================================

export interface Guarantee {
    type: 'NON_DESTRUCTIVE' | 'REVERSIBLE' | 'AUDITABLE' | 'BOUNDED_TIME' | 'BOUNDED_COST';
    description: string;
    metadata?: Record<string, unknown>;
}

// ============================================================================
// Agent Execution Contract
// ============================================================================

export interface AgentExecutionContract {
    /** Type of agent */
    agentType: AgentType;

    /** Lifecycle phases where agent can operate */
    allowedPhases: LifecyclePhase[];

    /** Actions the agent is permitted to perform */
    allowedActions: AgentAction[];

    /** Required context for agent execution */
    requiredContext: string[];

    /** Artifacts produced by the agent */
    outputArtifacts: string[];

    /** Audit logging level */
    auditLevel: AuditLevel;

    /** Strategy for rolling back agent actions */
    rollbackStrategy: RollbackStrategy;

    /** Whether user approval is required before actions */
    approvalRequired: boolean;

    /** Guarantees provided by the agent */
    guarantees: Guarantee[];

    /** Maximum execution time (ms) */
    maxExecutionTime?: number;

    /** Maximum cost (in credits/tokens) */
    maxCost?: number;
}

// ============================================================================
// Agent Execution Context
// ============================================================================

export interface AgentExecutionContext {
    /** Current canvas state */
    canvasState: CanvasState;

    /** Current lifecycle phase */
    lifecyclePhase: LifecyclePhase;

    /** User preferences */
    userPreferences?: Record<string, unknown>;

    /** Historical data */
    history?: unknown[];

    /** Observability metrics */
    metrics?: Record<string, unknown>;
}

// ============================================================================
// Agent Execution Result
// ============================================================================

export interface AgentExecutionResult {
    /** Success status */
    success: boolean;

    /** Actions performed */
    actions: AgentAction[];

    /** Artifacts generated */
    artifacts: Record<string, unknown>;

    /** Suggestions made */
    suggestions?: unknown[];

    /** Errors encountered */
    errors?: string[];

    /** Execution metadata */
    metadata: {
        startTime: number;
        endTime: number;
        duration: number;
        cost?: number;
    };

    /** Audit log */
    auditLog?: AuditLogEntry[];
}

// ============================================================================
// Audit Log Entry
// ============================================================================

export interface AuditLogEntry {
    timestamp: number;
    agentType: AgentType;
    action: AgentAction;
    input: unknown;
    output: unknown;
    success: boolean;
    error?: string;
}

// ============================================================================
// Pre-defined Agent Contracts
// ============================================================================

/**
 * Shape Agent Contract
 * Suggests architectural patterns and connections during SHAPE phase
 */
export const ShapeAgentContract: AgentExecutionContract = {
    agentType: 'ShapeAgent',
    allowedPhases: [LifecyclePhase.SHAPE],
    allowedActions: [
        'suggest_node',
        'suggest_connection',
        'detect_pattern',
        'identify_gap',
    ],
    requiredContext: ['canvasState', 'lifecyclePhase'],
    outputArtifacts: ['suggestions', 'patterns'],
    auditLevel: 'SUMMARY',
    rollbackStrategy: 'UNDO',
    approvalRequired: true,
    guarantees: [
        {
            type: 'NON_DESTRUCTIVE',
            description: 'No modifications without user approval',
        },
        {
            type: 'REVERSIBLE',
            description: 'All suggested actions are undoable',
        },
        {
            type: 'AUDITABLE',
            description: 'All actions are logged',
        },
    ],
    maxExecutionTime: 5000, // 5 seconds
};

/**
 * Validation Agent Contract
 * Validates design completeness and identifies risks
 */
export const ValidationAgentContract: AgentExecutionContract = {
    agentType: 'ValidationAgent',
    allowedPhases: [LifecyclePhase.VALIDATE],
    allowedActions: [
        'validate_design',
        'identify_gap',
        'identify_risk',
    ],
    requiredContext: ['canvasState'],
    outputArtifacts: ['validationReport', 'risks', 'gaps'],
    auditLevel: 'FULL',
    rollbackStrategy: 'VERSION',
    approvalRequired: false, // Read-only operation
    guarantees: [
        {
            type: 'NON_DESTRUCTIVE',
            description: 'Read-only analysis, no modifications',
        },
        {
            type: 'BOUNDED_TIME',
            description: 'Completes within 10 seconds',
            metadata: { maxTime: 10000 },
        },
    ],
    maxExecutionTime: 10000, // 10 seconds
};

/**
 * Generation Agent Contract
 * Generates code artifacts from canvas design
 */
export const GenerationAgentContract: AgentExecutionContract = {
    agentType: 'GenerationAgent',
    allowedPhases: [LifecyclePhase.GENERATE],
    allowedActions: [
        'generate_code',
    ],
    requiredContext: ['canvasState', 'validationReport'],
    outputArtifacts: ['codeFiles', 'configFiles', 'documentation'],
    auditLevel: 'FULL',
    rollbackStrategy: 'VERSION',
    approvalRequired: true,
    guarantees: [
        {
            type: 'REVERSIBLE',
            description: 'Can revert to previous code version',
        },
        {
            type: 'AUDITABLE',
            description: 'All generated code is logged',
        },
        {
            type: 'BOUNDED_COST',
            description: 'Generation cost limited to 1000 tokens',
            metadata: { maxTokens: 1000 },
        },
    ],
    maxExecutionTime: 30000, // 30 seconds
    maxCost: 1000, // tokens
};

/**
 * Improvement Agent Contract
 * Analyzes metrics and suggests optimizations
 */
export const ImprovementAgentContract: AgentExecutionContract = {
    agentType: 'ImprovementAgent',
    allowedPhases: [LifecyclePhase.IMPROVE],
    allowedActions: [
        'analyze_metrics',
        'suggest_optimization',
        'identify_risk',
    ],
    requiredContext: ['canvasState', 'metrics'],
    outputArtifacts: ['optimizations', 'recommendations'],
    auditLevel: 'SUMMARY',
    rollbackStrategy: 'UNDO',
    approvalRequired: true,
    guarantees: [
        {
            type: 'NON_DESTRUCTIVE',
            description: 'Only suggests changes, does not modify',
        },
        {
            type: 'AUDITABLE',
            description: 'All analysis is logged',
        },
    ],
    maxExecutionTime: 15000, // 15 seconds
};

// ============================================================================
// Agent Contract Validation
// ============================================================================

/**
 * Validate that an agent can execute in current context
 */
export function validateAgentExecution(
    contract: AgentExecutionContract,
    context: AgentExecutionContext
): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    // Check phase
    if (!contract.allowedPhases.includes(context.lifecyclePhase)) {
        errors.push(
            `Agent ${contract.agentType} cannot execute in ${context.lifecyclePhase} phase. ` +
            `Allowed phases: ${contract.allowedPhases.join(', ')}`
        );
    }

    // Check required context
    for (const required of contract.requiredContext) {
        if (!(required in context)) {
            errors.push(`Missing required context: ${required}`);
        }
    }

    return {
        valid: errors.length === 0,
        errors,
    };
}

/**
 * Create audit log entry
 */
export function createAuditLogEntry(
    contract: AgentExecutionContract,
    action: AgentAction,
    input: unknown,
    output: unknown,
    success: boolean,
    error?: string
): AuditLogEntry {
    return {
        timestamp: Date.now(),
        agentType: contract.agentType,
        action,
        input,
        output,
        success,
        error,
    };
}
