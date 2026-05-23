import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { validateDataCloudMaturityProof } from '../check-data-cloud-maturity-proof.mjs';

describe('check-data-cloud-maturity-proof', () => {
  it('validates the Data Cloud maturity proof matrix against repo evidence', () => {
    const report = validateDataCloudMaturityProof();

    assert.equal(report.summary.passed, true, report.violations.join('\n'));
    assert.equal(report.productId, 'data-cloud');
    assert.equal(report.targetScore, 5);
  });
});
