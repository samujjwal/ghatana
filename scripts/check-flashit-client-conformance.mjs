#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

const errors = [];
const productShape = readJson('config/product-shape.json');
const declaredFlashitPackages = productShape.products.flashit?.clientPackages ?? [];

function toPosixPath(value) {
  return value.replace(/\\/g, '/');
}

function collectClientPackages(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  const entries = readdirSync(absoluteDir, { withFileTypes: true });
  const packagePaths = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }

    const packageJsonPath = path.join(relativeDir, entry.name, 'package.json');
    try {
      readFileSync(path.join(repoRoot, packageJsonPath), 'utf8');
      packagePaths.push(toPosixPath(packageJsonPath));
    } catch {
      // Ignore non-package subdirectories.
    }
  }

  return packagePaths.sort();
}

const actualFlashitPackages = collectClientPackages('products/flashit/client');
const normalizedDeclaredPackages = [...declaredFlashitPackages].map(toPosixPath).sort();
if (JSON.stringify(actualFlashitPackages) !== JSON.stringify(normalizedDeclaredPackages)) {
  errors.push(
    `FlashIt client packages must match config/product-shape.json. Actual=${JSON.stringify(actualFlashitPackages)} Declared=${JSON.stringify(normalizedDeclaredPackages)}`
  );
}

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

const webRouteAccess = readText('products/flashit/client/web/src/routeAccess.ts');
for (const token of ['resolveFlashitRole', 'isRouteAllowedForRole', 'FLASHIT_ROLE_ORDER']) {
  if (!webRouteAccess.includes(token)) {
    errors.push(`FlashIt web route access helpers must include ${token}`);
  }
}

const webLayout = readText('products/flashit/client/web/src/components/Layout.tsx');
const webShell = readText('products/flashit/client/web/src/components/FlashitProductShell.tsx');

if (!webLayout.includes('FlashitProductShell')) {
  errors.push('FlashIt web layout must compose the shared FlashitProductShell wrapper');
}

for (const token of ['ProductShell', 'flashitRouteManifest', '@ghatana/product-shell']) {
  if (!webShell.includes(token)) {
    errors.push(`FlashIt web shell must include ${token}`);
  }
}

for (const token of ['resolveFlashitRole', 'currentRole']) {
  if (!webShell.includes(token)) {
    errors.push(`FlashIt web shell must derive shared route role state with ${token}`);
  }
}

const webApp = readText('products/flashit/client/web/src/App.tsx');
for (const token of ['isRouteAllowedForRole', 'resolveFlashitRole', 'flashit-access-denied']) {
  if (!webApp.includes(token)) {
    errors.push(`FlashIt web app must enforce direct route entitlement with ${token}`);
  }
}
const webApiClient = readText('products/flashit/client/web/src/lib/api-client.ts');
if (!webApiClient.includes('FlashitApiClient')) {
  errors.push('FlashIt web must use the shared FlashitApiClient convention');
}

const mobileRouteManifest = readText('products/flashit/client/mobile/src/routeManifest.ts');
for (const token of [
  'ProductRouteCapability',
  'flashitMobileRouteManifest',
  'showInTabBar',
  'showInSettings',
  'resolveFlashitMobileRole',
  'isFlashitMobileRouteAllowed',
  'getFlashitMobileAccessibleRoutes',
]) {
  if (!mobileRouteManifest.includes(token)) {
    errors.push(`FlashIt mobile route manifest must include ${token}`);
  }
}

const mobileNavigation = readText('products/flashit/client/mobile/src/navigation/index.tsx');
for (const token of ['getFlashitMobileTabRoutes', 'flashitMobileTheme', 'currentUserAtom', 'tabRoutes.map', 'tabScreenRegistry']) {
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
