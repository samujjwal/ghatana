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

import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

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
  /AgentAdapter/,
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

const findings = [];

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const relativePath = path.relative(repoRoot, filePath);

  // Skip excluded files
  for (const exclude of EXCLUDED_PATTERNS) {
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

function scanDirectory(dir) {
  const fullPath = path.join(repoRoot, dir);
  const stat = statSync(fullPath);

  // Skip node_modules at directory level
  if (dir.includes('node_modules')) {
    return;
  }

  if (stat.isDirectory()) {
    for (const entry of readdirSync(fullPath)) {
      scanDirectory(path.join(dir, entry));
    }
  } else if (stat.isFile() && (fullPath.endsWith('.java') || fullPath.endsWith('.ts'))) {
    scanFile(fullPath);
  }
}

function main() {
  console.log('Phase 6: Auditing agent usage for Operator runtime migration...\n');

  for (const dir of SCAN_DIRECTORIES) {
    try {
      scanDirectory(dir);
    } catch (error) {
      console.error(`Error scanning ${dir}:`, error.message);
    }
  }

  if (findings.length === 0) {
    console.log('✅ No direct agent usage found - migration complete or not needed.');
    process.exit(0);
  }

  console.log(`Found ${findings.length} instances of direct agent usage:\n`);

  // Group by file
  const byFile = {};
  for (const finding of findings) {
    if (!byFile[finding.file]) {
      byFile[finding.file] = [];
    }
    byFile[finding.file].push(finding);
  }

  for (const [file, fileFindings] of Object.entries(byFile)) {
    console.log(`📄 ${file}`);
    for (const finding of fileFindings) {
      console.log(`   Line ${finding.line}: pattern "${finding.pattern}"`);
      console.log(`   Context:\n${finding.context.split('\n').map(l => '     ' + l).join('\n')}\n`);
    }
  }

  console.log('\nMigration recommendations:');
  console.log('1. Replace direct agent.execute() with AgentCapability execution.');
  console.log('2. Use AgentCapabilityExecutionFactory.createCapabilityExecutionTree() instead of registry.resolve().');
  console.log('3. Wrap agent instantiation in AgentEventOperatorCapabilityAdapter when event processing is needed.');
  console.log('4. Update imports to use EventOperatorCapability from operator-contracts.');

  process.exit(1);
}

try {
  main();
} catch (error) {
  console.error('Audit failed:', error);
  process.exit(1);
}
