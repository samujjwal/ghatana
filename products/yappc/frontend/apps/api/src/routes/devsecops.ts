/**
 * DevSecOps API Routes
 * 
 * REST API for DevSecOps dashboard - phases, items, workflows, insights.
 * All data is served from PostgreSQL via Prisma.
 * 
 * @doc.type router
 * @doc.purpose DevSecOps CRUD operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';

// ============================================================================
// Types
// ============================================================================

interface ItemFilter {
    phaseId?: string;
    status?: string[];
    priority?: string[];
    tags?: string[];
    search?: string;
}

interface CreateItemBody {
    title: string;
    description?: string;
    type: string;
    priority?: string;
    status?: string;
    phaseId: string;
    owners?: string[];
    tags?: string[];
    estimatedHours?: number;
    dueDate?: string;
}

interface UpdateItemBody {
    title?: string;
    description?: string;
    type?: string;
    priority?: string;
    status?: string;
    phaseId?: string;
    progress?: number;
    estimatedHours?: number;
    actualHours?: number;
    dueDate?: string;
}

interface ItemParams {
    id: string;
}

interface BulkUpdateBody {
    itemIds: string[];
    updates: {
        status?: string;
        priority?: string;
        phaseId?: string;
    };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Transform database item to API response format
 */
function transformItem(item: unknown) {
    return {
        id: item.id,
        title: item.title,
        description: item.description,
        type: item.type.toLowerCase().replace('_', '-'),
        priority: item.priority?.toLowerCase() ?? 'medium',
        status: item.status?.toLowerCase().replace('_', '-') ?? 'not-started',
        phaseId: item.phase?.key ?? item.phaseId,
        progress: item.progress ?? 0,
        estimatedHours: item.estimatedHours,
        actualHours: item.actualHours,
        dueDate: item.dueDate?.toISOString(),
        completedAt: item.completedAt?.toISOString(),
        createdAt: item.createdAt?.toISOString(),
        updatedAt: item.updatedAt?.toISOString(),
        owners: item.owners?.map((o: unknown) => ({
            id: o.user?.id ?? o.userId,
            name: o.user?.name ?? 'Unknown',
            email: o.user?.email,
            role: o.role ?? 'Owner',
        })) ?? [],
        tags: item.tags?.map((t: unknown) => t.tag) ?? [],
        artifacts: item.artifacts?.map((a: unknown) => ({
            id: a.id,
            name: a.name,
            type: a.type,
            url: a.url,
        })) ?? [],
        integrations: item.integrations?.map((i: unknown) => ({
            id: i.id,
            type: i.type,
            externalId: i.externalId,
            url: i.url,
        })) ?? [],
        metadata: {
            aiPriorityScore: item.aiPriorityScore,
            riskScore: item.riskScore,
            predictedDueDate: item.predictedDueDate?.toISOString(),
            sentimentScore: item.sentimentScore,
        },
    };
}

/**
 * Transform database phase to API response format
 */
function transformPhase(phase: unknown, itemCount: number = 0, completedCount: number = 0) {
    return {
        id: phase.key,
        title: phase.title,
        description: phase.description,
        icon: phase.icon ?? getPhaseIcon(phase.key),
        status: phase.status?.toLowerCase() ?? 'active',
        order: phase.order,
        color: phase.color,
        completed: phase.status === 'COMPLETED',
        itemCount,
        completedItemCount: completedCount,
        progress: itemCount > 0 ? Math.round((completedCount / itemCount) * 100) : 0,
        healthScore: phase.healthScore,
        riskScore: phase.riskScore,
    };
}

/**
 * Get default icon for phase based on key
 */
function getPhaseIcon(key: string): string {
    const icons: Record<string, string> = {
        'planning': '📋',
        'development': '💻',
        'security': '🔒',
        'operations': '⚙️',
        'ideation': '💡',
        'testing': '🧪',
        'deployment': '🚀',
    };
    return icons[key.toLowerCase()] ?? '📌';
}

/**
 * Map status string to database enum
 */
function mapStatusToEnum(status: string): string {
    const statusMap: Record<string, string> = {
        'not-started': 'NOT_STARTED',
        'in-progress': 'IN_PROGRESS',
        'blocked': 'BLOCKED',
        'in-review': 'IN_REVIEW',
        'completed': 'COMPLETED',
        'archived': 'ARCHIVED',
    };
    return statusMap[status.toLowerCase()] ?? 'NOT_STARTED';
}

/**
 * Map priority string to database enum
 */
function mapPriorityToEnum(priority: string): string {
    const priorityMap: Record<string, string> = {
        'critical': 'CRITICAL',
        'high': 'HIGH',
        'medium': 'MEDIUM',
        'low': 'LOW',
    };
    return priorityMap[priority.toLowerCase()] ?? 'MEDIUM';
}

/**
 * Map type string to database enum
 */
function mapTypeToEnum(type: string): string {
    const typeMap: Record<string, string> = {
        'feature': 'FEATURE',
        'story': 'STORY',
        'task': 'TASK',
        'bug': 'BUG',
        'epic': 'EPIC',
        'spike': 'SPIKE',
        'security-issue': 'SECURITY_ISSUE',
        'tech-debt': 'TECH_DEBT',
    };
    return typeMap[type.toLowerCase()] ?? 'TASK';
}

// ============================================================================
// Routes
// ============================================================================

export default async function devsecopsRoutes(fastify: FastifyInstance) {
    /**
     * GET /api/devsecops/overview
     * Get complete DevSecOps overview with phases, items, KPIs, and activity
     */
    fastify.get('/devsecops/overview', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/overview');

        // Get all phases with item counts
        const phases = await prisma.phase.findMany({
            orderBy: { order: 'asc' },
            include: {
                _count: {
                    select: { items: true },
                },
            },
        });

        // Get all items with relations
        const items = await prisma.item.findMany({
            include: {
                phase: { select: { key: true } },
                owners: {
                    include: { user: { select: { id: true, name: true, email: true } } },
                },
                tags: { select: { tag: true } },
                artifacts: { select: { id: true, name: true, type: true, url: true } },
                integrations: { select: { id: true, type: true, externalId: true, url: true } },
            },
        });

        // Get completed item counts per phase
        const phasesWithStats = await Promise.all(
            phases.map(async (phase) => {
                const completedCount = await prisma.item.count({
                    where: {
                        phaseId: phase.id,
                        status: 'COMPLETED',
                    },
                });
                return transformPhase(phase, phase._count.items, completedCount);
            })
        );

        // Get recent activity
        const recentActivity = await prisma.activityLog.findMany({
            take: 20,
            orderBy: { createdAt: 'desc' },
            include: {
                user: { select: { id: true, name: true } },
            },
        });

        // Get AI insights
        const aiInsights = await prisma.aIInsight.findMany({
            take: 10,
            orderBy: { createdAt: 'desc' },
            where: { status: 'ACTIVE' },
        });

        // Calculate KPIs
        const totalItems = items.length;
        const completedItems = items.filter(i => i.status === 'COMPLETED').length;
        const inProgressItems = items.filter(i => i.status === 'IN_PROGRESS').length;
        const blockedItems = items.filter(i => i.status === 'BLOCKED').length;

        const kpis = {
            totalItems,
            completedItems,
            inProgressItems,
            blockedItems,
            completionRate: totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0,
            velocity: inProgressItems + completedItems, // Simplified velocity
        };

        // Build persona dashboards (simplified)
        const personaDashboards = {
            developer: {
                focus: items.filter(i => 
                    i.status === 'IN_PROGRESS' && 
                    ['TASK', 'FEATURE', 'BUG'].includes(i.type)
                ).slice(0, 5).map(transformItem),
                metrics: {
                    tasksCompleted: items.filter(i => i.status === 'COMPLETED' && i.type === 'TASK').length,
                    bugsFixed: items.filter(i => i.status === 'COMPLETED' && i.type === 'BUG').length,
                },
            },
            security: {
                focus: items.filter(i => 
                    i.type === 'SECURITY_ISSUE' || 
                    i.tags?.some((t: unknown) => t.tag.toLowerCase().includes('security'))
                ).slice(0, 5).map(transformItem),
                metrics: {
                    openVulnerabilities: items.filter(i => i.type === 'SECURITY_ISSUE' && i.status !== 'COMPLETED').length,
                    securityTasksCompleted: items.filter(i => i.type === 'SECURITY_ISSUE' && i.status === 'COMPLETED').length,
                },
            },
            manager: {
                focus: items.filter(i => 
                    i.priority === 'HIGH' || i.priority === 'CRITICAL'
                ).slice(0, 5).map(transformItem),
                metrics: {
                    overallProgress: kpis.completionRate,
                    blockedItems: blockedItems,
                },
            },
        };

        const response = {
            data: {
                phases: phasesWithStats,
                items: items.map(transformItem),
                kpis,
                recentActivity: recentActivity.map(a => ({
                    id: a.id,
                    type: a.action,
                    description: a.description ?? `${a.action} performed`,
                    timestamp: a.createdAt.toISOString(),
                    user: a.user ? { id: a.user.id, name: a.user.name } : null,
                })),
                aiInsights: aiInsights.map(i => ({
                    id: i.id,
                    type: i.type,
                    severity: i.severity,
                    title: i.title,
                    description: i.description,
                    actionable: i.actionable,
                    createdAt: i.createdAt.toISOString(),
                })),
                personaDashboards,
            },
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        };

        return reply.send(response);
    });

    /**
     * GET /api/devsecops/phases
     * List all phases
     */
    fastify.get('/devsecops/phases', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/phases');

        const phases = await prisma.phase.findMany({
            orderBy: { order: 'asc' },
            include: {
                _count: { select: { items: true } },
            },
        });

        const phasesWithStats = await Promise.all(
            phases.map(async (phase) => {
                const completedCount = await prisma.item.count({
                    where: { phaseId: phase.id, status: 'COMPLETED' },
                });
                return transformPhase(phase, phase._count.items, completedCount);
            })
        );

        return reply.send({
            data: phasesWithStats,
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/items
     * List items with optional filters
     */
    fastify.get('/devsecops/items', async (request: FastifyRequest<{ Querystring: ItemFilter }>, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/items');

        const { phaseId, status, priority, tags, search } = request.query;

        const where: Record<string, unknown> = {};

        if (phaseId) {
            // Find phase by key
            const phase = await prisma.phase.findUnique({ where: { key: phaseId } });
            if (phase) {
                where.phaseId = phase.id;
            }
        }

        if (status && status.length > 0) {
            const statusArray = Array.isArray(status) ? status : [status];
            where.status = { in: statusArray.map(mapStatusToEnum) };
        }

        if (priority && priority.length > 0) {
            const priorityArray = Array.isArray(priority) ? priority : [priority];
            where.priority = { in: priorityArray.map(mapPriorityToEnum) };
        }

        if (tags && tags.length > 0) {
            const tagsArray = Array.isArray(tags) ? tags : [tags];
            where.tags = {
                some: { tag: { in: tagsArray } },
            };
        }

        if (search) {
            where.OR = [
                { title: { contains: search, mode: 'insensitive' } },
                { description: { contains: search, mode: 'insensitive' } },
            ];
        }

        const items = await prisma.item.findMany({
            where,
            include: {
                phase: { select: { key: true } },
                owners: {
                    include: { user: { select: { id: true, name: true, email: true } } },
                },
                tags: { select: { tag: true } },
                artifacts: { select: { id: true, name: true, type: true, url: true } },
                integrations: { select: { id: true, type: true, externalId: true, url: true } },
            },
            orderBy: [
                { priority: 'asc' },
                { createdAt: 'desc' },
            ],
        });

        return reply.send({
            data: items.map(transformItem),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/items/:id
     * Get single item by ID
     */
    fastify.get('/devsecops/items/:id', async (request: FastifyRequest<{ Params: ItemParams }>, reply: FastifyReply) => {
        const { id } = request.params;
        console.log('[DEVSECOPS] GET /devsecops/items/', id);

        const item = await prisma.item.findUnique({
            where: { id },
            include: {
                phase: { select: { key: true } },
                owners: {
                    include: { user: { select: { id: true, name: true, email: true } } },
                },
                tags: { select: { tag: true } },
                artifacts: { select: { id: true, name: true, type: true, url: true } },
                integrations: { select: { id: true, type: true, externalId: true, url: true } },
                comments: {
                    include: { author: { select: { id: true, name: true } } },
                    orderBy: { createdAt: 'desc' },
                },
                aiInsights: { where: { status: 'ACTIVE' } },
            },
        });

        if (!item) {
            return reply.status(404).send({
                success: false,
                error: 'Item not found',
            });
        }

        return reply.send({
            data: {
                ...transformItem(item),
                comments: item.comments.map((c: unknown) => ({
                    id: c.id,
                    content: c.content,
                    author: c.author,
                    createdAt: c.createdAt.toISOString(),
                })),
                aiInsights: item.aiInsights.map((i: unknown) => ({
                    id: i.id,
                    type: i.type,
                    title: i.title,
                    description: i.description,
                })),
            },
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * POST /api/devsecops/items
     * Create new item
     */
    fastify.post('/devsecops/items', async (request: FastifyRequest<{ Body: CreateItemBody }>, reply: FastifyReply) => {
        const body = request.body;
        console.log('[DEVSECOPS] POST /devsecops/items', body);

        // Find phase by key
        const phase = await prisma.phase.findUnique({ where: { key: body.phaseId } });
        if (!phase) {
            return reply.status(400).send({
                success: false,
                error: 'Phase not found',
            });
        }

        const item = await prisma.item.create({
            data: {
                title: body.title,
                description: body.description,
                type: mapTypeToEnum(body.type) as unknown,
                priority: body.priority ? mapPriorityToEnum(body.priority) as unknown : 'MEDIUM',
                status: body.status ? mapStatusToEnum(body.status) as unknown : 'NOT_STARTED',
                phaseId: phase.id,
                estimatedHours: body.estimatedHours,
                dueDate: body.dueDate ? new Date(body.dueDate) : undefined,
                tags: body.tags ? {
                    create: body.tags.map(tag => ({ tag })),
                } : undefined,
            },
            include: {
                phase: { select: { key: true } },
                tags: { select: { tag: true } },
            },
        });

        return reply.status(201).send({
            data: transformItem(item),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * PATCH /api/devsecops/items/:id
     * Update item
     */
    fastify.patch('/devsecops/items/:id', async (request: FastifyRequest<{ Params: ItemParams; Body: UpdateItemBody }>, reply: FastifyReply) => {
        const { id } = request.params;
        const body = request.body;
        console.log('[DEVSECOPS] PATCH /devsecops/items/', id, body);

        const updateData: Record<string, unknown> = {};

        if (body.title !== undefined) updateData.title = body.title;
        if (body.description !== undefined) updateData.description = body.description;
        if (body.type !== undefined) updateData.type = mapTypeToEnum(body.type);
        if (body.priority !== undefined) updateData.priority = mapPriorityToEnum(body.priority);
        if (body.status !== undefined) {
            updateData.status = mapStatusToEnum(body.status);
            if (body.status === 'completed') {
                updateData.completedAt = new Date();
            }
        }
        if (body.progress !== undefined) updateData.progress = body.progress;
        if (body.estimatedHours !== undefined) updateData.estimatedHours = body.estimatedHours;
        if (body.actualHours !== undefined) updateData.actualHours = body.actualHours;
        if (body.dueDate !== undefined) updateData.dueDate = body.dueDate ? new Date(body.dueDate) : null;

        if (body.phaseId !== undefined) {
            const phase = await prisma.phase.findUnique({ where: { key: body.phaseId } });
            if (phase) {
                updateData.phaseId = phase.id;
            }
        }

        const item = await prisma.item.update({
            where: { id },
            data: updateData,
            include: {
                phase: { select: { key: true } },
                owners: {
                    include: { user: { select: { id: true, name: true, email: true } } },
                },
                tags: { select: { tag: true } },
            },
        });

        return reply.send({
            data: transformItem(item),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * DELETE /api/devsecops/items/:id
     * Delete item
     */
    fastify.delete('/devsecops/items/:id', async (request: FastifyRequest<{ Params: ItemParams }>, reply: FastifyReply) => {
        const { id } = request.params;
        console.log('[DEVSECOPS] DELETE /devsecops/items/', id);

        await prisma.item.delete({ where: { id } });

        return reply.send({
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * POST /api/devsecops/items/bulk-update
     * Bulk update multiple items
     */
    fastify.post('/devsecops/items/bulk-update', async (request: FastifyRequest<{ Body: BulkUpdateBody }>, reply: FastifyReply) => {
        const { itemIds, updates } = request.body;
        console.log('[DEVSECOPS] POST /devsecops/items/bulk-update', { itemIds, updates });

        const updateData: Record<string, unknown> = {};

        if (updates.status !== undefined) {
            updateData.status = mapStatusToEnum(updates.status);
            if (updates.status === 'completed') {
                updateData.completedAt = new Date();
            }
        }
        if (updates.priority !== undefined) {
            updateData.priority = mapPriorityToEnum(updates.priority);
        }
        if (updates.phaseId !== undefined) {
            const phase = await prisma.phase.findUnique({ where: { key: updates.phaseId } });
            if (phase) {
                updateData.phaseId = phase.id;
            }
        }

        const result = await prisma.item.updateMany({
            where: { id: { in: itemIds } },
            data: updateData,
        });

        return reply.send({
            data: {
                success: true,
                updated: result.count,
                failed: itemIds.length - result.count,
            },
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/workflows
     * List workflows
     */
    fastify.get('/devsecops/workflows', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/workflows');

        const workflows = await prisma.workflow.findMany({
            include: {
                owner: { select: { id: true, name: true } },
                contributors: {
                    include: { user: { select: { id: true, name: true } } },
                },
            },
            orderBy: { updatedAt: 'desc' },
        });

        return reply.send({
            data: workflows.map((w: unknown) => ({
                id: w.id,
                name: w.name,
                workflowType: w.workflowType,
                category: w.category,
                status: w.status.toLowerCase(),
                currentStep: w.currentStep,
                aiMode: w.aiMode,
                ownerId: w.ownerId,
                ownerName: w.owner?.name ?? 'Unknown',
                createdAt: w.createdAt.toISOString(),
                updatedAt: w.updatedAt.toISOString(),
                contributors: w.contributors.map((c: unknown) => ({
                    userId: c.userId,
                    userName: c.user?.name ?? 'Unknown',
                    role: c.role,
                    joinedAt: c.joinedAt.toISOString(),
                })),
                steps: w.stepsData ?? {},
                audit: [],
            })),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/workflow-templates
     * List workflow templates
     */
    fastify.get('/devsecops/workflow-templates', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/workflow-templates');

        const templates = await prisma.workflowTemplate.findMany({
            where: { isActive: true },
            orderBy: { name: 'asc' },
        });

        return reply.send({
            data: templates.map((t: unknown) => ({
                id: t.id,
                name: t.name,
                description: t.description,
                workflowType: t.workflowType,
                category: t.category,
                defaultSteps: t.defaultSteps ?? {},
                estimatedDuration: t.estimatedDuration,
            })),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/ai-insights
     * List AI insights
     */
    fastify.get('/devsecops/ai-insights', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/ai-insights');

        const insights = await prisma.aIInsight.findMany({
            where: { status: 'ACTIVE' },
            orderBy: { createdAt: 'desc' },
            take: 50,
        });

        return reply.send({
            data: insights.map((i: unknown) => ({
                id: i.id,
                type: i.type,
                severity: i.severity,
                title: i.title,
                description: i.description,
                recommendation: i.recommendation,
                actionable: i.actionable,
                confidence: i.confidence,
                source: i.source,
                createdAt: i.createdAt.toISOString(),
            })),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/predictions
     * List predictions
     */
    fastify.get('/devsecops/predictions', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/predictions');

        const predictions = await prisma.prediction.findMany({
            orderBy: { createdAt: 'desc' },
            take: 20,
        });

        return reply.send({
            data: predictions.map((p: unknown) => ({
                id: p.id,
                type: p.type,
                name: p.name,
                value: p.value,
                confidence: p.confidence,
                timeframe: p.timeframe,
                factors: p.factors ?? [],
                createdAt: p.createdAt.toISOString(),
            })),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/anomaly-alerts
     * List anomaly alerts
     */
    fastify.get('/devsecops/anomaly-alerts', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/anomaly-alerts');

        const alerts = await prisma.anomalyAlert.findMany({
            orderBy: { detectedAt: 'desc' },
            take: 20,
        });

        return reply.send({
            data: alerts.map((a: unknown) => ({
                id: a.id,
                metric: a.metric,
                severity: a.severity,
                status: a.status,
                expectedValue: a.expectedValue,
                actualValue: a.actualValue,
                deviation: a.deviation,
                message: a.message,
                detectedAt: a.detectedAt.toISOString(),
                resolvedAt: a.resolvedAt?.toISOString(),
            })),
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    // ============================================================================
    // Phase Detail Routes
    // ============================================================================

    /**
     * GET /api/devsecops/phases/:phaseId
     * Get detailed information for a specific phase including items
     */
    fastify.get<{ Params: { phaseId: string } }>('/devsecops/phases/:phaseId', async (request, reply) => {
        const { phaseId } = request.params;
        console.log(`[DEVSECOPS] GET /devsecops/phases/${phaseId}`);

        // Find phase by key or id
        const phase = await prisma.phase.findFirst({
            where: {
                OR: [
                    { key: phaseId },
                    { id: phaseId },
                ],
            },
            include: {
                items: {
                    include: {
                        owners: {
                            include: { user: { select: { id: true, name: true, email: true } } },
                        },
                        tags: { select: { tag: true } },
                    },
                },
                _count: {
                    select: { items: true },
                },
            },
        });

        if (!phase) {
            return reply.status(404).send({
                success: false,
                error: `Phase ${phaseId} not found`,
            });
        }

        // Count completed items
        const completedItems = phase.items.filter((i) => i.status === 'COMPLETED');
        
        // Get owners from items
        const ownerSet = new Map<string, { id: string; name: string; email: string }>();
        for (const item of phase.items) {
            for (const owner of item.owners ?? []) {
                if (owner.user && !ownerSet.has(owner.user.id)) {
                    ownerSet.set(owner.user.id, owner.user);
                }
            }
        }

        return reply.send({
            data: {
                id: phase.key,
                title: phase.title,
                description: phase.description,
                icon: phase.icon ?? getPhaseIcon(phase.key),
                status: phase.status?.toLowerCase() ?? 'active',
                order: phase.order,
                color: phase.color,
                progress: phase.items.length > 0 
                    ? Math.round((completedItems.length / phase.items.length) * 100) 
                    : 0,
                itemCount: phase.items.length,
                completedItemCount: completedItems.length,
                healthScore: phase.healthScore,
                riskScore: phase.riskScore,
                predictedEndDate: phase.predictedEndDate?.toISOString(),
                owners: Array.from(ownerSet.values()),
                items: phase.items.map((item: { id: string; title: string; status: string | null; priority: string | null; progress: number | null; dueDate: Date | null }) => ({
                    id: item.id,
                    title: item.title,
                    status: item.status?.toLowerCase().replace('_', '-') ?? 'not-started',
                    priority: item.priority?.toLowerCase() ?? 'medium',
                    progress: item.progress ?? 0,
                    dueDate: item.dueDate?.toISOString(),
                })),
            },
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    // ============================================================================
    // Reports Routes
    // ============================================================================

    /**
     * GET /api/devsecops/reports
     * List all available reports
     */
    fastify.get('/devsecops/reports', async (request: FastifyRequest, reply: FastifyReply) => {
        console.log('[DEVSECOPS] GET /devsecops/reports');

        // Return static report definitions (report generation is dynamic based on current data)
        return reply.send({
            data: [
                { id: 'executive', title: 'Executive Summary', description: 'High-level overview of DevSecOps progress', type: 'summary' },
                { id: 'release', title: 'Release Readiness', description: 'Deployment readiness status', type: 'release' },
                { id: 'security', title: 'Security & Compliance', description: 'Security metrics and compliance status', type: 'security' },
                { id: 'operations', title: 'Operational Health', description: 'System health and performance', type: 'operations' },
            ],
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });

    /**
     * GET /api/devsecops/reports/:reportId
     * Get detailed report with KPIs and metrics
     */
    fastify.get<{ Params: { reportId: string } }>('/devsecops/reports/:reportId', async (request, reply) => {
        const { reportId } = request.params;
        console.log(`[DEVSECOPS] GET /devsecops/reports/${reportId}`);

        // Generate report data from current metrics
        const phases = await prisma.phase.findMany({
            include: {
                _count: { select: { items: true } },
            },
        });

        const totalItems = await prisma.item.count();
        const completedItems = await prisma.item.count({ where: { status: 'COMPLETED' } });
        const blockedItems = await prisma.item.count({ where: { status: 'BLOCKED' } });
        const inProgressItems = await prisma.item.count({ where: { status: 'IN_PROGRESS' } });

        // Generate KPIs based on current data
        const kpis = [
            { 
                name: 'Completion Rate', 
                value: totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0, 
                target: 80,
                unit: '%',
            },
            { 
                name: 'Active Work Items', 
                value: inProgressItems, 
                target: 10,
                unit: 'items',
            },
            { 
                name: 'Blocked Items', 
                value: blockedItems, 
                target: 0,
                unit: 'items',
            },
            { 
                name: 'Total Items', 
                value: totalItems, 
                target: totalItems,
                unit: 'items',
            },
        ];

        // Generate risks based on current state
        const risks: Array<{ severity: string; description: string }> = [];
        if (blockedItems > 0) {
            risks.push({ severity: 'high', description: `${blockedItems} items are currently blocked` });
        }
        if (completedItems / Math.max(totalItems, 1) < 0.5) {
            risks.push({ severity: 'medium', description: 'Completion rate is below 50%' });
        }

        // Add phase-specific risks
        for (const phase of phases) {
            if (phase.riskScore && phase.riskScore > 70) {
                risks.push({ severity: 'high', description: `${phase.title} phase has elevated risk score` });
            }
        }

        const reportTitles: Record<string, string> = {
            'executive': 'Executive Summary',
            'release': 'Release Readiness Report',
            'security': 'Security & Compliance Report',
            'operations': 'Operational Health Report',
        };

        return reply.send({
            data: {
                id: reportId,
                title: reportTitles[reportId] ?? `${reportId.charAt(0).toUpperCase() + reportId.slice(1)} Report`,
                generatedAt: new Date().toISOString(),
                data: {
                    kpis,
                    risks,
                    phases: phases.map(p => ({
                        id: p.key,
                        title: p.title,
                        itemCount: p._count?.items ?? 0,
                        status: p.status?.toLowerCase() ?? 'active',
                    })),
                },
            },
            success: true,
            metadata: {
                timestamp: new Date().toISOString(),
                requestId: Math.random().toString(36).slice(2),
            },
        });
    });
}
