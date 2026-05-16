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

export const FORBIDDEN_PRODUCT_PREFIXED_PATTERNS = [
  /^@ghatana\/yappc-/,
  /^@ghatana\/dcmaar-/,
  /^@ghatana\/data-cloud-/,
  /^@ghatana\/phr-/,
  /^@ghatana\/finance-/,
  /^@ghatana\/flashit-/,
  /^@ghatana\/tutorputor-/,
];

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

function packageNameFromSpecifier(specifier) {
  if (specifier.startsWith('.') || specifier.startsWith('/')) {
    return null;
  }
  if (!specifier.startsWith('@')) {
    return specifier.split('/')[0];
  }
  const parts = specifier.split('/');
  return parts.length >= 2 ? `${parts[0]}/${parts[1]}` : parts[0];
}

export function findDeprecatedPackageImports(files) {
  const violations = [];

  for (const file of files) {
    const filePath = normalize(file.path);
    const source = file.source;

    for (const specifier of extractImportSpecifiers(source)) {
      const packageName = packageNameFromSpecifier(specifier);
      if (!packageName) {
        continue;
      }

      // Check deprecated package imports
      const replacement = DEPRECATED_PACKAGES.get(packageName);
      if (replacement) {
        violations.push(
          `${filePath}: deprecated package import '${packageName}'. ` +
            `Use '${replacement}' instead.`,
        );
      }

      // Check forbidden product-prefixed imports in platform code
      if (filePath.startsWith('platform/')) {
        for (const pattern of FORBIDDEN_PRODUCT_PREFIXED_PATTERNS) {
          if (pattern.test(packageName)) {
            violations.push(
              `${filePath}: forbidden product-prefixed import '${packageName}' in platform code. ` +
                'Use a product-local package or canonical platform package name.',
            );
          }
        }
      }
    }

    // Check package.json dependencies for deprecated packages
    if (filePath.endsWith('/package.json')) {
      try {
        const packageJson = JSON.parse(source);
        const allDeps = {
          ...packageJson.dependencies,
          ...packageJson.devDependencies,
          ...packageJson.peerDependencies,
        };

        for (const depName of Object.keys(allDeps)) {
          const replacement = DEPRECATED_PACKAGES.get(depName);
          if (replacement) {
            violations.push(
              `${filePath}: deprecated package dependency '${depName}'. ` +
                `Replace with '${replacement}'.`,
            );
          }
        }
      } catch {
        // Skip non-JSON package files
      }
    }
  }

  return violations;
}

export function checkDeprecatedPackages(options = {}) {
  const files = options.files ?? [
    ...listFiles(['platform', 'products', 'shared-services'], SOURCE_GLOBS).map((file) => ({
      path: file,
      source: readFileSync(path.join(repoRoot, file), 'utf8'),
    })),
    ...listFiles(['platform', 'products', 'shared-services'], ['package.json']).map((file) => ({
      path: file,
      source: readFileSync(path.join(repoRoot, file), 'utf8'),
    })),
  ];
  return findDeprecatedPackageImports(files);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkDeprecatedPackages();

  if (violations.length === 0) {
    console.log('OK: deprecated package checks passed.');
    process.exit(0);
  }

  console.error('FAIL: deprecated package checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}
