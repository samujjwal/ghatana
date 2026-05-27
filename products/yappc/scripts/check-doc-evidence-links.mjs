#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve } from 'node:path';

const docsRoot = resolve(process.argv[2] ?? 'products/yappc/docs');
const evidenceDoc = resolve(docsRoot, 'PRODUCTION_READINESS_EVIDENCE_LINKS.md');
const claimPattern = /\b(100%\s+production-ready|production[- ]ready|ready for immediate production deployment|ready for use)\b/i;
const nonClaimPattern = /\b(cannot|without|not|only when|must not jump|evidence requirements|scans)\b/i;

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      return walk(path);
    }
    return extname(path) === '.md' ? [path] : [];
  });
}

function hasEvidenceMarker(lines, lineIndex) {
  return lines
    .slice(Math.max(0, lineIndex - 15), Math.min(lines.length, lineIndex + 3))
    .some((line) => /\bEvidence(?: links?)?:/i.test(line));
}

function claimViolations(file) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/);
  const violations = [];
  let inFence = false;

  lines.forEach((line, index) => {
    if (/^\s*```/.test(line)) {
      inFence = !inFence;
      return;
    }
    if (inFence || /^\s*#/.test(line) || !claimPattern.test(line) || nonClaimPattern.test(line)) {
      return;
    }
    if (!hasEvidenceMarker(lines, index)) {
      violations.push(`${file}:${index + 1}: ${line.trim()}`);
    }
  });

  return violations;
}

function evidenceLinkViolations() {
  if (!existsSync(evidenceDoc)) {
    return [`Missing evidence link matrix: ${evidenceDoc}`];
  }

  const content = readFileSync(evidenceDoc, 'utf8');
  const links = [...content.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)].map((match) => match[1]);
  const localLinks = links
    .filter((link) => !/^(https?:|#)/i.test(link))
    .map((link) => link.split('#')[0])
    .filter(Boolean);

  return localLinks
    .map((link) => normalize(resolve(dirname(evidenceDoc), link)))
    .filter((path) => !existsSync(path))
    .map((path) => `Evidence matrix links to missing file: ${path}`);
}

const violations = [
  ...walk(docsRoot).flatMap((file) => claimViolations(file)),
  ...evidenceLinkViolations(),
];

if (violations.length > 0) {
  console.error('YAPPC doc evidence link check failed:');
  violations.forEach((violation) => console.error(`- ${violation}`));
  process.exit(1);
}

console.log(`YAPPC doc evidence link check passed for ${docsRoot}`);
