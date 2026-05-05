#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

const errors = [];

function requireDeps(packageJsonPath, deps) {
  const pkg = readJson(packageJsonPath);
  const allDeps = { ...(pkg.dependencies ?? {}), ...(pkg.devDependencies ?? {}) };
  for (const dep of deps) {
    if (!allDeps[dep]) {
      errors.push(`${packageJsonPath} must declare dependency ${dep}`);
    }
  }
}

requireDeps('products/flashit/client/web/package.json', [
  '@ghatana/design-system',
  '@ghatana/product-shell',
  '@ghatana/tokens',
  '@flashit/shared',
]);

requireDeps('products/flashit/client/mobile/package.json', [
  '@ghatana/product-shell',
  '@ghatana/tokens',
  '@flashit/shared',
]);

const webRouteManifest = readText('products/flashit/client/web/src/routeManifest.tsx');
if (!webRouteManifest.includes('ProductRouteCapability')) {
  errors.push('FlashIt web route manifest must use ProductRouteCapability');
}

const webLayout = readText('products/flashit/client/web/src/components/Layout.tsx');
if (!webLayout.includes('getFlashitNavigationRoutes')) {
  errors.push('FlashIt web layout must derive navigation from getFlashitNavigationRoutes');
}

const webApiClient = readText('products/flashit/client/web/src/lib/api-client.ts');
if (!webApiClient.includes('FlashitApiClient')) {
  errors.push('FlashIt web must use the shared FlashitApiClient convention');
}

const mobileRouteManifest = readText('products/flashit/client/mobile/src/routeManifest.ts');
for (const token of ['ProductRouteCapability', 'flashitMobileRouteManifest', 'showInTabBar', 'showInSettings']) {
  if (!mobileRouteManifest.includes(token)) {
    errors.push(`FlashIt mobile route manifest must include ${token}`);
  }
}

const mobileNavigation = readText('products/flashit/client/mobile/src/navigation/index.tsx');
for (const token of ['getFlashitMobileTabRoutes', 'flashitMobileTheme']) {
  if (!mobileNavigation.includes(token)) {
    errors.push(`FlashIt mobile navigation must include ${token}`);
  }
}

const mobileTheme = readText('products/flashit/client/mobile/src/theme/kernelTheme.ts');
if (!mobileTheme.includes('@ghatana/tokens')) {
  errors.push('FlashIt mobile theme must source colors from @ghatana/tokens');
}

const mobileApiContext = readText('products/flashit/client/mobile/src/contexts/ApiContext.tsx');
if (!mobileApiContext.includes('FlashitApiClient')) {
  errors.push('FlashIt mobile must use the shared FlashitApiClient convention');
}

if (errors.length > 0) {
  console.error('FlashIt client conformance check failed:\n');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('FlashIt client conformance check passed.');
