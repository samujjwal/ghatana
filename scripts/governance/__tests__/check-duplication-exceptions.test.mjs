/**
 * Tests for scripts/governance/check-duplication-exceptions.mjs
 *
 * Uses Node.js built-in test runner (node:test + node:assert/strict).
 */
import assert from 'node:assert/strict';
import test from 'node:test';

import { runDuplicationExceptionChecks } from '../check-duplication-exceptions.mjs';

// ---------------------------------------------------------------------------
// Integration: real exception registry must pass
// ---------------------------------------------------------------------------

test('runDuplicationExceptionChecks passes against the real duplication-exceptions.json', () => {
  const issues = runDuplicationExceptionChecks();
  assert.deepEqual(
    issues,
    [],
    `Unexpected duplication exception issues:\n${issues.join('\n')}`,
  );
});
