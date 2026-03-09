/**
 * Virtual Organization React Query Hooks
 *
 * <p><b>Purpose</b><br>
 * React Query hooks for fetching and mutating virtual organization data.
 * Provides caching, background refetching, and optimistic updates.
 *
 * <p><b>Features</b><br>
 * - Automatic fallback to mock data when backend is unavailable
 * - Optimistic updates for mutations
 * - Stale-while-revalidate caching strategy
 * - Error handling with toast notifications
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: orgGraph, isLoading } = useOrgGraph();
 * const { mutate: updateDept } = useUpdateDepartment();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Virtual organization data hooks
 * @doc.layer product
 * @doc.pattern React Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useToast } from '@/lib/toast';
import {
    virtualOrgApi,
    type DepartmentFilters,
    type ServiceFilters,
    type WorkflowFilters,
    type IntegrationFilters,
    type PersonaBindingFilters,
} from '@/services/api/virtualOrgApi';
import { getMockOrgGraphData, getMockOrgConfig } from '@/features/org/mockOrgData';
import type {
    OrgConfig,
    DepartmentConfig,
    ServiceConfig,
    WorkflowConfig,
    IntegrationConfig,
    PersonaBinding,
} from '@/shared/types/org';

// ============================================================================
// Query Keys
// ============================================================================

export const orgQueryKeys = {
    all: ['org'] as const,
    config: () => [...orgQueryKeys.all, 'config'] as const,
    graph: () => [...orgQueryKeys.all, 'graph'] as const,
    node: (id: string) => [...orgQueryKeys.all, 'node', id] as const,
    departments: (filters?: DepartmentFilters) => [...orgQueryKeys.all, 'departments', filters] as const,
    department: (id: string) => [...orgQueryKeys.all, 'department', id] as const,
    services: (filters?: ServiceFilters) => [...orgQueryKeys.all, 'services', filters] as const,
    service: (id: string) => [...orgQueryKeys.all, 'service', id] as const,
    workflows: (filters?: WorkflowFilters) => [...orgQueryKeys.all, 'workflows', filters] as const,
    workflow: (id: string) => [...orgQueryKeys.all, 'workflow', id] as const,
    integrations: (filters?: IntegrationFilters) => [...orgQueryKeys.all, 'integrations', filters] as const,
    integration: (id: string) => [...orgQueryKeys.all, 'integration', id] as const,
    personaBindings: (filters?: PersonaBindingFilters) => [...orgQueryKeys.all, 'personaBindings', filters] as const,
    personaBinding: (id: string) => [...orgQueryKeys.all, 'personaBinding', id] as const,
};

// ============================================================================
// Configuration to control mock fallback
// ============================================================================

const USE_MOCK_FALLBACK = true; // Set to false when backend is ready

// ============================================================================
// Org Config Hooks
// ============================================================================

/**
 * Fetch organization configuration
 */
export function useOrgConfig() {
    return useQuery({
        queryKey: orgQueryKeys.config(),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getOrgConfig();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useOrgConfig] Backend unavailable, using mock data');
                    return getMockOrgConfig();
                }
                throw error;
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
    });
}

/**
 * Update organization configuration
 */
export function useUpdateOrgConfig() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (config: Partial<OrgConfig>) => virtualOrgApi.updateOrgConfig(config),
        onSuccess: (data) => {
            queryClient.setQueryData(orgQueryKeys.config(), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Organization configuration updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update configuration: ${error.message}`);
        },
    });
}

// ============================================================================
// Org Graph Hooks
// ============================================================================

/**
 * Fetch organization graph data for visualization
 */
export function useOrgGraph() {
    return useQuery({
        queryKey: orgQueryKeys.graph(),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getOrgGraph();
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useOrgGraph] Backend unavailable, using mock data');
                    return getMockOrgGraphData();
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000, // 2 minutes
    });
}

/**
 * Fetch a single node from the org graph
 */
export function useOrgNode(nodeId: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.node(nodeId ?? ''),
        queryFn: () => virtualOrgApi.getOrgNode(nodeId!),
        enabled: !!nodeId,
        staleTime: 1 * 60 * 1000, // 1 minute
    });
}

// ============================================================================
// Department Hooks
// ============================================================================

/**
 * Fetch departments list
 */
export function useDepartments(filters?: DepartmentFilters) {
    return useQuery({
        queryKey: orgQueryKeys.departments(filters),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getDepartments(filters);
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useDepartments] Backend unavailable, using mock data');
                    const mockGraph = getMockOrgGraphData();
                    return mockGraph.nodes
                        .filter(n => n.type === 'department')
                        .map(n => n.data as DepartmentConfig);
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single department
 */
export function useDepartment(id: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.department(id ?? ''),
        queryFn: () => virtualOrgApi.getDepartment(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

/**
 * Create a new department
 */
export function useCreateDepartment() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (department: Omit<DepartmentConfig, 'id'>) => virtualOrgApi.createDepartment(department),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Department created');
        },
        onError: (error: Error) => {
            showError(`Failed to create department: ${error.message}`);
        },
    });
}

/**
 * Update a department
 */
export function useUpdateDepartment() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: ({ id, ...data }: { id: string } & Partial<DepartmentConfig>) =>
            virtualOrgApi.updateDepartment(id, data),
        onSuccess: (data, variables) => {
            queryClient.setQueryData(orgQueryKeys.department(variables.id), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Department updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update department: ${error.message}`);
        },
    });
}

/**
 * Delete a department
 */
export function useDeleteDepartment() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.deleteDepartment(id),
        onSuccess: (_, id) => {
            queryClient.removeQueries({ queryKey: orgQueryKeys.department(id) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Department deleted');
        },
        onError: (error: Error) => {
            showError(`Failed to delete department: ${error.message}`);
        },
    });
}

// ============================================================================
// Service Hooks
// ============================================================================

/**
 * Fetch services list
 */
export function useServices(filters?: ServiceFilters) {
    return useQuery({
        queryKey: orgQueryKeys.services(filters),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getServices(filters);
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useServices] Backend unavailable, using mock data');
                    const mockGraph = getMockOrgGraphData();
                    return mockGraph.nodes
                        .filter(n => n.type === 'service')
                        .map(n => n.data as ServiceConfig);
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single service
 */
export function useService(id: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.service(id ?? ''),
        queryFn: () => virtualOrgApi.getService(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

/**
 * Create a new service
 */
export function useCreateService() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (service: Omit<ServiceConfig, 'id'>) => virtualOrgApi.createService(service),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.services() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Service created');
        },
        onError: (error: Error) => {
            showError(`Failed to create service: ${error.message}`);
        },
    });
}

/**
 * Update a service
 */
export function useUpdateService() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: ({ id, ...data }: { id: string } & Partial<ServiceConfig>) =>
            virtualOrgApi.updateService(id, data),
        onSuccess: (data, variables) => {
            queryClient.setQueryData(orgQueryKeys.service(variables.id), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.services() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Service updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update service: ${error.message}`);
        },
    });
}

/**
 * Delete a service
 */
export function useDeleteService() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.deleteService(id),
        onSuccess: (_, id) => {
            queryClient.removeQueries({ queryKey: orgQueryKeys.service(id) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.services() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Service deleted');
        },
        onError: (error: Error) => {
            showError(`Failed to delete service: ${error.message}`);
        },
    });
}

// ============================================================================
// Workflow Hooks
// ============================================================================

/**
 * Fetch workflows list
 */
export function useWorkflows(filters?: WorkflowFilters) {
    return useQuery({
        queryKey: orgQueryKeys.workflows(filters),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getWorkflows(filters);
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useWorkflows] Backend unavailable, using mock data');
                    const mockGraph = getMockOrgGraphData();
                    return mockGraph.nodes
                        .filter(n => n.type === 'workflow')
                        .map(n => n.data as WorkflowConfig);
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single workflow
 */
export function useWorkflow(id: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.workflow(id ?? ''),
        queryFn: () => virtualOrgApi.getWorkflow(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

/**
 * Create a new workflow
 */
export function useCreateWorkflow() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (workflow: Omit<WorkflowConfig, 'id'>) => virtualOrgApi.createWorkflow(workflow),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.workflows() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Workflow created');
        },
        onError: (error: Error) => {
            showError(`Failed to create workflow: ${error.message}`);
        },
    });
}

/**
 * Update a workflow
 */
export function useUpdateWorkflow() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: ({ id, ...data }: { id: string } & Partial<WorkflowConfig>) =>
            virtualOrgApi.updateWorkflow(id, data),
        onSuccess: (data, variables) => {
            queryClient.setQueryData(orgQueryKeys.workflow(variables.id), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.workflows() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Workflow updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update workflow: ${error.message}`);
        },
    });
}

/**
 * Delete a workflow
 */
export function useDeleteWorkflow() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.deleteWorkflow(id),
        onSuccess: (_, id) => {
            queryClient.removeQueries({ queryKey: orgQueryKeys.workflow(id) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.workflows() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Workflow deleted');
        },
        onError: (error: Error) => {
            showError(`Failed to delete workflow: ${error.message}`);
        },
    });
}

// ============================================================================
// Integration Hooks
// ============================================================================

/**
 * Fetch integrations list
 */
export function useIntegrations(filters?: IntegrationFilters) {
    return useQuery({
        queryKey: orgQueryKeys.integrations(filters),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getIntegrations(filters);
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[useIntegrations] Backend unavailable, using mock data');
                    const mockGraph = getMockOrgGraphData();
                    return mockGraph.nodes
                        .filter(n => n.type === 'integration')
                        .map(n => n.data as IntegrationConfig);
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single integration
 */
export function useIntegration(id: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.integration(id ?? ''),
        queryFn: () => virtualOrgApi.getIntegration(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

/**
 * Create a new integration
 */
export function useCreateIntegration() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (integration: Omit<IntegrationConfig, 'id'>) => virtualOrgApi.createIntegration(integration),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.integrations() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Integration created');
        },
        onError: (error: Error) => {
            showError(`Failed to create integration: ${error.message}`);
        },
    });
}

/**
 * Update an integration
 */
export function useUpdateIntegration() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: ({ id, ...data }: { id: string } & Partial<IntegrationConfig>) =>
            virtualOrgApi.updateIntegration(id, data),
        onSuccess: (data, variables) => {
            queryClient.setQueryData(orgQueryKeys.integration(variables.id), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.integrations() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Integration updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update integration: ${error.message}`);
        },
    });
}

/**
 * Delete an integration
 */
export function useDeleteIntegration() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.deleteIntegration(id),
        onSuccess: (_, id) => {
            queryClient.removeQueries({ queryKey: orgQueryKeys.integration(id) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.integrations() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Integration deleted');
        },
        onError: (error: Error) => {
            showError(`Failed to delete integration: ${error.message}`);
        },
    });
}

/**
 * Test integration connection
 */
export function useTestIntegration() {
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.testIntegration(id),
        onSuccess: (result) => {
            if (result.success) {
                showSuccess('Integration connection successful');
            } else {
                showError(`Integration test failed: ${result.message}`);
            }
        },
        onError: (error: Error) => {
            showError(`Failed to test integration: ${error.message}`);
        },
    });
}

// ============================================================================
// Persona Binding Hooks
// ============================================================================

/**
 * Fetch persona bindings list
 */
export function usePersonaBindings(filters?: PersonaBindingFilters) {
    return useQuery({
        queryKey: orgQueryKeys.personaBindings(filters),
        queryFn: async () => {
            try {
                return await virtualOrgApi.getPersonaBindings(filters);
            } catch (error) {
                if (USE_MOCK_FALLBACK) {
                    console.warn('[usePersonaBindings] Backend unavailable, using mock data');
                    const mockGraph = getMockOrgGraphData();
                    return mockGraph.nodes
                        .filter(n => n.type === 'persona')
                        .map(n => n.data as PersonaBinding);
                }
                throw error;
            }
        },
        staleTime: 2 * 60 * 1000,
    });
}

/**
 * Fetch a single persona binding
 */
export function usePersonaBinding(id: string | null) {
    return useQuery({
        queryKey: orgQueryKeys.personaBinding(id ?? ''),
        queryFn: () => virtualOrgApi.getPersonaBinding(id!),
        enabled: !!id,
        staleTime: 1 * 60 * 1000,
    });
}

/**
 * Create a new persona binding
 */
export function useCreatePersonaBinding() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (binding: Omit<PersonaBinding, 'id'>) => virtualOrgApi.createPersonaBinding(binding),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.personaBindings() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Persona binding created');
        },
        onError: (error: Error) => {
            showError(`Failed to create persona binding: ${error.message}`);
        },
    });
}

/**
 * Update a persona binding
 */
export function useUpdatePersonaBinding() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: ({ id, ...data }: { id: string } & Partial<PersonaBinding>) =>
            virtualOrgApi.updatePersonaBinding(id, data),
        onSuccess: (data, variables) => {
            queryClient.setQueryData(orgQueryKeys.personaBinding(variables.id), data);
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.personaBindings() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Persona binding updated');
        },
        onError: (error: Error) => {
            showError(`Failed to update persona binding: ${error.message}`);
        },
    });
}

/**
 * Delete a persona binding
 */
export function useDeletePersonaBinding() {
    const queryClient = useQueryClient();
    const { showSuccess, showError } = useToast();

    return useMutation({
        mutationFn: (id: string) => virtualOrgApi.deletePersonaBinding(id),
        onSuccess: (_, id) => {
            queryClient.removeQueries({ queryKey: orgQueryKeys.personaBinding(id) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.personaBindings() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.graph() });
            showSuccess('Persona binding deleted');
        },
        onError: (error: Error) => {
            showError(`Failed to delete persona binding: ${error.message}`);
        },
    });
}
