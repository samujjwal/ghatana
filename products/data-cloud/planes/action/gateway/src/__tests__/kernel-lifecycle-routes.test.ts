import { createHmac } from 'node:crypto';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import type { FastifyInstance } from 'fastify';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildApp, GatewayMetrics, type KernelLifecycleApiPort } from '../app.js';

const TEST_SECRET = 'kernel-lifecycle-routes-test-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(payload: Record<string, unknown> = {}): string {
  return makeJwt({ sub: 'studio-user-1', tenantId: 'tenant-1', exp: Math.floor(Date.now() / 1000) + 3600, ...payload });
}

describe('/api/kernel lifecycle routes', () => {
  let app: FastifyInstance | undefined;
  let backend: ReturnType<typeof createServer> | undefined;

  afterEach(async () => {
    if (app !== undefined) {
      await app.close();
      app = undefined;
    }
    if (backend !== undefined) {
      await new Promise<void>((resolve) => {
        backend?.close(() => resolve());
      });
      backend = undefined;
    }
  });

  it('fails closed when Kernel lifecycle API is absent', async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['http://localhost:5173'],
    });
    await app.ready();

    const response = await app.inject({
      method: 'GET',
      url: '/api/kernel/product-units',
      headers: authHeaders('corr-kernel-absent'),
    });

    expect(response.statusCode).toBe(503);
    expect(response.json().message).toContain('Kernel lifecycle API requires');
    expect(response.headers['x-correlation-id']).toBe('corr-kernel-absent');
  });

  it('routes listProductUnits through injected handler and does not proxy to backendUrl', async () => {
    let backendCalls = 0;
    backend = createServer((_req: IncomingMessage, res: ServerResponse) => {
      backendCalls += 1;
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: 'backend should not receive kernel route' }));
    });
    await new Promise<void>((resolve) => {
      backend?.listen(0, '127.0.0.1', resolve);
    });
    const backendUrl = `http://127.0.0.1:${(backend.address() as AddressInfo).port}`;
    const metrics = new GatewayMetrics();
    const kernelLifecycleApi = createKernelLifecycleApi();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl,
      allowedOrigins: ['http://localhost:5173'],
      metrics,
      kernelLifecycleApi,
    });
    await app.ready();

    const response = await app.inject({
      method: 'GET',
      url: '/api/kernel/product-units',
      headers: authHeaders('corr-kernel-list'),
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual([{ id: 'digital-marketing' }]);
    expect(kernelLifecycleApi.listProductUnits).toHaveBeenCalledWith(
      expect.objectContaining({
        headers: expect.objectContaining({
          'x-correlation-id': 'corr-kernel-list',
          'x-ghatana-tenant-id': 'tenant-1',
        }),
      }),
    );
    expect(backendCalls).toBe(0);
    expect(metrics.snapshot().kernelLifecycleRequestsByOperation['listProductUnits:200']).toBe(1);
  });

  it('propagates tenant and correlation headers to execute handler', async () => {
    const kernelLifecycleApi = createKernelLifecycleApi();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['http://localhost:5173'],
      kernelLifecycleApi,
    });
    await app.ready();

    const response = await app.inject({
      method: 'POST',
      url: '/api/kernel/product-units/digital-marketing/lifecycle/execute',
      headers: authHeaders('corr-kernel-execute'),
      payload: { phase: 'build', dryRun: true },
    });

    expect(response.statusCode).toBe(200);
    expect(kernelLifecycleApi.executeLifecyclePhase).toHaveBeenCalledWith(
      expect.objectContaining({
        params: { productUnitId: 'digital-marketing' },
        body: { phase: 'build', dryRun: true },
        headers: expect.objectContaining({
          'x-correlation-id': 'corr-kernel-execute',
          'x-ghatana-workspace-id': 'workspace-1',
        }),
      }),
    );
  });

  it('rejects tenant mismatch before invoking Kernel handler', async () => {
    const kernelLifecycleApi = createKernelLifecycleApi();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['http://localhost:5173'],
      kernelLifecycleApi,
    });
    await app.ready();

    const response = await app.inject({
      method: 'GET',
      url: '/api/kernel/product-units',
      headers: {
        ...authHeaders('corr-kernel-mismatch'),
        'x-ghatana-tenant-id': 'tenant-2',
      },
    });

    expect(response.statusCode).toBe(403);
    expect(kernelLifecycleApi.listProductUnits).not.toHaveBeenCalled();
  });

  it('returns safe error when injected handler fails', async () => {
    const kernelLifecycleApi = createKernelLifecycleApi({
      listProductUnits: vi.fn().mockRejectedValue(new Error('internal provider detail')),
    });
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['http://localhost:5173'],
      kernelLifecycleApi,
    });
    await app.ready();

    const response = await app.inject({
      method: 'GET',
      url: '/api/kernel/product-units',
      headers: authHeaders('corr-kernel-failure'),
    });

    expect(response.statusCode).toBe(500);
    expect(response.json()).toEqual({
      error: 'Internal Server Error',
      message: 'Kernel lifecycle API handler failed',
      correlationId: 'corr-kernel-failure',
    });
  });
});

function authHeaders(correlationId: string): Record<string, string> {
  return {
    authorization: `Bearer ${validToken()}`,
    'x-correlation-id': correlationId,
    'x-ghatana-tenant-id': 'tenant-1',
    'x-ghatana-workspace-id': 'workspace-1',
    'x-ghatana-project-id': 'project-1',
  };
}

function createKernelLifecycleApi(overrides: Partial<KernelLifecycleApiPort> = {}): KernelLifecycleApiPort {
  const response = { statusCode: 200, headers: { 'x-correlation-id': 'corr-handler' }, body: { ok: true } };
  return {
    listProductUnits: vi.fn().mockResolvedValue({ ...response, body: [{ id: 'digital-marketing' }] }),
    getProductUnit: vi.fn().mockResolvedValue(response),
    createLifecyclePlan: vi.fn().mockResolvedValue({ ...response, statusCode: 201 }),
    executeLifecyclePhase: vi.fn().mockResolvedValue(response),
    listLifecycleRuns: vi.fn().mockResolvedValue(response),
    getLifecycleRun: vi.fn().mockResolvedValue(response),
    getGateResultManifest: vi.fn().mockResolvedValue(response),
    getArtifactManifest: vi.fn().mockResolvedValue(response),
    getDeploymentManifest: vi.fn().mockResolvedValue(response),
    getVerifyHealthReport: vi.fn().mockResolvedValue(response),
    requestApproval: vi.fn().mockResolvedValue({ ...response, statusCode: 201 }),
    submitApprovalDecision: vi.fn().mockResolvedValue(response),
    ...overrides,
  };
}
