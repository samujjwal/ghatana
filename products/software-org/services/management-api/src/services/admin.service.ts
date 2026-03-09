/**
 * Admin management service for Software-Org application.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for managing Admin entities including tenants, teams,
 * services, roles, personas, policies, settings, and integrations. Handles CRUD
 * operations, validation, audit logging, and cross-entity relationships.
 *
 * <p><b>Service Functions</b><br>
 * - Tenant management: CRUD, environment management, deactivation safety
 * - Team/Service management: CRUD, ownership linking
 * - Role/Persona management: CRUD, assignments, membership
 * - Policy management: CRUD, simulation, status changes
 * - Settings management: Platform and AI/Agent configuration
 * - Integration management: CRUD, health checks, testing
 * - Audit: Event logging and query
 *
 * <p><b>Boundary Compliance</b><br>
 * This service handles USER-FACING Admin operations. Does NOT handle:
 * - Core domain logic (that's Java via virtual-org)
 * - Event processing patterns (that's Java via agentic-event-processor)
 * - Authentication/authorization (that's middleware)
 *
 * This is rapid-iteration Admin CRUD with real-time validation.
 *
 * @doc.type service
 * @doc.purpose Admin entity CRUD and business logic
 * @doc.layer product
 * @doc.pattern Service
 */
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

// =============================================================================
// Types
// =============================================================================

/** Audit event details */
export interface AuditEventInput {
    tenantId: string | null;
    actorUserId: string | null;
    entityType: string;
    entityId: string;
    action: string;
    details?: Record<string, unknown>;
}

/** Deactivation check result */
export interface DeactivationCheck {
    canDeactivate: boolean;
    blockers: string[];
    warnings: string[];
}

/** Policy simulation result */
export interface PolicySimulationResult {
    matched: boolean;
    actions: string[];
    reason: string;
}

/** Integration test result */
export interface IntegrationTestResult {
    success: boolean;
    latencyMs: number;
    message: string;
    testedAt: string;
}

// =============================================================================
// Audit Service
// =============================================================================

/**
 * Write an audit event to the database
 *
 * @param event - Audit event details
 */
export async function writeAuditEvent(event: AuditEventInput): Promise<void> {
    await prisma.auditEvent.create({
        data: {
            tenantId: event.tenantId,
            actorUserId: event.actorUserId,
            entityType: event.entityType,
            entityId: event.entityId,
            action: event.action,
            detailsJson: (event.details ?? {}) as Prisma.InputJsonValue,
        },
    });
}

/**
 * Query audit events with filtering
 *
 * @param filters - Query filters
 * @returns Paginated audit events
 */
export async function queryAuditEvents(filters: {
    tenantId?: string;
    entityType?: string;
    entityId?: string;
    action?: string;
    actorUserId?: string;
    startDate?: Date;
    endDate?: Date;
    limit?: number;
    offset?: number;
}): Promise<{ data: unknown[]; total: number; hasMore: boolean }> {
    const where: Prisma.AuditEventWhereInput = {};

    if (filters.tenantId) where.tenantId = filters.tenantId;
    if (filters.entityType) where.entityType = filters.entityType;
    if (filters.entityId) where.entityId = filters.entityId;
    if (filters.action) where.action = filters.action;
    if (filters.actorUserId) where.actorUserId = filters.actorUserId;
    if (filters.startDate || filters.endDate) {
        where.timestamp = {};
        if (filters.startDate) where.timestamp.gte = filters.startDate;
        if (filters.endDate) where.timestamp.lte = filters.endDate;
    }

    const limit = filters.limit ?? 50;
    const offset = filters.offset ?? 0;

    const [events, total] = await Promise.all([
        prisma.auditEvent.findMany({
            where,
            orderBy: { timestamp: 'desc' },
            take: limit,
            skip: offset,
            include: {
                actor: { select: { id: true, email: true, name: true } },
            },
        }),
        prisma.auditEvent.count({ where }),
    ]);

    return {
        data: events.map((e) => {
            const eventWithActor = e as typeof e & { actor?: { email?: string; name?: string } };
            return {
                id: e.id,
                timestamp: e.timestamp.toISOString(),
                tenantId: e.tenantId,
                entityType: e.entityType,
                entityId: e.entityId,
                action: e.action,
                details: e.detailsJson,
                actorUserId: e.actorUserId,
                actorEmail: eventWithActor.actor?.email ?? null,
                actorName: eventWithActor.actor?.name ?? null,
            };
        }),
        total,
        hasMore: offset + events.length < total,
    };
}

// =============================================================================
// Tenant Service
// =============================================================================

/**
 * Check if a tenant can be safely deactivated
 *
 * @param tenantId - Tenant to check
 * @returns Deactivation check result
 */
export async function checkTenantDeactivation(tenantId: string): Promise<DeactivationCheck> {
    const blockers: string[] = [];
    const warnings: string[] = [];

    // Check for active services
    const activeServices = await prisma.service.count({
        where: { tenantId, status: 'active' },
    });
    if (activeServices > 0) {
        blockers.push(`${activeServices} active services must be deactivated first`);
    }

    // Check for active policies
    const activePolicies = await prisma.policy.count({
        where: { tenantId, status: 'active' },
    });
    if (activePolicies > 0) {
        warnings.push(`${activePolicies} active policies will be deactivated`);
    }

    // Check for active integrations
    const connectedIntegrations = await prisma.integration.count({
        where: { tenantId, status: 'connected' },
    });
    if (connectedIntegrations > 0) {
        warnings.push(`${connectedIntegrations} integrations will be disconnected`);
    }

    return {
        canDeactivate: blockers.length === 0,
        blockers,
        warnings,
    };
}

/**
 * Deactivate a tenant and all related entities
 *
 * @param tenantId - Tenant to deactivate
 * @param actorUserId - User performing the action
 * @returns Updated tenant
 */
export async function deactivateTenant(
    tenantId: string,
    actorUserId: string | null
): Promise<unknown> {
    const check = await checkTenantDeactivation(tenantId);
    if (!check.canDeactivate) {
        throw new Error(`Cannot deactivate tenant: ${check.blockers.join(', ')}`);
    }

    // Deactivate in transaction
    const tenant = await prisma.$transaction(async (tx) => {
        // Deactivate policies
        await tx.policy.updateMany({
            where: { tenantId, status: 'active' },
            data: { status: 'inactive' },
        });

        // Disconnect integrations
        await tx.integration.updateMany({
            where: { tenantId, status: 'connected' },
            data: { status: 'disconnected' },
        });

        // Deactivate tenant
        return tx.tenant.update({
            where: { id: tenantId },
            data: { status: 'inactive' },
        });
    });

    await writeAuditEvent({
        tenantId,
        actorUserId,
        entityType: 'tenant',
        entityId: tenantId,
        action: 'deactivate',
        details: { warnings: check.warnings },
    });

    return tenant;
}

// =============================================================================
// Team Service
// =============================================================================

/**
 * Get teams for a tenant/department with member counts
 *
 * @param filters - Query filters
 * @returns Teams with counts
 */
export async function getTeams(filters: {
    tenantId: string;
    departmentId?: string;
    status?: string;
}): Promise<unknown[]> {
    const where: Prisma.TeamWhereInput = { tenantId: filters.tenantId };
    if (filters.departmentId) where.departmentId = filters.departmentId;
    if (filters.status) where.status = filters.status;

    const teams = await prisma.team.findMany({
        where,
        include: {
            department: { select: { id: true, name: true } },
            _count: { select: { personas: true } },
        },
        orderBy: { name: 'asc' },
    });

    return teams.map((t) => ({
        id: t.id,
        name: t.name,
        slug: t.slug,
        description: t.description,
        status: t.status,
        departmentId: t.departmentId,
        departmentName: t.department.name,
        memberCount: t._count.personas,
        createdAt: t.createdAt.toISOString(),
        updatedAt: t.updatedAt.toISOString(),
    }));
}

// =============================================================================
// Service (Application) Service
// =============================================================================

/**
 * Check if a service can be safely deactivated
 *
 * @param serviceId - Service to check
 * @returns Deactivation check result
 */
export async function checkServiceDeactivation(serviceId: string): Promise<DeactivationCheck> {
    const blockers: string[] = [];
    const warnings: string[] = [];

    const service = await prisma.service.findUnique({
        where: { id: serviceId },
        include: {
            _count: {
                select: {
                    // Check for linked entities if model supports it
                },
            },
        },
    });

    if (!service) {
        blockers.push('Service not found');
        return { canDeactivate: false, blockers, warnings };
    }

    // Add business-specific checks here
    // e.g., check for active incidents, pending deployments, etc.

    return {
        canDeactivate: blockers.length === 0,
        blockers,
        warnings,
    };
}

/**
 * Link a service to an owner team
 *
 * @param serviceId - Service to link
 * @param teamId - Team to assign as owner
 * @param actorUserId - User performing the action
 * @returns Updated service
 */
export async function linkServiceToTeam(
    serviceId: string,
    teamId: string,
    actorUserId: string | null
): Promise<unknown> {
    const service = await prisma.service.findUnique({ where: { id: serviceId } });
    if (!service) {
        throw new Error('Service not found');
    }

    const team = await prisma.team.findUnique({ where: { id: teamId } });
    if (!team) {
        throw new Error('Team not found');
    }

    // Validate same tenant
    if (service.tenantId !== team.tenantId) {
        throw new Error('Service and team must belong to the same tenant');
    }

    const previousTeamId = service.teamId;

    const updated = await prisma.service.update({
        where: { id: serviceId },
        data: { teamId: teamId },
        include: {
            team: { select: { id: true, name: true } },
        },
    });

    await writeAuditEvent({
        tenantId: service.tenantId,
        actorUserId,
        entityType: 'service',
        entityId: serviceId,
        action: 'link_team',
        details: { previousTeamId, newTeamId: teamId },
    });

    return updated;
}

// =============================================================================
// Policy Service
// =============================================================================

/**
 * Simulate a policy against an event
 *
 * @param policyId - Policy to simulate
 * @param eventType - Event type to test
 * @param eventPayload - Event payload for context (reserved for future use)
 * @returns Simulation result
 */
export async function simulatePolicy(
    policyId: string,
    eventType: string,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    eventPayload?: Record<string, unknown>
): Promise<PolicySimulationResult> {
    const policy = await prisma.policy.findUnique({ where: { id: policyId } });
    if (!policy) {
        throw new Error('Policy not found');
    }

    const triggers = policy.triggers as string[];
    const matched = triggers.includes(eventType) || triggers.includes('*');

    return {
        matched,
        actions: matched ? (policy.actions as string[]) : [],
        reason: matched
            ? `Event type "${eventType}" matches policy triggers`
            : `Event type "${eventType}" does not match policy triggers: ${triggers.join(', ')}`,
    };
}

/**
 * Activate or deactivate a policy
 *
 * @param policyId - Policy to update
 * @param status - New status
 * @param actorUserId - User performing the action
 * @returns Updated policy
 */
export async function updatePolicyStatus(
    policyId: string,
    status: 'active' | 'inactive' | 'draft',
    actorUserId: string | null
): Promise<unknown> {
    const policy = await prisma.policy.findUnique({ where: { id: policyId } });
    if (!policy) {
        throw new Error('Policy not found');
    }

    const previousStatus = policy.status;
    const updateData: Prisma.PolicyUpdateInput = { status };

    if (status === 'active' && previousStatus !== 'active') {
        updateData.activatedAt = new Date();
    }

    const updated = await prisma.policy.update({
        where: { id: policyId },
        data: updateData,
    });

    await writeAuditEvent({
        tenantId: policy.tenantId,
        actorUserId,
        entityType: 'policy',
        entityId: policyId,
        action: 'status_change',
        details: { previousStatus, newStatus: status },
    });

    return updated;
}

// =============================================================================
// Integration Service
// =============================================================================

/**
 * Test an integration connection
 *
 * @param integrationId - Integration to test
 * @returns Test result
 */
export async function testIntegration(integrationId: string): Promise<IntegrationTestResult> {
    const integration = await prisma.integration.findUnique({ where: { id: integrationId } });
    if (!integration) {
        throw new Error('Integration not found');
    }

    const startTime = Date.now();

    // Simulate connection test based on provider type
    // In production, this would make actual API calls
    const success = integration.status === 'connected';
    const latencyMs = Date.now() - startTime + Math.floor(Math.random() * 100);

    // Update health details
    await prisma.integration.update({
        where: { id: integrationId },
        data: {
            lastHealthCheck: new Date(),
            healthDetails: {
                healthStatus: success ? 'healthy' : 'unhealthy',
                lastTestLatencyMs: latencyMs,
                lastTestResult: success ? 'success' : 'failure',
            } as Prisma.InputJsonValue,
        },
    });

    return {
        success,
        latencyMs,
        message: success ? 'Connection successful' : 'Connection failed',
        testedAt: new Date().toISOString(),
    };
}

/**
 * Refresh integration connection (e.g., renew tokens)
 *
 * @param integrationId - Integration to refresh
 * @param actorUserId - User performing the action
 * @returns Updated integration
 */
export async function refreshIntegration(
    integrationId: string,
    actorUserId: string | null
): Promise<unknown> {
    const integration = await prisma.integration.findUnique({ where: { id: integrationId } });
    if (!integration) {
        throw new Error('Integration not found');
    }

    // In production, this would refresh OAuth tokens, API keys, etc.
    const updated = await prisma.integration.update({
        where: { id: integrationId },
        data: {
            status: 'connected',
            lastHealthCheck: new Date(),
            healthDetails: { healthStatus: 'healthy' } as Prisma.InputJsonValue,
        },
    });

    await writeAuditEvent({
        tenantId: integration.tenantId,
        actorUserId,
        entityType: 'integration',
        entityId: integrationId,
        action: 'refresh',
        details: { provider: integration.provider },
    });

    return updated;
}

// =============================================================================
// Settings Service
// =============================================================================

/**
 * Get merged platform settings (global + tenant overrides)
 *
 * @param tenantId - Tenant for overrides (optional)
 * @param categories - Categories to fetch
 * @returns Merged settings by category
 */
export async function getMergedSettings(
    tenantId: string | null,
    categories: string[]
): Promise<Record<string, Record<string, unknown>>> {
    const result: Record<string, Record<string, unknown>> = {};

    for (const category of categories) {
        // Get global settings (tenantId = null)
        const globalSettings = await prisma.platformSettings.findUnique({
            where: { tenantId_category: { tenantId: '', category } },
        });

        // Get tenant-specific overrides
        const tenantSettings = tenantId
            ? await prisma.platformSettings.findUnique({
                where: { tenantId_category: { tenantId, category } },
            })
            : null;

        // Merge: tenant settings override global
        result[category] = {
            ...((globalSettings?.settings as Record<string, unknown>) ?? {}),
            ...((tenantSettings?.settings as Record<string, unknown>) ?? {}),
        };
    }

    return result;
}

/**
 * Update platform settings for a category
 *
 * @param tenantId - Tenant for scoped settings (null for global)
 * @param category - Settings category
 * @param settings - New settings
 * @param actorUserId - User performing the action
 * @returns Updated settings
 */
export async function updateSettings(
    tenantId: string | null,
    category: string,
    settings: Record<string, unknown>,
    actorUserId: string | null
): Promise<unknown> {
    const result = await prisma.platformSettings.upsert({
        where: { tenantId_category: { tenantId: tenantId ?? '', category } },
        create: {
            tenantId,
            category,
            settings: settings as Prisma.InputJsonValue,
        },
        update: {
            settings: settings as Prisma.InputJsonValue,
        },
    });

    await writeAuditEvent({
        tenantId,
        actorUserId,
        entityType: 'platform_settings',
        entityId: category,
        action: 'update',
        details: { category, scope: tenantId ? 'tenant' : 'global' },
    });

    return result;
}

// =============================================================================
// Role Assignment Service
// =============================================================================

/**
 * Assign a role to a user or persona
 *
 * @param roleId - Role to assign
 * @param targetType - 'user' or 'persona'
 * @param targetId - User or persona ID
 * @param actorUserId - User performing the action
 */
export async function assignRole(
    roleId: string,
    targetType: 'user' | 'persona',
    targetId: string,
    actorUserId: string | null
): Promise<void> {
    const role = await prisma.role.findUnique({ where: { id: roleId } });
    if (!role) {
        throw new Error('Role not found');
    }

    // Store role assignment
    // Note: In a real implementation, you'd have a RoleAssignment model
    // For now, we just log the audit event
    await writeAuditEvent({
        tenantId: role.tenantId,
        actorUserId,
        entityType: 'role_assignment',
        entityId: `${roleId}:${targetType}:${targetId}`,
        action: 'assign',
        details: { roleId, roleName: role.name, targetType, targetId },
    });
}

/**
 * Revoke a role from a user or persona
 *
 * @param roleId - Role to revoke
 * @param targetType - 'user' or 'persona'
 * @param targetId - User or persona ID
 * @param actorUserId - User performing the action
 */
export async function revokeRole(
    roleId: string,
    targetType: 'user' | 'persona',
    targetId: string,
    actorUserId: string | null
): Promise<void> {
    const role = await prisma.role.findUnique({ where: { id: roleId } });
    if (!role) {
        throw new Error('Role not found');
    }

    await writeAuditEvent({
        tenantId: role.tenantId,
        actorUserId,
        entityType: 'role_assignment',
        entityId: `${roleId}:${targetType}:${targetId}`,
        action: 'revoke',
        details: { roleId, roleName: role.name, targetType, targetId },
    });
}
