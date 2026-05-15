import assert from 'node:assert/strict';
import { mobileDashboard } from '../src/data/mockData';

assert.equal(mobileDashboard.patient.name, 'Aarati Shrestha');
assert.ok(mobileDashboard.records.length > 0, 'expected demo records');
assert.ok(mobileDashboard.consents.length > 0, 'expected demo consents');
assert.ok(mobileDashboard.notifications.length > 0, 'expected demo notifications');
