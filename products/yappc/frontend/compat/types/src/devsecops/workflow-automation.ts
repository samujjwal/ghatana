/**
 * DevSecOps Workflow Automation System
 * 
 * Comprehensive type definitions for automated task processing, workflow orchestration,
 * and multi-agent collaboration across the DevSecOps lifecycle from ideation to enhancement.
 * 
 * This module EXTENDS (not duplicates) the base AI agent types.
 * - WorkflowAgentRole: Persona-based roles that map to AgentName implementations
 * - WorkflowAgentMetrics: Analytics-focused metrics (distinct from base AgentMetrics)
 * 
 * @module types/devsecops/workflow-automation
 * @doc.type module
 * @doc.purpose Workflow automation and agent orchestration types
 * @doc.layer product
 * @doc.pattern Domain Model
 * @see libs/ai/src/agents/types.ts for base AgentName, IAIAgent, AgentContext, AgentResult
 */

import type { Phase, User, Priority } from './index';

// ============================================================================
// BASE AGENT TYPE RE-DECLARATIONS
// ============================================================================
// These mirror the types from @ghatana/yappc-ai to avoid circular dependency
// (types package should not depend on implementation packages)
// See: libs/ai/src/agents/types.ts for authoritative definitions

/**
 * Agent names registry - matches @ghatana/yappc-ai AgentName type
 * @see libs/ai/src/agents/types.ts
 */
export type AgentName =
    | 'CopilotAgent'
    | 'QueryParserAgent'
    | 'PredictionAgent'
    | 'AnomalyDetectorAgent'
    | 'CodeGeneratorAgent'
    | 'SentimentAgent'
    | 'RecommendationAgent'
    | 'SearchAgent'
    | 'WorkflowRouterAgent'
    | 'DocGeneratorAgent'
    | 'PRAnalyzerAgent'
    | 'TicketClassifierAgent';

/**
 * Agent execution context - matches @ghatana/yappc-ai AgentContext
 * @see libs/ai/src/agents/types.ts
 */
export interface AgentContext {
    userId: string;
    workspaceId: string;
    requestId: string;
    permissions: string[];
    timeout?: number;
    metadata?: Record<string, unknown>;
}

/**
 * Agent health status - matches @ghatana/yappc-ai AgentHealth
 * @see libs/ai/src/agents/types.ts
 */
export interface AgentHealth {
    healthy: boolean;
    latency: number;
    lastCheck: Date;
    dependencies: Record<string, 'healthy' | 'degraded' | 'unhealthy'>;
    errorMessage?: string;
}

/**
 * Agent metadata - matches @ghatana/yappc-ai AgentMetadata
 * @see libs/ai/src/agents/types.ts
 */
export interface AgentMetadata {
    name: AgentName;
    version: string;
    description: string;
    capabilities: string[];
    supportedModels: string[];
    latencySLA: number;
    costPerRequest?: number;
}

/**
 * Agent execution metrics - matches @ghatana/yappc-ai base AgentMetrics
 * Note: This is DIFFERENT from WorkflowAgentMetrics (analytics-focused)
 * @see libs/ai/src/agents/types.ts
 */
export interface BaseAgentMetrics {
    latencyMs: number;
    tokensUsed?: number;
    modelVersion: string;
    confidence?: number;
    cost?: number;
}

/**
 * Agent execution result - matches @ghatana/yappc-ai AgentResult
 * @see libs/ai/src/agents/types.ts
 */
export interface AgentResult<T> {
    success: boolean;
    data?: T;
    metrics: BaseAgentMetrics;
}

// ============================================================================
// WORKFLOW ORCHESTRATION TYPES
// ============================================================================

/**
 * Workflow state machine states
 */
export type WorkflowState =
    | 'pending'        // Waiting for prerequisites
    | 'ready'          // Ready to start
    | 'assigned'       // Assigned to agent/user
    | 'in-progress'    // Being processed
    | 'review'         // Under review
    | 'approved'       // Approved to proceed
    | 'rejected'       // Rejected, needs rework
    | 'blocked'        // Blocked by dependencies
    | 'completed'      // Successfully completed
    | 'failed'         // Failed execution
    | 'cancelled';     // Cancelled by user/system

/**
 * Workflow transition triggers
 */
export type WorkflowTrigger =
    | 'manual'         // Manual user action
    | 'automatic'      // Automatic based on rules
    | 'scheduled'      // Time-based trigger
    | 'event'          // External event
    | 'dependency'     // Dependency completion
    | 'agent';         // Agent decision

/**
 * Workflow state transition
 */
export interface WorkflowTransition {
    id: string;
    fromState: WorkflowState;
    toState: WorkflowState;
    trigger: WorkflowTrigger;
    condition?: TransitionCondition;
    actions?: TransitionAction[];
    timestamp: string;
    triggeredBy: User | Agent;
    reason?: string;
    metadata?: Record<string, unknown>;
}

/**
 * Condition for workflow transition
 */
export interface TransitionCondition {
    type: 'expression' | 'approval' | 'validation' | 'dependency' | 'custom';
    expression?: string;
    requiredApprovals?: number;
    validators?: Validator[];
    dependencies?: string[];
    custom?: (context: WorkflowContext) => Promise<boolean>;
}

/**
 * Action to execute on transition
 */
export interface TransitionAction {
    type: 'notify' | 'assign' | 'update' | 'create' | 'execute' | 'webhook';
    target: string;
    parameters: Record<string, unknown>;
    priority: Priority;
    retryPolicy?: RetryPolicy;
}

/**
 * Retry policy for actions
 */
export interface RetryPolicy {
    maxAttempts: number;
    backoffType: 'fixed' | 'exponential' | 'linear';
    initialDelayMs: number;
    maxDelayMs: number;
    retryableErrors?: string[];
}

/**
 * Workflow definition
 */
export interface Workflow {
    id: string;
    name: string;
    description: string;
    version: string;
    phases: WorkflowPhase[];
    initialState: WorkflowState;
    finalStates: WorkflowState[];
    transitions: WorkflowTransition[];
    rules: WorkflowRule[];
    agents: AgentAssignment[];
    createdAt: string;
    updatedAt: string;
    createdBy: User;
    enabled: boolean;
    metadata?: Record<string, unknown>;
}

/**
 * Workflow phase configuration
 */
export interface WorkflowPhase {
    phaseId: string;
    order: number;
    required: boolean;
    parallelExecution: boolean;
    entryConditions?: TransitionCondition[];
    exitConditions?: TransitionCondition[];
    estimatedDuration?: number; // minutes
    agents: string[]; // Agent IDs that can work on this phase
    autoProgressOn?: WorkflowEvent[];
}

/**
 * Workflow rule for automation
 */
export interface WorkflowRule {
    id: string;
    name: string;
    description: string;
    enabled: boolean;
    priority: number;
    trigger: RuleTrigger;
    conditions: RuleCondition[];
    actions: RuleAction[];
    schedule?: RuleSchedule;
}

/**
 * Rule trigger configuration
 */
export interface RuleTrigger {
    type: 'item_created' | 'item_updated' | 'status_changed' | 'phase_entered' |
    'time_elapsed' | 'dependency_met' | 'custom_event';
    filters?: Record<string, unknown>;
    debounceMs?: number;
}

/**
 * Rule condition to evaluate
 */
export interface RuleCondition {
    field: string;
    operator: 'eq' | 'ne' | 'gt' | 'lt' | 'gte' | 'lte' | 'in' | 'contains' | 'matches';
    value: unknown;
    negate?: boolean;
}

/**
 * Rule action to execute
 */
export interface RuleAction {
    type: 'assign_agent' | 'update_field' | 'create_subtask' | 'notify' |
    'escalate' | 'delegate' | 'approve' | 'reject';
    parameters: Record<string, unknown>;
    runAsync?: boolean;
}

/**
 * Rule schedule for periodic execution
 */
export interface RuleSchedule {
    cron: string;
    timezone: string;
    enabled: boolean;
    lastRun?: string;
    nextRun?: string;
}

/**
 * Workflow context for execution
 */
export interface WorkflowContext {
    workflowId: string;
    itemId: string;
    currentPhase: Phase;
    currentState: WorkflowState;
    history: WorkflowTransition[];
    variables: Record<string, unknown>;
    permissions: string[];
    timeout?: number;
}

/**
 * Workflow event for triggers
 */
export interface WorkflowEvent {
    id: string;
    type: string;
    workflowId: string;
    itemId: string;
    timestamp: string;
    data: Record<string, unknown>;
    source: 'user' | 'agent' | 'system';
}

// ============================================================================
// AGENT SYSTEM TYPES
// ============================================================================

/**
 * Workflow agent roles in the DevSecOps lifecycle.
 * 
 * These are PERSONA-BASED ROLES that map to underlying AgentName implementations.
 * Each role defines a specialized capability set for workflow automation.
 * 
 * @doc.type type-alias
 * @doc.purpose Define persona-based agent roles for workflow stages
 * @doc.layer product
 * @doc.pattern Value Object
 * @see AgentName for base agent identifiers
 */
export type WorkflowAgentRole =
    // Persona-based agents
    | 'product-manager'
    | 'architect'
    | 'developer'
    | 'security-engineer'
    | 'devops-engineer'
    | 'qa-engineer'
    | 'sre'
    | 'compliance-officer'

    // Task-performing agents
    | 'code-generator'
    | 'test-generator'
    | 'documentation-generator'
    | 'security-scanner'
    | 'dependency-analyzer'
    | 'performance-analyzer'

    // Orchestration agents
    | 'task-planner'
    | 'task-delegator'
    | 'task-reviewer'
    | 'workflow-orchestrator'
    | 'dependency-resolver'

    // Analysis agents
    | 'requirement-analyzer'
    | 'risk-assessor'
    | 'impact-analyzer'
    | 'cost-estimator'

    // Communication agents
    | 'notification-agent'
    | 'escalation-agent'
    | 'collaboration-agent';

/**
 * Agent capability descriptor
 */
export interface AgentCapability {
    id: string;
    name: string;
    description: string;
    inputSchema: Record<string, unknown>;
    outputSchema: Record<string, unknown>;
    latencySLA: number; // milliseconds
    costPerExecution?: number;
    requiredPermissions: string[];
}

/**
 * Workflow Agent definition for DevSecOps automation.
 * 
 * This extends the concept of IAIAgent with workflow-specific properties.
 * Use with WorkflowAgentRole to define specialized automation agents.
 * 
 * @doc.type interface
 * @doc.purpose Workflow-specific agent configuration
 * @doc.layer product
 * @doc.pattern Domain Model
 * @see IAIAgent for base agent interface
 */
export interface WorkflowAgent {
    id: string;
    name: string;
    /** The persona-based role this agent fulfills */
    role: WorkflowAgentRole;
    /** Maps to underlying AgentName implementation */
    agentName: AgentName;
    description: string;
    version: string;
    capabilities: AgentCapability[];
    supportedPhases: string[]; // Phase IDs this agent can work on
    priority: number; // Higher priority agents are selected first
    enabled: boolean;
    autonomous: boolean; // Can act without human approval
    configuration: AgentConfiguration;
    performance: AgentPerformance;
    createdAt: string;
    updatedAt: string;
}

/**
 * Agent configuration
 */
export interface AgentConfiguration {
    model?: string; // AI model to use
    temperature?: number;
    maxTokens?: number;
    timeout: number;
    retryPolicy: RetryPolicy;
    approvalRequired: boolean;
    notifyOnCompletion: boolean;
    notifyOnFailure: boolean;
    customSettings?: Record<string, unknown>;
}

/**
 * Agent performance metrics
 */
export interface AgentPerformance {
    totalExecutions: number;
    successfulExecutions: number;
    failedExecutions: number;
    averageLatencyMs: number;
    averageConfidence: number;
    lastExecutionAt?: string;
    uptime: number; // percentage
}

/**
 * Agent assignment to workflow/task
 */
export interface AgentAssignment {
    id: string;
    agentId: string;
    workflowId?: string;
    itemId?: string;
    phaseId?: string;
    assignedAt: string;
    assignedBy: User;
    status: 'pending' | 'active' | 'paused' | 'completed' | 'failed';
    priority: Priority;
    constraints?: AssignmentConstraints;
}

/**
 * Constraints for agent assignment
 */
export interface AssignmentConstraints {
    maxDuration?: number; // minutes
    requiredConfidence?: number; // 0-1
    mustReviewBefore?: Date;
    dependencies?: string[];
    resources?: ResourceConstraint[];
}

/**
 * Resource constraint
 */
export interface ResourceConstraint {
    type: 'cpu' | 'memory' | 'tokens' | 'api_calls' | 'cost';
    limit: number;
    current: number;
}

/**
 * Agent execution request
 */
export interface AgentExecutionRequest {
    id: string;
    agentId: string;
    task: AgentTask;
    context: WorkflowContext;
    priority: Priority;
    requestedAt: string;
    requestedBy: User;
    deadline?: string;
}

/**
 * Agent task
 */
export interface AgentTask {
    id: string;
    type: AgentTaskType;
    title: string;
    description: string;
    input: Record<string, unknown>;
    expectedOutput: Record<string, unknown>;
    acceptanceCriteria?: string[];
    constraints?: TaskConstraints;
}

/**
 * Agent task types
 */
export type AgentTaskType =
    | 'analyze'
    | 'plan'
    | 'design'
    | 'implement'
    | 'test'
    | 'review'
    | 'approve'
    | 'deploy'
    | 'monitor'
    | 'optimize'
    | 'document'
    | 'notify';

/**
 * Task constraints
 */
export interface TaskConstraints {
    timeLimit?: number; // minutes
    costLimit?: number; // USD
    qualityThreshold?: number; // 0-1
    dependencies?: string[];
    requiredTools?: string[];
}

/**
 * Agent execution result
 */
export interface AgentExecutionResult {
    id: string;
    requestId: string;
    agentId: string;
    status: 'success' | 'partial' | 'failed';
    output: Record<string, unknown>;
    confidence: number; // 0-1
    reasoning?: string;
    suggestions?: AgentSuggestion[];
    metrics: ExecutionMetrics;
    startedAt: string;
    completedAt: string;
    error?: AgentExecutionError;
}

/**
 * Agent suggestion
 */
export interface AgentSuggestion {
    id: string;
    type: 'improvement' | 'alternative' | 'warning' | 'next_step';
    title: string;
    description: string;
    confidence: number;
    impact: 'low' | 'medium' | 'high';
    actionable: boolean;
    action?: TransitionAction;
}

/**
 * Agent execution error
 */
export interface AgentExecutionError {
    code: string;
    message: string;
    retryable: boolean;
    cause?: string;
    stack?: string;
}

/**
 * Execution metrics
 */
export interface ExecutionMetrics {
    durationMs: number;
    tokensUsed?: number;
    apiCalls?: number;
    cost?: number;
    resourceUsage?: Record<string, number>;
}

// ============================================================================
// DELEGATION & COLLABORATION TYPES
// ============================================================================

/**
 * Task delegation
 */
export interface TaskDelegation {
    id: string;
    fromAgent: string;
    toAgent: string;
    task: AgentTask;
    reason: string;
    priority: Priority;
    delegatedAt: string;
    acceptedAt?: string;
    completedAt?: string;
    status: 'pending' | 'accepted' | 'rejected' | 'completed' | 'escalated';
    result?: AgentExecutionResult;
}

/**
 * Agent collaboration session
 */
export interface CollaborationSession {
    id: string;
    participants: Agent[];
    objective: string;
    strategy: 'sequential' | 'parallel' | 'consensus' | 'competitive';
    currentPhase: string;
    messages: CollaborationMessage[];
    decisions: CollaborationDecision[];
    startedAt: string;
    completedAt?: string;
    outcome?: Record<string, unknown>;
}

/**
 * Collaboration message
 */
export interface CollaborationMessage {
    id: string;
    fromAgent: string;
    toAgents: string[];
    type: 'query' | 'response' | 'proposal' | 'objection' | 'agreement' | 'report';
    content: string;
    data?: Record<string, unknown>;
    timestamp: string;
    urgent: boolean;
}

/**
 * Collaboration decision
 */
export interface CollaborationDecision {
    id: string;
    topic: string;
    proposals: DecisionProposal[];
    selectedProposal?: string;
    decidedAt?: string;
    decidedBy: string; // Agent ID or 'consensus'
    reasoning: string;
    confidence: number;
}

/**
 * Decision proposal
 */
export interface DecisionProposal {
    id: string;
    proposedBy: string; // Agent ID
    title: string;
    description: string;
    pros: string[];
    cons: string[];
    estimatedCost?: number;
    estimatedDuration?: number;
    confidence: number;
    votes?: Record<string, 'for' | 'against' | 'abstain'>;
}

// ============================================================================
// REVIEW & APPROVAL TYPES
// ============================================================================

/**
 * Review request
 */
export interface ReviewRequest {
    id: string;
    itemId: string;
    reviewType: ReviewType;
    requestedBy: User | Agent;
    assignedTo: User | Agent;
    deadline?: string;
    priority: Priority;
    criteria: ReviewCriteria[];
    status: 'pending' | 'in-progress' | 'approved' | 'rejected' | 'needs-revision';
    createdAt: string;
    completedAt?: string;
}

/**
 * Review types
 */
export type ReviewType =
    | 'code-review'
    | 'security-review'
    | 'architecture-review'
    | 'design-review'
    | 'test-review'
    | 'documentation-review'
    | 'compliance-review'
    | 'peer-review';

/**
 * Review criteria
 */
export interface ReviewCriteria {
    id: string;
    category: string;
    description: string;
    required: boolean;
    weight: number; // 0-1
    automatable: boolean;
}

/**
 * Review result
 */
export interface ReviewResult {
    id: string;
    requestId: string;
    reviewer: User | Agent;
    decision: 'approved' | 'rejected' | 'needs-revision';
    score: number; // 0-100
    findings: ReviewFinding[];
    comments: string;
    recommendations?: string[];
    completedAt: string;
}

/**
 * Review finding
 */
export interface ReviewFinding {
    id: string;
    criteriaId: string;
    severity: 'critical' | 'major' | 'minor' | 'info';
    title: string;
    description: string;
    location?: string;
    suggestion?: string;
    autoFixable: boolean;
    confidence?: number;
}

/**
 * Validator for automated checks
 */
export interface Validator {
    id: string;
    name: string;
    type: 'syntax' | 'security' | 'performance' | 'style' | 'compliance' | 'custom';
    enabled: boolean;
    configuration: Record<string, unknown>;
    execute: (context: WorkflowContext) => Promise<ValidationResult>;
}

/**
 * Validation result
 */
export interface ValidationResult {
    valid: boolean;
    score: number; // 0-100
    issues: ValidationIssue[];
    warnings: string[];
    info: string[];
    executionTime: number;
}

/**
 * Validation issue
 */
export interface ValidationIssue {
    id: string;
    severity: 'error' | 'warning' | 'info';
    rule: string;
    message: string;
    location?: string;
    suggestedFix?: string;
}

// ============================================================================
// MONITORING & ANALYTICS TYPES
// ============================================================================

/**
 * Workflow analytics
 */
export interface WorkflowAnalytics {
    workflowId: string;
    timeRange: TimeRange;
    metrics: WorkflowMetrics;
    phaseMetrics: Record<string, PhaseMetrics>;
    agentMetrics: Record<string, AgentMetrics>;
    bottlenecks: Bottleneck[];
    trends: Trend[];
}

/**
 * Time range for analytics
 */
export interface TimeRange {
    start: string;
    end: string;
    granularity: 'hour' | 'day' | 'week' | 'month';
}

/**
 * Workflow metrics
 */
export interface WorkflowMetrics {
    totalItems: number;
    completedItems: number;
    averageCycleTime: number; // minutes
    averageLeadTime: number; // minutes
    throughput: number; // items per day
    successRate: number; // 0-1
    automationRate: number; // 0-1
    costPerItem: number;
}

/**
 * Phase metrics
 */
export interface PhaseMetrics {
    phaseId: string;
    averageDuration: number; // minutes
    successRate: number;
    bottleneckScore: number; // 0-10
    automationRate: number;
    topAgents: string[];
}

/**
 * Workflow-specific agent metrics for analytics.
 * 
 * Distinct from base AgentMetrics (which tracks per-execution metrics).
 * This tracks aggregate analytics across multiple executions.
 * 
 * @doc.type interface
 * @doc.purpose Analytics-focused agent performance metrics
 * @doc.layer product
 * @doc.pattern Value Object
 * @see AgentMetrics for per-execution metrics from base types
 */
export interface WorkflowAgentMetrics {
    agentId: string;
    tasksCompleted: number;
    successRate: number;
    averageConfidence: number;
    averageLatency: number;
    costEfficiency: number; // tasks per dollar
    qualityScore: number; // 0-100
}

/**
 * Bottleneck identification
 */
export interface Bottleneck {
    id: string;
    type: 'phase' | 'agent' | 'dependency' | 'resource';
    location: string;
    severity: 'critical' | 'major' | 'minor';
    description: string;
    impact: string;
    suggestions: string[];
    detectedAt: string;
}

/**
 * Trend analysis
 */
export interface Trend {
    metric: string;
    direction: 'increasing' | 'decreasing' | 'stable';
    rate: number;
    confidence: number;
    prediction?: number;
    timeframe: string;
}

// ============================================================================
// AUTOMATION RULES ENGINE
// ============================================================================

/**
 * Automation rule engine configuration
 */
export interface AutomationEngine {
    id: string;
    name: string;
    enabled: boolean;
    workflows: string[];
    rules: WorkflowRule[];
    executionMode: 'realtime' | 'batch' | 'scheduled';
    batchSize?: number;
    scheduleCron?: string;
    failurePolicy: 'continue' | 'stop' | 'rollback';
    notifications: NotificationConfig;
}

/**
 * Notification configuration
 */
export interface NotificationConfig {
    onSuccess: boolean;
    onFailure: boolean;
    onBottleneck: boolean;
    channels: NotificationChannel[];
    recipients: string[];
    template?: string;
}

/**
 * Notification channel
 */
export type NotificationChannel = 'email' | 'slack' | 'teams' | 'webhook' | 'in-app';

// ============================================================================
// BACKWARD COMPATIBILITY ALIASES (DEPRECATED)
// ============================================================================

/**
 * @deprecated Use WorkflowAgentRole instead
 */
export type AgentType = WorkflowAgentRole;

/**
 * @deprecated Use WorkflowAgent instead
 */
export type Agent = WorkflowAgent;

/**
 * @deprecated Use WorkflowAgentMetrics instead
 */
export type AgentMetrics = WorkflowAgentMetrics;
