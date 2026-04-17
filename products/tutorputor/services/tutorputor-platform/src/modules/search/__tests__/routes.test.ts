import Fastify from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { searchRoutes } from '../routes.js';

describe('searchRoutes', () => {
  const service = {
    search: vi.fn(),
    autocomplete: vi.fn(),
    getPopularSearches: vi.fn(),
    getSimilar: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    app = Fastify();
    await app.register(searchRoutes, { service: service as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it('rejects missing search query', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/',
      headers: { 'x-tenant-id': 'tenant-1' },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: 'Validation Error',
      message: 'Invalid input: expected string, received undefined',
    });
  });

  it('rejects invalid type filters through zod validation', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/?q=motion&limit=0',
      headers: { 'x-tenant-id': 'tenant-1' },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: 'Validation Error',
    });
    expect(service.search).not.toHaveBeenCalled();
  });

  it('passes parsed filters to the service for valid searches', async () => {
    service.search.mockResolvedValue({ results: [], total: 0, facets: {}, took: 1 });

    const response = await app.inject({
      method: 'GET',
      url: '/?q=motion&type=module,thread,unknown&category=physics,biology&limit=5&offset=2&free=true',
      headers: { 'x-tenant-id': 'tenant-1' },
    });

    expect(response.statusCode).toBe(200);
    expect(service.search).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: 'tenant-1',
        query: 'motion',
        limit: 5,
        offset: 2,
        filters: expect.objectContaining({
          type: ['module', 'thread'],
          category: ['physics', 'biology'],
          price: expect.objectContaining({ free: true }),
        }),
      }),
    );
  });

  it('rejects autocomplete requests without q', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/autocomplete',
      headers: { 'x-tenant-id': 'tenant-1' },
    });

    expect(response.statusCode).toBe(400);
    expect(service.autocomplete).not.toHaveBeenCalled();
  });

  it('rejects invalid similar-module limits', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/similar/module-1?limit=-1',
      headers: { 'x-tenant-id': 'tenant-1' },
    });

    expect(response.statusCode).toBe(400);
    expect(service.getSimilar).not.toHaveBeenCalled();
  });
});