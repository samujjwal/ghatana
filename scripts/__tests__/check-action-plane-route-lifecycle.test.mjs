import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { validateActionPlaneRouteLifecycle } from '../check-action-plane-route-lifecycle.mjs';

describe('check-action-plane-route-lifecycle', () => {
  it('proves Action Plane canonical routes and AEP compatibility routes have explicit lifecycle metadata', () => {
    const report = validateActionPlaneRouteLifecycle({ writeEvidence: false });

    assert.equal(report.summary.passed, true, report.violations.join('\n'));
    assert.equal(report.productId, 'data-cloud');
    assert.equal(report.area, 'action-plane-route-lifecycle');
    assert.equal(report.policy.canonicalProductLanguage, 'Data Cloud Action Plane');
    assert.equal(report.policy.internalCompatibilityName, 'AEP');
    assert.ok(report.classification.canonicalActionPathCount > 0);
    assert.ok(report.classification.aepCompatibilityOnlyPathCount > 0);
    assert.ok(report.classification.registeredLegacyRouteCount > 0);
  });
});
