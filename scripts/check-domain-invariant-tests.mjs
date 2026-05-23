#!/usr/bin/env node

/**
 * P2-12: Domain-Specific Invariant Tests
 *
 * Validates domain-specific invariants for each product:
 * - Data Cloud: Entity consistency, audit trail integrity, outbox processing
 * - Finance: Transaction integrity, balance consistency, audit completeness
 * - PHR: Patient data privacy, consent validity, medical record integrity
 * - Digital Marketing: Campaign consistency, attribution accuracy, budget tracking
 * - YAPPC: Phase transition validity, conversation state consistency, artifact integrity
 *
 * Usage: node scripts/check-domain-invariant-tests.mjs [--product <product>]
 */

import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';
import { getActiveProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

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
 * Domain-specific invariant definitions per product
 */
const DOMAIN_INVARIANTS = {
  'data-cloud': {
    name: 'Data Cloud',
    invariants: [
      {
        id: 'entity-consistency',
        name: 'Entity Consistency',
        description: 'Entities maintain consistency across event store and materialized view',
        testPattern: 'EntityConsistencyTest',
      },
      {
        id: 'audit-trail-integrity',
        name: 'Audit Trail Integrity',
        description: 'Audit events are recorded for all mutations',
        testPattern: 'AuditTrailIntegrityTest',
      },
      {
        id: 'outbox-processing',
        name: 'Outbox Processing',
        description: 'Outbox entries are processed and marked complete',
        testPattern: 'OutboxProcessingTest',
      },
    ],
  },
  'finance': {
    name: 'Finance',
    invariants: [
      {
        id: 'transaction-integrity',
        name: 'Transaction Integrity',
        description: 'Transactions maintain ACID properties',
        testPattern: 'TransactionIntegrityTest',
      },
      {
        id: 'balance-consistency',
        name: 'Balance Consistency',
        description: 'Account balances remain consistent after transactions',
        testPattern: 'BalanceConsistencyTest',
      },
      {
        id: 'audit-completeness',
        name: 'Audit Completeness',
        description: 'All financial operations are audited',
        testPattern: 'FinancialAuditTest',
      },
    ],
  },
  'phr': {
    name: 'PHR',
    invariants: [
      {
        id: 'patient-data-privacy',
        name: 'Patient Data Privacy',
        description: 'Patient data is properly redacted and protected',
        testPattern: 'PatientDataPrivacyTest',
      },
      {
        id: 'consent-validity',
        name: 'Consent Validity',
        description: 'Consent records are valid and up-to-date',
        testPattern: 'ConsentValidityTest',
      },
      {
        id: 'medical-record-integrity',
        name: 'Medical Record Integrity',
        description: 'Medical records maintain integrity and immutability',
        testPattern: 'MedicalRecordIntegrityTest',
      },
    ],
  },
  'digital-marketing': {
    name: 'Digital Marketing',
    invariants: [
      {
        id: 'campaign-consistency',
        name: 'Campaign Consistency',
        description: 'Campaign state remains consistent across all channels',
        testPattern: 'CampaignConsistencyTest',
      },
      {
        id: 'attribution-accuracy',
        name: 'Attribution Accuracy',
        description: 'Attribution tracking is accurate and complete',
        testPattern: 'AttributionAccuracyTest',
      },
      {
        id: 'budget-tracking',
        name: 'Budget Tracking',
        description: 'Campaign budgets are tracked and enforced',
        testPattern: 'BudgetTrackingTest',
      },
    ],
  },
  'yappc': {
    name: 'YAPPC',
    invariants: [
      {
        id: 'phase-transition-validity',
        name: 'Phase Transition Validity',
        description: 'Phase transitions are valid and reversible',
        testPattern: 'PhaseTransitionValidityTest',
      },
      {
        id: 'conversation-state-consistency',
        name: 'Conversation State Consistency',
        description: 'Conversation state remains consistent',
        testPattern: 'ConversationStateTest',
      },
      {
        id: 'artifact-integrity',
        name: 'Artifact Integrity',
        description: 'Generated artifacts maintain integrity',
        testPattern: 'ArtifactIntegrityTest',
      },
    ],
  },
};

/**
 * Check for domain-specific invariant tests
 */
function checkDomainInvariantTests(productPath, productName, productId) {
  const domainInvariants = DOMAIN_INVARIANTS[productId];
  
  if (!domainInvariants) {
    logWarning(`${productName}: No domain invariants defined for this product`);
    return false;
  }

  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let testsFound = 0;
  const invariantsFound = [];

  for (const invariant of domainInvariants.invariants) {
    let invariantTestFound = false;
    
    for (const testDir of testDirs) {
      if (!existsSync(testDir)) continue;

      function searchDir(dir) {
        try {
          const items = require('node:fs').readdirSync(dir);
          
          for (const item of items) {
            const itemPath = path.join(dir, item);
            const stat = require('node:fs').statSync(itemPath);
            
            if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
              searchDir(itemPath);
            } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
              const content = require('node:fs').readFileSync(itemPath, 'utf8');
              
              if (content.includes(invariant.testPattern) || 
                  content.includes(invariant.id) ||
                  (content.includes('invariant') && content.includes(invariant.name.toLowerCase()))) {
                invariantTestFound = true;
                logEvidence(`${productName}: Found invariant test for ${invariant.name}`);
              }
            }
          }
        } catch (e) {
          // Skip directories we can't read
        }
      }

      searchDir(testDir);
    }

    if (invariantTestFound) {
      testsFound++;
      invariantsFound.push(invariant.id);
    } else {
      logWarning(`${productName}: Missing invariant test for ${invariant.name} (${invariant.testPattern})`);
    }
  }

  const totalInvariants = domainInvariants.invariants.length;
  const coverage = (testsFound / totalInvariants) * 100;

  if (testsFound === totalInvariants) {
    logSuccess(`${productName}: All ${totalInvariants} domain invariant tests found (${coverage}% coverage)`);
    return true;
  } else if (testsFound > 0) {
    logWarning(`${productName}: Partial domain invariant test coverage (${testsFound}/${totalInvariants} = ${coverage.toFixed(1)}%)`);
    return false;
  } else {
    logError(`${productName}: No domain invariant tests found (0/${totalInvariants})`);
    return false;
  }
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'domain-invariant-tests');
  
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

  const reportPath = path.join(evidenceDir, 'domain-invariant-tests-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking domain-specific invariant tests across products...\n');

  // Resolve active products from canonical product registry
  const registryProducts = getActiveProducts();
  
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
    
    checkDomainInvariantTests(productPath, product.name, product.productId);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'Domain Invariant Tests Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nDomain invariant tests check passed.');
}

main();
