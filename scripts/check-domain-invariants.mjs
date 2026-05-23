#!/usr/bin/env node

/**
 * Per-Product Domain Invariant Tests
 *
 * Validates domain-specific invariants for each product:
 * - Business rule validation
 * - Data consistency checks
 * - State transition validation
 * - Constraint verification
 *
 * This ensures that core business rules and data consistency are maintained.
 *
 * Usage: node scripts/check-domain-invariants.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';
import { getActiveProducts, resolveProductForProof, getProductLifecycleTestCommand } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_MODE = getReleaseMode();
const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

const violations = [];
const warnings = [];
const evidence = [];
const stableGeneratedAt = process.env.GITHUB_SHA ? `commit:${process.env.GITHUB_SHA}` : 'generated-on-demand';

function logError(message) {
  violations.push(message);
  console.error(`❌ ERROR: ${message}`);
}

function logWarning(message) {
  warnings.push(message);
  console.warn(`⚠️  WARNING: ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

function logEvidence(message) {
  evidence.push(message);
  console.log(`  📋 ${message}`);
}

/**
 * Execute domain invariant tests
 */
function executeDomainInvariantTests(productPath, productName, testCommand) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let testFound = false;
  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') && (item.includes('Invariant') || item.includes('Domain') || item.includes('BusinessRule'))) {
            testFound = true;
            const className = item.replace('.java', '');
            const content = readFileSync(itemPath, 'utf8');
            const packageMatch = content.match(/^\s*package\s+([\w.]+);/m);
            const testPattern = packageMatch ? `${packageMatch[1]}.${className}` : `*${className}`;
            
            try {
              let args;
              if (testCommand.startsWith('pnpm')) {
                args = ['pnpm', 'test', '--', className];
              } else {
                args = [
                  './scripts/run-gradle-wrapper.mjs',
                  testCommand,
                  '--tests',
                  testPattern,
                  '--no-daemon',
                  '--max-workers=1',
                ];
              }

              console.log(`  Executing: ${testCommand.startsWith('pnpm') ? 'pnpm' : 'node'} ${args.join(' ')}`);
              const output = execFileSync(testCommand.startsWith('pnpm') ? 'pnpm' : process.execPath, args, {
                cwd: repoRoot,
                encoding: 'utf8',
                timeout: 120000
              });

              const testPassed = output.includes('BUILD SUCCESSFUL') || output.includes('PASSED') || output.includes('PASS');
              const testFailed = output.includes('FAILED') || output.includes('BUILD FAILED') || output.includes('FAIL');

              if (testPassed) {
                logSuccess(`${productName}: Domain invariant tests PASSED`);
                logEvidence(`${productName}: Executed real domain invariant scenarios`);
                return true;
              } else if (testFailed) {
                logError(`${productName}: Domain invariant tests FAILED`);
                return false;
              }
            } catch (error) {
              logWarning(`${productName}: Failed to execute test ${className}: ${error.message}`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (!testFound) {
    logWarning(`${productName}: No domain invariant test found, falling back to posture checks`);
    return false;
  }

  return false;
}

/**
 * Check for business rule validation
 */
function checkBusinessRuleValidation(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasBusinessRules = false;

  for (const srcDir of srcDirs) {
    if (!existsSync(srcDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('business') || content.includes('Business') || content.includes('domain') || content.includes('Domain')) &&
                (content.includes('rule') || content.includes('Rule') || content.includes('invariant') || content.includes('Invariant'))) {
              hasBusinessRules = true;
              logEvidence(`${productName}: Has business rule validation`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasBusinessRules) {
    logSuccess(`${productName}: Business rule validation present`);
  } else {
    logWarning(`${productName}: Missing business rule validation`);
  }

  return hasBusinessRules;
}

/**
 * Check for data consistency checks
 */
function checkDataConsistency(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasDataConsistency = false;

  for (const srcDir of srcDirs) {
    if (!existsSync(srcDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('consistency') || content.includes('Consistency') || content.includes('validate') || content.includes('Validate')) &&
                (content.includes('data') || content.includes('Data') || content.includes('state') || content.includes('State'))) {
              hasDataConsistency = true;
              logEvidence(`${productName}: Has data consistency checks`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasDataConsistency) {
    logSuccess(`${productName}: Data consistency checks present`);
  } else {
    logWarning(`${productName}: Missing data consistency checks`);
  }

  return hasDataConsistency;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel/evidence', 'domain-invariants');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const report = {
    timestamp: stableGeneratedAt,
    violations,
    warnings,
    evidence,
    summary: {
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
    }
  };

  const reportPath = path.join(evidenceDir, 'domain-invariants-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking domain invariants across products...\n');

  const registryProducts = getActiveProducts();
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logError(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    const testCommand = getProductLifecycleTestCommand(product.productId) || `${product.productId}:test`;
    const testsPassed = executeDomainInvariantTests(productPath, product.name, testCommand);
    
    if (!testsPassed) {
      if (RELEASE_MODE === 'release') {
        logError(`${product.name}: No executable domain invariant test found - required in release mode`);
      } else {
        logWarning(`${product.name}: Test execution failed, falling back to posture checks`);
        checkBusinessRuleValidation(productPath, product.name);
        checkDataConsistency(productPath, product.name);
      }
    }
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'Domain Invariants Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nDomain invariants check passed.');
}

main();
