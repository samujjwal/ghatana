/**
 * Configuration API Client
 *
 * <p><b>Purpose</b><br>
 * API client functions for the configuration backend service.
 * Handles fetching organization configuration entities including
 * departments, personas, phases, stages, services, integrations, flows, and operators.
 *
 * <p><b>Endpoints</b><br>
 * - GET /config - Get full org configuration
 * - GET /config/departments - List departments
 * - GET /config/personas - List personas
 * - GET /config/phases - List phases
 * - GET /config/stages - List stage mappings
 * - GET /config/services - List services
 * - GET /config/integrations - List integrations
 * - GET /config/flows - List flows
 * - GET /config/operators - List operators
 * - GET /config/graph - Get org graph data
 * (Note: baseURL already includes /api/v1)
 *
 * @doc.type service
 * @doc.purpose Configuration API client
 * @doc.layer product
 * @doc.pattern API Client
 */

import { apiClient } from './index';

// ============================================================================
// Type Definitions
// ============================================================================

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
    role: string | { name: string; level: string; title: string };
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
    description?: string;
    category?: string;
    unit?: string;
    trend?: 'up' | 'down';
    thresholds?: {
        warning: number;
        critical: number;
    };
}

export interface ServiceConfig {
    id: string;
    name: string;
    description?: string;
    department_id?: string;
    tier?: string;
    risk_level?: string;
    type?: string;
    url?: string;
    status?: string;
    health_endpoint?: string;
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
    type?: string;
    description?: string;
    enabled?: boolean;
    department_ids?: string[];
    service_ids?: string[] | 'all';
    managed_by_personas?: string[];
    auth_type?: string;
    config?: Record<string, unknown>;
    external_url?: string;
    icon?: string;
    status?: string;
    capabilities?: string[];
}

export interface FlowConfig {
    id: string;
    name: string;
    persona_id?: string;
    description?: string;
    phases?: string[];
    steps: FlowStep[];
    quick_actions?: QuickAction[];
    permissions?: string[];
    metrics?: MetricConfig[];
    type?: string;
}

export interface FlowStep {
    step_id?: string;
    id?: string;
    name?: string;
    phase_id?: string;
    label?: string;
    description?: string;
    route?: string;
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
    version?: string;
    domain?: string;
    name?: string;
    category?: string;
    description?: string;
    modes?: OperatorMode[];
    inputs?: OperatorInput[];
    outputs?: OperatorOutput[];
}

export interface OperatorMode {
    name?: string;
    id?: string;
    description?: string;
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
    type?: string;
    status?: string;
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
        type: string;
        timeout?: string;
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

export interface ConfigGraphNode {
    id: string;
    type: 'department' | 'service' | 'integration' | 'persona' | 'workflow' | 'operator';
    label: string;
    data: unknown;
}

export interface ConfigGraphEdge {
    id: string;
    source: string;
    target: string;
    type: 'owns' | 'depends-on' | 'uses' | 'triggers';
}

export interface ConfigGraphData {
    nodes: ConfigGraphNode[];
    edges: ConfigGraphEdge[];
}

// ============================================================================
// API Response Types
// ============================================================================

export interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    timestamp: string;
}

// ============================================================================
// API Functions
// ============================================================================

/**
 * Get the full organization configuration
 */
export async function getOrgConfig(): Promise<OrgConfig> {
    const response = await apiClient.get<ApiResponse<OrgConfig>>('/config');
    return response.data.data;
}

/**
 * Reload configuration from files
 */
export async function reloadConfig(): Promise<OrgConfig> {
    const response = await apiClient.post<ApiResponse<OrgConfig>>('/config/reload');
    return response.data.data;
}

/**
 * Get organization graph data for visualization
 */
export async function getConfigGraph(): Promise<ConfigGraphData> {
    const response = await apiClient.get<ApiResponse<ConfigGraphData>>('/config/graph');
    return response.data.data;
}

// ============================================================================
// Departments
// ============================================================================

/**
 * List all departments
 */
export async function getDepartments(): Promise<DepartmentConfig[]> {
    const response = await apiClient.get<ApiResponse<DepartmentConfig[]>>('/config/departments');
    return response.data.data;
}

/**
 * Get a single department by ID
 */
export async function getDepartment(id: string): Promise<DepartmentConfig> {
    const response = await apiClient.get<ApiResponse<DepartmentConfig>>(`/config/departments/${id}`);
    return response.data.data;
}

// ============================================================================
// Personas
// ============================================================================

/**
 * List all personas
 */
export async function getPersonas(): Promise<PersonaConfig[]> {
    const response = await apiClient.get<ApiResponse<PersonaConfig[]>>('/config/personas');
    return response.data.data;
}

/**
 * Get a single persona by ID
 */
export async function getPersona(id: string): Promise<PersonaConfig> {
    const response = await apiClient.get<ApiResponse<PersonaConfig>>(`/config/personas/${id}`);
    return response.data.data;
}

// ============================================================================
// Phases
// ============================================================================

/**
 * List all phases
 */
export async function getPhases(): Promise<PhaseConfig[]> {
    const response = await apiClient.get<ApiResponse<PhaseConfig[]>>('/config/phases');
    return response.data.data;
}

/**
 * Get a single phase by ID
 */
export async function getPhase(id: string): Promise<PhaseConfig> {
    const response = await apiClient.get<ApiResponse<PhaseConfig>>(`/config/phases/${id}`);
    return response.data.data;
}

// ============================================================================
// Stages
// ============================================================================

/**
 * List all stage mappings
 */
export async function getStages(): Promise<StageMapping[]> {
    const response = await apiClient.get<ApiResponse<StageMapping[]>>('/config/stages');
    return response.data.data;
}

// ============================================================================
// Services
// ============================================================================

/**
 * List all services
 */
export async function getServices(): Promise<ServiceConfig[]> {
    const response = await apiClient.get<ApiResponse<ServiceConfig[]>>('/config/services');
    return response.data.data;
}

/**
 * Get a single service by ID
 */
export async function getService(id: string): Promise<ServiceConfig> {
    const response = await apiClient.get<ApiResponse<ServiceConfig>>(`/config/services/${id}`);
    return response.data.data;
}

// ============================================================================
// Integrations
// ============================================================================

/**
 * List all integrations
 */
export async function getIntegrations(): Promise<IntegrationConfig[]> {
    const response = await apiClient.get<ApiResponse<IntegrationConfig[]>>('/config/integrations');
    return response.data.data;
}

/**
 * Get a single integration by ID
 */
export async function getIntegration(id: string): Promise<IntegrationConfig> {
    const response = await apiClient.get<ApiResponse<IntegrationConfig>>(`/config/integrations/${id}`);
    return response.data.data;
}

// ============================================================================
// Interactions
// ============================================================================

/**
 * List all interactions
 */
export async function getInteractions(): Promise<InteractionConfig[]> {
    const response = await apiClient.get<ApiResponse<InteractionConfig[]>>('/config/interactions');
    return response.data.data;
}

/**
 * Get a single interaction by ID
 */
export async function getInteraction(id: string): Promise<InteractionConfig> {
    const response = await apiClient.get<ApiResponse<InteractionConfig>>(`/config/interactions/${id}`);
    return response.data.data;
}

// ============================================================================
// Flows
// ============================================================================

/**
 * List all DevSecOps flows
 */
export async function getFlows(): Promise<FlowConfig[]> {
    const response = await apiClient.get<ApiResponse<FlowConfig[]>>('/config/flows');
    return response.data.data;
}

/**
 * Get a single flow by ID
 */
export async function getFlow(id: string): Promise<FlowConfig> {
    const response = await apiClient.get<ApiResponse<FlowConfig>>(`/config/flows/${id}`);
    return response.data.data;
}

// ============================================================================
// Operators
// ============================================================================

/**
 * List all operators
 */
export async function getOperators(): Promise<OperatorConfig[]> {
    const response = await apiClient.get<ApiResponse<OperatorConfig[]>>('/config/operators');
    return response.data.data;
}

/**
 * Get a single operator by ID
 */
export async function getOperator(id: string): Promise<OperatorConfig> {
    const response = await apiClient.get<ApiResponse<OperatorConfig>>(`/config/operators/${id}`);
    return response.data.data;
}

// ============================================================================
// Aggregated Endpoints
// ============================================================================

/**
 * List all agents across all departments
 */
export async function getAgents(): Promise<AgentConfig[]> {
    const response = await apiClient.get<ApiResponse<AgentConfig[]>>('/config/agents');
    return response.data.data;
}

/**
 * List all workflows across all departments
 */
export async function getWorkflows(): Promise<WorkflowConfig[]> {
    const response = await apiClient.get<ApiResponse<WorkflowConfig[]>>('/config/workflows');
    return response.data.data;
}

/**
 * List all KPIs across all departments
 */
export async function getKpis(): Promise<KpiConfig[]> {
    const response = await apiClient.get<ApiResponse<KpiConfig[]>>('/config/kpis');
    return response.data.data;
}

// ============================================================================
// Export all
// ============================================================================

export const configApi = {
    // Full config
    getOrgConfig,
    reloadConfig,
    getConfigGraph,
    // Departments
    getDepartments,
    getDepartment,
    // Personas
    getPersonas,
    getPersona,
    // Phases
    getPhases,
    getPhase,
    // Stages
    getStages,
    // Services
    getServices,
    getService,
    // Integrations
    getIntegrations,
    getIntegration,
    // Flows
    getFlows,
    getFlow,
    // Operators
    getOperators,
    getOperator,
    // Aggregated
    getAgents,
    getWorkflows,
    getKpis,
};

export default configApi;
