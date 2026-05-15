import assert from 'node:assert/strict';
import test from 'node:test';

import { validateDomainRegistryDocument } from '../validate-domain-registry.mjs';

const productIds = new Set(['digital-marketing', 'phr', 'finance', 'flashit', 'data-cloud', 'yappc']);
const schema = {
  type: 'object',
  required: ['version', 'domains'],
  properties: {
    version: { type: 'string' },
    domains: {
      type: 'array',
      minItems: 1,
      items: {
        type: 'object',
        required: [
          'id',
          'name',
          'ownerLayer',
          'classification',
          'primaryLocations',
          'secondaryLocations',
          'allowedConsumers',
          'forbiddenDependencies',
          'requiredChecks',
          'productAssociations',
          'currentStateEvidence',
        ],
        properties: {
          id: { type: 'string' },
          name: { type: 'string' },
          ownerLayer: { type: 'string' },
          classification: {
            type: 'string',
            enum: [
              'existing-executable',
              'existing-partial',
              'declared-only',
              'target-architecture',
              'anti-pattern',
            ],
          },
          primaryLocations: { type: 'array', minItems: 1, items: { type: 'string' } },
          secondaryLocations: { type: 'array', items: { type: 'string' } },
          allowedConsumers: { type: 'array', items: { type: 'string' } },
          forbiddenDependencies: { type: 'array', items: { type: 'string' } },
          requiredChecks: { type: 'array', items: { type: 'string' } },
          productAssociations: { type: 'array', items: { type: 'string' } },
          currentStateEvidence: { type: 'array', items: { type: 'string' } },
        },
      },
    },
  },
};

function validDocument() {
  return {
    version: '1.0.0',
    domains: [
      {
        id: 'product-development-kernel-lifecycle',
        name: 'Product Development Kernel Lifecycle',
        ownerLayer: 'platform-kernel',
        classification: 'existing-executable',
        primaryLocations: ['platform/typescript/kernel-lifecycle'],
        secondaryLocations: ['scripts/check-kernel-platform-lifecycle.mjs'],
        allowedConsumers: ['digital-marketing'],
        forbiddenDependencies: ['products/data-cloud/planes'],
        requiredChecks: ['pnpm --dir platform/typescript/kernel-lifecycle test'],
        productAssociations: ['digital-marketing'],
        currentStateEvidence: ['platform/typescript/kernel-lifecycle/src']
      }
    ]
  };
}

test('valid registry passes', () => {
  const issues = validateDomainRegistryDocument(validDocument(), {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert.deepEqual(issues, []);
});

test('missing required field fails', () => {
  const document = validDocument();
  delete document.domains[0].name;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert.match(issues[0].field, /name/);
});

test('unknown classification fails', () => {
  const document = validDocument();
  document.domains[0].classification = 'implemented';

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'classification'));
});

test('invalid product association fails', () => {
  const document = validDocument();
  document.domains[0].productAssociations = ['unknown-product'];

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.issue.includes('unknown product association')));
});

test('missing path fails for non-target architecture domains', () => {
  const issues = validateDomainRegistryDocument(validDocument(), {
    schema,
    productIds,
    pathExists: () => false,
  });

  assert(issues.some((issue) => issue.issue.includes('does not exist')));
});

test('target-architecture missing path passes when classification is explicit', () => {
  const document = validDocument();
  document.domains[0].classification = 'target-architecture';

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => false,
  });

  assert(!issues.some((issue) => issue.issue.includes('does not exist')));
});

test('schema violation fails when required array is missing', () => {
  const document = validDocument();
  delete document.domains[0].requiredChecks;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.issue.includes('schema violation')));
});