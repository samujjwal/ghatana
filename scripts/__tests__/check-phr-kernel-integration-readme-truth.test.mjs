import assert from 'node:assert/strict';
import test from 'node:test';

import {
  findPhrKernelIntegrationReadmeTruthViolations,
} from '../check-phr-kernel-integration-readme-truth.mjs';

const TRUTHFUL_README = `
# PHR Kernel Integration Status

This document is a code-grounded status snapshot for the PHR product's Kernel integration. It is not release evidence, a compliance attestation, or proof that staging/performance/security audit gates have passed.

YAPPC must not contain PHR-specific generation logic or PHR domain knowledge.

| Area | Code-grounded status | Validation anchor |
| --- | --- | --- |
| HIPAA/staging/performance evidence | Not complete in this snapshot | Formal staging, compliance, and performance artifacts are still required before production-readiness claims |

Product-specific healthcare providers remain inside \`products/phr/**\`.
`;

test('passes when README states code-grounded status and remaining evidence work', () => {
  assert.deepEqual(findPhrKernelIntegrationReadmeTruthViolations(TRUTHFUL_README), []);
});

test('fails unsupported complete integration claims', () => {
  const violations = findPhrKernelIntegrationReadmeTruthViolations(`
# PHR Kernel Integration Status

This document is a code-grounded status snapshot for the PHR product's Kernel integration. It is not release evidence.
YAPPC must not contain PHR-specific generation logic.
Not complete in this snapshot.
Product-specific healthcare providers remain inside \`products/phr/**\`.

PHR Kernel integration is complete.
`);

  assert.equal(violations.length, 1);
  assert.match(violations[0], /unsupported completion/);
});

test('fails production readiness claims without explicit negation', () => {
  const violations = findPhrKernelIntegrationReadmeTruthViolations(`
# PHR Kernel Integration Status

This document is a code-grounded status snapshot for the PHR product's Kernel integration. It is not release evidence.
YAPPC must not contain PHR-specific generation logic.
Not complete in this snapshot.
Product-specific healthcare providers remain inside \`products/phr/**\`.

The product is production-ready.
`);

  assert.equal(violations.length, 1);
  assert.match(violations[0], /production-ready/);
});

test('allows explicitly negated readiness language', () => {
  const violations = findPhrKernelIntegrationReadmeTruthViolations(`
# PHR Kernel Integration Status

This document is a code-grounded status snapshot for the PHR product's Kernel integration. It is not release evidence.
YAPPC must not contain PHR-specific generation logic.
Not complete in this snapshot.
Product-specific healthcare providers remain inside \`products/phr/**\`.

Compliance artifacts are still required before production-readiness claims.
`);

  assert.deepEqual(violations, []);
});

test('fails when required ownership markers are missing', () => {
  const violations = findPhrKernelIntegrationReadmeTruthViolations(`
# PHR Kernel Integration Status

PHR Kernel integration notes.
`);

  assert(violations.some((violation) => violation.includes('Missing required truth marker')));
});
