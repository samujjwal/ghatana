#!/usr/bin/env node

import assert from 'node:assert/strict';
import test from 'node:test';

import {
  loadCanonicalRegistry,
  resolveAffectedProducts,
} from './resolve-affected-products.mjs';

const registry = loadCanonicalRegistry();

function affected(files, options = {}) {
  return resolveAffectedProducts(files, registry, options).affectedProducts;
}

test('resolves a product-only source change to the owning product', () => {
  assert.deepEqual(
    affected(['products/phr/src/main/java/com/ghatana/phr/kernel/service/AppointmentService.java']),
    ['phr'],
  );
});

test('resolves pnpm package glob changes from registry package declarations', () => {
  assert.deepEqual(
    affected(['products/flashit/client/web/src/routeAccess.ts']),
    ['flashit'],
  );
});

test('resolves shared Java platform changes to Gradle-backed active entries', () => {
  const products = affected(['platform/java/security/src/main/java/com/ghatana/platform/security/Policy.java']);

  assert(products.includes('phr'));
  assert(products.includes('finance'));
  assert(products.includes('digital-marketing'));
  assert(products.includes('flashit'));
  assert(products.includes('data-cloud'));
  assert(!products.includes('aura'));
});

test('resolves platform contract changes to active Gradle-backed business products', () => {
  const products = affected(
    ['platform/contracts/src/main/java/com/ghatana/contracts/ProductInteractionContract.java'],
    { businessProductsOnly: true },
  );

  assert(products.includes('phr'));
  assert(products.includes('finance'));
  assert(products.includes('digital-marketing'));
  assert(products.includes('flashit'));
  assert(!products.includes('data-cloud'));
});

test('resolves shared UI platform changes to pnpm-backed active entries', () => {
  const products = affected(['platform/typescript/product-shell/src/components/ProductShell.tsx']);

  assert(products.includes('phr'));
  assert(products.includes('digital-marketing'));
  assert(products.includes('flashit'));
  assert(products.includes('data-cloud'));
  assert(products.includes('yappc'));
  assert(products.includes('finance'));
});

test('resolves registry changes to every active non-demo CI entry', () => {
  const products = affected(['config/canonical-product-registry.json']);

  assert(products.includes('phr'));
  assert(products.includes('finance'));
  assert(products.includes('security-gateway'));
  assert(!products.includes('aura'));
});

test('supports business-product-only CI scopes', () => {
  const expected = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product')
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .map(([productId]) => productId)
    .sort();

  assert.deepEqual(
    affected(['config/canonical-product-registry.json'], { businessProductsOnly: true }),
    expected,
  );
});

test('treats platform-kernel changes as shared impact for all active business products', () => {
  const expected = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product')
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .map(([productId]) => productId)
    .sort();

  assert.deepEqual(
    affected(['platform-kernel/kernel-core/build.gradle.kts'], { businessProductsOnly: true }),
    expected,
  );
});

test('treats shared-services changes as shared impact for all active business products', () => {
  const expected = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product')
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .map(([productId]) => productId)
    .sort();

  assert.deepEqual(
    affected(['shared-services/tenant-bridge/src/main/java/com/ghatana/shared/TenantBridge.java'], { businessProductsOnly: true }),
    expected,
  );
});

test('treats design-system package changes as shared impact for active pnpm-backed products', () => {
  const products = affected(['platform/typescript/design-system/src/atoms/Button.tsx']);

  assert(products.includes('phr'));
  assert(products.includes('digital-marketing'));
  assert(products.includes('flashit'));
  assert(products.includes('data-cloud'));
  assert(products.includes('yappc'));
});

test('treats shared release script changes as global impact for active CI products', () => {
  const expected = Object.entries(registry)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .filter(([, product]) => product.kind !== 'demo/example')
    .map(([productId]) => productId)
    .sort();

  assert.deepEqual(
    affected(['scripts/check-product-release-readiness.mjs']),
    expected,
  );
});

test('returns no affected products for docs-only changes', () => {
  const result = resolveAffectedProducts(
    ['docs/kernel/KERNEL_PRODUCT_BOUNDARY.md', 'products/phr/README.md'],
    registry,
  );

  assert.deepEqual(result.affectedProducts, []);
  assert.equal(result.docsOnly, true);
});
