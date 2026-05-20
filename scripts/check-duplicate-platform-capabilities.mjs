#!/usr/bin/env node
/**
 * check-duplicate-platform-capabilities.mjs
 *
 * Prevents products from re-implementing capabilities that the platform already
 * provides centrally:
 *
 *   1. Product-local generic lifecycle runners (use @ghatana/kernel-lifecycle instead)
 *   2. Product-local generic gate engines (use platform lifecycle gate evaluation instead)
 *   3. Duplicate artifact/deployment/health schema files across product areas
 *      (canonical schemas live in config/ and platform/contracts/)
 *
 * Exceptions are read from config/duplication-exception-registry.json.
 * Add an entry there (with id, owner, reason, expiryDate, removalPlan) to
 * suppress a known violation temporarily.
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve, basename, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

// ──────────────────────────────────────────────────────────────────────────────
// 1. Lifecycle runner patterns — files that implement a generic "run lifecycle"
//    orchestration belong in platform/typescript/kernel-lifecycle, not products.
//
//    Pattern: file name or class that looks like a standalone lifecycle executor.
// ──────────────────────────────────────────────────────────────────────────────
const LIFECYCLE_RUNNER_FILE_PATTERNS = [
  /lifecycle[-_]?runner\.(ts|java|kt)$/i,
  /lifecycle[-_]?executor\.(ts|java|kt)$/i,
  /lifecycle[-_]?orchestrator\.(ts|java|kt)$/i,
  /run[-_]?lifecycle\.(ts|java|kt)$/i,
  /LifecycleRunner\.(ts|java|kt)$/,
  /LifecycleExecutor\.(ts|java|kt)$/,
  /LifecycleOrchestrator\.(ts|java|kt)$/,
];

// Content markers — files whose content declares a generic lifecycle runner class
const LIFECYCLE_RUNNER_CONTENT_MARKERS = [
  /class\s+\w*LifecycleRunner\b/,
  /class\s+\w*LifecycleExecutor\b/,
  /class\s+\w*LifecycleOrchestrator\b/,
  /implements\s+LifecycleRunner\b/,
  /implements\s+LifecycleExecutor\b/,
];

// ──────────────────────────────────────────────────────────────────────────────
// 2. Gate engine patterns — generic gate evaluation logic belongs in the platform.
// ──────────────────────────────────────────────────────────────────────────────
const GATE_ENGINE_FILE_PATTERNS = [
  /gate[-_]?engine\.(ts|java|kt)$/i,
  /gate[-_]?runner\.(ts|java|kt)$/i,
  /gate[-_]?evaluator\.(ts|java|kt)$/i,
  /GateEngine\.(ts|java|kt)$/,
  /GateRunner\.(ts|java|kt)$/,
  /GateEvaluator\.(ts|java|kt)$/,
];

const GATE_ENGINE_CONTENT_MARKERS = [
  /class\s+\w*GateEngine\b/,
  /class\s+\w*GateRunner\b/,
  /class\s+\w*GateEvaluator\b/,
  /implements\s+GateEngine\b/,
  /implements\s+GateRunner\b/,
];

// ──────────────────────────────────────────────────────────────────────────────
// 3. Duplicate schema names — these canonical schema filenames must not be
//    duplicated across product folders.
//
//    Canonical locations: config/*.schema.json, platform/contracts/*.schema.json
// ──────────────────────────────────────────────────────────────────────────────
const PLATFORM_SCHEMA_BASENAMES = [
  'product-artifact-manifest.schema.json',
  'product-build-manifest.schema.json',
  'product-deployment-manifest.schema.json',
  'product-release-manifest.schema.json',
  'product-shape-capability-matrix.schema.json',
  'product-lifecycle-profiles-schema.json',
  'product-route-entitlement.schema.json',
  'deployment-targets-schema.json',
  'health-check.schema.json',
  'artifact-manifest.schema.json',
  'deployment-manifest.schema.json',
  'lifecycle-manifest.schema.json',
];

// ──────────────────────────────────────────────────────────────────────────────
// Ignored path segments (skip these directories)
// ──────────────────────────────────────────────────────────────────────────────
const IGNORED_SEGMENTS = [
  '/node_modules/',
  '/dist/',
  '/build/',
  '/coverage/',
  '/.turbo/',
  '/__tests__/',
  '/.gradle/',
  '/target/',
];

function shouldIgnore(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  return IGNORED_SEGMENTS.some(seg => normalized.includes(seg));
}

function normalizeRepoRelativePath(filePath) {
  const absolutePath = resolve(repoRoot, filePath);
  return relative(repoRoot, absolutePath).replace(/\\/g, '/');
}

// ──────────────────────────────────────────────────────────────────────────────
// File walking
// ──────────────────────────────────────────────────────────────────────────────
function walkDirectory(dir, callback) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return;
  }
  for (const entry of entries) {
    const full = join(dir, entry);
    if (shouldIgnore(full + '/')) continue;
    let stats;
    try {
      stats = statSync(full);
    } catch {
      continue;
    }
    if (stats.isDirectory()) {
      walkDirectory(full, callback);
    } else if (stats.isFile()) {
      callback(full);
    }
  }
}

function listProductFiles(ext) {
  // Try ripgrep first for speed
  const rg = spawnSync('rg', ['--files', '--glob', `*.${ext}`, 'products/'], {
    cwd: repoRoot,
    encoding: 'utf8',
  });

  if (rg.status === 0) {
    return rg.stdout
      .split('\n')
      .map(l => l.trim())
      .filter(Boolean)
      .map(normalizeRepoRelativePath)
      .filter(l => !shouldIgnore(l));
  }

  // Fallback: walk manually
  const results = [];
  walkDirectory(join(repoRoot, 'products'), file => {
    if (file.endsWith(`.${ext}`)) results.push(normalizeRepoRelativePath(file));
  });
  return results;
}

function listProductJsonFiles() {
  const rg = spawnSync('rg', ['--files', '--glob', '*.schema.json', 'products/'], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  if (rg.status === 0) {
    return rg.stdout
      .split('\n')
      .map(l => l.trim())
      .filter(Boolean)
      .map(normalizeRepoRelativePath)
      .filter(l => !shouldIgnore(l));
  }
  const results = [];
  walkDirectory(join(repoRoot, 'products'), file => {
    if (file.endsWith('.schema.json')) results.push(normalizeRepoRelativePath(file));
  });
  return results;
}

// ──────────────────────────────────────────────────────────────────────────────
// Load exception registry
// ──────────────────────────────────────────────────────────────────────────────
function loadExceptionRegistry() {
  const registryPath = join(repoRoot, 'config/duplication-exception-registry.json');
  if (!existsSync(registryPath)) return [];

  try {
    const data = JSON.parse(readFileSync(registryPath, 'utf8'));
    return data.exceptions ?? [];
  } catch {
    return [];
  }
}

function buildExceptionSet(exceptions) {
  // Build a set of suppressed file paths (from affectedPaths arrays)
  const paths = new Set();
  for (const entry of exceptions) {
    if (Array.isArray(entry.affectedPaths)) {
      for (const p of entry.affectedPaths) paths.add(normalizeRepoRelativePath(p));
    }
  }
  return paths;
}

// ──────────────────────────────────────────────────────────────────────────────
// Check 1: Product-local lifecycle runners
// ──────────────────────────────────────────────────────────────────────────────
function checkLifecycleRunners(exceptions) {
  const violations = [];
  const files = [
    ...listProductFiles('ts'),
    ...listProductFiles('java'),
    ...listProductFiles('kt'),
  ];

  for (const relPath of files) {
    if (exceptions.has(relPath)) continue;

    const fileName = basename(relPath);
    const isNameMatch = LIFECYCLE_RUNNER_FILE_PATTERNS.some(p => p.test(fileName));
    if (!isNameMatch) continue;

    // Skip test files
    if (relPath.includes('.test.') || relPath.includes('.spec.') || relPath.includes('Test.java')) {
      continue;
    }

    violations.push({
      path: relPath,
      message: `Product-local lifecycle runner detected: "${relPath}". Generic lifecycle orchestration belongs in @ghatana/kernel-lifecycle (platform/typescript/kernel-lifecycle) or platform/java/lifecycle. If this is product-specific and intentional, add an exception to config/duplication-exception-registry.json.`,
    });
  }

  // Also scan by content for any matching class definitions
  for (const relPath of files) {
    if (exceptions.has(relPath)) continue;
    if (relPath.includes('.test.') || relPath.includes('.spec.') || relPath.includes('Test.java')) {
      continue;
    }
    // Only re-scan files that weren't already caught by filename
    const fileName = basename(relPath);
    if (LIFECYCLE_RUNNER_FILE_PATTERNS.some(p => p.test(fileName))) continue;

    let content;
    try {
      content = readFileSync(join(repoRoot, relPath), 'utf8');
    } catch {
      continue;
    }
    const isContentMatch = LIFECYCLE_RUNNER_CONTENT_MARKERS.some(p => p.test(content));
    if (isContentMatch) {
      violations.push({
        path: relPath,
        message: `Product-local lifecycle runner class detected in: "${relPath}". Generic lifecycle orchestration belongs in @ghatana/kernel-lifecycle. If this is product-specific and intentional, add an exception to config/duplication-exception-registry.json.`,
      });
    }
  }

  return violations;
}

// ──────────────────────────────────────────────────────────────────────────────
// Check 2: Product-local gate engines
// ──────────────────────────────────────────────────────────────────────────────
function checkGateEngines(exceptions) {
  const violations = [];
  const files = [
    ...listProductFiles('ts'),
    ...listProductFiles('java'),
    ...listProductFiles('kt'),
  ];

  for (const relPath of files) {
    if (exceptions.has(relPath)) continue;

    const fileName = basename(relPath);
    const isNameMatch = GATE_ENGINE_FILE_PATTERNS.some(p => p.test(fileName));
    if (!isNameMatch) continue;

    if (relPath.includes('.test.') || relPath.includes('.spec.') || relPath.includes('Test.java')) {
      continue;
    }

    violations.push({
      path: relPath,
      message: `Product-local gate engine detected: "${relPath}". Gate evaluation logic belongs in platform kernel lifecycle. If this is product-specific and intentional, add an exception to config/duplication-exception-registry.json.`,
    });
  }

  // Content scan
  for (const relPath of files) {
    if (exceptions.has(relPath)) continue;
    if (relPath.includes('.test.') || relPath.includes('.spec.') || relPath.includes('Test.java')) {
      continue;
    }
    const fileName = basename(relPath);
    if (GATE_ENGINE_FILE_PATTERNS.some(p => p.test(fileName))) continue;

    let content;
    try {
      content = readFileSync(join(repoRoot, relPath), 'utf8');
    } catch {
      continue;
    }
    const isContentMatch = GATE_ENGINE_CONTENT_MARKERS.some(p => p.test(content));
    if (isContentMatch) {
      violations.push({
        path: relPath,
        message: `Product-local gate engine class detected in: "${relPath}". Gate evaluation belongs in platform lifecycle. If intentional, add an exception to config/duplication-exception-registry.json.`,
      });
    }
  }

  return violations;
}

// ──────────────────────────────────────────────────────────────────────────────
// Check 3: Duplicate platform schema filenames
// ──────────────────────────────────────────────────────────────────────────────
function checkDuplicateSchemas(exceptions) {
  const violations = [];
  const schemaFiles = listProductJsonFiles();

  for (const relPath of schemaFiles) {
    if (exceptions.has(relPath)) continue;

    const fileName = basename(relPath);
    if (PLATFORM_SCHEMA_BASENAMES.includes(fileName)) {
      violations.push({
        path: relPath,
        message: `Duplicate platform schema detected: "${relPath}" uses a filename reserved for canonical platform schemas ("${fileName}"). Canonical location: config/ or platform/contracts/. If this is a product-specific override with a clear reason, add an exception to config/duplication-exception-registry.json.`,
      });
    }
  }

  return violations;
}

// ──────────────────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────────────────
function checkDuplicatePlatformCapabilities() {
  const rawExceptions = loadExceptionRegistry();
  const exceptions = buildExceptionSet(rawExceptions);

  const lifecycleViolations = checkLifecycleRunners(exceptions);
  const gateViolations = checkGateEngines(exceptions);
  const schemaViolations = checkDuplicateSchemas(exceptions);

  const allViolations = [...lifecycleViolations, ...gateViolations, ...schemaViolations];

  if (allViolations.length === 0) {
    console.log('OK: Duplicate platform capability checks passed (0 violations).');
    return;
  }

  console.error(
    `FAIL: Duplicate platform capability check found ${allViolations.length} violation(s):`
  );
  for (const v of allViolations) {
    console.error(`\n  [${v.path}]`);
    console.error(`    ${v.message}`);
  }
  console.error(
    '\nFix: Remove product-local duplications and use the canonical platform implementation,\n' +
    'or add a justified exception to config/duplication-exception-registry.json.'
  );
  process.exit(1);
}

checkDuplicatePlatformCapabilities();
