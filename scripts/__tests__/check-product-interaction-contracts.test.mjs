import assert from 'node:assert/strict';
import test from 'node:test';

import { checkProductInteractionContracts } from '../check-product-interaction-contracts.mjs';

function contract(overrides = {}) {
  return {
    contractId: 'kernel://interactions/phr.consent-status.v1',
    schemaVersion: '1.0.0',
    providerProductId: 'phr',
    consumerProductIds: ['digital-marketing'],
    mode: 'request-response',
    requestSchemaRef: 'schemas/request.json',
    responseSchemaRef: 'schemas/response.json',
    policy: {
      requiresAuth: true,
      requiresTenant: true,
      requiresConsent: true,
      piiClassification: 'healthcare-contact',
    },
    evidence: {
      required: true,
      manifestType: 'interaction-evidence',
    },
    ...overrides,
  };
}

const registry = {
  phr: {
    id: 'phr',
    lifecycleConfigPath: 'products/phr/kernel-product.yaml',
  },
  'digital-marketing': {
    id: 'digital-marketing',
    lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml',
  },
};

test('product interaction contracts pass when consumed contract has provider declaration', () => {
  const result = checkProductInteractionContracts(registry, {
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'phr'
            ? { provides: [contract()] }
            : { consumes: [contract()] },
      },
    }),
  });

  assert.deepEqual(result.errors, []);
  assert.equal(result.consumedContractCount, 1);
});

test('product interaction contracts fail when consumed contract has no provider declaration', () => {
  const result = checkProductInteractionContracts(registry, {
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'digital-marketing'
            ? { consumes: [contract()] }
            : {},
      },
    }),
  });

  assert(
    result.errors.some((error) =>
      error.includes('but no product publishes or provides it'),
    ),
  );
});

test('product interaction contracts require pii classification for consent interactions', () => {
  const result = checkProductInteractionContracts(registry, {
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'phr'
            ? {
                provides: [
                  contract({
                    policy: {
                      requiresAuth: true,
                      requiresTenant: true,
                      requiresConsent: true,
                    },
                  }),
                ],
              }
            : {},
      },
    }),
  });

  assert(
    result.errors.some((error) =>
      error.includes('policy.piiClassification is required'),
    ),
  );
});
