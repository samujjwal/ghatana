import assert from 'node:assert/strict';
import test from 'node:test';

import { validateDuplicationExceptions } from '../check-duplication-exceptions.mjs';

test('valid duplication exceptions pass', () => {
  const issues = validateDuplicationExceptions({ version: '1.0.0', exceptions: [] }, { today: '2026-05-15' });
  assert.deepEqual(issues, []);
});

test('expired exception fails', () => {
  const issues = validateDuplicationExceptions({
    version: '1.0.0',
    exceptions: [{
      id: 'legacy-duplication',
      owner: 'platform-team',
      duplicateType: 'package',
      paths: ['a', 'b'],
      reason: 'temporary overlap',
      riskLevel: 'medium',
      expiryDate: '2026-01-01',
      removalPlan: 'Remove after migration',
      validationCheck: 'node scripts/check-example.mjs',
    }],
  }, { today: '2026-05-15' });

  assert(issues.some((issue) => issue.issue.includes('expired')));
});

test('high risk without active remediation fails', () => {
  const issues = validateDuplicationExceptions({
    version: '1.0.0',
    exceptions: [{
      id: 'high-risk-duplication',
      owner: 'platform-team',
      duplicateType: 'adapter',
      paths: ['a', 'b'],
      reason: 'temporary overlap',
      riskLevel: 'high',
      expiryDate: '2026-06-01',
      removalPlan: 'Remove after migration',
      validationCheck: 'node scripts/check-example.mjs',
    }],
  }, { today: '2026-05-15' });

  assert(issues.some((issue) => issue.issue.includes('active remediation')));
});