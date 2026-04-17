import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import configRoutes from '../config-api.js';

const { clearCache, loadOrgConfig, loadServiceConfig, mockSyncService } = vi.hoisted(() => ({
    clearCache: vi.fn(),
    loadOrgConfig: vi.fn(),
    loadServiceConfig: vi.fn(),
    mockSyncService: {
        syncFromConfig: vi.fn(),
        exportToConfig: vi.fn(),
    },
}));

vi.mock('../../services/config-loader.service.js', () => ({
    getConfigLoader: () => ({
        clearCache,
        loadOrgConfig,
        loadAllDepartments: vi.fn(),
        loadDepartmentConfig: vi.fn(),
        loadAllPersonas: vi.fn(),
        loadPersonaConfig: vi.fn(),
        loadPhases: vi.fn(),
        loadStageMappings: vi.fn(),
        loadAllServices: vi.fn(),
        loadServiceConfig,
        loadAllIntegrations: vi.fn(),
        loadIntegrationConfig: vi.fn(),
    }),
}));

vi.mock('../../services/config-sync.service.js', () => ({
    ConfigSyncService: {
        getInstance: () => mockSyncService,
    },
}));

describe('Config API Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(configRoutes, { prefix: '/api/v1/config' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should load the full organization config', async () => {
        loadOrgConfig.mockResolvedValue({ name: 'Software Org', version: 1 });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/config',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: { name: 'Software Org', version: 1 },
            success: true,
            message: undefined,
            timestamp: expect.any(String),
        });
    });

    it('should reject exports without a path', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/config/export',
            payload: {},
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            data: null,
            success: false,
            message: 'Export path is required',
            timestamp: expect.any(String),
        });
        expect(mockSyncService.exportToConfig).not.toHaveBeenCalled();
    });

    it('should return 404 when a service config is missing', async () => {
        loadServiceConfig.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/config/services/missing-service',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            data: null,
            success: false,
            message: 'Service not found: missing-service',
            timestamp: expect.any(String),
        });
    });
});