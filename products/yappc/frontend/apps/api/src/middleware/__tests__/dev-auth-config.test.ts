import { describe, expect, it } from 'vitest';

import {
  assertDevAuthBypassAllowed,
  isDevAuthBypassEnabled,
} from '../dev-auth-config';

describe('dev-auth-config', () => {
  it('enables dev auth bypass only in local development', () => {
    expect(
      isDevAuthBypassEnabled({
        NODE_ENV: 'development',
        ENABLE_DEV_AUTH_BYPASS: 'true',
        CI: 'false',
      })
    ).toBe(true);
  });

  it('disables dev auth bypass in production and CI', () => {
    expect(
      isDevAuthBypassEnabled({
        NODE_ENV: 'production',
        ENABLE_DEV_AUTH_BYPASS: 'true',
      })
    ).toBe(false);
    expect(
      isDevAuthBypassEnabled({
        NODE_ENV: 'development',
        ENABLE_DEV_AUTH_BYPASS: 'true',
        CI: 'true',
      })
    ).toBe(false);
  });

  it('throws when bypass is explicitly enabled outside development', () => {
    expect(() =>
      assertDevAuthBypassAllowed({
        NODE_ENV: 'production',
        ENABLE_DEV_AUTH_BYPASS: 'true',
      })
    ).toThrow('NODE_ENV=development');
  });
});