/**
 * YAPPC Task System - Type Definitions
 *
 * Comprehensive type system for the task execution framework.
 * Extends and integrates with existing DevSecOps types.
 *
 * @module types/tasks
 */

import type { Priority } from '../devsecops';
import type { WorkflowCategory } from '../workflow';

// ============================================================================
// Lifecycle Stage Types
// ============================================================================

/**
 * The 8 lifecycle stages from the Developer Lifecycle Model
 */
export type LifecycleStage =
    | 'intent'
    | 'context'
    | 'plan'
    | 'execute'
    | 'verify'
    | 'observe'
    | 'learn'
    | 'institutionalize';

/**
 * Lifecycle stage groupings (aligned with 8-stage lifecycle)
 */
export type StageGroup = 'planning' | 'building' | 'operating' | 'learning';

/**
 * Lifecycle stage configuration
 */
export interface LifecycleStageConfig {
    id: LifecycleStage;
    name: string;
    description: string;
    order: number;
    icon: string;
    color: string;
    required: boolean;
    entryCriteria: string[];
    exitCriteria: string[];
    typicalActivities: string[];
    artifacts: string[];
}

/**
 * Stage group configuration
 */
export interface StageGroupConfig {
    id: StageGroup;
    name: string;
    description: string;
    stages: LifecycleStage[];
    color: string;
}

// ============================================================================
// Task Domain Types
// ============================================================================

/**
 * Task domain identifiers (26 domains from IC Task Universe)
 */
export type TaskDomainId =
    | 'intent-problem-framing'
    | 'stakeholder-user-understanding'
    | 'context-gathering-synthesis'
    | 'requirements-engineering'
    | 'research-discovery'
    | 'experimentation-prototyping'
    | 'product-experience-design'
    | 'visual-brand-design'
    | 'system-architecture-design'
    | 'task-decomposition-estimation'
    | 'dependency-program-coordination'
    | 'change-release-planning'
    | 'software-development'
    | 'data-ml-development'
    | 'content-documentation-production'
    | 'testing-qa'
    | 'security-engineering'
    | 'privacy-risk-compliance'
    | 'build-cicd-deployment'
    | 'operations-monitoring'
    | 'incident-response-recovery'
    | 'customer-internal-support'
    | 'observability-analytics'
    | 'performance-optimization'
    | 'knowledge-management'
    | 'continuous-improvement';

/**
 * Task automation level
 */
export type AutomationLevel = 'manual' | 'assisted' | 'automated';

/**
 * Persona type (developer roles)
 */
export type Persona =
    | 'Developer'
    | 'Tech Lead'
    | 'PM'
    | 'Security'
    | 'DevOps'
    | 'QA';

// ============================================================================
// Audit Artifact Types
// ============================================================================

/**
 * Types of audit artifacts that can be captured
 */
export type AuditArtifactType =
    | 'InputSnapshot'
    | 'OutputSnapshot'
    | 'ChangeSet'
    | 'DecisionRecord'
    | 'VerificationResult'
    | 'PolicyCheck'
    | 'IncidentRecord'
    | 'RootCauseReport'
    | 'PerformanceReport'
    | 'SecurityReview'
    | 'VulnerabilityReport'
    | 'ComplianceReport'
    | 'ReviewComments'
    | 'ADR'
    | 'SchemaDesign'
    | 'DataModel'
    | 'PipelineDesign'
    | 'BackupPlan'
    | 'MigrationRecord'
    | 'BugFixRecord'
    | 'TechDebtRecord'
    | 'DependencyReport'
    | 'ProgressRecord'
    | 'AIGeneratedCode'
    | 'AIAnalysis'
    | 'AuditTrail';

// ============================================================================
// Task UI Configuration
// ============================================================================

/**
 * Task UI display configuration
 */
export interface TaskUIConfig {
    icon: string;
    color: string;
    tags: string[];
    inputHints?: string[];
    outputHints?: string[];
}

// ============================================================================
// Core Task Definition
// ============================================================================

/**
 * Complete task definition as loaded from YAML
 */
export interface TaskDefinition {
    /** Unique task identifier (e.g., prob-001, impl-003) */
    id: string;
    /** Human-readable task name */
    name: string;
    /** Detailed task description */
    description: string;
    /** Personas who typically perform this task */
    personas: Persona[];
    /** Lifecycle stages where this task applies */
    lifecycleStages: LifecycleStage[];
    /** Level of automation support */
    automationLevel: AutomationLevel;
    /** Required capabilities/skills */
    requiredCapabilities: string[];
    /** Input data schema reference */
    inputSchema: string;
    /** Output data schema reference */
    outputSchema: string;
    /** Audit artifacts to capture */
    auditArtifacts: AuditArtifactType[];
    /** UI configuration */
    ui: TaskUIConfig;
}

/**
 * Task domain configuration
 */
export interface TaskDomainConfig {
    id: TaskDomainId;
    name: string;
    description: string;
    order: number;
    icon: string;
    color: string;
    lifecycleStages: LifecycleStage[];
    /** Agent personas that can work on this domain */
    agentPersonas?: string[];
    /** Audit artifact types produced by this domain */
    auditArtifacts?: string[];
    /** Expected inputs for this domain */
    inputs?: string[];
    /** Expected outputs from this domain */
    outputs?: string[];
}

/**
 * Complete domain with tasks
 */
export interface TaskDomain extends TaskDomainConfig {
    tasks: TaskDefinition[];
}

// ============================================================================
// Task Execution Types
// ============================================================================

/**
 * Task execution status
 */
export type TaskExecutionStatus =
    | 'pending'
    | 'in-progress'
    | 'blocked'
    | 'completed'
    | 'failed'
    | 'skipped';

/**
 * Task execution instance
 */
export interface TaskExecution {
    /** Unique execution ID */
    id: string;
    /** Reference to task definition */
    taskId: string;
    /** Reference to workflow execution */
    workflowExecutionId?: string;
    /** Current status */
    status: TaskExecutionStatus;
    /** Priority */
    priority: Priority;
    /** Assigned user */
    assignee?: string;
    /** Start timestamp */
    startedAt?: Date;
    /** Completion timestamp */
    completedAt?: Date;
    /** Input data */
    input?: Record<string, unknown>;
    /** Output data */
    output?: Record<string, unknown>;
    /** Captured audit artifacts */
    auditArtifacts: CapturedArtifact[];
    /** Error information if failed */
    error?: TaskError;
    /** Metadata */
    metadata?: Record<string, unknown>;
}

/**
 * Captured audit artifact
 */
export interface CapturedArtifact {
    type: AuditArtifactType;
    capturedAt: Date;
    data: unknown;
    hash?: string;
}

/**
 * Task error information
 */
export interface TaskError {
    code: string;
    message: string;
    details?: unknown;
    stack?: string;
}

// ============================================================================
// Workflow Types
// ============================================================================

export type { WorkflowCategory } from '../workflow';

/**
 * Workflow phase definition
 */
export interface WorkflowPhase {
    id: string;
    name: string;
    stages: LifecycleStage[];
    tasks: string[]; // Task IDs
}

/**
 * Complete workflow definition
 */
export interface WorkflowDefinition {
    id: string;
    name: string;
    description: string;
    category: WorkflowCategory;
    icon: string;
    color: string;
    estimatedDuration: string;
    lifecycleStages: LifecycleStage[];
    phases: WorkflowPhase[];
}

/**
 * Workflow execution status
 */
export type WorkflowExecutionStatus =
    | 'pending'
    | 'in-progress'
    | 'paused'
    | 'completed'
    | 'failed'
    | 'cancelled';

/**
 * Workflow execution instance
 */
export interface WorkflowExecution {
    id: string;
    workflowId: string;
    status: WorkflowExecutionStatus;
    currentPhase?: string;
    currentStage?: LifecycleStage;
    startedAt?: Date;
    completedAt?: Date;
    taskExecutions: TaskExecution[];
    metadata?: Record<string, unknown>;
}

// ============================================================================
// Stage Transition Types
// ============================================================================

/**
 * Transition type
 */
export type TransitionType = 'forward' | 'backward' | 'skip';

/**
 * Transition trigger
 */
export type TransitionTrigger =
    | 'manual'
    | 'auto_on_criteria'
    | 'auto_on_failure'
    | 'auto_on_alert'
    | 'scheduled';

/**
 * Stage transition definition
 */
export interface StageTransition {
    from: LifecycleStage;
    to: LifecycleStage;
    type: TransitionType;
    description: string;
    requiredArtifacts?: string[];
    conditions?: string[];
    reason?: string;
    triggers: TransitionTrigger[];
}

/**
 * Transition rule
 */
export interface TransitionRule {
    id: string;
    description: string;
    enforcement: 'required' | 'warning' | 'auto';
    maxCount?: number;
}

// ============================================================================
// Registry Types
// ============================================================================

/**
 * Task registry state
 */
export interface TaskRegistryState {
    domains: Map<TaskDomainId, TaskDomain>;
    tasks: Map<string, TaskDefinition>;
    workflows: Map<string, WorkflowDefinition>;
    stages: Map<LifecycleStage, LifecycleStageConfig>;
    transitions: StageTransition[];
    isLoaded: boolean;
    lastUpdated?: Date;
}

/**
 * Task query filter
 */
export interface TaskFilter {
    domain?: TaskDomainId;
    stage?: LifecycleStage;
    persona?: Persona;
    automationLevel?: AutomationLevel;
    tags?: string[];
    capabilities?: string[];
    search?: string;
}

/**
 * Workflow query filter
 */
export interface WorkflowFilter {
    category?: WorkflowCategory;
    stages?: LifecycleStage[];
    search?: string;
}

// ============================================================================
// Export all types
// ============================================================================

export type {
    Priority,
    ItemStatus,
    UserRole
} from '../devsecops';
