/**
 * Admin API Types
 *
 * Type definitions for Admin API routes.
 * Follows SOFTWARE_ORG_ADMIN_IMPLEMENTATION_PLAN.md specifications.
 *
 * @doc.type module
 * @doc.purpose Type definitions for Admin API
 * @doc.layer product
 * @doc.pattern Types
 */

// =============================================================================
// Tenant & Environment Types
// =============================================================================

export interface TenantResponse {
    id: string;
    key: string;
    name: string;
    displayName: string | null;
    description: string | null;
    status: string;
    plan: string;
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

export interface TenantUpdateBody {
    name?: string;
    displayName?: string;
    description?: string;
    status?: string;
    plan?: string;
}

export interface EnvironmentResponse {
    id: string;
    tenantId: string;
    key: string;
    name: string;
    region: string | null;
    healthy: boolean;
    lastCheck: string;
    createdAt: string;
}

export interface EnvironmentCreateBody {
    key: string;
    name: string;
    region?: string;
}

// =============================================================================
// Department & Team Types
// =============================================================================

export interface DepartmentResponse {
    id: string;
    organizationId: string;
    name: string;
    type: string;
    description: string | null;
    status: string;
    memberCount?: number;
    teamCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface DepartmentCreateBody {
    name: string;
    type: string;
    description?: string;
}

export interface TeamResponse {
    id: string;
    tenantId: string;
    departmentId: string;
    name: string;
    slug: string;
    description: string | null;
    leadId: string | null;
    status: string;
    memberCount?: number;
    serviceCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface TeamCreateBody {
    departmentId: string;
    name: string;
    slug: string;
    description?: string;
    leadId?: string;
}

// =============================================================================
// Service Types
// =============================================================================

export interface ServiceResponse {
    id: string;
    tenantId: string;
    teamId: string | null;
    name: string;
    slug: string;
    description: string | null;
    repository: string | null;
    status: string;
    tier: string;
    ownerTeam?: TeamResponse;
    createdAt: string;
    updatedAt: string;
}

export interface ServiceCreateBody {
    teamId?: string;
    name: string;
    slug: string;
    description?: string;
    repository?: string;
    tier?: string;
}

export interface ServiceLinkBody {
    workflowIds?: string[];
    agentIds?: string[];
}

// =============================================================================
// Persona & Role Types
// =============================================================================

export interface PersonaResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    type: string;
    description: string | null;
    primaryTeamId: string | null;
    active: boolean;
    roleIds?: string[];
    memberCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface PersonaCreateBody {
    name: string;
    slug: string;
    type?: string;
    description?: string;
    primaryTeamId?: string;
}

export interface PersonaMemberBody {
    memberId: string;
    memberType: string;
}

export interface RoleResponse {
    id: string;
    tenantId: string | null;
    name: string;
    slug: string;
    description: string | null;
    permissions: string[];
    scopes: string[];
    isSystem: boolean;
    active: boolean;
    userCount?: number;
    createdAt: string;
    updatedAt: string;
}

export interface RoleCreateBody {
    name: string;
    slug: string;
    description?: string;
    permissions?: string[];
    scopes?: string[];
}

export interface RoleAssignmentBody {
    roleId: string;
    personaId?: string;
    userId?: string;
    scope?: string;
}

// =============================================================================
// Policy Types
// =============================================================================

export interface PolicyResponse {
    id: string;
    tenantId: string;
    name: string;
    description: string | null;
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
    tenantId?: string;
    category: string;
    environments?: string[];
    serviceIds?: string[];
    rules?: Record<string, unknown>;

    // Back-compat / advanced fields (optional)
    slug?: string;
    type?: string;
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

// =============================================================================
// Permission Simulator Types
// =============================================================================

export interface PermissionSimulateBody {
    userId: string;
    permissionId: string;
    context?: Record<string, unknown>;
}

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
    context?: Record<string, unknown>;
}

// =============================================================================
// Platform Settings Types
// =============================================================================

export interface PlatformSettingsResponse {
    id: string;
    tenantId: string | null;
    category: string;
    settings: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

export interface PlatformSettingsUpdateBody {
    settings: Record<string, unknown>;
}

export interface GeneralSettingsBody {
    displayName?: string;
    defaultTimezone?: string;
    defaultLocale?: string;
    features?: {
        operate?: boolean;
        observe?: boolean;
        build?: boolean;
    };
}

export interface AppearanceSettingsBody {
    defaultTheme?: 'light' | 'dark' | 'system';
    brandColorToken?: string;
    customCss?: string;
}

export interface AiAgentsSettingsBody {
    enabled?: boolean;
    allowedTools?: string[];
    guardrails?: {
        maxRiskLevel?: string;
        requireApprovalAbove?: string;
        disallowedActions?: string[];
    };
    auditLevel?: 'none' | 'decisions' | 'all';
}

// =============================================================================
// Integration Types
// =============================================================================

export interface IntegrationResponse {
    id: string;
    tenantId: string;
    type: string;
    provider: string;
    name: string;
    status: string;
    healthDetails: Record<string, unknown>;
    lastHealthCheck: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface IntegrationCreateBody {
    type: string;
    provider: string;
    name: string;
    configuration: Record<string, unknown>;
}

export interface IntegrationTestResponse {
    success: boolean;
    message: string;
    latencyMs?: number;
}

// =============================================================================
// Audit Log Types
// =============================================================================

export interface AuditEventResponse {
    id: string;
    tenantId: string | null;
    actorUserId: string | null;
    actorEmail?: string;
    entityType: string;
    entityId: string;
    action: string;
    decision: string | null;
    reason: string | null;
    details: Record<string, unknown>;
    timestamp: string;
}

export interface AuditLogQuery {
    from?: string;
    to?: string;
    tenantId?: string;
    environment?: string;
    entityType?: string;
    entityId?: string;
    actorId?: string;
    action?: string;
    page?: number;
    limit?: number;
}

// =============================================================================
// Deactivation Check Types
// =============================================================================

export interface DeactivationCheckResponse {
    canDeactivate: boolean;
    blockers: Array<{
        type: string;
        count: number;
        description: string;
    }>;
    warnings: Array<{
        type: string;
        count: number;
        description: string;
    }>;
}
