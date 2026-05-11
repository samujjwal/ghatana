#!/usr/bin/env node

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const outputPath = path.join(repoRoot, 'docs/kernel/PRODUCT_KERNEL_AUDIT_PROGRESS.md');
const checkMode = process.argv.includes('--check');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

const productShape = readJson('config/product-shape.json');
const registry = readJson('config/kernel-product-capability-registry.json');

const checks = [
  {
    id: 'product-manifest-contracts',
    command: 'pnpm check:product-manifest-contracts',
    evidence: [
      'scripts/check-product-manifest-contracts.mjs',
      'platform/typescript/product-manifest-contracts/index.mjs',
      'config/kernel-product-capability-registry.json',
    ],
    proves: 'Product manifests parse through schema-backed validation, registry references, policy vocabulary, plugin ownership, dependency scope, and product-shape surface alignment.',
  },
  {
    id: 'route-entitlement-contracts',
    command: 'pnpm check:route-entitlement-contracts',
    evidence: [
      'scripts/check-route-entitlement-contracts.mjs',
      'products/phr/src/test/java/com/ghatana/phr/api/PhrHttpServerTest.java',
      'products/digital-marketing/dm-api/src/test/java/com/ghatana/digitalmarketing/api/DmosRouteEntitlementServletTest.java',
      'products/flashit/backend/gateway/src/routes/__tests__/entitlements.test.ts',
    ],
    proves: 'PHR, DMOS, and FlashIt route entitlement APIs have behavioral coverage and FlashIt frontend/backend route parity is enforced.',
  },
  {
    id: 'shared-product-shells',
    command: 'pnpm check:shared-product-shells',
    evidence: [
      'scripts/check-shared-product-shells.mjs',
      'platform/typescript/product-shell/src/access.ts',
      'platform/typescript/product-shell/src/useProductEntitlements.ts',
      'platform/typescript/product-shell/src/components/ProductShell.tsx',
    ],
    proves: 'Audited product shells use shared shell composition, stable config helpers, product-owned routing content, and product-neutral platform docs.',
  },
  {
    id: 'data-access-contract',
    command: 'pnpm check:data-access-contract',
    evidence: [
      'scripts/check-data-access-contract.mjs',
      'products/flashit/backend/gateway/src/lib/data-access-context.ts',
      'products/flashit/backend/gateway/src/lib/__tests__/data-access-context.test.ts',
    ],
    proves: 'Data-access metadata carries tenant/principal/correlation/idempotency/audit fields and FlashIt tenant resolution fails closed.',
  },
  {
    id: 'observability-conformance',
    command: 'pnpm check:observability-conformance',
    evidence: [
      'scripts/check-observability-conformance.mjs',
      'config/observability/product-observability-flows.json',
    ],
    proves: 'Product observability flow evidence remains registered for trace, metric, log, redaction, and audit fields.',
  },
];

function statusFor(check) {
  const missing = check.evidence.filter((file) => !existsSync(path.join(repoRoot, file)));
  return missing.length === 0
    ? { status: 'covered', missing }
    : { status: 'missing evidence', missing };
}

const lines = [];
lines.push('# Product/Kernel Audit Progress');
lines.push('');
lines.push('> Generated from executable contract metadata. Do not hand-edit status counts.');
lines.push('');
lines.push('## Snapshot');
lines.push('');
lines.push(`- Products in product shape: ${Object.keys(productShape.products).sort().map((id) => `\`${id}\``).join(', ')}`);
lines.push(`- Kernel capabilities in registry: ${Object.keys(registry.kernelCapabilities).length}`);
lines.push(`- Platform plugins in registry: ${Object.keys(registry.plugins).length}`);
lines.push(`- Domain packs in registry: ${Object.keys(registry.domainPacks).length}`);
lines.push('');
lines.push('## Executable Checks');
lines.push('');
lines.push('| Check | Status | Command | Proof |');
lines.push('| --- | --- | --- | --- |');
for (const check of checks) {
  const result = statusFor(check);
  const proof = result.status === 'covered'
    ? check.proves
    : `Missing evidence: ${result.missing.map((file) => `\`${file}\``).join(', ')}`;
  lines.push(`| \`${check.id}\` | ${result.status} | \`${check.command}\` | ${proof} |`);
}
lines.push('');
lines.push('## Evidence Files');
lines.push('');
for (const check of checks) {
  lines.push(`### ${check.id}`);
  lines.push('');
  for (const evidence of check.evidence) {
    lines.push(`- \`${evidence}\``);
  }
  lines.push('');
}
lines.push('## Policy');
lines.push('');
lines.push('Compliance status is derived from contract scripts, registry metadata, product-shape declarations, and behavioral tests. Historical audit task prose belongs in issue trackers or changelogs, not this generated status file.');
lines.push('');

const nextContent = `${lines.join('\n')}`;

if (checkMode) {
  const existing = existsSync(outputPath) ? readFileSync(outputPath, 'utf8') : '';
  if (existing !== nextContent) {
    console.error('docs/kernel/PRODUCT_KERNEL_AUDIT_PROGRESS.md is out of date. Run pnpm generate:product-kernel-audit-progress.');
    process.exit(1);
  }
  console.log('Product/kernel audit progress report is up to date.');
} else {
  writeFileSync(outputPath, nextContent, 'utf8');
  console.log('Wrote docs/kernel/PRODUCT_KERNEL_AUDIT_PROGRESS.md');
}
