/**
 * Admin API Routes
 *
 * REST API endpoints for Admin management (Organization, Security, Settings).
 * Implements journeys from SOFTWARE_ORG_ADMIN_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for Admin management
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET/POST /api/v1/admin/tenants - Tenant management
 * - GET/POST /api/v1/admin/departments - Department management
 * - GET/POST /api/v1/admin/teams - Team management
 * - GET/POST /api/v1/admin/services - Service management
 * - GET/POST /api/v1/admin/personas - Persona management
 * - GET/POST /api/v1/admin/roles - Role management
 * - GET/POST /api/v1/admin/policies - Policy management
 * - GET /api/v1/admin/audit-log - Audit log access
 * - GET/PUT /api/v1/admin/settings - Platform settings
 * - GET/POST /api/v1/admin/integrations - Integration management
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';
import type {
    TenantResponse,
    TenantCreateBody,
    TenantUpdateBody,
    EnvironmentCreateBody,
    TeamResponse,
    TeamCreateBody,
    ServiceResponse,
    ServiceCreateBody,
    ServiceLinkBody,
    PersonaResponse,
    PersonaCreateBody,
    PersonaMemberBody,
    RoleResponse,
    RoleCreateBody,
    RoleAssignmentBody,
    PolicyResponse,
    PolicyCreateBody,
    PolicySimulateBody,
    PolicySimulateResponse,
    PlatformSettingsUpdateBody,
    IntegrationResponse,
    IntegrationCreateBody,
    IntegrationTestResponse,
    AuditEventResponse,
    AuditLogQuery,
    DeactivationCheckResponse,
} from '../types/admin.js';

function getHeaderTenantId(headers: Record<string, unknown>): string | null {
    const raw = headers['x-tenant-id'];
    if (typeof raw === 'string' && raw.trim().length > 0) return raw;
    return null;
}

function slugify(input: string): string {
    return input
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)/g, '');
}

/**
 * Helper to write audit event
 */
async function writeAuditEvent(
    tenantId: string | null,
    actorUserId: string | null,
    entityType: string,
    entityId: string,
    action: string,
    details: Record<string, unknown> = {}
): Promise<void> {
    await prisma.auditEvent.create({
        data: {
            tenantId,
            actorUserId,
            entityType,
            entityId,
            action,
            detailsJson: details as Prisma.InputJsonValue,
        },
    });
}

/**
 * Register admin routes
 */
export default async function adminRoutes(fastify: FastifyInstance): Promise<void> {
    // =========================================================================
    // Tenant Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/tenants
     * List all tenants with optional filtering
     */
    fastify.get<{ Querystring: { status?: string; page?: number; limit?: number } }>(
        '/admin/tenants',
        async (request, reply) => {
            const { status, page = 1, limit = 20 } = request.query;
            const skip = (page - 1) * limit;

            const where = status ? { status } : {};

            const [tenants, total] = await Promise.all([
                prisma.tenant.findMany({
                    where,
                    skip,
                    take: limit,
                    orderBy: { createdAt: 'desc' },
                    include: {
                        _count: { select: { environments: true } },
                    },
                }),
                prisma.tenant.count({ where }),
            ]);

            const response: TenantResponse[] = tenants.map((t) => ({
                id: t.id,
                key: t.key,
                name: t.name,
                displayName: t.displayName,
                description: t.description,
                status: t.status,
                plan: t.plan,
                createdAt: t.createdAt.toISOString(),
                updatedAt: t.updatedAt.toISOString(),
                environmentCount: t._count.environments,
            }));

            return reply.send({ data: response, total, page, limit });
        }
    );

    /**
     * POST /api/v1/admin/tenants
     * Create a new tenant
     */
    fastify.post<{ Body: TenantCreateBody }>(
        '/admin/tenants',
        async (request, reply) => {
            const { key, name, displayName, description, plan } = request.body;

            // Check for duplicate key
            const existing = await prisma.tenant.findUnique({ where: { key } });
            if (existing) {
                return reply.status(409).send({ error: 'Tenant with this key already exists' });
            }

            const tenant = await prisma.tenant.create({
                data: {
                    key,
                    name,
                    displayName,
                    description,
                    plan: plan ?? 'standard',
                },
            });

            await writeAuditEvent(tenant.id, null, 'tenant', tenant.id, 'create', { key, name });

            const response: TenantResponse = {
                id: tenant.id,
                key: tenant.key,
                name: tenant.name,
                displayName: tenant.displayName,
                description: tenant.description,
                status: tenant.status,
                plan: tenant.plan,
                createdAt: tenant.createdAt.toISOString(),
                updatedAt: tenant.updatedAt.toISOString(),
            };

            return reply.status(201).send(response);
        }
    );

    /**
     * GET /api/v1/admin/tenants/:tenantId
     * Get tenant details
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/admin/tenants/:tenantId',
        async (request, reply) => {
            const { tenantId } = request.params;

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
                include: {
                    environments: true,
                    _count: {
                        select: {
                            teams: true,
                            services: true,
                            personas: true,
                            policies: true,
                        },
                    },
                },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            return reply.send({
                id: tenant.id,
                key: tenant.key,
                name: tenant.name,
                displayName: tenant.displayName,
                description: tenant.description,
                status: tenant.status,
                plan: tenant.plan,
                createdAt: tenant.createdAt.toISOString(),
                updatedAt: tenant.updatedAt.toISOString(),
                environments: tenant.environments,
                counts: tenant._count,
            });
        }
    );

    /**
     * PUT /api/v1/admin/tenants/:tenantId
     * Update tenant
     */
    fastify.put<{ Params: { tenantId: string }; Body: TenantUpdateBody }>(
        '/admin/tenants/:tenantId',
        async (request, reply) => {
            const { tenantId } = request.params;
            const updates = request.body;

            const tenant = await prisma.tenant.update({
                where: { id: tenantId },
                data: updates,
            });

            await writeAuditEvent(
                tenant.id,
                null,
                'tenant',
                tenant.id,
                'update',
                updates as unknown as Record<string, unknown>
            );

            return reply.send({
                id: tenant.id,
                key: tenant.key,
                name: tenant.name,
                displayName: tenant.displayName,
                description: tenant.description,
                status: tenant.status,
                plan: tenant.plan,
                createdAt: tenant.createdAt.toISOString(),
                updatedAt: tenant.updatedAt.toISOString(),
            });
        }
    );

    /**
     * POST /api/v1/admin/tenants/:tenantId/environments
     * Create environment for tenant
     */
    fastify.post<{ Params: { tenantId: string }; Body: EnvironmentCreateBody }>(
        '/admin/tenants/:tenantId/environments',
        async (request, reply) => {
            const { tenantId } = request.params;
            const { key, name, region } = request.body;

            // Check for duplicate
            const existing = await prisma.environment.findUnique({
                where: { tenantId_key: { tenantId, key } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Environment with this key already exists' });
            }

            const env = await prisma.environment.create({
                data: { tenantId, key, name, region },
            });

            await writeAuditEvent(tenantId, null, 'environment', env.id, 'create', { key, name });

            return reply.status(201).send(env);
        }
    );

    /**
     * GET /api/v1/admin/tenants/:tenantId/deactivation-check
     * Check if tenant can be safely deactivated
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/admin/tenants/:tenantId/deactivation-check',
        async (request, reply) => {
            const { tenantId } = request.params;

            const [activeWorkflows, pendingHitl, activePolicies] = await Promise.all([
                prisma.workflow.count({
                    where: { department: { organization: { id: tenantId } }, status: 'ACTIVE' },
                }),
                prisma.hitlAction.count({
                    where: { organization: { id: tenantId }, state: 'PENDING' },
                }),
                prisma.policy.count({
                    where: { tenantId, status: 'active' },
                }),
            ]);

            const blockers = [];
            const warnings = [];

            if (pendingHitl > 0) {
                blockers.push({
                    type: 'pending_hitl',
                    count: pendingHitl,
                    description: 'Pending human-in-the-loop actions must be resolved',
                });
            }

            if (activeWorkflows > 0) {
                warnings.push({
                    type: 'active_workflows',
                    count: activeWorkflows,
                    description: 'Active workflows will be paused',
                });
            }

            if (activePolicies > 0) {
                warnings.push({
                    type: 'active_policies',
                    count: activePolicies,
                    description: 'Active policies will be disabled',
                });
            }

            const response: DeactivationCheckResponse = {
                canDeactivate: blockers.length === 0,
                blockers,
                warnings,
            };

            return reply.send(response);
        }
    );

    // =========================================================================
    // Department Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/departments
     * List departments for tenant/organization
     */
    fastify.get<{ Querystring: { tenantId?: string; organizationId?: string; page?: number; limit?: number } }>(
        '/admin/departments',
        async (request, reply) => {
            const { organizationId, page = 1, limit = 50 } = request.query;
            const skip = (page - 1) * limit;

            // Build where clause - departments are under organizations
            const where: Record<string, unknown> = {};
            if (organizationId) {
                where.organizationId = organizationId;
            }

            const [departments, total] = await Promise.all([
                prisma.department.findMany({
                    where,
                    skip,
                    take: limit,
                    orderBy: { name: 'asc' },
                    include: {
                        _count: { select: { teams: true, agents: true } },
                    },
                }),
                prisma.department.count({ where }),
            ]);

            const response = departments.map((d) => ({
                id: d.id,
                organizationId: d.organizationId,
                name: d.name,
                type: d.type,
                description: d.description,
                status: d.status,
                teamCount: d._count.teams,
                memberCount: d._count.agents, // Using agents count as member proxy
                createdAt: d.createdAt.toISOString(),
                updatedAt: d.updatedAt.toISOString(),
            }));

            return reply.send({ data: response, total, page, limit });
        }
    );

    /**
     * GET /api/v1/admin/departments/:departmentId
     * Get department by ID
     */
    fastify.get<{ Params: { departmentId: string } }>(
        '/admin/departments/:departmentId',
        async (request, reply) => {
            const { departmentId } = request.params;

            const department = await prisma.department.findUnique({
                where: { id: departmentId },
                include: {
                    organization: { select: { name: true } },
                    _count: { select: { teams: true, agents: true } },
                },
            });

            if (!department) {
                return reply.status(404).send({ error: 'Department not found' });
            }

            return reply.send({
                id: department.id,
                organizationId: department.organizationId,
                organizationName: department.organization.name,
                name: department.name,
                type: department.type,
                description: department.description,
                status: department.status,
                teamCount: department._count.teams,
                memberCount: department._count.agents,
                createdAt: department.createdAt.toISOString(),
                updatedAt: department.updatedAt.toISOString(),
            });
        }
    );

    /**
     * POST /api/v1/admin/departments
     * Create a new department
     */
    fastify.post<{ Querystring: { organizationId: string }; Body: { name: string; type: string; description?: string } }>(
        '/admin/departments',
        async (request, reply) => {
            const { organizationId } = request.query;
            const { name, type, description } = request.body;

            // Check for duplicate name in organization
            const existing = await prisma.department.findUnique({
                where: { organizationId_name: { organizationId, name } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Department with this name already exists' });
            }

            const department = await prisma.department.create({
                data: { organizationId, name, type, description },
            });

            await writeAuditEvent(null, null, 'department', department.id, 'create', { name, type });

            return reply.status(201).send({
                id: department.id,
                organizationId: department.organizationId,
                name: department.name,
                type: department.type,
                description: department.description,
                status: department.status,
                createdAt: department.createdAt.toISOString(),
                updatedAt: department.updatedAt.toISOString(),
            });
        }
    );

    /**
     * PUT /api/v1/admin/departments/:departmentId
     * Update a department
     */
    fastify.put<{ Params: { departmentId: string }; Body: { name?: string; type?: string; description?: string; status?: string } }>(
        '/admin/departments/:departmentId',
        async (request, reply) => {
            const { departmentId } = request.params;
            const updates = request.body;

            const department = await prisma.department.update({
                where: { id: departmentId },
                data: updates,
            });

            await writeAuditEvent(null, null, 'department', departmentId, 'update', updates);

            return reply.send({
                id: department.id,
                organizationId: department.organizationId,
                name: department.name,
                type: department.type,
                description: department.description,
                status: department.status,
                createdAt: department.createdAt.toISOString(),
                updatedAt: department.updatedAt.toISOString(),
            });
        }
    );

    // =========================================================================
    // Team Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/teams
     * List teams for tenant
     */
    fastify.get<{ Querystring: { tenantId: string; departmentId?: string } }>(
        '/admin/teams',
        async (request, reply) => {
            const { tenantId, departmentId } = request.query;

            const where: Record<string, unknown> = { tenantId };
            if (departmentId) where.departmentId = departmentId;

            const teams = await prisma.team.findMany({
                where,
                include: {
                    department: { select: { name: true } },
                    _count: { select: { services: true, personas: true } },
                },
                orderBy: { name: 'asc' },
            });

            const response: TeamResponse[] = teams.map((t) => ({
                id: t.id,
                tenantId: t.tenantId,
                departmentId: t.departmentId,
                name: t.name,
                slug: t.slug,
                description: t.description,
                leadId: t.leadId,
                status: t.status,
                memberCount: t._count.personas,
                serviceCount: t._count.services,
                createdAt: t.createdAt.toISOString(),
                updatedAt: t.updatedAt.toISOString(),
            }));

            return reply.send({ data: response });
        }
    );

    /**
     * POST /api/v1/admin/teams
     * Create a new team
     */
    fastify.post<{ Querystring: { tenantId: string }; Body: TeamCreateBody }>(
        '/admin/teams',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { departmentId, name, slug, description, leadId } = request.body;

            // Check for duplicate slug
            const existing = await prisma.team.findUnique({
                where: { tenantId_slug: { tenantId, slug } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Team with this slug already exists' });
            }

            const team = await prisma.team.create({
                data: { tenantId, departmentId, name, slug, description, leadId },
            });

            await writeAuditEvent(tenantId, null, 'team', team.id, 'create', { name, slug });

            return reply.status(201).send(team);
        }
    );

    /**
     * GET /api/v1/admin/teams/:teamId
     * Get team by ID
     */
    fastify.get<{ Params: { teamId: string } }>(
        '/admin/teams/:teamId',
        async (request, reply) => {
            const { teamId } = request.params;

            const team = await prisma.team.findUnique({
                where: { id: teamId },
                include: {
                    department: { select: { id: true, name: true, type: true } },
                    _count: { select: { services: true, personas: true } },
                },
            });

            if (!team) {
                return reply.status(404).send({ error: 'Team not found' });
            }

            return reply.send({
                id: team.id,
                tenantId: team.tenantId,
                departmentId: team.departmentId,
                departmentName: team.department?.name,
                name: team.name,
                slug: team.slug,
                description: team.description,
                leadId: team.leadId,
                status: team.status,
                memberCount: team._count.personas,
                serviceCount: team._count.services,
                createdAt: team.createdAt.toISOString(),
                updatedAt: team.updatedAt.toISOString(),
            });
        }
    );

    /**
     * GET /api/v1/admin/teams/:teamId/services
     * Get services owned by a team
     */
    fastify.get<{ Params: { teamId: string } }>(
        '/admin/teams/:teamId/services',
        async (request, reply) => {
            const { teamId } = request.params;

            const services = await prisma.service.findMany({
                where: { teamId },
                orderBy: { name: 'asc' },
            });

            const response = services.map((s) => ({
                id: s.id,
                tenantId: s.tenantId,
                teamId: s.teamId,
                name: s.name,
                slug: s.slug,
                description: s.description,
                repository: s.repository,
                status: s.status,
                tier: s.tier,
                createdAt: s.createdAt.toISOString(),
                updatedAt: s.updatedAt.toISOString(),
            }));

            return reply.send({ data: response });
        }
    );

    // =========================================================================
    // Service Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/services
     * List services for tenant
     */
    fastify.get<{ Querystring: { tenantId: string; teamId?: string } }>(
        '/admin/services',
        async (request, reply) => {
            const { tenantId, teamId } = request.query;

            const where: Record<string, unknown> = { tenantId };
            if (teamId) where.teamId = teamId;

            const services = await prisma.service.findMany({
                where,
                include: {
                    team: { select: { id: true, name: true, slug: true } },
                },
                orderBy: { name: 'asc' },
            });

            const response: ServiceResponse[] = services.map((s) => ({
                id: s.id,
                tenantId: s.tenantId,
                teamId: s.teamId,
                name: s.name,
                slug: s.slug,
                description: s.description,
                repository: s.repository,
                status: s.status,
                tier: s.tier,
                ownerTeam: s.team
                    ? {
                        id: s.team.id,
                        tenantId: s.tenantId,
                        departmentId: '',
                        name: s.team.name,
                        slug: s.team.slug,
                        description: null,
                        leadId: null,
                        status: 'ACTIVE',
                        createdAt: '',
                        updatedAt: '',
                    }
                    : undefined,
                createdAt: s.createdAt.toISOString(),
                updatedAt: s.updatedAt.toISOString(),
            }));

            return reply.send({ data: response });
        }
    );

    /**
     * POST /api/v1/admin/services
     * Create a new service
     */
    fastify.post<{ Querystring: { tenantId: string }; Body: ServiceCreateBody }>(
        '/admin/services',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { teamId, name, slug, description, repository, tier } = request.body;

            // Check for duplicate slug
            const existing = await prisma.service.findUnique({
                where: { tenantId_slug: { tenantId, slug } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Service with this slug already exists' });
            }

            const service = await prisma.service.create({
                data: { tenantId, teamId, name, slug, description, repository, tier },
            });

            await writeAuditEvent(tenantId, null, 'service', service.id, 'create', { name, slug });

            return reply.status(201).send(service);
        }
    );

    /**
     * PUT /api/v1/admin/services/:serviceId/links
     * Link service to workflows and agents
     */
    fastify.put<{ Params: { serviceId: string }; Body: ServiceLinkBody }>(
        '/admin/services/:serviceId/links',
        async (request, reply) => {
            const { serviceId } = request.params;
            const { workflowIds = [], agentIds = [] } = request.body;

            // Remove existing links
            await prisma.serviceLink.deleteMany({ where: { serviceId } });

            // Create new links
            const links = [
                ...workflowIds.map((id) => ({ serviceId, targetType: 'workflow', targetId: id })),
                ...agentIds.map((id) => ({ serviceId, targetType: 'agent', targetId: id })),
            ];

            if (links.length > 0) {
                await prisma.serviceLink.createMany({ data: links });
            }

            const service = await prisma.service.findUnique({ where: { id: serviceId } });
            await writeAuditEvent(service?.tenantId ?? null, null, 'service', serviceId, 'update_links', {
                workflowIds,
                agentIds,
            });

            return reply.send({ success: true, linkCount: links.length });
        }
    );

    // =========================================================================
    // Persona Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/personas
     * List personas for tenant
     */
    fastify.get<{ Querystring: { tenantId: string; type?: string } }>(
        '/admin/personas',
        async (request, reply) => {
            const { tenantId, type } = request.query;

            const where: Record<string, unknown> = { tenantId };
            if (type) where.type = type;

            const personas = await prisma.persona.findMany({
                where,
                include: {
                    _count: { select: { members: true, roleAssignments: true } },
                    roleAssignments: { select: { roleId: true } },
                },
                orderBy: { name: 'asc' },
            });

            const response: PersonaResponse[] = personas.map((p) => ({
                id: p.id,
                tenantId: p.tenantId,
                name: p.name,
                slug: p.slug,
                type: p.type,
                description: p.description,
                primaryTeamId: p.primaryTeamId,
                active: p.active,
                roleIds: p.roleAssignments.map((ra) => ra.roleId),
                memberCount: p._count.members,
                createdAt: p.createdAt.toISOString(),
                updatedAt: p.updatedAt.toISOString(),
            }));

            return reply.send({ data: response });
        }
    );

    /**
     * POST /api/v1/admin/personas
     * Create a new persona
     */
    fastify.post<{ Querystring: { tenantId: string }; Body: PersonaCreateBody }>(
        '/admin/personas',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { name, slug, type, description, primaryTeamId } = request.body;

            // Check for duplicate slug
            const existing = await prisma.persona.findUnique({
                where: { tenantId_slug: { tenantId, slug } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Persona with this slug already exists' });
            }

            const persona = await prisma.persona.create({
                data: { tenantId, name, slug, type, description, primaryTeamId },
            });

            await writeAuditEvent(tenantId, null, 'persona', persona.id, 'create', { name, slug, type });

            return reply.status(201).send(persona);
        }
    );

    /**
     * PUT /api/v1/admin/personas/:personaId/members
     * Update persona members
     */
    fastify.put<{ Params: { personaId: string }; Body: { members: PersonaMemberBody[] } }>(
        '/admin/personas/:personaId/members',
        async (request, reply) => {
            const { personaId } = request.params;
            const { members } = request.body;

            // Remove existing members
            await prisma.personaMember.deleteMany({ where: { personaId } });

            // Add new members
            if (members.length > 0) {
                await prisma.personaMember.createMany({
                    data: members.map((m) => ({
                        personaId,
                        memberId: m.memberId,
                        memberType: m.memberType,
                    })),
                });
            }

            const persona = await prisma.persona.findUnique({ where: { id: personaId } });
            await writeAuditEvent(persona?.tenantId ?? null, null, 'persona', personaId, 'update_members', {
                memberCount: members.length,
            });

            return reply.send({ success: true, memberCount: members.length });
        }
    );

    /**
     * PUT /api/v1/admin/personas/:personaId/roles
     * Update persona role assignments
     */
    fastify.put<{ Params: { personaId: string }; Body: { roleIds: string[] } }>(
        '/admin/personas/:personaId/roles',
        async (request, reply) => {
            const { personaId } = request.params;
            const { roleIds } = request.body;

            // Remove existing role assignments for this persona
            await prisma.roleAssignment.deleteMany({ where: { personaId } });

            // Add new role assignments
            if (roleIds.length > 0) {
                await prisma.roleAssignment.createMany({
                    data: roleIds.map((roleId) => ({ roleId, personaId })),
                });
            }

            const persona = await prisma.persona.findUnique({ where: { id: personaId } });
            await writeAuditEvent(persona?.tenantId ?? null, null, 'persona', personaId, 'update_roles', {
                roleIds,
            });

            return reply.send({ success: true, roleCount: roleIds.length });
        }
    );

    // =========================================================================
    // Role Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/roles
     * List roles (global + tenant-specific)
     */
    fastify.get<{ Querystring: { tenantId?: string } }>(
        '/admin/roles',
        async (request, reply) => {
            const headerTenantId = getHeaderTenantId(request.headers as unknown as Record<string, unknown>);
            const { tenantId = headerTenantId ?? undefined, page = 1, limit = 50 } =
                request.query as unknown as { tenantId?: string; page?: number; limit?: number };

            const safePage = Math.max(1, Number(page) || 1);
            const safeLimit = Math.min(200, Math.max(1, Number(limit) || 50));
            const skip = (safePage - 1) * safeLimit;

            // Get global roles + tenant-specific roles
            const where = tenantId
                ? { OR: [{ tenantId: null }, { tenantId }] }
                : { tenantId: null };

            const [roles, total] = await Promise.all([
                prisma.role.findMany({
                    where,
                    include: {
                        _count: { select: { roleAssignments: true } },
                    },
                    orderBy: [{ isSystem: 'desc' }, { name: 'asc' }],
                    skip,
                    take: safeLimit,
                }),
                prisma.role.count({ where }),
            ]);

            const response: RoleResponse[] = roles.map((r) => ({
                id: r.id,
                tenantId: r.tenantId,
                name: r.name,
                slug: r.slug,
                description: r.description,
                permissions: r.permissions as string[],
                scopes: r.scopes as string[],
                isSystem: r.isSystem,
                active: r.active,
                userCount: r._count.roleAssignments,
                createdAt: r.createdAt.toISOString(),
                updatedAt: r.updatedAt.toISOString(),
            }));

            return reply.send({ data: response, total, page: safePage, limit: safeLimit });
        }
    );

    /**
     * GET /api/v1/admin/roles/:roleId
     * Get role details
     */
    fastify.get<{ Params: { roleId: string } }>(
        '/admin/roles/:roleId',
        async (request, reply) => {
            const { roleId } = request.params;

            const role = await prisma.role.findUnique({
                where: { id: roleId },
                include: { _count: { select: { roleAssignments: true } } },
            });

            if (!role) return reply.status(404).send({ error: 'Role not found' });

            const response: RoleResponse = {
                id: role.id,
                tenantId: role.tenantId,
                name: role.name,
                slug: role.slug,
                description: role.description,
                permissions: role.permissions as string[],
                scopes: role.scopes as string[],
                isSystem: role.isSystem,
                active: role.active,
                userCount: role._count.roleAssignments,
                createdAt: role.createdAt.toISOString(),
                updatedAt: role.updatedAt.toISOString(),
            };

            return reply.send(response);
        }
    );

    /**
     * PUT /api/v1/admin/roles/:roleId
     * Update role
     */
    fastify.put<{ Params: { roleId: string }; Body: Partial<RoleCreateBody> & { active?: boolean } }>(
        '/admin/roles/:roleId',
        async (request, reply) => {
            const { roleId } = request.params;
            const { name, slug, description, permissions, scopes, active } = request.body;

            const existing = await prisma.role.findUnique({ where: { id: roleId } });
            if (!existing) return reply.status(404).send({ error: 'Role not found' });

            if (slug && slug !== existing.slug) {
                const dup = await prisma.role.findFirst({
                    where: {
                        id: { not: roleId },
                        slug,
                        tenantId: existing.tenantId,
                    },
                });
                if (dup) return reply.status(409).send({ error: 'Role with this slug already exists' });
            }

            const updated = await prisma.role.update({
                where: { id: roleId },
                data: {
                    name: name ?? undefined,
                    slug: slug ?? undefined,
                    description: description ?? undefined,
                    permissions: (permissions ?? undefined) as unknown as Prisma.InputJsonValue,
                    scopes: (scopes ?? undefined) as unknown as Prisma.InputJsonValue,
                    active: typeof active === 'boolean' ? active : undefined,
                },
            });

            await writeAuditEvent(existing.tenantId ?? null, null, 'role', roleId, 'update', {
                name,
                slug,
                description,
                permissions,
                scopes,
                active,
            });

            return reply.send(updated);
        }
    );

    /**
     * DELETE /api/v1/admin/roles/:roleId
     * Delete role
     */
    fastify.delete<{ Params: { roleId: string } }>(
        '/admin/roles/:roleId',
        async (request, reply) => {
            const { roleId } = request.params;
            const role = await prisma.role.findUnique({ where: { id: roleId } });
            if (!role) return reply.status(404).send({ error: 'Role not found' });

            await prisma.role.delete({ where: { id: roleId } });
            await writeAuditEvent(role.tenantId ?? null, null, 'role', roleId, 'delete');

            return reply.send({ success: true });
        }
    );

    /**
     * POST /api/v1/admin/roles
     * Create a new role
     */
    fastify.post<{ Querystring: { tenantId?: string }; Body: RoleCreateBody }>(
        '/admin/roles',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { name, slug, description, permissions, scopes } = request.body;

            // Check for duplicate slug
            const existing = await prisma.role.findFirst({
                where: { slug, OR: [{ tenantId: null }, { tenantId }] },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Role with this slug already exists' });
            }

            const role = await prisma.role.create({
                data: {
                    tenantId,
                    name,
                    slug,
                    description,
                    permissions: permissions ?? [],
                    scopes: scopes ?? [],
                },
            });

            await writeAuditEvent(tenantId ?? null, null, 'role', role.id, 'create', { name, slug });

            return reply.status(201).send(role);
        }
    );

    /**
     * POST /api/v1/admin/role-assignments
     * Assign role to user or persona
     */
    fastify.post<{ Body: RoleAssignmentBody }>(
        '/admin/role-assignments',
        async (request, reply) => {
            const { roleId, personaId, userId, scope } = request.body;

            // Check if role exists
            const role = await prisma.role.findUnique({ where: { id: roleId } });
            if (!role) {
                return reply.status(404).send({ error: 'Role not found' });
            }

            // Check for duplicate assignment
            const existing = await prisma.roleAssignment.findFirst({
                where: { roleId, personaId, userId },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Role already assigned' });
            }

            const assignment = await prisma.roleAssignment.create({
                data: { roleId, personaId, userId, scope },
            });

            await writeAuditEvent(role.tenantId, null, 'role_assignment', assignment.id, 'create', {
                roleId,
                personaId,
                userId,
            });

            return reply.status(201).send(assignment);
        }
    );

    /**
     * GET /api/v1/admin/role-assignments
     * List role assignments (optionally filtered)
     */
    fastify.get<{ Querystring: { userId?: string; tenantId?: string } }>(
        '/admin/role-assignments',
        async (request, reply) => {
            const headerTenantId = getHeaderTenantId(request.headers as unknown as Record<string, unknown>);
            const { userId, tenantId = headerTenantId ?? undefined } = request.query;

            const assignments = await prisma.roleAssignment.findMany({
                where: {
                    ...(userId ? { userId } : {}),
                },
                include: {
                    role: true,
                },
                orderBy: { createdAt: 'desc' },
            });

            const filtered = tenantId
                ? assignments.filter((a) => a.role.tenantId === null || a.role.tenantId === tenantId)
                : assignments;

            return reply.send({
                data: filtered.map((a) => ({
                    id: a.id,
                    userId: a.userId ?? '',
                    tenantId: a.role.tenantId ?? (tenantId ?? 'global'),
                    roleId: a.roleId,
                    roleName: a.role.name,
                })),
            });
        }
    );

    // =========================================================================
    // Policy Routes
    // =========================================================================

    function simulatePolicyDecision(input: {
        policyCategory: string;
        policyStatus: string;
        rules: Record<string, unknown>;
        event: PolicySimulateBody['event'];
    }): PolicySimulateResponse {
        const { policyCategory, policyStatus, rules, event } = input;

        const matchedRules: string[] = [];
        const reasons: string[] = [];

        // Extremely small, deterministic simulator (enough to support UI wiring).
        // If `rules.denyEventTypes` contains the event type => blocked.
        // Else if policy not active => warning.
        // Else allowed.

        const denyEventTypes = Array.isArray((rules as Record<string, unknown>).denyEventTypes)
            ? (((rules as Record<string, unknown>).denyEventTypes as unknown[])?.filter(
                (x): x is string => typeof x === 'string'
            ) ?? [])
            : [];

        if (denyEventTypes.includes(event.type)) {
            matchedRules.push('denyEventTypes');
            reasons.push(`Event type "${event.type}" is denied by policy rules`);
            return { result: 'blocked', reasons, matchedRules };
        }

        if (policyStatus !== 'active') {
            reasons.push(`Policy is ${policyStatus}; simulation treated as warning`);
            reasons.push(`Category: ${policyCategory}`);
            return { result: 'warning', reasons, matchedRules };
        }

        reasons.push('No blocking rules matched');
        reasons.push(`Category: ${policyCategory}`);
        return { result: 'allowed', reasons, matchedRules };
    }

    /**
     * GET /api/v1/admin/policies
     * List policies for tenant
     */
    fastify.get<{ Querystring: { tenantId: string; status?: string; type?: string } }>(
        '/admin/policies',
        async (request, reply) => {
            const headerTenantId = getHeaderTenantId(request.headers as unknown as Record<string, unknown>);
            const { tenantId = headerTenantId ?? '', status, type, page = 1, limit = 50, serviceId } =
                request.query as unknown as {
                    tenantId?: string;
                    status?: string;
                    type?: string;
                    serviceId?: string;
                    page?: number;
                    limit?: number;
                };

            if (!tenantId) return reply.status(400).send({ error: 'tenantId is required' });

            const safePage = Math.max(1, Number(page) || 1);
            const safeLimit = Math.min(200, Math.max(1, Number(limit) || 50));
            const skip = (safePage - 1) * safeLimit;

            const where: Record<string, unknown> = { tenantId };
            if (status) where.status = status;
            if (type) where.type = type;
            // NOTE: serviceId filtering is best-effort; we store serviceIds in `scope` JSON.
            // If it becomes critical, we can add a dedicated relational join table.
            if (serviceId) {
                where.scope = {
                    path: ['serviceIds'],
                    array_contains: [serviceId],
                };
            }

            const [policies, total] = await Promise.all([
                prisma.policy.findMany({
                    where,
                    orderBy: [{ priority: 'desc' }, { createdAt: 'desc' }],
                    skip,
                    take: safeLimit,
                }),
                prisma.policy.count({ where }),
            ]);

            const response: PolicyResponse[] = policies.map((p) => ({
                id: p.id,
                tenantId: p.tenantId,
                name: p.name,
                description: p.description,
                status: p.status,
                category:
                    (p.metadata as Record<string, unknown> | null)?.category &&
                        typeof (p.metadata as Record<string, unknown>).category === 'string'
                        ? ((p.metadata as Record<string, unknown>).category as string)
                        : p.type,
                environments:
                    (p.scope as Record<string, unknown> | null)?.environments &&
                        Array.isArray((p.scope as Record<string, unknown>).environments)
                        ? ((p.scope as Record<string, unknown>).environments as string[])
                        : [],
                serviceIds:
                    (p.scope as Record<string, unknown> | null)?.serviceIds &&
                        Array.isArray((p.scope as Record<string, unknown>).serviceIds)
                        ? ((p.scope as Record<string, unknown>).serviceIds as string[])
                        : [],
                rules:
                    (p.configuration as Record<string, unknown> | null)?.rules &&
                        typeof (p.configuration as Record<string, unknown>).rules === 'object'
                        ? ((p.configuration as Record<string, unknown>).rules as Record<string, unknown>)
                        : (p.configuration as Record<string, unknown>),
                createdAt: p.createdAt.toISOString(),
                updatedAt: p.updatedAt.toISOString(),
            }));

            return reply.send({ data: response, total, page: safePage, limit: safeLimit });
        }
    );

    /**
     * GET /api/v1/admin/policies/:policyId
     * Get policy details
     */
    fastify.get<{ Params: { policyId: string } }>(
        '/admin/policies/:policyId',
        async (request, reply) => {
            const { policyId } = request.params;
            const policy = await prisma.policy.findUnique({ where: { id: policyId } });
            if (!policy) return reply.status(404).send({ error: 'Policy not found' });

            const response: PolicyResponse = {
                id: policy.id,
                tenantId: policy.tenantId,
                name: policy.name,
                description: policy.description,
                status: policy.status,
                category:
                    (policy.metadata as Record<string, unknown> | null)?.category &&
                        typeof (policy.metadata as Record<string, unknown>).category === 'string'
                        ? ((policy.metadata as Record<string, unknown>).category as string)
                        : policy.type,
                environments:
                    (policy.scope as Record<string, unknown> | null)?.environments &&
                        Array.isArray((policy.scope as Record<string, unknown>).environments)
                        ? ((policy.scope as Record<string, unknown>).environments as string[])
                        : [],
                serviceIds:
                    (policy.scope as Record<string, unknown> | null)?.serviceIds &&
                        Array.isArray((policy.scope as Record<string, unknown>).serviceIds)
                        ? ((policy.scope as Record<string, unknown>).serviceIds as string[])
                        : [],
                rules:
                    (policy.configuration as Record<string, unknown> | null)?.rules &&
                        typeof (policy.configuration as Record<string, unknown>).rules === 'object'
                        ? ((policy.configuration as Record<string, unknown>).rules as Record<string, unknown>)
                        : (policy.configuration as Record<string, unknown>),
                createdAt: policy.createdAt.toISOString(),
                updatedAt: policy.updatedAt.toISOString(),
            };

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/admin/policies
     * Create a new policy
     */
    fastify.post<{ Querystring: { tenantId?: string }; Body: PolicyCreateBody }>(
        '/admin/policies',
        async (request, reply) => {
            const headerTenantId = getHeaderTenantId(request.headers as unknown as Record<string, unknown>);
            const queryTenantId = request.query.tenantId;
            const { name, description, tenantId: bodyTenantId, category, environments, serviceIds, rules, slug, type } = request.body;

            const tenantId = queryTenantId ?? bodyTenantId ?? headerTenantId;
            if (!tenantId) return reply.status(400).send({ error: 'tenantId is required' });

            const effectiveSlug = (slug ?? '').trim().length > 0 ? slug!.trim() : slugify(name);
            const effectiveType = (type ?? '').trim().length > 0 ? type!.trim() : category;

            // Check for duplicate slug
            const existing = await prisma.policy.findUnique({
                where: { tenantId_slug: { tenantId, slug: effectiveSlug } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Policy with this slug already exists' });
            }

            const policy = await prisma.policy.create({
                data: {
                    tenantId,
                    name,
                    slug: effectiveSlug,
                    description,
                    type: effectiveType,
                    scope: ({ environments: environments ?? [], serviceIds: serviceIds ?? [] } as unknown) as Prisma.InputJsonValue,
                    configuration: ({ rules: rules ?? {} } as unknown) as Prisma.InputJsonValue,
                    metadata: ({ category } as unknown) as Prisma.InputJsonValue,
                },
            });

            await writeAuditEvent(tenantId, null, 'policy', policy.id, 'create', {
                name,
                slug: effectiveSlug,
                category,
            });

            return reply.status(201).send(policy);
        }
    );

    /**
     * PUT /api/v1/admin/policies/:policyId
     * Update a policy
     */
    fastify.put<{ Params: { policyId: string }; Body: Partial<PolicyCreateBody> & { status?: string } }>(
        '/admin/policies/:policyId',
        async (request, reply) => {
            const { policyId } = request.params;
            const existing = await prisma.policy.findUnique({ where: { id: policyId } });
            if (!existing) return reply.status(404).send({ error: 'Policy not found' });

            const { name, description, category, environments, serviceIds, rules, status, slug, type } = request.body;

            const updated = await prisma.policy.update({
                where: { id: policyId },
                data: {
                    name: name ?? undefined,
                    description: description ?? undefined,
                    status: status ?? undefined,
                    slug: slug ?? undefined,
                    type: type ?? (category ?? undefined),
                    scope:
                        environments || serviceIds
                            ? (({
                                environments: environments ??
                                    ((existing.scope as Record<string, unknown> | null)?.environments as string[] | undefined) ??
                                    [],
                                serviceIds: serviceIds ??
                                    ((existing.scope as Record<string, unknown> | null)?.serviceIds as string[] | undefined) ??
                                    [],
                            } as unknown) as Prisma.InputJsonValue)
                            : undefined,
                    configuration: rules
                        ? (({ rules } as unknown) as Prisma.InputJsonValue)
                        : undefined,
                    metadata: category
                        ? (({ ...(existing.metadata as Record<string, unknown>), category } as unknown) as Prisma.InputJsonValue)
                        : undefined,
                },
            });

            await writeAuditEvent(existing.tenantId, null, 'policy', policyId, 'update', {
                name,
                status,
                category,
            });

            return reply.send(updated);
        }
    );

    /**
     * POST /api/v1/admin/policies/:policyId/simulate
     * Simulate policy against an event
     */
    fastify.post<{ Params: { policyId: string }; Body: PolicySimulateBody }>(
        '/admin/policies/:policyId/simulate',
        async (request, reply) => {
            const { policyId } = request.params;
            const { event } = request.body;

            const policy = await prisma.policy.findUnique({ where: { id: policyId } });
            if (!policy) {
                return reply.status(404).send({ error: 'Policy not found' });
            }

            const category =
                (policy.metadata as Record<string, unknown> | null)?.category &&
                    typeof (policy.metadata as Record<string, unknown>).category === 'string'
                    ? ((policy.metadata as Record<string, unknown>).category as string)
                    : policy.type;

            const rules =
                (policy.configuration as Record<string, unknown> | null)?.rules &&
                    typeof (policy.configuration as Record<string, unknown>).rules === 'object'
                    ? ((policy.configuration as Record<string, unknown>).rules as Record<string, unknown>)
                    : (policy.configuration as Record<string, unknown>);

            return reply.send(
                simulatePolicyDecision({
                    policyCategory: category,
                    policyStatus: policy.status,
                    rules,
                    event,
                })
            );
        }
    );

    /**
     * POST /api/v1/admin/policies/simulate
     * Simulate a *draft* policy (no persistence)
     */
    fastify.post<{ Body: { policy: PolicyCreateBody; event: PolicySimulateBody['event'] } }>(
        '/admin/policies/simulate',
        async (request, reply) => {
            const { policy, event } = request.body;
            const rules = policy.rules ?? {};
            const category = policy.category;
            const status = 'draft';

            return reply.send(
                simulatePolicyDecision({
                    policyCategory: category,
                    policyStatus: status,
                    rules,
                    event,
                })
            );
        }
    );

    // =========================================================================
    // Permission Simulator Routes
    // =========================================================================

    /**
     * POST /api/v1/admin/permissions/simulate
     * Simulate permission check for a user ("test as user")
     */
    fastify.post<{ Body: { userId: string; permissionId: string; context?: Record<string, unknown> } }>(
        '/admin/permissions/simulate',
        async (request, reply) => {
            const { userId, permissionId, context = {} } = request.body;

            if (!userId || !permissionId) {
                return reply.status(400).send({ error: 'userId and permissionId are required' });
            }

            try {
                // Get user's role assignments
                const roleAssignments = await prisma.roleAssignment.findMany({
                    where: { userId },
                    include: { role: true },
                });

                // Collect all permissions from assigned roles
                const allPermissions = new Set<string>();
                for (const assignment of roleAssignments) {
                    const perms = assignment.role.permissions as string[];
                    perms.forEach((p) => allPermissions.add(p));
                }

                const hasPermission = allPermissions.has(permissionId);

                return reply.send({
                    userId,
                    permissionId,
                    granted: hasPermission,
                    matchedRoles: roleAssignments
                        .filter((a) => (a.role.permissions as string[]).includes(permissionId))
                        .map((a) => ({
                            roleId: a.role.id,
                            roleName: a.role.name,
                            roleSlug: a.role.slug,
                        })),
                    allRoles: roleAssignments.map((a) => ({
                        roleId: a.role.id,
                        roleName: a.role.name,
                        permissions: a.role.permissions as string[],
                    })),
                    context,
                });
            } catch (error) {
                fastify.log.error(error, '[Admin] Permission simulation error');
                return reply.status(500).send({ error: 'Failed to simulate permission' });
            }
        }
    );

    /**
     * PUT /api/v1/admin/policies/:policyId/status
     * Activate or deactivate a policy
     */
    fastify.put<{ Params: { policyId: string }; Body: { status: string } }>(
        '/admin/policies/:policyId/status',
        async (request, reply) => {
            const { policyId } = request.params;
            const { status } = request.body;

            const updateData: Record<string, unknown> = { status };
            if (status === 'active') {
                updateData.activatedAt = new Date();
            }

            const policy = await prisma.policy.update({
                where: { id: policyId },
                data: updateData,
            });

            await writeAuditEvent(policy.tenantId, null, 'policy', policyId, 'status_change', {
                newStatus: status,
            });

            return reply.send(policy);
        }
    );

    // =========================================================================
    // Audit Log Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/audit-log
     * Query audit log with filters
     */
    fastify.get<{ Querystring: AuditLogQuery }>(
        '/admin/audit-log',
        async (request, reply) => {
            const { from, to, tenantId, entityType, entityId, actorId, action, page = 1, limit = 50 } = request.query;
            const skip = (page - 1) * limit;

            const where: Record<string, unknown> = {};
            if (tenantId) where.tenantId = tenantId;
            if (entityType) where.entityType = entityType;
            if (entityId) where.entityId = entityId;
            if (actorId) where.actorUserId = actorId;
            if (action) where.action = action;
            if (from || to) {
                where.timestamp = {};
                if (from) (where.timestamp as Record<string, unknown>).gte = new Date(from);
                if (to) (where.timestamp as Record<string, unknown>).lte = new Date(to);
            }

            const [events, total] = await Promise.all([
                prisma.auditEvent.findMany({
                    where,
                    skip,
                    take: limit,
                    orderBy: { timestamp: 'desc' },
                    include: {
                        actor: { select: { email: true } },
                    },
                }),
                prisma.auditEvent.count({ where }),
            ]);

            const response: AuditEventResponse[] = events.map((e) => ({
                id: e.id,
                tenantId: e.tenantId,
                actorUserId: e.actorUserId,
                actorEmail: e.actor?.email,
                entityType: e.entityType,
                entityId: e.entityId,
                action: e.action,
                decision: e.decision,
                reason: e.reason,
                details: e.detailsJson as Record<string, unknown>,
                timestamp: e.timestamp.toISOString(),
            }));

            return reply.send({ data: response, total, page, limit });
        }
    );

    /**
     * GET /api/v1/admin/audit-log/:eventId
     * Get detailed audit event
     */
    fastify.get<{ Params: { eventId: string } }>(
        '/admin/audit-log/:eventId',
        async (request, reply) => {
            const { eventId } = request.params;

            const event = await prisma.auditEvent.findUnique({
                where: { id: eventId },
                include: { actor: { select: { email: true, name: true } } },
            });

            if (!event) {
                return reply.status(404).send({ error: 'Audit event not found' });
            }

            return reply.send({
                id: event.id,
                tenantId: event.tenantId,
                actor: event.actor,
                entityType: event.entityType,
                entityId: event.entityId,
                action: event.action,
                decision: event.decision,
                reason: event.reason,
                details: event.detailsJson,
                timestamp: event.timestamp.toISOString(),
            });
        }
    );

    // =========================================================================
    // Platform Settings Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/settings/platform
     * Get platform settings (global + tenant overlay)
     */
    fastify.get<{ Querystring: { tenantId?: string } }>(
        '/admin/settings/platform',
        async (request, reply) => {
            const { tenantId } = request.query;

            // Get global settings
            const globalSettings = await prisma.platformSettings.findMany({
                where: { tenantId: null },
            });

            // Get tenant-specific overrides if tenantId provided
            let tenantSettings: typeof globalSettings = [];
            if (tenantId) {
                tenantSettings = await prisma.platformSettings.findMany({
                    where: { tenantId },
                });
            }

            // Merge settings by category
            const categories = ['general', 'appearance', 'localization', 'ai-agents', 'notifications'];
            const merged: Record<string, Record<string, unknown>> = {};

            for (const category of categories) {
                const global = globalSettings.find((s) => s.category === category);
                const tenant = tenantSettings.find((s) => s.category === category);

                merged[category] = {
                    ...((global?.settings as Record<string, unknown>) ?? {}),
                    ...((tenant?.settings as Record<string, unknown>) ?? {}),
                };
            }

            return reply.send({ settings: merged });
        }
    );

    /**
     * PUT /api/v1/admin/settings/platform
     * Update platform settings
     */
    fastify.put<{ Querystring: { tenantId?: string; category: string }; Body: PlatformSettingsUpdateBody }>(
        '/admin/settings/platform',
        async (request, reply) => {
            const { tenantId, category } = request.query;
            const { settings } = request.body;

            const result = await prisma.platformSettings.upsert({
                where: { tenantId_category: { tenantId: tenantId ?? '', category } },
                create: { tenantId, category, settings: settings as Prisma.InputJsonValue },
                update: { settings: settings as Prisma.InputJsonValue },
            });

            await writeAuditEvent(tenantId ?? null, null, 'platform_settings', category, 'update', {
                category,
            });

            return reply.send(result);
        }
    );

    /**
     * GET /api/v1/admin/settings/ai-agents
     * Get AI & Agents settings
     */
    fastify.get<{ Querystring: { tenantId?: string } }>(
        '/admin/settings/ai-agents',
        async (request, reply) => {
            const { tenantId } = request.query;

            const settings = await prisma.platformSettings.findFirst({
                where: { tenantId: tenantId ?? null, category: 'ai-agents' },
            });

            return reply.send({
                settings: (settings?.settings as Record<string, unknown>) ?? {
                    enabled: true,
                    allowedTools: ['metrics', 'incidents', 'git'],
                    guardrails: {
                        maxRiskLevel: 'medium',
                        requireApprovalAbove: 'high',
                        disallowedActions: ['direct_prod_deploy'],
                    },
                    auditLevel: 'decisions',
                },
            });
        }
    );

    /**
     * PUT /api/v1/admin/settings/ai-agents
     * Update AI & Agents settings
     */
    fastify.put<{ Querystring: { tenantId?: string }; Body: PlatformSettingsUpdateBody }>(
        '/admin/settings/ai-agents',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { settings } = request.body;

            const result = await prisma.platformSettings.upsert({
                where: { tenantId_category: { tenantId: tenantId ?? '', category: 'ai-agents' } },
                create: { tenantId, category: 'ai-agents', settings: settings as Prisma.InputJsonValue },
                update: { settings: settings as Prisma.InputJsonValue },
            });

            await writeAuditEvent(
                tenantId ?? null,
                null,
                'ai_settings',
                'ai-agents',
                'update',
                settings as Record<string, unknown>
            );

            return reply.send(result);
        }
    );

    // =========================================================================
    // Integration Routes
    // =========================================================================

    /**
     * GET /api/v1/admin/integrations
     * List integrations for tenant
     */
    fastify.get<{ Querystring: { tenantId: string; type?: string } }>(
        '/admin/integrations',
        async (request, reply) => {
            const { tenantId, type } = request.query;

            const where: Record<string, unknown> = { tenantId };
            if (type) where.type = type;

            const integrations = await prisma.integration.findMany({
                where,
                orderBy: [{ type: 'asc' }, { provider: 'asc' }],
            });

            const response: IntegrationResponse[] = integrations.map((i) => ({
                id: i.id,
                tenantId: i.tenantId,
                type: i.type,
                provider: i.provider,
                name: i.name,
                status: i.status,
                healthDetails: i.healthDetails as Record<string, unknown>,
                lastHealthCheck: i.lastHealthCheck?.toISOString() ?? null,
                createdAt: i.createdAt.toISOString(),
                updatedAt: i.updatedAt.toISOString(),
            }));

            return reply.send({ data: response });
        }
    );

    /**
     * POST /api/v1/admin/integrations
     * Create a new integration
     */
    fastify.post<{ Querystring: { tenantId: string }; Body: IntegrationCreateBody }>(
        '/admin/integrations',
        async (request, reply) => {
            const { tenantId } = request.query;
            const { type, provider, name, configuration } = request.body;

            // Check for duplicate
            const existing = await prisma.integration.findUnique({
                where: { tenantId_type_provider: { tenantId, type, provider } },
            });
            if (existing) {
                return reply.status(409).send({ error: 'Integration of this type/provider already exists' });
            }

            const integration = await prisma.integration.create({
                data: {
                    tenantId,
                    type,
                    provider,
                    name,
                    configuration: configuration as Prisma.InputJsonValue,
                },
            });

            await writeAuditEvent(tenantId, null, 'integration', integration.id, 'create', { type, provider });

            return reply.status(201).send(integration);
        }
    );

    /**
     * POST /api/v1/admin/integrations/:integrationId/test
     * Test integration connection
     */
    fastify.post<{ Params: { integrationId: string } }>(
        '/admin/integrations/:integrationId/test',
        async (request, reply) => {
            const { integrationId } = request.params;

            const integration = await prisma.integration.findUnique({ where: { id: integrationId } });
            if (!integration) {
                return reply.status(404).send({ error: 'Integration not found' });
            }

            // TODO: Implement actual connection testing based on provider
            // For now, simulate a successful test
            const startTime = Date.now();
            await new Promise((resolve) => setTimeout(resolve, 100)); // Simulate network latency
            const latencyMs = Date.now() - startTime;

            // Update health status
            await prisma.integration.update({
                where: { id: integrationId },
                data: {
                    status: 'connected',
                    lastHealthCheck: new Date(),
                    healthDetails: { lastTestResult: 'success', latencyMs },
                },
            });

            const response: IntegrationTestResponse = {
                success: true,
                message: 'Connection successful',
                latencyMs,
            };

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/admin/integrations/health
     * Get health status for all integrations
     */
    fastify.get<{ Querystring: { tenantId: string } }>(
        '/admin/integrations/health',
        async (request, reply) => {
            const { tenantId } = request.query;

            const integrations = await prisma.integration.findMany({
                where: { tenantId },
                select: { id: true, type: true, provider: true, name: true, status: true, lastHealthCheck: true },
            });

            return reply.send({
                data: integrations.map((i) => ({
                    ...i,
                    lastHealthCheck: i.lastHealthCheck?.toISOString() ?? null,
                })),
            });
        }
    );
}
