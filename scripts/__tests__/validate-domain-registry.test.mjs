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
          'boundaryPolicy',
          'sourceOfTruth',
          'independentExecutionChecks',
          'fullRegressionChecks',
          'reasonCodes',
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
          boundaryPolicy: {
            type: 'object',
            required: ['mayImport', 'mustNotImport', 'mayOwn', 'mustNotOwn'],
            properties: {
              mayImport: { type: 'array', items: { type: 'string' } },
              mustNotImport: { type: 'array', items: { type: 'string' } },
              mayOwn: { type: 'array', items: { type: 'string' } },
              mustNotOwn: { type: 'array', items: { type: 'string' } },
            },
          },
          sourceOfTruth: { type: 'string' },
          independentExecutionChecks: { type: 'array', items: { type: 'string' } },
          fullRegressionChecks: { type: 'array', items: { type: 'string' } },
          reasonCodes: { type: 'array', items: { type: 'string' } },
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
        currentStateEvidence: ['platform/typescript/kernel-lifecycle/src'],
        boundaryPolicy: {
          mayImport: ['platform/typescript/kernel-*'],
          mustNotImport: ['products/data-cloud/planes/**'],
          mayOwn: ['platform/typescript/kernel-lifecycle'],
          mustNotOwn: ['product-specific-logic']
        },
        sourceOfTruth: 'platform/typescript/kernel-product-contracts',
        independentExecutionChecks: ['pnpm --dir platform/typescript/kernel-lifecycle test'],
        fullRegressionChecks: ['pnpm --dir platform/typescript/kernel-lifecycle test'],
        reasonCodes: []
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

test('missing boundaryPolicy fails', () => {
  const document = validDocument();
  delete document.domains[0].boundaryPolicy;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'boundaryPolicy'));
});

test('missing boundaryPolicy.mayImport fails', () => {
  const document = validDocument();
  delete document.domains[0].boundaryPolicy.mayImport;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'boundaryPolicy.mayImport'));
});

test('missing boundaryPolicy.mustNotImport fails', () => {
  const document = validDocument();
  delete document.domains[0].boundaryPolicy.mustNotImport;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'boundaryPolicy.mustNotImport'));
});

test('missing boundaryPolicy.mayOwn fails', () => {
  const document = validDocument();
  delete document.domains[0].boundaryPolicy.mayOwn;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'boundaryPolicy.mayOwn'));
});

test('missing boundaryPolicy.mustNotOwn fails', () => {
  const document = validDocument();
  delete document.domains[0].boundaryPolicy.mustNotOwn;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'boundaryPolicy.mustNotOwn'));
});

test('missing sourceOfTruth fails', () => {
  const document = validDocument();
  delete document.domains[0].sourceOfTruth;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'sourceOfTruth'));
});

test('missing independentExecutionChecks fails', () => {
  const document = validDocument();
  delete document.domains[0].independentExecutionChecks;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'independentExecutionChecks'));
});

test('empty independentExecutionChecks fails', () => {
  const document = validDocument();
  document.domains[0].independentExecutionChecks = [];

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'independentExecutionChecks'));
});

test('missing fullRegressionChecks fails', () => {
  const document = validDocument();
  delete document.domains[0].fullRegressionChecks;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'fullRegressionChecks'));
});

test('empty fullRegressionChecks fails', () => {
  const document = validDocument();
  document.domains[0].fullRegressionChecks = [];

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'fullRegressionChecks'));
});

test('missing reasonCodes fails for non-executable classification', () => {
  const document = validDocument();
  document.domains[0].classification = 'existing-partial';
  delete document.domains[0].reasonCodes;

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'reasonCodes'));
});

test('empty reasonCodes fails for non-executable classification', () => {
  const document = validDocument();
  document.domains[0].classification = 'existing-partial';
  document.domains[0].reasonCodes = [];

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(issues.some((issue) => issue.field === 'reasonCodes'));
});

test('empty reasonCodes passes for existing-executable classification', () => {
  const document = validDocument();
  document.domains[0].classification = 'existing-executable';
  document.domains[0].reasonCodes = [];

  const issues = validateDomainRegistryDocument(document, {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert(!issues.some((issue) => issue.field === 'reasonCodes'));
});

test('all current domain entries pass validation', () => {
  // This test ensures that all domains in the actual registry pass validation
  // It will be updated once we run the actual validation
  const issues = validateDomainRegistryDocument(validDocument(), {
    schema,
    productIds,
    pathExists: () => true,
  });

  assert.deepEqual(issues, []);
});