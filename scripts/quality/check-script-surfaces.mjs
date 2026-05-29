#!/usr/bin/env node

import { existsSync, readdirSync, statSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const registryPath = path.join(repoRoot, 'config', 'script-surface-registry.json');

const SCRIPT_EXTENSIONS = new Set(['.mjs', '.js', '.cjs', '.sh', '.ps1']);
const STALE_SCRIPT_PATH_PATTERNS = [
  /session-notes\//i,
  /COMPLETION_REPORT/i,
  /FINAL_STATUS/i,
  /VERIFICATION_REPORT/i,
  /IMPLEMENTATION_PROGRESS/i,
  /IMPLEMENTATION_COMPLETE/i,
];

function normalizePath(filePath) {
  return filePath.replace(/\\/g, '/');
}

function loadRegistry() {
  if (!existsSync(registryPath)) {
    throw new Error('Missing config/script-surface-registry.json');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function walkScripts(rootDir) {
  if (!existsSync(rootDir)) {
    return [];
  }

  const scripts = [];
  const stack = [rootDir];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current) {
      continue;
    }

    for (const entry of readdirSync(current)) {
      const fullPath = path.join(current, entry);
      const stats = statSync(fullPath);
      if (stats.isDirectory()) {
        if (entry === 'node_modules' || entry === '.git' || entry === 'dist' || entry === 'build') {
          continue;
        }
        stack.push(fullPath);
        continue;
      }

      if (SCRIPT_EXTENSIONS.has(path.extname(fullPath).toLowerCase())) {
        scripts.push(normalizePath(path.relative(repoRoot, fullPath)));
      }
    }
  }

  return scripts;
}

function matchesGlob(value, glob) {
  const escaped = glob
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/\*\*/g, '<<<DOUBLE>>>')
    .replace(/\*/g, '[^/]*')
    .replace(/<<<DOUBLE>>>/g, '.*');
  return new RegExp(`^${escaped}$`).test(value);
}

export function runScriptSurfaceCheck() {
  const registry = loadRegistry();
  const failures = [];
  const warnings = [];

  const registryEntries = registry.surfaces ?? [];
  const byPath = new Map(registryEntries.map((entry) => [normalizePath(entry.path), entry]));

  for (const entry of registryEntries) {
    const filePath = path.join(repoRoot, entry.path);
    if (entry.status === 'delete') {
      if (existsSync(filePath)) {
        failures.push(`${entry.path}: marked delete but file still exists`);
      }
      continue;
    }

    if (!existsSync(filePath)) {
      failures.push(`${entry.path}: registered script missing`);
      continue;
    }

    if (entry.destructive && !entry.hasDryRun) {
      warnings.push(`${entry.path}: destructive script without dry-run support`);
    }

    if ((entry.kind === 'ci-check' || entry.kind === 'release-tool') && (!entry.invokedBy || entry.invokedBy.length === 0)) {
      failures.push(`${entry.path}: ${entry.kind} must declare invokedBy references`);
    }

    if (entry.status !== 'delete' && entry.path !== 'scripts/quality/check-script-surfaces.mjs') {
      const text = readFileSync(filePath, 'utf8');
      for (const pattern of STALE_SCRIPT_PATH_PATTERNS) {
        if (pattern.test(text)) {
          failures.push(`${entry.path}: references stale one-off session/report paths`);
          break;
        }
      }
    }
  }

  const managedRoots = registry.managedRoots ?? [];
  const managedScripts = new Set(
    managedRoots.flatMap((root) => walkScripts(path.join(repoRoot, root))),
  );

  const allowPatterns = registry.allowUnregisteredPaths ?? [];
  for (const scriptPath of managedScripts) {
    if (byPath.has(scriptPath)) {
      continue;
    }

    const allowed = allowPatterns.some((pattern) => matchesGlob(scriptPath, normalizePath(pattern)));
    if (!allowed) {
      failures.push(`${scriptPath}: unregistered managed script`);
    }
  }

  return {
    passed: failures.length === 0,
    failures,
    warnings,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = runScriptSurfaceCheck();

  if (result.warnings.length > 0) {
    console.warn('WARN: script surface checks found warnings:');
    for (const warning of result.warnings) {
      console.warn(` - ${warning}`);
    }
  }

  if (result.passed) {
    console.log('OK: script surface checks passed.');
    process.exit(0);
  }

  console.error('FAIL: script surface checks found issues:');
  for (const failure of result.failures) {
    console.error(` - ${failure}`);
  }
  process.exit(1);
}
