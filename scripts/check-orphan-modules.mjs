#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const REPORT_DIR = path.join(repoRoot, 'build', 'reports', 'architecture');
const REPORT_PATH = path.join(REPORT_DIR, 'orphan-modules.json');

const REQUIRED_TS_PACKAGE_FILES = ['package.json', 'tsconfig.json'];
const REQUIRED_JAVA_FILES = ['build.gradle.kts'];

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function readJson(absolutePath) {
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

function loadOrphanAllowlist() {
  const allowlistPath = path.join(repoRoot, 'config', 'orphan-module-allowlist.json');
  if (!existsSync(allowlistPath)) {
    return { allowlist: [] };
  }
  return readJson(allowlistPath);
}

function isAllowlisted(allowlist, relativePath, missingFile) {
  const normalizedPath = normalize(relativePath);
  for (const entry of allowlist.allowlist ?? []) {
    if (normalize(entry.path) === normalizedPath && entry.missingFile === missingFile) {
      // Check if the allowlist entry has expired
      if (entry.reviewBy) {
        const reviewDate = new Date(entry.reviewBy);
        if (reviewDate < new Date()) {
          console.warn(`Warning: Allowlist entry expired for ${relativePath}:${missingFile} (expired ${entry.reviewBy})`);
          return false;
        }
      }
      return true;
    }
  }
  return false;
}

function isShellOnlyDirectory(directoryPath) {
  const entries = readdirSync(directoryPath);
  if (entries.length === 0) {
    return true;
  }
  // A shell-only directory contains only index.ts and no package.json
  const hasPackageJson = entries.includes('package.json');
  const hasOnlyIndex = entries.length === 1 && entries[0] === 'index.ts';
  return hasOnlyIndex && !hasPackageJson;
}

function isBuildOnlyDirectory(directoryPath) {
  const entries = getDirectoryEntries(directoryPath).filter((entry) => entry !== '.DS_Store');
  if (entries.length === 0) {
    return true;
  }

  if (entries.length === 1 && entries[0] === 'build') {
    return true;
  }

  return false;
}

function directoryHasFiles(directoryPath) {
  const entries = getDirectoryEntries(directoryPath).filter((entry) => entry !== '.DS_Store');
  for (const entry of entries) {
    const entryPath = path.join(directoryPath, entry);
    let stats;
    try {
      stats = statSync(entryPath);
    } catch {
      continue;
    }

    if (stats.isFile()) {
      return true;
    }

    if (stats.isDirectory() && directoryHasFiles(entryPath)) {
      return true;
    }
  }

  return false;
}

function isBuildAndEmptySrcSkeleton(directoryPath) {
  const entries = getDirectoryEntries(directoryPath).filter((entry) => entry !== '.DS_Store');
  const allowedEntries = new Set(['build', 'src']);
  if (entries.length === 0 || !entries.every((entry) => allowedEntries.has(entry))) {
    return false;
  }

  const srcPath = path.join(directoryPath, 'src');
  if (!existsSync(srcPath)) {
    return false;
  }

  return !directoryHasFiles(srcPath);
}

function getDirectoryEntries(directoryPath) {
  try {
    return readdirSync(directoryPath);
  } catch {
    return [];
  }
}

export function findOrphanModules(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const allowlist = options.allowlist ?? loadOrphanAllowlist();

  const violations = [];
  const orphans = [];

  // Check TypeScript packages under platform/typescript/*
  const tsPackagesDir = path.join(root, 'platform', 'typescript');
  if (existsSync(tsPackagesDir)) {
    const tsPackages = getDirectoryEntries(tsPackagesDir);
    for (const pkg of tsPackages) {
      const pkgPath = path.join(tsPackagesDir, pkg);
      const relativePath = normalize(path.relative(root, pkgPath));

      let stats;
      try {
        stats = statSync(pkgPath);
      } catch {
        continue;
      }
      if (!stats.isDirectory()) {
        continue;
      }

      // Ignore generated build-output directories accidentally left under module roots.
      if (pkg === 'build' || isBuildOnlyDirectory(pkgPath)) {
        continue;
      }

      // Check for shell-only directory (index.ts only, no package.json)
      if (isShellOnlyDirectory(pkgPath)) {
        if (!isAllowlisted(allowlist, relativePath, 'package.json')) {
          violations.push(
            `${relativePath}: folder-only shell with no package.json. ` +
              'Remediation: Add package.json or remove the orphan directory.',
          );
          orphans.push({ path: relativePath, reason: 'shell-only-directory', type: 'typescript' });
        }
        continue;
      }

      // Check for required files
      const entries = getDirectoryEntries(pkgPath);
      for (const required of REQUIRED_TS_PACKAGE_FILES) {
        if (!entries.includes(required)) {
          // Skip sub-directories (foundation, canvas, etc.) that are parent folders
          const hasSubPackages = entries.some((entry) => {
            const entryPath = path.join(pkgPath, entry);
            try {
              return statSync(entryPath).isDirectory() && existsSync(path.join(entryPath, 'package.json'));
            } catch {
              return false;
            }
          });

          if (hasSubPackages) {
            continue;
          }

          if (!isAllowlisted(allowlist, relativePath, required)) {
            violations.push(
              `${relativePath}: missing required file '${required}'. ` +
                'Remediation: Add the file or allowlist in config/orphan-module-allowlist.json.',
            );
            orphans.push({ path: relativePath, reason: `missing-${required}`, type: 'typescript' });
          }
        }
      }
    }
  }

  // Check Java modules under platform/java/*
  const javaModulesDir = path.join(root, 'platform', 'java');
  if (existsSync(javaModulesDir)) {
    const javaModules = getDirectoryEntries(javaModulesDir);
    for (const mod of javaModules) {
      const modPath = path.join(javaModulesDir, mod);
      const relativePath = normalize(path.relative(root, modPath));

      let stats;
      try {
        stats = statSync(modPath);
      } catch {
        continue;
      }
      if (!stats.isDirectory()) {
        continue;
      }

      // Ignore generated build-output directories accidentally left under module roots.
      if (mod === 'build' || isBuildOnlyDirectory(modPath) || isBuildAndEmptySrcSkeleton(modPath)) {
        continue;
      }

      const entries = getDirectoryEntries(modPath);
      for (const required of REQUIRED_JAVA_FILES) {
        if (!entries.includes(required)) {
          if (!isAllowlisted(allowlist, relativePath, required)) {
            violations.push(
              `${relativePath}: missing required file '${required}'. ` +
                'Remediation: Add the file or allowlist in config/orphan-module-allowlist.json.',
            );
            orphans.push({ path: relativePath, reason: `missing-${required}`, type: 'java' });
          }
        }
      }
    }
  }

  return { violations, orphans };
}

export function checkOrphanModules(options = {}) {
  return findOrphanModules(options);
}

function writeReport(orphans) {
  try {
    mkdirSync(REPORT_DIR, { recursive: true });
    writeFileSync(
      REPORT_PATH,
      JSON.stringify(
        {
          schemaVersion: '1.0.0',
          generatedAt: new Date().toISOString(),
          orphanCount: orphans.length,
          orphans,
        },
        null,
        2,
      ) + '\n',
    );
    console.log(`Report written to ${path.relative(repoRoot, REPORT_PATH)}`);
  } catch (error) {
    console.warn(`Warning: Failed to write report: ${error.message}`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { violations, orphans } = checkOrphanModules();

  writeReport(orphans);

  if (violations.length === 0) {
    console.log('OK: orphan module checks passed.');
    process.exit(0);
  }

  console.error('FAIL: orphan module checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}
