import axios, { AxiosInstance } from 'axios';
import { MockPersonaRoleService } from './persona-role-mock.service.js';

/**
 * Client for Java persona role domain service.
 *
 * Purpose:
 * Queries Java domain service for authoritative role definitions,
 * validation, and permission resolution.
 *
 * Mock Mode:
 * Set JAVA_API_MOCK=true to use MockPersonaRoleService (for development/testing).
 * Set JAVA_API_MOCK=false to call real Java HTTP service (production).
 *
 * Architecture Boundary Compliance:
 * - ✅ Node.js queries Java for role definitions (read-only)
 * - ✅ Node.js validates role combinations before saving user preferences
 * - ✅ Node.js resolves permissions for authorization checks
 * - ❌ Node.js does NOT create/modify role definitions (that's Java domain logic)
 *
 * Integration Pattern:
 * 1. User updates persona config → validateRoles() before saving to DB
 * 2. User loads persona config → getRoleDefinitions() to enrich UI
 * 3. Authorization check → resolvePermissions() to verify access
 *
 * Caching Strategy:
 * Role definitions should be cached (Redis, 1 hour TTL) since they
 * rarely change. This client does NOT implement caching - that's
 * the responsibility of the service layer.
 *
 * Error Handling:
 * - Network errors → throw Error (service layer handles retry)
 * - 404 Not Found → return null (let service layer handle)
 * - 400 Bad Request → throw ValidationError
 * - 500 Internal Error → throw Error
 */

export interface RoleDefinition {
    roleId: string;
    displayName: string;
    description: string;
    type: 'BASE' | 'SPECIALIZED' | 'CUSTOM';
    permissions: string[];
    capabilities: string[];
    parentRoles: string[];
}

export interface ValidationResult {
    isValid: boolean;
    errorMessage?: string;
}

export interface EffectivePermissions {
    permissions: Record<string, boolean>;
    capabilities: Record<string, boolean>;
}

export class ValidationError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'ValidationError';
    }
}

/**
 * HTTP client for Java persona role service
 */
export class PersonaRoleDomainClient {
    private client: AxiosInstance;

    constructor(baseUrl?: string) {
        this.client = axios.create({
            baseURL: baseUrl || process.env.JAVA_API_BASE_URL || 'http://localhost:8080',
            timeout: 5000,
            headers: {
                'Content-Type': 'application/json',
            },
        });
    }

    /**
     * Get all available role definitions
     *
     * @returns List of role definitions
     * @throws Error if request fails
     */
    async getAllRoles(): Promise<RoleDefinition[]> {
        try {
            const response = await this.client.get<RoleDefinition[]>(
                '/api/v1/personas/roles'
            );
            return response.data;
        } catch (error) {
            if (axios.isAxiosError(error)) {
                throw new Error(`Failed to fetch roles: ${error.message}`);
            }
            throw error;
        }
    }

    /**
     * Get specific role definition by ID
     *
     * @param roleId Role identifier (e.g., "admin", "tech-lead")
     * @returns Role definition or null if not found
     * @throws Error if request fails
     */
    async getRoleDefinition(roleId: string): Promise<RoleDefinition | null> {
        try {
            const response = await this.client.get<RoleDefinition>(
                `/api/v1/personas/roles/${roleId}`
            );
            return response.data;
        } catch (error) {
            if (axios.isAxiosError(error)) {
                if (error.response?.status === 404) {
                    return null; // Role not found
                }
                throw new Error(`Failed to fetch role ${roleId}: ${error.message}`);
            }
            throw error;
        }
    }

    /**
     * Validate role activation combination
     *
     * Business rules enforced by Java service:
     * - At least one role must be activated
     * - Maximum 5 roles can be activated
     * - Admin and Viewer roles are incompatible
     * - All roles must exist
     *
     * @param roleIds List of role IDs to validate
     * @returns Validation result with error message if invalid
     * @throws ValidationError if validation fails
     */
    async validateRoleActivation(roleIds: string[]): Promise<ValidationResult> {
        try {
            const response = await this.client.post<ValidationResult>(
                '/api/v1/personas/roles/validate',
                { roleIds }
            );
            return response.data;
        } catch (error) {
            if (axios.isAxiosError(error)) {
                if (error.response?.status === 400) {
                    throw new ValidationError(
                        error.response.data?.error || 'Invalid role combination'
                    );
                }
                throw new Error(`Failed to validate roles: ${error.message}`);
            }
            throw error;
        }
    }

    /**
     * Resolve effective permissions from active roles
     *
     * Combines permissions from all active roles, including inherited
     * permissions from parent roles.
     *
     * Use this for authorization checks:
     * const permissions = await client.resolvePermissions(['tech-lead', 'backend-developer']);
     * if (permissions.permissions['code.approve']) {
     *   // User can approve code
     * }
     *
     * @param roleIds List of active role IDs
     * @returns Effective permissions and capabilities
     * @throws Error if request fails
     */
    async resolveEffectivePermissions(
        roleIds: string[]
    ): Promise<EffectivePermissions> {
        try {
            const response = await this.client.post<EffectivePermissions>(
                '/api/v1/personas/roles/resolve-permissions',
                { roleIds }
            );
            return response.data;
        } catch (error) {
            if (axios.isAxiosError(error)) {
                if (error.response?.status === 400) {
                    throw new ValidationError(
                        error.response.data?.error || 'Invalid role IDs'
                    );
                }
                throw new Error(`Failed to resolve permissions: ${error.message}`);
            }
            throw error;
        }
    }

    /**
     * Check if user has specific permission
     *
     * Convenience method for common authorization checks.
     *
     * @param roleIds User's active role IDs
     * @param permission Permission to check (e.g., "code.approve")
     * @returns true if user has permission
     */
    async hasPermission(
        roleIds: string[],
        permission: string
    ): Promise<boolean> {
        const permissions = await this.resolveEffectivePermissions(roleIds);
        return permissions.permissions[permission] === true;
    }

    /**
     * Check if user has specific capability
     *
     * Convenience method for common authorization checks.
     *
     * @param roleIds User's active role IDs
     * @param capability Capability to check (e.g., "approveCodeReviews")
     * @returns true if user has capability
     */
    async hasCapability(
        roleIds: string[],
        capability: string
    ): Promise<boolean> {
        const permissions = await this.resolveEffectivePermissions(roleIds);
        return permissions.capabilities[capability] === true;
    }
}

// Singleton instance
let _instance: PersonaRoleDomainClient | MockPersonaRoleService | null = null;

/**
 * Get singleton instance of persona role domain client
 *
 * Returns:
 * - MockPersonaRoleService if JAVA_API_MOCK=true (development/testing)
 * - PersonaRoleDomainClient if JAVA_API_MOCK=false (production)
 */
export function getPersonaRoleDomainClient(): PersonaRoleDomainClient | MockPersonaRoleService {
    if (!_instance) {
        const useMock = process.env.JAVA_API_MOCK === 'true';
        _instance = useMock ? new MockPersonaRoleService() : new PersonaRoleDomainClient();
    }
    return _instance;
}
