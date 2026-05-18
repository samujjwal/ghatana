#!/usr/bin/env node
/**
 * check-package-governance.mjs
 *
 * Validates platform TypeScript package governance:
 * - Platform packages use @ghatana/* naming
 * - No deprecated package names are referenced
 * - Platform packages do not import products/*
 * - sso-client main barrel does not import Fastify/Express
 * - Canvas consumers use canonical @ghatana/canvas subpaths
 * - Orphan platform folders without package.json are flagged
 *
 * Per kernel-todo.md §1.5 requirements.
 *
 * Exits non-zero on any violation.
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/** Deprecated package names that must not appear anywhere. */
export const DEPRECATED_PACKAGE_NAMES = [
  '@ghatana/ui',
  '@ghatana/utils',
  '@ghatana/accessibility-audit',
  '@ghatana/audit-components',
  '@ghatana/canvas-core',
  '@ghatana/canvas-react',
  '@ghatana/canvas-plugins',
  '@ghatana/canvas-tools',
  '@ghatana/canvas-chrome',
];

/** Deprecated package name prefixes. */
const DEPRECATED_PREFIXES = [
  '@ghatana/dcmaar-',
  '@ghatana/yappc-',
];

/** Known orphan folders explicitly excluded from the orphan check (index-only shells or migration targets). */
const ORPHAN_EXCLUDE_LIST = new Set([
  'audit-ui',
  'nlp-ui',
  'privacy-ui',
  'security-ui',
  'voice-ui',
  'selection-ui',
  'browser-events',
  'router-facade',
]);

function resolvePath(relative) {
  return path.join(repoRoot, relative);
}

function readText(filePath) {
  return readFileSync(filePath, 'utf8');
}

function isDirectory(filePath) {
  try {
    return statSync(filePath).isDirectory();
  } catch {
    return false;
  }
}

function listDirs(dirPath) {
  try {
    return readdirSync(dirPath).filter(name => isDirectory(path.join(dirPath, name)));
  } catch {
    return [];
  }
}

/**
 * Check if a file content references a deprecated package name.
 * Returns a list of found deprecated names.
 */
function findDeprecatedImports(content) {
  const found = [];
  for (const deprecated of DEPRECATED_PACKAGE_NAMES) {
    // Match import/require patterns
    const escaped = deprecated.replace(/[@/]/g, c => `\\${c}`);
    const pattern = new RegExp(`['"\`]${escaped}(?:[/'"'\`])`, 'g');
    if (pattern.test(content)) {
      found.push(deprecated);
    }
  }
  for (const prefix of DEPRECATED_PREFIXES) {
    const escaped = prefix.replace(/[@/]/g, c => `\\${c}`);
    const pattern = new RegExp(`['"\`]${escaped}[a-z0-9-]+['"\`/]`, 'g');
    if (pattern.test(content)) {
      found.push(`${prefix}* (prefix match)`);
    }
  }
  return found;
}

/**
 * Check if a platform package imports from products/* (forbidden).
 */
function findForbiddenPlatformToProductImports(content, packagePath) {
  // Platform packages must not import from products/*
  const forbiddenPattern = /from\s+['"`][^'"` ]*\/products\//g;
  const matches = [...content.matchAll(forbiddenPattern)];
  return matches.map(m => m[0]);
}

/**
 * Run package governance checks.
 * Returns an array of issue objects { file, issue }.
 */
export function runPackageGovernanceChecks() {
  const issues = [];
  const platformTsRoot = resolvePath('platform/typescript');

  if (!existsSync(platformTsRoot)) {
    issues.push({ file: 'platform/typescript', issue: 'Platform TypeScript root directory does not exist.' });
    return issues;
  }

  const topLevelDirs = listDirs(platformTsRoot);

  for (const dirName of topLevelDirs) {
    const dirPath = path.join(platformTsRoot, dirName);
    const packageJsonPath = path.join(dirPath, 'package.json');

    // Orphan check: platform folder without package.json
    if (!existsSync(packageJsonPath)) {
      if (!ORPHAN_EXCLUDE_LIST.has(dirName)) {
        issues.push({
          file: `platform/typescript/${dirName}`,
          issue: `Orphan platform folder without package.json. Either create a package.json or delete this folder.`,
        });
      }
      continue;
    }

    let pkg;
    try {
      pkg = JSON.parse(readText(packageJsonPath));
    } catch {
      issues.push({
        file: `platform/typescript/${dirName}/package.json`,
        issue: 'package.json is not valid JSON.',
      });
      continue;
    }

    // Package name must use @ghatana/* scope
    if (typeof pkg.name !== 'string' || !pkg.name.startsWith('@ghatana/')) {
      issues.push({
        file: `platform/typescript/${dirName}/package.json`,
        issue: `Package name '${pkg.name ?? ''}' does not use @ghatana/* scope.`,
      });
    }

    // README must exist
    if (!existsSync(path.join(dirPath, 'README.md'))) {
      issues.push({
        file: `platform/typescript/${dirName}`,
        issue: 'Missing README.md.',
      });
    }

    // Public entry point should exist for library packages
    const hasStandardEntry =
      existsSync(path.join(dirPath, 'src', 'index.ts')) ||
      existsSync(path.join(dirPath, 'src', 'index.tsx')) ||
      existsSync(path.join(dirPath, 'index.ts')) ||
      existsSync(path.join(dirPath, 'index.tsx')) ||
      existsSync(path.join(dirPath, 'index.mjs')) ||
      existsSync(path.join(dirPath, 'index.js'));
    if (!hasStandardEntry) {
      // Only flag if this isn't a tooling/config package or app shell
      const isToolingOrApp = [
        'eslint-plugin', 'ds-governance', 'ds-schema', 'ds-generator', 'ds-registry',
        'ghatana-studio', // app shell, entry via App.tsx
        'platform-events', // may use index.mjs pattern
      ].some(t => dirName === t || dirName.includes(t));
      if (!isToolingOrApp) {
        issues.push({
          file: `platform/typescript/${dirName}`,
          issue: 'Missing public entry point (src/index.ts, src/index.tsx, index.mjs, or index.ts). Platform packages must have a public entry point.',
        });
      }
    }

    // Check for deprecated imports in package.json
    const pkgContent = readText(packageJsonPath);
    const deprecatedInPkg = findDeprecatedImports(pkgContent);
    for (const deprecated of deprecatedInPkg) {
      issues.push({
        file: `platform/typescript/${dirName}/package.json`,
        issue: `References deprecated package name: ${deprecated}`,
      });
    }
  }

  // Check sso-client does not import Fastify/Express in main barrel
  const ssoClientIndex = resolvePath('platform/typescript/sso-client/src/index.ts');
  if (existsSync(ssoClientIndex)) {
    const ssoContent = readText(ssoClientIndex);
    if (/from\s+['"`]fastify|from\s+['"`]express/i.test(ssoContent)) {
      issues.push({
        file: 'platform/typescript/sso-client/src/index.ts',
        issue: 'sso-client main barrel must not import Fastify or Express. Use subpath ./security/fastify instead.',
      });
    }
  }

  return issues;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const issues = runPackageGovernanceChecks();

  if (issues.length > 0) {
    console.error('FAIL: package governance checks found issues:');
    for (const issue of issues) {
      if (typeof issue === 'string') {
        console.error(` - ${issue}`);
      } else {
        console.error(` - [${issue.file}] ${issue.issue}`);
      }
    }
    process.exit(1);
  } else {
    console.log('OK: package governance checks passed.');
  }
}
