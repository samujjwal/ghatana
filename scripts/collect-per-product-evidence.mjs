#!/usr/bin/env node

/**
 * Wave 2: Add Per-Product a11y/i18n/AI/Security/Release Evidence
 *
 * Collects and aggregates per-product evidence for:
 * - Accessibility (a11y) compliance
 * - Internationalization (i18n) maturity
 * - AI governance
 * - Security posture
 * - Release readiness
 *
 * This ensures each product has comprehensive evidence across all quality dimensions.
 *
 * Usage: node scripts/collect-per-product-evidence.mjs [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

const violations = [];
const warnings = [];
const evidence = [];

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
 * Check for a11y evidence
 */
function checkA11yEvidence(productPath, productName) {
  const evidenceDirs = [
    path.join(productPath, '.kernel', 'evidence', 'a11y-behavioral-proof'),
    path.join(productPath, 'evidence', 'a11y'),
  ];

  let hasA11yEvidence = false;
  let hasA11yTests = false;

  for (const evidenceDir of evidenceDirs) {
    if (!existsSync(evidenceDir)) continue;

    const files = readdirSync(evidenceDir);
    if (files.length > 0) {
      hasA11yEvidence = true;
      logEvidence(`${productName}: Has a11y evidence directory`);
    }
  }

  // Check for a11y tests
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('a11y') || content.includes('accessibility') || content.includes('aria')) {
              hasA11yTests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasA11yEvidence) {
    logSuccess(`${productName}: Has a11y evidence`);
  } else {
    logWarning(`${productName}: Missing a11y evidence`);
  }

  if (hasA11yTests) {
    logSuccess(`${productName}: Has a11y tests`);
  } else {
    logWarning(`${productName}: Missing a11y tests`);
  }

  return hasA11yEvidence || hasA11yTests;
}

/**
 * Check for i18n evidence
 */
function checkI18nEvidence(productPath, productName) {
  const evidenceDirs = [
    path.join(productPath, '.kernel', 'evidence', 'i18n-conformance'),
    path.join(productPath, 'evidence', 'i18n'),
  ];

  let hasI18nEvidence = false;
  let hasLocaleFiles = false;

  for (const evidenceDir of evidenceDirs) {
    if (!existsSync(evidenceDir)) continue;

    const files = readdirSync(evidenceDir);
    if (files.length > 0) {
      hasI18nEvidence = true;
      logEvidence(`${productName}: Has i18n evidence directory`);
    }
  }

  // Check for locale files
  const localeDirs = [
    path.join(productPath, 'src/locales'),
    path.join(productPath, 'src/i18n'),
    path.join(productPath, 'locales'),
  ];

  for (const localeDir of localeDirs) {
    if (!existsSync(localeDir)) continue;

    const files = readdirSync(localeDir);
    const localeFiles = files.filter(f => f.endsWith('.json') || f.endsWith('.yaml'));
    
    if (localeFiles.length > 0) {
      hasLocaleFiles = true;
    }
  }

  if (hasI18nEvidence) {
    logSuccess(`${productName}: Has i18n evidence`);
  } else {
    logWarning(`${productName}: Missing i18n evidence`);
  }

  if (hasLocaleFiles) {
    logSuccess(`${productName}: Has locale files`);
  } else {
    logWarning(`${productName}: Missing locale files`);
  }

  return hasI18nEvidence || hasLocaleFiles;
}

/**
 * Check for AI governance evidence
 */
function checkAIGovernanceEvidence(productPath, productName) {
  const evidenceDirs = [
    path.join(productPath, '.kernel', 'evidence', 'ai-governance-behavioral-proof'),
    path.join(productPath, 'evidence', 'ai-governance'),
  ];

  let hasAIEvidence = false;
  let hasAITests = false;

  for (const evidenceDir of evidenceDirs) {
    if (!existsSync(evidenceDir)) continue;

    const files = readdirSync(evidenceDir);
    if (files.length > 0) {
      hasAIEvidence = true;
      logEvidence(`${productName}: Has AI governance evidence directory`);
    }
  }

  // Check for AI tests
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'src/test/java'),
  ];

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.java')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('AIGovernance') || content.includes('ai-governance') ||
                content.includes('ModelAvailability') || content.includes('model-availability')) {
              hasAITests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasAIEvidence) {
    logSuccess(`${productName}: Has AI governance evidence`);
  } else {
    logWarning(`${productName}: Missing AI governance evidence`);
  }

  if (hasAITests) {
    logSuccess(`${productName}: Has AI governance tests`);
  } else {
    logWarning(`${productName}: Missing AI governance tests`);
  }

  return hasAIEvidence || hasAITests;
}

/**
 * Check for security evidence
 */
function checkSecurityEvidence(productPath, productName) {
  const evidenceDirs = [
    path.join(productPath, '.kernel', 'evidence', 'security'),
    path.join(productPath, 'evidence', 'security'),
  ];

  let hasSecurityEvidence = false;
  let hasSecurityTests = false;

  for (const evidenceDir of evidenceDirs) {
    if (!existsSync(evidenceDir)) continue;

    const files = readdirSync(evidenceDir);
    if (files.length > 0) {
      hasSecurityEvidence = true;
      logEvidence(`${productName}: Has security evidence directory`);
    }
  }

  // Check for security tests
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'src/test/java'),
  ];

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.java')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('security') || content.includes('Security') ||
                content.includes('auth') || content.includes('Auth') ||
                content.includes('vulnerability') || content.includes('Vulnerability')) {
              hasSecurityTests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasSecurityEvidence) {
    logSuccess(`${productName}: Has security evidence`);
  } else {
    logWarning(`${productName}: Missing security evidence`);
  }

  if (hasSecurityTests) {
    logSuccess(`${productName}: Has security tests`);
  } else {
    logWarning(`${productName}: Missing security tests`);
  }

  return hasSecurityEvidence || hasSecurityTests;
}

/**
 * Check for release evidence
 */
function checkReleaseEvidence(productPath, productName) {
  const evidenceDirs = [
    path.join(productPath, '.kernel', 'evidence'),
    path.join(productPath, 'evidence'),
  ];

  let hasReleaseEvidence = false;
  let hasReleaseSummary = false;

  for (const evidenceDir of evidenceDirs) {
    if (!existsSync(evidenceDir)) continue;

    const files = readdirSync(evidenceDir);
    if (files.length > 0) {
      hasReleaseEvidence = true;
      logEvidence(`${productName}: Has release evidence directory`);
    }
  }

  // Check for release summary
  const summaryPath = path.join(productPath, 'RELEASE_SUMMARY.md');
  if (existsSync(summaryPath)) {
    hasReleaseSummary = true;
  }

  if (hasReleaseEvidence) {
    logSuccess(`${productName}: Has release evidence`);
  } else {
    logWarning(`${productName}: Missing release evidence`);
  }

  if (hasReleaseSummary) {
    logSuccess(`${productName}: Has release summary`);
  } else {
    logWarning(`${productName}: Missing release summary`);
  }

  return hasReleaseEvidence || hasReleaseSummary;
}

/**
 * Generate per-product evidence report
 */
function generatePerProductEvidenceReport(productName, productPath) {
  const report = {
    product: productName,
    timestamp: new Date().toISOString(),
    evidence: {
      a11y: checkA11yEvidence(productPath, productName),
      i18n: checkI18nEvidence(productPath, productName),
      aiGovernance: checkAIGovernanceEvidence(productPath, productName),
      security: checkSecurityEvidence(productPath, productName),
      release: checkReleaseEvidence(productPath, productName)
    }
  };

  return report;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport(reports) {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'per-product-evidence');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const report = {
    timestamp: new Date().toISOString(),
    products: reports,
    summary: {
      totalProducts: reports.length,
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
    }
  };

  const reportPath = path.join(evidenceDir, `per-product-evidence-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main execution
 */
function main() {
  console.log('Collecting per-product a11y/i18n/AI/security/release evidence...\n');

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
    { path: 'products/aep', name: 'AEP' },
    { path: 'products/digital-marketing', name: 'Digital Marketing' },
    { path: 'products/phr', name: 'PHR' },
    { path: 'products/finance', name: 'Finance' },
    { path: 'products/flashit', name: 'FlashIt' },
    { path: 'products/yappc', name: 'YAPPC' },
  ];

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  const reports = [];

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logWarning(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- ${product.name} ---`);
    
    const report = generatePerProductEvidenceReport(product.name, productPath);
    reports.push(report);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport(reports);

  if (violations.length > 0) {
    console.log('\nPer-product evidence collection failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nPer-product evidence collection passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nPer-product evidence collection passed.');
}

main();
