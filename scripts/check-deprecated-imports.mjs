#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const SOURCE_GLOBS = ['*.ts', '*.tsx', '*.js', '*.jsx', '*.mjs', '*.cjs'];
const IGNORED_SEGMENTS = ['/node_modules/', '/dist/', '/build/', '/coverage/', '/.turbo/', '/.ignored/'];

export const DEPRECATED_PACKAGES = new Map([
  ['@ghatana/ui', '@ghatana/design-system'],
  ['@ghatana/utils', '@ghatana/platform-utils'],
  ['@ghatana/accessibility-audit', '@ghatana/accessibility'],
  ['@ghatana/audit-components', '@ghatana/design-system'],
  ['@ghatana/canvas-core', '@ghatana/canvas'],
  ['@ghatana/canvas-react', '@ghatana/canvas'],
  ['@ghatana/canvas-plugins', '@ghatana/canvas'],
  ['@ghatana/canvas-tools', '@ghatana/canvas'],
  ['@ghatana/canvas-chrome', '@ghatana/canvas'],
]);

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function isIgnored(filePath) {
  const normalized = `/${normalize(filePath)}`;
  return IGNORED_SEGMENTS.some((segment) => normalized.includes(segment));
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
      .map(normalize);
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
    let stats;
    try {
      stats = statSync(fullPath);
    } catch {
      continue;
    }
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

export function findDeprecatedImports(files) {
  const violations = [];

  for (const file of files) {
    const filePath = normalize(file.path);
    const source = file.source;

    for (const specifier of extractImportSpecifiers(source)) {
      const replacement = DEPRECATED_PACKAGES.get(specifier);
      if (replacement) {
        violations.push(`${filePath}: deprecated import '${specifier}' detected. Use '${replacement}'.`);
      }
    }

    if (filePath.startsWith('platform/typescript/') && filePath.endsWith('/package.json')) {
      const packageJson = JSON.parse(source);
      if (typeof packageJson.name === 'string' && /^@ghatana\/(digital-marketing|phr|finance|flashit|data-cloud|yappc)/.test(packageJson.name)) {
        violations.push(`${filePath}: forbidden product-prefixed platform package name '${packageJson.name}'. Use a product-local package or a canonical platform package name.`);
      }
    }
  }

  return violations;
}

export function checkDeprecatedImports(options = {}) {
  const files = options.files ?? [
    ...listFiles(['platform', 'products', 'shared-services'], SOURCE_GLOBS).map((file) => ({
      path: file,
      source: readFileSync(path.join(repoRoot, file), 'utf8'),
    })),
    ...listFiles(['platform/typescript'], ['package.json']).map((file) => ({
      path: file,
      source: readFileSync(path.join(repoRoot, file), 'utf8'),
    })),
  ];
  return findDeprecatedImports(files);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkDeprecatedImports();

  if (violations.length === 0) {
    console.log('OK: deprecated import checks passed.');
    process.exit(0);
  }

  console.error('FAIL: deprecated import checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}