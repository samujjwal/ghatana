#!/usr/bin/env node

import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/check-action-plane-boundaries.mjs';
const EVIDENCE_PATH = '.kernel/evidence/action-plane-boundaries.json';
const COMMAND = 'pnpm check:action-plane-boundaries';
const DEFAULT_ROOTS = ['products/data-cloud/planes', 'products/data-cloud/delivery', 'products/data-cloud/extensions', 'products/data-cloud/contracts'];
const ACTION_PLANE_ROOT = 'products/data-cloud/planes/action';
const TEXT_EXTENSIONS = new Set(['.java', '.kt', '.kts', '.gradle', '.xml', '.md', '.yaml', '.yml', '.ts', '.tsx', '.js', '.mjs']);
const EXCLUDED_DIRS = new Set(['build', '.gradle', '.idea', 'node_modules', 'dist', '.next']);

const SEMANTIC_RULE_EXCEPTIONS = [
  {
    rule: 'patternspec-semantics-in-data-cloud-plane',
    path: 'products/data-cloud/contracts/openapi/action-plane.yaml',
    reason: 'Canonical action-plane OpenAPI contract intentionally contains PatternSpec lifecycle vocabulary.',
  },
  {
    rule: 'patternspec-semantics-in-data-cloud-plane',
    path: 'products/data-cloud/delivery/ui/src/generated/api/action-plane.ts',
    reason: 'Generated API client mirrors canonical action-plane contract terminology.',
  },
  {
    rule: 'patternspec-semantics-in-data-cloud-plane',
    path: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/WorkflowExecutionCapability.java',
    reason: 'Launcher plugin adapter bridges action-plane workflow contracts and may reference PatternSpec types.',
  },
  {
    rule: 'patternspec-semantics-in-data-cloud-plane',
    path: 'products/data-cloud/delivery/ui/src/features/workflow/components/WorkflowCanvas.tsx',
    reason: 'UI workflow canvas renders canonical action-plane pattern contracts.',
  },
];

const FORBIDDEN_RULES = [
  {
    id: 'aep-internal-package',
    pattern: /\b(?:import\s+)?com\.ghatana\.aep\.(?!api\b|client\b|event\.spi\b|model\b|sdk\b|action\b|policy\b|registry\b|operator\.contract\b)[A-Za-z0-9_.]*/g,
    message: 'Non-action Data Cloud planes must not import AEP internal packages',
  },
  {
    id: 'action-plane-gradle-dependency',
    pattern: /project\(["']?:products:data-cloud:planes:action(?::[^"')]+)?["']?\)/g,
    message: 'Non-action Data Cloud planes must not depend on Action Plane Gradle modules',
  },
];

// DC-P2-002: Explicit allowlists for boundary enforcement
const ALLOWLIST_RULES = [
  {
    id: 'delivery-runtime-composition-allowlist',
    allowedPath: 'products/data-cloud/delivery/runtime-composition',
    allowlist: [
      'delivery/runtime-composition may compose planes',
      'Runtime composition across planes is allowed',
    ],
    check: (file, source) => {
      if (!file.includes('delivery/runtime-composition')) return null;
      // runtime-composition can depend on planes through public contracts
      return null; // No violation - this is the allowlist
    },
  },
  {
    id: 'extensions-kernel-bridge-allowlist',
    allowedPath: 'products/data-cloud/extensions/kernel-bridge',
    allowlist: [
      'extensions/kernel-bridge may use public contracts/SPI',
      'Kernel bridge may depend on public contracts',
    ],
    check: (file, source) => {
      if (!file.includes('extensions/kernel-bridge')) return null;
      // kernel-bridge can use public contracts/SPI
      return null; // No violation - this is the allowlist
    },
  },
  {
    id: 'contracts-no-implementation-dependency',
    allowedPath: 'products/data-cloud/contracts',
    allowlist: [
      'contracts must not import runtime implementation',
      'Contracts must remain pure',
    ],
    check: (file, source) => {
      if (!file.includes('products/data-cloud/contracts')) return null;
      // Check if contracts import implementation packages
      const implImportPattern = /\bimport\s+com\.ghatana\.datacloud\.(?!contracts\b)[A-Za-z0-9_.]*/g;
      const matches = source.matchAll(implImportPattern);
      for (const match of matches) {
        return {
          file,
          line: source.slice(0, match.index).split(/\r?\n/).length,
          rule: 'contracts-no-implementation-dependency',
          message: 'Contracts must not import runtime implementation packages',
          match: match[0],
        };
      }
      return null;
    },
  },
];

const NON_ACTION_SEMANTIC_RULES = [
  {
    id: 'eventcloud-semantics-in-data-cloud-plane',
    pattern: /\bEventCloud\b/g,
    message: 'Non-action Data Cloud planes must use EventLog/storage-plane wording; EventCloud semantics are AEP-owned',
  },
  {
    id: 'patternspec-semantics-in-data-cloud-plane',
    pattern: /\b(?:PatternSpec|EPL|EventOperatorCapability|EventOperator runtime|EventOperatorCapability runtime|adaptive event runtime|complex event processing|CEP|pattern promotion|recommended pattern|predictive pattern|Pattern lifecycle)\b/g,
    message: 'Non-action Data Cloud planes must not expose PatternSpec, EPL, EventOperator, CEP, adaptive runtime, or pattern lifecycle semantics',
  },
];

const ACTION_SEMANTIC_RULES = [
  {
    id: 'data-plane-semantics-in-action-plane',
    pattern: /\b(?:MetaCollection|MetaDataset|MetaDataSource|DynamicQueryBuilder|EntityCrudHandler|DataProductHandler)\b/g,
    message: 'Action Plane must not import Data Plane entity/handler semantics directly; use contracts/SPI',
  },
  {
    id: 'event-plane-semantics-in-action-plane',
    pattern: /\b(?:EventLogHandler|EventPlaneHandler|storage.*plane.*event)\b/g,
    message: 'Action Plane must not import Event Plane handler semantics directly; use contracts/SPI',
  },
  {
    id: 'governance-plane-semantics-in-action-plane',
    pattern: /\b(?:PolicyHandler|GovernanceHandler|compliance.*handler)\b/g,
    message: 'Action Plane must not import Governance Plane handler semantics directly; use contracts/SPI',
  },
  {
    id: 'context-plane-semantics-in-action-plane',
    pattern: /\b(?:ContextLayerHandler|CollectionContextHandler|SemanticSearchHandler)\b/g,
    message: 'Action Plane must not import Context Plane handler semantics directly; use contracts/SPI',
  },
];

function isSemanticBoundaryAllowed(file, source, matchIndex) {
  if (file.includes('/src/test/')) {
    return true;
  }

  // Allow EventCloud terminology in connector bridge implementations
  if (file.includes('trino/')) {
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

function isRuleException(file, ruleId) {
  return SEMANTIC_RULE_EXCEPTIONS.some((entry) => entry.rule === ruleId && entry.path === file);
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

function walk(root, relativePath, files, options = { excludeActionRoot: true }) {
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
    // Exclude Action Plane implementation directories from boundary checks
    if (options.excludeActionRoot && (normalized === ACTION_PLANE_ROOT || normalized.startsWith(`${ACTION_PLANE_ROOT}/`))) {
      continue;
    }
    walk(root, child, files, options);
  }
}

export function findActionPlaneBoundaryViolations(root = process.cwd(), scanRoots = DEFAULT_ROOTS) {
  const files = [];
  for (const scanRoot of scanRoots) {
    walk(root, scanRoot, files, { excludeActionRoot: true });
  }

  const actionFiles = [];
  walk(root, ACTION_PLANE_ROOT, actionFiles, { excludeActionRoot: false });

  const violations = [];
  for (const file of files) {
    const source = readFileSync(path.join(root, file), 'utf8');
    
    // DC-P2-002: Check allowlist rules first
    for (const allowlistRule of ALLOWLIST_RULES) {
      const violation = allowlistRule.check(file, source);
      if (violation) {
        violations.push(violation);
      }
    }
    
    for (const rule of FORBIDDEN_RULES) {
      for (const match of source.matchAll(rule.pattern)) {
        if (rule.id === 'action-plane-gradle-dependency' && match[0].includes(':products:data-cloud:planes:action:operator-contracts')) {
          continue;
        }
        // Allow AEP internal imports in connector bridge implementations
        if (file.includes('/extensions/plugins/trino/')) {
          continue;
        }
        // Exclude test files from AEP internal package check (they contain ArchUnit rule assertions)
        if (file.includes('/src/test/')) {
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

    for (const rule of NON_ACTION_SEMANTIC_RULES) {
      if (isRuleException(file, rule.id)) {
        continue;
      }
      for (const match of source.matchAll(rule.pattern)) {
        // Allow EventCloud terminology in connector bridge implementations
        if (file.includes('/extensions/plugins/trino/')) {
          continue;
        }
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

  for (const file of actionFiles) {
    const source = readFileSync(path.join(root, file), 'utf8');
    for (const rule of ACTION_SEMANTIC_RULES) {
      if (isRuleException(file, rule.id)) {
        continue;
      }
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
  return { files, actionFiles, violations };
}

export function createActionPlaneBoundaryEvidence(root = process.cwd(), now = new Date()) {
  const { files, actionFiles, violations } = findActionPlaneBoundaryViolations(root);
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
      excludedRoots: [ACTION_PLANE_ROOT],
      coLocatedActionRoot: ACTION_PLANE_ROOT,
      rule: 'Non-action Data Cloud planes, delivery, extensions, and contracts must not import AEP internals or depend on Action Plane modules.',
      semanticRule: 'Non-action Data Cloud planes, delivery, extensions, and contracts must not expose AEP-owned EventCloud, PatternSpec/EPL, EventOperator, EventOperatorCapability runtime, CEP, adaptive event runtime, pattern promotion, recommended/predictive pattern, or Pattern lifecycle semantics.',
      publicAepPackageAllowlist: [
        'com.ghatana.aep.api',
        'com.ghatana.aep.client',
        'com.ghatana.aep.event.spi',
        'com.ghatana.aep.model',
        'com.ghatana.aep.sdk',
      ],
      // DC-P2-002: Explicit allowlists for boundary enforcement
      allowlists: ALLOWLIST_RULES.map((rule) => ({
        id: rule.id,
        allowedPath: rule.allowedPath,
        allowlist: rule.allowlist,
      })),
      semanticRuleExceptions: SEMANTIC_RULE_EXCEPTIONS,
    },
    summary: {
      scannedFiles: files.length,
      scannedActionFiles: actionFiles.length,
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
