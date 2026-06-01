import assert from 'node:assert/strict';
import test from 'node:test';

import {
  findPhrDomainIdentifierViolations,
  findYappcPhrKnowledgeViolations,
} from '../check-yappc-no-phr-knowledge.mjs';

test('detects PHR domain identifiers in YAPPC source content', () => {
  const violations = findPhrDomainIdentifierViolations(
    'products/yappc/docs/example.md',
    'Generate a PHR route contract with PhrSpecificRenderer.',
  );

  assert.deepEqual(
    violations.map((violation) => violation.match),
    ['PHR', 'PhrSpecificRenderer'],
  );
});

test('detects PHR domain identifiers in source paths', () => {
  const violations = findPhrDomainIdentifierViolations(
    'products/yappc/frontend/web/src/lib/phr-contract-visualizer.ts',
    'export const productContractVisualizer = true;',
  );

  assert.equal(violations.length, 1);
  assert.equal(violations[0].line, 0);
});

test('does not flag generic product contract language', () => {
  const violations = findPhrDomainIdentifierViolations(
    'products/yappc/docs/kernel-product-generation.md',
    'Generate routes, policies, workflows, APIs, and screens from a Kernel ProductContract.',
  );

  assert.deepEqual(violations, []);
});

test('current tracked YAPPC scan has no PHR-specific domain identifiers', () => {
  assert.deepEqual(findYappcPhrKnowledgeViolations(), []);
});
