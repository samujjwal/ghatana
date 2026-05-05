import { afterEach, describe, expect, it, vi } from 'vitest';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('FlashIt AI mode helpers', () => {
  it('treats FLASHIT_AI_DISABLED=true as disabled mode', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'true');
    const { isAiDisabled } = await import('../ai-mode');

    expect(isAiDisabled()).toBe(true);
  });

  it('throws before AI features can start when disabled mode is enabled', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'true');
    const { assertAiEnabled } = await import('../ai-mode');

    expect(() => assertAiEnabled('visual search')).toThrow(/FlashIt AI is disabled/i);
  });

  it('requires OPENAI_API_KEY when AI mode is enabled', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'false');
    vi.stubEnv('OPENAI_API_KEY', '');
    const { requireAiSecret } = await import('../ai-mode');

    expect(() => requireAiSecret('OPENAI_API_KEY', 'embeddings')).toThrow(/OPENAI_API_KEY/i);
  });
});
