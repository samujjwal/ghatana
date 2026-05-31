import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { mobileDashboardFixture } from '../src/__tests__/fixtures/mobileDashboardFixture';
import {
  assertMobileRouteCoverage,
  getMobileRoutes,
  getScreenKeyForPath,
  isRouteAvailableOnMobile,
} from '../src/mobileRouteManifest';

assert.equal(mobileDashboardFixture.patient.name, 'Aarati Shrestha');
assert.ok(mobileDashboardFixture.records.length > 0, 'expected records');
assert.ok(mobileDashboardFixture.consents.length > 0, 'expected consents');
assert.ok(mobileDashboardFixture.notifications.length > 0, 'expected notifications');

assert.doesNotThrow(() => assertMobileRouteCoverage());
assert.equal(getScreenKeyForPath('/records/:recordId'), 'recordDetail');
assert.equal(isRouteAvailableOnMobile('/records/:recordId'), true);
assert.deepEqual(
  getMobileRoutes().map((route) => route.key),
  ['dashboard', 'records', 'consents', 'settings', 'notifications', 'emergency'],
);
assert.equal(
  getMobileRoutes().some((route) => route.key === 'recordDetail'),
  false,
);

const appSource = readFileSync(resolve(__dirname, '../src/App.tsx'), 'utf8');
assert.equal(appSource.includes('TAB_KEYS'), false, 'mobile tabs must be derived from the route contract');
