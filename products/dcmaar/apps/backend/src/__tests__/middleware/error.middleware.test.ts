/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * Error Middleware Tests
 */

import express from 'express';
import request from 'supertest';
import { errorHandler, HttpError } from '../../middleware/error.middleware';

describe('errorHandler middleware', () => {
  const originalEnv = process.env.NODE_ENV;

  afterEach(() => {
    process.env.NODE_ENV = originalEnv;
  });

  it('sanitizes generic errors in production mode', async () => {
    process.env.NODE_ENV = 'production';
    const app = express();
    app.get('/fail', () => {
      throw new Error('Sensitive error');
    });
    app.use(errorHandler);

    const response = await request(app).get('/fail').expect(500);
    expect(response.body.error).toBe('Internal server error');
    expect(response.body.code).toBeUndefined();
  });

  it('includes stack when in development', async () => {
    process.env.NODE_ENV = 'development';
    const app = express();
    app.get('/dev-error', () => {
      throw new Error('Dev error');
    });
    app.use(errorHandler);

    const response = await request(app).get('/dev-error').expect(500);
    expect(response.body.error).toBe('Dev error');
    expect(response.body.stack).toBeDefined();
  });

  it('respects exposed http errors with custom status and codes', async () => {
    const app = express();
    app.get('/forbidden', () => {
      const err: HttpError = new Error('Forbidden access');
      err.status = 403;
      err.code = 'forbidden';
      err.expose = true;
      throw err;
    });
    app.use(errorHandler);

    const response = await request(app).get('/forbidden').expect(403);
    expect(response.body.error).toBe('Forbidden access');
    expect(response.body.code).toBe('forbidden');
  });
});

