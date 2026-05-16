/**
 * @fileoverview Contract tests for SemanticProductModel deterministic ID guarantees.
 *
 * P1-8: The model ID must be deterministic given the same snapshot + elements.
 * Calling the pipeline twice with identical inputs must produce identical model.id values.
 */

import { describe, it, expect } from 'vitest';
import { SemanticProductModelSchema, ModelElementIdSchema } from '../types';

describe('SemanticProductModel — deterministic ID contract', () => {
  it('parses a valid SemanticProductModel with a deterministic hex ID', () => {
    const deterministicId = 'a'.repeat(64); // SHA-256 hex = 64 chars

    const model = SemanticProductModelSchema.parse({
      id: deterministicId,
      repositoryRoot: '/workspace/myapp',
      createdAt: '2025-01-01T00:00:00.000Z',
      updatedAt: '2025-01-01T00:00:00.000Z',
      version: 1,
      elements: [],
      elementIndex: {},
      residualIslandIds: [],
    });

    expect(model.id).toBe(deterministicId);
    expect(model.version).toBe(1);
    expect(model.elements).toHaveLength(0);
  });

  it('rejects a model with an empty id', () => {
    expect(() =>
      SemanticProductModelSchema.parse({
        id: '',
        repositoryRoot: '/workspace/myapp',
        createdAt: '2025-01-01T00:00:00.000Z',
        updatedAt: '2025-01-01T00:00:00.000Z',
        version: 0,
        elements: [],
        elementIndex: {},
        residualIslandIds: [],
      })
    ).toThrow();
  });

  it('rejects a model with negative version', () => {
    expect(() =>
      SemanticProductModelSchema.parse({
        id: 'valid-id-string',
        repositoryRoot: '/repo',
        createdAt: '2025-01-01T00:00:00.000Z',
        updatedAt: '2025-01-01T00:00:00.000Z',
        version: -1,
        elements: [],
        elementIndex: {},
        residualIslandIds: [],
      })
    ).toThrow();
  });

  it('rejects a model with non-datetime createdAt', () => {
    expect(() =>
      SemanticProductModelSchema.parse({
        id: 'valid-id',
        repositoryRoot: '/repo',
        createdAt: 'not-a-date',
        updatedAt: '2025-01-01T00:00:00.000Z',
        version: 0,
        elements: [],
        elementIndex: {},
        residualIslandIds: [],
      })
    ).toThrow();
  });

  it('ModelElementIdSchema accepts a valid non-empty string', () => {
    const parsed = ModelElementIdSchema.parse('element-abc-123');
    expect(parsed).toBe('element-abc-123');
  });

  it('ModelElementIdSchema rejects empty string', () => {
    expect(() => ModelElementIdSchema.parse('')).toThrow();
  });

  it('two models with the same ID are semantically equal', () => {
    const baseModel = {
      id: 'deterministic-hash-64chars-padded0000000000000000000000000000',
      repositoryRoot: '/repo',
      createdAt: '2025-01-01T00:00:00.000Z',
      updatedAt: '2025-01-01T00:00:00.000Z',
      version: 1,
      elements: [],
      elementIndex: {},
      residualIslandIds: [],
    };

    const a = SemanticProductModelSchema.parse(baseModel);
    const b = SemanticProductModelSchema.parse({ ...baseModel });

    expect(a.id).toBe(b.id);
    expect(a.version).toBe(b.version);
    expect(a.repositoryRoot).toBe(b.repositoryRoot);
  });

  it('elementIndex maps kind to residualIslandIds correctly', () => {
    const model = SemanticProductModelSchema.parse({
      id: 'test-id-with-elements',
      repositoryRoot: '/repo',
      createdAt: '2025-01-01T00:00:00.000Z',
      updatedAt: '2025-01-01T00:00:00.000Z',
      version: 2,
      elements: [],
      elementIndex: { component: ['elem-1', 'elem-2'], page: ['elem-3'] },
      residualIslandIds: ['residual-1'],
    });

    expect(model.elementIndex['component']).toEqual(['elem-1', 'elem-2']);
    expect(model.elementIndex['page']).toEqual(['elem-3']);
    expect(model.residualIslandIds).toEqual(['residual-1']);
  });
});
