import { describe, expect, it } from 'vitest';
import {
  PHR_ROLE_ORDER,
  isRouteAllowedForRole,
  phrRouteContracts,
  type PhrRouteContract,
} from '../routeManifest';
import type { PhrRole } from '../phrRouteContracts';
import { formatPhrDate, t } from '../i18n/phrI18n';

const roles: readonly PhrRole[] = ['patient', 'caregiver', 'fchv', 'clinician', 'admin'];

function route(path: string): PhrRouteContract {
  const found = phrRouteContracts.find((candidate) => candidate.path === path);
  if (!found) {
    throw new Error(`Route not found in PHR route contract: ${path}`);
  }
  return found;
}

describe('PHR privacy hardening', () => {
  it('keeps the role hierarchy ordered from patient to admin', () => {
    expect(PHR_ROLE_ORDER.patient).toBeLessThan(PHR_ROLE_ORDER.caregiver);
    expect(PHR_ROLE_ORDER.caregiver).toBeLessThan(PHR_ROLE_ORDER.fchv);
    expect(PHR_ROLE_ORDER.fchv).toBeLessThan(PHR_ROLE_ORDER.clinician);
    expect(PHR_ROLE_ORDER.clinician).toBeLessThan(PHR_ROLE_ORDER.admin);
  });

  it('denies patient, caregiver, and FCHV roles from emergency and audit routes', () => {
    for (const restrictedRoute of [route('/emergency'), route('/audit')]) {
      expect(isRouteAllowedForRole(restrictedRoute, 'patient')).toBe(false);
      expect(isRouteAllowedForRole(restrictedRoute, 'caregiver')).toBe(false);
      expect(isRouteAllowedForRole(restrictedRoute, 'fchv')).toBe(false);
    }
  });

  it('keeps admin-only release readiness unavailable to lower-privilege roles', () => {
    const releaseReadiness = route('/release-readiness');

    expect(releaseReadiness.minimumRole).toBe('admin');
    for (const role of roles.filter((candidate) => candidate !== 'admin')) {
      expect(isRouteAllowedForRole(releaseReadiness, role)).toBe(false);
    }
    expect(isRouteAllowedForRole(releaseReadiness, 'admin')).toBe(true);
  });

  it('requires stable direct-link routes to have backend, policy, and test metadata', () => {
    const directLinkStableRoutes = phrRouteContracts.filter(
      (candidate) => candidate.stability === 'stable' && !candidate.hidden && !candidate.blocked,
    );

    expect(directLinkStableRoutes.length).toBeGreaterThan(0);
    for (const stableRoute of directLinkStableRoutes) {
      expect(stableRoute.apiEndpoint, stableRoute.path).toMatch(/^\/api\//);
      expect(stableRoute.policyId, stableRoute.path).toMatch(/^phr\./);
      expect(stableRoute.testId, stableRoute.path).toMatch(/^phr-/);
    }
  });

  it('does not expose mobile-only routes as web direct links', () => {
    expect(phrRouteContracts.some((candidate) => candidate.path === '/mobile/dashboard')).toBe(false);
  });

  it('keeps hidden or blocked routes out of the stable direct-link surface', () => {
    for (const candidate of phrRouteContracts) {
      if (candidate.hidden || candidate.blocked) {
        expect(candidate.stability, candidate.path).not.toBe('stable');
      }
    }
  });

  it('uses i18n helpers for route labels and locale-specific dates', () => {
    expect(t('route.dashboard.label', {}, 'en')).toBe('Dashboard');
    expect(t('route.dashboard.label', {}, 'ne')).not.toBe('Dashboard');
    expect(formatPhrDate('2026-05-01T00:00:00Z', 'en')).not.toEqual(
      formatPhrDate('2026-05-01T00:00:00Z', 'ne'),
    );
  });
});
