/**
 * Persona API service
 *
 * @doc.type service
 * @doc.purpose Persona preference management using @ghatana/api
 * @doc.layer product
 * @doc.pattern Service Pattern
 *
 * Provides methods for interacting with persona preference endpoints.
 * All methods are type-safe and throw ApiError on failures.
 *
 * <p><b>Mock Mode</b><br>
 * When the backend is unavailable (connection refused), returns mock data
 * to allow frontend development without a running backend.
 */

import { softwareOrgApi, isConnectionError, extractData } from './ghatana-client';
import type {
    RoleDefinition,
    PersonaPreference,
    UpdatePersonaPreferenceInput,
    EffectivePermissions,
    ValidationResult,
    WorkspaceAccessResult,
} from './persona.types';

// ============================================================================
// MOCK DATA (used when backend is unavailable)
// ============================================================================

const MOCK_ROLES: RoleDefinition[] = [
    {
        roleId: 'admin',
        displayName: 'Admin',
        description: 'Full administrative access to all features',
        type: 'BASE',
        permissions: ['read', 'write', 'delete', 'admin'],
        capabilities: ['manage-users', 'manage-roles', 'manage-settings', 'view-all'],
        parentRoles: [],
    },
    {
        roleId: 'lead',
        displayName: 'Tech Lead',
        description: 'Team lead with planning and oversight capabilities',
        type: 'SPECIALIZED',
        permissions: ['read', 'write', 'approve'],
        capabilities: ['view-metrics', 'manage-team', 'approve-deployments', 'view-reports'],
        parentRoles: ['engineer'],
    },
    {
        roleId: 'engineer',
        displayName: 'Software Engineer',
        description: 'Developer with access to coding and deployment tools',
        type: 'BASE',
        permissions: ['read', 'write'],
        capabilities: ['view-metrics', 'deploy', 'view-pipelines', 'manage-code'],
        parentRoles: [],
    },
    {
        roleId: 'viewer',
        displayName: 'Viewer',
        description: 'Read-only access to dashboards and reports',
        type: 'BASE',
        permissions: ['read'],
        capabilities: ['view-metrics', 'view-reports'],
        parentRoles: [],
    },
];

const MOCK_PREFERENCE: PersonaPreference = {
    id: 'pref-default',
    userId: 'user-1',
    workspaceId: 'default',
    activeRoles: ['engineer'],
    preferences: {
        dashboardLayout: {
            type: 'grid',
            columns: 3,
        },
    },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
};

/**
 * Get all available role definitions
 */
export async function getAllRoles(): Promise<RoleDefinition[]> {
    try {
        const response = await softwareOrgApi.get<RoleDefinition[]>('/personas/roles');
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, using mock roles');
            return MOCK_ROLES;
        }
        throw error;
    }
}

/**
 * Get specific role definition by ID
 */
export async function getRoleDefinition(roleId: string): Promise<RoleDefinition> {
    try {
        const response = await softwareOrgApi.get<RoleDefinition>(`/personas/roles/${roleId}`);
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, using mock role');
            const role = MOCK_ROLES.find(r => r.roleId === roleId);
            if (role) return role;
            throw new Error(`Role not found: ${roleId}`);
        }
        throw error;
    }
}

/**
 * Get user's persona preference for a workspace
 */
export async function getPersonaPreference(
    workspaceId: string
): Promise<PersonaPreference | null> {
    try {
        const response = await softwareOrgApi.get<PersonaPreference>(
            `/personas/preferences/${workspaceId}`
        );
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, using mock preference');
            return { ...MOCK_PREFERENCE, workspaceId };
        }
        if (error instanceof Error && 'status' in error && (error as any).status === 404) {
            return null;
        }
        throw error;
    }
}

/**
 * Create or update persona preference
 */
export async function upsertPersonaPreference(
    workspaceId: string,
    data: UpdatePersonaPreferenceInput
): Promise<PersonaPreference> {
    try {
        const response = await softwareOrgApi.put<PersonaPreference>(
            `/personas/preferences/${workspaceId}`,
            { body: data }
        );
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, returning mock response');
            return {
                ...MOCK_PREFERENCE,
                workspaceId,
                activeRoles: data.activeRoles,
                preferences: data.preferences,
                updatedAt: new Date().toISOString(),
            };
        }
        throw error;
    }
}

/**
 * Delete persona preference
 */
export async function deletePersonaPreference(workspaceId: string): Promise<void> {
    try {
        await softwareOrgApi.delete<void>(`/personas/preferences/${workspaceId}`);
        return;
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, simulating delete');
            return;
        }
        throw error;
    }
}

/**
 * Validate role activation combination
 */
export async function validateRoleActivation(
    roleIds: string[]
): Promise<ValidationResult> {
    try {
        const response = await softwareOrgApi.post<ValidationResult>('/personas/roles/validate', {
            body: { roleIds },
        });
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, returning valid');
            return { isValid: true };
        }
        throw error;
    }
}

/**
 * Resolve effective permissions from active roles
 */
export async function resolveEffectivePermissions(
    roleIds: string[]
): Promise<EffectivePermissions> {
    try {
        const response = await softwareOrgApi.post<EffectivePermissions>(
            '/personas/roles/resolve-permissions',
            { body: { roleIds } }
        );
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, computing mock permissions');
            const permissions: Record<string, boolean> = {};
            const capabilities: Record<string, boolean> = {};

            for (const roleId of roleIds) {
                const role = MOCK_ROLES.find(r => r.roleId === roleId);
                if (role) {
                    role.permissions.forEach(p => permissions[p] = true);
                    role.capabilities.forEach(c => capabilities[c] = true);
                }
            }

            return { permissions, capabilities };
        }
        throw error;
    }
}

/**
 * Verify user has access to workspace
 */
export async function verifyWorkspaceAccess(
    workspaceId: string
): Promise<WorkspaceAccessResult> {
    try {
        const response = await softwareOrgApi.get<WorkspaceAccessResult>(
            `/workspaces/${workspaceId}/access`
        );
        return extractData(response);
    } catch (error) {
        if (isConnectionError(error)) {
            console.warn('[PersonaService] Backend unavailable, granting mock access');
            return { hasAccess: true, role: 'owner' };
        }
        throw error;
    }
}
