/**
 * Approvals API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for approval workflow management
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/approvals - List approvals with filters
 * - POST /api/v1/approvals - Submit new approval request
 * - GET /api/v1/approvals/:id - Get approval details
 * - POST /api/v1/approvals/:id/decision - Record approval decision
 * - GET /api/v1/approvals/pending - Get pending approvals for user
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import { getJavaServiceClient } from '../services/java-client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

/** Request interfaces */
interface ListApprovalsQuery {
    status?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'IN_PROGRESS' | 'COMPLETED';
    type?: string;
    requesterId?: string;
    limit?: number;
    offset?: number;
}

interface SubmitApprovalBody {
    type: string;
    requesterId: string;
    title?: string;
    description?: string;
    data: Record<string, unknown>;
    metadata?: Record<string, unknown>;
    approvers: Array<{
        userId: string;
        role: string;
        level: number;
    }>;
}

interface RecordDecisionBody {
    approverId: string;
    decision: 'APPROVE' | 'REJECT';
    comment?: string;
}

interface DelegateApprovalBody {
    fromUserId: string;
    toUserId: string;
    reason?: string;
}

interface GetPendingQuery {
    userId: string;
}

/**
 * Register approval routes
 */
export default async function approvalsRoutes(fastify: FastifyInstance) {
    const isPlainObject = (value: unknown): value is Record<string, unknown> =>
        typeof value === 'object' && value !== null && !Array.isArray(value);

    const mapApprovalForApi = (approval: {
        id: string;
        type: string;
        requesterId: string;
        status: string;
        data: Prisma.JsonValue;
        metadata: Prisma.JsonValue;
        currentStepIndex: number;
        createdAt: Date;
        updatedAt: Date;
        completedAt: Date | null;
        approvalSteps?: Array<{
            id: string;
            approvalId: string;
            level: number;
            approverId: string | null;
            role: string;
            status: string;
            decision: string | null;
            comment: string | null;
            notifiedAt: Date | null;
            decidedAt: Date | null;
            reminderSentAt: Date | null;
            createdAt: Date;
            updatedAt: Date;
        }>;
    }) => {
        const { approvalSteps, ...rest } = approval;

        const metadataObject = isPlainObject(rest.metadata) ? rest.metadata : null;
        const title = typeof metadataObject?.title === 'string' ? metadataObject.title : null;
        const description =
            typeof metadataObject?.description === 'string' ? metadataObject.description : null;

        return {
            ...rest,
            title,
            description,
            createdAt: rest.createdAt.toISOString(),
            updatedAt: rest.updatedAt.toISOString(),
            completedAt: rest.completedAt?.toISOString() ?? null,
            steps:
                (approvalSteps ?? []).map((step) => ({
                    id: step.id,
                    approvalId: step.approvalId,
                    level: step.level,
                    approverId: step.approverId,
                    role: step.role,
                    status: step.status,
                    decision: step.decision,
                    comment: step.comment,
                    notifiedAt: step.notifiedAt?.toISOString() ?? null,
                    decidedAt: step.decidedAt?.toISOString() ?? null,
                    reminderSentAt: step.reminderSentAt?.toISOString() ?? null,
                    createdAt: step.createdAt.toISOString(),
                    updatedAt: step.updatedAt.toISOString(),
                })) ?? [],
        };
    };

    const toJavaApprovalType = (
        approvalType: string
    ): 'TIME_OFF' | 'EXPENSE' | 'HIRE' | 'PROMOTION' | 'PURCHASE' | null => {
        switch (approvalType) {
            case 'TIME_OFF':
            case 'time-off':
            case 'time_off':
                return 'TIME_OFF';
            case 'EXPENSE':
            case 'expense':
            case 'budget':
                return 'EXPENSE';
            case 'HIRE':
            case 'hire':
                return 'HIRE';
            case 'PROMOTION':
            case 'promotion':
                return 'PROMOTION';
            case 'PURCHASE':
            case 'purchase':
                return 'PURCHASE';
            default:
                return null;
        }
    };

    /**
     * List approvals with optional filters
     */
    fastify.get<{ Querystring: ListApprovalsQuery }>(
        '/approvals',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        status: { type: 'string', enum: ['PENDING', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED'] },
                        type: { type: 'string' },
                        requesterId: { type: 'string' },
                        limit: { type: 'number', default: 50 },
                        offset: { type: 'number', default: 0 },
                    },
                },
                response: {
                    200: {
                        type: 'object',
                        properties: {
                            data: {
                                type: 'array',
                                items: {
                                    type: 'object',
                                    properties: {
                                        id: { type: 'string' },
                                        type: { type: 'string' },
                                        requesterId: { type: 'string' },
                                        status: { type: 'string' },
                                        title: { type: 'string', nullable: true },
                                        description: { type: 'string', nullable: true },
                                        data: { type: 'object', additionalProperties: true },
                                        metadata: { type: 'object', additionalProperties: true },
                                        currentStepIndex: { type: 'number' },
                                        createdAt: { type: 'string' },
                                        updatedAt: { type: 'string' },
                                        completedAt: { type: 'string', nullable: true },
                                        steps: {
                                            type: 'array',
                                            items: {
                                                type: 'object',
                                                properties: {
                                                    id: { type: 'string' },
                                                    approvalId: { type: 'string' },
                                                    level: { type: 'number' },
                                                    approverId: { type: 'string', nullable: true },
                                                    role: { type: 'string' },
                                                    status: { type: 'string' },
                                                    decision: { type: 'string', nullable: true },
                                                    comment: { type: 'string', nullable: true },
                                                    notifiedAt: { type: 'string', nullable: true },
                                                    decidedAt: { type: 'string', nullable: true },
                                                    reminderSentAt: { type: 'string', nullable: true },
                                                    createdAt: { type: 'string' },
                                                    updatedAt: { type: 'string' },
                                                },
                                            },
                                        },
                                    },
                                },
                            },
                            total: { type: 'number' },
                            limit: { type: 'number' },
                            offset: { type: 'number' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { status, type, requesterId, limit = 50, offset = 0 } = request.query;

            const where: Prisma.ApprovalWhereInput = {};
            if (status) where.status = status;
            if (type) where.type = type;
            if (requesterId) where.requesterId = requesterId;

            const [approvals, total] = await Promise.all([
                prisma.approval.findMany({
                    where,
                    take: limit,
                    skip: offset,
                    orderBy: { createdAt: 'desc' },
                    include: {
                        approvalSteps: {
                            orderBy: { level: 'asc' },
                        },
                    },
                }),
                prisma.approval.count({ where }),
            ]);

            return {
                data: approvals.map(mapApprovalForApi),
                total,
                limit,
                offset,
            };
        }
    );

    /**
     * Submit new approval request
     */
    fastify.post<{ Body: SubmitApprovalBody }>(
        '/approvals',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['type', 'requesterId', 'data', 'approvers'],
                    properties: {
                        type: { type: 'string' },
                        requesterId: { type: 'string' },
                        title: { type: 'string' },
                        description: { type: 'string' },
                        data: { type: 'object' },
                        metadata: { type: 'object' },
                        approvers: {
                            type: 'array',
                            items: {
                                type: 'object',
                                required: ['userId', 'role', 'level'],
                                properties: {
                                    userId: { type: 'string' },
                                    role: { type: 'string' },
                                    level: { type: 'number' },
                                },
                            },
                        },
                    },
                },
                response: {
                    201: {
                        type: 'object',
                        properties: {
                            id: { type: 'string' },
                            type: { type: 'string' },
                            status: { type: 'string' },
                            currentStepIndex: { type: 'number' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { type, requesterId, data, metadata = {}, approvers, title, description } = request.body;

            const mergedMetadata: Record<string, unknown> = {
                ...(metadata ?? {}),
                ...(title ? { title } : {}),
                ...(description ? { description } : {}),
            };

            // Create approval with steps in a transaction
            const approval = await prisma.approval.create({
                data: {
                    type,
                    requesterId,
                    status: 'PENDING',
                    data: data as Prisma.InputJsonValue,
                    metadata: mergedMetadata as Prisma.InputJsonValue,
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: approvers.map((approver) => ({
                            level: approver.level,
                            approverId: approver.userId,
                            role: approver.role,
                            status: approver.level === 0 ? 'NOTIFIED' : 'PENDING',
                            notifiedAt: approver.level === 0 ? new Date() : undefined,
                        })),
                    },
                },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            // Integrate with Java approval engine
            try {
                const javaType = toJavaApprovalType(type);
                if (!javaType) {
                    reply.code(201);
                    return {
                        id: approval.id,
                        type: approval.type,
                        status: approval.status,
                        currentStepIndex: approval.currentStepIndex,
                    };
                }

                const javaClient = getJavaServiceClient();
                await javaClient.submitApproval({
                    requestId: approval.id,
                    type: javaType,
                    requesterId,
                    approvers: approvers.map(a => a.userId),
                    metadata: mergedMetadata,
                });
            } catch (error) {
                fastify.log.error(error, 'Failed to submit to Java approval engine');
                // Continue even if Java service fails - we have the data in DB
            }

            reply.code(201);
            return {
                id: approval.id,
                type: approval.type,
                status: approval.status,
                currentStepIndex: approval.currentStepIndex,
            };
        }
    );

    /**
     * Get approval details
     */
    fastify.get<{ Params: { id: string } }>(
        '/approvals/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    properties: {
                        id: { type: 'string' },
                    },
                },
                response: {
                    200: {
                        type: 'object',
                        properties: {
                            id: { type: 'string' },
                            type: { type: 'string' },
                            requesterId: { type: 'string' },
                            status: { type: 'string' },
                            data: { type: 'object' },
                            metadata: { type: 'object' },
                            currentStepIndex: { type: 'number' },
                            createdAt: { type: 'string' },
                            steps: { type: 'array' },
                        },
                    },
                    404: {
                        type: 'object',
                        properties: {
                            error: { type: 'string' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;

            const approval = await prisma.approval.findUnique({
                where: { id },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            if (!approval) {
                reply.code(404);
                return { error: 'Approval not found' };
            }

            return mapApprovalForApi(approval);
        }
    );

    /**
     * Record approval decision
     */
    fastify.post<{ Params: { id: string }; Body: RecordDecisionBody }>(
        '/approvals/:id/decision',
        {
            schema: {
                params: {
                    type: 'object',
                    properties: {
                        id: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    required: ['approverId', 'decision'],
                    properties: {
                        approverId: { type: 'string' },
                        decision: { type: 'string', enum: ['APPROVE', 'REJECT'] },
                        comment: { type: 'string' },
                    },
                },
                response: {
                    200: {
                        type: 'object',
                        properties: {
                            approval: { type: 'object' },
                            nextStep: { type: 'object', nullable: true },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;
            const { approverId, decision, comment } = request.body;

            // Get approval with current step
            const approval = await prisma.approval.findUnique({
                where: { id },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            if (!approval) {
                reply.code(404);
                return { error: 'Approval not found' };
            }

            const currentStep = approval.approvalSteps[approval.currentStepIndex];
            if (!currentStep || currentStep.approverId !== approverId) {
                reply.code(403);
                return { error: 'Not authorized to approve this step' };
            }

            // Update current step
            await prisma.approvalStep.update({
                where: { id: currentStep.id },
                data: {
                    status: 'COMPLETED',
                    decision: decision === 'APPROVE' ? 'APPROVE' : 'REJECT',
                    comment,
                    decidedAt: new Date(),
                },
            });

            // Update approval status
            let newStatus = approval.status;
            let newStepIndex = approval.currentStepIndex;
            let nextStep = null;

            if (decision === 'REJECT') {
                newStatus = 'REJECTED';
            } else if (approval.currentStepIndex < approval.approvalSteps.length - 1) {
                // Move to next step
                newStepIndex = approval.currentStepIndex + 1;
                newStatus = 'IN_PROGRESS';
                nextStep = approval.approvalSteps[newStepIndex];

                // Notify next approver
                await prisma.approvalStep.update({
                    where: { id: nextStep.id },
                    data: {
                        status: 'NOTIFIED',
                        notifiedAt: new Date(),
                    },
                });
            } else {
                // All steps approved
                newStatus = 'APPROVED';
            }

            const updatedApproval = await prisma.approval.update({
                where: { id },
                data: {
                    status: newStatus,
                    currentStepIndex: newStepIndex,
                    completedAt: newStatus === 'APPROVED' || newStatus === 'REJECTED' ? new Date() : undefined,
                },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            // Integrate with Java approval engine for audit and notifications
            try {
                const javaClient = getJavaServiceClient();
                await javaClient.recordDecision(id, {
                    approverId,
                    approved: decision === 'APPROVE',
                    comments: comment,
                });
            } catch (error) {
                fastify.log.error(error, 'Failed to record decision in Java approval engine');
                // Continue even if Java service fails
            }

            return {
                approval: mapApprovalForApi(updatedApproval),
                nextStep,
            };
        }
    );

    /**
     * Get pending approvals for a user
     */
    fastify.get<{ Querystring: GetPendingQuery }>(
        '/approvals/pending',
        {
            schema: {
                querystring: {
                    type: 'object',
                    required: ['userId'],
                    properties: {
                        userId: { type: 'string' },
                    },
                },
                response: {
                    200: {
                        type: 'object',
                        properties: {
                            data: { type: 'array' },
                            total: { type: 'number' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { userId } = request.query;

            const pendingApprovals = await prisma.approval.findMany({
                where: {
                    status: {
                        in: ['PENDING', 'IN_PROGRESS'],
                    },
                    approvalSteps: {
                        some: {
                            approverId: userId,
                            status: {
                                in: ['NOTIFIED', 'PENDING'],
                            },
                        },
                    },
                },
                include: {
                    approvalSteps: {
                        where: {
                            approverId: userId,
                        },
                        orderBy: { level: 'asc' },
                    },
                },
                orderBy: {
                    createdAt: 'asc',
                },
            });

            return {
                data: pendingApprovals.map(mapApprovalForApi),
                total: pendingApprovals.length,
            };
        }
    );

    /**
     * Delegate approval to another user
     */
    fastify.post<{ Params: { id: string }; Body: DelegateApprovalBody }>(
        '/approvals/:id/delegate',
        {
            schema: {
                params: {
                    type: 'object',
                    properties: {
                        id: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    required: ['fromUserId', 'toUserId'],
                    properties: {
                        fromUserId: { type: 'string' },
                        toUserId: { type: 'string' },
                        reason: { type: 'string' },
                    },
                },
                response: {
                    200: {
                        type: 'object',
                        properties: {
                            success: { type: 'boolean' },
                            approval: { type: 'object' },
                        },
                    },
                    403: {
                        type: 'object',
                        properties: {
                            error: { type: 'string' },
                        },
                    },
                    404: {
                        type: 'object',
                        properties: {
                            error: { type: 'string' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;
            const { fromUserId, toUserId, reason } = request.body;

            // Get approval with current step
            const approval = await prisma.approval.findUnique({
                where: { id },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            if (!approval) {
                reply.code(404);
                return { error: 'Approval not found' };
            }

            const currentStep = approval.approvalSteps[approval.currentStepIndex];
            if (!currentStep || currentStep.approverId !== fromUserId) {
                reply.code(403);
                return { error: 'Not authorized to delegate this approval' };
            }

            // Update the step to delegate to new approver
            await prisma.approvalStep.update({
                where: { id: currentStep.id },
                data: {
                    approverId: toUserId,
                    comment: reason ? `Delegated: ${reason}` : 'Delegated to another user',
                    notifiedAt: new Date(),
                },
            });

            const updatedApproval = await prisma.approval.findUnique({
                where: { id },
                include: {
                    approvalSteps: {
                        orderBy: { level: 'asc' },
                    },
                },
            });

            return {
                success: true,
                approval: updatedApproval ? mapApprovalForApi(updatedApproval) : null,
            };
        }
    );
}
