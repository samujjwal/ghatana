import { describe, expect, it } from 'vitest';
import routeContractJson from '../../../../config/phr-route-contract.json';
import { attachPhrRouteElement } from '../phrRouteElements';
import { PHR_ROLE_ORDER, phrRouteContracts, type PhrRole } from '../phrRouteContracts';

interface CanonicalRoute {
  readonly path: string;
  readonly minimumRole: PhrRole;
  readonly personas: readonly PhrRole[];
  readonly stability: string;
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly testId?: string;
}

interface CanonicalContract {
  readonly roleOrder: Readonly<Record<PhrRole, number>>;
  readonly routes: readonly CanonicalRoute[];
}

const canonicalContract = routeContractJson as unknown as CanonicalContract;

describe('route contract parity', () => {
  it('uses the canonical JSON route list as the TypeScript projection', () => {
    expect(phrRouteContracts).toEqual(canonicalContract.routes);
  });

  it('uses the canonical JSON role order as the TypeScript projection', () => {
    expect(PHR_ROLE_ORDER).toEqual(canonicalContract.roleOrder);
  });

  it('has stability and role metadata for every route', () => {
    for (const route of phrRouteContracts) {
      expect(route.stability).toMatch(/^(stable|preview|blocked|hidden)$/);
      expect(Object.keys(PHR_ROLE_ORDER)).toContain(route.minimumRole);
      expect(route.personas.length).toBeGreaterThan(0);
    }
  });

  it('has route elements for every canonical route', () => {
    for (const route of phrRouteContracts) {
      expect(() => attachPhrRouteElement(route)).not.toThrow();
    }
  });

  it('requires stable routes to have backend and verification metadata', () => {
    const missingMetadata = phrRouteContracts
      .filter((route) => route.stability === 'stable')
      .filter((route) => !route.apiEndpoint || !route.policyId || !route.testId)
      .map((route) => route.path);

    expect(missingMetadata).toEqual([]);
  });
});
