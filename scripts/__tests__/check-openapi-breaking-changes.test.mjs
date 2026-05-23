import test from 'node:test';
import assert from 'node:assert/strict';

import { detectRemovedOperations, extractPathMethodKeys } from '../check-openapi-breaking-changes.mjs';

test('extractPathMethodKeys parses YAML route-method pairs', () => {
  const yaml = [
    'openapi: 3.0.3',
    'paths:',
    '  /v1/agents:',
    '    get:',
    '      responses:',
    "        '200':",
    '          description: ok',
    '    post:',
    '      responses:',
    "        '201':",
    '          description: created',
    '  /v1/agents/{agentId}:',
    '    delete:',
    '      responses:',
    "        '204':",
    '          description: removed',
    '',
  ].join('\n');

  const keys = extractPathMethodKeys(yaml);

  assert.equal(keys.has('GET /v1/agents'), true);
  assert.equal(keys.has('POST /v1/agents'), true);
  assert.equal(keys.has('DELETE /v1/agents/{agentId}'), true);
  assert.equal(keys.size, 3);
});

test('extractPathMethodKeys parses JSON route-method pairs', () => {
  const json = JSON.stringify({
    openapi: '3.0.3',
    paths: {
      '/v1/collections': {
        get: { responses: { '200': { description: 'ok' } } },
        post: { responses: { '201': { description: 'created' } } },
      },
      '/v1/collections/{id}': {
        patch: { responses: { '200': { description: 'updated' } } },
      },
    },
  }, null, 2);

  const keys = extractPathMethodKeys(json);

  assert.equal(keys.has('GET /v1/collections'), true);
  assert.equal(keys.has('POST /v1/collections'), true);
  assert.equal(keys.has('PATCH /v1/collections/{id}'), true);
  assert.equal(keys.size, 3);
});

test('detectRemovedOperations separates waived and unwaived removals', () => {
  const baselineKeys = new Set([
    'GET /v1/reports',
    'POST /v1/reports',
    'DELETE /v1/reports/{id}',
  ]);
  const currentKeys = new Set([
    'GET /v1/reports',
  ]);
  const waivers = new Set([
    'data-cloud:POST /v1/reports',
  ]);

  const result = detectRemovedOperations({
    baselineKeys,
    currentKeys,
    waivers,
    specId: 'data-cloud',
  });

  assert.deepEqual(result.waivedRemoved, ['POST /v1/reports']);
  assert.deepEqual(result.unwaivedRemoved, ['DELETE /v1/reports/{id}']);
  assert.deepEqual(result.removed, ['DELETE /v1/reports/{id}', 'POST /v1/reports']);
});
