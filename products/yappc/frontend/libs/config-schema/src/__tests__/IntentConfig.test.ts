/**
 * IntentConfig Schema Tests
 *
 * Tests for IntentConfig schema validation and type inference.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import { IntentConfigSchema } from '../schemas/IntentConfig';

describe('IntentConfigSchema', () => {
  const validIntentConfig = {
    id: 'intent-1',
    version: '1.0.0',
    intent: 'Create a user management page with CRUD operations',
    description: 'A page for managing users with create, read, update, and delete functionality',
    requirementIds: ['req-1', 'req-2', 'req-3'],
    aiGenerated: true,
    aiConfidence: 0.95,
    aiModel: 'gpt-4',
    aiPrompt: 'Create a user management page',
    aiTimestamp: '2024-01-01T00:00:00Z',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'ai-system',
    tags: ['user-management', 'crud'],
  };

  it('should validate a valid IntentConfig', () => {
    const result = IntentConfigSchema.safeParse(validIntentConfig);
    expect(result.success).toBe(true);
  });

  it('should reject IntentConfig without required intent field', () => {
    const invalidConfig = { ...validIntentConfig, intent: '' };
    const result = IntentConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject IntentConfig with empty requirementIds array', () => {
    const invalidConfig = { ...validIntentConfig, requirementIds: [] };
    const result = IntentConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should accept IntentConfig with AI lineage fields omitted', () => {
    const minimalConfig = {
      ...validIntentConfig,
      aiGenerated: false,
      aiConfidence: undefined,
      aiModel: undefined,
      aiPrompt: undefined,
      aiTimestamp: undefined,
    };
    const result = IntentConfigSchema.safeParse(minimalConfig);
    expect(result.success).toBe(true);
  });

  it('should reject IntentConfig with invalid aiConfidence (out of range)', () => {
    const invalidConfig = { ...validIntentConfig, aiConfidence: 1.5 };
    const result = IntentConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject IntentConfig with invalid aiConfidence (negative)', () => {
    const invalidConfig = { ...validIntentConfig, aiConfidence: -0.1 };
    const result = IntentConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should accept IntentConfig with aiGenerated defaulting to false', () => {
    const configWithoutAiGenerated = {
      ...validIntentConfig,
      aiGenerated: undefined,
    };
    const result = IntentConfigSchema.safeParse(configWithoutAiGenerated);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.aiGenerated).toBe(false);
    }
  });
});
