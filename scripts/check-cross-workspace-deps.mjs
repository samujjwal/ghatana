#!/usr/bin/env node
/**
 * Cross-Workspace Dependency Policy Enforcement
 *
 * Enforces that product packages do NOT directly depend on other product
 * packages via workspace: protocol. Products must only depend on:
 *   - Their own internal libs (same product prefix, e.g. @ghatana/yappc-*)
 *   - Platform packages (@ghatana/platform-*, @ghatana/design-system, etc.)
 *   - Public NPM packages
 *
 * Cross-product dependencies create hard coupling, circular dependency risks,
 * and build-order brittleness. They must be routed through platform contracts.
 *
 * Usage: node scripts/check-cross-workspace-deps.mjs
 * Exit:  0 = no violations, 1 = violations found
 *
 * @doc.type   tooling
 * @doc.purpose Enforce product-to-product dependency isolation
 * @doc.layer  infrastructure
 */

import { readFileSync, readdirSync, existsSync, statSync } from 'fs';
import { join, relative, resolve } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// Product package name prefixes — these are NOT allowed as cross-deps
// ---------------------------------------------------------------------------
const PRODUCT_PREFIXES = [
  '@ghatana/yappc-',
  '@ghatana/data-cloud-',
  '@ghatana/aep-',
  '@ghatana/audio-video-',
  '@ghatana/flashit-',
  '@ghatana/tutorputor-',
  '@ghatana/dcmaar-',
  '@ghatana/phr-',
  '@ghatana/software-org-',
  '@yappc/',
];

// Platform packages that are explicitly allowed anywhere
const PLATFORM_PREFIXES = [
  '@ghatana/platform-',
  '@ghatana/design-system',
  '@ghatana/tokens',
  '@ghatana/theme',
  '@ghatana/realtime',
  '@ghatana/utils',
  '@ghatana/api-',
  '@ghatana/canvas-',
];

// Product root directories (sub-globs as declared in pnpm-workspace.yaml)
const PRODUCT_ROOTS = [
  'products/data-cloud',
  'products/aep',
  'products/yappc',
  'products/audio-video',
  'products/flashit',
  'products/tutorputor',
  'products/dcmaar',
  'products/phr',
  'products/software-org',
];

// ---------------------------------------------------------------------------

/** @typedef {{ pkg: string, dep: string, version: string, file: string }} Violation */

/**
 * Determines the product prefix for a package located at `pkgPath`.
 * Returns null when the package is not a product package.
 * @param {string} pkgPath - absolute path to the package directory
 * @returns {string | null}
 */
function owningProduct(pkgPath) {
  const rel = relative(REPO_ROOT, pkgPath).replace(/\\/g, '/');
  for (const root of PRODUCT_ROOTS) {
    if (rel.startsWith(root + '/') || rel === root) {
      // e.g. "products/yappc" → "yappc"
      return root.split('/')[1];
    }
  }
  return null;
}

/**
 * Checks whether `depName` is a cross-product dependency relative to `ownerProduct`.
 * @param {string} depName
 * @param {string} ownerProduct  e.g. "yappc"
 * @returns {boolean}
 */
function isCrossProductDep(depName, ownerProduct) {
  // Platform packages are always allowed
  if (PLATFORM_PREFIXES.some((p) => depName.startsWith(p))) return false;

  // Check if the dep belongs to a different product
  for (const prefix of PRODUCT_PREFIXES) {
    if (depName.startsWith(prefix)) {
      // Derive the product name from the prefix
      const prefixProduct = prefix.replace('@ghatana/', '').replace('@', '').replace('/', '').replace('-', '');
      // Allow intra-product (same product depends on its own lib)
      const depProduct = prefix.replace('@ghatana/', '').replace(/-$/, '').replace('@', '').replace('/', '');
      if (!ownerProduct.startsWith(depProduct) && !depProduct.startsWith(ownerProduct)) {
        return true;
      }
    }
  }
  return false;
}

/**
 * Collect all package.json files under a root directory (non-recursive past node_modules).
 * @param {string} dir
 * @returns {string[]}
 */
function findPackageJsonFiles(dir) {
  const results = [];
  if (!existsSync(dir)) return results;

  const entries = readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...findPackageJsonFiles(full));
    } else if (entry.name === 'package.json') {
      results.push(full);
    }
  }
  return results;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

/** @type {Violation[]} */
const violations = [];
/** @type {string[]} */
const scanned = [];

for (const productRoot of PRODUCT_ROOTS) {
  const absRoot = join(REPO_ROOT, productRoot);
  const pkgFiles = findPackageJsonFiles(absRoot);

  for (const pkgFile of pkgFiles) {
    let pkg;
    try {
      pkg = JSON.parse(readFileSync(pkgFile, 'utf8'));
    } catch {
      continue;
    }

    if (!pkg.name) continue;

    const pkgDir = pkgFile.replace(/[/\\]package\.json$/, '');
    const owner = owningProduct(pkgDir);
    if (!owner) continue;

    scanned.push(pkg.name);

    const allDeps = {
      ...pkg.dependencies,
      ...pkg.devDependencies,
      ...pkg.peerDependencies,
    };

    for (const [dep, version] of Object.entries(allDeps)) {
      if (!String(version).startsWith('workspace:')) continue;

      if (isCrossProductDep(dep, owner)) {
        violations.push({
          pkg: pkg.name,
          dep,
          version: String(version),
          file: relative(REPO_ROOT, pkgFile).replace(/\\/g, '/'),
        });
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

console.log(`\nCross-Workspace Dependency Policy Check`);
console.log(`Scanned ${scanned.length} product packages across ${PRODUCT_ROOTS.length} product areas.\n`);

if (violations.length === 0) {
  console.log('✅ No cross-product workspace dependency violations found.');
  process.exit(0);
}

console.error(`❌ Found ${violations.length} cross-product dependency violation(s):\n`);
for (const v of violations) {
  console.error(`  ${v.pkg}`);
  console.error(`    depends on: ${v.dep} (${v.version})`);
  console.error(`    file: ${v.file}`);
  console.error(`    → Route through a platform contract package instead.\n`);
}
console.error(
  `Cross-product workspace: dependencies create hard coupling and must be removed.\n` +
  `Products must only depend on platform packages or their own internal libs.\n`
);
process.exit(1);
