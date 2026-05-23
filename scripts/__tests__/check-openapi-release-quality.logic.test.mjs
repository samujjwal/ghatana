import test from 'node:test';
import assert from 'node:assert/strict';

import {
  collectGenericSchemas,
  extractOperationsFromYaml,
  validateSpecQuality,
} from '../check-openapi-release-quality.mjs';

test('extractOperationsFromYaml reads path-method operation blocks', () => {
  const source = [
    'openapi: 3.0.3',
    'paths:',
    '  /v1/items:',
    '    get:',
    '      responses:',
    "        '200':",
    '          description: ok',
    '          content:',
    '            application/json:',
    '              schema:',
    "                $ref: '#/components/schemas/Item'",
    "        '400':",
    "          $ref: '#/components/responses/BadRequest'",
    '      x-ghatana-sensitivity: INTERNAL',
    '      x-ghatana-required-access: VIEWER',
    '    post:',
    '      parameters:',
    "        - $ref: '#/components/parameters/IdempotencyKeyHeader'",
    '      responses:',
    "        '204':",
    '          description: no content',
    "        '401':",
    "          $ref: '#/components/responses/Unauthorized'",
    '      x-ghatana-sensitivity: CRITICAL',
    '      x-ghatana-required-access: ADMIN',
    '',
  ].join('\n');

  const operations = extractOperationsFromYaml(source);

  assert.equal(operations.length, 2);
  assert.equal(operations[0].method, 'GET');
  assert.equal(operations[0].path, '/v1/items');
  assert.equal(operations[1].method, 'POST');
});

test('collectGenericSchemas finds object schemas with additionalProperties true', () => {
  const source = [
    'openapi: 3.0.3',
    'components:',
    '  schemas:',
    '    GenericPayload:',
    '      type: object',
    '      additionalProperties: true',
    '    TypedPayload:',
    '      type: object',
    '      properties:',
    '        id:',
    '          type: string',
    '',
  ].join('\n');

  const genericSchemas = collectGenericSchemas(source);

  assert.equal(genericSchemas.has('GenericPayload'), true);
  assert.equal(genericSchemas.has('TypedPayload'), false);
});

test('validateSpecQuality reports missing typed/error/idempotency metadata', () => {
  const source = [
    'openapi: 3.0.3',
    'paths:',
    '  /api/v1/action/items/{id}:',
    '    patch:',
    '      x-ghatana-sensitivity: CRITICAL',
    '      responses:',
    "        '200':",
    '          description: ok',
    '',
  ].join('\n');

  const result = validateSpecQuality({
    source,
    specId: 'fixture',
    allowedGenericSchemas: new Set(),
    requireExamples: true,
  });

  assert.equal(result.violations.some((entry) => entry.includes('typed 2xx response schema')), true);
  assert.equal(result.violations.some((entry) => entry.includes('error response contract')), true);
  assert.equal(result.violations.some((entry) => entry.includes('x-ghatana sensitivity and access metadata')), true);
  assert.equal(result.violations.some((entry) => entry.includes('idempotency header contract')), true);
  assert.equal(result.violations.some((entry) => entry.includes('example/examples')), true);
});

test('validateSpecQuality passes for a well-typed operation', () => {
  const source = [
    'openapi: 3.0.3',
    'paths:',
    '  /api/v1/action/run:',
    '    post:',
    '      x-ghatana-sensitivity: CRITICAL',
    '      x-ghatana-required-access: ADMIN',
    '      parameters:',
    "        - $ref: '#/components/parameters/IdempotencyKeyHeader'",
    '      responses:',
    "        '200':",
    '          description: ok',
    '          content:',
    '            application/json:',
    '              schema:',
    "                $ref: '#/components/schemas/ActionResult'",
    "        '400':",
    "          $ref: '#/components/responses/BadRequest'",
    'components:',
    '  schemas:',
    '    ActionResult:',
    '      type: object',
    '      properties:',
    '        id:',
    '          type: string',
    'x-ghatana-contract-version: 1',
    'example: {}',
    '',
  ].join('\n');

  const result = validateSpecQuality({
    source,
    specId: 'fixture',
    allowedGenericSchemas: new Set(),
    requireExamples: true,
  });

  assert.deepEqual(result.violations, []);
});
