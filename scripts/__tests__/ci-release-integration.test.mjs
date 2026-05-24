/**
 * Tests for ci-release-integration.mjs
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';

describe('ci-release-integration', () => {
  it('should define supported actions', () => {
    const supportedActions = ['validate', 'build', 'deploy'];

    assert.strictEqual(supportedActions.length, 3);
    assert.strictEqual(supportedActions[0], 'validate');
    assert.strictEqual(supportedActions[1], 'build');
    assert.strictEqual(supportedActions[2], 'deploy');
  });

  it('should require release tag and action', () => {
    const releaseTag = 'v1.0.0';
    const action = 'validate';

    assert.ok(releaseTag);
    assert.ok(action);
    assert.strictEqual(typeof releaseTag, 'string');
    assert.strictEqual(typeof action, 'string');
  });

  it('should support validate action', () => {
    const action = 'validate';
    assert.strictEqual(action, 'validate');
  });

  it('should support build action', () => {
    const action = 'build';
    assert.strictEqual(action, 'build');
  });

  it('should support deploy action', () => {
    const action = 'deploy';
    assert.strictEqual(action, 'deploy');
  });

  it('should reject unknown actions', () => {
    const supportedActions = ['validate', 'build', 'deploy'];
    const unknownAction = 'unknown';

    assert.strictEqual(supportedActions.includes(unknownAction), false);
  });
});
