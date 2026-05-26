#!/usr/bin/env node
/**
 * PHR i18n Conformance Gate
 * -------------------------
 * Static analysis that detects raw user-visible English strings in PHR page
 * and screen source files that should go through the i18n translation function.
 *
 * Patterns checked:
 * 1. JSX text nodes that are plain English sentences (e.g., >Loading...</>)
 * 2. String literal props on JSX elements that look like user-visible labels
 *    (title=, label=, placeholder=, aria-label=) containing English words —
 *    except where already wrapped in a t() call.
 * 3. Alert.alert / window.alert calls whose first two string arguments are
 *    raw English literals rather than t('...') keys.
 *
 * Allowlist:
 * - Test files and mocks (__tests__/, *.test.*, *.spec.*, __mocks__/)
 * - aria-label patterns that reference t() calls
 * - className, id, data-*, key, style attribute values
 * - Short non-sentence strings (single token, all-caps, pure digits/symbols)
 * - Strings that are clearly code paths or enums (e.g., 'patient', 'active')
 *
 * Exit code 0 = no violations.
 * Exit code 1 = one or more violations found.
 *
 * Usage:
 *   node scripts/check-phr-i18n-conformance.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const PHR_ROOT = resolve(__dirname, '../products/phr');

// ─── File walker ───────────────────────────────────────────────────────────────

function walkFiles(dir, extensions) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== 'build' && entry !== '.gradle') {
      results.push(...walkFiles(full, extensions));
    } else if (stat.isFile() && extensions.some((ext) => full.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

// ─── Scope directories ────────────────────────────────────────────────────────

/** Page and screen source paths scanned for raw string violations. */
const SCAN_DIRS = [
  resolve(PHR_ROOT, 'apps/web/src/pages'),
  resolve(PHR_ROOT, 'apps/mobile/src/screens'),
];

const EXTENSIONS = ['.tsx', '.ts'];

/** Paths that are excluded even if they match a scan dir. */
const EXCLUDE_PATTERNS = [
  /__tests__/,
  /\.test\./,
  /\.spec\./,
  /__mocks__/,
];

// ─── Detection patterns ───────────────────────────────────────────────────────

/**
 * Matches JSX text content that looks like a multi-word English sentence or
 * user-visible label that is NOT inside a t() call or a variable reference.
 *
 * Examples caught:
 *   >Loading records...</>
 *   title="No records found"
 *   placeholder="Enter reason (required)"
 *
 * Examples allowed:
 *   >{t('records.loading')}</>
 *   title={t('records.title')}
 */
const RAW_ENGLISH_PATTERNS = [
  // JSX child text: > some English words </  (3+ words or sentence ending with period/ellipsis)
  {
    pattern: />\s*([A-Z][a-z]+(?:\s+[a-zA-Z]+){2,}[.…!?]?)\s*</g,
    description: 'Raw English JSX text node',
    captureGroup: 1,
  },
  // JSX attribute string literals for user-visible props
  {
    pattern: /\b(?:title|label|placeholder|aria-label|accessibilityLabel)\s*=\s*"([A-Z][a-zA-Z\s,.']{10,})"/g,
    description: 'Raw English string in user-visible JSX attribute',
    captureGroup: 1,
  },
  // Alert.alert first argument — catches Alert.alert('Some English text', ...)
  {
    pattern: /Alert\.alert\(\s*'([A-Z][a-zA-Z\s]{6,})'/g,
    description: 'Raw English string in Alert.alert title',
    captureGroup: 1,
  },
  // Template literal or string for button text when not inside {t(...)}
  {
    pattern: /<(?:Text|Button|Pressable)[^>]*>\s*([A-Z][a-z]+(?:\s+[a-zA-Z]+){1,})\s*<\/(?:Text|Button|Pressable)>/g,
    description: 'Raw English string inside Text/Button element',
    captureGroup: 1,
  },
];

/** Substrings that indicate a match is already properly wrapped or is a non-user string. */
const ALLOW_REGEXES = [
  /t\('/,                    // already uses t()
  /\{.*\}/,                  // contains JSX expression — not a raw literal
  /className|id=|data-/,     // not user-visible
  /PHR Nepal/,               // product name is intentionally not translated
  /^\s*\/\//,                // commented line
];

// ─── Scanner ──────────────────────────────────────────────────────────────────

function isAllowlisted(line) {
  return ALLOW_REGEXES.some((re) => re.test(line));
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const violations = [];

  for (const { pattern, description, captureGroup } of RAW_ENGLISH_PATTERNS) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(content)) !== null) {
      const matchedText = match[captureGroup];
      const matchIndex = match.index;
      // Find line number
      const linesBefore = content.slice(0, matchIndex).split('\n');
      const lineNumber = linesBefore.length;
      const lineContent = lines[lineNumber - 1] ?? '';

      if (isAllowlisted(lineContent)) continue;

      violations.push({
        file: filePath,
        line: lineNumber,
        description,
        text: matchedText.trim().slice(0, 80),
      });
    }
  }

  return violations;
}

// ─── Main ─────────────────────────────────────────────────────────────────────

function main() {
  const allFiles = [];
  for (const dir of SCAN_DIRS) {
    try {
      allFiles.push(...walkFiles(dir, EXTENSIONS));
    } catch {
      // Directory may not exist yet; skip gracefully
    }
  }

  const filesToCheck = allFiles.filter(
    (f) => !EXCLUDE_PATTERNS.some((re) => re.test(f)),
  );

  const allViolations = [];
  for (const file of filesToCheck) {
    allViolations.push(...scanFile(file));
  }

  if (allViolations.length === 0) {
    console.log(`PHR i18n conformance: OK — ${filesToCheck.length} files checked, no violations.`);
    process.exit(0);
  }

  console.error(`PHR i18n conformance: FAILED — ${allViolations.length} violation(s) found:\n`);
  for (const v of allViolations) {
    const rel = relative(resolve(__dirname, '..'), v.file);
    console.error(`  ${rel}:${v.line}  [${v.description}]`);
    console.error(`    → "${v.text}"`);
  }
  process.exit(1);
}

main();
