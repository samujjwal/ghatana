#!/usr/bin/env node

/**
 * Phase 6: Audit script to identify direct agent usage that needs migration to agent capabilities.
 *
 * This script scans the codebase for:
 * - Direct TypedAgent.execute() calls
 * - Direct agent registry resolve() calls without operator wrapping
 * - Agent instantiation without operator factory
 *
 * Usage: node scripts/audit-agent-usage.mjs
 */

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const SCRIPT_PATH = 'scripts/audit-agent-usage.mjs';
const EVIDENCE_PATH = '.kernel/evidence/agent-usage-audit.json';
const COMMAND = 'pnpm check:agent-usage-audit';
const EXCEPTION_REGISTRY_PATH = 'products/data-cloud/planes/action/agent-runtime/docs/AGENT_USAGE_EXCEPTIONS.md';

const SCAN_DIRECTORIES = [
  'products/data-cloud/planes/action/server',
  'products/data-cloud/planes/action/orchestrator',
  'products/data-cloud/planes/action/operator-contracts',
  'products/data-cloud/planes/action/agent-runtime',
  'products/data-cloud/planes/action/engine',
  'products/data-cloud/planes/governance',
  'products/data-cloud/planes/operations',
];

const AGENT_USAGE_PATTERNS = [
  // Direct TypedAgent.execute() calls
  /TypedAgent.*\.execute\(/g,
  // Direct agent registry resolve without operator factory
  /AgentRegistry.*\.resolve\(/g,
  // GaaAgentExecutor - direct agent executor usage
  /GaaAgentExecutor/g,
  // Agent instantiation without operator wrapper
  /new TypedAgent</g,
];

const EXCLUDED_PATTERNS = [
  // Test files
  /\/test\//,
  /\/__tests__\//,
  // Node modules
  /\/node_modules\//,
  // Factory and adapter files (expected to use agents)
  /AgentCapabilityExecutionFactory/,
  /AgentEventOperatorCapabilityAdapter/,
  /AgentAdapter/,
  /GovernedAgentDispatcher/,
  // Operator implementations (expected to use agents)
  /AbstractAgentInferenceOperator/,
  /AgentPredicateOperator/,
  /AgentActionOperator/,
  // UI canvas commands (not agent-related)
  /canvas\//,
  /CanvasCommand/,
  // Reflex engine (expected to use handlers)
  /ReflexEngine/,
  // Pipeline execute (not agent-specific)
  /AgentDispatchPipeline/,
  /AgentDispatchStage/,
  // Circuit breaker (not agent-specific)
  /CircuitBreakerOperator/,
  // State store (not agent-specific)
  /HybridStateStore/,
  // Query plan (not agent-specific)
  /WindowedQueryPlan/,
  // Central registry service (catalog service, not agent execution)
  /AepCentralRegistryService/,
  // Marketplace service (catalog service, not agent execution)
  /AgentMarketplaceService/,
];

function loadApprovedExceptionPatterns(root) {
  const registryPath = path.join(root, EXCEPTION_REGISTRY_PATH);
  const patterns = [...EXCLUDED_PATTERNS];
  if (!existsSync(registryPath)) {
    throw new Error(`Approved agent usage exception registry is missing: ${EXCEPTION_REGISTRY_PATH}`);
  }
  const markdown = readFileSync(registryPath, 'utf8');
  let sawHeader = false;
  let exceptionCount = 0;
  for (const line of markdown.split(/\r?\n/)) {
    if (!line.trim().startsWith('|')) {
      continue;
    }
    const cells = line.trim().split('|').map((cell) => cell.trim()).filter(Boolean);
    if (cells[0] === 'Surface') {
      sawHeader = cells.length >= 5
        && cells[1] === 'Scope'
        && cells[2] === 'Owner'
        && cells[3] === 'Review/Rationale'
        && cells[4] === 'Revalidation';
      continue;
    }
    if (cells.length === 0 || /^-+$/.test(cells[0])) {
      continue;
    }
    if (cells.length < 5) {
      throw new Error(`${EXCEPTION_REGISTRY_PATH}: exception row must include Surface, Scope, Owner, Review/Rationale, and Revalidation`);
    }
    const surface = cells[0].replaceAll('`', '');
    const [scope, owner, rationale, revalidation] = cells.slice(1, 5);
    if (!surface || !scope || !owner || !rationale || !revalidation) {
      throw new Error(`${EXCEPTION_REGISTRY_PATH}: exception row has blank required metadata`);
    }
    exceptionCount += 1;
    if (surface && surface !== 'test fixtures') {
      patterns.push(new RegExp(surface.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    }
  }
  if (!sawHeader) {
    throw new Error(`${EXCEPTION_REGISTRY_PATH}: exception registry must declare Surface, Scope, Owner, Review/Rationale, and Revalidation columns`);
  }
  if (exceptionCount === 0) {
    throw new Error(`${EXCEPTION_REGISTRY_PATH}: exception registry must list at least one approved exception`);
  }
  return patterns;
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

function scanFile(root, filePath, findings, excludedPatterns) {
  const content = readFileSync(filePath, 'utf8');
  const relativePath = path.relative(root, filePath).replaceAll(path.sep, '/');

  // Skip excluded files
  for (const exclude of excludedPatterns) {
    if (exclude.test(relativePath)) {
      return;
    }
  }

  for (const pattern of AGENT_USAGE_PATTERNS) {
    const matches = content.matchAll(pattern);
    for (const match of matches) {
      findings.push({
        file: relativePath,
        pattern: pattern.source,
        line: getLineNumber(content, match.index),
        context: getContext(content, match.index),
      });
    }
  }
}

function getLineNumber(content, index) {
  const lines = content.substring(0, index).split('\n');
  return lines.length;
}

function getContext(content, index, contextLines = 2) {
  const lines = content.split('\n');
  const lineIndex = content.substring(0, index).split('\n').length - 1;
  const start = Math.max(0, lineIndex - contextLines);
  const end = Math.min(lines.length, lineIndex + contextLines + 1);
  return lines.slice(start, end).join('\n');
}

function scanDirectory(root, dir, findings, excludedPatterns) {
  const fullPath = path.join(root, dir);
  if (!existsSync(fullPath)) {
    return;
  }
  const stat = statSync(fullPath);

  // Skip node_modules at directory level
  if (dir.includes('node_modules')) {
    return;
  }

  if (stat.isDirectory()) {
    for (const entry of readdirSync(fullPath)) {
      scanDirectory(root, path.join(dir, entry), findings, excludedPatterns);
    }
  } else if (stat.isFile() && (fullPath.endsWith('.java') || fullPath.endsWith('.ts'))) {
    scanFile(root, fullPath, findings, excludedPatterns);
  }
}

export function findDirectAgentUsage(root = repoRoot) {
  const findings = [];
  const excludedPatterns = loadApprovedExceptionPatterns(root);
  for (const dir of SCAN_DIRECTORIES) {
    scanDirectory(root, dir, findings, excludedPatterns);
  }
  return findings;
}

export function createAgentUsageAuditEvidence(root = repoRoot, now = new Date()) {
  const findings = findDirectAgentUsage(root);
  const commit = currentGitSha(root);
  const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? commit;
  const excludedPatterns = loadApprovedExceptionPatterns(root);
  return {
    generatedAt: now.toISOString(),
    pass: findings.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit,
      sourceCommitSha: commit,
      targetCommitSha,
      targetEnvironment: process.env.RELEASE_ENVIRONMENT ?? 'staging',
    },
    sourceCommitSha: commit,
    targetCommitSha,
    targetEnvironment: process.env.RELEASE_ENVIRONMENT ?? 'staging',
    summary: {
      scannedDirectories: SCAN_DIRECTORIES.length,
      findingCount: findings.length,
      approvedExceptionRegistry: EXCEPTION_REGISTRY_PATH,
      approvedExceptionPatterns: excludedPatterns.map((pattern) => pattern.source),
    },
    violations: findings,
  };
}

export function writeAgentUsageAuditEvidence(root = repoRoot, evidence = createAgentUsageAuditEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : repoRoot;
  const evidence = createAgentUsageAuditEvidence(root);
  writeAgentUsageAuditEvidence(root, evidence);

  if (evidence.pass) {
    console.log(`Agent usage audit evidence written to ${EVIDENCE_PATH}.`);
    return;
  }

  console.error(`Found ${evidence.violations.length} instances of direct agent usage:\n`);

  // Group by file
  const byFile = {};
  for (const finding of evidence.violations) {
    if (!byFile[finding.file]) {
      byFile[finding.file] = [];
    }
    byFile[finding.file].push(finding);
  }

  for (const [file, fileFindings] of Object.entries(byFile)) {
    console.error(file);
    for (const finding of fileFindings) {
      console.error(`   Line ${finding.line}: pattern "${finding.pattern}"`);
      console.error(`   Context:\n${finding.context.split('\n').map(l => '     ' + l).join('\n')}\n`);
    }
  }

  console.error('\nMigration recommendations:');
  console.error('1. Replace direct agent.execute() with AgentCapability execution.');
  console.error('2. Use AgentCapabilityExecutionFactory.createCapabilityExecutionTree() instead of registry.resolve().');
  console.error('3. Wrap agent instantiation in AgentEventOperatorCapabilityAdapter when event processing is needed.');
  console.error('4. Update imports to use EventOperatorCapability from operator-contracts.');

  process.exit(1);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  try {
    main();
  } catch (error) {
    console.error('Audit failed:', error);
    process.exit(1);
  }
}
