/**
 * Virtual Organization API Client
 *
 * <p><b>Purpose</b><br>
 * API client functions for the virtual-org backend service.
 * Handles CRUD operations for organization configuration including
 * departments, services, workflows, integrations, and personas.
 *
 * <p><b>Endpoints</b><br>
 * - GET /org/config - Get full org configuration
 * - GET /org/graph - Get org graph data for visualization
 * - GET /org/departments - List departments
 * - GET /org/departments/:id - Get department details
 * - POST /org/departments - Create department
 * - PUT /org/departments/:id - Update department
 * - DELETE /org/departments/:id - Delete department
 * - Similar endpoints for services, workflows, integrations, personas
 *
 * <p><b>Backend Status</b><br>
 * These endpoints are not yet implemented in the backend.
 * The hooks using this API will fall back to mock data until available.
 *
 * @doc.type service
 * @doc.purpose Virtual organization API client
 * @doc.layer product
 * @doc.pattern API Client
 */

import { apiClient } from './index';
import type {
    OrgConfig,
    OrgGraphData,
    OrgGraphNode,
    DepartmentConfig,
    ServiceConfig,
    WorkflowConfig,
    IntegrationConfig,
    PersonaBinding,
} from '@/shared/types/org';

// Re-export types for convenience
export type OrgDepartment = DepartmentConfig;
export type OrgService = ServiceConfig;
export type OrgWorkflow = WorkflowConfig;
export type OrgIntegration = IntegrationConfig;
export type OrgPersonaBinding = PersonaBinding;

// ============================================================================
// API Response Types
// ============================================================================

export interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    timestamp: string;
}

export interface PaginatedResponse<T> {
    data: T[];
    total: number;
    page: number;
    pageSize: number;
    hasMore: boolean;
}

// ============================================================================
// Org Configuration API
// ============================================================================

/**
 * Get the full organization configuration
 */
export async function getOrgConfig(): Promise<OrgConfig> {
    const response = await apiClient.get<ApiResponse<OrgConfig>>('/org/config');
    return response.data.data;
}

/**
 * Update organization configuration
 */
export async function updateOrgConfig(config: Partial<OrgConfig>): Promise<OrgConfig> {
    const response = await apiClient.put<ApiResponse<OrgConfig>>('/org/config', config);
    return response.data.data;
}

// ============================================================================
// Org Graph API
// ============================================================================

/**
 * Get organization graph data for visualization
 */
export async function getOrgGraph(): Promise<OrgGraphData> {
    const response = await apiClient.get<ApiResponse<OrgGraphData>>('/org/graph');
    return response.data.data;
}

/**
 * Get a single node from the org graph
 */
export async function getOrgNode(nodeId: string): Promise<OrgGraphNode> {
    const response = await apiClient.get<ApiResponse<OrgGraphNode>>(`/org/graph/nodes/${nodeId}`);
    return response.data.data;
}

// ============================================================================
// Departments API
// ============================================================================

export interface DepartmentFilters {
    status?: 'active' | 'inactive';
    search?: string;
}

/**
 * List all departments
 */
export async function getDepartments(filters?: DepartmentFilters): Promise<OrgDepartment[]> {
    const params = new URLSearchParams();
    if (filters?.status) params.set('status', filters.status);
    if (filters?.search) params.set('search', filters.search);

    const response = await apiClient.get<ApiResponse<OrgDepartment[]>>(
        `/org/departments${params.toString() ? `?${params}` : ''}`
    );
    return response.data.data;
}

/**
 * Get a single department by ID
 */
export async function getDepartment(id: string): Promise<OrgDepartment> {
    const response = await apiClient.get<ApiResponse<OrgDepartment>>(`/org/departments/${id}`);
    return response.data.data;
}

/**
 * Create a new department
 */
export async function createDepartment(department: Omit<OrgDepartment, 'id'>): Promise<OrgDepartment> {
    const response = await apiClient.post<ApiResponse<OrgDepartment>>('/org/departments', department);
    return response.data.data;
}

/**
 * Update a department
 */
export async function updateDepartment(id: string, department: Partial<OrgDepartment>): Promise<OrgDepartment> {
    const response = await apiClient.put<ApiResponse<OrgDepartment>>(`/org/departments/${id}`, department);
    return response.data.data;
}

/**
 * Delete a department
 */
export async function deleteDepartment(id: string): Promise<void> {
    await apiClient.delete(`/org/departments/${id}`);
}

// ============================================================================
// Services API
// ============================================================================

export interface ServiceFilters {
    departmentId?: string;
    status?: 'healthy' | 'degraded' | 'down';
    search?: string;
}

/**
 * List all services
 */
export async function getServices(filters?: ServiceFilters): Promise<OrgService[]> {
    const params = new URLSearchParams();
    if (filters?.departmentId) params.set('departmentId', filters.departmentId);
    if (filters?.status) params.set('status', filters.status);
    if (filters?.search) params.set('search', filters.search);

    const response = await apiClient.get<ApiResponse<OrgService[]>>(
        `/org/services${params.toString() ? `?${params}` : ''}`
    );
    return response.data.data;
}

/**
 * Get a single service by ID
 */
export async function getService(id: string): Promise<OrgService> {
    const response = await apiClient.get<ApiResponse<OrgService>>(`/org/services/${id}`);
    return response.data.data;
}

/**
 * Create a new service
 */
export async function createService(service: Omit<OrgService, 'id'>): Promise<OrgService> {
    const response = await apiClient.post<ApiResponse<OrgService>>('/org/services', service);
    return response.data.data;
}

/**
 * Update a service
 */
export async function updateService(id: string, service: Partial<OrgService>): Promise<OrgService> {
    const response = await apiClient.put<ApiResponse<OrgService>>(`/org/services/${id}`, service);
    return response.data.data;
}

/**
 * Delete a service
 */
export async function deleteService(id: string): Promise<void> {
    await apiClient.delete(`/org/services/${id}`);
}

// ============================================================================
// Workflows API
// ============================================================================

export interface WorkflowFilters {
    departmentId?: string;
    status?: 'active' | 'paused' | 'archived';
    search?: string;
}

/**
 * List all workflows
 */
export async function getWorkflows(filters?: WorkflowFilters): Promise<OrgWorkflow[]> {
    const params = new URLSearchParams();
    if (filters?.departmentId) params.set('departmentId', filters.departmentId);
    if (filters?.status) params.set('status', filters.status);
    if (filters?.search) params.set('search', filters.search);

    const response = await apiClient.get<ApiResponse<OrgWorkflow[]>>(
        `/org/workflows${params.toString() ? `?${params}` : ''}`
    );
    return response.data.data;
}

/**
 * Get a single workflow by ID
 */
export async function getWorkflow(id: string): Promise<OrgWorkflow> {
    const response = await apiClient.get<ApiResponse<OrgWorkflow>>(`/org/workflows/${id}`);
    return response.data.data;
}

/**
 * Create a new workflow
 */
export async function createWorkflow(workflow: Omit<OrgWorkflow, 'id'>): Promise<OrgWorkflow> {
    const response = await apiClient.post<ApiResponse<OrgWorkflow>>('/org/workflows', workflow);
    return response.data.data;
}

/**
 * Update a workflow
 */
export async function updateWorkflow(id: string, workflow: Partial<OrgWorkflow>): Promise<OrgWorkflow> {
    const response = await apiClient.put<ApiResponse<OrgWorkflow>>(`/org/workflows/${id}`, workflow);
    return response.data.data;
}

/**
 * Delete a workflow
 */
export async function deleteWorkflow(id: string): Promise<void> {
    await apiClient.delete(`/org/workflows/${id}`);
}

// ============================================================================
// Integrations API
// ============================================================================

export interface IntegrationFilters {
    type?: string;
    status?: 'connected' | 'disconnected' | 'error';
    search?: string;
}

/**
 * List all integrations
 */
export async function getIntegrations(filters?: IntegrationFilters): Promise<OrgIntegration[]> {
    const params = new URLSearchParams();
    if (filters?.type) params.set('type', filters.type);
    if (filters?.status) params.set('status', filters.status);
    if (filters?.search) params.set('search', filters.search);

    const response = await apiClient.get<ApiResponse<OrgIntegration[]>>(
        `/org/integrations${params.toString() ? `?${params}` : ''}`
    );
    return response.data.data;
}

/**
 * Get a single integration by ID
 */
export async function getIntegration(id: string): Promise<OrgIntegration> {
    const response = await apiClient.get<ApiResponse<OrgIntegration>>(`/org/integrations/${id}`);
    return response.data.data;
}

/**
 * Create a new integration
 */
export async function createIntegration(integration: Omit<OrgIntegration, 'id'>): Promise<OrgIntegration> {
    const response = await apiClient.post<ApiResponse<OrgIntegration>>('/org/integrations', integration);
    return response.data.data;
}

/**
 * Update an integration
 */
export async function updateIntegration(id: string, integration: Partial<OrgIntegration>): Promise<OrgIntegration> {
    const response = await apiClient.put<ApiResponse<OrgIntegration>>(`/org/integrations/${id}`, integration);
    return response.data.data;
}

/**
 * Delete an integration
 */
export async function deleteIntegration(id: string): Promise<void> {
    await apiClient.delete(`/org/integrations/${id}`);
}

/**
 * Test integration connection
 */
export async function testIntegration(id: string): Promise<{ success: boolean; message: string }> {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>(
        `/org/integrations/${id}/test`
    );
    return response.data.data;
}

// ============================================================================
// Persona Bindings API
// ============================================================================

export interface PersonaBindingFilters {
    personaId?: string;
    departmentId?: string;
}

/**
 * List all persona bindings
 */
export async function getPersonaBindings(filters?: PersonaBindingFilters): Promise<OrgPersonaBinding[]> {
    const params = new URLSearchParams();
    if (filters?.personaId) params.set('personaId', filters.personaId);
    if (filters?.departmentId) params.set('departmentId', filters.departmentId);

    const response = await apiClient.get<ApiResponse<OrgPersonaBinding[]>>(
        `/org/persona-bindings${params.toString() ? `?${params}` : ''}`
    );
    return response.data.data;
}

/**
 * Get a single persona binding by ID
 */
export async function getPersonaBinding(id: string): Promise<OrgPersonaBinding> {
    const response = await apiClient.get<ApiResponse<OrgPersonaBinding>>(`/org/persona-bindings/${id}`);
    return response.data.data;
}

/**
 * Create a new persona binding
 */
export async function createPersonaBinding(binding: Omit<OrgPersonaBinding, 'id'>): Promise<OrgPersonaBinding> {
    const response = await apiClient.post<ApiResponse<OrgPersonaBinding>>('/org/persona-bindings', binding);
    return response.data.data;
}

/**
 * Update a persona binding
 */
export async function updatePersonaBinding(id: string, binding: Partial<OrgPersonaBinding>): Promise<OrgPersonaBinding> {
    const response = await apiClient.put<ApiResponse<OrgPersonaBinding>>(`/org/persona-bindings/${id}`, binding);
    return response.data.data;
}

/**
 * Delete a persona binding
 */
export async function deletePersonaBinding(id: string): Promise<void> {
    await apiClient.delete(`/org/persona-bindings/${id}`);
}

// ============================================================================
// Export all
// ============================================================================

export const virtualOrgApi = {
    // Config
    getOrgConfig,
    updateOrgConfig,
    // Graph
    getOrgGraph,
    getOrgNode,
    // Departments
    getDepartments,
    getDepartment,
    createDepartment,
    updateDepartment,
    deleteDepartment,
    // Services
    getServices,
    getService,
    createService,
    updateService,
    deleteService,
    // Workflows
    getWorkflows,
    getWorkflow,
    createWorkflow,
    updateWorkflow,
    deleteWorkflow,
    // Integrations
    getIntegrations,
    getIntegration,
    createIntegration,
    updateIntegration,
    deleteIntegration,
    testIntegration,
    // Persona Bindings
    getPersonaBindings,
    getPersonaBinding,
    createPersonaBinding,
    updatePersonaBinding,
    deletePersonaBinding,
};

export default virtualOrgApi;
