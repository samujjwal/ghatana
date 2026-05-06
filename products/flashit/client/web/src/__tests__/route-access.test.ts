import { describe, expect, it } from 'vitest';
import { isRouteAllowedForRole, resolveFlashitRole } from '../routeAccess';

describe('FlashIt route access', () => {
  it('derives roles from tier and admin markers', () => {
    expect(resolveFlashitRole(null)).toBe('guest');
    expect(resolveFlashitRole({ tier: 'FREE' })).toBe('member');
    expect(resolveFlashitRole({ tier: 'pro' })).toBe('premium');
    expect(resolveFlashitRole({ role: 'ADMIN' })).toBe('admin');
    expect(resolveFlashitRole({ isAdmin: true, tier: 'FREE' })).toBe('admin');
  });

  it('enforces minimum roles consistently for route contracts', () => {
    const analyticsRoute = { minimumRole: 'premium' };
    const dashboardRoute = { minimumRole: 'member' };
    const publicRoute = {};

    expect(isRouteAllowedForRole(analyticsRoute, resolveFlashitRole({ tier: 'FREE' }))).toBe(false);
    expect(isRouteAllowedForRole(analyticsRoute, resolveFlashitRole({ tier: 'PRO' }))).toBe(true);
    expect(isRouteAllowedForRole(dashboardRoute, resolveFlashitRole({ tier: 'FREE' }))).toBe(true);
    expect(isRouteAllowedForRole(publicRoute, resolveFlashitRole(null))).toBe(true);
  });
});
