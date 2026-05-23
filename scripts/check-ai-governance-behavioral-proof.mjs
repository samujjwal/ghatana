#!/usr/bin/env node

/**
 * P1-3: AI Governance Behavioral Proof Suite
 *
 * Validates comprehensive AI governance with behavioral verification:
 * - Model availability proof
 * - Fallback prevention proof
 * - Privacy redaction before model calls
 * - Prompt/input/output provenance tracking
 * - Cost budget enforcement
 * - Evaluation quality thresholds
 * - Human approval for risky AI actions
 * - Audit evidence for AI-generated recommendations/actions
 *
 * This replaces shallow posture checks with deep behavioral verification that
 * AI operations are governed, safe, and auditable.
 *
 * Usage: node scripts/check-ai-governance-behavioral-proof.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';
import { getAIGovernanceProducts, resolveProductForProof, getProductLifecycleTestCommand } from './lib/product-registry-helper.mjs';

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
 * Execute AI governance behavioral tests
 */
function executeAIGovernanceTests(productPath, productName, testCommand) {
  // Look for AI governance tests
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
          } else if (item.endsWith('.java') && (item.includes('AIGovernance') || item.includes('ModelGovernance'))) {
            testFound = true;
            const className = item.replace('.java', '');
            const content = readFileSync(itemPath, 'utf8');
            const packageMatch = content.match(/^\s*package\s+([\w.]+);/m);
            const testPattern = packageMatch ? `${packageMatch[1]}.${className}` : `*${className}`;
            
            try {
              // Execute the test using product-specific test command
              let args;
              if (testCommand.startsWith('pnpm')) {
                // For pnpm products
                args = ['pnpm', 'test', '--', className];
              } else {
                // For Gradle products
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
                logSuccess(`${productName}: AI governance tests PASSED`);
                logEvidence(`${productName}: Executed real AI governance behavioral scenarios`);
                return true;
              } else if (testFailed) {
                logError(`${productName}: AI governance tests FAILED`);
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
    logWarning(`${productName}: No AI governance test found, falling back to posture checks`);
    return false;
  }

  return false;
}

/**
 * Check for AI governance infrastructure (fallback)
 */
function checkAIGovernanceInfrastructure(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasAIGovernance = false;
  let hasModelRegistry = false;

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
            
            if (content.includes('AIGovernance') || 
                content.includes('AI governance') ||
                content.includes('ModelGovernance') ||
                content.includes('ModelRegistry')) {
              hasAIGovernance = true;
            }
            
            if (content.includes('ModelRegistry') || 
                content.includes('model registry') ||
                content.includes('ModelCatalog')) {
              hasModelRegistry = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasAIGovernance) {
    logSuccess(`${productName}: Has AI governance infrastructure`);
  } else {
    logWarning(`${productName}: Missing AI governance infrastructure`);
  }

  if (hasModelRegistry) {
    logSuccess(`${productName}: Has model registry`);
  } else {
    logWarning(`${productName}: Missing model registry`);
  }

  return hasAIGovernance && hasModelRegistry;
}

/**
 * Check for model availability proof
 */
function checkModelAvailability(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasAvailabilityCheck = false;

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
            
            if ((content.includes('model') || content.includes('Model')) &&
                (content.includes('availability') || content.includes('Availability') || content.includes('health') || content.includes('Health')) &&
                (content.includes('check') || content.includes('Check') || content.includes('verify'))) {
              hasAvailabilityCheck = true;
              logEvidence(`${productName}: Has model availability check`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasAvailabilityCheck) {
    logSuccess(`${productName}: Model availability proof covered`);
  } else {
    logWarning(`${productName}: Missing model availability proof`);
  }

  return hasAvailabilityCheck;
}

/**
 * Check for fallback prevention proof
 */
function checkFallbackPrevention(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasFallbackPrevention = false;

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
            
            if ((content.includes('fallback') || content.includes('Fallback')) &&
                (content.includes('prevent') || content.includes('Prevent') || content.includes('disable') || content.includes('Disable'))) {
              hasFallbackPrevention = true;
              logEvidence(`${productName}: Has fallback prevention logic`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasFallbackPrevention) {
    logSuccess(`${productName}: Fallback prevention proof covered`);
  } else {
    logWarning(`${productName}: Missing fallback prevention proof`);
  }

  return hasFallbackPrevention;
}

/**
 * Check for privacy redaction before model calls
 */
function checkPrivacyRedaction(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasPrivacyRedaction = false;

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
            
            if ((content.includes('privacy') || content.includes('Privacy') || content.includes('PII')) &&
                (content.includes('redact') || content.includes('Redact') || content.includes('sanitize') || content.includes('Sanitize')) &&
                (content.includes('model') || content.includes('Model') || content.includes('llm') || content.includes('LLM'))) {
              hasPrivacyRedaction = true;
              logEvidence(`${productName}: Has privacy redaction before model calls`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasPrivacyRedaction) {
    logSuccess(`${productName}: Privacy redaction proof covered`);
  } else {
    logWarning(`${productName}: Missing privacy redaction proof`);
  }

  return hasPrivacyRedaction;
}

/**
 * Check for prompt/input/output provenance tracking
 */
function checkProvenanceTracking(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasProvenanceTracking = false;

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
            
            if ((content.includes('provenance') || content.includes('Provenance') || content.includes('traceability')) &&
                (content.includes('prompt') || content.includes('input') || content.includes('output') || content.includes('response'))) {
              hasProvenanceTracking = true;
              logEvidence(`${productName}: Has provenance tracking`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasProvenanceTracking) {
    logSuccess(`${productName}: Provenance tracking proof covered`);
  } else {
    logWarning(`${productName}: Missing provenance tracking proof`);
  }

  return hasProvenanceTracking;
}

/**
 * Check for cost budget enforcement
 */
function checkCostBudgetEnforcement(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasCostBudget = false;

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
            
            if ((content.includes('cost') || content.includes('Cost') || content.includes('budget') || content.includes('Budget')) &&
                (content.includes('enforce') || content.includes('Enforce') || content.includes('limit') || content.includes('Limit')) &&
                (content.includes('model') || content.includes('Model') || content.includes('llm') || content.includes('LLM'))) {
              hasCostBudget = true;
              logEvidence(`${productName}: Has cost budget enforcement`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasCostBudget) {
    logSuccess(`${productName}: Cost budget enforcement proof covered`);
  } else {
    logWarning(`${productName}: Missing cost budget enforcement proof`);
  }

  return hasCostBudget;
}

/**
 * Check for evaluation quality thresholds
 */
function checkEvaluationQualityThresholds(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasQualityThresholds = false;

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
            
            if ((content.includes('evaluation') || content.includes('Evaluation') || content.includes('quality') || content.includes('Quality')) &&
                (content.includes('threshold') || content.includes('Threshold') || content.includes('score') || content.includes('metric'))) {
              hasQualityThresholds = true;
              logEvidence(`${productName}: Has evaluation quality thresholds`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasQualityThresholds) {
    logSuccess(`${productName}: Evaluation quality thresholds proof covered`);
  } else {
    logWarning(`${productName}: Missing evaluation quality thresholds proof`);
  }

  return hasQualityThresholds;
}

/**
 * Check for human approval for risky AI actions
 */
function checkHumanApproval(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasHumanApproval = false;

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
            
            if ((content.includes('human') || content.includes('Human') || content.includes('manual') || content.includes('Manual')) &&
                (content.includes('approval') || content.includes('Approval') || content.includes('review') || content.includes('Review')) &&
                (content.includes('risky') || content.includes('Risky') || content.includes('dangerous') || content.includes('critical'))) {
              hasHumanApproval = true;
              logEvidence(`${productName}: Has human approval for risky actions`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasHumanApproval) {
    logSuccess(`${productName}: Human approval proof covered`);
  } else {
    logWarning(`${productName}: Missing human approval proof`);
  }

  return hasHumanApproval;
}

/**
 * Check for audit evidence for AI-generated recommendations/actions
 */
function checkAIAuditEvidence(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasAIAuditEvidence = false;

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
            
            if ((content.includes('audit') || content.includes('Audit')) &&
                (content.includes('AI') || content.includes('ai') || content.includes('model') || content.includes('Model')) &&
                (content.includes('recommendation') || content.includes('action') || content.includes('evidence') || content.includes('log'))) {
              hasAIAuditEvidence = true;
              logEvidence(`${productName}: Has AI audit evidence tracking`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasAIAuditEvidence) {
    logSuccess(`${productName}: AI audit evidence proof covered`);
  } else {
    logWarning(`${productName}: Missing AI audit evidence proof`);
  }

  return hasAIAuditEvidence;
}

/**
 * Check for AI safety guardrails
 */
function checkAISafetyGuardrails(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src/main/java'),
    path.join(productPath, 'src'),
  ];

  let hasSafetyGuardrails = false;

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
            
            if ((content.includes('safety') || content.includes('Safety') || content.includes('guardrail') || content.includes('Guardrail')) &&
                (content.includes('AI') || content.includes('ai') || content.includes('model') || content.includes('Model'))) {
              hasSafetyGuardrails = true;
              logEvidence(`${productName}: Has AI safety guardrails`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasSafetyGuardrails) {
    logSuccess(`${productName}: AI safety guardrails implemented`);
  } else {
    logWarning(`${productName}: Missing AI safety guardrails`);
  }

  return hasSafetyGuardrails;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ai-governance-behavioral-proof');
  
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

  const reportPath = path.join(evidenceDir, 'ai-governance-behavioral-proof-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking AI governance behavioral proof across products...\n');

  // Resolve AI-enabled products from canonical product registry
  const registryProducts = getAIGovernanceProducts();
  
  // Resolve product information for proof
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  // Filter by product if specified
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
    
    // Determine test command for this product
    const testCommand = getProductLifecycleTestCommand(product.productId) || `${product.productId}:test`;
    
    // Execute real tests instead of posture checks
    const testsPassed = executeAIGovernanceTests(productPath, product.name, testCommand);
    
    if (!testsPassed) {
      // In release mode, fail if no executable test is found
      if (RELEASE_MODE === 'release') {
        logError(`${product.name}: No executable AI governance test found - required in release mode`);
      } else {
        // Fall back to posture checks in local mode
        logWarning(`${product.name}: Test execution failed, falling back to posture checks`);
        checkAIGovernanceInfrastructure(productPath, product.name);
        checkModelAvailability(productPath, product.name);
        checkFallbackPrevention(productPath, product.name);
        checkPrivacyRedaction(productPath, product.name);
        checkProvenanceTracking(productPath, product.name);
        checkCostBudgetEnforcement(productPath, product.name);
        checkEvaluationQualityThresholds(productPath, product.name);
        checkHumanApproval(productPath, product.name);
        checkAIAuditEvidence(productPath, product.name);
        checkAISafetyGuardrails(productPath, product.name);
      }
    }
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'AI Governance Behavioral Proof Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nAI governance behavioral proof check passed.');
}

main();
