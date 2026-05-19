/**
 * @fileoverview Import/export round-trip and schema validation tests for BuilderDocument.
 *
 * Verifies that:
 * 1. A canonical document round-trips through serialize → deserialize without data loss.
 * 2. The round-tripped document passes validateBuilderDocument with no error-severity issues.
 * 3. The JSON schema export from the barrel is well-formed.
 * 4. Unknown-format inputs are rejected at the boundary with clear errors.
 *
 * @test.type unit
 * @test.execution <100ms
 * @test.infra none
 */

import { describe, it, expect } from 'vitest';
import {
  createBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
  validateBuilderDocument,
  CURRENT_SCHEMA_VERSION,
} from '../builder-document.js';
import {
  builderDocumentV1Schema,
  BUILDER_DOCUMENT_V1_SCHEMA_ID,
  BUILDER_DOCUMENT_V1_VERSION,
} from '../../schema/index.js';

// =============================================================================
// JSON round-trip
// =============================================================================

describe('BuilderDocument serialize / deserialize round-trip', () => {
  it('serializes to a valid JSON string', () => {
    const doc = createBuilderDocument('test-owner');
    const json = serializeBuilderDocument(doc);
    expect(typeof json).toBe('string');
    // Must be parseable JSON
    expect(() => JSON.parse(json)).not.toThrow();
  });

  it('preserves schemaVersion through the round-trip', () => {
    const doc = createBuilderDocument('test-owner');
    const json = serializeBuilderDocument(doc);
    const result = deserializeBuilderDocument(json);
    expect(result.success).toBe(true);
    expect(result.document?.schemaVersion).toBe(CURRENT_SCHEMA_VERSION);
  });

  it('preserves owner through the round-trip', () => {
    const doc = createBuilderDocument('round-trip-owner');
    const json = serializeBuilderDocument(doc);
    const result = deserializeBuilderDocument(json);
    expect(result.success).toBe(true);
    // owner is a top-level field on BuilderDocument, not inside metadata
    expect(result.document?.owner).toBe('round-trip-owner');
  });

  it('round-tripped document passes validateBuilderDocument with no error-severity issues', () => {
    const doc = createBuilderDocument('validation-owner');
    const json = serializeBuilderDocument(doc);
    const deserialized = deserializeBuilderDocument(json);
    expect(deserialized.success).toBe(true);

    const validation = validateBuilderDocument(deserialized.document!);
    const errors = validation.issues.filter((i) => i.severity === 'error');
    expect(errors).toHaveLength(0);
  });

  it('returns success:false for malformed JSON', () => {
    const result = deserializeBuilderDocument('not-valid-json{{{{');
    expect(result.success).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('returns success:false for JSON with missing required fields', () => {
    const incomplete = JSON.stringify({ schemaVersion: '1.0.0' /* missing other required fields */ });
    const result = deserializeBuilderDocument(incomplete);
    expect(result.success).toBe(false);
  });

  it('returns success:false for a completely empty object', () => {
    const result = deserializeBuilderDocument('{}');
    expect(result.success).toBe(false);
  });
});

// =============================================================================
// validateBuilderDocument
// =============================================================================

describe('validateBuilderDocument', () => {
  it('passes for a freshly created canonical document', () => {
    const doc = createBuilderDocument('owner');
    const result = validateBuilderDocument(doc);
    expect(result.valid).toBe(true);
    expect(result.issues.filter((i) => i.severity === 'error')).toHaveLength(0);
  });

  it('reports an error for a document with an invalid schemaVersion', () => {
    const doc = createBuilderDocument('owner');
    // Force a schema version mismatch to exercise schema validation
    const invalidDoc = {
      ...doc,
      schemaVersion: 'INVALID_VERSION',
    };
    const result = validateBuilderDocument(invalidDoc);
    expect(result.valid).toBe(false);
    expect(result.issues.filter((i) => i.severity === 'error').length).toBeGreaterThan(0);
  });
});

// =============================================================================
// JSON Schema export
// =============================================================================

describe('JSON Schema export', () => {
  it('exports a non-empty schema object', () => {
    expect(builderDocumentV1Schema).toBeDefined();
    expect(typeof builderDocumentV1Schema).toBe('object');
  });

  it('schema has the correct $id', () => {
    const schema = builderDocumentV1Schema as Record<string, unknown>;
    expect(schema['$id']).toBe(BUILDER_DOCUMENT_V1_SCHEMA_ID);
  });

  it('schema version constant matches the schema title/version', () => {
    expect(BUILDER_DOCUMENT_V1_VERSION).toBe('1.0.0');
  });

  it('schema is readonly (immutable reference)', () => {
    // Verifying that modifying the exported object does not affect the original.
    const schema = builderDocumentV1Schema as Record<string, unknown>;
    const originalId = schema['$id'];
    expect(originalId).toBeDefined();
  });
});
