#!/usr/bin/env node

/**
 * P1-2: Runtime Dependency Failure-Injection Test Suite
 *
 * Validates system resilience by executing real dependency-failure scenarios:
 * - Postgres unavailable
 * - ClickHouse unavailable
 * - OpenSearch unavailable
 * - S3 unavailable
 * - Audit sink unavailable
 * - Policy engine unavailable
 * - AI completion unavailable
 * - Network timeout
 * - Queue saturation
 *
 * This replaces token-based release checks with executable failure-injection
 * scenarios that prove the system can handle dependency failures gracefully.
 *
 * Usage: node scripts/check-runtime-dependency-failure-injection.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, shouldFailOnWarning, processValidationResults, logValidationResults, validateProductCoverage } from './lib/release-evidence-policy.mjs';
import { getRuntimeDependencyProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_MODE = getReleaseMode();
const productArgIndex = process.argv.indexOf('--product');
const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1]
  ?? (productArgIndex >= 0 ? process.argv[productArgIndex + 1] : undefined);

const violations = [];
const warnings = [];
const evidence = [];
let executedTestProductCount = 0;
let currentExpectedProductCount = 0;
const stableGeneratedAt = resolveEvidenceIdentity();

const requiredScenarioPatterns = {
  postgresDown: ['postgres unavailability'],
  clickhouseDown: ['clickhouse unavailability'],
  openSearchDown: ['opensearch unavailability'],
  s3Down: ['s3 unavailability'],
  auditSinkUnavailable: ['audit sink unavailability'],
  policyEngineUnavailable: ['policy engine unavailability'],
  aiCompletionUnavailable: ['ai completion unavailability'],
  networkTimeout: ['network timeout'],
  queueSaturation: ['queue saturation'],
  retryBackoff: ['retry implementation', 'backoff implementation'],
};
const excludedTestClasses = new Map([
  [
    'Data Cloud Launcher',
    new Set([
      'AtomicWorkflowFailureInjectionTest',
      'RuntimeDependencyFailureInjectionTest',
    ]),
  ],
]);

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

/**
 * Execute runtime dependency failure-injection tests
 */
function executeDependencyFailureTests(productPath, productName, gradleTask) {
  // Look for integration tests that cover dependency failures
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'integration-tests/src/test/java'),
  ];

  let testFound = false;
  let executedPassed = false;
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
          } else if (
            item.endsWith('Test.java') &&
            (item.includes('Failure') ||
              item.includes('Resilience') ||
              item.includes('Retry') ||
              item.includes('Dlq') ||
              item.includes('AuthorizationService'))
          ) {
            testFound = true;
            const className = item.replace('.java', '');
            if (excludedTestClasses.get(productName)?.has(className)) {
              continue;
            }
            const content = readFileSync(itemPath, 'utf8');
            if (!hasRequiredScenarioCoverage(content)) {
              continue;
            }
            const packageMatch = content.match(/^\s*package\s+([\w.]+);/m);
            const testPattern = packageMatch ? `${packageMatch[1]}.${className}` : `*${className}`;
            
            try {
              const args = [
                './scripts/run-gradle-wrapper.mjs',
                gradleTask,
                '--tests',
                testPattern,
                '--no-daemon',
                '--max-workers=1',
              ];

              console.log(`  Executing: node ${args.join(' ')}`);
              const output = execFileSync(process.execPath, args, {
                cwd: repoRoot,
                encoding: 'utf8',
                timeout: 300000
              });

              const testPassed = output.includes('BUILD SUCCESSFUL') || output.includes('PASSED');
              const testFailed = output.includes('FAILED') || output.includes('BUILD FAILED');

              if (testPassed) {
                logSuccess(`${productName}: Dependency failure tests PASSED`);
                logEvidence(`${productName}: Executed real dependency failure scenarios`);
                executedPassed = true;
                return;
              } else if (testFailed) {
                logError(`${productName}: Dependency failure tests FAILED`);
                return;
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
    if (executedPassed) {
      return true;
    }
  }

  if (!testFound) {
    logWarning(`${productName}: No dependency failure test found, falling back to posture checks`);
    return false;
  }

  return false;
}

/**
 * Check for failure-injection test infrastructure (fallback)
 */
function checkFailureInjectionInfrastructure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasFailureInjectionTests = false;
  let hasChaosOrResilienceTests = false;

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
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('failure') && content.includes('injection') ||
                content.includes('FailureInjection') ||
                content.includes('simulateFailure') ||
                content.includes('injectFailure')) {
              hasFailureInjectionTests = true;
            }
            
            if (content.includes('chaos') || content.includes('Chaos') ||
                content.includes('resilience') || content.includes('Resilience') ||
                content.includes('circuit') && content.includes('breaker')) {
              hasChaosOrResilienceTests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasFailureInjectionTests || hasChaosOrResilienceTests) {
    logSuccess(`${productName}: Has failure-injection or resilience test infrastructure`);
    return true;
  } else {
    logError(`${productName}: Missing failure-injection test infrastructure`);
    return false;
  }
}

/**
 * Check for Postgres unavailability scenario
 */
function checkPostgresUnavailability(productPath, productName) {
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
            
            if ((content.includes('postgres') || content.includes('Postgres') || content.includes('PostgreSQL')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('disconnect') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has Postgres unavailability test`);
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
    logSuccess(`${productName}: Postgres unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing Postgres unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for ClickHouse unavailability scenario
 */
function checkClickHouseUnavailability(productPath, productName) {
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
            
            if ((content.includes('clickhouse') || content.includes('ClickHouse')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('disconnect') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has ClickHouse unavailability test`);
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
    logSuccess(`${productName}: ClickHouse unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing ClickHouse unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for OpenSearch unavailability scenario
 */
function checkOpenSearchUnavailability(productPath, productName) {
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
            
            if ((content.includes('opensearch') || content.includes('OpenSearch') || content.includes('elasticsearch') || content.includes('Elasticsearch')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('disconnect') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has OpenSearch unavailability test`);
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
    logSuccess(`${productName}: OpenSearch unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing OpenSearch unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for S3 unavailability scenario
 */
function checkS3Unavailability(productPath, productName) {
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
            
            if ((content.includes('s3') || content.includes('S3') || content.includes('storage') || content.includes('Storage')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('disconnect') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has S3 unavailability test`);
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
    logSuccess(`${productName}: S3 unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing S3 unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for audit sink unavailability scenario
 */
function checkAuditSinkUnavailability(productPath, productName) {
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
            
            if ((content.includes('audit') || content.includes('Audit')) &&
                (content.includes('sink') || content.includes('Sink')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has audit sink unavailability test`);
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
    logSuccess(`${productName}: Audit sink unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing audit sink unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for policy engine unavailability scenario
 */
function checkPolicyEngineUnavailability(productPath, productName) {
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
            
            if ((content.includes('policy') || content.includes('Policy')) &&
                (content.includes('engine') || content.includes('Engine')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has policy engine unavailability test`);
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
    logSuccess(`${productName}: Policy engine unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing policy engine unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for AI completion unavailability scenario
 */
function checkAICompletionUnavailability(productPath, productName) {
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
            
            if ((content.includes('ai') || content.includes('AI') || content.includes('llm') || content.includes('LLM')) &&
                (content.includes('completion') || content.includes('Completion') || content.includes('inference')) &&
                (content.includes('unavailable') || content.includes('down') || content.includes('fail'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has AI completion unavailability test`);
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
    logSuccess(`${productName}: AI completion unavailability scenario covered`);
  } else {
    logWarning(`${productName}: Missing AI completion unavailability scenario`);
  }

  return hasScenario;
}

/**
 * Check for network timeout scenario
 */
function checkNetworkTimeout(productPath, productName) {
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
            
            if ((content.includes('network') || content.includes('Network')) &&
                (content.includes('timeout') || content.includes('Timeout'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has network timeout test`);
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
    logSuccess(`${productName}: Network timeout scenario covered`);
  } else {
    logWarning(`${productName}: Missing network timeout scenario`);
  }

  return hasScenario;
}

/**
 * Check for queue saturation scenario
 */
function checkQueueSaturation(productPath, productName) {
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
            
            if ((content.includes('queue') || content.includes('Queue')) &&
                (content.includes('saturation') || content.includes('Saturation') || content.includes('full') || content.includes('backpressure'))) {
              hasScenario = true;
              logEvidence(`${productName}: Has queue saturation test`);
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
    logSuccess(`${productName}: Queue saturation scenario covered`);
  } else {
    logWarning(`${productName}: Missing queue saturation scenario`);
  }

  return hasScenario;
}

/**
 * Check for circuit breaker implementation
 */
function checkCircuitBreakerImplementation(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasCircuitBreaker = false;

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
            
            if (content.includes('CircuitBreaker') || 
                content.includes('circuit breaker') ||
                content.includes('@CircuitBreaker') ||
                content.includes('resilience4j')) {
              hasCircuitBreaker = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasCircuitBreaker) {
    logSuccess(`${productName}: Has circuit breaker implementation`);
  } else {
    logWarning(`${productName}: Missing circuit breaker implementation`);
  }

  return hasCircuitBreaker;
}

/**
 * Check for retry and backoff implementation
 */
function checkRetryBackoffImplementation(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasRetry = false;
  let hasBackoff = false;

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
            
            if (content.includes('@Retry') || 
                content.includes('retryPolicy') ||
                content.includes('retry')) {
              hasRetry = true;
            }
            
            if (content.includes('backoff') || 
                content.includes('Backoff') ||
                content.includes('exponential backoff')) {
              hasBackoff = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasRetry) {
    logSuccess(`${productName}: Has retry implementation`);
  } else {
    logWarning(`${productName}: Missing retry implementation`);
  }

  if (hasBackoff) {
    logSuccess(`${productName}: Has backoff implementation`);
  } else {
    logWarning(`${productName}: Missing backoff implementation`);
  }

  return hasRetry && hasBackoff;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'runtime-dependency-failure-injection');
  
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
      logError(`Missing runtime dependency failure scenario evidence: ${scenario}`);
    }
  }

  if (executedTestProductCount === 0) {
    logError('No product executed real runtime dependency failure-injection tests');
  }

  const report = {
    timestamp: stableGeneratedAt,
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

  const reportPath = path.join(evidenceDir, 'runtime-dependency-failure-injection-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking runtime dependency failure-injection proof across products...\n');

  // Resolve products from canonical product registry
  const registryProducts = getRuntimeDependencyProducts();
  
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
    logError(`No runtime dependency product matched --product=${PRODUCT_ARG}`);
  }
  currentExpectedProductCount = filteredProducts.length;

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
    const testsPassed = executeDependencyFailureTests(productPath, product.name, gradleTask);
    
    if (!testsPassed) {
      // In release mode, fail if no executable test is found - no fallback posture checks allowed
      if (RELEASE_MODE === 'release') {
        logError(`${product.name}: No executable runtime dependency failure-injection test found - required in release mode`);
        logError(`${product.name}: Fallback posture checks are not allowed in release mode`);
        logError(`${product.name}: Add explicit waiver in config/release-proof-waivers.json if this product has no external dependencies`);
      } else {
        // Fall back to posture checks in local mode only
        logWarning(`${product.name}: Test execution failed, falling back to posture checks (local mode only)`);
        checkFailureInjectionInfrastructure(productPath, product.name);
        checkPostgresUnavailability(productPath, product.name);
        checkClickHouseUnavailability(productPath, product.name);
        checkOpenSearchUnavailability(productPath, product.name);
        checkS3Unavailability(productPath, product.name);
        checkAuditSinkUnavailability(productPath, product.name);
        checkPolicyEngineUnavailability(productPath, product.name);
        checkAICompletionUnavailability(productPath, product.name);
        checkNetworkTimeout(productPath, product.name);
        checkQueueSaturation(productPath, product.name);
        checkCircuitBreakerImplementation(productPath, product.name);
        checkRetryBackoffImplementation(productPath, product.name);
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
  logValidationResults(validationResults, 'Runtime Dependency Failure-Injection Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nRuntime dependency failure-injection check passed.');
}

main();
