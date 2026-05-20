import assert from 'node:assert/strict';
import { demoDashboard } from '../src/data/demoDashboard';

assert.equal(demoDashboard.patient.name, 'Aarati Shrestha');
assert.ok(demoDashboard.records.length > 0, 'expected demo records');
assert.ok(demoDashboard.consents.length > 0, 'expected demo consents');
assert.ok(demoDashboard.notifications.length > 0, 'expected demo notifications');
