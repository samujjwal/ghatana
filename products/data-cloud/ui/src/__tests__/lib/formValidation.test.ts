/**
 * Tests for centralized form validation schemas and hooks
 *
 * Covers: FINDING-DC-UI-M3 (form validation patterns)
 *
 * @doc.type test
 * @doc.purpose Unit tests for form validation schemas
 * @doc.layer frontend
 */

import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  nameSchema,
  descriptionSchema,
  slugSchema,
  tagsSchema,
  collectionSchema,
  alertRuleSchema,
  workflowSchema,
  settingsSchema,
  FIELD_LIMITS,
} from '../../lib/forms/validation';

// Helper to safely parse a schema and return success/error info
function parse<T>(schema: z.ZodType<T>, data: unknown): { success: boolean; error?: string } {
  const result = schema.safeParse(data);
  return {
    success: result.success,
    error: result.success ? undefined : result.error.issues[0]?.message,
  };
}

describe('nameSchema', () => {
  it('accepts a valid name', () => {
    expect(parse(nameSchema, 'My Collection').success).toBe(true);
  });

  it('trims whitespace', () => {
    const result = nameSchema.parse('  trimmed  ');
    expect(result).toBe('trimmed');
  });

  it('rejects empty string', () => {
    expect(parse(nameSchema, '').success).toBe(false);
  });

  it('rejects name exceeding max length', () => {
    expect(parse(nameSchema, 'a'.repeat(FIELD_LIMITS.NAME_MAX + 1)).success).toBe(false);
  });
});

describe('descriptionSchema', () => {
  it('accepts undefined', () => {
    expect(parse(descriptionSchema, undefined).success).toBe(true);
  });

  it('accepts empty string', () => {
    expect(parse(descriptionSchema, '').success).toBe(true);
  });

  it('rejects description exceeding max length', () => {
    expect(parse(descriptionSchema, 'x'.repeat(FIELD_LIMITS.DESCRIPTION_MAX + 1)).success).toBe(false);
  });
});

describe('slugSchema', () => {
  it('accepts a valid slug', () => {
    expect(parse(slugSchema, 'my-collection-123').success).toBe(true);
  });

  it('rejects uppercase characters', () => {
    expect(parse(slugSchema, 'MyCollection').success).toBe(false);
  });

  it('rejects spaces', () => {
    expect(parse(slugSchema, 'my collection').success).toBe(false);
  });

  it('accepts undefined (optional)', () => {
    expect(parse(slugSchema, undefined).success).toBe(true);
  });
});

describe('tagsSchema', () => {
  it('accepts an empty array', () => {
    expect(parse(tagsSchema, []).success).toBe(true);
  });

  it('rejects arrays exceeding max count', () => {
    const tooMany = Array.from({ length: FIELD_LIMITS.TAG_MAX_COUNT + 1 }, (_, i) => `tag-${i}`);
    expect(parse(tagsSchema, tooMany).success).toBe(false);
  });
});

describe('collectionSchema', () => {
  it('accepts a valid collection form', () => {
    const result = parse(collectionSchema, {
      name: 'My Collection',
      description: 'A test collection',
      isPublic: false,
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing name', () => {
    expect(parse(collectionSchema, { isPublic: false }).success).toBe(false);
  });
});

describe('alertRuleSchema', () => {
  const validAlert = {
    name: 'High Latency Alert',
    severity: 'HIGH',
    enabled: true,
    condition: 'latency > 500',
    notificationChannels: ['slack-ops'],
  };

  it('accepts a valid alert rule', () => {
    expect(parse(alertRuleSchema, validAlert).success).toBe(true);
  });

  it('rejects invalid severity', () => {
    expect(parse(alertRuleSchema, { ...validAlert, severity: 'ULTRA' }).success).toBe(false);
  });

  it('rejects empty notificationChannels', () => {
    expect(parse(alertRuleSchema, { ...validAlert, notificationChannels: [] }).success).toBe(false);
  });
});

describe('workflowSchema', () => {
  it('accepts a valid workflow', () => {
    expect(parse(workflowSchema, { name: 'My Workflow' }).success).toBe(true);
  });

  it('rejects negative timeout', () => {
    expect(parse(workflowSchema, { name: 'W', timeoutSeconds: -1 }).success).toBe(false);
  });

  it('rejects timeout exceeding 24 hours', () => {
    expect(parse(workflowSchema, { name: 'W', timeoutSeconds: 86_401 }).success).toBe(false);
  });
});

describe('settingsSchema', () => {
  it('accepts valid settings', () => {
    expect(
      parse(settingsSchema, {
        displayName: 'Alice',
        email: 'alice@example.com',
        notificationsEnabled: true,
        theme: 'dark',
        defaultPageSize: 50,
      }).success
    ).toBe(true);
  });

  it('rejects invalid email', () => {
    expect(
      parse(settingsSchema, {
        displayName: 'Bob',
        email: 'not-an-email',
        notificationsEnabled: false,
        theme: 'light',
        defaultPageSize: 25,
      }).success
    ).toBe(false);
  });
});
