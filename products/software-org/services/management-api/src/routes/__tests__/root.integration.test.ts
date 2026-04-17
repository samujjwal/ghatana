import Fastify, { FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { rootRoutes } from '../root.js';

const { mockRootService } = vi.hoisted(() => ({
    mockRootService: {
        getAllTenants: vi.fn(),
        getTenantDetails: vi.fn(),
        getPlatformHealth: vi.fn(),
        getAggregatedAlerts: vi.fn(),
        searchGlobalUsers: vi.fn(),
        suspendUser: vi.fn(),
    },
}));

vi.mock('../../services/root.service.js', () => ({
    rootService: mockRootService,
}));

describe('Root Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(rootRoutes, { prefix: '/api/v1/root' });
        vi.clearAllMocks();
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should return tenant summaries', async () => {
        mockRootService.getAllTenants.mockResolvedValue([
            { id: 'tenant-1', key: 'acme', name: 'Acme' },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/root/tenants',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([{ id: 'tenant-1', key: 'acme', name: 'Acme' }]);
        expect(mockRootService.getAllTenants).toHaveBeenCalledTimes(1);
    });

    it('should return 404 when tenant is missing', async () => {
        mockRootService.getTenantDetails.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/root/tenants/missing-tenant',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Tenant not found' });
    });

    it('should forward aggregated alert filters', async () => {
        mockRootService.getAggregatedAlerts.mockResolvedValue({
            summary: { totalAlerts: 2 },
            byTenant: [],
            recentAlerts: [],
        });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/root/alerts/aggregated?severity=critical&status=active&limit=5',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            summary: { totalAlerts: 2 },
            byTenant: [],
            recentAlerts: [],
        });
        expect(mockRootService.getAggregatedAlerts).toHaveBeenCalledWith({
            severity: 'critical',
            status: 'active',
            limit: 5,
        });
    });

    it('should suspend a user with the provided reason', async () => {
        mockRootService.suspendUser.mockResolvedValue({
            success: true,
            userId: 'user-42',
            reason: 'Policy violation',
            status: 'suspended',
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/root/users/user-42/suspend',
            payload: { reason: 'Policy violation' },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            userId: 'user-42',
            reason: 'Policy violation',
            status: 'suspended',
        });
        expect(mockRootService.suspendUser).toHaveBeenCalledWith('user-42', 'Policy violation');
    });
});