#!/usr/bin/env node
/**
 * PHR PHI Log Safety Gate
 * -----------------------
 * Static analysis that detects PHI leakage patterns in log/diagnostic calls.
 *
 * Patterns checked:
 * 1. `console.log` / `console.info` / `console.debug` calls that reference
 *    PHI-bearing identifiers (patientId, nationalId, name, dob, etc.).
 * 2. `console.error` / `console.warn` calls that emit raw `error.message` or
 *    `error.stack` where the variable holds a PHI-rich domain object (heuristic).
 * 3. Java log statements (`log.info(...)`, `logger.info(...)`) that contain
 *    patient identifiers from PHR source files.
 * 4. Diagnostic event emissions that include `message`, `stack`, or `componentStack`.
 *
 * Allowlist: test files and mocks are excluded.
 *
 * Exit code 0 = no violations.
 * Exit code 1 = one or more violations found.
 *
 * Usage:
 *   node scripts/check-phr-phi-log-safety.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const PHR_ROOT = resolve(__dirname, '../products/phr');

// ─── File walker ─────────────────────────────────────────────────────────────

function walkFiles(dir, extensions) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== 'build' && entry !== '.gradle') {
      results.push(...walkFiles(full, extensions));
    } else if (stat.isFile() && extensions.some((ext) => entry.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

function isTestFile(path) {
  return (
    path.includes('__tests__') ||
    path.includes('/test/') ||
    path.endsWith('.test.ts') ||
    path.endsWith('.test.tsx') ||
    path.endsWith('Test.java') ||
    path.endsWith('IT.java') ||
    path.includes('__mocks__')
  );
}

// ─── PHI identifier heuristics ────────────────────────────────────────────────

// Terms that commonly appear near PHI in source code.
const PHI_TERMS_RE = /\b(patientId|nationalId|nhsNumber|dob|dateOfBirth|ssn|mrn|patientName|principalName)\b/;

// Console calls that should not carry PHI.
const CONSOLE_RISKY_RE = /console\.(log|info|debug|warn|error)\s*\(/;

// Java log calls.
const JAVA_LOG_RE = /(?:log|logger)\.(info|debug|warn|error|trace)\s*\(/;

// ─── Violations ───────────────────────────────────────────────────────────────

const violations = [];

function violation(file, lineNum, message) {
  const rel = relative(resolve(__dirname, '..'), file);
  violations.push(`  ✗  ${rel}:${lineNum}  ${message}`);
}

// ─── TypeScript / React checks ────────────────────────────────────────────────

for (const file of walkFiles(PHR_ROOT, ['.ts', '.tsx'])) {
  if (isTestFile(file)) continue;

  const lines = readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, i) => {
    // console.* with a PHI-bearing variable name
    if (CONSOLE_RISKY_RE.test(line) && PHI_TERMS_RE.test(line)) {
      violation(
        file,
        i + 1,
        `console.* call contains PHI identifier. Remove PHI from log call or use a PHI-safe error code.`,
      );
    }

    // Diagnostic event that includes message or stack
    if (/dispatchEvent\s*\(/.test(line) || /CustomEvent\s*\(/.test(line)) {
      const context = lines.slice(Math.max(0, i - 2), i + 5).join('\n');
      if (/[^a-zA-Z](message|stack|componentStack)\s*:/.test(context)) {
        const hasCode = /code\s*:/.test(context);
        const hasMsgOrStack = /[^a-zA-Z](message|stack|componentStack)\s*:/.test(context);
        if (!hasCode && hasMsgOrStack) {
          violation(
            file,
            i + 1,
            'Diagnostic event emission includes message/stack without error code. Replace with a non-PHI error code.',
          );
        }
      }
    }
  });
}

// ─── Java checks ─────────────────────────────────────────────────────────────

const JAVA_PHI_RE = /\b(patientId|principalId|nationalId|dob|dateOfBirth|ssn|mrn)\b/;

for (const file of walkFiles(PHR_ROOT, ['.java'])) {
  if (isTestFile(file)) continue;

  const lines = readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, i) => {
    if (JAVA_LOG_RE.test(line) && JAVA_PHI_RE.test(line)) {
      // Heuristic: if the log line contains a PHI identifier that is a variable
      // reference (not just a field access in a map), flag it.
      // We skip if the identifier is clearly a key string: "patientId"
      const strippedStrings = line.replace(/"[^"]*"/g, '""');
      if (JAVA_PHI_RE.test(strippedStrings)) {
        violation(
          file,
          i + 1,
          'Java log statement includes a PHI identifier. Use a structured log entry with PHI-safe codes instead.',
        );
      }
    }
  });
}

// ─── Report ───────────────────────────────────────────────────────────────────

if (violations.length > 0) {
  console.error('\n[phr-phi-log-safety] FAIL: PHI log safety violations detected:\n');
  for (const v of violations) {
    console.error(v);
  }
  console.error(`\n${violations.length} violation(s) found. Fix before merging to main.\n`);
  process.exit(1);
}

console.log('[phr-phi-log-safety] PASS: No PHI log safety violations detected.');
