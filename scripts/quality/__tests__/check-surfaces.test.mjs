import assert from 'node:assert/strict';
import test from 'node:test';

import { runDocumentationSurfaceCheck } from '../check-documentation-surfaces.mjs';
import { runScriptSurfaceCheck } from '../check-script-surfaces.mjs';

test('documentation surface check returns structured result', () => {
  const result = runDocumentationSurfaceCheck();
  assert.equal(typeof result.passed, 'boolean');
  assert.ok(Array.isArray(result.failures));
});

test('script surface check returns structured result', () => {
  const result = runScriptSurfaceCheck();
  assert.equal(typeof result.passed, 'boolean');
  assert.ok(Array.isArray(result.failures));
  assert.ok(Array.isArray(result.warnings));
});
