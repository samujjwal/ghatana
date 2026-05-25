#!/usr/bin/env node

/**
 * DC-P9-002: Add execution-path audit for direct agent bypass
 *
 * This script detects:
 * - direct TypedAgent.process outside capability adapter
 * - direct AgentDispatcher.dispatch from PatternSpec/runtime paths
 * - model calls outside approved agent runtime
 * - tool calls outside capability/tool policy
 * - direct production action calls without AgentAction/capability policy
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_PATH = 'scripts/check-agent-runtime-bypass.mjs';
const ROOT = path.dirname(fileURLToPath(import.meta.url));
const ALLOWLIST_FILE = path.join(ROOT, 'config/agent-bypass-allowlist.json');

// Allowlist of approved direct agent calls (should be tiny and documented)
const ALLOWLIST = {
  // Known safe direct calls that are part of the capability adapter infrastructure
  capabilityAdapters: [
    'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java',
  ],
  // Test files are exempt
  testFiles: [
    '**/test/**/*.java',
    '**/__tests__/**/*.java',
  ],
};

/**
 * Patterns to detect bypass violations
 */
const VIOLATION_PATTERNS = [
  {
    name: 'direct_typed_agent_process',
    pattern: /TypedAgent\.process\s*\(/g,
    description: 'Direct TypedAgent.process call outside capability adapter',
    severity: 'error',
  },
  {
    name: 'direct_agent_dispatcher_dispatch',
    pattern: /AgentDispatcher\.dispatch\s*\(/g,
    description: 'Direct AgentDispatcher.dispatch call',
    severity: 'warning',
  },
  {
    name: 'direct_model_call',
    pattern: /\.callModel\s*\(/g,
    description: 'Direct model call outside approved agent runtime',
    severity: 'error',
  },
  {
    name: 'direct_tool_call',
    pattern: /\.executeTool\s*\(/g,
    description: 'Direct tool call outside capability/tool policy',
    severity: 'error',
  },
  {
    name: 'direct_action_call',
    pattern: /\.executeAction\s*\(/g,
    description: 'Direct production action call without AgentAction/capability policy',
    severity: 'error',
  },
];

/**
 * Check if a file is in the allowlist
 */
function isAllowed(filePath) {
  // Check if it's a test file
  if (filePath.includes('/test/') || filePath.includes('/__tests__/')) {
    return true;
  }

  // Check if it's in the capability adapters allowlist
  for (const allowedPath of ALLOWLIST.capabilityAdapters) {
    if (filePath.endsWith(allowedPath) || filePath.includes(allowedPath)) {
      return true;
    }
  }

  return false;
}

/**
 * Scan a Java file for violations
 */
function scanFile(filePath, content) {
  if (isAllowed(filePath)) {
    return [];
  }

  const violations = [];

  for (const pattern of VIOLATION_PATTERNS) {
    const matches = content.matchAll(pattern.pattern);
    for (const match of matches) {
      violations.push({
        file: filePath,
        pattern: pattern.name,
        description: pattern.description,
        severity: pattern.severity,
        line: getLineNumber(content, match.index),
      });
    }
  }

  return violations;
}

/**
 * Get line number from content index
 */
function getLineNumber(content, index) {
  const lines = content.substring(0, index).split('\n');
  return lines.length;
}

/**
 * Find all Java files in the codebase
 */
function findJavaFiles(dir, files = []) {
  const entries = existsSync(dir) ? require('node:fs').readdirSync(dir, { withFileTypes: true }) : [];
  
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip build directories and node_modules
      if (!['build', 'node_modules', '.gradle', 'target'].includes(entry.name)) {
        findJavaFiles(fullPath, files);
      }
    } else if (entry.isFile() && entry.name.endsWith('.java')) {
      files.push(fullPath);
    }
  }
  
  return files;
}

/**
 * Main function
 */
function main() {
  const productsDir = path.join(ROOT, 'products');
  const javaFiles = findJavaFiles(productsDir);
  
  const allViolations = [];
  
  for (const filePath of javaFiles) {
    try {
      const content = readFileSync(filePath, 'utf8');
      const violations = scanFile(filePath, content);
      allViolations.push(...violations);
    } catch (error) {
      console.error(`Error reading file ${filePath}: ${error.message}`);
    }
  }
  
  // Group violations by severity
  const errors = allViolations.filter(v => v.severity === 'error');
  const warnings = allViolations.filter(v => v.severity === 'warning');
  
  if (errors.length > 0) {
    console.error(`\n❌ Found ${errors.length} agent runtime bypass violations:\n`);
    for (const violation of errors) {
      console.error(`  ${violation.file}:${violation.line}`);
      console.error(`    ${violation.description} (${violation.pattern})\n`);
    }
    process.exit(1);
  }
  
  if (warnings.length > 0) {
    console.warn(`\n⚠️  Found ${warnings.length} warnings:\n`);
    for (const violation of warnings) {
      console.warn(`  ${violation.file}:${violation.line}`);
      console.warn(`    ${violation.description} (${violation.pattern})\n`);
    }
  }
  
  console.log(`✅ Agent runtime bypass check passed. Scanned ${javaFiles.length} Java files.`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}

export { scanFile, isAllowed, VIOLATION_PATTERNS };
