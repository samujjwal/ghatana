import { describe, expect, it } from 'vitest';

import phrRouteContract from '../../../../../../../phr/config/phr-route-contract.json';
import phrUseCaseBaseline from '../../../../../../../phr/config/phr-usecase-baseline.json';
import { buildPhrCompletenessOverlay } from '../phrCompletenessOverlay';

describe('PHR completeness overlay model', () => {
  it('builds route, state, and coverage model from canonical PHR contracts', () => {
    const model = buildPhrCompletenessOverlay(
      phrRouteContract,
      phrUseCaseBaseline,
      '2026-05-28T00:00:00.000Z',
    );

    expect(model.product).toBe('phr');
    expect(model.totals.routes).toBe(phrRouteContract.routes.length);
    expect(model.totals.stableRoutes).toBeGreaterThan(0);
    expect(model.totals.hiddenRoutes + model.totals.blockedRoutes).toBeGreaterThan(0);
    expect(model.totals.stableCoveragePercent).toBeGreaterThan(0);
    const navigationStateRoutes = model.routes.filter((route) => route.path === '/forbidden' || route.path === '/not-found');
    expect(navigationStateRoutes.every((route) => route.score === 100)).toBe(true);
  });

  it('marks hidden and blocked routes as guarded from direct links', () => {
    const model = buildPhrCompletenessOverlay(phrRouteContract, phrUseCaseBaseline);
    const guardedRoutes = model.routes.filter((route) => route.lifecycle === 'hidden' || route.lifecycle === 'blocked');

    expect(guardedRoutes.length).toBeGreaterThan(0);
    expect(guardedRoutes.every((route) => route.directLinkAllowed === false)).toBe(true);
    expect(model.gaps.some((gap) => gap.category === 'route-state')).toBe(false);
  });

  it('reports stable route coverage gaps across web, mobile, backend, and tests', () => {
    const model = buildPhrCompletenessOverlay(
      {
        product: 'phr',
        routes: [
          {
            path: '/records',
            label: 'Records',
            group: 'care',
            stability: 'stable',
            apiEndpoint: '/api/v1/records',
            policyId: 'phr.records.view',
          },
        ],
      },
      {
        product: 'phr',
        usecases: [
          {
            id: 'uc-patient-records',
            persona: 'patient',
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
