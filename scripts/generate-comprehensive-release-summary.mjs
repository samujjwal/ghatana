#!/usr/bin/env node

/**
 * P3-20: Comprehensive Release Summary Generator
 *
 * Generates release summaries scoring all 47 dimensions per product:
 * - Atomic workflow failure-injection (5 dimensions)
 * - Runtime dependency failure-injection (12 dimensions)
 * - AI governance behavioral proof (8 dimensions)
 * - Accessibility behavioral proof (7 dimensions)
 * - Internationalization behavioral proof (6 dimensions)
 * - Domain-specific invariant tests (3 dimensions)
 * - SLO and cost budgets (4 dimensions)
 * - Product release readiness (2 dimensions)
 *
 * Usage: node scripts/generate-comprehensive-release-summary.mjs [--product <product>]
 */

import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getActiveProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

/**
 * 47 release quality dimensions
 */
const DIMENSIONS = {
  // Atomic workflow failure-injection (5)
  'atomic-workflow-event-append-failure': { category: 'atomic-workflow', weight: 1 },
  'atomic-workflow-audit-write-failure': { category: 'atomic-workflow', weight: 1 },
  'atomic-workflow-outbox-failure': { category: 'atomic-workflow', weight: 1 },
  'atomic-workflow-idempotency': { category: 'atomic-workflow', weight: 1 },
  'atomic-workflow-rollback': { category: 'atomic-workflow', weight: 1 },
  
  // Runtime dependency failure-injection (12)
  'runtime-dep-postgres-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-clickhouse-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-opensearch-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-s3-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-audit-sink-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-policy-engine-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-ai-completion-unavailability': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-network-timeout': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-queue-saturation': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-circuit-breaker': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-retry-backoff': { category: 'runtime-dep', weight: 1 },
  'runtime-dep-fallback-prevention': { category: 'runtime-dep', weight: 1 },
  
  // AI governance behavioral proof (8)
  'ai-gov-model-availability': { category: 'ai-gov', weight: 1 },
  'ai-gov-fallback-prevention': { category: 'ai-gov', weight: 1 },
  'ai-gov-privacy-redaction': { category: 'ai-gov', weight: 1 },
  'ai-gov-provenance-tracking': { category: 'ai-gov', weight: 1 },
  'ai-gov-cost-budget': { category: 'ai-gov', weight: 1 },
  'ai-gov-evaluation-quality': { category: 'ai-gov', weight: 1 },
  'ai-gov-human-approval': { category: 'ai-gov', weight: 1 },
  'ai-gov-audit-evidence': { category: 'ai-gov', weight: 1 },
  
  // Accessibility behavioral proof (7)
  'a11y-keyboard-journey': { category: 'a11y', weight: 1 },
  'a11y-screen-reader': { category: 'a11y', weight: 1 },
  'a11y-table-accessibility': { category: 'a11y', weight: 1 },
  'a11y-chart-accessibility': { category: 'a11y', weight: 1 },
  'a11y-modal-accessibility': { category: 'a11y', weight: 1 },
  'a11y-focus-management': { category: 'a11y', weight: 1 },
  'a11y-color-contrast': { category: 'a11y', weight: 1 },
  
  // Internationalization behavioral proof (6)
  'i18n-missing-key-extraction': { category: 'i18n', weight: 1 },
  'i18n-date-formatting': { category: 'i18n', weight: 1 },
  'i18n-number-formatting': { category: 'i18n', weight: 1 },
  'i18n-currency-formatting': { category: 'i18n', weight: 1 },
  'i18n-timezone-coverage': { category: 'i18n', weight: 1 },
  'i18n-rtl-readiness': { category: 'i18n', weight: 1 },
  
  // Domain-specific invariant tests (3)
  'domain-invariant-entity-consistency': { category: 'domain-invariant', weight: 1 },
  'domain-invariant-audit-integrity': { category: 'domain-invariant', weight: 1 },
  'domain-invariant-business-rule': { category: 'domain-invariant', weight: 1 },
  
  // SLO and cost budgets (4)
  'slo-latency': { category: 'slo', weight: 1 },
  'slo-error-rate': { category: 'slo', weight: 1 },
  'slo-availability': { category: 'slo', weight: 1 },
  'cost-budget-enforcement': { category: 'slo', weight: 1 },
  
  // Product release readiness (2)
  'release-readiness-test-coverage': { category: 'release-readiness', weight: 1 },
  'release-readiness-security-compliance': { category: 'release-readiness', weight: 1 },
};

/**
 * Load evidence from a file
 */
function loadEvidence(evidencePath) {
  if (!existsSync(evidencePath)) {
    return null;
  }

  try {
    const content = readFileSync(evidencePath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`Failed to load evidence from ${evidencePath}: ${error.message}`);
    return null;
  }
}

/**
 * Score a dimension based on evidence
 */
function scoreDimension(dimensionId, evidence) {
  if (!evidence) {
    return { score: 0, status: 'no-evidence' };
  }

  const evidenceStr = JSON.stringify(evidence);
  
  // Check for violations
  if (evidence.violations && evidence.violations.length > 0) {
    return { score: 0, status: 'failed' };
  }

  // Check for warnings
  if (evidence.warnings && evidence.warnings.length > 0) {
    return { score: 0.5, status: 'warning' };
  }

  // Check for evidence items
  if (evidence.evidence && evidence.evidence.length > 0) {
    return { score: 1, status: 'passed' };
  }

  return { score: 0, status: 'no-evidence' };
}

/**
 * Generate comprehensive release summary
 */
function generateComprehensiveSummary() {
  console.log('Generating comprehensive release summary scoring all 47 dimensions...\n');

  // Resolve active products from canonical product registry
  const registryProducts = getActiveProducts();
  
  // Resolve product information for summary
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  const summary = {
    timestamp: new Date().toISOString(),
    totalDimensions: Object.keys(DIMENSIONS).length,
    products: [],
  };

  for (const product of filteredProducts) {
    console.log(`\n--- Scoring ${product.name} ---`);

    const productScores = {
      productId: product.productId,
      name: product.name,
      dimensions: {},
      categoryScores: {},
      overallScore: 0,
    };

    let totalScore = 0;
    let totalWeight = 0;

    // Score each dimension
    for (const [dimensionId, dimension] of Object.entries(DIMENSIONS)) {
      const evidencePath = path.join(repoRoot, '.kernel', 'evidence', dimension.category, `${dimension.category}-latest.json`);
      const evidence = loadEvidence(evidencePath);
      const result = scoreDimension(dimensionId, evidence);

      productScores.dimensions[dimensionId] = {
        ...result,
        weight: dimension.weight,
      };

      totalScore += result.score * dimension.weight;
      totalWeight += dimension.weight;
    }

    // Calculate category scores
    const categories = {};
    for (const [dimensionId, dimension] of Object.entries(DIMENSIONS)) {
      if (!categories[dimension.category]) {
        categories[dimension.category] = { score: 0, weight: 0 };
      }
      categories[dimension.category].score += productScores.dimensions[dimensionId].score * dimension.weight;
      categories[dimension.category].weight += dimension.weight;
    }

    for (const [category, data] of Object.entries(categories)) {
      productScores.categoryScores[category] = data.weight > 0 ? data.score / data.weight : 0;
    }

    // Calculate overall score
    productScores.overallScore = totalWeight > 0 ? totalScore / totalWeight : 0;

    console.log(`Overall score: ${(productScores.overallScore * 100).toFixed(1)}%`);
    for (const [category, score] of Object.entries(productScores.categoryScores)) {
      console.log(`  ${category}: ${(score * 100).toFixed(1)}%`);
    }

    summary.products.push(productScores);
  }

  // Write summary
  const summaryDir = path.join(repoRoot, 'release-evidence');
  if (!existsSync(summaryDir)) {
    mkdirSync(summaryDir, { recursive: true });
  }

  const summaryPath = path.join(summaryDir, 'comprehensive-release-summary-latest.json');
  writeFileSync(summaryPath, JSON.stringify(summary, null, 2));

  console.log(`\n📄 Comprehensive release summary written to: ${summaryPath}`);
  console.log(`\nTotal dimensions scored: ${summary.totalDimensions}`);
  console.log(`Products scored: ${summary.products.length}`);
}

generateComprehensiveSummary();
