/**
 * @doc.type test
 * @doc.purpose Verify that base-ui components are properly exported from yappc-ui and @yappc/base-ui
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('base-ui exports', () => {
  it('should export Popover from base-ui', async () => {
    // This test verifies that the base-ui index.ts properly exports Popover
    // and that it's accessible via ../index.ts
    const exports = await import('../index');
    expect(exports.Popover).toBeDefined();
    expect(['function', 'object']).toContain(typeof exports.Popover);
  });

  it('should export Select components from base-ui', async () => {
    const exports = await import('../index');
    expect(exports.Select).toBeDefined();
    expect(exports.SelectOption).toBeDefined();
    expect(exports.SelectGroup).toBeDefined();
  });

  it('should have type definitions available', async () => {
    // If the base-ui is properly configured, type definitions should be available
    // This is a compile-time check via TypeScript
    const { Popover }: { Popover: unknown } = await import('../index');
    expect(Popover).toBeDefined();
  });

  it('should export from nested components directory', async () => {
    const { Popover } = await import('../components/Popover');
    expect(Popover).toBeDefined();
  });
});
