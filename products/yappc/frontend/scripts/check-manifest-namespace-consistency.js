#!/usr/bin/env node

/**
 * Enforces manifest-level namespace consistency for active YAPPC frontend packages.
 *
 * Policy:
 * - package.json files in apps/* and libs/* must not declare @ghatana/yappc-* dependencies
 * - this applies to dependencies, devDependencies, peerDependencies, and optionalDependencies
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const TARGET_DIRS = ['apps', 'libs'];
const LEGACY_DEP_PREFIX = '@ghatana/yappc-';
const DEP_FIELDS = [
  'dependencies',
  'devDependencies',
  'peerDependencies',
  'optionalDependencies',
];

function collectManifestPaths(baseDir) {
  if (!fs.existsSync(baseDir)) {
    return [];
  }

  const entries = fs.readdirSync(baseDir, { withFileTypes: true });
  const manifests = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }

    const manifestPath = path.join(baseDir, entry.name, 'package.json');
    if (fs.existsSync(manifestPath)) {
      manifests.push(manifestPath);
    }
  }

  return manifests;
}

function scanManifest(manifestPath) {
  const packageJson = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const violations = [];

  for (const field of DEP_FIELDS) {
    const deps = packageJson[field] || {};
    for (const depName of Object.keys(deps)) {
      if (depName.startsWith(LEGACY_DEP_PREFIX)) {
        violations.push({
          packageName: packageJson.name || '(unnamed package)',
          manifestPath: path.relative(ROOT, manifestPath),
          field,
          dependency: depName,
          version: deps[depName],
        });
      }
    }
  }

  return violations;
}

function main() {
  const manifests = TARGET_DIRS.flatMap((dir) =>
    collectManifestPaths(path.join(ROOT, dir))
  );
  const violations = manifests.flatMap(scanManifest);

  if (violations.length === 0) {
    console.log(
      'Manifest namespace check passed: no @ghatana/yappc-* dependencies in apps/* or libs/* package.json files.'
    );
    process.exit(0);
  }

  console.error(
    'Manifest namespace check failed. Migrate dependencies to @yappc/* equivalents:'
  );
  for (const violation of violations) {
    console.error(
      `- ${violation.manifestPath} (${violation.packageName}) ${violation.field}: ${violation.dependency}@${violation.version}`
    );
  }

  process.exit(1);
}

main();
