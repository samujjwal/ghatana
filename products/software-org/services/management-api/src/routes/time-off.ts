/**
 * Time Off API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for time-off request management
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/time-off - List time-off requests
 * - POST /api/v1/time-off - Submit new time-off request
 * - GET /api/v1/time-off/:id - Get time-off details
 * - PATCH /api/v1/time-off/:id - Update time-off request
 * - DELETE /api/v1/time-off/:id - Cancel time-off request
 * - GET /api/v1/time-off/conflicts - Check for scheduling conflicts
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

interface ListTimeOffQuery {
    userId?: string;
    status?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
    startDate?: string;
    endDate?: string;
    limit?: number;
    offset?: number;
}

interface SubmitTimeOffBody {
    userId: string;
    type: 'VACATION' | 'SICK' | 'PERSONAL' | 'BEREAVEMENT' | 'UNPAID';
    startDate: string;
    endDate: string;
    reason?: string;
}

interface UpdateTimeOffBody {
    startDate?: string;
    endDate?: string;
    reason?: string;
}

interface CheckConflictsQuery {
    userId: string;
    startDate: string;
    endDate: string;
    excludeId?: string;
}

export default async function timeOffRoutes(fastify: FastifyInstance) {
    /**
     * List time-off requests
     */
    fastify.get<{ Querystring: ListTimeOffQuery }>(
        '/time-off',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        userId: { type: 'string' },
                        status: { type: 'string', enum: ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'] },
                        startDate: { type: 'string', format: 'date' },
                        endDate: { type: 'string', format: 'date' },
                        limit: { type: 'number', default: 50 },
                        offset: { type: 'number', default: 0 },
                    },
                },
            },
        },
        async (request, reply) => {
            const { userId, status, startDate, endDate, limit = 50, offset = 0 } = request.query;

            const where: Prisma.TimeOffRequestWhereInput = {};
            if (userId) where.userId = userId;
            if (status) where.status = status;
            if (startDate || endDate) {
                const dateFilter: Prisma.DateTimeFilter = {};
                if (startDate) dateFilter.gte = new Date(startDate);
                if (endDate) dateFilter.lte = new Date(endDate);
                where.startDate = dateFilter;
            }

            const [requests, total] = await Promise.all([
                prisma.timeOffRequest.findMany({
                    where,
                    take: limit,
                    skip: offset,
                    orderBy: { createdAt: 'desc' },
                }),
                prisma.timeOffRequest.count({ where }),
            ]);

            return {
                data: requests,
                total,
                limit,
                offset,
            };
        }
    );

    /**
     * Submit new time-off request
     */
    fastify.post<{ Body: SubmitTimeOffBody }>(
        '/time-off',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['userId', 'type', 'startDate', 'endDate'],
                    properties: {
                        userId: { type: 'string' },
                        type: { type: 'string', enum: ['VACATION', 'SICK', 'PERSONAL', 'BEREAVEMENT', 'UNPAID'] },
                        startDate: { type: 'string', format: 'date' },
                        endDate: { type: 'string', format: 'date' },
                        reason: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const { userId, type, startDate, endDate, reason } = request.body;

            // Check for conflicts
            const conflicts = await prisma.timeOffRequest.findMany({
                where: {
                    userId,
                    status: { in: ['PENDING', 'APPROVED'] },
                    OR: [
                        {
                            AND: [
                                { startDate: { lte: new Date(startDate) } },
                                { endDate: { gte: new Date(startDate) } },
                            ],
                        },
                        {
                            AND: [
                                { startDate: { lte: new Date(endDate) } },
                                { endDate: { gte: new Date(endDate) } },
                            ],
                        },
                    ],
                },
            });

            if (conflicts.length > 0) {
                reply.code(409);
                return {
                    error: 'Time-off request conflicts with existing requests',
                    conflicts: conflicts.map((c) => ({
                        id: c.id,
                        startDate: c.startDate,
                        endDate: c.endDate,
                        status: c.status,
                    })),
                };
            }

            // Calculate days
            const start = new Date(startDate);
            const end = new Date(endDate);
            const days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;

            const timeOff = await prisma.timeOffRequest.create({
                data: {
                    userId,
                    type,
                    startDate: new Date(startDate),
                    endDate: new Date(endDate),
                    days,
                    status: 'PENDING',
                    reason,
                },
            });

            reply.code(201);
            return timeOff;
        }
    );

    /**
     * Get time-off details
     */
    fastify.get<{ Params: { id: string } }>(
        '/time-off/:id',
        {
            schema: {
            },
        },
        async (request, reply) => {
            const { id } = request.params;

            const timeOff = await prisma.timeOffRequest.findUnique({
                where: { id },
            });

            if (!timeOff) {
                reply.code(404);
                return { error: 'Time-off request not found' };
            }

            return timeOff;
        }
    );

    /**
     * Update time-off request
     */
    fastify.patch<{ Params: { id: string }; Body: UpdateTimeOffBody }>(
        '/time-off/:id',
        {
            schema: {
                body: {
                    type: 'object',
                    properties: {
                        startDate: { type: 'string', format: 'date' },
                        endDate: { type: 'string', format: 'date' },
                        reason: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;
            const updates = request.body;

            const existing = await prisma.timeOffRequest.findUnique({ where: { id } });

            if (!existing) {
                reply.code(404);
                return { error: 'Time-off request not found' };
            }

            if (existing.status !== 'PENDING') {
                reply.code(400);
                return { error: 'Can only update pending requests' };
            }

            const data: Prisma.TimeOffRequestUpdateInput = {};
            if (updates.startDate !== undefined) data.startDate = new Date(updates.startDate);
            if (updates.endDate !== undefined) data.endDate = new Date(updates.endDate);
            if (updates.reason !== undefined) data.reason = updates.reason;

            // Recalculate days if dates changed
            if (data.startDate || data.endDate) {
                const start = (data.startDate as Date | undefined) ?? existing.startDate;
                const end = (data.endDate as Date | undefined) ?? existing.endDate;
                data.days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            }

            const updated = await prisma.timeOffRequest.update({
                where: { id },
                data,
            });

            return updated;
        }
    );

    /**
     * Cancel time-off request
     */
    fastify.delete<{ Params: { id: string } }>(
        '/time-off/:id',
        {
            schema: {
            },
        },
        async (request, reply) => {
            const { id } = request.params;

            const existing = await prisma.timeOffRequest.findUnique({ where: { id } });

            if (!existing) {
                reply.code(404);
                return { error: 'Time-off request not found' };
            }

            if (existing.status === 'CANCELLED') {
                reply.code(400);
                return { error: 'Request already cancelled' };
            }

            await prisma.timeOffRequest.update({
                where: { id },
                data: { status: 'CANCELLED' },
            });

            return { success: true };
        }
    );

    /**
     * Check for scheduling conflicts
     */
    fastify.get<{ Querystring: CheckConflictsQuery }>(
        '/time-off/conflicts',
        {
            schema: {
                querystring: {
                    type: 'object',
                    required: ['userId', 'startDate', 'endDate'],
                    properties: {
                        userId: { type: 'string' },
                        startDate: { type: 'string', format: 'date' },
                        endDate: { type: 'string', format: 'date' },
                        excludeId: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const { userId, startDate, endDate, excludeId } = request.query;

            const where: Prisma.TimeOffRequestWhereInput = {
                userId,
                status: { in: ['PENDING', 'APPROVED'] },
                OR: [
                    {
                        AND: [
                            { startDate: { lte: new Date(startDate) } },
                            { endDate: { gte: new Date(startDate) } },
                        ],
                    },
                    {
                        AND: [
                            { startDate: { lte: new Date(endDate) } },
                            { endDate: { gte: new Date(endDate) } },
                        ],
                    },
                ],
            };

            if (excludeId) {
                where.NOT = { id: excludeId };
            }

            const conflicts = await prisma.timeOffRequest.findMany({ where });

            return {
                hasConflicts: conflicts.length > 0,
                conflicts: conflicts.map((c) => ({
                    id: c.id,
                    startDate: c.startDate,
                    endDate: c.endDate,
                    type: c.type,
                    status: c.status,
                })),
            };
        }
    );
}
