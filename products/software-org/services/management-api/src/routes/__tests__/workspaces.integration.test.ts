import Fastify, { FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import workspaceRoutes from '../workspaces.js';

const { authenticate, mockWorkspaceService } = vi.hoisted(() => ({
    authenticate: vi.fn(async (request: { userId?: string }) => {
        request.userId = 'admin-user';
    }),
    mockWorkspaceService: {
        verifyWorkspaceAdmin: vi.fn(),
        getWorkspaceOverride: vi.fn(),
        upsertWorkspaceOverride: vi.fn(),
        deleteWorkspaceOverride: vi.fn(),
    },
}));

vi.mock('../../middleware/auth.js', () => ({
    authenticate,
}));

vi.mock('../../services/workspace.service.js', () => mockWorkspaceService);

describe('Workspace Routes Integration Tests', () => {
    let fastify: FastifyInstance;
    const workspaceId = '123e4567-e89b-12d3-a456-426614174000';

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(workspaceRoutes, { prefix: '/api/workspaces' });
        vi.clearAllMocks();
        authenticate.mockImplementation(async (request: { userId?: string }) => {
            request.userId = 'admin-user';
        });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should return workspace overrides for admins', async () => {
        mockWorkspaceService.verifyWorkspaceAdmin.mockResolvedValue(true);
        mockWorkspaceService.getWorkspaceOverride.mockResolvedValue({
            id: 'override-1',
            workspaceId,
            overrides: { disabledPlugins: ['deployments'] },
            createdAt: '2026-04-16T00:00:00.000Z',
            updatedAt: '2026-04-16T00:00:00.000Z',
        });

        const response = await fastify.inject({
            method: 'GET',
            url: `/api/workspaces/${workspaceId}/overrides`,
            headers: { authorization: 'Bearer token' },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            data: {
                id: 'override-1',
                workspaceId,
                overrides: { disabledPlugins: ['deployments'] },
                createdAt: '2026-04-16T00:00:00.000Z',
                updatedAt: '2026-04-16T00:00:00.000Z',
            },
        });
    });

    it('should reject override updates for non-admin users', async () => {
        mockWorkspaceService.verifyWorkspaceAdmin.mockResolvedValue(false);

        const response = await fastify.inject({
            method: 'POST',
            url: `/api/workspaces/${workspaceId}/overrides`,
            headers: { authorization: 'Bearer token' },
            payload: {
                overrides: {
                    featureFlags: { approvals: true },
                },
            },
        });

        expect(response.statusCode).toBe(403);
        expect(response.json()).toEqual({
            success: false,
            error: 'Admin access required',
        });
    });

    it('should return 404 when deleting missing overrides', async () => {
        mockWorkspaceService.verifyWorkspaceAdmin.mockResolvedValue(true);
        mockWorkspaceService.deleteWorkspaceOverride.mockResolvedValue(false);

        const response = await fastify.inject({
            method: 'DELETE',
            url: `/api/workspaces/${workspaceId}/overrides`,
            headers: { authorization: 'Bearer token' },
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            success: false,
            error: 'Workspace overrides not found',
        });
    });
});