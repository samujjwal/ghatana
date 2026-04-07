import { describe, expect, it } from 'vitest';

import { LEGACY_ENV_FLAGS } from '../FeatureFlagProvider';

describe('LEGACY_ENV_FLAGS', () => {
  it('does not expose MOCK_AUTH anymore', () => {
    expect('MOCK_AUTH' in LEGACY_ENV_FLAGS).toBe(false);
  });

  it('keeps onboarding fallback for legacy callers', () => {
    expect(typeof LEGACY_ENV_FLAGS.ONBOARDING).toBe('boolean');
  });
});