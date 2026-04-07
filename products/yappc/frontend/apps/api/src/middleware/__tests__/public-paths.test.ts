import { describe, expect, it } from 'vitest';

import { isPublicPath } from '../public-paths';

describe('isPublicPath', () => {
  it('allows top-level health and metrics routes', () => {
    expect(isPublicPath('/health')).toBe(true);
    expect(isPublicPath('/metrics')).toBe(true);
  });

  it('allows public auth endpoints under versioned prefixes', () => {
    expect(isPublicPath('/api/auth/login')).toBe(true);
    expect(isPublicPath('/v1/auth/refresh')).toBe(true);
  });

  it('rejects protected auth identity routes', () => {
    expect(isPublicPath('/api/auth/me')).toBe(false);
  });

  it('ignores query strings while evaluating access', () => {
    expect(isPublicPath('/api/auth/login?redirect=/app')).toBe(true);
  });
});
