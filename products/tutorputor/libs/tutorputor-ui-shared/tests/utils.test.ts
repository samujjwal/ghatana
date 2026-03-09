import { describe, it, expect } from 'vitest';
import { cn } from '../src/utils.js';

describe('cn', () => {
    it('merges simple class strings', () => {
        expect(cn('foo', 'bar')).toBe('foo bar');
    });

    it('resolves Tailwind conflicts in favour of the last class', () => {
        // twMerge should drop the earlier conflicting class
        expect(cn('p-4', 'p-6')).toBe('p-6');
        expect(cn('text-red-500', 'text-blue-500')).toBe('text-blue-500');
    });

    it('handles conditional class values via clsx', () => {
        const active = true;
        const disabled = false;
        expect(cn('base', active && 'active', disabled && 'disabled')).toBe('base active');
    });

    it('handles object syntax', () => {
        expect(cn({ 'font-bold': true, 'font-normal': false })).toBe('font-bold');
    });

    it('handles array syntax', () => {
        expect(cn(['px-4', 'py-2'])).toBe('px-4 py-2');
    });

    it('handles undefined and null gracefully', () => {
        expect(cn('foo', undefined, null as unknown as string, 'bar')).toBe('foo bar');
    });

    it('returns empty string for no inputs', () => {
        expect(cn()).toBe('');
    });
});
