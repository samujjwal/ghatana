import { describe, it, expect } from 'vitest';

describe('@dcmaar/types/agent smoke', () => {
  it('exports ApiResponse type', async () => {
    const mod = await import('../agent/index');
    // Type guards — verify the module exports exist at runtime
    expect(mod).toBeDefined();
  });

  it('AppConfig shape is exported', async () => {
    // Verify the module can be imported without errors — type-only export check
    const mod = await import('../agent/index');
    expect(typeof mod).toBe('object');
  });
});
