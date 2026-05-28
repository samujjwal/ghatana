import { describe, expect, it } from 'vitest';
import routeContractJson from '../../../../config/phr-route-contract.json';
import { attachPhrRouteElement } from '../phrRouteElements';
import { PHR_ROLE_ORDER, phrRouteContracts, type PhrRole } from '../phrRouteContracts';
import { NotFoundPage } from '../pages/NotFoundPage';

interface CanonicalRoute {
  readonly path: string;
  readonly stability: string;
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly testId?: string;
}

interface CanonicalContract {
  readonly roleOrder: Readonly<Record<PhrRole, number>>;
  readonly routes: readonly CanonicalRoute[];
}

const canonicalContract = routeContractJson as CanonicalContract;

describe('Route Entitlement Parity', () => {
  it('projects the exact canonical JSON routes into the web contract', () => {
    expect(phrRouteContracts.map((route) => route.path)).toEqual(
      canonicalContract.routes.map((route) => route.path),
    );
  });

  it('projects the canonical role hierarchy without local overrides', () => {
    expect(PHR_ROLE_ORDER).toEqual(canonicalContract.roleOrder);
    expect(PHR_ROLE_ORDER.fchv).toBeLessThan(PHR_ROLE_ORDER.clinician);
  });

  it('requires stable routes to expose endpoint, policy, and test metadata', () => {
    const incompleteStableRoutes = canonicalContract.routes
      .filter((route) => route.stability === 'stable')
      .filter((route) => !route.apiEndpoint || !route.policyId || !route.testId)
      .map((route) => route.path);

    expect(incompleteStableRoutes).toEqual([]);
  });

  it('excludes mobile-only dashboard from the web route contract', () => {
    expect(phrRouteContracts.some((route) => route.path === '/mobile/dashboard')).toBe(false);
  });

  it('blocks hidden direct links with the not-found route element', () => {
    const hiddenRoutes = phrRouteContracts.filter((route) => route.stability === 'hidden');

    expect(hiddenRoutes.length).toBeGreaterThan(0);
    for (const route of hiddenRoutes) {
      expect(attachPhrRouteElement(route).element.type).toBe(NotFoundPage);
    }
  });
});
