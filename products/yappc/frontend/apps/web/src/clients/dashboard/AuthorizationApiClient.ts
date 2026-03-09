/**
 * Authorization API Client
 * 
 * Client for interacting with the Authorization API:
 * - Check permissions
 * - Get user permissions
 * - Get persona mappings
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Authorization API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    CheckPermissionRequest,
    CheckPermissionResponse,
    UserPermissionsResponse,
    PersonaPermissionsResponse,
    Persona,
} from './types';

/**
 * Client for Authorization API operations
 * 
 * @example
 * ```typescript
 * const client = new AuthorizationApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const canEdit = await client.checkPermission({
 *   userId: 'user-1',
 *   resource: 'requirement:req-1',
 *   action: 'edit',
 * });
 * 
 * const permissions = await client.getUserPermissions('user-1');
 * ```
 */
export class AuthorizationApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/auth';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Check if a user has permission to perform an action
     */
    async checkPermission(request: CheckPermissionRequest): Promise<ApiResponse<CheckPermissionResponse>> {
        return this.post<CheckPermissionResponse>(`${this.basePath}/check`, request);
    }

    /**
     * Get all permissions for a user
     */
    async getUserPermissions(userId: string): Promise<ApiResponse<UserPermissionsResponse>> {
        return this.get<UserPermissionsResponse>(`${this.basePath}/users/${userId}/permissions`);
    }

    /**
     * Get permissions for a persona
     */
    async getPersonaPermissions(persona: Persona): Promise<ApiResponse<PersonaPermissionsResponse>> {
        return this.get<PersonaPermissionsResponse>(
            `${this.basePath}/personas/${encodeURIComponent(persona)}/permissions`
        );
    }

    /**
     * Get all persona mappings
     */
    async getAllPersonas(): Promise<ApiResponse<PersonaPermissionsResponse[]>> {
        return this.get<PersonaPermissionsResponse[]>(`${this.basePath}/personas`);
    }

    /**
     * Get current user's permissions (based on auth token)
     */
    async getMyPermissions(): Promise<ApiResponse<UserPermissionsResponse>> {
        return this.get<UserPermissionsResponse>(`${this.basePath}/me/permissions`);
    }

    /**
     * Check if current user can access a resource
     */
    async canAccess(resource: string, action: string): Promise<ApiResponse<boolean>> {
        return this.get<boolean>(`${this.basePath}/can-access`, { resource, action });
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        // Mock for permission check
        this.registerMock<CheckPermissionResponse>(`${this.basePath}/check`, {
            allowed: true,
            reason: 'User has role-based permission',
            requiredPermissions: ['requirements:edit'],
            userPermissions: ['requirements:view', 'requirements:edit', 'requirements:delete'],
            persona: 'Product Owner',
            role: 'PRODUCT_OWNER',
        });

        // Mock for user permissions
        this.registerMock<UserPermissionsResponse>(`${this.basePath}/users/user-1/permissions`, {
            userId: 'user-1',
            persona: 'Product Owner',
            role: 'PRODUCT_OWNER',
            permissions: [
                'requirements:view',
                'requirements:create',
                'requirements:edit',
                'requirements:delete',
                'requirements:approve',
                'backlog:view',
                'backlog:manage',
                'sprint:view',
                'sprint:plan',
                'dashboard:view',
                'analytics:view',
            ],
            effectivePermissions: [
                'requirements:view',
                'requirements:create',
                'requirements:edit',
                'requirements:delete',
                'requirements:approve',
                'backlog:view',
                'backlog:manage',
                'sprint:view',
                'sprint:plan',
                'dashboard:view',
                'analytics:view',
                'audit:view', // From group membership
            ],
            deniedPermissions: [
                'admin:*',
                'system:*',
            ],
            groups: ['product-team', 'project-alpha'],
        });

        // Mock for persona permissions
        const personaPermissions: PersonaPermissionsResponse[] = [
            {
                persona: 'Business Analyst',
                role: 'BUSINESS_ANALYST',
                permissions: ['requirements:view', 'requirements:create', 'requirements:edit', 'analytics:view'],
                description: 'Analyzes business needs and translates to requirements',
            },
            {
                persona: 'Product Owner',
                role: 'PRODUCT_OWNER',
                permissions: [
                    'requirements:*',
                    'backlog:manage',
                    'sprint:plan',
                    'analytics:view',
                    'dashboard:view',
                ],
                description: 'Owns product vision and prioritizes backlog',
            },
            {
                persona: 'Backend Developer',
                role: 'BACKEND_DEVELOPER',
                permissions: [
                    'code:*',
                    'api:*',
                    'database:view',
                    'requirements:view',
                    'testing:execute',
                ],
                description: 'Develops server-side applications and APIs',
            },
            {
                persona: 'Security Engineer',
                role: 'SECURITY_ENGINEER',
                permissions: [
                    'security:*',
                    'audit:view',
                    'compliance:view',
                    'code:review',
                    'vulnerability:*',
                ],
                description: 'Ensures application and infrastructure security',
            },
            {
                persona: 'DevOps Engineer',
                role: 'DEVOPS_ENGINEER',
                permissions: [
                    'deployment:*',
                    'infrastructure:*',
                    'monitoring:*',
                    'ci-cd:*',
                    'secrets:manage',
                ],
                description: 'Manages deployment pipelines and infrastructure',
            },
            {
                persona: 'Architect',
                role: 'ARCHITECT',
                permissions: [
                    'architecture:*',
                    'code:review',
                    'requirements:review',
                    'tech-debt:manage',
                    'patterns:enforce',
                ],
                description: 'Designs system architecture and enforces patterns',
            },
        ];

        this.registerMock<PersonaPermissionsResponse[]>(`${this.basePath}/personas`, personaPermissions);

        // Individual persona mocks
        personaPermissions.forEach((p) => {
            this.registerMock<PersonaPermissionsResponse>(
                `${this.basePath}/personas/${encodeURIComponent(p.persona)}/permissions`,
                p
            );
        });

        // Mock for my permissions
        this.registerMock<UserPermissionsResponse>(`${this.basePath}/me/permissions`, {
            userId: 'current-user',
            persona: 'Full Stack Developer',
            role: 'FULL_STACK_DEVELOPER',
            permissions: [
                'code:*',
                'ui:*',
                'api:*',
                'requirements:view',
                'testing:execute',
                'dashboard:view',
            ],
            effectivePermissions: [
                'code:view',
                'code:create',
                'code:edit',
                'ui:view',
                'ui:create',
                'ui:edit',
                'api:view',
                'api:create',
                'requirements:view',
                'testing:execute',
                'dashboard:view',
            ],
            deniedPermissions: ['admin:*', 'deployment:*'],
            groups: ['developers', 'team-alpha'],
        });
    }
}
