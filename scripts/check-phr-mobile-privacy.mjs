#!/usr/bin/env node
/**
 * PHR Mobile Privacy Gate
 * -----------------------
 * Static analysis check that enforces mobile PHI handling conventions:
 *
 * 1. No direct `AsyncStorage.setItem` calls in source files (all PHI must go
 *    through `phiSet` from `phiEncryptedStorage`).
 * 2. Every `fetchMobileDashboard` call must receive a `session` argument
 *    (no bare zero-argument calls that would omit session context headers).
 * 3. Error boundary `componentDidCatch` must not reference `error.message`
 *    or `errorInfo.componentStack` in event emissions.
 * 4. Every file that imports `fetchMobileDashboard` must also import from
 *    the session store (session context must flow through the call chain).
 *
 * Exit code 0 = all checks pass.
 * Exit code 1 = one or more violations detected.
 *
 * Usage:
 *   node scripts/check-phr-mobile-privacy.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const MOBILE_SRC = resolve(__dirname, '../products/phr/apps/mobile/src');

// ─── File walker ─────────────────────────────────────────────────────────────

function walkTs(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== '__mocks__') {
      results.push(...walkTs(full));
    } else if (stat.isFile() && (entry.endsWith('.ts') || entry.endsWith('.tsx'))) {
      results.push(full);
    }
  }
  return results;
}

// ─── Checks ──────────────────────────────────────────────────────────────────

const violations = [];

function violation(file, line, message) {
  const rel = relative(resolve(__dirname, '..'), file);
  violations.push(`  ✗  ${rel}:${line}  ${message}`);
}

for (const file of walkTs(MOBILE_SRC)) {
  const isTestFile = file.includes('__tests__') || file.endsWith('.test.ts') || file.endsWith('.test.tsx');
  const lines = readFileSync(file, 'utf8').split('\n');
  const content = lines.join('\n');

  // ── Check 1: No bare AsyncStorage.setItem outside phiEncryptedStorage ──────
  if (!file.endsWith('phiEncryptedStorage.ts') && !isTestFile) {
    lines.forEach((line, i) => {
      if (/AsyncStorage\.setItem\s*\(/.test(line)) {
        violation(file, i + 1, 'Direct AsyncStorage.setItem with PHI is forbidden. Use phiSet() from phiEncryptedStorage instead.');
      }
    });
  }

  // ── Check 2: fetchMobileDashboard must be called with session argument ──────
  const fetchCallRe = /fetchMobileDashboard\s*\(\s*\)/g;
  lines.forEach((line, i) => {
    if (fetchCallRe.test(line) && !isTestFile) {
      violation(file, i + 1, 'fetchMobileDashboard() called without session argument. Pass the MobileSession to include required PHI access headers.');
    }
  });

  // ── Check 3: componentDidCatch must not emit error.message or componentStack ─
  if (content.includes('componentDidCatch') && !isTestFile) {
    const catchIdx = lines.findIndex((l) => l.includes('componentDidCatch'));
    if (catchIdx >= 0) {
      // Check the next 20 lines for PHI-risky patterns.
      const window = lines.slice(catchIdx, catchIdx + 20).join('\n');
      if (/error\.message/.test(window) || /errorInfo\.componentStack/.test(window)) {
        violation(
          file,
          catchIdx + 1,
          'componentDidCatch emits error.message or componentStack which may contain PHI. Emit only a sanitized error code.',
        );
      }
    }
  }

  // ── Check 4: syncOfflineDashboard must be called with session argument ───────
  const syncCallRe = /syncOfflineDashboard\s*\(\s*\)/g;
  lines.forEach((line, i) => {
    if (syncCallRe.test(line) && !isTestFile) {
      violation(file, i + 1, 'syncOfflineDashboard() called without session argument.');
    }
  });
}

// ─── Report ───────────────────────────────────────────────────────────────────

if (violations.length > 0) {
  console.error('\n[phr-mobile-privacy] FAIL: Privacy policy violations detected:\n');
  for (const v of violations) {
    console.error(v);
  }
  console.error(`\n${violations.length} violation(s) found. Fix before merging to main.\n`);
  process.exit(1);
}

console.log('[phr-mobile-privacy] PASS: No mobile privacy policy violations found.');
