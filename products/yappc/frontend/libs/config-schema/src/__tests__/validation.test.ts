/**
 * Validation Utilities Tests
 *
 * Tests for validation functions.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import {
  validatePageConfig,
  validateIntentConfig,
  validateRequirementConfig,
} from '../validation';
import {
  PageConfigSchema,
  IntentConfigSchema,
  RequirementConfigSchema,
} from '../schemas';

describe('validatePageConfig', () => {
  const validPageConfig = {
    id: 'page-1',
    version: '1.0.0',
    requirementIds: ['req-1'],
    title: 'Test Page',
    description: 'A test page',
    route: '/test',
    layout: 'canvas',
    layoutConfig: {
      responsiveBreakpoints: [],
    },
    components: [],
    data: {
      sources: [],
      bindings: [],
    },
    actions: [],
    connections: {
      events: [],
      data: [],
      navigation: [],
    },
    contracts: {
      inputs: [],
      outputs: [],
    },
    permissions: {
      view: [],
      edit: [],
      delete: [],
    },
    i18n: {
      defaultLocale: 'en',
      supportedLocales: [],
      translations: {},
    },
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'test-user',
    tags: [],
  };

  it('should return valid: true for valid PageConfig', () => {
    const result = validatePageConfig(validPageConfig);
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should return valid: false with errors for invalid PageConfig', () => {
    const invalidConfig = { ...validPageConfig, id: '' };
    const result = validatePageConfig(invalidConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should handle non-object data', () => {
    const result = validatePageConfig(null);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should handle missing required fields', () => {
    const incompleteConfig = { id: 'page-1' };
    const result = validatePageConfig(incompleteConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});

describe('validateIntentConfig', () => {
  const validIntentConfig = {
    id: 'intent-1',
    version: '1.0.0',
    intent: 'Create a user management page',
    requirementIds: ['req-1'],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'test-user',
    tags: [],
  };

  it('should return valid: true for valid IntentConfig', () => {
    const result = validateIntentConfig(validIntentConfig);
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should return valid: false with errors for invalid IntentConfig', () => {
    const invalidConfig = { ...validIntentConfig, intent: '' };
    const result = validateIntentConfig(invalidConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should handle non-object data', () => {
    const result = validateIntentConfig(undefined);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});

describe('validateRequirementConfig', () => {
  const validRequirementConfig = {
    id: 'req-1',
    version: '1.0.0',
    title: 'User can create accounts',
    description: 'Users should be able to create accounts',
    type: 'functional',
    priority: 'high',
    status: 'approved',
    acceptanceCriteria: [
      {
        id: 'ac-1',
        criteria: 'User can enter email',
        priority: 'must',
        status: 'completed',
      },
    ],
    linkedPageIds: [],
    linkedComponentIds: [],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'test-user',
    tags: [],
  };

  it('should return valid: true for valid RequirementConfig', () => {
    const result = validateRequirementConfig(validRequirementConfig);
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should return valid: false with errors for invalid RequirementConfig', () => {
    const invalidConfig = { ...validRequirementConfig, title: '' };
    const result = validateRequirementConfig(invalidConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should handle non-object data', () => {
    const result = validateRequirementConfig('invalid');
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});
