/**
 * Input Sanitization Tests
 */

import { describe, it, expect } from 'vitest';
import {
  sanitizeHtml,
  sanitizeSqlInput,
  sanitizeEmail,
  sanitizeUsername,
  sanitizeUrl,
  limitLength,
  removeControlCharacters,
} from '../sanitizer.js';

describe('sanitizeHtml', () => {
  it('should remove script tags', () => {
    expect(sanitizeHtml('<script>alert("xss")</script>')).not.toContain('<script>');
  });

  it('should remove iframe tags', () => {
    expect(sanitizeHtml('<iframe src="evil.com"></iframe>')).not.toContain('<iframe>');
  });

  it('should remove event handlers', () => {
    expect(sanitizeHtml('<div onclick="alert(1)">')).not.toContain('onclick');
  });

  it('should remove javascript: protocol', () => {
    expect(sanitizeHtml('<a href="javascript:alert(1)">')).not.toContain('javascript:');
  });

  it('should preserve safe HTML', () => {
    expect(sanitizeHtml('<p>Hello</p>')).toContain('<p>Hello</p>');
  });

  it('should handle empty input', () => {
    expect(sanitizeHtml('')).toBe('');
    expect(sanitizeHtml(null as any)).toBe('');
  });
});

describe('sanitizeSqlInput', () => {
  it('should remove SQL injection patterns', () => {
    expect(sanitizeSqlInput("'; DROP TABLE users; --")).not.toContain(';');
    expect(sanitizeSqlInput("'; DROP TABLE users; --")).not.toContain('--');
  });

  it('should remove quotes', () => {
    expect(sanitizeSqlInput("O'Reilly")).not.toContain("'");
  });

  it('should preserve safe input', () => {
    expect(sanitizeSqlInput('John Doe')).toBe('John Doe');
  });

  it('should handle empty input', () => {
    expect(sanitizeSqlInput('')).toBe('');
  });
});

describe('sanitizeEmail', () => {
  it('should lowercase email', () => {
    expect(sanitizeEmail('John@Example.COM')).toBe('john@example.com');
  });

  it('should trim whitespace', () => {
    expect(sanitizeEmail('  john@example.com  ')).toBe('john@example.com');
  });

  it('should remove invalid characters', () => {
    expect(sanitizeEmail('john@example.com<script>')).not.toContain('<script>');
  });

  it('should handle empty input', () => {
    expect(sanitizeEmail('')).toBe('');
  });
});

describe('sanitizeUsername', () => {
  it('should lowercase username', () => {
    expect(sanitizeUsername('JohnDoe')).toBe('johndoe');
  });

  it('should remove invalid characters', () => {
    expect(sanitizeUsername('john@doe')).toBe('johndoe');
  });

  it('should preserve valid characters', () => {
    expect(sanitizeUsername('john_doe-123')).toBe('john_doe-123');
  });

  it('should handle empty input', () => {
    expect(sanitizeUsername('')).toBe('');
  });
});

describe('sanitizeUrl', () => {
  it('should remove javascript: protocol', () => {
    expect(sanitizeUrl('javascript:alert(1)')).not.toContain('javascript:');
  });

  it('should remove vbscript: protocol', () => {
    expect(sanitizeUrl('vbscript:msgbox(1)')).not.toContain('vbscript:');
  });

  it('should remove data: protocol', () => {
    expect(sanitizeUrl('data:text/html,<script>')).not.toContain('data:');
  });

  it('should preserve safe URLs', () => {
    expect(sanitizeUrl('https://example.com')).toBe('https://example.com');
  });

  it('should handle empty input', () => {
    expect(sanitizeUrl('')).toBe('');
  });
});

describe('limitLength', () => {
  it('should limit string length', () => {
    expect(limitLength('Hello World', 5)).toBe('Hello');
  });

  it('should trim whitespace', () => {
    expect(limitLength('  Hello  ', 10)).toBe('Hello');
  });

  it('should handle empty input', () => {
    expect(limitLength('', 10)).toBe('');
  });

  it('should handle strings shorter than limit', () => {
    expect(limitLength('Hi', 10)).toBe('Hi');
  });
});

describe('removeControlCharacters', () => {
  it('should remove control characters', () => {
    expect(removeControlCharacters('Hello\x00World')).toBe('HelloWorld');
  });

  it('should preserve normal characters', () => {
    expect(removeControlCharacters('Hello World')).toBe('Hello World');
  });

  it('should handle empty input', () => {
    expect(removeControlCharacters('')).toBe('');
  });
});
