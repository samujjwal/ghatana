import {
  assertMobileRouteCoverage,
  getMobileRouteCoverageViolations,
  getMobileRoutes,
  getScreenKeyForPath,
  isRouteAvailableOnMobile,
} from '../mobileRouteManifest';

describe('mobile route manifest', () => {
  it('fails coverage when stable mobile routes lack screen keys', () => {
    expect(getMobileRouteCoverageViolations()).toEqual([]);
    expect(() => assertMobileRouteCoverage()).not.toThrow();
  });

  it('maps stable mobile detail routes without adding them to tab navigation', () => {
    expect(getScreenKeyForPath('/records/:recordId')).toBe('recordDetail');
    expect(isRouteAvailableOnMobile('/records/:recordId')).toBe(true);
    expect(getMobileRoutes().map((route) => route.path)).not.toContain('/records/:recordId');
  });

  it('derives tab navigation routes from the canonical mobile contract surface', () => {
    expect(getMobileRoutes().map((route) => route.key)).toEqual([
      'dashboard',
      'records',
      'consents',
      'settings',
      'notifications',
      'emergency',
    ]);
  });
});
