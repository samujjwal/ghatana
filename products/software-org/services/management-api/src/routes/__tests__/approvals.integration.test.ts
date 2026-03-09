/**
 * Integration tests for approval workflow
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end approval workflow from API → Prisma → Java services
 * @doc.layer integration
 * @doc.pattern Integration Test
 *
 * Test Strategy:
 * - Test complete approval submission flow
 * - Test decision recording and status updates
 * - Test pending approvals retrieval
 * - Test multi-step approval progression
 * - Verify Java service integration (mocked)
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import approvalsRoutes from '../approvals.js';
import { prisma } from '../../db/client.js';

// Mock Java service client
const mockJavaClient = {
    submitApproval: vi.fn(),
    recordDecision: vi.fn(),
    getPendingApprovals: vi.fn(),
    getApprovalStatus: vi.fn(),
    healthCheck: vi.fn(),
};

vi.mock('../services/java-client.js', () => ({
    getJavaServiceClient: () => mockJavaClient,
}));

describe('Approval Workflow Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        // Create fresh Fastify instance
        fastify = Fastify({ logger: false });
        await fastify.register(approvalsRoutes, { prefix: '/api/v1' });

        // Clear all mocks
        vi.clearAllMocks();

        // Setup default mock responses
        mockJavaClient.submitApproval.mockResolvedValue({
            requestId: 'approval-1',
            status: 'PENDING',
        });
        mockJavaClient.recordDecision.mockResolvedValue({
            requestId: 'approval-1',
            status: 'APPROVED',
        });
    });

    afterEach(async () => {
        await fastify.close();
    });

    describe('POST /api/v1/approvals - Submit Approval', () => {
        it('should create approval request and call Java service', async () => {
            // GIVEN: Valid approval request
            const requestBody = {
                type: 'TIME_OFF',
                requesterId: 'user-1',
                data: {
                    startDate: '2025-12-20',
                    endDate: '2025-12-27',
                    reason: 'Holiday vacation',
                },
                metadata: {
                    priority: 'normal',
                },
                approvers: [
                    { userId: 'manager-1', role: 'MANAGER', level: 0 },
                    { userId: 'exec-1', role: 'EXECUTIVE', level: 1 },
                ],
            };

            // WHEN: Submit approval request
            const response = await fastify.inject({
                method: 'POST',
                url: '/api/v1/approvals',
                payload: requestBody,
            });

            // THEN: Request created successfully
            expect(response.statusCode).toBe(201);
            const result = JSON.parse(response.body);
            expect(result).toHaveProperty('id');
            expect(result.type).toBe('TIME_OFF');
            expect(result.status).toBe('PENDING');
            expect(result.currentStepIndex).toBe(0);

            // AND: Java service was called
            expect(mockJavaClient.submitApproval).toHaveBeenCalledWith({
                requestId: result.id,
                type: 'TIME_OFF',
                requesterId: 'user-1',
                approvers: ['manager-1', 'exec-1'],
                metadata: { priority: 'normal' },
            });

            // AND: Database has approval and steps
            const approval = await prisma.approval.findUnique({
                where: { id: result.id },
                include: { approvalSteps: true },
            });
            expect(approval).toBeTruthy();
            expect(approval?.approvalSteps).toHaveLength(2);
            expect(approval?.approvalSteps[0].status).toBe('NOTIFIED'); // First step notified
            expect(approval?.approvalSteps[1].status).toBe('PENDING'); // Second step pending
        });

        it('should continue if Java service fails', async () => {
            // GIVEN: Java service fails
            mockJavaClient.submitApproval.mockRejectedValue(new Error('Service unavailable'));

            // WHEN: Submit approval request
            const response = await fastify.inject({
                method: 'POST',
                url: '/api/v1/approvals',
                payload: {
                    type: 'EXPENSE',
                    requesterId: 'user-1',
                    data: { amount: 1000 },
                    approvers: [{ userId: 'manager-1', role: 'MANAGER', level: 0 }],
                },
            });

            // THEN: Request still succeeds (graceful degradation)
            expect(response.statusCode).toBe(201);
            const result = JSON.parse(response.body);
            expect(result).toHaveProperty('id');

            // AND: Database still has the approval
            const approval = await prisma.approval.findUnique({
                where: { id: result.id },
            });
            expect(approval).toBeTruthy();
        });

        it('should validate required fields', async () => {
            // WHEN: Submit with missing fields
            const response = await fastify.inject({
                method: 'POST',
                url: '/api/v1/approvals',
                payload: {
                    type: 'TIME_OFF',
                    // Missing requesterId and approvers
                },
            });

            // THEN: Validation error
            expect(response.statusCode).toBe(400);
        });
    });

    describe('POST /api/v1/approvals/:id/decision - Record Decision', () => {
        it('should approve and advance to next step', async () => {
            // GIVEN: Existing approval with 2 steps
            const approval = await prisma.approval.create({
                data: {
                    type: 'TIME_OFF',
                    requesterId: 'user-1',
                    status: 'PENDING',
                    data: {},
                    metadata: {},
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: [
                            {
                                level: 0,
                                approverId: 'manager-1',
                                role: 'MANAGER',
                                status: 'NOTIFIED',
                            },
                            {
                                level: 1,
                                approverId: 'exec-1',
                                role: 'EXECUTIVE',
                                status: 'PENDING',
                            },
                        ],
                    },
                },
                include: { approvalSteps: true },
            });

            // WHEN: Manager approves
            const response = await fastify.inject({
                method: 'POST',
                url: `/api/v1/approvals/${approval.id}/decision`,
                payload: {
                    approverId: 'manager-1',
                    decision: 'APPROVE',
                    comment: 'Approved - enjoy your vacation',
                },
            });

            // THEN: Decision recorded
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.approval.status).toBe('IN_PROGRESS');
            expect(result.approval.currentStepIndex).toBe(1);
            expect(result.nextStep).toBeTruthy();
            expect(result.nextStep.approverId).toBe('exec-1');

            // AND: Java service was called
            expect(mockJavaClient.recordDecision).toHaveBeenCalledWith(
                approval.id,
                {
                    approverId: 'manager-1',
                    approved: true,
                    comments: 'Approved - enjoy your vacation',
                }
            );

            // AND: Database updated
            const updated = await prisma.approval.findUnique({
                where: { id: approval.id },
                include: { approvalSteps: true },
            });
            expect(updated?.currentStepIndex).toBe(1);
            expect(updated?.approvalSteps[0].status).toBe('COMPLETED');
            expect(updated?.approvalSteps[0].decision).toBe('APPROVE');
            expect(updated?.approvalSteps[1].status).toBe('NOTIFIED');
        });

        it('should complete approval on final step', async () => {
            // GIVEN: Approval at final step
            const approval = await prisma.approval.create({
                data: {
                    type: 'HIRE',
                    requesterId: 'user-1',
                    status: 'IN_PROGRESS',
                    data: {},
                    metadata: {},
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: [
                            {
                                level: 0,
                                approverId: 'ceo-1',
                                role: 'EXECUTIVE',
                                status: 'NOTIFIED',
                            },
                        ],
                    },
                },
            });

            // WHEN: CEO approves
            const response = await fastify.inject({
                method: 'POST',
                url: `/api/v1/approvals/${approval.id}/decision`,
                payload: {
                    approverId: 'ceo-1',
                    decision: 'APPROVE',
                },
            });

            // THEN: Approval completed
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.approval.status).toBe('APPROVED');
            expect(result.approval.completedAt).toBeTruthy();
            expect(result.nextStep).toBeNull();
        });

        it('should reject and stop workflow', async () => {
            // GIVEN: Approval with multiple steps
            const approval = await prisma.approval.create({
                data: {
                    type: 'EXPENSE',
                    requesterId: 'user-1',
                    status: 'PENDING',
                    data: { amount: 10000 },
                    metadata: {},
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: [
                            {
                                level: 0,
                                approverId: 'manager-1',
                                role: 'MANAGER',
                                status: 'NOTIFIED',
                            },
                            {
                                level: 1,
                                approverId: 'finance-1',
                                role: 'FINANCE',
                                status: 'PENDING',
                            },
                        ],
                    },
                },
            });

            // WHEN: Manager rejects
            const response = await fastify.inject({
                method: 'POST',
                url: `/api/v1/approvals/${approval.id}/decision`,
                payload: {
                    approverId: 'manager-1',
                    decision: 'REJECT',
                    comment: 'Amount exceeds budget',
                },
            });

            // THEN: Approval rejected
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.approval.status).toBe('REJECTED');
            expect(result.approval.completedAt).toBeTruthy();
            expect(result.nextStep).toBeNull();

            // AND: Database shows rejection
            const updated = await prisma.approval.findUnique({
                where: { id: approval.id },
                include: { approvalSteps: true },
            });
            expect(updated?.status).toBe('REJECTED');
            expect(updated?.approvalSteps[0].decision).toBe('REJECT');
        });

        it('should return 403 if approver not authorized', async () => {
            // GIVEN: Approval
            const approval = await prisma.approval.create({
                data: {
                    type: 'TIME_OFF',
                    requesterId: 'user-1',
                    status: 'PENDING',
                    data: {},
                    metadata: {},
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: [
                            {
                                level: 0,
                                approverId: 'manager-1',
                                role: 'MANAGER',
                                status: 'NOTIFIED',
                            },
                        ],
                    },
                },
            });

            // WHEN: Wrong approver tries to approve
            const response = await fastify.inject({
                method: 'POST',
                url: `/api/v1/approvals/${approval.id}/decision`,
                payload: {
                    approverId: 'wrong-user',
                    decision: 'APPROVE',
                },
            });

            // THEN: Forbidden
            expect(response.statusCode).toBe(403);
        });
    });

    describe('GET /api/v1/approvals/:id - Get Approval', () => {
        it('should return approval with steps', async () => {
            // GIVEN: Existing approval
            const approval = await prisma.approval.create({
                data: {
                    type: 'PROMOTION',
                    requesterId: 'user-1',
                    status: 'PENDING',
                    data: { position: 'Senior Engineer' },
                    metadata: {},
                    currentStepIndex: 0,
                    approvalSteps: {
                        create: [
                            {
                                level: 0,
                                approverId: 'manager-1',
                                role: 'MANAGER',
                                status: 'NOTIFIED',
                            },
                        ],
                    },
                },
            });

            // WHEN: Get approval
            const response = await fastify.inject({
                method: 'GET',
                url: `/api/v1/approvals/${approval.id}`,
            });

            // THEN: Returns approval details
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.id).toBe(approval.id);
            expect(result.type).toBe('PROMOTION');
            expect(result.status).toBe('PENDING');
            expect(result.steps).toHaveLength(1);
        });

        it('should return 404 for non-existent approval', async () => {
            // WHEN: Get non-existent approval
            const response = await fastify.inject({
                method: 'GET',
                url: '/api/v1/approvals/non-existent',
            });

            // THEN: Not found
            expect(response.statusCode).toBe(404);
        });
    });

    describe('GET /api/v1/approvals/pending - Get Pending Approvals', () => {
        it('should return pending approvals for user', async () => {
            // GIVEN: Multiple approvals
            await prisma.approval.createMany({
                data: [
                    {
                        type: 'TIME_OFF',
                        requesterId: 'user-1',
                        status: 'PENDING',
                        data: {},
                        metadata: {},
                        currentStepIndex: 0,
                    },
                    {
                        type: 'EXPENSE',
                        requesterId: 'user-2',
                        status: 'APPROVED',
                        data: {},
                        metadata: {},
                        currentStepIndex: 1,
                    },
                ],
            });

            const approvals = await prisma.approval.findMany();

            // Create steps
            await prisma.approvalStep.createMany({
                data: [
                    {
                        approvalId: approvals[0].id,
                        level: 0,
                        approverId: 'manager-1',
                        role: 'MANAGER',
                        status: 'NOTIFIED',
                    },
                    {
                        approvalId: approvals[1].id,
                        level: 0,
                        approverId: 'manager-1',
                        role: 'MANAGER',
                        status: 'COMPLETED',
                    },
                ],
            });

            // WHEN: Get pending for manager-1
            const response = await fastify.inject({
                method: 'GET',
                url: '/api/v1/approvals/pending?userId=manager-1',
            });

            // THEN: Returns only pending
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.data).toHaveLength(1);
            expect(result.data[0].status).toBe('PENDING');
        });
    });

    describe('GET /api/v1/approvals - List Approvals', () => {
        it('should filter by status', async () => {
            // GIVEN: Approvals with different statuses
            await prisma.approval.createMany({
                data: [
                    {
                        type: 'TIME_OFF',
                        requesterId: 'user-1',
                        status: 'PENDING',
                        data: {},
                        metadata: {},
                        currentStepIndex: 0,
                    },
                    {
                        type: 'EXPENSE',
                        requesterId: 'user-1',
                        status: 'APPROVED',
                        data: {},
                        metadata: {},
                        currentStepIndex: 1,
                    },
                    {
                        type: 'HIRE',
                        requesterId: 'user-1',
                        status: 'REJECTED',
                        data: {},
                        metadata: {},
                        currentStepIndex: 0,
                    },
                ],
            });

            // WHEN: Filter by PENDING
            const response = await fastify.inject({
                method: 'GET',
                url: '/api/v1/approvals?status=PENDING',
            });

            // THEN: Returns only pending
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.data).toHaveLength(1);
            expect(result.data[0].status).toBe('PENDING');
            expect(result.total).toBe(1);
        });

        it('should support pagination', async () => {
            // GIVEN: Many approvals
            await prisma.approval.createMany({
                data: Array.from({ length: 15 }, (_, i) => ({
                    type: 'TIME_OFF',
                    requesterId: 'user-1',
                    status: 'PENDING',
                    data: {},
                    metadata: {},
                    currentStepIndex: 0,
                })),
            });

            // WHEN: Get page 2 with limit 10
            const response = await fastify.inject({
                method: 'GET',
                url: '/api/v1/approvals?limit=10&offset=10',
            });

            // THEN: Returns remaining items
            expect(response.statusCode).toBe(200);
            const result = JSON.parse(response.body);
            expect(result.data).toHaveLength(5);
            expect(result.total).toBe(15);
            expect(result.limit).toBe(10);
            expect(result.offset).toBe(10);
        });
    });
});
