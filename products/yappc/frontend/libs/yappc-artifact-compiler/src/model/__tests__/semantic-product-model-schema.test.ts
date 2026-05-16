import { describe, expect, it } from 'vitest';
import { SemanticProductModelSchema } from '../types';

describe('SemanticProductModelSchema', () => {
  it('accepts a UUID model id with a deterministic source model reference', () => {
    const result = SemanticProductModelSchema.safeParse({
      id: 'f8f4f02b-0c63-4a18-b76d-0ef4ca86019f',
      sourceModelRef: 'artifact://github/ghatana/repo#model:elements:1',
      repositoryRoot: '/tmp/repo',
      createdAt: '2026-05-15T00:00:00.000Z',
      updatedAt: '2026-05-15T00:00:00.000Z',
      version: 1,
      elements: [],
      elementIndex: {},
      residualIslandIds: [],
    });

    expect(result.success).toBe(true);
  });
});