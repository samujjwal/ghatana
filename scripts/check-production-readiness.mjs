#!/usr/bin/env node

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();

const DEFAULT_SCAN_ROOTS = [
  'platform',
  'platform-kernel',
  'platform-plugins',
  'shared-services',
  'services',
  'products/data-cloud',
  'products/data-cloud/planes/action',
  'products/yappc',
  'products/virtual-org',
];

const argv = process.argv.slice(2);

function argValue(name) {
  const index = argv.indexOf(name);
  return index >= 0 ? argv[index + 1] : undefined;
}

function splitCsv(value) {
  return String(value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map(toPosix);
}

function normalizeRoot(root) {
  return toPosix(root).replace(/^\.?\//, '').replace(/\/$/, '');
}

function readChangedFilesFromGit() {
  const base = argValue('--base') || process.env.GITHUB_BASE_SHA || 'origin/main';
  const head = argValue('--head') || process.env.GITHUB_SHA || 'HEAD';
  const result = spawnSync('git', ['diff', '--name-only', `${base}...${head}`], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  if (result.status !== 0) {
    console.error(`Could not resolve changed-only production readiness diff ${base}...${head}.`);
    if (result.stderr) {
      console.error(result.stderr.trim());
    }
    process.exit(result.status ?? 1);
  }
  return result.stdout.split(/\r?\n/).filter(Boolean).map(toPosix);
}

function productRoots(productIds) {
  if (productIds.length === 0) {
    return [];
  }

  const registry = loadCanonicalRegistry(repoRoot);
  const roots = [];
  for (const productId of productIds) {
    const product = registry[productId];
    if (!product) {
      console.error(`Unknown product id for production readiness scan: ${productId}`);
      process.exit(1);
    }
    roots.push(`products/${productId}`);
    for (const candidate of [
      product.manifestPath,
      product.buildFile,
      ...(product.pnpmPackages ?? []),
      ...(product.surfaces ?? []).flatMap((surface) => [surface.path, surface.packagePath].filter(Boolean)),
    ]) {
      if (typeof candidate === 'string') {
        roots.push(candidate.replace(/\/package\.json$/, ''));
      }
    }
  }
  return roots.map(normalizeRoot);
}

function selectedRoots() {
  const explicitRoots = splitCsv(argValue('--roots'));
  const products = splitCsv(argValue('--products') || process.env.AFFECTED_PRODUCTS);
  const roots = [...explicitRoots, ...productRoots(products)];
  return roots.length > 0 ? [...new Set(roots.map(normalizeRoot))] : DEFAULT_SCAN_ROOTS;
}

function selectedChangedFiles() {
  const paths = splitCsv(argValue('--paths'));
  return paths.length > 0 ? paths : readChangedFilesFromGit();
}

const INCLUDE_EXTENSIONS = new Set([
  '.java',
  '.kt',
  '.kts',
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.py',
  '.rs',
  '.go',
  '.sh',
  '.bash',
]);

const EXCLUDED_DIRS = new Set([
  '.git',
  '.gradle',
  '.idea',
  '.next',
  '.turbo',
  '.vscode',
  'build',
  'dist',
  'out',
  'target',
  'coverage',
  'node_modules',
  '.venv',
  'venv',
  'site-packages',
  '.tools',
]);

const MARKER_PATTERN = /(?:\/\/|#|\/\*|\*)\s*(TODO|FIXME|HACK|XXX)\b/;
const FORBIDDEN_NAME_PATTERN = /^\s*(?:export\s+)?(?:public|private|protected|static|final|abstract|async\s+)*(?:class|interface|record|enum|function|const|let|var|type)\s+([A-Za-z0-9_]*(Unsafe|Hack|Temp|Demo))\b/;
const SUBSTRING_DECISION_PATTERN = /contains\(\s*"\\\"allow\\\":true"\s*\)/;

function toPosix(p) {
  return p.split(path.sep).join('/');
}

function isTestFile(relativePath) {
  const p = toPosix(relativePath);
  return (
    p.includes('/src/test/') ||
    p.includes('/test/') ||
    p.includes('/__tests__/') ||
    p.endsWith('.test.ts') ||
    p.endsWith('.test.tsx') ||
    p.endsWith('.spec.ts') ||
    p.endsWith('.spec.tsx') ||
    p.endsWith('.test.js') ||
    p.endsWith('.spec.js') ||
    p.endsWith('Test.java') ||
    p.endsWith('IT.java')
  );
}

function shouldExcludeDir(dirName) {
  if (EXCLUDED_DIRS.has(dirName)) {
    return true;
  }
  return dirName.startsWith('.pnpm');
}

function shouldScanFile(filePath) {
  return INCLUDE_EXTENSIONS.has(path.extname(filePath));
}

function isNonProductionPath(relativePath) {
  const p = toPosix(relativePath).toLowerCase();
  return (
    p.includes('/src/test/') ||
    p.includes('/test/') ||
    p.includes('/__tests__/') ||
    p.includes('/stories/') ||
    p.includes('.stories.') ||
    p.includes('/storybook/') ||
    p.includes('/examples/') ||
    p.includes('/example/') ||
    p.includes('.examples.') ||
    p.includes('/demo/') ||
    p.includes('/mocks/') ||
    p.includes('/mock-api') ||
    p.includes('/scripts/') ||
    p.includes('/docs/') ||
    p.includes('/fixtures/') ||
    p.includes('/samples/') ||
    p.includes('/benchmarks/') ||
    p.includes('/performance/') ||
    p.includes('/e2e-tests/') ||
    p.includes('/integration-tests/') ||
    p.includes('/dist/') ||
    p.includes('/build/') ||
    p.includes('/generated/') ||
    p.includes('.generated.')
  );
}

function walk(dirPath, out) {
  if (!fs.existsSync(dirPath)) {
    return;
  }

  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      if (shouldExcludeDir(entry.name)) {
        continue;
      }
      walk(full, out);
      continue;
    }

    if (entry.isFile() && shouldScanFile(full)) {
      out.push(full);
    }
  }
}

function isUnderRoots(relativePath, roots) {
  return roots.some((root) => relativePath === root || relativePath.startsWith(`${root}/`));
}

function filesForScan(roots) {
  if (argv.includes('--changed-only')) {
    return selectedChangedFiles()
      .filter((relativePath) => isUnderRoots(relativePath, roots))
      .filter((relativePath) => fs.existsSync(path.join(repoRoot, relativePath)))
      .filter((relativePath) => shouldScanFile(relativePath))
      .map((relativePath) => path.join(repoRoot, relativePath));
  }

  const files = [];
  for (const root of roots) {
    walk(path.join(repoRoot, root), files);
  }
  return files;
}

function getLineViolations(content, regex) {
  const lines = content.split(/\r?\n/);
  const matches = [];
  for (let i = 0; i < lines.length; i += 1) {
    if (regex.test(lines[i])) {
      matches.push({ line: i + 1, text: lines[i] });
    }
  }
  return matches;
}

const violations = [];

function requireIncludes(relativePath, needle, message) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!fs.existsSync(absolutePath)) {
    violations.push({
      type: 'MISSING_REQUIRED_FILE',
      file: relativePath,
      line: 1,
      snippet: `Missing required file: ${relativePath}`,
    });
    return;
  }
  const content = fs.readFileSync(absolutePath, 'utf8');
  if (!content.includes(needle)) {
    violations.push({
      type: 'MISSING_REQUIRED_GUARD',
      file: relativePath,
      line: 1,
      snippet: message,
    });
  }
}

function requireNotIncludes(relativePath, needle, message) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!fs.existsSync(absolutePath)) {
    return;
  }
  const content = fs.readFileSync(absolutePath, 'utf8');
  if (content.includes(needle)) {
    violations.push({
      type: 'FORBIDDEN_LITERAL_IN_PRODUCTION',
      file: relativePath,
      line: 1,
      snippet: message,
    });
  }
}

// Provider schema drift and in-memory fallback hardening checks
requireIncludes(
  'products/data-cloud/planes/action/gateway/src/app.ts',
  'LifecycleRuntimeTruthSnapshotSchema',
  'Gateway must validate runtime-truth payloads with LifecycleRuntimeTruthSnapshotSchema',
);
requireIncludes(
  'products/data-cloud/planes/action/gateway/src/app.ts',
  'LifecycleMemoryRecordSchema',
  'Gateway must validate memory payloads with LifecycleMemoryRecordSchema',
);
requireIncludes(
  'products/data-cloud/planes/action/gateway/src/app.ts',
  'sendProviderStoreUnavailable',
  'Gateway must fail closed when provider store is unavailable',
);
requireNotIncludes(
  'products/data-cloud/planes/action/gateway/src/app.ts',
  'new InMemoryProviderStore(',
  'Gateway production path must not instantiate InMemoryProviderStore',
);

// Studio lifecycle hardcoded string drift checks (must use translation keys)
requireNotIncludes(
  'platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx',
  'Blocked reason codes',
  'LifecyclePage must not hardcode user-facing "Blocked reason codes" text',
);
requireNotIncludes(
  'platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx',
  'No gate evidence available.',
  'LifecyclePage must not hardcode gate evidence empty-state text',
);

// Disabled-product execution safeguards
requireIncludes(
  'platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts',
  'lifecycleExecutionAllowed === false',
  'Planner must enforce lifecycleExecutionAllowed guard',
);
requireIncludes(
  'platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts',
  'ProductLifecycleNotReadyError',
  'KernelLifecycleService must return NOT_READY behavior for blocked products',
);

const rootsToScan = selectedRoots();
const productionFiles = filesForScan(rootsToScan);

for (const filePath of productionFiles) {
    const rel = path.relative(repoRoot, filePath);
    const relPosix = toPosix(rel);

    const content = fs.readFileSync(filePath, 'utf8');
    const isTest = isTestFile(relPosix);
    const isNonProduction = isNonProductionPath(relPosix);

    if (!isTest && !isNonProduction) {
      const markerViolations = getLineViolations(content, MARKER_PATTERN);
      for (const hit of markerViolations) {
        violations.push({
          type: 'FORBIDDEN_MARKER_IN_PRODUCTION',
          file: relPosix,
          line: hit.line,
          snippet: hit.text.trim(),
        });
      }

      const mockImportViolations = getLineViolations(content, /from\s+['"][^'"]*__mocks__[^'"]*['"]|require\(['"][^'"]*__mocks__[^'"]*['"]\)/);
      for (const hit of mockImportViolations) {
        violations.push({
          type: 'TEST_MOCK_IMPORTED_IN_PRODUCTION',
          file: relPosix,
          line: hit.line,
          snippet: hit.text.trim(),
        });
      }

      const forbiddenNameViolations = getLineViolations(content, FORBIDDEN_NAME_PATTERN);
      for (const hit of forbiddenNameViolations) {
        violations.push({
          type: 'FORBIDDEN_PRODUCTION_SYMBOL_NAME',
          file: relPosix,
          line: hit.line,
          snippet: hit.text.trim(),
        });
      }

      const substringDecisionViolations = getLineViolations(content, SUBSTRING_DECISION_PATTERN);
      for (const hit of substringDecisionViolations) {
        violations.push({
          type: 'FORBIDDEN_SUBSTRING_DECISION_LOGIC',
          file: relPosix,
          line: hit.line,
          snippet: hit.text.trim(),
        });
      }
    }
}

if (violations.length > 0) {
  console.error('Production readiness policy violations detected:\n');
  for (const violation of violations) {
    console.error(
      `[${violation.type}] ${violation.file}:${violation.line} -> ${violation.snippet}`,
    );
  }
  console.error(`\nTotal violations: ${violations.length}`);
  process.exit(1);
}

console.log('Production readiness checks passed.');
