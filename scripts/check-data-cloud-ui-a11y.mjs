#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const violations = [];

function readJson(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    violations.push(`Missing required file: ${relativePath}`);
    return null;
  }
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

const uiPackage = readJson('products/data-cloud/delivery/ui/package.json');
if (uiPackage) {
  const scripts = uiPackage.scripts ?? {};
  if (scripts['test:e2e:a11y'] !== 'playwright test --grep @a11y') {
    violations.push('Data Cloud UI package must expose test:e2e:a11y as "playwright test --grep @a11y"');
  }
  for (const dependencyName of ['@axe-core/react', 'eslint-plugin-jsx-a11y']) {
    const inDeps = uiPackage.dependencies?.[dependencyName] || uiPackage.devDependencies?.[dependencyName];
    if (!inDeps) {
      violations.push(`Data Cloud UI package is missing accessibility dependency: ${dependencyName}`);
    }
  }
}

const a11ySpecPath = path.join(repoRoot, 'products/data-cloud/delivery/ui/e2e/a11y.spec.ts');
if (!existsSync(a11ySpecPath)) {
  violations.push('Missing Data Cloud a11y E2E spec: products/data-cloud/delivery/ui/e2e/a11y.spec.ts');
} else {
  const a11ySpec = readFileSync(a11ySpecPath, 'utf8');
  if (!a11ySpec.includes('@a11y')) {
    violations.push('Data Cloud a11y E2E spec must include the @a11y tag.');
  }
}

const accessibilityWorkflowPath = path.join(repoRoot, '.github/workflows/accessibility.yml');
if (!existsSync(accessibilityWorkflowPath)) {
  violations.push('Missing accessibility workflow: .github/workflows/accessibility.yml');
} else {
  const workflow = readFileSync(accessibilityWorkflowPath, 'utf8');
  for (const token of ['products/data-cloud/delivery/ui/**', "package: '@data-cloud/ui'"]) {
    if (!workflow.includes(token)) {
      violations.push(`Accessibility workflow missing Data Cloud coverage token ${JSON.stringify(token)}`);
    }
  }
}

const releaseWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-release.yml');
if (existsSync(releaseWorkflowPath)) {
  const workflow = readFileSync(releaseWorkflowPath, 'utf8');
  if (!workflow.includes('pnpm check:data-cloud-ui-a11y')) {
    violations.push('Data Cloud release workflow must execute check:data-cloud-ui-a11y');
  }
}

if (violations.length > 0) {
  console.error('Data Cloud UI accessibility conformance failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Data Cloud UI accessibility conformance passed.');
