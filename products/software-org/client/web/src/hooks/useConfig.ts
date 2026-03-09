/**
 * Configuration React Query Hooks
 *
 * <p><b>Purpose</b><br>
 * React Query hooks for fetching organization configuration data.
 * Provides caching, background refetching, and error handling for
 * departments, personas, phases, stages, services, integrations, flows, and operators.
 *
 * <p><b>Features</b><br>
 * - Automatic fallback to mock data when backend is unavailable
 * - Stale-while-revalidate caching strategy
 * - Error handling with toast notifications
 * - Optimistic updates for mutations
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: personas, isLoading } = usePersonas();
 * const { data: departments } = useDepartments();
 * const { data: orgConfig } = useOrgConfig();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Configuration data hooks
 * @doc.layer product
 * @doc.pattern React Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useToast } from '@/lib/toast';
import {
    configApi,
    type OrgConfig,
    type DepartmentConfig,
    type PersonaConfig,
    type PhaseConfig,
    type StageMapping,
    type ServiceConfig,
    type IntegrationConfig,
    type InteractionConfig,
    type FlowConfig,
    type OperatorConfig,
    type AgentConfig,
    type WorkflowConfig,
    type KpiConfig,
    type ConfigGraphData,
} from '@/services/api/configApi';

// ============================================================================
// Query Keys
// ============================================================================

export const configQueryKeys = {
    all: ['config'] as const,
    orgConfig: () => [...configQueryKeys.all, 'orgConfig'] as const,
    graph: () => [...configQueryKeys.all, 'graph'] as const,
    departments: () => [...configQueryKeys.all, 'departments'] as const,
    department: (id: string) => [...configQueryKeys.all, 'department', id] as const,
    personas: () => [...configQueryKeys.all, 'personas'] as const,
    persona: (id: string) => [...configQueryKeys.all, 'persona', id] as const,
    phases: () => [...configQueryKeys.all, 'phases'] as const,
    phase: (id: string) => [...configQueryKeys.all, 'phase', id] as const,
    stages: () => [...configQueryKeys.all, 'stages'] as const,
    services: () => [...configQueryKeys.all, 'services'] as const,
    service: (id: string) => [...configQueryKeys.all, 'service', id] as const,
    integrations: () => [...configQueryKeys.all, 'integrations'] as const,
    integration: (id: string) => [...configQueryKeys.all, 'integration', id] as const,
    interactions: () => [...configQueryKeys.all, 'interactions'] as const,
    interaction: (id: string) => [...configQueryKeys.all, 'interaction', id] as const,
    flows: () => [...configQueryKeys.all, 'flows'] as const,
    flow: (id: string) => [...configQueryKeys.all, 'flow', id] as const,
    operators: () => [...configQueryKeys.all, 'operators'] as const,
    operator: (id: string) => [...configQueryKeys.all, 'operator', id] as const,
    agents: () => [...configQueryKeys.all, 'agents'] as const,
    workflows: () => [...configQueryKeys.all, 'workflows'] as const,
    kpis: () => [...configQueryKeys.all, 'kpis'] as const,
};

// ============================================================================
// Configuration to control mock fallback
// ============================================================================

const USE_MOCK_FALLBACK = false; // Set to false when backend is ready

// Mock data generators for fallback
function getMockPersonas(): PersonaConfig[] {
    return [
        { id: 'product_manager', display_name: 'Product Manager', tags: ['product'], permissions: ['read:all'] },
        { id: 'backend_engineer', display_name: 'Backend Engineer', tags: ['engineering'], permissions: ['write:code'] },
        { id: 'sre', display_name: 'Site Reliability Engineer', tags: ['operations'], permissions: ['deploy:production'] },
    ];
}

function getMockDepartments(): DepartmentConfig[] {
    return [
        { id: 'engineering', name: 'Engineering', type: 'ENGINEERING', description: 'Software development' },
        { id: 'devops', name: 'DevOps', type: 'DEVOPS', description: 'Infrastructure and operations' },
        { id: 'product', name: 'Product', type: 'PRODUCT', description: 'Product management' },
    ];
}

function getMockPhases(): PhaseConfig[] {
    return [
        { id: 'PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', display_name: 'Problem Discovery', personas: ['product_manager'], description: 'Identify and validate customer problems' },
        { id: 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN', display_name: 'Solution Design', personas: ['product_manager', 'backend_engineer'], description: 'Design solutions to validated problems' },
        { id: 'PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE', display_name: 'Build & Integrate', personas: ['backend_engineer'], description: 'Implement and integrate solutions' },
        { id: 'PRODUCT_LIFECYCLE_PHASE_VALIDATE', display_name: 'Validate', personas: ['backend_engineer', 'sre'], description: 'Test and validate implementations' },
        { id: 'PRODUCT_LIFECYCLE_PHASE_RELEASE', display_name: 'Release', personas: ['sre'], description: 'Deploy to production environments' },
        { id: 'PRODUCT_LIFECYCLE_PHASE_OPERATE', display_name: 'Operate', personas: ['sre'], description: 'Monitor and maintain production systems' },
    ];
}

function getMockStages(): StageMapping[] {
    return [
        { stage: 'stage-plan', phases: ['PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN'] },
        { stage: 'stage-develop', phases: ['PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE'] },
        { stage: 'stage-test', phases: ['PRODUCT_LIFECYCLE_PHASE_VALIDATE'] },
        { stage: 'stage-deploy', phases: ['PRODUCT_LIFECYCLE_PHASE_RELEASE', 'PRODUCT_LIFECYCLE_PHASE_OPERATE'] },
    ];
}

function getMockWorkflows(): WorkflowConfig[] {
    return [
        {
            id: 'wf-ci-cd-pipeline',
            name: 'CI/CD Pipeline',
            trigger: { event: 'git.push' },
            steps: [
                { id: 'step-1', agent: 'build-agent', action: 'compile', description: 'Compile source code' },
                { id: 'step-2', agent: 'test-agent', action: 'run-tests', description: 'Run unit and integration tests' },
                { id: 'step-3', agent: 'security-agent', action: 'scan', description: 'Security vulnerability scan' },
                { id: 'step-4', agent: 'deploy-agent', action: 'deploy', description: 'Deploy to staging environment' },
            ],
        },
        {
            id: 'wf-security-scan',
            name: 'Security Scan',
            trigger: { event: 'schedule' },
            steps: [
                { id: 'step-1', agent: 'security-agent', action: 'dependency-scan', description: 'Scan dependencies for vulnerabilities' },
                { id: 'step-2', agent: 'security-agent', action: 'sast', description: 'Static application security testing' },
                { id: 'step-3', agent: 'notification-agent', action: 'notify', description: 'Send security report' },
            ],
        },
        {
            id: 'wf-incident-response',
            name: 'Incident Response',
            trigger: { event: 'alert.triggered' },
            steps: [
                { id: 'step-1', agent: 'triage-agent', action: 'analyze', description: 'Analyze incident and gather context' },
                { id: 'step-2', agent: 'notification-agent', action: 'page', description: 'Page on-call engineer' },
                { id: 'step-3', agent: 'remediation-agent', action: 'auto-remediate', description: 'Attempt automatic remediation' },
            ],
        },
    ];
}

function getMockAgents(): AgentConfig[] {
    return [
        {
            id: 'agent-build',
            name: 'Build Agent',
            role: 'CI/CD Automation',
            department: 'engineering',
            capabilities: ['compile', 'package', 'artifact-upload', 'cache-management'],
        },
        {
            id: 'agent-security',
            name: 'Security Agent',
            role: 'Security Scanning',
            department: 'security',
            capabilities: ['vulnerability-scan', 'sast', 'dast', 'dependency-audit'],
        },
        {
            id: 'agent-deploy',
            name: 'Deploy Agent',
            role: 'Deployment Automation',
            department: 'devops',
            capabilities: ['deploy', 'rollback', 'health-check', 'traffic-shift'],
        },
        {
            id: 'agent-triage',
            name: 'Triage Agent',
            role: 'Incident Triage',
            department: 'devops',
            capabilities: ['incident-analysis', 'context-gathering', 'severity-assessment'],
        },
    ];
}

function getMockKpis(): KpiConfig[] {
    return [
        { id: 'kpi-deployment-frequency', name: 'Deployment Frequency', target: '> 10 per week', measurement: 'Count of production deployments' },
        { id: 'kpi-lead-time', name: 'Lead Time for Changes', target: '< 1 day', measurement: 'Median time from commit to deploy' },
        { id: 'kpi-mttr', name: 'Mean Time to Recovery', target: '< 1 hour', measurement: 'Average incident resolution time' },
        { id: 'kpi-change-failure-rate', name: 'Change Failure Rate', target: '< 5%', measurement: 'Failed deployments / Total deployments' },
    ];
}

// ============================================================================
// Full Org Config Hooks
// ============================================================================

/**
 * Fetch the complete organization configuration
 */
export function useOrgConfig() {
    return useQuery({
        queryKey: configQueryKeys.orgConfig(),
        queryFn: async (): Promise<OrgConfig> => {
            try {
                return await configApi.getOrgConfig();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useOrgConfig] Backend unavailable, using mock data');
                    return {
                        id: 'software-org',
                        name: 'Software Organization',
                        description: 'Virtual software organization',
                        version: '1.0.0',
                        departments: getMockDepartments(),
                        personas: getMockPersonas(),
                        phases: getMockPhases(),
                        stages: [],
                        services: [],
                        integrations: [],
                        flows: [],
                        operators: [],
                        metadata: {
                            loaded_at: new Date().toISOString(),
                            config_path: 'mock',
                        },
                    };
                }
                throw error;
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
    });
}

/**
 * Reload configuration from files
 */
export function useReloadConfig() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: () => configApi.reloadConfig(),
        onSuccess: (data) => {
            queryClient.setQueryData(configQueryKeys.orgConfig(), data);
            queryClient.invalidateQueries({ queryKey: configQueryKeys.all });
            showSuccess('Configuration reloaded successfully');
        },
        onError: (error: Error) => {
            showError(`Failed to reload configuration: ${error.message}`);
        },
    });
}

/**
 * Fetch organization graph data for visualization
 */
export function useConfigGraph() {
    return useQuery({
        queryKey: configQueryKeys.graph(),
        queryFn: async (): Promise<ConfigGraphData> => {
            try {
                return await configApi.getConfigGraph();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useConfigGraph] Backend unavailable, using mock data');
                    return { nodes: [], edges: [] };
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000, // 2 minutes
    });
}

// ============================================================================
// Department Hooks
// ============================================================================

/**
 * Fetch all departments
 */
export function useConfigDepartments() {
    return useQuery({
        queryKey: configQueryKeys.departments(),
        queryFn: async (): Promise<DepartmentConfig[]> => {
            try {
                return await configApi.getDepartments();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useConfigDepartments] Backend unavailable, using mock data');
                    return getMockDepartments();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single department by ID
 */
export function useConfigDepartment(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.department(id ?? ''),
        queryFn: () => configApi.getDepartment(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Persona Hooks
// ============================================================================

/**
 * Fetch all personas
 */
export function usePersonas() {
    return useQuery({
        queryKey: configQueryKeys.personas(),
        queryFn: async (): Promise<PersonaConfig[]> => {
            try {
                return await configApi.getPersonas();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[usePersonas] Backend unavailable, using mock data');
                    return getMockPersonas();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single persona by ID
 */
export function usePersona(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.persona(id ?? ''),
        queryFn: () => configApi.getPersona(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Phase Hooks
// ============================================================================

/**
 * Fetch all phases
 */
export function usePhases() {
    return useQuery({
        queryKey: configQueryKeys.phases(),
        queryFn: async (): Promise<PhaseConfig[]> => {
            try {
                return await configApi.getPhases();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[usePhases] Backend unavailable, using mock data');
                    return getMockPhases();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single phase by ID
 */
export function usePhase(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.phase(id ?? ''),
        queryFn: () => configApi.getPhase(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Stage Hooks
// ============================================================================

/**
 * Fetch all stage mappings
 */
export function useStages() {
    return useQuery({
        queryKey: configQueryKeys.stages(),
        queryFn: async (): Promise<StageMapping[]> => {
            try {
                return await configApi.getStages();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useStages] Backend unavailable, using mock data');
                    return getMockStages();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

// ============================================================================
// Service Hooks
// ============================================================================

/**
 * Fetch all services
 */
export function useConfigServices() {
    return useQuery({
        queryKey: configQueryKeys.services(),
        queryFn: async (): Promise<ServiceConfig[]> => {
            try {
                return await configApi.getServices();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useConfigServices] Backend unavailable, using mock data');
                    return [];
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single service by ID
 */
export function useConfigService(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.service(id ?? ''),
        queryFn: () => configApi.getService(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Integration Hooks
// ============================================================================

/**
 * Fetch all integrations
 */
export function useConfigIntegrations() {
    return useQuery({
        queryKey: configQueryKeys.integrations(),
        queryFn: async (): Promise<IntegrationConfig[]> => {
            try {
                return await configApi.getIntegrations();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useConfigIntegrations] Backend unavailable, using mock data');
                    return [];
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single integration by ID
 */
export function useConfigIntegration(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.integration(id ?? ''),
        queryFn: () => configApi.getIntegration(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Interaction Hooks
// ============================================================================

/**
 * Fetch all interactions
 */
export function useInteractions() {
    return useQuery({
        queryKey: configQueryKeys.interactions(),
        queryFn: async (): Promise<InteractionConfig[]> => {
            try {
                return await configApi.getInteractions();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useInteractions] Backend unavailable, using mock data');
                    return [];
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single interaction by ID
 */
export function useInteraction(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.interaction(id ?? ''),
        queryFn: () => configApi.getInteraction(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Flow Hooks
// ============================================================================

/**
 * Fetch all DevSecOps flows
 */
export function useFlows() {
    return useQuery({
        queryKey: configQueryKeys.flows(),
        queryFn: async (): Promise<FlowConfig[]> => {
            try {
                return await configApi.getFlows();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useFlows] Backend unavailable, using mock data');
                    return [];
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single flow by ID
 */
export function useFlow(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.flow(id ?? ''),
        queryFn: () => configApi.getFlow(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Operator Hooks
// ============================================================================

/**
 * Fetch all operators
 */
export function useOperators() {
    return useQuery({
        queryKey: configQueryKeys.operators(),
        queryFn: async (): Promise<OperatorConfig[]> => {
            try {
                return await configApi.getOperators();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useOperators] Backend unavailable, using mock data');
                    return [];
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single operator by ID
 */
export function useOperator(id: string | null) {
    return useQuery({
        queryKey: configQueryKeys.operator(id ?? ''),
        queryFn: () => configApi.getOperator(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

// ============================================================================
// Aggregated Hooks
// ============================================================================

/**
 * Fetch all agents across all departments
 */
export function useAgents() {
    return useQuery({
        queryKey: configQueryKeys.agents(),
        queryFn: async (): Promise<AgentConfig[]> => {
            try {
                return await configApi.getAgents();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useAgents] Backend unavailable, using mock data');
                    return getMockAgents();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch all workflows across all departments
 */
export function useConfigWorkflows() {
    return useQuery({
        queryKey: configQueryKeys.workflows(),
        queryFn: async (): Promise<WorkflowConfig[]> => {
            try {
                return await configApi.getWorkflows();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useConfigWorkflows] Backend unavailable, using mock data');
                    return getMockWorkflows();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch all KPIs across all departments
 */
export function useKpis() {
    return useQuery({
        queryKey: configQueryKeys.kpis(),
        queryFn: async (): Promise<KpiConfig[]> => {
            try {
                return await configApi.getKpis();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useKpis] Backend unavailable, using mock data');
                    return getMockKpis();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}
