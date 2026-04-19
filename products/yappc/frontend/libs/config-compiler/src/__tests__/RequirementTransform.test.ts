/**
 * Requirement Transform Tests
 *
 * Tests for RequirementTransform class.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import { RequirementTransform } from '../generators/RequirementTransform';

describe('RequirementTransform', () => {
  const transform = new RequirementTransform();

  it('should transform intent to requirements', async () => {
    const intentConfig = {
      id: 'intent-1',
      version: '1.0.0',
      intent: 'Create a user management page',
      requirementIds: [],
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      author: 'test-user',
      tags: ['user-management'],
    };

    const result = await transform.transformIntentToRequirements(intentConfig);

    expect(result).toBeDefined();
    expect(result.length).toBe(1);
    expect(result[0].intentId).toBe('intent-1');
    expect(result[0].author).toBe('test-user');
    expect(result[0].tags).toEqual(['user-management']);
  });

  it('should validate valid transformation', () => {
    const intentConfig = {
      id: 'intent-1',
      version: '1.0.0',
      intent: 'Create a user management page',
      requirementIds: [],
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      author: 'test-user',
      tags: [],
    };

    const result = transform.validateTransformation(intentConfig);

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should reject intent without id', () => {
    const intentConfig = {
      version: '1.0.0',
      intent: 'Create a user management page',
      requirementIds: [],
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      author: 'test-user',
      tags: [],
    };

    const result = transform.validateTransformation(intentConfig);

    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should reject intent without intent field', () => {
    const intentConfig = {
      id: 'intent-1',
      version: '1.0.0',
      requirementIds: [],
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      author: 'test-user',
      tags: [],
    };

    const result = transform.validateTransformation(intentConfig);

    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});
