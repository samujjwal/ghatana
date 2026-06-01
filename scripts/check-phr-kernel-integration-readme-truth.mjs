#!/usr/bin/env node

/**
 * Guards the PHR Kernel integration README from drifting back into unsupported
 * completion or production-readiness claims.
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const README_RELATIVE_PATH = 'products/phr/PHR_KERNEL_INTEGRATION_README.md';

const REQUIRED_TRUTH_MARKERS = [
  /code-grounded status snapshot/i,
  /not release evidence/i,
  /not complete in this snapshot/i,
  /YAPPC must not contain PHR-specific/i,
  /Product-specific healthcare providers remain inside `products\/phr\/\*\*`/i,
];

const UNSUPPORTED_CLAIM_PATTERNS = [
  /\b(all|every|fully)\s+integration\s+tasks\s+(are\s+)?complete\b/i,
  /\bPHR\s+Kernel\s+integration\s+(is\s+)?complete\b/i,
  /\bproduction[- ]ready\b/i,
  /\bHIPAA\s+(ready|complete|certified|compliant)\b/i,
  /\brelease\s+evidence\s+(is\s+)?complete\b/i,
  /\bstaging\s+sign[- ]off\s+(is\s+)?complete\b/i,
];

function readReadme(rootDir) {
  const readmePath = path.join(rootDir, README_RELATIVE_PATH);
  if (!existsSync(readmePath)) {
    throw new Error(`Missing ${README_RELATIVE_PATH}`);
  }
  return readFileSync(readmePath, 'utf8');
}

export function findPhrKernelIntegrationReadmeTruthViolations(source) {
  const violations = [];

  for (const marker of REQUIRED_TRUTH_MARKERS) {
    if (!marker.test(source)) {
      violations.push(`Missing required truth marker: ${marker}`);
    }
  }

  const lines = source.split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const pattern of UNSUPPORTED_CLAIM_PATTERNS) {
      if (!pattern.test(line)) {
        continue;
      }

      const lineText = line.trim();
      const explicitlyNegated =
        /\bnot\s+complete\b/i.test(lineText) ||
        /\bnot\s+release\s+evidence\b/i.test(lineText) ||
        /\bnot\s+production[- ]read(?:y|iness)\b/i.test(lineText) ||
        /\bstill\s+required\b/i.test(lineText) ||
        /\bmust\s+be\s+produced\b/i.test(lineText);

      if (!explicitlyNegated) {
        violations.push(
          `${README_RELATIVE_PATH}:${index + 1} contains unsupported completion/readiness claim: ${lineText}`,
        );
      }
    }
  });

  return violations;
}

export function checkPhrKernelIntegrationReadmeTruth(options = {}) {
  const rootDir = options.rootDir ?? repoRoot;
  return findPhrKernelIntegrationReadmeTruthViolations(readReadme(rootDir));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const rootArg = process.argv.find((arg) => arg.startsWith('--root='));
  const rootDir = rootArg ? path.resolve(rootArg.slice('--root='.length)) : repoRoot;
  const violations = checkPhrKernelIntegrationReadmeTruth({ rootDir });

  if (violations.length === 0) {
    console.log('OK: PHR Kernel integration README truth check passed.');
    process.exit(0);
  }

  console.error('FAIL: PHR Kernel integration README truth check found issues:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}
