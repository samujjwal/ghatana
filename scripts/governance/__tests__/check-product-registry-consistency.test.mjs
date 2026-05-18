/**
 * Tests for scripts/governance/check-product-registry-consistency.mjs
 *
 * Uses Node.js built-in test runner (node:test + node:assert/strict).
 */
import assert from 'node:assert/strict';
import test from 'node:test';

import {
  runProductRegistryConsistencyChecks,
  validateProductRegistryProducts,
} from '../check-product-registry-consistency.mjs';

// ---------------------------------------------------------------------------
// Integration: real registry must pass
// ---------------------------------------------------------------------------

test('runProductRegistryConsistencyChecks passes against the real product registry', () => {
  const issues = runProductRegistryConsistencyChecks();
  assert.deepEqual(
    issues,
    [],
    `Unexpected product registry consistency issues:\n${issues.join('\n')}`,
  );
});

// ---------------------------------------------------------------------------
// §2.3 unit tests: pilot / blocked state
// ---------------------------------------------------------------------------

const VALID_PILOT = {
  'digital-marketing': {
    lifecycleExecutionAllowed: true,
    lifecycle: { enabled: true, toolchain: { 'backend-api': 'gradle-java-service' } },
    surfaces: [{ type: 'backend-api', path: 'products/digital-marketing/api' }],
    toolchain: { adapters: { 'backend-api': 'gradle-java-service' } },
    artifacts: { 'backend-api': { type: 'jvm-service', required: true } },
  },
};

test('validateProductRegistryProducts: digital-marketing pilot passes', () => {
  const issues = validateProductRegistryProducts(VALID_PILOT);
  assert.deepEqual(issues, [], `Unexpected issues:\n${issues.join('\n')}`);
});

test('validateProductRegistryProducts: non-pilot product with lifecycleExecutionAllowed=true is rejected', () => {
  const products = {
    'digital-marketing': VALID_PILOT['digital-marketing'],
    'phr': {
      lifecycleExecutionAllowed: true,
      lifecycleReadiness: {
        reasonCodes: [
          'planned-shape-only',
          'requires-consent-gate',
          'requires-pii-classification',
          'requires-audit-evidence',
          'requires-fhir-contract-validation',
          'requires-data-sovereignty-gate',
        ],
      },
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('[phr]') && i.includes('only')),
    `Expected phr to be rejected as non-pilot, got:\n${issues.join('\n')}`,
  );
  assert.ok(
    issues.some((i) => i.includes('[phr]') && i.includes('blocked list')),
    `Expected phr to be rejected as blocked, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: phr must have FHIR and consent blocker codes', () => {
  const products = {
    ...VALID_PILOT,
    'phr': {
      lifecycleExecutionAllowed: false,
      lifecycleReadiness: {
        // Missing requires-fhir-contract-validation and requires-consent-gate
        reasonCodes: ['planned-shape-only'],
      },
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('requires-fhir-contract-validation')),
    `Expected missing FHIR blocker code in issues, got:\n${issues.join('\n')}`,
  );
  assert.ok(
    issues.some((i) => i.includes('requires-consent-gate')),
    `Expected missing consent-gate blocker code in issues, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: data-cloud as platform-provider must not be enabled', () => {
  const products = {
    ...VALID_PILOT,
    'data-cloud': {
      lifecycleExecutionAllowed: true,
      lifecycleReadiness: {
        reasonCodes: [
          'disabled-observed',
          'platform-provider-mode-required',
          'requires-bootstrap-platform-separation',
          'requires-runtime-truth-provider',
        ],
      },
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('[data-cloud]') && i.includes('platform-provider')),
    `Expected platform-provider rejection for data-cloud, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: enabled pilot without toolchain adapters is rejected', () => {
  const products = {
    'digital-marketing': {
      ...VALID_PILOT['digital-marketing'],
      toolchain: undefined,
      lifecycle: { enabled: true },
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('[digital-marketing]') && i.includes('toolchain')),
    `Expected toolchain missing error, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: enabled pilot without artifacts is rejected', () => {
  const products = {
    'digital-marketing': {
      ...VALID_PILOT['digital-marketing'],
      artifacts: undefined,
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('[digital-marketing]') && i.includes('artifact')),
    `Expected artifact missing error, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: no enabled pilot raises an error', () => {
  const products = {
    'digital-marketing': {
      ...VALID_PILOT['digital-marketing'],
      lifecycleExecutionAllowed: false,
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('No product has lifecycleExecutionAllowed')),
    `Expected no-pilot error, got:\n${issues.join('\n')}`,
  );
});

test('validateProductRegistryProducts: yappc platform-provider blocker codes are required', () => {
  const products = {
    ...VALID_PILOT,
    'yappc': {
      lifecycleExecutionAllowed: false,
      lifecycleReadiness: {
        // Missing creator-lifecycle-distinct-from-kernel and artifact-intelligence-evidence-contracts-ready
        reasonCodes: ['disabled-observed', 'platform-provider-mode-required'],
      },
    },
  };
  const issues = validateProductRegistryProducts(products);
  assert.ok(
    issues.some((i) => i.includes('creator-lifecycle-distinct-from-kernel')),
    `Expected missing creator-lifecycle blocker code, got:\n${issues.join('\n')}`,
  );
  assert.ok(
    issues.some((i) => i.includes('artifact-intelligence-evidence-contracts-ready')),
    `Expected missing artifact-intelligence blocker code, got:\n${issues.join('\n')}`,
  );
});

