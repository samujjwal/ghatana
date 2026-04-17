import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import approvalsRoutes from '../approvals.js';

const { mockPrisma, mockJavaClient } = vi.hoisted(() => ({
    mockPrisma: {
        approval: {
            findMany: vi.fn(),
            count: vi.fn(),
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
        },
        approvalStep: {
            update: vi.fn(),
        },
    },
    mockJavaClient: {
        submitApproval: vi.fn(),
        recordDecision: vi.fn(),
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

vi.mock('../../../generated/prisma-client/index.js', () => ({
    Prisma: {
        JsonNull: null,
    },
}));

vi.mock('../../services/java-client.js', () => ({
    getJavaServiceClient: () => mockJavaClient,
}));

function buildApprovalStep(overrides: Record<string, unknown> = {}) {
    return {
        id: 'step-1',
        approvalId: 'approval-1',
        level: 0,
        approverId: 'approver-1',
        role: 'MANAGER',
        status: 'NOTIFIED',
        decision: null,
        comment: null,
        notifiedAt: new Date('2026-04-16T10:00:00.000Z'),
        decidedAt: null,
        reminderSentAt: null,
        createdAt: new Date('2026-04-16T09:00:00.000Z'),
        updatedAt: new Date('2026-04-16T09:30:00.000Z'),
        ...overrides,
    };
}

function buildApproval(overrides: Record<string, unknown> = {}) {
    return {
        id: 'approval-1',
        type: 'TIME_OFF',
        requesterId: 'requester-1',
        status: 'PENDING',
        data: { days: 3 },
        metadata: { title: 'Vacation Request', description: 'Week off in May' },
        currentStepIndex: 0,
        createdAt: new Date('2026-04-16T09:00:00.000Z'),
        updatedAt: new Date('2026-04-16T09:30:00.000Z'),
        completedAt: null,
        approvalSteps: [buildApprovalStep()],
        ...overrides,
    };
}

describe('Approvals Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(approvalsRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list approvals with filters and mapped title metadata', async () => {
        mockPrisma.approval.findMany.mockResolvedValue([buildApproval()]);
        mockPrisma.approval.count.mockResolvedValue(1);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/approvals?status=PENDING&type=TIME_OFF&requesterId=requester-1&limit=10&offset=5',
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.approval.findMany).toHaveBeenCalledWith(
            expect.objectContaining({
                where: {
                    status: 'PENDING',
                    type: 'TIME_OFF',
                    requesterId: 'requester-1',
                },
                take: 10,
                skip: 5,
            })
        );
        expect(response.json()).toEqual({
            data: [
                expect.objectContaining({
                    id: 'approval-1',
                    title: 'Vacation Request',
                    description: 'Week off in May',
                    steps: [expect.objectContaining({ id: 'step-1', approverId: 'approver-1' })],
                }),
            ],
            total: 1,
            limit: 10,
            offset: 5,
        });
    });

    it('should merge title and description into metadata when submitting approvals', async () => {
        mockPrisma.approval.create.mockImplementation(async ({ data }: { data: Record<string, unknown> }) =>
            buildApproval({
                id: 'approval-created',
                type: data.type,
                requesterId: data.requesterId,
                status: data.status,
                data: data.data,
                metadata: data.metadata,
                currentStepIndex: data.currentStepIndex,
                approvalSteps: [buildApprovalStep()],
            })
        );

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/approvals',
            payload: {
                type: 'legacy-manual',
                requesterId: 'requester-1',
                title: 'Budget exception',
                description: 'Need approval for one-off tooling cost',
                data: { amount: 1200 },
                metadata: { source: 'ops' },
                approvers: [{ userId: 'approver-1', role: 'DIRECTOR', level: 0 }],
            },
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.approval.create).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    metadata: {
                        source: 'ops',
                        title: 'Budget exception',
                        description: 'Need approval for one-off tooling cost',
                    },
                    status: 'PENDING',
                }),
            })
        );
        expect(mockJavaClient.submitApproval).not.toHaveBeenCalled();
        expect(response.json()).toEqual({
            id: 'approval-created',
            type: 'legacy-manual',
            status: 'PENDING',
            currentStepIndex: 0,
        });
    });

    it('should return 404 when approval details are missing', async () => {
        mockPrisma.approval.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/approvals/missing-approval',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Approval not found' });
    });

    it('should reject decisions from users who are not the current approver', async () => {
        mockPrisma.approval.findUnique.mockResolvedValue(buildApproval());

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/approvals/approval-1/decision',
            payload: {
                approverId: 'wrong-approver',
                decision: 'APPROVE',
            },
        });

        expect(response.statusCode).toBe(403);
        expect(response.json()).toEqual({ error: 'Not authorized to approve this step' });
        expect(mockPrisma.approvalStep.update).not.toHaveBeenCalled();
    });

    it('should advance to the next step and notify the Java service after an approval decision', async () => {
        mockPrisma.approval.findUnique.mockResolvedValue(
            buildApproval({
                approvalSteps: [
                    buildApprovalStep({ id: 'step-1', level: 0, approverId: 'approver-1', status: 'NOTIFIED' }),
                    buildApprovalStep({ id: 'step-2', level: 1, approverId: 'approver-2', status: 'PENDING' }),
                ],
            })
        );
        mockPrisma.approvalStep.update.mockResolvedValue({ id: 'step-updated' });
        mockPrisma.approval.update.mockResolvedValue(
            buildApproval({
                status: 'IN_PROGRESS',
                currentStepIndex: 1,
                approvalSteps: [
                    buildApprovalStep({ id: 'step-1', level: 0, approverId: 'approver-1', status: 'COMPLETED', decision: 'APPROVE' }),
                    buildApprovalStep({ id: 'step-2', level: 1, approverId: 'approver-2', status: 'NOTIFIED' }),
                ],
            })
        );

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/approvals/approval-1/decision',
            payload: {
                approverId: 'approver-1',
                decision: 'APPROVE',
                comment: 'Looks good',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.approvalStep.update).toHaveBeenNthCalledWith(
            1,
            expect.objectContaining({
                where: { id: 'step-1' },
                data: expect.objectContaining({
                    status: 'COMPLETED',
                    decision: 'APPROVE',
                    comment: 'Looks good',
                }),
            })
        );
        expect(mockPrisma.approvalStep.update).toHaveBeenNthCalledWith(
            2,
            expect.objectContaining({
                where: { id: 'step-2' },
                data: expect.objectContaining({
                    status: 'NOTIFIED',
                }),
            })
        );
        expect(mockJavaClient.recordDecision).toHaveBeenCalledWith('approval-1', {
            approverId: 'approver-1',
            approved: true,
            comments: 'Looks good',
        });
        expect(response.json()).toEqual({
            approval: {},
            nextStep: {},
        });
    });

    it('should return pending approvals for the requested approver', async () => {
        mockPrisma.approval.findMany.mockResolvedValue([
            buildApproval({
                id: 'approval-pending',
                approvalSteps: [buildApprovalStep({ approverId: 'approver-9' })],
            }),
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/approvals/pending?userId=approver-9',
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.approval.findMany).toHaveBeenCalledWith(
            expect.objectContaining({
                where: expect.objectContaining({
                    approvalSteps: {
                        some: {
                            approverId: 'approver-9',
                            status: { in: ['NOTIFIED', 'PENDING'] },
                        },
                    },
                }),
            })
        );
        expect(response.json()).toEqual({
            data: [expect.objectContaining({ id: 'approval-pending' })],
            total: 1,
        });
    });
});
