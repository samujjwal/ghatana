export interface PersonaConfig {
    id: string;
    display_name: string;
    description?: string;
    tags: string[];
    department?: string;
    permissions?: string[];
    default_phases?: string[];
    quick_actions?: QuickAction[];
}

export interface QuickAction {
    id: string;
    label: string;
    icon: string;
    href: string;
}

export interface PhaseConfig {
    id: string;
    display_name: string;
    description?: string;
    personas: string[];
    entry_criteria?: string[];
    exit_criteria?: string[];
}

export interface StageMapping {
    stage: string;
    phases: string[];
}

export interface DepartmentConfig {
    id: string;
    name: string;
    type: string;
    description: string;
    agents?: AgentConfig[];
    workflows?: WorkflowConfig[];
    kpis?: KpiConfig[];
    tools?: string[];
}

export interface AgentConfig {
    id: string;
    name: string;
    role: string;
    department: string;
    capabilities: string[];
    personality?: {
        temperature: number;
        creativity: number;
        assertiveness: number;
    };
    model?: {
        id: string;
        max_tokens: number;
    };
    system_prompt?: string;
}

export interface WorkflowConfig {
    id: string;
    name: string;
    trigger?: {
        event: string;
    };
    steps: WorkflowStep[];
}

export interface WorkflowStep {
    id: string;
    agent?: string;
    action?: string;
    description?: string;
    condition?: string;
    type?: string;
    timeout?: string;
}

export interface KpiConfig {
    id: string;
    name: string;
    target: string;
    measurement: string;
}

export interface ServiceConfig {
    id: string;
    name: string;
    description: string;
    department_id: string;
    tier: string;
    risk_level: string;
    slo?: {
        availability: number;
        latency_p95_ms: number;
        error_rate_threshold: number;
    };
    dependencies?: string[];
    dependents?: string[];
    environments?: EnvironmentConfig[];
    integration_ids?: string[];
    tags?: string[];
    repository?: {
        url: string;
        branch: string;
    };
}

export interface EnvironmentConfig {
    name: string;
    url?: string;
    replicas?: number;
}

export interface IntegrationConfig {
    id: string;
    name: string;
    type: string;
    description: string;
    enabled: boolean;
    department_ids?: string[];
    service_ids?: string[] | 'all';
    managed_by_personas?: string[];
    config?: Record<string, unknown>;
    external_url?: string;
    icon?: string;
    status?: string;
    capabilities?: string[];
}

export interface FlowConfig {
    id: string;
    name: string;
    persona_id: string;
    description: string;
    phases: string[];
    steps: FlowStep[];
    quick_actions?: QuickAction[];
    permissions?: string[];
    metrics?: MetricConfig[];
}

export interface FlowStep {
    step_id: string;
    phase_id: string;
    label: string;
    description?: string;
    route: string;
    permissions?: string[];
    requires_approval?: boolean;
    approvers?: string[];
    tools?: string[];
}

export interface MetricConfig {
    name: string;
    description: string;
    target: string;
}

export interface OperatorConfig {
    id: string;
    version: string;
    domain: string;
    category?: string;
    description: string;
    modes: OperatorMode[];
}

export interface OperatorMode {
    name: string;
    description: string;
    role?: string;
    inputs?: OperatorInput[];
    outputs?: OperatorOutput[];
}

export interface OperatorInput {
    name: string;
    description: string;
    required?: boolean;
}

export interface OperatorOutput {
    name: string;
    description: string;
    format?: string;
}

export interface InteractionConfig {
    id: string;
    name: string;
    displayName?: string;
    description?: string;
    type?: string; // handoff, collaboration, delegation, escalation, consultation, notification
    status?: string; // active, draft, disabled
    sourceDepartment?: string;
    targetDepartment?: string;
    sourceTeam?: string;
    targetTeam?: string;
    sourcePersona?: string;
    targetPersona?: string;
    trigger?: {
        events: Array<{
            type: string;
            conditions?: Record<string, unknown>;
        }>;
    };
    protocol?: {
        type: string; // async, sync, hybrid
        timeout?: string; // ISO 8601 duration (e.g., PT4H)
        retry?: {
            maxAttempts: number;
            backoffMs: number;
        };
    };
    handoff?: {
        requirements: Array<{
            name: string;
            mandatory: boolean;
            description?: string;
        }>;
    };
    actions?: Array<{
        name: string;
        type: string;
        parameters?: Record<string, unknown>;
    }>;
    metadata?: Record<string, unknown>;
}

export interface OrgConfig {
    id: string;
    name: string;
    description: string;
    version: string;
    departments: DepartmentConfig[];
    personas: PersonaConfig[];
    phases: PhaseConfig[];
    stages: StageMapping[];
    services: ServiceConfig[];
    integrations: IntegrationConfig[];
    interactions: InteractionConfig[];
    flows: FlowConfig[];
    operators: OperatorConfig[];
    metadata: {
        loaded_at: string;
        config_path: string;
    };
}
