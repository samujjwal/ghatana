#!/usr/bin/env node
/**
 * Production Stub / Placeholder Scan
 *
 * Scans production source files for patterns that indicate incomplete, demo,
 * or stub implementations that must not be shipped to production.
 *
 * Exempted paths (never scanned):
 *   - test, tests, __tests__, *.test.*, *.spec.*
 *   - docs, *.md
 *   - node_modules, build, dist, .gradle
 *   - scripts/, monitoring/, config/
 *
 * Exit: 0 = clean, 1 = critical violations found
 *
 * Usage: node scripts/check-production-stubs.mjs [--report] [--allowlist=path]
 *
 * @doc.type   tooling
 * @doc.purpose Fail CI when production code contains stubs, mocks, or placeholders
 * @doc.layer  infrastructure
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, relative, extname, resolve } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// Load declarative production-critical scan scopes
// ---------------------------------------------------------------------------
const SCAN_CONFIG_PATH = join(REPO_ROOT, 'config/production-critical-scopes.config.json');
let scanConfig = {
  scanScopes: [
    { path: 'platform', critical: true },
    { path: 'products', critical: true },
    { path: 'shared-services', critical: true },
    { path: 'platform-kernel', critical: true },
    { path: 'platform-plugins', critical: true },
  ],
  excludedPathSegments: [],
  excludedFilenamePatterns: [],
  scanPatterns: [],
};

if (existsSync(SCAN_CONFIG_PATH)) {
  try {
    scanConfig = JSON.parse(readFileSync(SCAN_CONFIG_PATH, 'utf8'));
  } catch (error) {
    console.warn(`Warning: failed to load scan config from ${SCAN_CONFIG_PATH}, using defaults`);
  }
}

// ---------------------------------------------------------------------------
// CLI args
// ---------------------------------------------------------------------------
const args = process.argv.slice(2);
const PRODUCE_REPORT = args.includes('--report');
const ALLOWLIST_FLAG = args.find((a) => a.startsWith('--allowlist='));
const ALLOWLIST_PATH = ALLOWLIST_FLAG ? ALLOWLIST_FLAG.split('=')[1] : null;
const CHANGED_ONLY = args.includes('--changed-only');
const BASE_REF_FLAG = args.find((a) => a.startsWith('--base-ref='));
const BASE_REF = BASE_REF_FLAG ? BASE_REF_FLAG.split('=')[1] : 'origin/main';

/** @returns {Set<string> | null} */
function getChangedFiles() {
  if (!CHANGED_ONLY) return null;
  try {
    const output = execSync(`git diff --name-only ${BASE_REF}...HEAD`, {
      cwd: REPO_ROOT,
      stdio: ['ignore', 'pipe', 'ignore'],
      encoding: 'utf8',
    });
    return new Set(
      output
        .split('\n')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
        .map((s) => s.replace(/\\/g, '/')),
    );
  } catch {
    console.warn(`Warning: failed to resolve changed files from ${BASE_REF}; scanning full scope.`);
    return null;
  }
}

const changedFiles = getChangedFiles();

// ---------------------------------------------------------------------------
// Patterns — each entry describes one category of violation
// ---------------------------------------------------------------------------

/** @type {{ id: string; severity: 'critical' | 'warning'; pattern: RegExp; description: string }[]} */
const PATTERNS = scanConfig.scanPatterns.map((p) => ({
  id: p.id,
  severity: p.severity,
  pattern: new RegExp(p.pattern, 'i'),
  description: p.description,
}));

// ---------------------------------------------------------------------------
// File-extension filter for production files
// ---------------------------------------------------------------------------
const PROD_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs', '.java', '.kt']);

// ---------------------------------------------------------------------------
// Path filters — these segments anywhere in the path trigger exclusion
// ---------------------------------------------------------------------------
const EXCLUDED_PATH_SEGMENTS = scanConfig.excludedPathSegments;

const EXCLUDED_FILENAME_PATTERNS = scanConfig.excludedFilenamePatterns.map((p) => new RegExp(p));

// ---------------------------------------------------------------------------
// Allowlist loading
// ---------------------------------------------------------------------------

/** @type {Map<string, { owner: string; expiry: string; reason: string }[]>} */
const allowlist = new Map();

if (ALLOWLIST_PATH && existsSync(ALLOWLIST_PATH)) {
  try {
    const raw = JSON.parse(readFileSync(ALLOWLIST_PATH, 'utf8'));
    for (const entry of raw) {
      if (!allowlist.has(entry.file)) allowlist.set(entry.file, []);
      allowlist.get(entry.file).push({ owner: entry.owner, expiry: entry.expiry, reason: entry.reason });
    }
    console.log(`Loaded ${allowlist.size} allowlisted file(s) from ${ALLOWLIST_PATH}`);
  } catch {
    console.warn(`Warning: could not parse allowlist at ${ALLOWLIST_PATH}`);
  }
}

// ---------------------------------------------------------------------------
// File walking
// ---------------------------------------------------------------------------

/**
 * @param {string} dir
 * @returns {string[]}
 */
function walkDir(dir) {
  if (!existsSync(dir)) return [];
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const rel = relative(REPO_ROOT, full).replace(/\\/g, '/');

    // Exclude by segment
    if (EXCLUDED_PATH_SEGMENTS.some((seg) => rel.includes(seg))) continue;

    const stat = statSync(full);
    if (stat.isDirectory()) {
      results.push(...walkDir(full));
    } else if (PROD_EXTENSIONS.has(extname(full))) {
      if (EXCLUDED_FILENAME_PATTERNS.some((p) => p.test(full))) continue;
      results.push(full);
    }
  }
  return results;
}

// ---------------------------------------------------------------------------
// Scan
// ---------------------------------------------------------------------------

/** @type {{ file: string; line: number; col: number; patternId: string; severity: string; description: string; text: string }[]} */
const violations = [];

const scanRoots = scanConfig.scanScopes.map((scope) => join(REPO_ROOT, scope.path));

for (const root of scanRoots) {
  for (const filePath of walkDir(root)) {
    const rel = relative(REPO_ROOT, filePath).replace(/\\/g, '/');

    if (CHANGED_ONLY && changedFiles && !changedFiles.has(rel)) {
      continue;
    }

    // Skip allowlisted files (expired entries still trigger a warning)
    const allowEntries = allowlist.get(rel);
    if (allowEntries) {
      const today = new Date().toISOString().slice(0, 10);
      const expired = allowEntries.filter((e) => e.expiry < today);
      if (expired.length > 0) {
        console.warn(`\n⚠  Expired allowlist entry for ${rel}:`);
        for (const e of expired) {
          console.warn(`   owner: ${e.owner}, expiry: ${e.expiry}, reason: ${e.reason}`);
        }
      } else {
        continue; // still valid allowlist — skip
      }
    }

    let source;
    try {
      source = readFileSync(filePath, 'utf8');
    } catch {
      continue;
    }

    const lines = source.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      for (const { id, severity, pattern, description } of PATTERNS) {
        const match = pattern.exec(line);
        if (match) {
          violations.push({
            file: rel,
            line: i + 1,
            col: match.index + 1,
            patternId: id,
            severity,
            description,
            text: line.trim().slice(0, 120),
          });
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

const critical = violations.filter((v) => v.severity === 'critical');
const warnings = violations.filter((v) => v.severity === 'warning');

if (violations.length === 0) {
  console.log('✅  No production stub/placeholder violations found.');
  process.exit(0);
}

// Group by product/severity for the summary
/** @param {typeof violations} vs */
function printViolations(vs) {
  for (const v of vs) {
    console.log(`\n  ${v.severity.toUpperCase()}  [${v.patternId}]  ${v.file}:${v.line}:${v.col}`);
    console.log(`  ${v.description}`);
    console.log(`  → ${v.text}`);
  }
}

console.log('\n══════════════════════════════════════════════════════════════');
console.log('  Production Stub / Placeholder Scan Report');
console.log('══════════════════════════════════════════════════════════════');
console.log(`  Critical: ${critical.length}   Warnings: ${warnings.length}   Total: ${violations.length}`);

if (critical.length > 0) {
  console.log('\n── Critical violations (CI will fail) ─────────────────────────');
  printViolations(critical);
}

if (warnings.length > 0) {
  console.log('\n── Warnings (CI will not fail) ─────────────────────────────────');
  printViolations(warnings);
}

console.log('\n══════════════════════════════════════════════════════════════\n');

if (PRODUCE_REPORT) {
  const reportPath = join(REPO_ROOT, 'build', 'reports', 'stub-scan.json');
  try {
    const { mkdirSync, writeFileSync } = await import('fs');
    mkdirSync(join(REPO_ROOT, 'build', 'reports'), { recursive: true });
    writeFileSync(reportPath, JSON.stringify({ summary: { critical: critical.length, warnings: warnings.length, total: violations.length }, violations }, null, 2));
    console.log(`Report written to ${reportPath}`);
  } catch (err) {
    console.warn(`Could not write report: ${err.message}`);
  }
}

if (critical.length > 0) {
  console.error(`❌  ${critical.length} critical violation(s) found. Fix before merging.`);
  process.exit(1);
}

console.log(`⚠  ${warnings.length} warning(s) found. No critical violations — CI passes.`);
process.exit(0);
