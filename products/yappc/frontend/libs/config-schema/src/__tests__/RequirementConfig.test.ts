/**
 * RequirementConfig Schema Tests
 *
 * Tests for RequirementConfig schema validation and type inference.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import {
  RequirementConfigSchema,
  AcceptanceCriteriaSchema,
} from '../schemas/RequirementConfig';

describe('RequirementConfigSchema', () => {
  const validRequirementConfig = {
    id: 'req-1',
    version: '1.0.0',
    title: 'User can create new accounts',
    description: 'Users should be able to create new accounts with email and password',
    type: 'functional' as const,
    priority: 'high' as const,
    status: 'approved' as const,
    acceptanceCriteria: [
      {
        id: 'ac-1',
        criteria: 'User can enter email and password',
        priority: 'must' as const,
        status: 'completed' as const,
      },
      {
        id: 'ac-2',
        criteria: 'User receives confirmation email',
        priority: 'should' as const,
        status: 'pending' as const,
      },
    ],
    intentId: 'intent-1',
    linkedPageIds: ['page-1'],
    linkedComponentIds: ['component-1'],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'product-owner',
    assignee: 'developer-1',
    tags: ['authentication', 'user-management'],
  };

  it('should validate a valid RequirementConfig', () => {
    const result = RequirementConfigSchema.safeParse(validRequirementConfig);
    expect(result.success).toBe(true);
  });

  it('should reject RequirementConfig without required title', () => {
    const invalidConfig = { ...validRequirementConfig, title: '' };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject RequirementConfig without required description', () => {
    const invalidConfig = { ...validRequirementConfig, description: '' };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject RequirementConfig with invalid type', () => {
    const invalidConfig = { ...validRequirementConfig, type: 'invalid' };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject RequirementConfig with invalid priority', () => {
    const invalidConfig = { ...validRequirementConfig, priority: 'urgent' };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject RequirementConfig with invalid status', () => {
    const invalidConfig = { ...validRequirementConfig, status: 'invalid' };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should accept RequirementConfig with optional fields omitted', () => {
    const minimalConfig = {
      ...validRequirementConfig,
      intentId: undefined,
      assignee: undefined,
    };
    const result = RequirementConfigSchema.safeParse(minimalConfig);
    expect(result.success).toBe(true);
  });

  it('should reject RequirementConfig with empty acceptanceCriteria array', () => {
    const invalidConfig = { ...validRequirementConfig, acceptanceCriteria: [] };
    const result = RequirementConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });
});

describe('AcceptanceCriteriaSchema', () => {
  const validAcceptanceCriteria = {
    id: 'ac-1',
    criteria: 'User can enter email and password',
    priority: 'must' as const,
    status: 'completed' as const,
  };

  it('should validate a valid AcceptanceCriteria', () => {
    const result = AcceptanceCriteriaSchema.safeParse(validAcceptanceCriteria);
    expect(result.success).toBe(true);
  });

  it('should reject AcceptanceCriteria without required criteria', () => {
    const invalidCriteria = { ...validAcceptanceCriteria, criteria: '' };
    const result = AcceptanceCriteriaSchema.safeParse(invalidCriteria);
    expect(result.success).toBe(false);
  });

  it('should reject AcceptanceCriteria with invalid priority', () => {
    const invalidCriteria = { ...validAcceptanceCriteria, priority: 'urgent' };
    const result = AcceptanceCriteriaSchema.safeParse(invalidCriteria);
    expect(result.success).toBe(false);
  });

  it('should reject AcceptanceCriteria with invalid status', () => {
    const invalidCriteria = { ...validAcceptanceCriteria, status: 'invalid' };
    const result = AcceptanceCriteriaSchema.safeParse(invalidCriteria);
    expect(result.success).toBe(false);
  });

  it('should accept all valid priority values', () => {
    const priorities: Array<'must' | 'should' | 'could'> = ['must', 'should', 'could'];
    priorities.forEach((priority) => {
      const criteria = { ...validAcceptanceCriteria, priority };
      const result = AcceptanceCriteriaSchema.safeParse(criteria);
      expect(result.success).toBe(true);
    });
  });

  it('should accept all valid status values', () => {
    const statuses: Array<'pending' | 'in-progress' | 'completed' | 'blocked'> = [
      'pending',
      'in-progress',
      'completed',
      'blocked',
    ];
    statuses.forEach((status) => {
      const criteria = { ...validAcceptanceCriteria, status };
      const result = AcceptanceCriteriaSchema.safeParse(criteria);
      expect(result.success).toBe(true);
    });
  });
});
