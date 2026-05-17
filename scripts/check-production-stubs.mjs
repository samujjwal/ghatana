#!/usr/bin/env node
// Authoritative Source: docs/TESTING.md
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
const DEFAULT_ALLOWLIST_PATH = join(REPO_ROOT, 'config', 'production-stubs.allowlist.json');
const ALLOWLIST_PATH = ALLOWLIST_FLAG ? ALLOWLIST_FLAG.split('=')[1] : DEFAULT_ALLOWLIST_PATH;
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

/** @type {{ id: string; severity: 'critical' | 'warning'; pattern: RegExp; description: string; includePaths?: RegExp[]; excludePaths?: RegExp[] }[]} */
const PATTERNS = scanConfig.scanPatterns.map((p) => ({
  id: p.id,
  severity: p.severity,
  pattern: new RegExp(p.pattern, 'i'),
  description: p.description,
  includePaths: Array.isArray(p.includePaths) ? p.includePaths.map((value) => new RegExp(value, 'i')) : undefined,
  excludePaths: Array.isArray(p.excludePaths) ? p.excludePaths.map((value) => new RegExp(value, 'i')) : undefined,
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

/** @type {Map<string, { owner: string; expiry: string; reason: string; issueLink: string; safeFallback: string; featureFlag: string }[]>} */
const allowlist = new Map();
const allowlistErrors = [];

if (ALLOWLIST_PATH && existsSync(ALLOWLIST_PATH)) {
  try {
    const raw = JSON.parse(readFileSync(ALLOWLIST_PATH, 'utf8'));
    if (!Array.isArray(raw)) {
      throw new Error('allowlist must be a JSON array');
    }
    for (const entry of raw) {
      const validation = validateAllowlistEntry(entry);
      if (validation.length > 0) {
        allowlistErrors.push(...validation.map((error) => `${entry?.file ?? '<unknown file>'}: ${error}`));
        continue;
      }
      if (!allowlist.has(entry.file)) allowlist.set(entry.file, []);
      allowlist.get(entry.file).push({
        owner: entry.owner,
        expiry: entry.expiry,
        reason: entry.reason,
        issueLink: entry.issueLink,
        safeFallback: entry.safeFallback,
        featureFlag: entry.featureFlag,
      });
    }
    if (ALLOWLIST_FLAG) {
      console.log(`Loaded ${allowlist.size} allowlisted file(s) from ${ALLOWLIST_PATH}`);
    }
  } catch {
    allowlistErrors.push(`could not parse allowlist at ${ALLOWLIST_PATH}`);
  }
}

function validateAllowlistEntry(entry) {
  const errors = [];
  if (!entry || typeof entry !== 'object') {
    return ['entry must be an object'];
  }
  if (typeof entry.file !== 'string' || entry.file.trim().length === 0) {
    errors.push('file is required');
  }
  if (typeof entry.owner !== 'string' || entry.owner.trim().length === 0) {
    errors.push('owner is required');
  }
  if (typeof entry.reason !== 'string' || entry.reason.trim().length === 0) {
    errors.push('reason is required');
  }
  if (typeof entry.expiry !== 'string' || Number.isNaN(Date.parse(entry.expiry))) {
    errors.push('expiry must be an ISO date');
  }
  if (typeof entry.issueLink !== 'string' || entry.issueLink.trim().length === 0) {
    errors.push('issueLink is required');
  } else if (!/^GH-\d+$/i.test(entry.issueLink.trim()) && !/^https:\/\//i.test(entry.issueLink.trim())) {
    errors.push('issueLink must be a GH-<id> reference or an https URL');
  }
  if (typeof entry.safeFallback !== 'string' || entry.safeFallback.trim().length === 0) {
    errors.push('safeFallback is required');
  }
  if (typeof entry.featureFlag !== 'string' || entry.featureFlag.trim().length === 0) {
    errors.push('featureFlag is required');
  }
  return errors;
}

/**
 * Skip warning-pattern matches on comment-only lines to avoid counting
 * JSDoc/examples as production violations.
 *
 * Critical patterns remain fully scanned, including comments.
 *
 * @param {string} line
 * @returns {boolean}
 */
function isCommentOnlyLine(line) {
  const trimmed = line.trim();
  return (
    trimmed.startsWith('//') ||
    trimmed.startsWith('/*') ||
    trimmed.startsWith('*') ||
    trimmed.startsWith('*/')
  );
}

/**
 * Best-effort check for whether the current line is inside a method that returns Promise<Void>.
 * This prevents false positives for valid ActiveJ success completions like `return Promise.of(null);`.
 *
 * @param {string[]} lines
 * @param {number} index 0-based line index
 * @returns {boolean}
 */
function isWithinPromiseVoidMethod(lines, index) {
  const start = Math.max(0, index - 240);
  for (let i = index; i >= start; i--) {
    const candidate = lines[i].trim();
    // Stop at class/interface boundary to avoid crossing into another type block.
    if (/\b(class|interface|record|enum)\b/.test(candidate)) {
      return false;
    }
    // Find the nearest likely method declaration above this line.
    if (/^(public|protected|private)\s+/.test(candidate) && candidate.includes('(')) {
      return /Promise\s*<\s*Void\s*>/.test(candidate);
    }
    // Also handle package-private methods with explicit Promise return.
    if (/^Promise\s*<\s*[^>]+>\s+\w+\s*\(/.test(candidate)) {
      return /Promise\s*<\s*Void\s*>/.test(candidate);
    }
  }
  return false;
}

/**
 * Detect whether an empty-list return is likely a defensive guard clause,
 * e.g. `if (x == null || x.isEmpty()) { return List.of(); }`.
 *
 * @param {string[]} lines
 * @param {number} index 0-based line index
 * @returns {boolean}
 */
function isGuardClauseEmptyCollectionReturn(lines, index) {
  let previous = null;
  for (let i = index - 1; i >= Math.max(0, index - 6); i--) {
    const candidate = lines[i].trim();
    if (candidate.length === 0) continue;
    previous = candidate;
    break;
  }
  if (!previous) return false;

  return (
    /^if\s*\(.*\)\s*\{?$/.test(previous) ||
    /^else\s+if\s*\(.*\)\s*\{?$/.test(previous) ||
    /^case\s+.+:\s*$/.test(previous) ||
    /^default\s*:\s*$/.test(previous)
  );
}

/**
 * Detect fallback empty-list returns in collection parsing/normalization helpers,
 * e.g. methods that branch on `instanceof List` and otherwise return an empty list.
 *
 * @param {string[]} lines
 * @param {number} index 0-based line index
 * @returns {boolean}
 */
function isCollectionParsingFallback(lines, index) {
  const start = Math.max(0, index - 36);
  let sawListCheck = false;
  let sawProjection = false;

  for (let i = start; i < index; i++) {
    const candidate = lines[i].trim();
    if (/(instanceof\s+List|instanceof\s+Collection|\.split\(|Arrays\.stream\()/.test(candidate)) {
      sawListCheck = true;
    }
    if (
      /\.toList\(\)/.test(candidate) ||
      /Collectors\.toList\(\)/.test(candidate) ||
      /List\.copyOf\(/.test(candidate) ||
      /new\s+ArrayList\s*</.test(candidate) ||
      /new\s+ArrayList\s*\(/.test(candidate)
    ) {
      sawProjection = true;
    }
  }

  return sawListCheck && sawProjection;
}

/**
 * Detect operational empty-list fallback in remote HTTP calls where a non-200
 * response or exception intentionally degrades to an empty result.
 *
 * @param {string[]} lines
 * @param {number} index 0-based line index
 * @returns {boolean}
 */
function isOperationalRemoteFallbackEmptyCollection(lines, index) {
  const start = Math.max(0, index - 70);
  const end = Math.min(lines.length - 1, index + 24);
  let sawHttpSend = false;
  let sawStatusHandling = false;
  let sawExceptionHandling = false;

  for (let i = start; i <= end; i++) {
    const candidate = lines[i].trim();
    if (/httpClient\.send\(/.test(candidate)) {
      sawHttpSend = true;
    }
    if (/response\.statusCode\(\)/.test(candidate)) {
      sawStatusHandling = true;
    }
    if (/catch\s*\(\s*Exception\s+/.test(candidate)) {
      sawExceptionHandling = true;
    }
  }

  return sawHttpSend && sawStatusHandling && sawExceptionHandling;
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
          console.warn(
            `   owner: ${e.owner}, expiry: ${e.expiry}, reason: ${e.reason}, issue: ${e.issueLink}, fallback: ${e.safeFallback}, flag: ${e.featureFlag}`,
          );
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
      for (const { id, severity, pattern, description, includePaths, excludePaths } of PATTERNS) {
        if (severity === 'warning' && isCommentOnlyLine(line)) {
          continue;
        }
        if (id === 'RETURN_NULL_PROMISE' && isWithinPromiseVoidMethod(lines, i)) {
          continue;
        }
        if (id === 'RETURN_EMPTY_LIST' && isGuardClauseEmptyCollectionReturn(lines, i)) {
          continue;
        }
        if (id === 'RETURN_EMPTY_LIST' && isCollectionParsingFallback(lines, i)) {
          continue;
        }
        if (id === 'RETURN_EMPTY_LIST' && isOperationalRemoteFallbackEmptyCollection(lines, i)) {
          continue;
        }
        if (includePaths && includePaths.length > 0 && !includePaths.some((pathPattern) => pathPattern.test(rel))) {
          continue;
        }
        if (excludePaths && excludePaths.length > 0 && excludePaths.some((pathPattern) => pathPattern.test(rel))) {
          continue;
        }
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

for (const error of allowlistErrors) {
  critical.push({
    file: ALLOWLIST_PATH ?? 'allowlist',
    line: 1,
    col: 1,
    patternId: 'INVALID_ALLOWLIST',
    severity: 'critical',
    description: 'Production stub allowlist entries require owner, expiry, reason, issueLink, safeFallback, and featureFlag',
    text: error,
  });
}

if (critical.length === 0 && warnings.length === 0) {
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
console.log(`  Critical: ${critical.length}   Warnings: ${warnings.length}   Total: ${critical.length + warnings.length}`);

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
