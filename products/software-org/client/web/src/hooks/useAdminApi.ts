/**
 * Admin API Hooks
 *
 * React Query hooks for all Admin API endpoints.
 * Covers Organization, Security, and Settings journeys
 * from SOFTWARE_ORG_ADMIN_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type hooks
 * @doc.purpose Admin API data fetching and mutations
 * @doc.layer product
 * @doc.pattern React Query
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { orgDataAtom } from '../state/orgState';

// =============================================================================
// Types (matching backend types/admin.ts)
// =============================================================================

export interface TenantResponse {
    id: string;
    key: string;
    name: string;
    displayName: string | null;
    description: string | null;
    status: string;
    plan: string;
    /** Optional slug used by some admin UIs */
    slug?: string;
    /** Optional contact email used by some admin UIs */
    contactEmail?: string;
    createdAt: string;
    updatedAt: string;
    environmentCount?: number;
}

export interface TenantCreateBody {
    key: string;
    name: string;
    displayName?: string;
    description?: string;
    plan?: string;
}

export interface EnvironmentResponse {
    id: string;
    key: string;
    name: string;
    tenantId: string;
}

export interface DepartmentResponse {
    id: string;
    name: string;
    description: string | null;
    organizationId: string;
    type: string;
    status: string;
    teamCount?: number;
    memberCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface DepartmentCreateBody {
    name: string;
    description?: string;
    tenantId?: string;
    type?: string;
    /** Optional slug (some UIs send this) */
    slug?: string;
}

export interface TeamResponse {
    id: string;
    name: string;
    description: string | null;
    departmentId: string;
    tenantId: string;
    slug: string;
    leadId: string | null;
    status: string;
    departmentName?: string;
    /** Optional nested department for legacy UIs */
    department?: { id?: string; name: string };
    memberCount?: number;
    serviceCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface TeamCreateBody {
    name: string;
    description?: string;
    departmentId?: string;
    tenantId?: string;
    /** Optional slug (some UIs send this) */
    slug?: string;
}

export interface ServiceResponse {
    id: string;
    key: string;
    name: string;
    description: string | null;
    tenantId: string;
    ownerTeamId: string | null;
    status: string;
    ownerTeamName?: string;
}

export interface ServiceCreateBody {
    key: string;
    name: string;
    description?: string;
    tenantId: string;
    ownerTeamId?: string;
}

export interface PersonaResponse {
    id: string;
    name: string;
    slug: string;
    type: 'HUMAN' | 'AGENT';
    description: string | null;
    tenantId: string;
    primaryTeamId: string | null;
    primaryTeamName?: string;
    active: boolean;
    roleCount?: number;
}

export interface PersonaCreateBody {
    name: string;
    slug?: string;
    type: 'HUMAN' | 'AGENT';
    description?: string;
    tenantId?: string;
    primaryTeamId?: string;
}

export interface RoleResponse {
    id: string;
    name: string;
    description: string | null;
    tenantId: string | null;
    permissions: string[];
    userCount?: number;
}

export interface RoleCreateBody {
    name: string;
    description?: string;
    tenantId?: string;
    permissions?: string[];
    /** Optional scope used by some admin UIs */
    scope?: string;
}

export interface PolicyResponse {
    id: string;
    name: string;
    description: string | null;
    tenantId: string;
    status: string;
    category: string;
    environments: string[];
    serviceIds: string[];
    rules: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

export interface PolicyCreateBody {
    name: string;
    description?: string;
    tenantId: string;
    category: string;
    environments?: string[];
    serviceIds?: string[];
    rules?: Record<string, unknown>;
}

export interface PolicySimulateBody {
    event: {
        type: string;
        serviceId?: string;
        environment?: string;
        requestedByRole?: string;
        [key: string]: unknown;
    };
}

export interface PolicySimulateResponse {
    result: 'allowed' | 'blocked' | 'warning';
    reasons: string[];
    matchedRules: string[];
}

export interface AuditEventResponse {
    id: string;
    tenantId: string | null;
    actorUserId: string | null;
    actorName?: string;
    entityType: string;
    entityId: string;
    action: string;
    details: Record<string, unknown>;
    timestamp: string;
}

export interface AuditLogQuery {
    tenantId?: string;
    entityType?: string;
    actorId?: string;
    from?: string;
    to?: string;
    page?: number;
    limit?: number;
}

export interface PlatformSettingsResponse {
    id: string;
    tenantId: string | null;
    displayName: string | null;
    defaultTimezone: string;
    defaultLocale: string;
    defaultTheme: string;
    features: Record<string, boolean>;
    appearance: Record<string, unknown>;
}

export interface IntegrationResponse {
    id: string;
    type: string;
    name: string;
    tenantId: string | null;
    status: string;
    config: Record<string, unknown>;
    /** Back-compat alias for `config` */
    configuration?: Record<string, unknown>;
    lastCheckedAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface IntegrationCreateBody {
    type: string;
    name: string;
    tenantId?: string;
    config?: Record<string, unknown>;
    /** Back-compat alias for `config` */
    configuration?: Record<string, unknown>;
}

export interface DeactivationCheckResponse {
    tenantId: string;
    blocked: boolean;
    warnings: string[];
    blockers: string[];
    serviceCount: number;
    policyCount: number;
    integrationCount: number;
}

export interface PaginatedResponse<T> {
    data: T[];
    total: number;
    page: number;
    limit: number;
}

// =============================================================================
// API Client Helper
// =============================================================================

const API_BASE = '/api/v1';

async function fetchApi<T>(
    endpoint: string,
    tenantId: string,
    options?: RequestInit
): Promise<T> {
    const response = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'X-Tenant-Id': tenantId,
            ...options?.headers,
        },
    });

    if (!response.ok) {
        const error = await response.json().catch(() => ({ error: 'Request failed' }));
        throw new Error(error.error || `HTTP ${response.status}`);
    }

    return response.json();
}

// =============================================================================
// Query Keys
// =============================================================================

export const ADMIN_QUERY_KEYS = {
    tenants: ['admin', 'tenants'] as const,
    tenant: (id: string) => ['admin', 'tenants', id] as const,
    tenantEnvironments: (tenantId: string) => ['admin', 'tenants', tenantId, 'environments'] as const,
    tenantDeactivationCheck: (id: string) => ['admin', 'tenants', id, 'deactivation-check'] as const,
    departments: ['admin', 'departments'] as const,
    department: (id: string) => ['admin', 'departments', id] as const,
    teams: ['admin', 'teams'] as const,
    team: (id: string) => ['admin', 'teams', id] as const,
    teamServices: (teamId: string) => ['admin', 'teams', teamId, 'services'] as const,
    services: ['admin', 'services'] as const,
    service: (id: string) => ['admin', 'services', id] as const,
    personas: ['admin', 'personas'] as const,
    persona: (id: string) => ['admin', 'personas', id] as const,
    roles: ['admin', 'roles'] as const,
    role: (id: string) => ['admin', 'roles', id] as const,
    roleAssignments: ['admin', 'role-assignments'] as const,
    policies: ['admin', 'policies'] as const,
    policy: (id: string) => ['admin', 'policies', id] as const,
    auditLog: ['admin', 'audit-log'] as const,
    settings: ['admin', 'settings'] as const,
    integrations: ['admin', 'integrations'] as const,
    integration: (id: string) => ['admin', 'integrations', id] as const,
    integrationsHealth: ['admin', 'integrations', 'health'] as const,
};

// =============================================================================
// Tenant Hooks
// =============================================================================

export function useTenants(options?: { status?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.tenants, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.status) params.set('status', options.status);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<TenantResponse>>(
                `/admin/tenants${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useTenant(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.tenant(id),
        queryFn: () => fetchApi<TenantResponse>(`/admin/tenants/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreateTenant() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: TenantCreateBody) =>
            fetchApi<TenantResponse>('/admin/tenants', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.tenants });
        },
    });
}

export function useUpdateTenant() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, ...body }: { id: string } & Partial<TenantCreateBody>) =>
            fetchApi<TenantResponse>(`/admin/tenants/${id}`, tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.tenants });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.tenant(variables.id) });
        },
    });
}

export function useTenantDeactivationCheck(tenantIdToCheck: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.tenantDeactivationCheck(tenantIdToCheck),
        queryFn: () =>
            fetchApi<DeactivationCheckResponse>(
                `/admin/tenants/${tenantIdToCheck}/deactivation-check`,
                tenantId
            ),
        enabled: !!tenantIdToCheck,
    });
}

export function useDeactivateTenant() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) =>
            fetchApi<TenantResponse>(`/admin/tenants/${id}/status`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ status: 'inactive' }),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.tenants });
        },
    });
}

// =============================================================================
// Environment Hooks
// =============================================================================

export function useTenantEnvironments(tenantIdToFetch: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.tenantEnvironments(tenantIdToFetch),
        queryFn: () =>
            fetchApi<{ data: EnvironmentResponse[] }>(
                `/admin/tenants/${tenantIdToFetch}/environments`,
                tenantId
            ),
        enabled: !!tenantIdToFetch,
    });
}

export function useCreateEnvironment() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            tenantIdToUpdate,
            environments,
        }: {
            tenantIdToUpdate: string;
            environments: Array<{ key: string; name: string }>;
        }) =>
            fetchApi<{ tenantId: string; environments: EnvironmentResponse[] }>(
                `/admin/tenants/${tenantIdToUpdate}/environments`,
                tenantId,
                {
                    method: 'POST',
                    body: JSON.stringify({ environments }),
                }
            ),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({
                queryKey: ADMIN_QUERY_KEYS.tenantEnvironments(variables.tenantIdToUpdate),
            });
        },
    });
}

// =============================================================================
// Department Hooks
// =============================================================================

export function useDepartments(options?: { tenantId?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.departments, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<DepartmentResponse>>(
                `/admin/departments${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useDepartment(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.department(id),
        queryFn: () => fetchApi<DepartmentResponse>(`/admin/departments/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreateDepartment() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: DepartmentCreateBody) =>
            fetchApi<DepartmentResponse>('/admin/departments', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.departments });
        },
    });
}

// =============================================================================
// Team Hooks
// =============================================================================

export function useTeams(options?: { tenantId?: string; departmentId?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.teams, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.departmentId) params.set('departmentId', options.departmentId);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<TeamResponse>>(
                `/admin/teams${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useTeam(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.team(id),
        queryFn: () => fetchApi<TeamResponse>(`/admin/teams/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useTeamServices(teamId: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.teamServices(teamId),
        queryFn: () => fetchApi<{ data: ServiceResponse[] }>(`/admin/teams/${teamId}/services`, tenantId),
        enabled: !!teamId,
    });
}

export function useCreateTeam() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: TeamCreateBody) =>
            fetchApi<TeamResponse>('/admin/teams', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.teams });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.departments });
        },
    });
}

export function useUpdateTeam() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, ...body }: { id: string } & Partial<TeamCreateBody>) =>
            fetchApi<TeamResponse>(`/admin/teams/${id}`, tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.teams });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.team(variables.id) });
        },
    });
}

// =============================================================================
// Service Hooks
// =============================================================================

export function useServices(options?: { tenantId?: string; ownerTeamId?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.services, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.ownerTeamId) params.set('ownerTeamId', options.ownerTeamId);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<ServiceResponse>>(
                `/admin/services${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useService(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.service(id),
        queryFn: () => fetchApi<ServiceResponse>(`/admin/services/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreateService() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: ServiceCreateBody) =>
            fetchApi<ServiceResponse>('/admin/services', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.services });
        },
    });
}

export function useUpdateServiceOwnership() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            serviceIds,
            newOwnerTeamId,
            tenantIdToUpdate,
        }: {
            serviceIds: string[];
            newOwnerTeamId: string;
            tenantIdToUpdate: string;
        }) =>
            fetchApi<{ updated: number; failed: string[] }>('/admin/services/ownership', tenantId, {
                method: 'PUT',
                body: JSON.stringify({ serviceIds, newOwnerTeamId, tenantId: tenantIdToUpdate }),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.services });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.teams });
        },
    });
}

export function useLinkServiceWorkflows() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            serviceId,
            workflowIds,
            agentIds,
        }: {
            serviceId: string;
            workflowIds?: string[];
            agentIds?: string[];
        }) =>
            fetchApi<ServiceResponse>(`/admin/services/${serviceId}/links`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ workflowIds, agentIds }),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.service(variables.serviceId) });
        },
    });
}

// =============================================================================
// Persona Hooks
// =============================================================================

export function usePersonas(options?: { tenantId?: string; type?: 'HUMAN' | 'AGENT'; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.personas, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.type) params.set('type', options.type);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<PersonaResponse>>(
                `/admin/personas${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function usePersona(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.persona(id),
        queryFn: () => fetchApi<PersonaResponse>(`/admin/personas/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreatePersona() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: PersonaCreateBody) =>
            fetchApi<PersonaResponse>('/admin/personas', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.personas });
        },
    });
}

export function useUpdatePersonaMembers() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            personaId,
            userIds,
            teamIds,
            agentIds,
        }: {
            personaId: string;
            userIds?: string[];
            teamIds?: string[];
            agentIds?: string[];
        }) =>
            fetchApi<PersonaResponse>(`/admin/personas/${personaId}/members`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ userIds, teamIds, agentIds }),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.persona(variables.personaId) });
        },
    });
}

export function useUpdatePersonaRoles() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ personaId, roleIds }: { personaId: string; roleIds: string[] }) =>
            fetchApi<PersonaResponse>(`/admin/personas/${personaId}/roles`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ roleIds }),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.persona(variables.personaId) });
        },
    });
}

// =============================================================================
// Role Hooks
// =============================================================================

export function useRoles(options?: { tenantId?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.roles, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<RoleResponse>>(
                `/admin/roles${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useRole(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.role(id),
        queryFn: () => fetchApi<RoleResponse>(`/admin/roles/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreateRole() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: RoleCreateBody) =>
            fetchApi<RoleResponse>('/admin/roles', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.roles });
        },
    });
}

export function useUpdateRole() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, ...body }: { id: string } & Partial<RoleCreateBody>) =>
            fetchApi<RoleResponse>(`/admin/roles/${id}`, tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.roles });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.role(variables.id) });
        },
    });
}

// =============================================================================
// Role Assignment Hooks
// =============================================================================

export function useRoleAssignments(options?: { userId?: string; tenantId?: string }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.roleAssignments, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.userId) params.set('userId', options.userId);
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            const qs = params.toString();
            return fetchApi<{
                data: Array<{
                    id: string;
                    userId: string;
                    tenantId: string;
                    roleId: string;
                    roleName: string;
                }>;
            }>(`/admin/role-assignments${qs ? `?${qs}` : ''}`, tenantId);
        },
        staleTime: 30000,
    });
}

export function useCreateRoleAssignment() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: { userId: string; tenantId: string; roleId: string; scope?: string }) =>
            fetchApi<{ id: string; userId: string; tenantId: string; roleId: string }>(
                '/admin/role-assignments',
                tenantId,
                {
                    method: 'POST',
                    body: JSON.stringify(body),
                }
            ),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.roleAssignments });
        },
    });
}

// =============================================================================
// Policy Hooks
// =============================================================================

export function usePolicies(options?: { tenantId?: string; status?: string; serviceId?: string; page?: number; limit?: number }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.policies, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.status) params.set('status', options.status);
            if (options?.serviceId) params.set('serviceId', options.serviceId);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<PolicyResponse>>(
                `/admin/policies${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function usePolicy(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.policy(id),
        queryFn: () => fetchApi<PolicyResponse>(`/admin/policies/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useCreatePolicy() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: PolicyCreateBody) =>
            fetchApi<PolicyResponse>('/admin/policies', tenantId, {
                method: 'POST',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.policies });
        },
    });
}

export function useUpdatePolicy() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, ...body }: { id: string } & Partial<PolicyCreateBody>) =>
            fetchApi<PolicyResponse>(`/admin/policies/${id}`, tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.policies });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.policy(variables.id) });
        },
    });
}

export function useSimulatePolicy() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useMutation({
        mutationFn: (input: { policyId?: string; policy?: PolicyCreateBody; event: PolicySimulateBody['event'] }) => {
            if (input.policyId) {
                return fetchApi<PolicySimulateResponse>(`/admin/policies/${input.policyId}/simulate`, tenantId, {
                    method: 'POST',
                    body: JSON.stringify({ event: input.event }),
                });
            }

            if (!input.policy) {
                throw new Error('policy is required when policyId is not provided');
            }

            return fetchApi<PolicySimulateResponse>('/admin/policies/simulate', tenantId, {
                method: 'POST',
                body: JSON.stringify({ policy: input.policy, event: input.event }),
            });
        },
    });
}

export function useUpdatePolicyStatus() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ policyId, status }: { policyId: string; status: 'active' | 'inactive' | 'draft' }) =>
            fetchApi<PolicyResponse>(`/admin/policies/${policyId}/status`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ status }),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.policies });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.policy(variables.policyId) });
        },
    });
}

// =============================================================================
// Permission Simulator Hooks
// =============================================================================

export interface PermissionSimulateResponse {
    userId: string;
    permissionId: string;
    granted: boolean;
    matchedRoles: Array<{
        roleId: string;
        roleName: string;
        roleSlug: string;
    }>;
    allRoles: Array<{
        roleId: string;
        roleName: string;
        permissions: string[];
    }>;
}

export function useSimulatePermission() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useMutation({
        mutationFn: ({ userId, permissionId }: { userId: string; permissionId: string }) =>
            fetchApi<PermissionSimulateResponse>('/admin/permissions/simulate', tenantId, {
                method: 'POST',
                body: JSON.stringify({ userId, permissionId }),
            }),
    });
}

// =============================================================================
// Audit Log Hooks
// =============================================================================

export function useAuditLog(options?: AuditLogQuery) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.auditLog, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.entityType) params.set('entityType', options.entityType);
            if (options?.actorId) params.set('actorId', options.actorId);
            if (options?.from) params.set('from', options.from);
            if (options?.to) params.set('to', options.to);
            if (options?.page) params.set('page', options.page.toString());
            if (options?.limit) params.set('limit', options.limit.toString());
            const qs = params.toString();
            return fetchApi<PaginatedResponse<AuditEventResponse>>(
                `/admin/audit-log${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 10000,
    });
}

export function useAuditEvent(eventId: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.auditLog, eventId],
        queryFn: () => fetchApi<AuditEventResponse>(`/admin/audit-log/${eventId}`, tenantId),
        enabled: !!eventId,
    });
}

export function useExportAuditLog() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useMutation({
        mutationFn: async ({
            format,
            ...filters
        }: AuditLogQuery & { format: 'json' | 'csv' }) => {
            const params = new URLSearchParams();
            params.set('format', format);
            if (filters.tenantId) params.set('tenantId', filters.tenantId);
            if (filters.entityType) params.set('entityType', filters.entityType);
            if (filters.from) params.set('from', filters.from);
            if (filters.to) params.set('to', filters.to);

            const response = await fetch(`${API_BASE}/admin/audit-log/export?${params}`, {
                headers: {
                    'X-Tenant-Id': tenantId,
                },
            });

            if (!response.ok) {
                throw new Error('Export failed');
            }

            return response.blob();
        },
    });
}

// =============================================================================
// Platform Settings Hooks
// =============================================================================

export function usePlatformSettings(tenantIdToFetch?: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.settings, tenantIdToFetch],
        queryFn: () => {
            const params = tenantIdToFetch ? `?tenantId=${tenantIdToFetch}` : '';
            return fetchApi<PlatformSettingsResponse>(`/admin/settings/platform${params}`, tenantId);
        },
        staleTime: 60000,
    });
}

export function useUpdatePlatformSettings() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: Partial<PlatformSettingsResponse>) =>
            fetchApi<PlatformSettingsResponse>('/admin/settings/platform', tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.settings });
        },
    });
}

// =============================================================================
// Integration Hooks
// =============================================================================

export function useIntegrations(options?: { tenantId?: string; type?: string }) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.integrations, options],
        queryFn: () => {
            const params = new URLSearchParams();
            if (options?.tenantId) params.set('tenantId', options.tenantId);
            if (options?.type) params.set('type', options.type);
            const qs = params.toString();
            return fetchApi<{ data: IntegrationResponse[] }>(
                `/admin/integrations${qs ? `?${qs}` : ''}`,
                tenantId
            );
        },
        staleTime: 30000,
    });
}

export function useIntegration(id: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.integration(id),
        queryFn: () => fetchApi<IntegrationResponse>(`/admin/integrations/${id}`, tenantId),
        enabled: !!id,
    });
}

export function useIntegrationsHealth() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: ADMIN_QUERY_KEYS.integrationsHealth,
        queryFn: () =>
            fetchApi<{ data: Array<{ id: string; type: string; status: string; latencyMs?: number }> }>(
                '/admin/integrations/health',
                tenantId
            ),
        staleTime: 15000,
        refetchInterval: 30000,
    });
}

export function useCreateIntegration() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: IntegrationCreateBody) => {
            const { configuration, ...rest } = body;
            const payload = {
                ...rest,
                config: rest.config ?? configuration,
            };

            return fetchApi<IntegrationResponse>('/admin/integrations', tenantId, {
                method: 'POST',
                body: JSON.stringify(payload),
            });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integrations });
        },
    });
}

export function useUpdateIntegration() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, ...body }: { id: string } & Partial<IntegrationCreateBody>) => {
            const { configuration, ...rest } = body;
            const payload = {
                ...rest,
                config: rest.config ?? configuration,
            };

            return fetchApi<IntegrationResponse>(`/admin/integrations/${id}`, tenantId, {
                method: 'PUT',
                body: JSON.stringify(payload),
            });
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integrations });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integration(variables.id) });
        },
    });
}

export function useTestIntegration() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (integrationId: string) =>
            fetchApi<{ status: string; latencyMs: number; message: string }>(
                `/admin/integrations/${integrationId}/test`,
                tenantId,
                { method: 'POST' }
            ),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integrationsHealth });
        },
    });
}

export function useUpdateIntegrationStatus() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, status }: { id: string; status: 'connected' | 'disabled' }) =>
            fetchApi<IntegrationResponse>(`/admin/integrations/${id}/status`, tenantId, {
                method: 'PUT',
                body: JSON.stringify({ status }),
            }),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integrations });
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.integration(variables.id) });
        },
    });
}

// =============================================================================
// AI & Agent Settings Hooks
// =============================================================================

export interface AIAgentSettingsResponse {
    tenantId: string | null;
    allowedToolsByPersona: Record<string, string[]>;
    guardrails: {
        autoRemediationEnvironments: string[];
        requireHumanApprovalInProd: boolean;
    };
    auditLogging: {
        enabled: boolean;
    };
}

export function useAIAgentSettings(tenantIdToFetch?: string) {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';

    return useQuery({
        queryKey: [...ADMIN_QUERY_KEYS.settings, 'ai-agents', tenantIdToFetch],
        queryFn: () => {
            const params = tenantIdToFetch ? `?tenantId=${tenantIdToFetch}` : '';
            return fetchApi<AIAgentSettingsResponse>(`/admin/settings/ai-agents${params}`, tenantId);
        },
        staleTime: 60000,
    });
}

export function useUpdateAIAgentSettings() {
    const [orgData] = useAtom(orgDataAtom);
    const tenantId = orgData?.tenantId || 'default';
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: Partial<AIAgentSettingsResponse>) =>
            fetchApi<AIAgentSettingsResponse>('/admin/settings/ai-agents', tenantId, {
                method: 'PUT',
                body: JSON.stringify(body),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEYS.settings });
        },
    });
}
