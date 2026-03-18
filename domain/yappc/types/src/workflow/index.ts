/**
 * Workflow domain types for the Unified Workflow UX system.
 *
 * @doc.type module
 * @doc.purpose Workflow type definitions
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// ENUMS
// ============================================================================

/**
 * Workflow lifecycle steps.
 */
export type WorkflowStep =
    | 'INTENT'
    | 'CONTEXT'
    | 'PLAN'
    | 'EXECUTE'
    | 'VERIFY'
    | 'OBSERVE'
    | 'LEARN'
    | 'INSTITUTIONALIZE';

/**
 * All workflow steps in order.
 */
export const WORKFLOW_STEPS: WorkflowStep[] = [
    'INTENT',
    'CONTEXT',
    'PLAN',
    'EXECUTE',
    'VERIFY',
    'OBSERVE',
    'LEARN',
    'INSTITUTIONALIZE',
];

/**
 * Workflow types supported by the system.
 */
export type WorkflowType =
    // Ideation & Demand
    | 'NEW_PRODUCT'
    | 'FEATURE_REQUEST'
    | 'REGULATORY_REQ'
    // Architecture & Design
    | 'ARCHITECTURE_REVIEW'
    | 'THREAT_MODEL'
    | 'DATA_CLASSIFICATION'
    // Development
    | 'FEATURE'
    | 'BUG_FIX'
    | 'REFACTOR'
    | 'DEPENDENCY_UPDATE'
    | 'TESTING'
    | 'DOCUMENTATION'
    // Build & CI
    | 'CI_PIPELINE'
    | 'SAST_SCAN'
    | 'IMAGE_BUILD'
    // Release & Deploy
    | 'RELEASE'
    | 'DEPLOYMENT'
    | 'ROLLBACK'
    // Runtime Operations
    | 'INCIDENT'
    | 'SCALING_EVENT'
    | 'CAPACITY_PLAN'
    | 'INFRASTRUCTURE'
    // SecOps
    | 'SECURITY_INCIDENT'
    | 'VULNERABILITY_REMEDIATION'
    | 'ACCESS_REVIEW'
    | 'SECURITY_UPDATE'
    // GRC & Compliance
    | 'AUDIT_PREP'
    | 'POLICY_REVIEW'
    | 'RISK_ASSESSMENT'
    // Optimization
    | 'POST_MORTEM'
    | 'COST_OPTIMIZATION'
    // Institutionalization
    | 'TEMPLATE_CREATION'
    | 'GUARDRAIL_UPDATE'
    // Legacy (for backward compatibility)
    | 'SECURITY'
    | 'OPTIMIZATION'
    | 'MIGRATION'
    | 'SUPPORT'
    | 'AI_ASSIST';

/**
 * Workflow category (DevSecOps stage).
 * Maps to the 10-stage DevSecOps lifecycle.
 *
 * @doc.type enum
 * @doc.purpose Categorize workflows by DevSecOps lifecycle stage
 * @doc.layer product
 */
export type WorkflowCategory =
    | 'IDEATION'
    | 'ARCHITECTURE'
    | 'DEVELOPMENT'
    | 'BUILD'
    | 'RELEASE'
    | 'OPERATIONS'
    | 'SECOPS'
    | 'GRC'
    | 'OPTIMIZATION'
    | 'INSTITUTIONALIZATION';

/**
 * Mapping of WorkflowType to WorkflowCategory.
 */
export const WORKFLOW_TYPE_TO_CATEGORY: Record<WorkflowType, WorkflowCategory> = {
    // Ideation & Demand
    NEW_PRODUCT: 'IDEATION',
    FEATURE_REQUEST: 'IDEATION',
    REGULATORY_REQ: 'IDEATION',
    // Architecture & Design
    ARCHITECTURE_REVIEW: 'ARCHITECTURE',
    THREAT_MODEL: 'ARCHITECTURE',
    DATA_CLASSIFICATION: 'ARCHITECTURE',
    // Development
    FEATURE: 'DEVELOPMENT',
    BUG_FIX: 'DEVELOPMENT',
    REFACTOR: 'DEVELOPMENT',
    DEPENDENCY_UPDATE: 'DEVELOPMENT',
    // Build & CI
    CI_PIPELINE: 'BUILD',
    SAST_SCAN: 'BUILD',
    IMAGE_BUILD: 'BUILD',
    // Release & Deploy
    RELEASE: 'RELEASE',
    DEPLOYMENT: 'RELEASE',
    ROLLBACK: 'RELEASE',
    // Runtime Operations
    INCIDENT: 'OPERATIONS',
    SCALING_EVENT: 'OPERATIONS',
    CAPACITY_PLAN: 'OPERATIONS',
    INFRASTRUCTURE: 'OPERATIONS',
    // SecOps
    SECURITY_INCIDENT: 'SECOPS',
    VULNERABILITY_REMEDIATION: 'SECOPS',
    ACCESS_REVIEW: 'SECOPS',
    SECURITY_UPDATE: 'SECOPS',
    // GRC & Compliance
    AUDIT_PREP: 'GRC',
    POLICY_REVIEW: 'GRC',
    RISK_ASSESSMENT: 'GRC',
    // Optimization
    POST_MORTEM: 'OPTIMIZATION',
    COST_OPTIMIZATION: 'OPTIMIZATION',
    // Institutionalization
    TEMPLATE_CREATION: 'INSTITUTIONALIZATION',
    GUARDRAIL_UPDATE: 'INSTITUTIONALIZATION',
    // Legacy mappings
    SECURITY: 'SECOPS',
    DOCUMENTATION: 'DEVELOPMENT',
    TESTING: 'DEVELOPMENT',
    OPTIMIZATION: 'OPTIMIZATION',
    MIGRATION: 'DEVELOPMENT',
    SUPPORT: 'OPERATIONS',
    AI_ASSIST: 'DEVELOPMENT',
};

/**
 * Workflow status.
 */
export type WorkflowStatus =
    | 'DRAFT'
    | 'ACTIVE'
    | 'PAUSED'
    | 'COMPLETED'
    | 'CANCELLED';

/**
 * Step status within a workflow.
 */
export type StepStatus =
    | 'NOT_STARTED'
    | 'IN_PROGRESS'
    | 'COMPLETED'
    | 'REVISITED'
    | 'BLOCKED';

/**
 * AI mode for workflow execution.
 */
export type AIMode =
    | 'AI_AUTONOMOUS'
    | 'AI_ASSISTED'
    | 'HUMAN_ONLY';

/**
 * Audit action types.
 */
export type AuditAction =
    | 'CREATED'
    | 'STEP_STARTED'
    | 'STEP_COMPLETED'
    | 'STEP_REVISITED'
    | 'DATA_UPDATED'
    | 'AI_SUGGESTION_ACCEPTED'
    | 'AI_SUGGESTION_REJECTED'
    | 'STATUS_CHANGED'
    | 'OWNER_CHANGED';

// ============================================================================
// STEP DATA INTERFACES
// ============================================================================

/**
 * Intent step data.
 */
export interface IntentStepData {
    workflowType?: WorkflowType;
    goalStatement?: string;
    successCriteria?: string[];
}

/**
 * Context step data.
 */
export interface ContextStepData {
    systemsImpacted?: string[];
    constraints?: string[];
    references?: ContextReference[];
}

export interface ContextReference {
    id: string;
    type: 'TICKET' | 'REPO' | 'SERVICE' | 'WORKFLOW' | 'DOCUMENT';
    name: string;
    url?: string;
}

/**
 * Plan step data.
 */
export interface PlanStepData {
    tasks?: PlanTask[];
    riskAssessment?: RiskAssessment;
    hasRollbackPlan?: boolean;
}

export interface PlanTask {
    id: string;
    title: string;
    status: 'TODO' | 'IN_PROGRESS' | 'DONE';
    assignee?: string;
}

export interface RiskAssessment {
    level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    factors: string[];
    mitigations: string[];
    hasRollbackPlan?: boolean;
    rollbackPlan?: string;
}

/**
 * Execute step data.
 */
export interface ExecuteStepData {
    changes?: ChangeRecord[];
    progress?: number;
}

export interface ChangeRecord {
    id: string;
    type: 'CODE' | 'CONFIG' | 'TEST' | 'DOCS' | 'INFRASTRUCTURE';
    description: string;
    status: 'PENDING' | 'IN_REVIEW' | 'MERGED' | 'ROLLED_BACK' | 'COMPLETED';
    prUrl?: string;
    path?: string;
}

/**
 * Verify step data.
 */
export interface VerifyStepData {
    verificationStatus: 'PENDING' | 'IN_PROGRESS' | 'PASSED' | 'FAILED';
    acceptanceChecklist?: ChecklistItem[];
    evidence?: VerificationEvidence[];
}

export interface ChecklistItem {
    id: string;
    label: string;
    checked: boolean;
    verifiedBy?: string;
    verifiedAt?: string;
}

export interface VerificationEvidence {
    id: string;
    type: 'TEST_RESULT' | 'SCREENSHOT' | 'LOG' | 'MANUAL_CHECK';
    name: string;
    status: 'PASS' | 'FAIL';
    attachments?: string[];
    notes?: string;
}

/**
 * Observe step data.
 */
export interface ObserveStepData {
    metricsDelta: MetricsDelta;
    anomalies: Anomaly[];
    observationWindow: ObservationWindow;
}

export interface MetricsDelta {
    before: Record<string, number>;
    after: Record<string, number>;
    percentChange: Record<string, number>;
}

export interface Anomaly {
    id: string;
    type: 'REGRESSION' | 'SPIKE' | 'DROP' | 'ERROR_RATE' | 'LATENCY';
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    metric: string;
    description: string;
    detectedAt: string;
    resolved: boolean;
}

export interface ObservationWindow {
    startedAt: string;
    endedAt?: string;
    durationHours: number;
    status: 'ACTIVE' | 'EXTENDED' | 'COMPLETED';
}

/**
 * Learn step data.
 */
export interface LearnStepData {
    lessons: Lesson[];
    rootCauses: RootCause[];
}

export interface Lesson {
    id: string;
    category: 'WHAT_WORKED' | 'WHAT_DIDNT' | 'IMPROVEMENT';
    description: string;
    actionable: boolean;
}

export interface RootCause {
    id: string;
    category: 'PROCESS' | 'TECHNOLOGY' | 'PEOPLE' | 'EXTERNAL';
    description: string;
    contributingFactors: string[];
}

/**
 * Institutionalize step data.
 */
export interface InstitutionalizeStepData {
    actions?: InstitutionalAction[];
    approvalChain?: string[];
    effectiveDate?: string;
}

export interface InstitutionalAction {
    id: string;
    type: 'CHECKLIST' | 'TEMPLATE' | 'ADR' | 'RUNBOOK' | 'POLICY';
    title: string;
    owner: string;
    enforcementLevel: number;
    status: 'PENDING' | 'APPROVED' | 'REJECTED';
    approvers?: string[];
}

// ============================================================================
// WORKFLOW STATE
// ============================================================================

/**
 * Individual step state within a workflow.
 */
export interface WorkflowStepState<T = unknown> {
    status: StepStatus;
    data: T;
    startedAt?: string;
    completedAt?: string;
    revisitCount: number;
    aiConfidence?: number;
    blockedReason?: string;
}

/**
 * Complete workflow steps state.
 */
export interface WorkflowSteps {
    intent: WorkflowStepState<IntentStepData>;
    context: WorkflowStepState<ContextStepData>;
    plan: WorkflowStepState<PlanStepData>;
    execute: WorkflowStepState<ExecuteStepData>;
    verify: WorkflowStepState<VerifyStepData>;
    observe: WorkflowStepState<ObserveStepData>;
    learn: WorkflowStepState<LearnStepData>;
    institutionalize: WorkflowStepState<InstitutionalizeStepData>;
}

/**
 * Audit entry for workflow changes.
 */
export interface WorkflowAuditEntry {
    id: string;
    action: AuditAction;
    step?: WorkflowStep;
    userId: string;
    userName: string;
    timestamp: string;
    details?: Record<string, unknown>;
    previousValue?: unknown;
    newValue?: unknown;
}

/**
 * Workflow metrics.
 */
export interface WorkflowMetrics {
    totalDurationHours?: number;
    stepDurations: Partial<Record<WorkflowStep, number>>;
    revisitCount: number;
    aiSuggestionsAccepted: number;
    aiSuggestionsRejected: number;
    blockedCount: number;
}

/**
 * Complete workflow state (canonical schema).
 */
export interface Workflow {
    id: string;
    workflowType: WorkflowType;
    category: WorkflowCategory;
    name: string;
    description?: string;
    currentStep: WorkflowStep;
    status: WorkflowStatus;
    aiMode: AIMode;
    ownerId: string;
    ownerName: string;
    contributors: WorkflowContributor[];
    steps: WorkflowSteps;
    audit: WorkflowAuditEntry[];
    metrics: WorkflowMetrics;
    templateId?: string;
    projectId?: string;
    createdAt: string;
    updatedAt: string;
}

export interface WorkflowContributor {
    userId: string;
    userName: string;
    role: 'OWNER' | 'CONTRIBUTOR' | 'REVIEWER';
    joinedAt: string;
}

// ============================================================================
// WORKFLOW TEMPLATES
// ============================================================================

/**
 * Workflow template definition.
 */
export interface WorkflowTemplate {
    id: string;
    name: string;
    description: string;
    workflowType: WorkflowType;
    category: WorkflowCategory;
    defaultIntent: Partial<IntentStepData>;
    requiredFields: Partial<Record<WorkflowStep, string[]>>;
    defaultRisks: string[];
    defaultMetrics: string[];
    isSystem: boolean;
    createdAt: string;
    updatedAt: string;
}

// ============================================================================
// AGENT ORCHESTRATION TYPES
// ============================================================================

/**
 * Agent execution status.
 *
 * @doc.type enum
 * @doc.purpose Track agent execution lifecycle
 * @doc.layer product
 */
export type AgentExecutionStatus =
    | 'PENDING'
    | 'RUNNING'
    | 'COMPLETED'
    | 'FAILED'
    | 'SKIPPED';

/**
 * Agent definition for step composition.
 * Each step can contain multiple agents that run in sequence or parallel.
 *
 * @doc.type interface
 * @doc.purpose Define an agent within a workflow step
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface AgentDefinition {
    /** Unique identifier for the agent */
    id: string;
    /** Human-readable name */
    name: string;
    /** Agent role (e.g., Security Architect, QA Engineer) */
    role: string;
    /** Reference to the operator YAML spec */
    operatorRef: string;
    /** Execution mode: 'auto' runs without human input, 'hitl' requires human approval */
    executionMode: 'auto' | 'hitl';
    /** Order of execution within the step (lower runs first) */
    order: number;
    /** Dependencies on other agents (by id) that must complete first */
    dependsOn: string[];
}

/**
 * Agent execution record for audit and tracking.
 *
 * @doc.type interface
 * @doc.purpose Track agent execution within a step
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface AgentExecution {
    /** Agent definition reference */
    agentId: string;
    /** Current execution status */
    status: AgentExecutionStatus;
    /** When execution started */
    startedAt?: string;
    /** When execution completed */
    completedAt?: string;
    /** Duration in milliseconds */
    durationMs?: number;
    /** Error message if failed */
    errorMessage?: string;
    /** Output artifacts from agent */
    outputs?: Record<string, unknown>;
}

/**
 * Step agent composition - defines which agents run for a given step + category combination.
 *
 * @doc.type interface
 * @doc.purpose Map agents to specific steps based on workflow category
 * @doc.layer product
 * @doc.pattern Configuration
 */
export interface StepAgentComposition {
    /** The workflow step */
    step: WorkflowStep;
    /** The workflow category this composition applies to */
    category: WorkflowCategory;
    /** Agents to execute for this step/category combination */
    agents: AgentDefinition[];
}

// ============================================================================
// API TYPES
// ============================================================================

/**
 * Create workflow request.
 */
export interface CreateWorkflowInput {
    name: string;
    description?: string;
    workflowType: WorkflowType;
    templateId?: string;
    projectId?: string;
    aiMode?: AIMode;
}

/**
 * Update step data request.
 */
export interface UpdateStepDataInput {
    workflowId: string;
    step: WorkflowStep;
    data: unknown;
}

/**
 * Advance step request.
 */
export interface AdvanceStepInput {
    workflowId: string;
    fromStep: WorkflowStep;
    toStep: WorkflowStep;
}

// ============================================================================
// DEFAULT VALUES
// ============================================================================

/**
 * Create default step state.
 */
export function createDefaultStepState<T>(defaultData: T): WorkflowStepState<T> {
    return {
        status: 'NOT_STARTED',
        data: defaultData,
        revisitCount: 0,
    };
}

/**
 * Create default workflow steps.
 */
export function createDefaultWorkflowSteps(): WorkflowSteps {
    return {
        intent: createDefaultStepState<IntentStepData>({
            workflowType: 'FEATURE',
            goalStatement: '',
            successCriteria: [],
        }),
        context: createDefaultStepState<ContextStepData>({
            systemsImpacted: [],
            constraints: [],
            references: [],
        }),
        plan: createDefaultStepState<PlanStepData>({
            tasks: [],
            riskAssessment: {
                level: 'LOW',
                factors: [],
                mitigations: [],
            },
            hasRollbackPlan: false,
        }),
        execute: createDefaultStepState<ExecuteStepData>({
            changes: [],
            progress: 0,
        }),
        verify: createDefaultStepState<VerifyStepData>({
            verificationStatus: 'PENDING',
            acceptanceChecklist: [],
            evidence: [],
        }),
        observe: createDefaultStepState<ObserveStepData>({
            metricsDelta: { before: {}, after: {}, percentChange: {} },
            anomalies: [],
            observationWindow: {
                startedAt: new Date().toISOString(),
                durationHours: 24,
                status: 'ACTIVE',
            },
        }),
        learn: createDefaultStepState<LearnStepData>({
            lessons: [],
            rootCauses: [],
        }),
        institutionalize: createDefaultStepState<InstitutionalizeStepData>({
            actions: [],
            approvalChain: [],
        }),
    };
}

/**
 * Create default workflow metrics.
 */
export function createDefaultMetrics(): WorkflowMetrics {
    return {
        stepDurations: {},
        revisitCount: 0,
        aiSuggestionsAccepted: 0,
        aiSuggestionsRejected: 0,
        blockedCount: 0,
    };
}

/**
 * Create a new default workflow with the given properties.
 */
export interface CreateDefaultWorkflowOptions {
    id: string;
    title: string;
    type: WorkflowType;
    aiMode?: AIMode;
    createdBy: string;
    description?: string;
}

export function createDefaultWorkflow(options: CreateDefaultWorkflowOptions): Workflow {
    const now = new Date().toISOString();
    const category = WORKFLOW_TYPE_TO_CATEGORY[options.type];
    return {
        id: options.id,
        workflowType: options.type,
        category,
        name: options.title,
        description: options.description,
        currentStep: 'INTENT',
        status: 'DRAFT',
        aiMode: options.aiMode || 'AI_ASSISTED',
        ownerId: options.createdBy,
        ownerName: options.createdBy,
        contributors: [
            {
                userId: options.createdBy,
                userName: options.createdBy,
                role: 'OWNER',
                joinedAt: now,
            },
        ],
        steps: createDefaultWorkflowSteps(),
        audit: [],
        metrics: createDefaultMetrics(),
        createdAt: now,
        updatedAt: now,
    };
}
