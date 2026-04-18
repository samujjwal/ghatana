/**
 * Input Validation Tests
 */

import { describe, it, expect } from 'vitest';
import {
  emailSchema,
  usernameSchema,
  passwordSchema,
  uuidSchema,
  moduleIdSchema,
  paginationSchema,
  aiQuerySchema,
  moduleSchema,
  registrationSchema,
  loginSchema,
  validate,
  safeValidate,
} from '../validator.js';

describe('emailSchema', () => {
  it('should validate valid emails', () => {
    expect(() => emailSchema.parse('user@example.com')).not.toThrow();
    expect(() => emailSchema.parse('user.name+tag@example.co.uk')).not.toThrow();
  });

  it('should reject invalid emails', () => {
    expect(() => emailSchema.parse('invalid')).toThrow();
    expect(() => emailSchema.parse('@example.com')).toThrow();
    expect(() => emailSchema.parse('user@')).toThrow();
  });

  it('should lowercase and trim emails', () => {
    expect(emailSchema.parse('  User@Example.COM  ')).toBe('user@example.com');
  });
});

describe('usernameSchema', () => {
  it('should validate valid usernames', () => {
    expect(() => usernameSchema.parse('john_doe')).not.toThrow();
    expect(() => usernameSchema.parse('john-doe')).not.toThrow();
    expect(() => usernameSchema.parse('john123')).not.toThrow();
  });

  it('should reject invalid usernames', () => {
    expect(() => usernameSchema.parse('jo')).toThrow(); // Too short
    expect(() => usernameSchema.parse('john@doe')).toThrow(); // Invalid character
    expect(() => usernameSchema.parse('a'.repeat(51))).toThrow(); // Too long
  });

  it('should lowercase usernames', () => {
    expect(usernameSchema.parse('JohnDoe')).toBe('johndoe');
  });
});

describe('passwordSchema', () => {
  it('should validate strong passwords', () => {
    expect(() => passwordSchema.parse('Password123')).not.toThrow();
  });

  it('should reject weak passwords', () => {
    expect(() => passwordSchema.parse('short')).toThrow(); // Too short
    expect(() => passwordSchema.parse('nouppercase123')).toThrow(); // No uppercase
    expect(() => passwordSchema.parse('NOLOWERCASE123')).toThrow(); // No lowercase
    expect(() => passwordSchema.parse('NoNumbers')).toThrow(); // No numbers
  });
});

describe('uuidSchema', () => {
  it('should validate valid UUIDs', () => {
    expect(() => uuidSchema.parse('550e8400-e29b-41d4-a716-446655440000')).not.toThrow();
  });

  it('should reject invalid UUIDs', () => {
    expect(() => uuidSchema.parse('not-a-uuid')).toThrow();
    expect(() => uuidSchema.parse('550e8400-e29b-41d4-a716')).toThrow(); // Too short
  });
});

describe('paginationSchema', () => {
  it('should validate pagination params', () => {
    expect(() => paginationSchema.parse({ page: 1, limit: 20 })).not.toThrow();
  });

  it('should provide defaults', () => {
    const result = paginationSchema.parse({});
    expect(result.page).toBe(1);
    expect(result.limit).toBe(20);
  });

  it('should coerce string numbers', () => {
    const result = paginationSchema.parse({ page: '2', limit: '10' });
    expect(result.page).toBe(2);
    expect(result.limit).toBe(10);
  });

  it('should enforce limits', () => {
    expect(() => paginationSchema.parse({ limit: 200 })).toThrow(); // Too high
  });
});

describe('aiQuerySchema', () => {
  it('should validate AI queries', () => {
    expect(() => aiQuerySchema.parse({
      question: 'What is photosynthesis?',
      moduleId: 'mod-123',
      locale: 'en',
    })).not.toThrow();
  });

  it('should require question', () => {
    expect(() => aiQuerySchema.parse({})).toThrow();
  });

  it('should enforce question length limits', () => {
    expect(() => aiQuerySchema.parse({
      question: 'a'.repeat(1001),
    })).toThrow();
  });
});

describe('moduleSchema', () => {
  it('should validate module data', () => {
    expect(() => moduleSchema.parse({
      title: 'Introduction to Physics',
      description: 'A comprehensive introduction to physics concepts',
      domain: 'physics',
      difficulty: 'beginner',
      estimatedDuration: 60,
    })).not.toThrow();
  });

  it('should validate domain enum', () => {
    expect(() => moduleSchema.parse({
      title: 'Test',
      description: 'Test',
      domain: 'invalid',
      difficulty: 'beginner',
      estimatedDuration: 60,
    })).toThrow();
  });

  it('should validate difficulty enum', () => {
    expect(() => moduleSchema.parse({
      title: 'Test',
      description: 'Test',
      domain: 'physics',
      difficulty: 'invalid',
      estimatedDuration: 60,
    })).toThrow();
  });
});

describe('registrationSchema', () => {
  it('should validate registration data', () => {
    expect(() => registrationSchema.parse({
      email: 'user@example.com',
      password: 'Password123',
      name: 'John Doe',
    })).not.toThrow();
  });

  it('should require all fields', () => {
    expect(() => registrationSchema.parse({
      email: 'user@example.com',
      password: 'Password123',
    })).toThrow();
  });
});

describe('loginSchema', () => {
  it('should validate login data', () => {
    expect(() => loginSchema.parse({
      email: 'user@example.com',
      password: 'Password123',
    })).not.toThrow();
  });

  it('should require password', () => {
    expect(() => loginSchema.parse({
      email: 'user@example.com',
    })).toThrow();
  });
});

describe('validate', () => {
  it('should throw on invalid data', () => {
    expect(() => validate(emailSchema, 'invalid')).toThrow();
  });

  it('should return validated data', () => {
    const result = validate(emailSchema, 'user@example.com');
    expect(result).toBe('user@example.com');
  });
});

describe('safeValidate', () => {
  it('should return null on invalid data', () => {
    const result = safeValidate(emailSchema, 'invalid');
    expect(result).toBeNull();
  });

  it('should return validated data on success', () => {
    const result = safeValidate(emailSchema, 'user@example.com');
    expect(result).toBe('user@example.com');
  });
});
