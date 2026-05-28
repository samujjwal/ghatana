import assert from 'node:assert/strict';
import { mobileDashboardFixture } from '../src/__tests__/fixtures/mobileDashboardFixture';

assert.equal(mobileDashboardFixture.patient.name, 'Aarati Shrestha');
assert.ok(mobileDashboardFixture.records.length > 0, 'expected records');
assert.ok(mobileDashboardFixture.consents.length > 0, 'expected consents');
assert.ok(mobileDashboardFixture.notifications.length > 0, 'expected notifications');
