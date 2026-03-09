#!/usr/bin/env node
// Generates minimal JSON Schemas for validation in desktop/extension
// Uses only Node built-ins; no extra libraries.

const fs = require('fs');
const path = require('path');

const SCHEMA_DIRS = [
  path.join(__dirname, '../../services/desktop/src/schemas'),
  path.join(__dirname, '../../services/extension/src/schemas'),
];

// Minimal, forward-compatible schemas that match current tests and allow
// additive evolution. Keep constraints light to avoid brittle clients.
const EventEnvelopeSchema = {
  $schema: 'http://json-schema.org/draft-07/schema#',
  $id: 'EventEnvelope.schema.json',
  title: 'EventEnvelope',
  type: 'object',
  required: ['meta', 'events'],
  additionalProperties: true,
  properties: {
    meta: {
      type: 'object',
      required: ['tenant_id', 'device_id', 'session_id', 'timestamp'],
      additionalProperties: true,
      properties: {
        tenant_id: { type: 'string' },
        device_id: { type: 'string' },
        session_id: { type: 'string' },
        timestamp: { anyOf: [{ type: 'number' }, { type: 'string' }] },
        schema_version: { type: 'string' },
      },
    },
    events: {
      type: 'array',
      minItems: 0,
      items: {
        type: 'object',
        additionalProperties: true,
      },
    },
    schema_version: { type: 'string' },
    idempotency_key: { type: 'string' },
    source_os: { type: 'string' },
    source_arch: { type: 'string' },
  },
};

const IngestRequestSchema = {
  $schema: 'http://json-schema.org/draft-07/schema#',
  $id: 'IngestRequest.schema.json',
  title: 'IngestRequest',
  type: 'object',
  required: ['batch'],
  additionalProperties: true,
  properties: {
    batch: {
      type: 'object',
      required: ['envelopes'],
      additionalProperties: true,
      properties: {
        envelopes: {
          type: 'array',
          minItems: 1,
          items: EventEnvelopeSchema,
        },
      },
    },
    schema_version: { type: 'string' },
    idempotency_key: { type: 'string' },
    source_os: { type: 'string' },
    source_arch: { type: 'string' },
  },
};

function ensureDirs() {
  SCHEMA_DIRS.forEach((dir) => {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  });
}

function writeSchemas() {
  const files = [
    ['EventEnvelope.schema.json', EventEnvelopeSchema],
    ['IngestRequest.schema.json', IngestRequestSchema],
  ];
  SCHEMA_DIRS.forEach((dir) => {
    files.forEach(([name, schema]) => {
      fs.writeFileSync(path.join(dir, name), JSON.stringify(schema, null, 2));
    });
  });
}

try {
  ensureDirs();
  writeSchemas();
  console.log('✅ Schema generation complete');
} catch (err) {
  console.error('❌ Schema generation failed:', err);
  process.exit(1);
}
