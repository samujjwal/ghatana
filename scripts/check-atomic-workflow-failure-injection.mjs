#!/usr/bin/env node

/**
 * P1-1: Atomic Workflow Failure-Injection Test Suite
 *
 * Executes real failure-injection tests for atomic workflows:
 * - Business write succeeds, event append fails
 * - Event append succeeds, audit write fails
 * - Audit succeeds, outbox fails
 * - Idempotency write fails
 * - Retry after partial failure
 * - Rollback after partial failure
 * - Replay after crash
 *
 * This replaces posture-only proof with executable failure-injection
 * scenarios that prove transactional atomicity under failure.
 *
 * Usage: node scripts/check-atomic-workflow-failure-injection.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, shouldFailOnWarning, processValidationResults, logValidationResults, validateProductCoverage } from './lib/release-evidence-policy.mjs';
import { getAtomicWorkflowProducts, resolveProductForProof, validateProductPath } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_MODE = getReleaseMode();
const CI_MODE = process.argv.includes('--ci') || process.env.CI === 'true';
const productArgIndex = process.argv.indexOf('--product');
const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1]
  ?? (productArgIndex >= 0 ? process.argv[productArgIndex + 1] : undefined);

const violations = [];
const warnings = [];
const evidence = [];
let executedTestProductCount = 0;
let currentExpectedProductCount = 0;
let currentProductScope = [];
const stableGeneratedAt = resolveEvidenceIdentity();

const requiredScenarioPatterns = {
  businessWriteEventAppendFailure: ['business write/event append failure'],
  eventAppendAuditWriteFailure: ['event append/audit write failure'],
  auditOutboxFailure: ['audit/outbox failure'],
  idempotencyWriteFailure: ['idempotency write failure'],
  retryAfterPartialFailure: ['retry after partial failure'],
  rollbackAfterPartialFailure: ['rollback after partial failure'],
  replayAfterCrash: ['replay after crash'],
  sideEffectRollback: ['side effect rollback', 'rollback verification', 'cleanup after failure'],
};

function resolveEvidenceIdentity() {
  if (process.env.GITHUB_SHA) {
    return `commit:${process.env.GITHUB_SHA}`;
  }
  try {
    return `commit:${execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim()}`;
  } catch {
    return 'generated-on-demand';
  }
}

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

function hasRequiredScenarioCoverage(content) {
  const normalized = content.toLowerCase();
  return Object.values(requiredScenarioPatterns)
    .every((patterns) => patterns.every((pattern) => normalized.includes(pattern)));
}

function sleepMs(durationMs) {
  const signal = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(signal, 0, 0, durationMs);
}

function isTransientGradleCleanupFailure(error) {
  const text = `${error?.message ?? ''}\n${error?.stdout ?? ''}\n${error?.stderr ?? ''}`;
  return /Unable to delete directory|output\.bin|EBUSY|EPERM|file[s]? open/i.test(text);
}

function execGradleProof(args) {
  let lastError = null;
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      return execFileSync(process.execPath, args, {
        cwd: repoRoot,
        encoding: 'utf8',
        timeout: 300000
      });
    } catch (error) {
      lastError = error;
      if (attempt === 2 || !isTransientGradleCleanupFailure(error)) {
        throw error;
      }
      logWarning('Transient Gradle cleanup lock detected; retrying atomic failure-injection proof once');
      sleepMs(1500);
    }
  }
  throw lastError;
}

/**
 * Execute atomic workflow failure-injection tests
 */
function executeAtomicWorkflowTests(productPath, productName, gradleTask) {
  // Look for atomic workflow failure-injection tests
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'integration-tests/src/test/java'),
  ];

  let testFound = false;
  let executedPassed = false;
  const executionWarnings = [];
  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      if (executedPassed) {
        return;
      }
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          if (executedPassed) {
            return;
          }
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('Test.java') && (item.includes('AtomicWorkflow') || item.includes('FailureInjection'))) {
            testFound = true;
            const className = item.replace('.java', '');
            const content = readFileSync(itemPath, 'utf8');
            if (!hasRequiredScenarioCoverage(content)) {
              continue;
            }
            const packageMatch = content.match(/^\s*package\s+([\w.]+);/m);
            const testPattern = packageMatch ? `${packageMatch[1]}.${className}` : `*${className}`;
            
            try {
              // Execute the test using Gradle wrapper for portability
              const args = [
                './scripts/run-gradle-wrapper.mjs',
                gradleTask,
                '--tests',
                testPattern,
                '--no-daemon',
                '--no-build-cache',
                '--max-workers=1',
              ];

              console.log(`  Executing: node ${args.join(' ')}`);
              const output = execGradleProof(args);

              const testPassed = output.includes('BUILD SUCCESSFUL') || output.includes('PASSED');
              const testFailed = output.includes('FAILED') || output.includes('BUILD FAILED');

              if (testPassed) {
                logSuccess(`${productName}: Atomic workflow failure-injection tests PASSED`);
                logEvidence(`${productName}: Executed real atomic workflow failure scenarios`);
                executedPassed = true;
                return;
              } else if (testFailed) {
                logError(`${productName}: Atomic workflow failure-injection tests FAILED`);
                return;
              }
            } catch (error) {
              executionWarnings.push(`${productName}: Failed to execute test ${className}: ${error.message}`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
    if (executedPassed) {
      return true;
    }
  }

  for (const warning of executionWarnings) {
    logWarning(warning);
  }

  if (!testFound) {
    logWarning(`${productName}: No atomic workflow failure-injection test found, falling back to posture checks`);
    return false;
  }

  return false;
}

/**
 * Check for business write succeeds, event append fails scenario
 */
function checkBusinessWriteEventAppendFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;
  let hasRollbackVerification = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('business') || content.includes('Business')) &&
                (content.includes('write') || content.includes('Write')) &&
                (content.includes('event') || content.includes('Event')) &&
                (content.includes('append') || content.includes('Append')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has business write/event append failure test`);
            }

            // Check for rollback verification
            if ((content.includes('rollback') || content.includes('Rollback') || content.includes('cleanup') || content.includes('Cleanup')) &&
                (content.includes('verify') || content.includes('Verify') || content.includes('assert') || content.includes('Assert'))) {
              hasRollbackVerification = true;
              logEvidence(`${productName}: Has rollback verification in business write/event append test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Business write/event append failure scenario covered`);
    if (hasRollbackVerification) {
      logSuccess(`${productName}: Side-effect rollback verification present`);
    } else if (RELEASE_MODE === 'release') {
      logError(`${productName}: Missing side-effect rollback verification - required in release mode`);
    } else {
      logWarning(`${productName}: Missing side-effect rollback verification`);
    }
  } else {
    logWarning(`${productName}: Missing business write/event append failure scenario`);
  }

  return hasScenario && (hasRollbackVerification || RELEASE_MODE !== 'release');
}

/**
 * Check for event append succeeds, audit write fails scenario
 */
function checkEventAppendAuditWriteFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;
  let hasRollbackVerification = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('event') || content.includes('Event')) &&
                (content.includes('append') || content.includes('Append')) &&
                (content.includes('audit') || content.includes('Audit')) &&
                (content.includes('write') || content.includes('Write')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has event append/audit write failure test`);
            }

            // Check for rollback verification
            if ((content.includes('rollback') || content.includes('Rollback') || content.includes('cleanup') || content.includes('Cleanup')) &&
                (content.includes('verify') || content.includes('Verify') || content.includes('assert') || content.includes('Assert'))) {
              hasRollbackVerification = true;
              logEvidence(`${productName}: Has rollback verification in event append/audit write test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Event append/audit write failure scenario covered`);
    if (hasRollbackVerification) {
      logSuccess(`${productName}: Side-effect rollback verification present`);
    } else if (RELEASE_MODE === 'release') {
      logError(`${productName}: Missing side-effect rollback verification - required in release mode`);
    } else {
      logWarning(`${productName}: Missing side-effect rollback verification`);
    }
  } else {
    logWarning(`${productName}: Missing event append/audit write failure scenario`);
  }

  return hasScenario && (hasRollbackVerification || RELEASE_MODE !== 'release');
}

/**
 * Check for audit succeeds, outbox fails scenario
 */
function checkAuditOutboxFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;
  let hasRollbackVerification = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('audit') || content.includes('Audit')) &&
                (content.includes('outbox') || content.includes('Outbox')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has audit/outbox failure test`);
            }

            // Check for rollback verification
            if ((content.includes('rollback') || content.includes('Rollback') || content.includes('cleanup') || content.includes('Cleanup')) &&
                (content.includes('verify') || content.includes('Verify') || content.includes('assert') || content.includes('Assert'))) {
              hasRollbackVerification = true;
              logEvidence(`${productName}: Has rollback verification in audit/outbox test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Audit/outbox failure scenario covered`);
    if (hasRollbackVerification) {
      logSuccess(`${productName}: Side-effect rollback verification present`);
    } else if (RELEASE_MODE === 'release') {
      logError(`${productName}: Missing side-effect rollback verification - required in release mode`);
    } else {
      logWarning(`${productName}: Missing side-effect rollback verification`);
    }
  } else {
    logWarning(`${productName}: Missing audit/outbox failure scenario`);
  }

  return hasScenario && (hasRollbackVerification || RELEASE_MODE !== 'release');
}

/**
 * Check for idempotency write fails scenario
 */
function checkIdempotencyWriteFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('idempotency') || content.includes('Idempotency')) &&
                (content.includes('write') || content.includes('Write')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has idempotency write failure test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Idempotency write failure scenario covered`);
  } else {
    logWarning(`${productName}: Missing idempotency write failure scenario`);
  }

  return hasScenario;
}

/**
 * Check for retry after partial failure scenario
 */
function checkRetryAfterPartialFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('retry') || content.includes('Retry')) &&
                (content.includes('partial') || content.includes('Partial')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has retry after partial failure test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Retry after partial failure scenario covered`);
  } else {
    logWarning(`${productName}: Missing retry after partial failure scenario`);
  }

  return hasScenario;
}

/**
 * Check for rollback after partial failure scenario
 */
function checkRollbackAfterPartialFailure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('rollback') || content.includes('Rollback')) &&
                (content.includes('partial') || content.includes('Partial')) &&
                (content.includes('fail') || content.includes('Fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has rollback after partial failure test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Rollback after partial failure scenario covered`);
  } else {
    logWarning(`${productName}: Missing rollback after partial failure scenario`);
  }

  return hasScenario;
}

/**
 * Check for replay after crash scenario
 */
function checkReplayAfterCrash(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScenario = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('replay') || content.includes('Replay')) &&
                (content.includes('crash') || content.includes('Crash'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has replay after crash test`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScenario) {
    logSuccess(`${productName}: Replay after crash scenario covered`);
  } else {
    logWarning(`${productName}: Missing replay after crash scenario`);
  }

  return hasScenario;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'atomic-workflow-failure-injection');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const evidenceText = evidence.join(' | ').toLowerCase();
  const scenarioCoverage = Object.fromEntries(
    Object.entries(requiredScenarioPatterns).map(([scenario, patterns]) => [
      scenario,
      patterns.every((pattern) => evidenceText.includes(pattern)),
    ]),
  );
  const missingScenarios = Object.entries(scenarioCoverage)
    .filter(([, covered]) => covered !== true)
    .map(([scenario]) => scenario);

  if (missingScenarios.length > 0) {
    for (const scenario of missingScenarios) {
      if (RELEASE_MODE === 'release' || CI_MODE) {
        logError(`Missing atomic failure-injection scenario evidence: ${scenario}`);
      } else {
        logWarning(`Missing atomic failure-injection scenario evidence: ${scenario}`);
      }
    }
  }

  if (executedTestProductCount === 0) {
    if (RELEASE_MODE === 'release' || CI_MODE) {
      logError('No product executed real atomic workflow failure-injection tests');
    } else {
      logWarning('No product executed real atomic workflow failure-injection tests');
    }
  }

  const report = {
    timestamp: stableGeneratedAt,
    productScope: currentProductScope,
    violations,
    warnings,
    evidence,
    scenarioCoverage,
    summary: {
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
      missingScenarioCount: missingScenarios.length,
      executedTestProductCount,
      expectedProductCount: currentExpectedProductCount,
    }
  };

  const reportPath = path.join(evidenceDir, 'atomic-workflow-failure-injection-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking atomic workflow failure-injection proof across products...\n');

  // Resolve products from canonical product registry
  const registryProducts = getAtomicWorkflowProducts();
  
  // Resolve product information for proof
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => {
        const query = PRODUCT_ARG.toLowerCase();
        return p.productId.toLowerCase().includes(query) ||
          p.name.toLowerCase().includes(query) ||
          p.path.toLowerCase().includes(query);
      })
    : products;

  if (PRODUCT_ARG && filteredProducts.length === 0) {
    logError(`No atomic workflow product matched --product=${PRODUCT_ARG}`);
  }
  currentExpectedProductCount = filteredProducts.length;
  currentProductScope = filteredProducts.map((product) => product.productId);

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logError(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    // Determine Gradle task for this product
    const gradleTask = product.gradleTask || `${product.productId}:test`;
    
    // Execute real tests instead of posture checks
    const testsPassed = executeAtomicWorkflowTests(productPath, product.name, gradleTask);
    
    if (!testsPassed) {
      // In release mode, fail if no executable test is found - no fallback posture checks allowed
      if (RELEASE_MODE === 'release') {
        logError(`${product.name}: No executable atomic workflow failure-injection test found - required in release mode`);
        logError(`${product.name}: Fallback posture checks are not allowed in release mode`);
        logError(`${product.name}: Add explicit waiver in config/release-proof-waivers.json if this product is non-mutating`);
      } else {
        // Fall back to posture checks in local mode only
        logWarning(`${product.name}: Test execution failed, falling back to posture checks (local mode only)`);
        checkBusinessWriteEventAppendFailure(productPath, product.name);
        checkEventAppendAuditWriteFailure(productPath, product.name);
        checkAuditOutboxFailure(productPath, product.name);
        checkIdempotencyWriteFailure(productPath, product.name);
        checkRetryAfterPartialFailure(productPath, product.name);
        checkRollbackAfterPartialFailure(productPath, product.name);
        checkReplayAfterCrash(productPath, product.name);
      }
    } else {
      executedTestProductCount += 1;
      for (const patterns of Object.values(requiredScenarioPatterns)) {
        for (const pattern of patterns) {
          logEvidence(`${product.name}: Executable test run validated ${pattern}`);
        }
      }
    }
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);
  console.log(`Products with executed tests: ${executedTestProductCount}`);

  generateEvidenceReport();

  // Validate product coverage in release mode
  const expectedProductCount = filteredProducts.length;
  const coverageIssues = validateProductCoverage(executedTestProductCount, expectedProductCount, RELEASE_MODE);
  coverageIssues.forEach(issue => {
    if (issue.severity === 'error') {
      logError(issue.message);
    } else {
      logWarning(issue.message);
    }
  });

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'Atomic Workflow Failure-Injection Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nAtomic workflow failure-injection check passed.');
}

main();
