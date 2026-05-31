import { describe, expect, it } from 'vitest';
import routeContractJson from '../../../../config/phr-route-contract.json';
import { attachPhrRouteElement, isPhrRouteBrowserMountable } from '../phrRouteElements';
import { phrRoutePlugin } from '../phrRoutePlugin';
import { phrBrowserRouteManifest } from '../routes';
import {
  PHR_ROLE_ORDER,
  getRouteDescriptionI18nKey,
  getRouteLabelI18nKey,
  phrRouteContracts,
  type PhrRole,
} from '../phrRouteContracts';

interface CanonicalRoute {
  readonly path: string;
  readonly minimumRole: PhrRole;
  readonly personas: readonly PhrRole[];
  readonly stability: string;
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly testId?: string;
  readonly i18nKey: string;
  readonly descriptionI18nKey: string;
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

  it('projects route registration through the Kernel route plugin', () => {
    expect(phrRoutePlugin.routes).toBe(phrRouteContracts);
    expect(phrRoutePlugin.roleOrder).toEqual(canonicalContract.roleOrder);
    expect(phrRoutePlugin.getBrowserRoutes().map((route) => route.path)).toEqual(
      phrBrowserRouteManifest.map((route) => route.path),
    );
  });

  it('keeps Kernel route capability metadata in parity with stable contract routes', () => {
    const capabilityByPath = new Map(
      phrRoutePlugin.capabilities.map((capability) => [capability.path, capability] as const),
    );

    for (const route of phrRouteContracts.filter((candidate) => candidate.stability === 'stable')) {
      const capability = capabilityByPath.get(route.path);
      expect(capability, route.path).toBeDefined();
      expect(capability?.apiEndpoint).toBe(route.apiEndpoint);
      expect(capability?.policyId).toBe(route.policyId);
      expect(capability?.testId).toBe(route.testId);
      expect(capability?.directLinkAllowed).toBe(true);
      expect(capability?.discoverable).toBe(true);
    }
  });

  it('has stability and role metadata for every route', () => {
    for (const route of phrRouteContracts) {
      expect(route.stability).toMatch(/^(stable|preview|blocked|hidden|deferred|removed)$/);
      expect(Object.keys(PHR_ROLE_ORDER)).toContain(route.minimumRole);
      expect(route.personas.length).toBeGreaterThan(0);
    }
  });

  it('has route elements for every browser-mountable canonical route', () => {
    for (const route of phrRouteContracts.filter(isPhrRouteBrowserMountable)) {
      expect(() => attachPhrRouteElement(route)).not.toThrow();
    }
  });

  it('keeps hidden, deferred, and removed routes out of the browser route manifest', () => {
    const mountedPaths = new Set(phrBrowserRouteManifest.map((route) => route.path));
    for (const route of phrRouteContracts.filter((candidate) => !isPhrRouteBrowserMountable(candidate))) {
      expect(mountedPaths.has(route.path), route.path).toBe(false);
      expect(attachPhrRouteElement(route).path).toBe(route.path);
    }
  });

  it('requires stable routes to have backend and verification metadata', () => {
    const missingMetadata = phrRouteContracts
      .filter((route) => route.stability === 'stable')
      .filter((route) => !route.apiEndpoint || !route.policyId || !route.testId)
      .map((route) => route.path);

    expect(missingMetadata).toEqual([]);
  });

  it('requires route label and description i18n keys without raw text fallback', () => {
    for (const route of phrRouteContracts) {
      expect(getRouteLabelI18nKey(route)).toBe(route.i18nKey);
      expect(getRouteDescriptionI18nKey(route)).toBe(route.descriptionI18nKey);
      expect(getRouteLabelI18nKey(route)).not.toBe(route.label);
      expect(getRouteDescriptionI18nKey(route)).not.toBe(route.description);
    }
  });
});
