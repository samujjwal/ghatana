/**
 * Tests for Polyglot Fixture Node Service
 * 
 * @doc.type module
 * @doc.purpose Tests for Node.js service surface
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import request from 'supertest';
import express from 'express';

const app = express();

app.get('/health', (_req, res) => {
  res.json({
    status: 'UP',
    service: 'node-service',
    version: '1.0.0'
  });
});

app.get('/api/ping', (_req, res) => {
  res.json({
    message: 'pong',
    timestamp: Date.now()
  });
});

describe('Node Service', () => {
  it('health endpoint returns UP', async () => {
    const response = await request(app).get('/health');
    assert.strictEqual(response.status, 200);
    assert.strictEqual(response.body.status, 'UP');
    assert.strictEqual(response.body.service, 'node-service');
  });

  it('ping endpoint returns pong', async () => {
    const response = await request(app).get('/api/ping');
    assert.strictEqual(response.status, 200);
    assert.strictEqual(response.body.message, 'pong');
    assert.ok(response.body.timestamp);
  });
});
