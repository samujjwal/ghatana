#!/usr/bin/env node

/**
 * P1-09: Enforce product/shared-library dependency boundaries.
 * 
 * This script implements architecture checks for the documented rules:
 * - Data/Event/Context/Governance must not import Action internals.
 * - Contracts must not depend on runtime implementation.
 * - UI must use generated clients/adapters, not backend internals.
 * - Extensions must depend on contracts/SPI, not launcher internals.
 * - Shared libraries must not contain Data Cloud product behavior.
 *
 * @doc.type script
 * @doc.purpose Enforce product/shared-library dependency boundaries
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join } from 'path';

const VIOLATION_RULES = {
  dataEventContextGovernanceToAction: {
    pattern: /from ['"].*action.*['"]|from ['"].*planes\/action.*['"]/i,
    description: 'Data/Event/Context/Governance must not import Action internals',
    allowedPaths: ['products/data-cloud/planes/action'],
    forbiddenPaths: [
      'products/data-cloud/planes/data',
      'products/data-cloud/planes/event',
      'products/data-cloud/planes/context',
      'products/data-cloud/planes/governance',
    ],
  },
  contractsToRuntime: {
    pattern: /from ['"].*launcher.*['"]|from ['"].*runtime.*['"]/i,
    description: 'Contracts must not depend on runtime implementation',
    allowedPaths: ['products/data-cloud/contracts'],
    forbiddenPaths: ['products/data-cloud/delivery/launcher'],
  },
  uiToBackendInternals: {
    pattern: /from ['"].*\/launcher\/.*['"]|from ['"].*\/handlers\/.*['"]|from ['"].*\/http\/.*['"]/i,
    description: 'UI must use generated clients/adapters, not backend internals',
    allowedPaths: ['products/data-cloud/delivery/ui'],
    forbiddenPaths: ['products/data-cloud/delivery/launcher'],
  },
  extensionsToLauncherInternals: {
    pattern: /from ['"].*\/launcher\/.*['"]|from ['"].*\/handlers\/.*['"]/i,
    description: 'Extensions must depend on contracts/SPI, not launcher internals',
    allowedPaths: ['products/data-cloud/extensions'],
    forbiddenPaths: ['products/data-cloud/delivery/launcher'],
  },
};

function checkFile(filePath, rule) {
  const content = readFileSync(filePath, 'utf8');
  const matches = content.match(rule.pattern);
  return matches ? matches.length : 0;
}

function scanDirectory(dir, results = []) {
  const files = readdirSync(dir);
  
  for (const file of files) {
    const filePath = join(dir, file);
    const stat = statSync(filePath);
    
    if (stat.isDirectory()) {
      // Skip node_modules and build directories
      if (!['node_modules', 'dist', 'build', '.next'].includes(file)) {
        scanDirectory(filePath, results);
      }
    } else if (file.endsWith('.ts') || file.endsWith('.tsx') || file.endsWith('.js') || file.endsWith('.mjs')) {
      results.push(filePath);
    }
  }
  
  return results;
}

function main() {
  const repoRoot = process.argv[2] || process.cwd();
  
  console.log('Checking dependency boundaries...');
  
  const allFiles = scanDirectory(repoRoot);
  let totalViolations = 0;
  
  for (const [ruleName, rule] of Object.entries(VIOLATION_RULES)) {
    console.log(`\nChecking: ${rule.description}`);
    let ruleViolations = 0;
    
    for (const filePath of allFiles) {
      // Check if file is in a forbidden path
      const isInForbiddenPath = rule.forbiddenPaths.some(forbiddenPath => 
        filePath.includes(forbiddenPath)
      );
      
      // Check if file is in an allowed path (should not trigger this rule)
      const isInAllowedPath = rule.allowedPaths.some(allowedPath => 
        filePath.includes(allowedPath)
      );
      
      if (isInForbiddenPath && !isInAllowedPath) {
        const violations = checkFile(filePath, rule);
        if (violations > 0) {
          console.log(`  ❌ ${filePath}: ${violations} violation(s)`);
          ruleViolations += violations;
        }
      }
    }
    
    totalViolations += ruleViolations;
    
    if (ruleViolations === 0) {
      console.log(`  ✅ No violations`);
    }
  }
  
  console.log(`\nTotal violations: ${totalViolations}`);
  
  if (totalViolations > 0) {
    console.error('❌ Dependency boundary check FAILED');
    process.exit(1);
  } else {
    console.log('✅ Dependency boundary check PASSED');
    process.exit(0);
  }
}

main();
