/**
 * Organization Configuration Types
 *
 * <p><b>Purpose</b><br>
 * Shared TypeScript interfaces for the Virtual Software Organization configuration.
 * Used by Org Builder, DevSecOps board, persona workspaces, and routing helpers.
 *
 * <p><b>Key Concepts</b><br>
 * - OrgConfig: Root configuration for the entire virtual organization
 * - DepartmentConfig: Team/department definitions with ownership
 * - ServiceConfig: Microservices with SLOs, risk levels, and dependencies
 * - WorkflowConfig: Automation workflows with triggers and steps
 * - PersonaBinding: Maps personas to departments, services, and permissions
 * - IntegrationConfig: External integrations and agent configurations
 * - DevSecOpsFlowConfig: Consolidated flow definition for all personas
 *
 * @doc.type types
 * @doc.purpose Shared organization configuration types
 * @doc.layer shared
 * @doc.pattern Domain Types
 */

/**
 * Risk level classification for services
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 * Environment tier for deployments
 */
export type EnvironmentTier = 'development' | 'staging' | 'production';

/**
 * Service tier classification
 */
export type ServiceTier = 'tier-0' | 'tier-1' | 'tier-2' | 'tier-3';

/**
 * Persona identifiers used across the application
 */
export type PersonaId = 'engineer' | 'lead' | 'sre' | 'security' | 'admin' | 'viewer';

/**
 * DevSecOps phase identifiers
 */
export type DevSecOpsPhaseId =
    | 'intake'
    | 'plan'
    | 'build'
    | 'verify'
    | 'review'
    | 'staging'
    | 'deploy'
    | 'operate'
    | 'learn';

/**
 * Integration/agent type classification
 */
export type IntegrationType =
    | 'ci-cd'
    | 'monitoring'
    | 'security-scanner'
    | 'notification'
    | 'ticketing'
    | 'source-control'
    | 'artifact-registry'
    | 'secrets-manager'
    | 'ai-agent';

/**
 * Node type for the Org Graph visualization
 */
export type OrgGraphNodeType =
    | 'department'
    | 'service'
    | 'workflow'
    | 'integration'
    | 'persona';

/**
 * Edge type for the Org Graph visualization
 */
export type OrgGraphEdgeType =
    | 'owns'
    | 'depends-on'
    | 'triggers'
    | 'monitors'
    | 'deploys-to';

/**
 * Service Level Objective configuration
 */
export interface SLOConfig {
    /** Availability target (e.g., 99.9) */
    availability: number;
    /** Latency P95 target in milliseconds */
    latencyP95Ms: number;
    /** Error rate threshold as percentage */
    errorRateThreshold: number;
}

/**
 * Department configuration
 */
export interface DepartmentConfig {
    /** Unique identifier */
    id: string;
    /** Display name */
    name: string;
    /** Description of the department's purpose */
    description: string;
    /** Team lead or owner */
    owner: string;
    /** List of team member IDs */
    members: string[];
    /** Parent department ID (for nested orgs) */
    parentId?: string;
    /** Associated service IDs */
    serviceIds: string[];
    /** Associated workflow IDs */
    workflowIds: string[];
    /** Color for visualization */
    color?: string;
    /** Icon identifier */
    icon?: string;
}

/**
 * Service configuration
 */
export interface ServiceConfig {
    /** Unique identifier */
    id: string;
    /** Display name */
    name: string;
    /** Description of the service */
    description: string;
    /** Owning department ID */
    departmentId: string;
    /** Service tier classification */
    tier: ServiceTier;
    /** Risk level */
    riskLevel: RiskLevel;
    /** SLO configuration */
    slo: SLOConfig;
    /** IDs of services this depends on */
    dependencies: string[];
    /** IDs of services that depend on this */
    dependents: string[];
    /** Repository URL */
    repoUrl?: string;
    /** Documentation URL */
    docsUrl?: string;
    /** Environments where deployed */
    environments: EnvironmentTier[];
    /** Associated integration IDs */
    integrationIds: string[];
    /** Tags for filtering */
    tags: string[];
}

/**
 * Workflow step configuration
 */
export interface WorkflowStepConfig {
    /** Step identifier */
    id: string;
    /** Step name */
    name: string;
    /** Step type */
    type: 'manual' | 'automated' | 'approval' | 'notification';
    /** Integration ID if automated */
    integrationId?: string;
    /** Approver persona IDs if approval step */
    approvers?: PersonaId[];
    /** Timeout in seconds */
    timeoutSeconds?: number;
}

/**
 * Workflow configuration
 */
export interface WorkflowConfig {
    /** Unique identifier */
    id: string;
    /** Display name */
    name: string;
    /** Description */
    description: string;
    /** Trigger type */
    trigger: 'manual' | 'event' | 'schedule' | 'webhook';
    /** Trigger configuration (cron, event type, etc.) */
    triggerConfig?: Record<string, unknown>;
    /** Workflow steps */
    steps: WorkflowStepConfig[];
    /** Associated department IDs */
    departmentIds: string[];
    /** Associated service IDs */
    serviceIds: string[];
    /** Whether workflow is enabled */
    enabled: boolean;
}

/**
 * Persona binding - maps personas to org entities and permissions
 */
export interface PersonaBinding {
    /** Persona identifier */
    personaId: PersonaId;
    /** Display name for this persona */
    displayName: string;
    /** Description of the persona's role */
    description: string;
    /** Department IDs this persona has access to */
    departmentIds: string[];
    /** Service IDs this persona can manage */
    serviceIds: string[];
    /** Workflow IDs this persona can execute */
    workflowIds: string[];
    /** Permission keys granted to this persona */
    permissions: string[];
    /** Default DevSecOps phases visible to this persona */
    defaultPhases: DevSecOpsPhaseId[];
    /** Quick action configurations for this persona */
    quickActions: PersonaQuickAction[];
}

/**
 * Quick action configuration for a persona
 */
export interface PersonaQuickAction {
    /** Action identifier */
    id: string;
    /** Display label */
    label: string;
    /** Icon identifier */
    icon: string;
    /** Navigation href */
    href: string;
    /** Required permissions */
    permissions?: string[];
    /** Badge count data key */
    badgeKey?: string;
}

/**
 * Integration/agent configuration
 */
export interface IntegrationConfig {
    /** Unique identifier */
    id: string;
    /** Display name */
    name: string;
    /** Integration type */
    type: IntegrationType;
    /** Description */
    description: string;
    /** Whether integration is enabled */
    enabled: boolean;
    /** Department IDs that use this integration */
    departmentIds: string[];
    /** Service IDs that use this integration */
    serviceIds: string[];
    /** Persona IDs that can manage this integration */
    managedByPersonas: PersonaId[];
    /** Configuration endpoint or settings page path */
    configPath?: string;
    /** External URL for the integration */
    externalUrl?: string;
    /** Icon identifier */
    icon?: string;
    /** Health status */
    status: 'healthy' | 'degraded' | 'down' | 'unknown';
}

/**
 * DevSecOps flow step configuration
 */
export interface DevSecOpsFlowStep {
    /** Step identifier */
    stepId: string;
    /** Phase this step belongs to */
    phaseId: DevSecOpsPhaseId;
    /** Display label */
    label: string;
    /** Route path for this step */
    route: string;
    /** Route parameters */
    routeParams?: Record<string, string>;
    /** Required permissions */
    permissions?: string[];
}

/**
 * DevSecOps flow configuration - consolidated type for all personas
 */
export interface DevSecOpsFlowConfig {
    /** Flow identifier (e.g., 'engineer', 'sre', 'security') */
    id: string;
    /** Display name */
    name: string;
    /** Associated persona ID */
    personaId: PersonaId;
    /** Ordered list of phases in this flow */
    phases: DevSecOpsPhaseId[];
    /** Flow steps with routing */
    steps: DevSecOpsFlowStep[];
    /** Description of the flow */
    description: string;
}

/**
 * Root organization configuration
 */
export interface OrgConfig {
    /** Organization identifier */
    id: string;
    /** Organization name */
    name: string;
    /** Organization description */
    description: string;
    /** All departments */
    departments: DepartmentConfig[];
    /** All services */
    services: ServiceConfig[];
    /** All workflows */
    workflows: WorkflowConfig[];
    /** Persona bindings */
    personaBindings: PersonaBinding[];
    /** Integrations and agents */
    integrations: IntegrationConfig[];
    /** DevSecOps flow configurations */
    devSecOpsFlows: DevSecOpsFlowConfig[];
    /** Organization metadata */
    metadata: {
        version: string;
        lastUpdated: string;
        createdBy: string;
    };
}

/**
 * Org Graph node for visualization
 */
export interface OrgGraphNode {
    /** Node identifier */
    id: string;
    /** Node type */
    type: OrgGraphNodeType;
    /** Display label */
    label: string;
    /** Node data (department, service, etc.) */
    data: DepartmentConfig | ServiceConfig | WorkflowConfig | IntegrationConfig | PersonaBinding;
    /** Position for layout */
    position?: { x: number; y: number };
    /** Visual styling */
    style?: {
        color?: string;
        icon?: string;
        size?: 'sm' | 'md' | 'lg';
    };
}

/**
 * Org Graph edge for visualization
 */
export interface OrgGraphEdge {
    /** Edge identifier */
    id: string;
    /** Source node ID */
    source: string;
    /** Target node ID */
    target: string;
    /** Edge type */
    type: OrgGraphEdgeType;
    /** Display label */
    label?: string;
    /** Visual styling */
    style?: {
        color?: string;
        dashed?: boolean;
        animated?: boolean;
    };
}

/**
 * Org Graph data structure
 */
export interface OrgGraphData {
    /** All nodes */
    nodes: OrgGraphNode[];
    /** All edges */
    edges: OrgGraphEdge[];
}

// ============================================================================
// UNIFIED PERSONA MODEL (Human/Agent Agnostic)
// ============================================================================

/**
 * Persona execution mode - human, automated agent, or hybrid
 */
export type PersonaExecutionMode = 'human' | 'agent' | 'hybrid';

/**
 * Persona availability status - same semantics for humans and agents
 * - available: Ready to accept work
 * - busy: Currently working on tasks
 * - away: Temporarily unavailable (break for humans, throttled for agents)
 * - offline: Not available (off-hours for humans, stopped for agents)
 * - maintenance: Planned unavailability (PTO for humans, upgrade for agents)
 */
export type PersonaAvailabilityStatus = 'available' | 'busy' | 'away' | 'offline' | 'maintenance';

/**
 * Capacity type - how capacity is measured
 * - hours: Work hours (humans)
 * - compute: Compute units/tokens (agents)
 * - tasks: Concurrent task limit (both)
 */
export type CapacityType = 'hours' | 'compute' | 'tasks';

/**
 * Persona capacity - unified for humans and agents
 * Humans: work hours, PTO, meetings
 * Agents: compute budget, rate limits, concurrent tasks
 */
export interface PersonaCapacity {
    /** Capacity measurement type */
    type: CapacityType;
    /** Available capacity (0-100%) */
    available: number;
    /** Currently allocated capacity (0-100%) */
    allocated: number;
    /** Daily limit (hours, tokens, or tasks) */
    dailyLimit: number;
    /** Weekly limit */
    weeklyLimit: number;
    /** Current utilization (0-100%) */
    currentUtilization: number;
    /** Reserved capacity for urgent work */
    reservedForUrgent: number;
}

/**
 * Availability schedule entry
 */
export interface AvailabilityScheduleEntry {
    /** Day of week (0=Sunday, 6=Saturday) */
    dayOfWeek: number;
    /** Start time (HH:mm format) */
    startTime: string;
    /** End time (HH:mm format) */
    endTime: string;
    /** Timezone */
    timezone: string;
}

/**
 * Planned absence - PTO for humans, maintenance for agents
 */
export interface PlannedAbsence {
    /** Unique identifier */
    id: string;
    /** Start date */
    startDate: string;
    /** End date */
    endDate: string;
    /** Reason/description */
    reason: string;
    /** Type of absence */
    type: 'pto' | 'sick' | 'holiday' | 'maintenance' | 'upgrade' | 'training';
    /** Approval status */
    status: 'pending' | 'approved' | 'rejected';
    /** Approver ID (if applicable) */
    approverId?: string;
}

/**
 * Persona availability - unified schedule for humans and agents
 */
export interface PersonaAvailability {
    /** Current status */
    status: PersonaAvailabilityStatus;
    /** Status message (e.g., "In a meeting", "Processing batch job") */
    statusMessage?: string;
    /** Regular schedule */
    schedule: AvailabilityScheduleEntry[];
    /** Planned absences (PTO for humans, maintenance for agents) */
    plannedAbsences: PlannedAbsence[];
    /** Next available time (null if currently available) */
    nextAvailable: string | null;
    /** Last active timestamp */
    lastActive: string;
}

/**
 * Capability/skill definition
 */
export interface Capability {
    /** Capability identifier */
    id: string;
    /** Display name */
    name: string;
    /** Category (e.g., 'technical', 'domain', 'soft-skill') */
    category: string;
    /** Proficiency level (0-100) */
    proficiency: number;
    /** Last assessed date */
    lastAssessed?: string;
    /** Certifications or validations */
    certifications?: string[];
}

/**
 * Growth goal - career development for humans, capability upgrade for agents
 */
export interface GrowthGoal {
    /** Goal identifier */
    id: string;
    /** Goal title */
    title: string;
    /** Description */
    description: string;
    /** Target date */
    targetDate: string;
    /** Progress (0-100%) */
    progress: number;
    /** Status */
    status: 'not-started' | 'in-progress' | 'completed' | 'cancelled';
    /** Related capabilities */
    relatedCapabilities: string[];
    /** Milestones */
    milestones: GrowthMilestone[];
}

/**
 * Growth milestone
 */
export interface GrowthMilestone {
    /** Milestone identifier */
    id: string;
    /** Title */
    title: string;
    /** Completed flag */
    completed: boolean;
    /** Completion date */
    completedAt?: string;
}

/**
 * Persona growth tracking - unified for humans and agents
 * Humans: skills, certifications, career level
 * Agents: model versions, capability upgrades, fine-tuning
 */
export interface PersonaGrowth {
    /** Current level/version (e.g., "Senior Engineer", "v2.1.0") */
    level: string;
    /** Progress to next level (0-100%) */
    progressToNext: number;
    /** Active goals/upgrades */
    activeGoals: GrowthGoal[];
    /** Completed milestones */
    completedMilestones: GrowthMilestone[];
    /** Skills/capabilities */
    capabilities: Capability[];
    /** Total experience points or equivalent metric */
    experiencePoints?: number;
}

/**
 * Tool configuration for a persona
 */
export interface PersonaToolConfig {
    /** Tool identifier */
    toolId: string;
    /** Tool name */
    name: string;
    /** Whether enabled for this persona */
    enabled: boolean;
    /** Tool-specific configuration */
    config?: Record<string, unknown>;
    /** Required permissions to use */
    requiredPermissions?: string[];
}

/**
 * Unified Persona interface - agnostic to human/agent
 * 
 * This is the core abstraction that treats all personas uniformly,
 * whether they are human workers or automated agents.
 */
export interface Persona {
    /** Unique identifier */
    id: string;
    /** Display name */
    name: string;
    /** Avatar URL or icon */
    avatar?: string;
    /** Persona role */
    role: PersonaId;
    /** Execution mode - human, agent, or hybrid */
    executionMode: PersonaExecutionMode;
    /** Description */
    description?: string;
    /** Department ID */
    departmentId?: string;
    /** Capacity configuration */
    capacity: PersonaCapacity;
    /** Availability schedule and status */
    availability: PersonaAvailability;
    /** Growth and development tracking */
    growth: PersonaGrowth;
    /** DevSecOps flow configuration */
    flowConfig?: DevSecOpsFlowConfig;
    /** Available tools */
    tools: PersonaToolConfig[];
    /** Permissions */
    permissions: string[];
    /** Metadata */
    metadata?: {
        createdAt: string;
        updatedAt: string;
        version?: string;
        tags?: string[];
    };
}

// ============================================================================
// WORK ITEM EXECUTION CONTEXT
// ============================================================================

/**
 * Canvas artifact for visual work
 */
export interface CanvasArtifact {
    /** Artifact identifier */
    id: string;
    /** Artifact type */
    type: 'diagram' | 'flowchart' | 'architecture' | 'wireframe' | 'sketch';
    /** Title */
    title: string;
    /** Canvas data (JSON) */
    data: string;
    /** Created timestamp */
    createdAt: string;
    /** Updated timestamp */
    updatedAt: string;
}

/**
 * File context for editor
 */
export interface FileContext {
    /** File path */
    path: string;
    /** File content (may be partial) */
    content?: string;
    /** Language identifier */
    language: string;
    /** Whether file is read-only */
    readOnly?: boolean;
    /** Line range to focus on */
    focusRange?: { start: number; end: number };
}

/**
 * VCS integration context
 */
export interface VCSContext {
    /** Repository URL */
    repoUrl: string;
    /** Current branch */
    branch: string;
    /** Linked pull requests */
    pullRequests: {
        id: string;
        title: string;
        url: string;
        status: 'open' | 'merged' | 'closed';
        reviewStatus: 'pending' | 'approved' | 'changes-requested';
    }[];
    /** Recent commits */
    recentCommits: {
        sha: string;
        message: string;
        author: string;
        timestamp: string;
    }[];
}

/**
 * CI/CD integration context
 */
export interface CIContext {
    /** Pipeline ID */
    pipelineId: string;
    /** Pipeline name */
    name: string;
    /** Current status */
    status: 'pending' | 'running' | 'passed' | 'failed' | 'cancelled';
    /** Pipeline URL */
    url?: string;
    /** Jobs/stages */
    stages: {
        id: string;
        name: string;
        status: 'pending' | 'running' | 'passed' | 'failed' | 'skipped';
        duration?: number;
    }[];
    /** Artifacts */
    artifacts?: { name: string; url: string }[];
}

/**
 * Observability integration context
 */
export interface ObservabilityContext {
    /** Dashboard URL */
    dashboardUrl?: string;
    /** Key metrics */
    metrics: {
        name: string;
        value: number;
        unit: string;
        trend: 'up' | 'down' | 'stable';
    }[];
    /** Active alerts */
    alerts: {
        id: string;
        severity: 'info' | 'warning' | 'critical';
        message: string;
        timestamp: string;
    }[];
    /** Log query URL */
    logsUrl?: string;
}

/**
 * Security integration context
 */
export interface SecurityContext {
    /** Vulnerability count by severity */
    vulnerabilities: {
        critical: number;
        high: number;
        medium: number;
        low: number;
    };
    /** Compliance status */
    complianceStatus: 'compliant' | 'non-compliant' | 'unknown';
    /** Security scan results URL */
    scanResultsUrl?: string;
    /** Last scan timestamp */
    lastScanAt?: string;
}

/**
 * AI assistant context
 */
export interface AIAssistantContext {
    /** Whether AI assistant is enabled */
    enabled: boolean;
    /** Context for AI (work item description, etc.) */
    context: string;
    /** Suggested actions from AI */
    suggestedActions: {
        id: string;
        action: string;
        description: string;
        confidence: number;
    }[];
    /** Chat history */
    chatHistory?: {
        role: 'user' | 'assistant';
        content: string;
        timestamp: string;
    }[];
}

/**
 * Work item action that can be performed
 */
export interface WorkItemAction {
    /** Action identifier */
    id: string;
    /** Action label */
    label: string;
    /** Action type */
    type: 'status-change' | 'assignment' | 'comment' | 'link' | 'custom';
    /** Icon identifier */
    icon?: string;
    /** Whether action is primary (highlighted) */
    primary?: boolean;
    /** Whether action is enabled */
    enabled: boolean;
    /** Disabled reason if not enabled */
    disabledReason?: string;
    /** Target status (for status-change actions) */
    targetStatus?: string;
    /** Required permissions */
    requiredPermissions?: string[];
}

/**
 * Execution context - provides all tools needed to complete work
 * This is the core of the embedded task execution model
 */
export interface ExecutionContext {
    /** Work item ID this context is for */
    workItemId: string;
    /** Current DevSecOps phase */
    currentPhase: DevSecOpsPhaseId;
    /** Current step in the flow */
    currentStepId: string;
    /** Canvas context for visual work */
    canvas?: {
        enabled: boolean;
        template?: string;
        artifacts: CanvasArtifact[];
    };
    /** Terminal context for command execution */
    terminal?: {
        enabled: boolean;
        allowedCommands?: string[];
        workingDirectory?: string;
        environment?: Record<string, string>;
    };
    /** Editor context for code/document editing */
    editor?: {
        enabled: boolean;
        files: FileContext[];
    };
    /** VCS integration */
    vcs?: VCSContext;
    /** CI/CD integration */
    ci?: CIContext;
    /** Observability integration */
    observability?: ObservabilityContext;
    /** Security integration */
    security?: SecurityContext;
    /** AI assistant */
    aiAssistant?: AIAssistantContext;
    /** Available actions at this step */
    availableActions: WorkItemAction[];
    /** Next step in the flow (if any) */
    nextStepId?: string;
}
