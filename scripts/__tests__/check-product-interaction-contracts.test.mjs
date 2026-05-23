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

const coveredSourceRefs = [
  {
    path: 'products/phr/src/main/java/ConsentStatusInteractionHandler.java',
    source: 'ProductInteractionHandler kernel://interactions/phr.consent-status.v1',
    isHandler: true,
    isTest: false,
  },
  {
    path: 'integration-tests/cross-service-workflow/src/test/java/PhrDmosProductInteractionContractTest.java',
    source: 'kernel://interactions/phr.consent-status.v1 consent-status-request.v1.json consent-status-response.v1.json',
    isHandler: false,
    isTest: true,
  },
];

test('product interaction contracts pass when consumed contract has provider declaration', () => {
  const result = checkProductInteractionContracts(registry, {
    validateSchemaRefs: false,
    sourceRefs: coveredSourceRefs,
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
  assert.equal(result.coverageSummary.passed, true);
  assert.equal(result.coverageMatrix.length, 2);
  assert(
    result.coverageMatrix.some((row) =>
      row.declarationKind === 'provides'
      && row.handler.status === 'covered'
      && row.tests.status === 'covered',
    ),
  );
});

test('product interaction contracts fail when consumed contract has no provider declaration', () => {
  const result = checkProductInteractionContracts(registry, {
    validateSchemaRefs: false,
    sourceRefs: coveredSourceRefs,
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
    validateSchemaRefs: false,
    sourceRefs: coveredSourceRefs,
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

test('product interaction contracts fail when schema hash drifts from schema content', () => {
  const result = checkProductInteractionContracts(registry, {
    sourceRefs: coveredSourceRefs,
    readSchemaFile: () => JSON.stringify({ type: 'object' }),
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'phr'
            ? {
                provides: [
                  contract({
                    requestSchemaSha256: '0'.repeat(64),
                    responseSchemaSha256: '0'.repeat(64),
                  }),
                ],
              }
            : {},
      },
    }),
  });

  assert(
    result.errors.some((error) =>
      error.includes('requestSchemaSha256 is 0000000000000000000000000000000000000000000000000000000000000000'),
    ),
  );
});

test('product interaction contracts fail when consumer schema hash differs from provider hash', () => {
  const schema = JSON.stringify({ type: 'object' });
  const digest = '7a38bf81f383f69433ad6e90063b36dae41e6b357c797b8a14be4175a2c59a7b';
  const result = checkProductInteractionContracts(registry, {
    sourceRefs: coveredSourceRefs,
    readSchemaFile: () => schema,
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'phr'
            ? {
                provides: [
                  contract({
                    requestSchemaSha256: digest,
                    responseSchemaSha256: digest,
                  }),
                ],
              }
            : {
                consumes: [
                  contract({
                    requestSchemaSha256: '1'.repeat(64),
                    responseSchemaSha256: digest,
                  }),
                ],
              },
      },
    }),
  });

  assert(
    result.errors.some((error) =>
      error.includes('requestSchemaSha256') && error.includes('does not match provider'),
    ),
  );
});

test('product interaction contracts fail closed when handler or test coverage is missing', () => {
  const result = checkProductInteractionContracts(registry, {
    validateSchemaRefs: false,
    sourceRefs: [],
    validateBridgeHandlers: false,
    loadKernelProduct: (productId) => ({
      config: {
        interactions:
          productId === 'phr'
            ? { provides: [contract()] }
            : { consumes: [contract()] },
      },
    }),
  });

  assert.equal(result.coverageSummary.passed, false);
  assert(
    result.errors.some((error) =>
      error.includes('is missing ProductInteractionHandler coverage'),
    ),
  );
  assert(
    result.errors.some((error) =>
      error.includes('is missing test coverage'),
    ),
  );
});
