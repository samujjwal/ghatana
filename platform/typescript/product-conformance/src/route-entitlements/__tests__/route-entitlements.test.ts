import { describe, expect, it } from 'vitest';
import { validateRouteEntitlementPayload } from '../index';

const validPayload = {
  product: 'flashit',
  principalId: 'principal-1',
  tenantId: 'tenant-1',
  role: 'member',
  persona: 'reflector',
  tier: 'core',
  correlationId: 'corr-1',
  routes: [
    {
      path: '/moments',
      label: 'Moments',
      description: 'Browse moments',
      iconName: 'file-text',
      group: 'Capture',
      minimumRole: 'member',
      lifecycle: 'stable',
      discoverable: true,
      personas: ['reflector'],
      tiers: ['core'],
      actions: ['view-moments'],
      cards: ['moment-list'],
    },
  ],
  actions: [{ id: 'view-moments', label: 'View moments', routePath: '/moments' }],
  cards: [{ id: 'moment-list', title: 'Moment list', routePath: '/moments', surface: 'dashboard' }],
} as const;

describe('route entitlement payload validator', () => {
  it('accepts a ProductRouteEntitlement-shaped payload', () => {
    const result = validateRouteEntitlementPayload(validPayload, { expectedProduct: 'flashit' });

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.entitlement?.routes[0]?.path).toBe('/moments');
    expect(result.entitlement?.actions?.[0]?.routePath).toBe('/moments');
  });

  it('reports product, identity, and route shape errors without throwing', () => {
    const result = validateRouteEntitlementPayload(
      {
        product: 'phr',
        principalId: '',
        role: '',
        routes: [{ path: '', label: '', lifecycle: 'removed', actions: ['read', 1] }],
        actions: [{ id: '', label: '', requiresConfirmation: 'yes' }],
        cards: [{ id: '', title: '', surface: 'panel' }],
      },
      { expectedProduct: 'flashit' },
    );

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('entitlement.product must be flashit');
    expect(result.errors).toContain('entitlement.principalId must be a non-empty string');
    expect(result.errors).toContain('entitlement.tenantId must be a non-empty string');
    expect(result.errors).toContain('entitlement.role must be a non-empty string');
    expect(result.errors).toContain('entitlement.routes[0].lifecycle must be one of stable, preview, boundary, deprecated');
    expect(result.errors).toContain('entitlement.routes[0].actions must be a string array when present');
    expect(result.errors).toContain('entitlement.actions[0].requiresConfirmation must be boolean when present');
    expect(result.errors).toContain('entitlement.cards[0].surface must be one of dashboard, detail, sidebar, modal');
  });

  it('can relax principal and tenant requirements for anonymous preview checks', () => {
    const result = validateRouteEntitlementPayload(
      {
        product: 'flashit',
        role: 'member',
        routes: [{ path: '/', label: 'Home' }],
      },
      { expectedProduct: 'flashit', requirePrincipal: false, requireTenant: false },
    );

    expect(result.valid).toBe(true);
  });
});
