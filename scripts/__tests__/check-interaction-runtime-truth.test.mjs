import assert from 'node:assert/strict';
import test from 'node:test';

import { checkInteractionRuntimeTruth } from '../check-interaction-runtime-truth.mjs';

const registry = {
  producer: {
    id: 'producer',
    lifecycleConfigPath: 'products/producer/kernel-product.yaml',
  },
  consumer: {
    id: 'consumer',
    lifecycleConfigPath: 'products/consumer/kernel-product.yaml',
  },
};

test('interaction runtime truth validates event contract topics through injected product configs', () => {
  const result = checkInteractionRuntimeTruth({
    registryPath: 'not-used',
    registryDocument: registry,
    repoRoot: process.cwd(),
    skipPlanner: true,
    validateEventBroker: false,
    loadKernelProduct: (productId) => ({
      interactions:
        productId === 'producer'
          ? {
              publishes: [
                {
                  contractId: 'kernel://interactions/test.event.v1',
                  mode: 'event-publish',
                  evidence: { required: false },
                },
              ],
            }
          : {},
    }),
  });

  assert.equal(result.eventContracts, 1);
  assert(
    result.errors.some((error) => error.includes('event contract must declare topic')),
  );
});
