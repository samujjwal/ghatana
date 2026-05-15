import assert from 'node:assert/strict';
import test from 'node:test';

import { validateDuplicationExceptions } from '../check-duplication-exceptions.mjs';

const schema = {
  type: 'object',
  required: ['version', 'exceptions'],
  properties: {
    version: { type: 'string' },
    exceptions: {
      type: 'array',
      items: {
        type: 'object',
        required: ['id', 'owner', 'duplicateType', 'paths', 'reason', 'riskLevel', 'expiryDate', 'removalPlan', 'validationCheck'],
        properties: {
          id: { type: 'string' },
          owner: { type: 'string' },
          duplicateType: { type: 'string' },
          paths: { type: 'array', minItems: 2, items: { type: 'string' } },
          reason: { type: 'string' },
          riskLevel: { type: 'string', enum: ['low', 'medium', 'high'] },
          expiryDate: { type: 'string', format: 'date' },
          removalPlan: { type: 'string' },
          validationCheck: { type: 'string' },
        },
      },
    },
  },
};

test('valid duplication exceptions pass', () => {
  const issues = validateDuplicationExceptions({ version: '1.0.0', exceptions: [] }, { today: '2026-05-15', schema });
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
  }, { today: '2026-05-15', schema });

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
  }, { today: '2026-05-15', schema });

  assert(issues.some((issue) => issue.issue.includes('active remediation')));
});

test('schema violation fails on unknown risk level', () => {
  const issues = validateDuplicationExceptions({
    version: '1.0.0',
    exceptions: [{
      id: 'schema-risk',
      owner: 'platform-team',
      duplicateType: 'adapter',
      paths: ['a', 'b'],
      reason: 'temporary overlap',
      riskLevel: 'critical',
      expiryDate: '2026-06-01',
      removalPlan: 'active remediation underway',
      validationCheck: 'node scripts/check-example.mjs',
    }],
  }, { today: '2026-05-15', schema });

  assert(issues.some((issue) => issue.issue.includes('schema violation')));
});