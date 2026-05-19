import assert from 'node:assert/strict';
import test from 'node:test';

import { validateOpeningLifecyclePilots } from '../check-opening-lifecycle-pilots.mjs';

function createRegistry() {
  return {
    'digital-marketing': {
      id: 'digital-marketing',
      lifecycleStatus: 'enabled',
      lifecycle: { enabled: true },
      lifecycleExecutionAllowed: true,
      lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml',
    },
    phr: {
      id: 'phr',
      lifecycleStatus: 'enabled',
      lifecycle: { enabled: true },
      lifecycleExecutionAllowed: true,
      lifecycleConfigPath: 'products/phr/kernel-product.yaml',
    },
    finance: {
      id: 'finance',
      lifecycleStatus: 'planned',
      lifecycle: { enabled: false },
      lifecycleExecutionAllowed: false,
    },
    flashit: {
      id: 'flashit',
      lifecycleStatus: 'planned',
      lifecycle: { enabled: false },
      lifecycleExecutionAllowed: false,
    },
    'data-cloud': {
      id: 'data-cloud',
      kind: 'platform-provider',
      lifecycleStatus: 'partial',
      lifecycle: { enabled: false },
      lifecycleExecutionAllowed: false,
    },
    yappc: {
      id: 'yappc',
      kind: 'platform-provider',
      lifecycleStatus: 'partial',
      lifecycle: { enabled: false },
      lifecycleExecutionAllowed: false,
    },
  };
}

const releasePlan = {
  schemaVersion: '1.0.0',
  openingLifecyclePilots: ['digital-marketing', 'phr'],
  disabledUntilReady: ['finance', 'flashit'],
  platformProviderValidators: ['data-cloud', 'yappc'],
};

test('opening lifecycle check accepts Digital Marketing and PHR as the only enabled pilots', () => {
  const errors = validateOpeningLifecyclePilots({
    releasePlan,
    registry: createRegistry(),
    pathExists: () => true,
  });

  assert.deepEqual(errors, []);
});

test('opening lifecycle check fails when Finance is enabled', () => {
  const registry = createRegistry();
  registry.finance.lifecycleStatus = 'enabled';
  registry.finance.lifecycle.enabled = true;
  registry.finance.lifecycleExecutionAllowed = true;

  const errors = validateOpeningLifecyclePilots({
    releasePlan,
    registry,
    pathExists: () => true,
  });

  assert(errors.some((error) => error.includes('finance must remain disabled')));
});

test('opening lifecycle check fails when Data Cloud is treated as an opening product', () => {
  const registry = createRegistry();
  registry['data-cloud'].lifecycleStatus = 'enabled';

  const errors = validateOpeningLifecyclePilots({
    releasePlan,
    registry,
    pathExists: () => true,
  });

  assert(errors.some((error) => error.includes('data-cloud must not be an opening lifecycle product')));
});
