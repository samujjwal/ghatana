#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const SOURCE_GLOBS = ['*.ts', '*.tsx', '*.js', '*.jsx', '*.mjs', '*.cjs'];
const IGNORED_SEGMENTS = ['/node_modules/', '/dist/', '/build/', '/coverage/', '/__tests__/', '.test.', '.spec.'];

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function isIgnored(filePath) {
  const normalized = `/${normalize(filePath)}`;
  return IGNORED_SEGMENTS.some((segment) => normalized.includes(segment));
}

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function listFiles(rootSegments, globPatterns) {
  try {
    const args = ['--files', ...rootSegments];
    for (const glob of globPatterns) {
      args.push('-g', glob);
    }
    return execFileSync('rg', args, { cwd: repoRoot, encoding: 'utf8' })
      .split(/\r?\n/)
      .filter(Boolean)
      .filter((file) => !isIgnored(file));
  } catch {
    const extensions = new Set(
      globPatterns
        .map((pattern) => pattern.replace('*', ''))
        .filter((pattern) => pattern.startsWith('.')),
    );
    const exactFileNames = new Set(
      globPatterns.filter((pattern) => !pattern.includes('*') && !pattern.startsWith('.')),
    );
    const files = [];
    for (const rootSegment of rootSegments) {
      const absoluteRoot = path.join(repoRoot, rootSegment);
      if (!existsSync(absoluteRoot)) {
        continue;
      }
      walkDirectory(absoluteRoot, extensions, exactFileNames, files);
    }
    return files;
  }
}

function walkDirectory(directory, extensions, exactFileNames, files) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const relativePath = normalize(path.relative(repoRoot, fullPath));
    if (isIgnored(relativePath)) {
      continue;
    }
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      walkDirectory(fullPath, extensions, exactFileNames, files);
      continue;
    }
    if (exactFileNames.has(entry) || extensions.has(path.extname(entry)) || (exactFileNames.size === 0 && extensions.size === 0)) {
      files.push(relativePath);
    }
  }
}

function extractImportSpecifiers(source) {
  const specifiers = [];
  const patterns = [
    /\bimport\s+(?:type\s+)?(?:[^'"()]+?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bexport\s+(?:type\s+)?(?:[^'"]+?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ];

  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      specifiers.push(match[1]);
    }
  }

  return specifiers;
}

function relativeImportTarget(filePath, specifier) {
  if (!specifier.startsWith('.')) {
    return null;
  }
  return normalize(path.normalize(path.join(path.dirname(filePath), specifier)));
}

function packageNameFromSpecifier(specifier) {
  if (!specifier.startsWith('@')) {
    return specifier.split('/')[0];
  }
  const [scope, name] = specifier.split('/');
  return name ? `${scope}/${name}` : scope;
}

function productFromPath(filePath, productIds) {
  const normalized = normalize(filePath);
  for (const productId of productIds) {
    if (normalized.startsWith(`products/${productId}/`)) {
      return productId;
    }
  }
  return null;
}

export function analyzeBoundaryViolations(files, options) {
  const productIds = options.productIds;
  const productPackageOwners = options.productPackageOwners;
  const violations = [];

  for (const file of files) {
    const filePath = normalize(file.path);
    const source = file.source;
    const imports = extractImportSpecifiers(source);
    const fileProduct = productFromPath(filePath, productIds);
    const isPlatformFile = filePath.startsWith('platform/');
    const isSharedTsPackage = filePath.startsWith('platform/typescript/');
    const isKernelFile = filePath.startsWith('platform/typescript/kernel-') || filePath.startsWith('platform-kernel/');

    for (const specifier of imports) {
      const relativeTarget = relativeImportTarget(filePath, specifier);
      const packageName = packageNameFromSpecifier(specifier);
      const ownedProduct = productPackageOwners.get(packageName);

      if (isPlatformFile) {
        if (ownedProduct) {
          violations.push(`${filePath}: platform code imports product package '${packageName}' owned by ${ownedProduct}`);
        }
        if (relativeTarget?.startsWith('products/')) {
          violations.push(`${filePath}: platform code imports product implementation path '${specifier}'`);
        }
      }

      if (isKernelFile) {
        if (relativeTarget?.startsWith('products/yappc/')) {
          violations.push(`${filePath}: kernel code imports YAPPC implementation internals via '${specifier}'`);
        }
        if (relativeTarget?.startsWith('products/data-cloud/planes/')) {
          violations.push(`${filePath}: kernel code imports Data Cloud plane internals via '${specifier}'`);
        }
        if (/^products\/yappc\//.test(specifier)) {
          violations.push(`${filePath}: kernel code imports YAPPC implementation internals via '${specifier}'`);
        }
        if (/^products\/data-cloud\/planes\//.test(specifier)) {
          violations.push(`${filePath}: kernel code imports Data Cloud plane internals via '${specifier}'`);
        }
      }

      if (isSharedTsPackage) {
        if (ownedProduct) {
          violations.push(`${filePath}: shared TypeScript package imports product package '${packageName}' owned by ${ownedProduct}`);
        }
        if (relativeTarget?.startsWith('products/')) {
          violations.push(`${filePath}: shared TypeScript package imports product implementation path '${specifier}'`);
        }
      }

      if (fileProduct) {
        if (relativeTarget?.startsWith('platform/') || /^@ghatana\/[^/]+\/src(?:\/|$)/.test(specifier)) {
          violations.push(`${filePath}: product code bypasses platform public exports via '${specifier}'`);
        }
      }
    }

    if (/^products\/[^/]+\/.*(?:kernel-product|kernel-lifecycle|run-product-task|lifecycle-runner)\.(?:[cm]?[jt]s|tsx?)$/.test(filePath)) {
      violations.push(`${filePath}: product-local lifecycle runner detected; lifecycle execution must remain centralized in kernel tooling`);
    }

    if (/^platform\/typescript\/[^/]*(digital-marketing|phr|finance|flashit|data-cloud|yappc)[^/]*\//.test(filePath)) {
      violations.push(`${filePath}: product-named code must not live under generic platform TypeScript modules`);
    }
  }

  return violations;
}

function loadRepoFiles() {
  const files = listFiles(['platform', 'products', 'platform-kernel'], SOURCE_GLOBS);
  return files.map((file) => ({
    path: file,
    source: readFileSync(path.join(repoRoot, file), 'utf8'),
  }));
}

function loadProductIds() {
  return Object.keys(readJson('config/canonical-product-registry.json').registry ?? {});
}

function loadProductPackageOwners(productIds) {
  const packageOwners = new Map();
  const packageFiles = listFiles(['products'], ['package.json']);

  for (const packageFile of packageFiles) {
    const productId = productFromPath(packageFile, productIds);
    if (!productId) {
      continue;
    }
    const packageJson = JSON.parse(readFileSync(path.join(repoRoot, packageFile), 'utf8'));
    if (typeof packageJson.name === 'string' && packageJson.name.length > 0) {
      packageOwners.set(packageJson.name, productId);
    }
  }

  return packageOwners;
}

export function checkDomainBoundaries(options = {}) {
  const productIds = options.productIds ?? loadProductIds();
  const files = options.files ?? loadRepoFiles();
  const productPackageOwners = options.productPackageOwners ?? loadProductPackageOwners(productIds);
  return analyzeBoundaryViolations(files, { productIds, productPackageOwners });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkDomainBoundaries();

  if (violations.length === 0) {
    console.log('OK: domain boundary checks passed.');
    process.exit(0);
  }

  console.error('FAIL: domain boundary checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}