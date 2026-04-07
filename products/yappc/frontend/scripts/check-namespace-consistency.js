#!/usr/bin/env node

/**
 * Enforces active-code namespace consistency for YAPPC frontend.
 *
 * Policy:
 * - Active code under apps/ and libs/ must not import deprecated @ghatana/yappc-* packages
 * - Active code under apps/ and libs/ must not import legacy reactflow package paths
 * - Compatibility shims, stories, tests, and generated/example files are excluded
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const TARGET_DIRS = ['apps', 'libs'];

const SOURCE_EXTENSIONS = new Set([
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
]);
const EXCLUDED_FILE_PATTERNS = [
  /\.stories\.[tj]sx?$/,
  /\.test\.[tj]sx?$/,
  /\.spec\.[tj]sx?$/,
  /(?:^|\/)examples?\.[tj]sx?$/,
  /__tests__\//,
  /\/examples?\//,
  /\/generated\//,
  /\/dist\//,
  /\/docs?\//,
];
const EXCLUDED_DIR_NAMES = new Set([
  'node_modules',
  'dist',
  'build',
  'coverage',
  'generated',
  'docs',
  'storybook-static',
  'test-results',
  'tests-output',
]);

const LEGACY_IMPORT_PATTERN =
  /^\s*(?![/*])(?:import|export)\s+[^'"\n]*['"](@ghatana\/yappc-[^'"\n]+)['"]/gm;
const LEGACY_REACTFLOW_PATTERN =
  /^\s*(?![/*])(?:import|export)\s+[^'"\n]*['"](reactflow(?:\/[^'"\n]+)?)['"]/gm;

function shouldScanFile(filePath) {
  const ext = path.extname(filePath);
  if (!SOURCE_EXTENSIONS.has(ext)) {
    return false;
  }

  const normalized = filePath.replace(/\\/g, '/');
  return !EXCLUDED_FILE_PATTERNS.some((pattern) => pattern.test(normalized));
}

function collectFiles(dir) {
  const files = [];
  if (!fs.existsSync(dir)) {
    return files;
  }

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isSymbolicLink()) {
      continue;
    }
    if (entry.isDirectory()) {
      if (EXCLUDED_DIR_NAMES.has(entry.name)) {
        continue;
      }
      files.push(...collectFiles(fullPath));
      continue;
    }
    if (shouldScanFile(fullPath)) {
      files.push(fullPath);
    }
  }

  return files;
}

function findViolations(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const violations = [];
  let match;

  while ((match = LEGACY_IMPORT_PATTERN.exec(content)) !== null) {
    const before = content.slice(0, match.index);
    const line = before.split('\n').length;
    violations.push({
      file: path.relative(ROOT, filePath),
      line,
      importPath: match[1],
    });
  }

  while ((match = LEGACY_REACTFLOW_PATTERN.exec(content)) !== null) {
    const before = content.slice(0, match.index);
    const line = before.split('\n').length;
    violations.push({
      file: path.relative(ROOT, filePath),
      line,
      importPath: match[1],
    });
  }

  return violations;
}

function main() {
  const files = TARGET_DIRS.flatMap((subdir) =>
    collectFiles(path.join(ROOT, subdir))
  );
  const violations = files.flatMap(findViolations);

  if (violations.length === 0) {
    console.log(
      'Namespace consistency check passed: no legacy @ghatana/yappc-* or reactflow imports in active code.'
    );
    process.exit(0);
  }

  console.error(
    'Namespace consistency check failed. Migrate legacy imports to @yappc/* or @xyflow/react:'
  );
  for (const violation of violations) {
    console.error(
      `- ${violation.file}:${violation.line} -> ${violation.importPath}`
    );
  }
  process.exit(1);
}

main();
