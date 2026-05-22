import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { runAuditTask } from '../check-production-readiness-audit-tasks.mjs';

const tasks = [
  'product-feature-completeness',
  'dmos-production-workflows',
  'phr-production-workflows',
  'release-rollback-drill',
  'studio-lifecycle-control-plane',
  'interaction-durable-event-provider',
  'evidence-retention-policy',
  'production-deployment-provenance',
];

describe('production readiness audit task checks', () => {
  for (const task of tasks) {
    it(`validates ${task} against real repo artifacts`, () => {
      assert.equal(runAuditTask(task), task);
    });
  }
});
