/**
 * Intent Parser Tests
 *
 * Tests for IntentParser class.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import { IntentParser } from '../generators/IntentParser';

describe('IntentParser', () => {
  const parser = new IntentParser();

  it('should parse natural language intent', async () => {
    const intent = 'Create a user management page with CRUD operations';
    const context = {
      author: 'test-user',
      tags: ['user-management'],
    };

    const result = await parser.parseIntent(intent, context);

    expect(result).toBeDefined();
    expect(result.intent).toBe(intent);
    expect(result.author).toBe('test-user');
    expect(result.tags).toEqual(['user-management']);
    expect(result.aiGenerated).toBe(true);
  });

  it('should validate valid intent', () => {
    const intent = 'Create a user management page';
    const result = parser.validateIntent(intent);

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should reject empty intent', () => {
    const result = parser.validateIntent('');

    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should reject intent that is too short', () => {
    const result = parser.validateIntent('abc');

    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should reject intent that is too long', () => {
    const longIntent = 'a'.repeat(5001);
    const result = parser.validateIntent(longIntent);

    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});
