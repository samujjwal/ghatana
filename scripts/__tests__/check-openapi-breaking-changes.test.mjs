import test from 'node:test';
import assert from 'node:assert/strict';

import { detectRemovedOperations, extractPathMethodKeys, detectSchemaBreakingChanges } from '../check-openapi-breaking-changes.mjs';

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

test('detectSchemaBreakingChanges detects removed response fields', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      id: { type: 'string' },
                      name: { type: 'string' },
                      email: { type: 'string' },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      id: { type: 'string' },
                      name: { type: 'string' },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'response_field_removed' && c.detail.includes('email')));
});

test('detectSchemaBreakingChanges detects newly required fields', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        post: {
          requestBody: {
            content: {
              'application/json': {
                schema: {
                  properties: {
                    name: { type: 'string' },
                    email: { type: 'string' },
                  },
                  required: ['name'],
                },
              },
            },
          },
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        post: {
          requestBody: {
            content: {
              'application/json': {
                schema: {
                  properties: {
                    name: { type: 'string' },
                    email: { type: 'string' },
                  },
                  required: ['name', 'email'],
                },
              },
            },
          },
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'required_field_added' && c.detail.includes('email')));
});

test('detectSchemaBreakingChanges detects removed enum values', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      status: {
                        type: 'string',
                        enum: ['active', 'inactive', 'pending'],
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      status: {
                        type: 'string',
                        enum: ['active', 'inactive'],
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'enum_value_removed' && c.detail.includes('pending')));
});

test('detectSchemaBreakingChanges detects removed status codes', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': { description: 'OK' },
            '404': { description: 'Not Found' },
          },
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': { description: 'OK' },
          },
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'status_code_removed' && c.detail.includes('404')));
});

test('detectSchemaBreakingChanges detects type changes', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      age: { type: 'number' },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        get: {
          responses: {
            '200': {
              content: {
                'application/json': {
                  schema: {
                    properties: {
                      age: { type: 'string' },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'response_type_changed' && c.detail.includes('age')));
});

test('detectSchemaBreakingChanges detects newly required parameters', () => {
  const baseline = {
    paths: {
      '/v1/users': {
        get: {
          parameters: [
            { name: 'limit', in: 'query', required: false },
          ],
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users': {
        get: {
          parameters: [
            { name: 'limit', in: 'query', required: true },
          ],
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'query_parameter_required' && c.detail.includes('limit')));
});

test('detectSchemaBreakingChanges detects removed required parameters', () => {
  const baseline = {
    paths: {
      '/v1/users/{id}': {
        get: {
          parameters: [
            { name: 'id', in: 'path', required: true },
          ],
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/users/{id}': {
        get: {
          parameters: [],
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'path_parameter_renamed' && c.detail.includes('id')));
});

test('detectSchemaBreakingChanges detects removed security requirements', () => {
  const baseline = {
    paths: {
      '/v1/admin': {
        get: {
          security: [{ bearerAuth: [] }],
        },
      },
    },
  };

  const current = {
    paths: {
      '/v1/admin': {
        get: {
          security: [],
        },
      },
    },
  };

  const changes = detectSchemaBreakingChanges(baseline, current, 'users-api');
  assert.ok(changes.some(c => c.type === 'security_requirement_changed'));
});
