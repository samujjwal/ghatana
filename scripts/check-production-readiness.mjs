#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const SCAN_ROOTS = [
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

for (const root of SCAN_ROOTS) {
  const absoluteRoot = path.join(repoRoot, root);
  const files = [];
  walk(absoluteRoot, files);

  for (const filePath of files) {
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
