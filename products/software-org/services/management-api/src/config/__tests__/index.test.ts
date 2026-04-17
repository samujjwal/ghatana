import { afterEach, describe, expect, it, vi } from 'vitest';

const baseEnv: NodeJS.ProcessEnv = {
    NODE_ENV: 'development',
    PORT: '3001',
    HOST: '0.0.0.0',
    DATABASE_URL: 'postgresql://localhost:5432/software_org_dev',
    JWT_SECRET: 'local-test-secret',
    JWT_EXPIRES_IN: '7d',
    CORS_ORIGIN: 'http://localhost:3000',
    LOG_LEVEL: 'info',
    PROMETHEUS_PORT: '9090',
};

describe('loadAppConfig', () => {
    afterEach(() => {
        vi.resetModules();
        vi.unstubAllEnvs();
    });

    async function importConfigModule() {
        for (const [key, value] of Object.entries(baseEnv)) {
            vi.stubEnv(key, value ?? '');
        }
        return import('../index.js');
    }

    it('loads valid environment variables into typed config', async () => {
        const { loadAppConfig } = await importConfigModule();

        const config = loadAppConfig(baseEnv);

        expect(config.env).toBe('development');
        expect(config.isDevelopment).toBe(true);
        expect(config.server.port).toBe(3001);
        expect(config.database.url).toBe('postgresql://localhost:5432/software_org_dev');
    });

    it('fails fast when DATABASE_URL is missing', async () => {
        const { loadAppConfig } = await importConfigModule();

        expect(() => loadAppConfig({ ...baseEnv, DATABASE_URL: undefined })).toThrow(
            'Invalid Software-Org management-api environment: DATABASE_URL: Invalid input: expected string, received undefined'
        );
    });

    it('rejects the development JWT secret in production', async () => {
        const { loadAppConfig } = await importConfigModule();

        expect(() => loadAppConfig({
            ...baseEnv,
            NODE_ENV: 'production',
            JWT_SECRET: 'dev-secret-change-in-production',
        })).toThrow('JWT_SECRET must not use the development default in production');
    });
});