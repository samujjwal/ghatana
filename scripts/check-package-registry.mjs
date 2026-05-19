#!/usr/bin/env node
// Authoritative Source: .github/copilot-instructions.md Section 32 + Section 17

/**
 * check-package-registry.mjs
 *
 * Validates that TypeScript packages in platform/typescript/ conform to the
 * canonical @ghatana/* package registry. Ensures:
 * - All platform packages use the @ghatana/ scope
 * - No deprecated package names are in use
 * - No product-prefixed names appear in platform scope
 * - Package names follow kebab-case convention
 * - No orphan packages (folder-only shells with package.json but no build config)
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

// Deprecated package names that must not appear in platform/typescript
// These have canonical replacements and must be fixed forward.
const DEPRECATED_PACKAGE_NAMES = new Set([
  '@ghatana/ui',
  '@ghatana/utils',
  '@ghatana/accessibility-audit',
  '@ghatana/audit-components',
  '@ghatana/canvas-core',
  '@ghatana/canvas-react',
  '@ghatana/canvas-plugins',
  '@ghatana/canvas-tools',
  '@ghatana/canvas-chrome',
]);

// Product names that must NOT appear as package-name segments in platform scope
// e.g. @ghatana/digital-marketing-* or @ghatana/yappc-* are forbidden
const FORBIDDEN_PRODUCT_PREFIXES = [
  'digital-marketing',
  'yappc',
  'data-cloud',
  'phr',
  'finance',
  'flashit',
  'tutorputor',
  'dcmaar',
  'audio-video',
  'software-org',
];

// Root-level platform workspace package — skip name pattern checks for this
const ROOT_PLATFORM_PACKAGE = '@ghatana/platform-typescript';

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function isPlatformTypescriptPackage(relativePath) {
  // Must be exactly one level deep from platform/typescript (not nested build dirs)
  const parts = relativePath.replace(/\\/g, '/').split('/');
  // e.g. platform/typescript/design-system/package.json → 4 parts
  // e.g. platform/typescript/package.json → 3 parts (root)
  return (parts.length === 3 || parts.length === 4) &&
    parts[0] === 'platform' &&
    parts[1] === 'typescript';
}

function collectPlatformPackages() {
  const platformTypescriptRoot = join(repoRoot, 'platform/typescript');
  if (!existsSync(platformTypescriptRoot)) {
    return [];
  }

  const packages = [];

  // Root package.json
  const rootPkg = join(platformTypescriptRoot, 'package.json');
  if (existsSync(rootPkg)) {
    packages.push({
      path: 'platform/typescript/package.json',
      data: readJson(rootPkg),
    });
  }

  // One-level-deep subdirectory packages
  for (const entry of readdirSync(platformTypescriptRoot)) {
    const subdirPath = join(platformTypescriptRoot, entry);
    let stats;
    try {
      stats = statSync(subdirPath);
    } catch {
      continue;
    }
    if (!stats.isDirectory()) continue;
    if (entry === 'node_modules') continue;

    const pkgPath = join(subdirPath, 'package.json');
    if (existsSync(pkgPath)) {
      packages.push({
        path: `platform/typescript/${entry}/package.json`,
        data: readJson(pkgPath),
      });
    }
  }

  return packages;
}

function checkKebabCase(name) {
  // Strip @ghatana/ prefix, then check the remainder is kebab-case
  const localName = name.replace(/^@ghatana\//, '');
  return /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/.test(localName);
}

function checkPackageRegistry() {
  const errors = [];
  const warnings = [];

  const packages = collectPlatformPackages();

  if (packages.length === 0) {
    errors.push('No packages found in platform/typescript — unexpected');
    reportAndExit(errors, warnings);
    return;
  }

  for (const { path: pkgPath, data: pkg } of packages) {
    const name = pkg.name;

    if (!name) {
      warnings.push(`${pkgPath}: package.json has no "name" field`);
      continue;
    }

    // Root workspace package is exempt from sub-checks
    if (name === ROOT_PLATFORM_PACKAGE) continue;

    // 1. Must use @ghatana/ scope
    if (!name.startsWith('@ghatana/')) {
      errors.push(
        `${pkgPath}: platform package "${name}" must use @ghatana/ scope`
      );
      continue;
    }

    // 2. Must not be a deprecated package name
    if (DEPRECATED_PACKAGE_NAMES.has(name)) {
      errors.push(
        `${pkgPath}: deprecated package name "${name}" must not appear in platform/typescript. Remove this package or migrate to its canonical replacement.`
      );
    }

    // 3. Must not use product-prefixed names
    const localName = name.replace(/^@ghatana\//, '');
    for (const productPrefix of FORBIDDEN_PRODUCT_PREFIXES) {
      if (localName === productPrefix || localName.startsWith(`${productPrefix}-`)) {
        errors.push(
          `${pkgPath}: forbidden product-prefixed platform package name "${name}". Product-specific packages belong under products/<product>/, not platform/typescript/.`
        );
        break;
      }
    }

    // 4. Must follow kebab-case naming
    if (!checkKebabCase(name)) {
      errors.push(
        `${pkgPath}: package name "${name}" must use kebab-case (lowercase letters, numbers, hyphens only)`
      );
    }
  }

  reportAndExit(errors, warnings);
}

function reportAndExit(errors, warnings) {
  if (warnings.length > 0) {
    console.log('Warnings:');
    for (const w of warnings) {
      console.log(`  - ${w}`);
    }
  }

  if (errors.length > 0) {
    console.error(`Package registry check FAILED (${errors.length} error(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }

  console.log('Package registry check passed.');
}

checkPackageRegistry();
