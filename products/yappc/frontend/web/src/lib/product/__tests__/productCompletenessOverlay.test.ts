import { describe, expect, it } from 'vitest';

import { buildProductCompletenessOverlay } from '../productCompletenessOverlay';

const routeContract = {
  product: 'sample-product',
  routes: [
    {
      path: '/dashboard',
      label: 'Dashboard',
      group: 'workspace',
      stability: 'stable',
      actions: ['view'],
      cards: ['summary'],
      apiEndpoint: '/api/v1/dashboard',
      policyId: 'sample.dashboard.view',
      testId: 'sample-dashboard-view-001',
    },
    {
      path: '/admin',
      label: 'Admin',
      group: 'admin',
      stability: 'hidden',
      actions: [],
      cards: [],
    },
  ],
} as const;

const useCaseBaseline = {
  product: 'sample-product',
  usecases: [
    {
      id: 'uc-operator-dashboard',
      persona: 'operator',
      iaRoute: '/dashboard',
      webRoute: '/dashboard',
      mobileScreen: 'dashboard',
      backendApis: ['GET /dashboard'],
      status: 'implemented',
    },
  ],
} as const;

describe('product completeness overlay model', () => {
  it('builds route, state, and coverage model from Kernel product contracts', () => {
    const model = buildProductCompletenessOverlay(
      routeContract,
      useCaseBaseline,
      '2026-05-28T00:00:00.000Z',
    );

    expect(model.product).toBe('sample-product');
    expect(model.totals.routes).toBe(routeContract.routes.length);
    expect(model.totals.stableRoutes).toBe(1);
    expect(model.totals.hiddenRoutes).toBe(1);
    expect(model.totals.stableCoveragePercent).toBe(100);
  });

  it('marks hidden and blocked routes as guarded from direct links', () => {
    const model = buildProductCompletenessOverlay(routeContract, useCaseBaseline);
    const guardedRoutes = model.routes.filter((route) => route.lifecycle === 'hidden' || route.lifecycle === 'blocked');

    expect(guardedRoutes.length).toBeGreaterThan(0);
    expect(guardedRoutes.every((route) => route.directLinkAllowed === false)).toBe(true);
    expect(model.gaps.some((gap) => gap.category === 'route-state')).toBe(false);
  });

  it('reports stable route coverage gaps across web, mobile, backend, and tests', () => {
    const model = buildProductCompletenessOverlay(
      {
        product: 'sample-product',
        routes: [
          {
            path: '/records',
            label: 'Records',
            group: 'workspace',
            stability: 'stable',
            actions: ['view'],
            cards: ['table'],
            apiEndpoint: '/api/v1/records',
            policyId: 'sample.records.view',
          },
        ],
      },
      {
        product: 'sample-product',
        usecases: [
          {
            id: 'uc-operator-records',
            persona: 'operator',
            iaRoute: '/records',
            webRoute: '/records',
            backendApis: ['GET /records'],
            status: 'implemented',
          },
        ],
      },
      '2026-05-28T00:00:00.000Z',
    );

    expect(model.routes[0]).toMatchObject({
      path: '/records',
      webCovered: true,
      mobileCovered: false,
      backendCovered: true,
      testCovered: false,
      score: 50,
    });
    expect(model.gaps.map((gap) => gap.category)).toEqual(['mobile', 'test']);
  });
});
