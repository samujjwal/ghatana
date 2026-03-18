import { describe, it, expect } from 'vitest';
import { cn } from '../cn';

describe('cn', () => {
  it('should merge class names', () => {
    expect(cn('foo', 'bar')).toBe('foo bar');
  });

  it('should handle conditional classes', () => {
    const isActive = true;
    const result = cn('base', isActive && 'active');
    expect(result).toContain('active');
  });

  it('should handle falsy values', () => {
    const result = cn('base', false, null, undefined, 'end');
    expect(result).toBe('base end');
  });

  it('should merge Tailwind conflicts', () => {
    // twMerge should resolve conflicting Tailwind classes
    const result = cn('p-4', 'p-2');
    expect(result).toBe('p-2');
  });

  it('should merge complex Tailwind classes', () => {
    const result = cn('text-red-500', 'text-blue-500');
    expect(result).toBe('text-blue-500');
  });

  it('should handle empty input', () => {
    expect(cn()).toBe('');
  });

  it('should handle object syntax', () => {
    const result = cn({ foo: true, bar: false, baz: true });
    expect(result).toContain('foo');
    expect(result).toContain('baz');
    expect(result).not.toContain('bar');
  });
});
