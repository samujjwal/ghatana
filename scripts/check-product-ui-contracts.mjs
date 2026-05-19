#!/usr/bin/env node

import { readFileSync, existsSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const manifest = JSON.parse(readFileSync(resolve(repoRoot, 'config/product-shape.json'), 'utf8'));
const workspaceConfig = readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8');
const requiredScripts = ['lint', 'type-check', 'test', 'test:coverage', 'test:e2e', 'test:e2e:a11y', 'build'];
const approvedRouterVersion = '^7.14.0';
const approvedPackageManager = 'pnpm@10.33.0';
const disallowedUiLibraries = [
  '@mui/material',
  '@mui/icons-material',
  '@chakra-ui/react',
  '@mantine/core',
  'antd',
  'semantic-ui-react',
];
const violations = [];

function getWebClientPackage(config) {
  const clientPackages = Array.isArray(config.clientPackages) ? config.clientPackages : [];
  return clientPackages.find((packagePath) =>
    /\/(apps\/web|client\/web|ui)\/package\.json$/.test(packagePath),
  );
}

function getCatalogVersion(dependencyName) {
  const escapedName = dependencyName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const catalogEntry = workspaceConfig.match(
    new RegExp(`^\\s*["']?${escapedName}["']?\\s*:\\s*["']([^"']+)["']\\s*$`, 'm'),
  );
  return catalogEntry?.[1];
}

function resolveDependencyVersion(dependencyName, declaredVersion) {
  if (declaredVersion === 'catalog:') {
    return getCatalogVersion(dependencyName) ?? declaredVersion;
  }

  return declaredVersion;
}

function requireFile(relativePath, message) {
  if (!existsSync(resolve(repoRoot, relativePath))) {
    violations.push(message);
  }
}

function listProductSourceFiles(clientDir) {
  const output = execSync(
    `rg --files ${clientDir}/src --glob '*.{ts,tsx,js,jsx}' --glob '!**/__tests__/**'`,
    { cwd: repoRoot, encoding: 'utf8' },
  ).trim();

  return output ? output.split('\n').filter(Boolean) : [];
}

for (const [product, config] of Object.entries(manifest.products)) {
  if (!config.ui) {
    continue;
  }

  const webClientPackage = getWebClientPackage(config);
  if (!webClientPackage) {
    if (config.lifecycle?.enabled) {
      violations.push(`${product}: missing canonical web UI package declaration in config/product-shape.json`);
    }
    continue;
  }

  const packagePath = resolve(repoRoot, webClientPackage);
  if (!existsSync(packagePath)) {
    violations.push(`${product}: missing UI package declaration at ${webClientPackage}`);
    continue;
  }

  const pkg = JSON.parse(readFileSync(packagePath, 'utf8'));
  if (pkg.packageManager !== approvedPackageManager) {
    violations.push(`${product}: packageManager must be ${approvedPackageManager} in ${webClientPackage}`);
  }

  for (const script of requiredScripts) {
    if (!pkg.scripts?.[script]) {
      violations.push(`${product}: missing required script '${script}' in ${webClientPackage}`);
    }
  }

  const dependencies = pkg.dependencies ?? {};
  const routerDependencies = ['react-router', 'react-router-dom'].filter((dependencyName) => dependencies[dependencyName]);
  if (routerDependencies.length !== 1) {
    violations.push(`${product}: expected exactly one approved router dependency in ${webClientPackage}`);
  } else {
    const routerName = routerDependencies[0];
    const resolvedRouterVersion = resolveDependencyVersion(routerName, dependencies[routerName]);
    if (resolvedRouterVersion !== approvedRouterVersion) {
      violations.push(
        `${product}: ${routerName} must use approved version ${approvedRouterVersion} in ${webClientPackage}`
      );
    }
  }

  if (!dependencies['@ghatana/design-system']) {
    violations.push(`${product}: missing @ghatana/design-system dependency in ${webClientPackage}`);
  }

  if (!dependencies['@ghatana/tokens']) {
    violations.push(`${product}: missing @ghatana/tokens dependency in ${webClientPackage}`);
  }

  for (const library of disallowedUiLibraries) {
    if (dependencies[library]) {
      violations.push(`${product}: disallowed design-system overlap dependency '${library}' in ${webClientPackage}`);
    }
  }

  const clientDir = webClientPackage.replace(/\/package\.json$/, '');
  const sourceFiles = listProductSourceFiles(clientDir);
  for (const file of sourceFiles) {
    const source = readFileSync(resolve(repoRoot, file), 'utf8');
    for (const library of disallowedUiLibraries) {
      const importPattern = new RegExp(`from ['"]${library.replace('/', '\\/')}['"]`);
      if (importPattern.test(source)) {
        violations.push(`${product}: disallowed UI library import '${library}' in ${file}`);
      }
    }
  }

  requireFile(
    `${clientDir}/playwright.config.ts`,
    `${product}: missing Playwright config at ${clientDir}/playwright.config.ts`,
  );

  const visualCandidates = [
    `${clientDir}/e2e/visual-regression.spec.ts`,
    `${clientDir}/e2e/11-visual-regression.spec.ts`,
    `${clientDir}/tests/e2e/phr-visual-regression.spec.ts`,
  ];
  if (!visualCandidates.some((candidate) => existsSync(resolve(repoRoot, candidate)))) {
    violations.push(`${product}: missing canonical visual regression spec under ${clientDir}`);
  }

  const a11yCandidates = [
    `${clientDir}/e2e/a11y.spec.ts`,
    `${clientDir}/e2e/09-accessibility.spec.ts`,
    `${clientDir}/tests/e2e/phr-a11y.spec.ts`,
  ];
  if (!a11yCandidates.some((candidate) => existsSync(resolve(repoRoot, candidate)))) {
    violations.push(`${product}: missing canonical accessibility spec under ${clientDir}`);
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
