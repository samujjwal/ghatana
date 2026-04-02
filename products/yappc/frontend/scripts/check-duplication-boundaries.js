#!/usr/bin/env node

/**
 * Enforces YAPPC frontend duplication boundaries for active code.
 *
 * Policy:
 * - Active code under apps/, libs/, and web/ must not import deprecated compat packages.
 * - Active code must not define local clsx/tailwind-merge based cn() helpers.
 *   Use @ghatana/platform-utils instead.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const TARGET_DIRS = ['apps', 'libs', 'web'];

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
  /__tests__\//,
  /\/examples?\//,
  /\/generated\//,
  /\/dist\//,
  /\/build\//,
  /\/docs?\//,
  /\/node_modules\//,
  /\/compat\//,
];

const DEPRECATED_IMPORT_PATTERN =
  /^\s*(?![/*])(?:import|export)\s+[^'"\n]*['"](@yappc\/(?:base-ui|config-hooks|development-ui|initialization-ui|messaging|navigation-ui|utils)(?:\/[^'"\n]*)?)['"]/gm;

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

  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectFiles(fullPath));
      continue;
    }
    if (shouldScanFile(fullPath)) {
      files.push(fullPath);
    }
  }

  return files;
}

function getLineNumber(content, index) {
  return content.slice(0, index).split('\n').length;
}

function findDeprecatedImportViolations(relativePath, content) {
  const violations = [];
  let match;

  while ((match = DEPRECATED_IMPORT_PATTERN.exec(content)) !== null) {
    violations.push({
      file: relativePath,
      line: getLineNumber(content, match.index),
      kind: 'deprecated-import',
      detail: match[1],
    });
  }

  return violations;
}

function hasLocalCnImplementation(content) {
  return (
    /from\s+['"]clsx['"]/.test(content) &&
    /from\s+['"]tailwind-merge['"]/.test(content) &&
    /export\s+function\s+cn\s*\(/.test(content) &&
    /twMerge\s*\(\s*clsx\s*\(\s*inputs\s*\)\s*\)/.test(content)
  );
}

function findCnImplementationViolations(relativePath, content) {
  if (!relativePath.endsWith('/cn.ts') && !relativePath.endsWith('/cn.tsx')) {
    return [];
  }

  if (!hasLocalCnImplementation(content)) {
    return [];
  }

  const marker = content.indexOf('export function cn');
  return [
    {
      file: relativePath,
      line: marker >= 0 ? getLineNumber(content, marker) : 1,
      kind: 'duplicate-cn',
      detail: 'Local cn implementation duplicates @ghatana/platform-utils',
    },
  ];
}

function findViolationsInContent(relativePath, content) {
  return [
    ...findDeprecatedImportViolations(relativePath, content),
    ...findCnImplementationViolations(relativePath, content),
  ];
}

function findViolations(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const relativePath = path.relative(ROOT, filePath).replace(/\\/g, '/');
  return findViolationsInContent(relativePath, content);
}

function main() {
  const files = TARGET_DIRS.flatMap((subdir) =>
    collectFiles(path.join(ROOT, subdir))
  );
  const violations = files.flatMap(findViolations);

  if (violations.length === 0) {
    console.log(
      'Duplication boundary check passed: no deprecated compat imports or duplicate cn helpers in active YAPPC frontend code.'
    );
    process.exit(0);
  }

  console.error('Duplication boundary check failed:');
  for (const violation of violations) {
    console.error(
      `- ${violation.file}:${violation.line} [${violation.kind}] ${violation.detail}`
    );
  }
  process.exit(1);
}

module.exports = {
  shouldScanFile,
  findViolationsInContent,
  hasLocalCnImplementation,
};

if (require.main === module) {
  main();
}