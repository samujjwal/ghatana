#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const SOURCE_GLOBS = ['*.ts', '*.tsx', '*.js', '*.jsx', '*.mjs', '*.cjs', '*.java', '*.kt', '*.kts'];
const IGNORED_SEGMENTS = ['/node_modules/', '/dist/', '/build/', '/coverage/', '/.turbo/', '/.ignored/'];

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
      .map(normalize)
      .filter((file) => !isIgnored(file));
  } catch {
    const extensions = new Set(
      globPatterns
        .map((pattern) => pattern.replace('*', ''))
        .filter((pattern) => pattern.startsWith('.')),
    );
    const files = [];
    for (const rootSegment of rootSegments) {
      const absoluteRoot = path.join(repoRoot, rootSegment);
      if (!existsSync(absoluteRoot)) {
        continue;
      }
      walkDirectory(absoluteRoot, extensions, files);
    }
    return files;
  }
}

function walkDirectory(directory, extensions, files) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const relativePath = normalize(path.relative(repoRoot, fullPath));
    if (isIgnored(relativePath)) {
      continue;
    }
    let stats;
    try {
      stats = statSync(fullPath);
    } catch {
      continue;
    }
    if (stats.isDirectory()) {
      walkDirectory(fullPath, extensions, files);
      continue;
    }
    if (extensions.has(path.extname(entry))) {
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

function extractJavaImports(source) {
  const specifiers = [];
  const javaImportPattern = /\bimport\s+(?:static\s+)?([a-zA-Z_][\w.]*(?:\.\*)?)\s*;/g;
  for (const match of source.matchAll(javaImportPattern)) {
    specifiers.push(match[1]);
  }
  return specifiers;
}

function relativeImportTarget(filePath, specifier) {
  if (!specifier.startsWith('.')) {
    return null;
  }
  return normalize(path.normalize(path.join(path.dirname(filePath), specifier)));
}

function loadBoundaryRules() {
  try {
    return readJson('scripts/boundary-rules.json');
  } catch (error) {
    console.warn('Warning: Failed to load boundary rules:', error.message);
    return { rules: [], productNames: [], productNeutralContractExceptions: [], legalBridgeLocations: [], allowlist: [] };
  }
}

function isAllowlisted(filePath, specifier, ruleId, allowlist) {
  return allowlist.some(
    (entry) =>
      entry.path === filePath &&
      entry.ruleId === ruleId &&
      typeof entry.reason === 'string' &&
      entry.reason.length > 0 &&
      typeof entry.owner === 'string' &&
      entry.owner.length > 0 &&
      typeof entry.reviewBy === 'string' &&
      entry.reviewBy.length > 0,
  );
}

function isProductNeutralException(filePath, exceptions) {
  return exceptions.some((exception) => normalize(filePath).startsWith(normalize(exception.path)));
}

function isLegalBridgeLocation(filePath, legalBridgeLocations) {
  return legalBridgeLocations.some((location) => normalize(filePath).startsWith(normalize(location)));
}

function matchesGlobPattern(filePath, pattern) {
  const regexStr = pattern
    .replace(/\*\*/g, '<<<DOUBLESTAR>>>')
    .replace(/\*/g, '[^/]*')
    .replace(/<<<DOUBLESTAR>>>/g, '.*');
  return new RegExp(`^${regexStr}`).test(filePath);
}

export function checkPlatformProductBoundaries(options = {}) {
  const rules = options.rules ?? loadBoundaryRules();
  const files = options.files ?? [
    ...listFiles(['platform'], SOURCE_GLOBS).map((file) => ({
      path: file,
      source: readFileSync(path.join(repoRoot, file), 'utf8'),
    })),
  ];

  return findBoundaryViolations(files, rules);
}

export function findBoundaryViolations(files, rules) {
  const violations = [];
  const productNames = rules.productNames ?? [];
  const allowlist = rules.allowlist ?? [];
  const exceptions = rules.productNeutralContractExceptions ?? [];
  const legalBridgeLocations = rules.legalBridgeLocations ?? [];

  for (const file of files) {
    const filePath = normalize(file.path);
    const source = file.source;
    const isPlatformFile = filePath.startsWith('platform/');
    const isKernelFile = filePath.startsWith('platform/typescript/kernel-');

    if (!isPlatformFile) {
      continue;
    }

    // Check TypeScript/JS imports
    const tsImports = extractImportSpecifiers(source);
    for (const specifier of tsImports) {
      const relTarget = relativeImportTarget(filePath, specifier);
      let handledBySpecificRule = false;

      // Rule: kernel must not import YAPPC/Data Cloud internals (more specific, checked first)
      if (isKernelFile && relTarget?.startsWith('products/')) {
        if (relTarget.startsWith('products/yappc/') && !isLegalBridgeLocation(relTarget, legalBridgeLocations)) {
          if (!isAllowlisted(filePath, specifier, 'kernel-no-yappc-internals', allowlist)) {
            violations.push(
              `${filePath}: kernel code imports YAPPC internals '${specifier}'. ` +
                'Remediation: Use bridge/provider contracts only.',
            );
          }
          handledBySpecificRule = true;
        }
        if (relTarget.startsWith('products/data-cloud/') && !isLegalBridgeLocation(relTarget, legalBridgeLocations)) {
          if (!isAllowlisted(filePath, specifier, 'kernel-no-data-cloud-internals', allowlist)) {
            violations.push(
              `${filePath}: kernel code imports Data Cloud internals '${specifier}'. ` +
                'Remediation: Use bridge/provider contracts only.',
            );
          }
          handledBySpecificRule = true;
        }
      }

      // Rule: platform must not import from products (generic fallback)
      if (!handledBySpecificRule && relTarget?.startsWith('products/')) {
        if (
          !isAllowlisted(filePath, specifier, 'platform-no-product-import', allowlist) &&
          !isProductNeutralException(filePath, exceptions)
        ) {
          violations.push(
            `${filePath}: platform code imports product path '${specifier}'. ` +
              'Remediation: Remove import or move logic to the product area.',
          );
        }
      }
    }

    // Check Java imports
    if (filePath.endsWith('.java') || filePath.endsWith('.kt') || filePath.endsWith('.kts')) {
      const javaImports = extractJavaImports(source);
      for (const javaImport of javaImports) {
        for (const productName of productNames) {
          const productJavaPackage = `com.ghatana.${productName.replace(/-/g, '')}`;
          if (
            filePath.startsWith('platform/java/') &&
            javaImport.startsWith(productJavaPackage) &&
            !isAllowlisted(filePath, javaImport, 'platform-no-product-import', allowlist)
          ) {
            violations.push(
              `${filePath}: platform Java code imports product package '${javaImport}'. ` +
                'Remediation: Remove import or document as product-neutral contract.',
            );
          }
        }
      }
    }

    // Rule: platform/typescript must not contain product-named implementation modules
    if (filePath.startsWith('platform/typescript/') && filePath.endsWith('/package.json')) {
      try {
        const packageJson = JSON.parse(source);
        const packageName = packageJson.name;
        if (typeof packageName === 'string') {
          for (const productName of productNames) {
            if (
              packageName.startsWith(`@ghatana/${productName}`) &&
              !isProductNeutralException(filePath, exceptions)
            ) {
              violations.push(
                `${filePath}: product-prefixed platform package name '${packageName}'. ` +
                  'Remediation: Use a product-local package or canonical platform package name.',
              );
            }
          }
        }
      } catch {
        // Skip non-JSON package files
      }
    }
  }

  return violations;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkPlatformProductBoundaries();

  if (violations.length === 0) {
    console.log('OK: platform/product boundary checks passed.');
    process.exit(0);
  }

  console.error('FAIL: platform/product boundary checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}
