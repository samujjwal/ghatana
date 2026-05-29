#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const registryPath = path.join(repoRoot, 'config', 'documentation-surface-registry.json');

const ABSOLUTE_PATH_PATTERN = /([A-Za-z]:\\[^\s]+|\/Users\/[^\s]+|\/home\/[^\s]+)/;

function normalizePath(filePath) {
  return filePath.replace(/\\/g, '/');
}

function loadRegistry() {
  if (!existsSync(registryPath)) {
    throw new Error('Missing config/documentation-surface-registry.json');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function checkCanonicalEntries(registry) {
  const failures = [];
  for (const surface of registry.surfaces ?? []) {
    const targetPath = path.join(repoRoot, surface.path);
    if (surface.status === 'delete') {
      if (existsSync(targetPath)) {
        failures.push(`${surface.path}: marked delete but file still exists`);
      }
      continue;
    }

    if (!existsSync(targetPath)) {
      failures.push(`${surface.path}: missing registered documentation surface`);
      continue;
    }

    if (surface.status === 'canonical' || surface.status === 'runbook' || surface.status === 'adr') {
      const text = readFileSync(targetPath, 'utf8');
      const lines = text.split(/\r?\n/);
      lines.forEach((line, index) => {
        if (ABSOLUTE_PATH_PATTERN.test(line)) {
          failures.push(`${surface.path}:${index + 1}: contains absolute local path`);
        }
      });
    }
  }
  return failures;
}

function checkProductTaxonomy(registry) {
  const failures = [];
  const taxonomy = registry.taxonomy ?? {};
  const activeProducts = taxonomy.activeProducts ?? [];
  const requiredForActive = taxonomy.requiredForActive ?? [];
  const designAlternates = taxonomy.designAlternates ?? [];
  const legacyCompat = taxonomy.legacyCompat ?? [];

  for (const productId of activeProducts) {
    const docsDir = path.join(repoRoot, 'products', productId, 'docs');
    if (!existsSync(docsDir)) {
      failures.push(`products/${productId}/docs: missing docs directory for active product`);
      continue;
    }

    for (const required of requiredForActive) {
      const requiredPath = path.join(docsDir, required);
      if (!existsSync(requiredPath)) {
        const compatMissing = required === '03-API_CONTRACTS.md'
          ? !legacyCompat.some((legacyName) => existsSync(path.join(docsDir, legacyName)))
          : true;
        if (compatMissing) {
          failures.push(`products/${productId}/docs/${required}: missing required active-product document`);
        }
      }
    }

    const hasDesignDoc = designAlternates.some((name) => existsSync(path.join(docsDir, name)));
    const hasLegacyUxDoc = existsSync(path.join(docsDir, '03-UX_WORKFLOWS.md'));
    if (!hasDesignDoc && !hasLegacyUxDoc) {
      failures.push(`products/${productId}/docs: missing design/UX document (expected one of ${designAlternates.join(', ')})`);
    }
  }

  return failures;
}

function checkDeletedPathReferences(registry) {
  const failures = [];
  const targets = (registry.deletedPathTargets ?? []).map((target) => normalizePath(target));
  if (targets.length === 0) {
    return failures;
  }

  const scanFiles = [
    'README.md',
    'package.json',
    'pnpm-workspace.yaml',
    'build.gradle.kts',
    'settings.gradle.kts',
  ];

  for (const fixedFile of scanFiles) {
    const fullPath = path.join(repoRoot, fixedFile);
    if (!existsSync(fullPath)) {
      continue;
    }
    const text = readFileSync(fullPath, 'utf8');
    for (const target of targets) {
      if (text.includes(target)) {
        failures.push(`${fixedFile}: references deleted path ${target}`);
      }
    }
  }

  const scanRoots = [
    path.join(repoRoot, 'docs'),
    path.join(repoRoot, '.github', 'workflows'),
    path.join(repoRoot, 'scripts'),
  ];

  const walk = (rootDir, relPrefix = '') => {
    if (!existsSync(rootDir)) {
      return;
    }
    for (const entry of readdirSync(rootDir)) {
      const full = path.join(rootDir, entry);
      const rel = normalizePath(path.join(relPrefix, entry));
      const stats = statSync(full);
      if (stats.isDirectory()) {
        if (entry === 'node_modules' || entry === '.git' || entry === 'dist' || entry === 'build') {
          continue;
        }
        walk(full, rel);
        continue;
      }
      if (!/\.(md|mdx|mjs|js|json|ya?ml|kts|gradle)$/i.test(entry)) {
        continue;
      }
      const text = readFileSync(full, 'utf8');
      for (const target of targets) {
        if (text.includes(target)) {
          failures.push(`${rel}: references deleted path ${target}`);
        }
      }
    }
  };

  for (const root of scanRoots) {
    walk(root, normalizePath(path.relative(repoRoot, root)));
  }

  return failures;
}

export function runDocumentationSurfaceCheck() {
  const registry = loadRegistry();
  const failures = [
    ...checkCanonicalEntries(registry),
    ...checkProductTaxonomy(registry),
    ...checkDeletedPathReferences(registry),
  ];

  return {
    passed: failures.length === 0,
    failures,
    warnings: [],
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = runDocumentationSurfaceCheck();
  if ((result.warnings ?? []).length > 0) {
    console.warn('WARN: documentation surface checks found warnings:');
    for (const warning of result.warnings) {
      console.warn(` - ${normalizePath(warning)}`);
    }
  }
  if (result.passed) {
    console.log('OK: documentation surface checks passed.');
    process.exit(0);
  }

  console.error('FAIL: documentation surface checks found issues:');
  for (const failure of result.failures) {
    console.error(` - ${normalizePath(failure)}`);
  }
  process.exit(1);
}
