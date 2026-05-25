#!/usr/bin/env node

import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/check-action-plane-boundaries.mjs';
const EVIDENCE_PATH = '.kernel/evidence/action-plane-boundaries.json';
const COMMAND = 'pnpm check:action-plane-boundaries';
const DEFAULT_ROOTS = ['products/data-cloud/planes'];
const TEXT_EXTENSIONS = new Set(['.java', '.kt', '.kts', '.gradle', '.xml', '.md', '.yaml', '.yml']);
const EXCLUDED_DIRS = new Set(['build', '.gradle', '.idea', 'node_modules']);

const FORBIDDEN_RULES = [
  {
    id: 'aep-internal-package',
    pattern: /\b(?:import\s+)?com\.ghatana\.aep\.(?!api\b|client\b|event\.spi\b|model\b|sdk\b)[A-Za-z0-9_.]*/g,
    message: 'Non-action Data Cloud planes must not import AEP internal packages',
  },
  {
    id: 'action-plane-gradle-dependency',
    pattern: /project\(["']?:products:data-cloud:planes:action(?::[^"')]+)?["']?\)/g,
    message: 'Non-action Data Cloud planes must not depend on Action Plane Gradle modules',
  },
];

const FORBIDDEN_SEMANTIC_RULES = [
  {
    id: 'eventcloud-semantics-in-data-cloud-plane',
    pattern: /\bEventCloud\b/g,
    message: 'Non-action Data Cloud planes must use EventLog/storage-plane wording; EventCloud semantics are AEP-owned',
  },
  {
    id: 'patternspec-semantics-in-data-cloud-plane',
    pattern: /\b(?:PatternSpec|EPL|EventOperatorCapability|EventOperator runtime|complex event processing|CEP)\b/g,
    message: 'Non-action Data Cloud planes must not expose PatternSpec, EPL, EventOperator, or CEP semantics',
  },
];

function isSemanticBoundaryAllowed(file, source, matchIndex) {
  if (file.includes('/src/test/')) {
    return true;
  }

  if (file.includes('/planes/shared-spi/') && /AEP'?s EventCloud can use for persistence/.test(source)) {
    return true;
  }

  const lineStart = source.lastIndexOf('\n', matchIndex) + 1;
  const lineEnd = source.indexOf('\n', matchIndex);
  const line = source.slice(lineStart, lineEnd === -1 ? source.length : lineEnd);
  return /\b(?:must not|does not|do not|not expose|not own|AEP-owned|owned by AEP|persistence plugin|stable SPI)\b/i.test(line);
}

function currentGitSha(root) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

function walk(root, relativePath, files) {
  const fullPath = path.join(root, relativePath);
  if (!existsSync(fullPath)) {
    return;
  }
  const stats = statSync(fullPath);
  if (stats.isFile()) {
    if (TEXT_EXTENSIONS.has(path.extname(fullPath))) {
      files.push(relativePath.replaceAll(path.sep, '/'));
    }
    return;
  }

  for (const entry of readdirSync(fullPath)) {
    if (EXCLUDED_DIRS.has(entry)) {
      continue;
    }
    const child = path.join(relativePath, entry);
    const normalized = child.replaceAll(path.sep, '/');
    if (normalized === 'products/data-cloud/planes/action' || normalized.startsWith('products/data-cloud/planes/action/')) {
      continue;
    }
    walk(root, child, files);
  }
}

export function findActionPlaneBoundaryViolations(root = process.cwd(), scanRoots = DEFAULT_ROOTS) {
  const files = [];
  for (const scanRoot of scanRoots) {
    walk(root, scanRoot, files);
  }

  const violations = [];
  for (const file of files) {
    const source = readFileSync(path.join(root, file), 'utf8');
    for (const rule of FORBIDDEN_RULES) {
      for (const match of source.matchAll(rule.pattern)) {
        const line = source.slice(0, match.index).split(/\r?\n/).length;
        violations.push({
          file,
          line,
          rule: rule.id,
          message: rule.message,
          match: match[0],
        });
      }
    }

    for (const rule of FORBIDDEN_SEMANTIC_RULES) {
      for (const match of source.matchAll(rule.pattern)) {
        if (isSemanticBoundaryAllowed(file, source, match.index)) {
          continue;
        }
        const line = source.slice(0, match.index).split(/\r?\n/).length;
        violations.push({
          file,
          line,
          rule: rule.id,
          message: rule.message,
          match: match[0],
        });
      }
    }
  }
  return { files, violations };
}

export function createActionPlaneBoundaryEvidence(root = process.cwd(), now = new Date()) {
  const { files, violations } = findActionPlaneBoundaryViolations(root);
  return {
    generatedAt: now.toISOString(),
    pass: violations.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: currentGitSha(root),
    },
    scope: {
      scannedRoots: DEFAULT_ROOTS,
      excludedRoots: ['products/data-cloud/planes/action'],
      rule: 'Non-action Data Cloud planes must not import AEP internals or depend on Action Plane modules.',
      semanticRule: 'Non-action Data Cloud planes must not expose AEP-owned EventCloud, PatternSpec/EPL, EventOperator, CEP, or adaptive event runtime semantics.',
      publicAepPackageAllowlist: [
        'com.ghatana.aep.api',
        'com.ghatana.aep.client',
        'com.ghatana.aep.event.spi',
        'com.ghatana.aep.model',
        'com.ghatana.aep.sdk',
      ],
    },
    summary: {
      scannedFiles: files.length,
      violationCount: violations.length,
    },
    violations,
  };
}

export function writeActionPlaneBoundaryEvidence(root = process.cwd(), evidence = createActionPlaneBoundaryEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createActionPlaneBoundaryEvidence(root);
  writeActionPlaneBoundaryEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Action Plane boundary check failed:');
    for (const violation of evidence.violations) {
      console.error(`- ${violation.file}:${violation.line}: ${violation.message} (${violation.match})`);
    }
    process.exit(1);
  }

  console.log(`Action Plane boundary evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
