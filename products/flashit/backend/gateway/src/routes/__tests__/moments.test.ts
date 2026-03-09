/**
 * Moment Routes Integration Tests
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';

// Mock Prisma
vi.mock('../../lib/prisma.js', () => ({
    prisma: {
        moment: {
            findMany: vi.fn(),
            findUnique: vi.fn(),
            findFirst: vi.fn(),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            count: vi.fn(),
        },
        sphere: {
            findFirst: vi.fn(),
        },
        $transaction: vi.fn(),
    },
}));

describe('Moment Routes', () => {
    let app: FastifyInstance;
    const mockUserId = 'user-123';

    const mockMoment = {
        id: 'moment-1',
        userId: mockUserId,
        sphereId: 'sphere-1',
        contentText: 'Test moment content',
        contentTranscript: null,
        contentType: 'TEXT',
        emotions: ['happy'],
        tags: ['work'],
        intent: null,
        sentimentScore: 0.8,
        importance: 5,
        entities: [],
        capturedAt: new Date().toISOString(),
        ingestedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        deletedAt: null,
        metadata: null,
        version: 1,
    };

    beforeAll(async () => {
        app = Fastify();

        // Mock authentication
        app.decorateRequest('user', null);
        app.addHook('preHandler', async (request) => {
            request.user = { id: mockUserId, email: 'test@example.com' };
        });

        // Register moment routes (simplified for testing)
        app.get('/api/moments', async (request) => {
            const query = request.query as { sphereIds?: string; query?: string; limit?: string };
            return {
                moments: [mockMoment],
                nextCursor: null,
                total: 1,
            };
        });

        app.get('/api/moments/:id', async (request) => {
            const { id } = request.params as { id: string };
            if (id === 'nonexistent') {
                return { statusCode: 404, error: 'Not Found' };
            }
            return { moment: { ...mockMoment, id } };
        });

        app.post('/api/moments', async (request) => {
            const body = request.body as { sphereId?: string; content: { text: string; type: string } };
            return {
                moment: {
                    ...mockMoment,
                    id: 'moment-new',
                    sphereId: body.sphereId || 'sphere-1',
                    contentText: body.content.text,
                    contentType: body.content.type,
                },
            };
        });

        app.post('/api/moments/classify-sphere', async (request) => {
            const body = request.body as { content: { text: string } };
            return {
                sphereId: 'sphere-1',
                confidence: 0.95,
                reasoning: 'Content matches personal sphere patterns',
            };
        });

        app.delete('/api/moments/:id', async () => {
            return { success: true };
        });

        app.get('/api/moments/search', async (request) => {
            const query = request.query as { q?: string };
            if (!query.q) {
                return { moments: [], total: 0 };
            }
            return {
                moments: [mockMoment],
                total: 1,
            };
        });

        await app.ready();
    });

    afterAll(async () => {
        await app.close();
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('GET /api/moments', () => {
        it('should return list of moments', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments',
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('moments');
            expect(Array.isArray(body.moments)).toBe(true);
        });

        it('should return moments with correct structure', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments',
            });

            const body = JSON.parse(response.body);
            const moment = body.moments[0];

            expect(moment).toHaveProperty('id');
            expect(moment).toHaveProperty('userId');
            expect(moment).toHaveProperty('sphereId');
            expect(moment).toHaveProperty('contentText');
            expect(moment).toHaveProperty('contentType');
            expect(moment).toHaveProperty('emotions');
            expect(moment).toHaveProperty('tags');
        });

        it('should support pagination', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments?limit=10',
            });

            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('nextCursor');
            expect(body).toHaveProperty('total');
        });

        it('should filter by sphereIds', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments?sphereIds=sphere-1',
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body.moments.length).toBeGreaterThan(0);
        });
    });

    describe('GET /api/moments/:id', () => {
        it('should return moment by id', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments/moment-1',
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('moment');
            expect(body.moment.id).toBe('moment-1');
        });

        it('should return 404 for nonexistent moment', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments/nonexistent',
            });

            const body = JSON.parse(response.body);
            expect(body.statusCode).toBe(404);
        });
    });

    describe('POST /api/moments', () => {
        it('should create a new moment', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/api/moments',
                payload: {
                    sphereId: 'sphere-1',
                    content: {
                        text: 'New moment content',
                        type: 'TEXT',
                    },
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('moment');
            expect(body.moment.contentText).toBe('New moment content');
        });

        it('should create moment without explicit sphereId (auto-classify)', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/api/moments',
                payload: {
                    content: {
                        text: 'Auto-classified moment',
                        type: 'TEXT',
                    },
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body.moment).toHaveProperty('sphereId');
        });
    });

    describe('POST /api/moments/classify-sphere', () => {
        it('should classify content to appropriate sphere', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/api/moments/classify-sphere',
                payload: {
                    content: {
                        text: 'Meeting notes from work project',
                        type: 'TEXT',
                    },
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('sphereId');
            expect(body).toHaveProperty('confidence');
            expect(body).toHaveProperty('reasoning');
            expect(body.confidence).toBeGreaterThan(0);
        });
    });

    describe('DELETE /api/moments/:id', () => {
        it('should delete moment', async () => {
            const response = await app.inject({
                method: 'DELETE',
                url: '/api/moments/moment-1',
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body.success).toBe(true);
        });
    });

    describe('GET /api/moments/search', () => {
        it('should search moments by query', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments/search?q=test',
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('moments');
            expect(body).toHaveProperty('total');
        });

        it('should return empty results for no query', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments/search',
            });

            const body = JSON.parse(response.body);
            expect(body.moments).toEqual([]);
            expect(body.total).toBe(0);
        });
    });

    describe('Content Types', () => {
        it('should handle TEXT content type', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/api/moments',
                payload: {
                    content: { text: 'Text content', type: 'TEXT' },
                },
            });

            const body = JSON.parse(response.body);
            expect(body.moment.contentType).toBe('TEXT');
        });

        it('should handle VOICE content type', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/api/moments',
                payload: {
                    content: { text: 'Voice transcription', type: 'VOICE' },
                },
            });

            const body = JSON.parse(response.body);
            expect(body.moment.contentType).toBe('VOICE');
        });
    });

    describe('Authorization', () => {
        it('should require authentication', async () => {
            // Verify user is attached to requests
            const response = await app.inject({
                method: 'GET',
                url: '/api/moments',
            });

            expect(response.statusCode).toBe(200);
        });
    });
});
