import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import personaRoutes from '../personas.js';

const { authenticate, mockPersonaService, broadcastPersonaUpdate, broadcastPersonaDelete } = vi.hoisted(() => ({
    authenticate: vi.fn(async (request: { userId?: string }) => {
        request.userId = 'user-1';
    }),
    mockPersonaService: {
        verifyWorkspaceAccess: vi.fn(),
        getPersonaPreference: vi.fn(),
        upsertPersonaPreference: vi.fn(),
        deletePersonaPreference: vi.fn(),
    },
    broadcastPersonaUpdate: vi.fn(),
    broadcastPersonaDelete: vi.fn(),
}));

vi.mock('../../middleware/auth.js', () => ({ authenticate }));
vi.mock('../../services/persona.service.js', () => mockPersonaService);
vi.mock('../../websocket/persona-sync.js', () => ({
    broadcastPersonaUpdate,
    broadcastPersonaDelete,
}));

describe('Persona Routes Integration Tests', () => {
    let fastify: FastifyInstance;
    const workspaceId = '123e4567-e89b-12d3-a456-426614174000';

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(personaRoutes, { prefix: '/api/personas' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should reject reads when the user lacks workspace access', async () => {
        mockPersonaService.verifyWorkspaceAccess.mockResolvedValue(false);

        const response = await fastify.inject({
            method: 'GET',
            url: `/api/personas/${workspaceId}`,
        });

        expect(response.statusCode).toBe(403);
        expect(response.json()).toEqual({
            success: false,
            error: 'Access denied to workspace',
        });
    });

    it('should update persona preferences and broadcast changes', async () => {
        mockPersonaService.verifyWorkspaceAccess.mockResolvedValue(true);
        mockPersonaService.upsertPersonaPreference.mockResolvedValue({
            id: 'pref-1',
            userId: 'user-1',
            workspaceId,
            activeRoles: ['architect'],
            preferences: { features: { canvasAi: true } },
            createdAt: '2026-04-16T10:00:00.000Z',
            updatedAt: '2026-04-16T11:00:00.000Z',
        });

        const response = await fastify.inject({
            method: 'PUT',
            url: `/api/personas/${workspaceId}`,
            payload: {
                activeRoles: ['architect'],
                preferences: { features: { canvasAi: true } },
            },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            data: {
                id: 'pref-1',
                userId: 'user-1',
                workspaceId,
                activeRoles: ['architect'],
                preferences: { features: { canvasAi: true } },
                createdAt: '2026-04-16T10:00:00.000Z',
                updatedAt: '2026-04-16T11:00:00.000Z',
            },
        });
        expect(broadcastPersonaUpdate).toHaveBeenCalledTimes(1);
    });

    it('should return 404 when deleting a missing preference', async () => {
        mockPersonaService.verifyWorkspaceAccess.mockResolvedValue(true);
        mockPersonaService.deletePersonaPreference.mockResolvedValue(false);

        const response = await fastify.inject({
            method: 'DELETE',
            url: `/api/personas/${workspaceId}`,
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            success: false,
            error: 'Persona preference not found',
        });
        expect(broadcastPersonaDelete).not.toHaveBeenCalled();
    });
});