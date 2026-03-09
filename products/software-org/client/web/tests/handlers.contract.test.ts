import { setupServer } from 'msw/node';
import { handlers } from '../src/mocks/handlers';

// Use global fetch (Node 18+). Vitest runs in Node environment by default.
const server = setupServer(...handlers as any);

describe('MSW handler contract tests', () => {
    beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
    afterAll(() => server.close());
    afterEach(() => server.resetHandlers());

    test('GET /api/v1/models returns array of models with id and name', async () => {
        const res = await fetch('http://localhost/api/v1/models');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(Array.isArray(body)).toBe(true);
        expect(body.length).toBeGreaterThan(0);
        const item = body[0];
        expect(typeof item.id).toBe('string');
        expect(typeof item.name).toBe('string');
    });

    test('GET /api/v1/tenants/default/metrics/health returns tenantId and healthy flag', async () => {
        const res = await fetch('http://localhost/api/v1/tenants/default/metrics/health');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(typeof body.tenantId).toBe('string');
        expect(typeof body.healthy).toBe('boolean');
    });

    test('GET /api/v1/tenants/default/workflows returns array with id, name, createdAt', async () => {
        const res = await fetch('http://localhost/api/v1/tenants/default/workflows');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(Array.isArray(body)).toBe(true);
        expect(body.length).toBeGreaterThan(0);
        const w = body[0];
        expect(typeof w.id).toBe('string');
        expect(typeof w.name).toBe('string');
        expect(typeof w.createdAt).toBe('string');
    });

    test('GET /api/v1/metrics returns object with timestamp, value, timeRange', async () => {
        const res = await fetch('http://localhost/api/v1/metrics');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(body).toBeTruthy();
        expect(typeof body.timestamp).toBe('string');
        expect(typeof body.value === 'number' || typeof body.value === 'number').toBe(true);
        expect(typeof body.timeRange).toBe('string');
    });

    test('GET /api/v1/tenants/default/anomalies returns array of anomalies with id, metric, value', async () => {
        const res = await fetch('http://localhost/api/v1/tenants/default/anomalies');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(Array.isArray(body)).toBe(true);
        const a = body[0];
        expect(typeof a.id).toBe('string');
        expect(typeof a.metric).toBe('string');
        expect(typeof a.value === 'number' || typeof a.value === 'number').toBe(true);
        expect(typeof a.detectedAt === 'string' || typeof a.timestamp === 'string').toBe(true);
    });

    test('GET /api/v1/tenants/default/training-jobs returns array with id, modelId, status', async () => {
        const res = await fetch('http://localhost/api/v1/tenants/default/training-jobs');
        expect(res.ok).toBe(true);
        const body = await res.json();
        expect(Array.isArray(body)).toBe(true);
        const job = body[0];
        expect(typeof job.id).toBe('string');
        expect(typeof job.modelId).toBe('string');
        expect(typeof job.status).toBe('string');
    });
});
