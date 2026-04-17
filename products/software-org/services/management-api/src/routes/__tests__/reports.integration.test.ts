import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import reportRoutes from '../reports.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        reportSchedule: {
            findMany: vi.fn(),
            create: vi.fn(),
        },
        reportDefinition: {
            findFirst: vi.fn(),
        },
        reportRun: {
            create: vi.fn(),
        },
        department: {
            findFirst: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Report Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(reportRoutes, { prefix: '/api/v1/reports' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list report schedules', async () => {
        mockPrisma.reportSchedule.findMany.mockResolvedValue([
            {
                id: 'schedule-1',
                frequency: 'weekly',
                dayOfWeek: 'monday',
                time: '09:00',
                recipients: ['ops@example.com'],
                formats: ['pdf'],
                enabled: true,
                nextRun: new Date('2026-04-23T09:00:00.000Z'),
                createdAt: new Date('2026-04-16T09:00:00.000Z'),
                report: { key: 'delivery-summary' },
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/reports/schedules',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            schedules: [
                {
                    id: 'schedule-1',
                    reportId: 'delivery-summary',
                    frequency: 'weekly',
                    dayOfWeek: 'monday',
                    time: '09:00',
                    recipients: ['ops@example.com'],
                    formats: ['pdf'],
                    enabled: true,
                    nextRun: '2026-04-23T09:00:00.000Z',
                },
            ],
        });
    });

    it('should reject schedules for unknown reports', async () => {
        mockPrisma.reportDefinition.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/reports/missing/schedule',
            payload: {
                frequency: 'weekly',
            },
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Report not found' });
    });

    it('should create manual report runs', async () => {
        mockPrisma.reportDefinition.findFirst.mockResolvedValue({
            id: 'report-1',
            key: 'delivery-summary',
            name: 'Delivery Summary',
        });
        mockPrisma.reportRun.create.mockResolvedValue({
            id: 'run-1',
            status: 'completed',
            runAt: new Date('2026-04-16T12:00:00.000Z'),
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/reports/delivery-summary/run',
        });

        expect(response.statusCode).toBe(201);
        expect(response.json()).toEqual({
            id: 'run-1',
            reportId: 'delivery-summary',
            status: 'completed',
            runAt: '2026-04-16T12:00:00.000Z',
        });
    });
});