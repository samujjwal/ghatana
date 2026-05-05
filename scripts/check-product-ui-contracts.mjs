#!/usr/bin/env node

import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const manifest = JSON.parse(readFileSync(resolve(repoRoot, 'config/product-shape.json'), 'utf8'));
const requiredScripts = ['lint', 'type-check', 'test', 'test:coverage', 'test:e2e', 'test:e2e:a11y', 'build'];
const approvedRouterVersion = '^7.14.0';
const disallowedUiLibraries = ['@mui/material'];
const violations = [];

for (const [product, config] of Object.entries(manifest.products)) {
  if (!config.ui) {
    continue;
  }

  const packagePath = resolve(repoRoot, config.packageJson);
  if (!existsSync(packagePath)) {
    violations.push(`${product}: missing UI package declaration at ${config.packageJson}`);
    continue;
  }

  const pkg = JSON.parse(readFileSync(packagePath, 'utf8'));
  for (const script of requiredScripts) {
    if (!pkg.scripts?.[script]) {
      violations.push(`${product}: missing required script '${script}' in ${config.packageJson}`);
    }
  }

  const dependencies = pkg.dependencies ?? {};
  const routerDependencies = ['react-router', 'react-router-dom'].filter((dependencyName) => dependencies[dependencyName]);
  if (routerDependencies.length !== 1) {
    violations.push(`${product}: expected exactly one approved router dependency in ${config.packageJson}`);
  } else {
    const routerName = routerDependencies[0];
    if (dependencies[routerName] !== approvedRouterVersion) {
      violations.push(
        `${product}: ${routerName} must use approved version ${approvedRouterVersion} in ${config.packageJson}`
      );
    }
  }

  if (!dependencies['@ghatana/design-system']) {
    violations.push(`${product}: missing @ghatana/design-system dependency in ${config.packageJson}`);
  }

  if (!dependencies['@ghatana/tokens']) {
    violations.push(`${product}: missing @ghatana/tokens dependency in ${config.packageJson}`);
  }

  for (const library of disallowedUiLibraries) {
    if (dependencies[library]) {
      violations.push(`${product}: disallowed design-system overlap dependency '${library}' in ${config.packageJson}`);
    }
  }
}

if (manifest.products.finance?.ui !== false) {
  violations.push('finance: expected explicit backend-only declaration');
}

if (violations.length > 0) {
  console.error('Product UI contract violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product UI contract validation passed.');
