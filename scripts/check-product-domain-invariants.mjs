#!/usr/bin/env node

import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-domain-invariants.json');

function collectFiles(rootDir, matcher) {
  const results = [];

  function walk(current) {
    if (!existsSync(current)) {
      return;
    }
    for (const entry of readdirSync(current)) {
      const fullPath = path.join(current, entry);
      const stats = statSync(fullPath);
      if (stats.isDirectory()) {
        if (entry === 'node_modules' || entry === '.git' || entry === 'build' || entry === 'dist') {
          continue;
        }
        walk(fullPath);
      } else if (matcher(entry, fullPath)) {
        results.push(fullPath);
      }
    }
  }

  walk(rootDir);
  return results;
}

function loadInvariantDeclaration(productPath) {
  const possiblePaths = [
    path.join(productPath, 'domain-invariants.yaml'),
    path.join(productPath, 'domain-invariants.json'),
    path.join(productPath, 'config', 'domain-invariants.yaml'),
    path.join(productPath, 'config', 'domain-invariants.json'),
  ];

  for (const possiblePath of possiblePaths) {
    if (existsSync(possiblePath)) {
      try {
        const content = readFileSync(possiblePath, 'utf8');
        // Simple YAML/JSON parsing - for YAML, we'd need a parser, but for now we'll handle JSON
        if (possiblePath.endsWith('.json')) {
          return JSON.parse(content);
        }
        // For YAML, return null for now - would need yaml parser
        return null;
      } catch (error) {
        console.warn(`Warning: Failed to parse invariant declaration at ${possiblePath}:`, error.message);
        return null;
      }
    }
  }

  return null;
}

function validateInvariantTestMapping(invariantDeclaration, productRoot) {
  const violations = [];

  if (!invariantDeclaration || !invariantDeclaration.invariants) {
    return violations;
  }

  for (const invariant of invariantDeclaration.invariants) {
    // If testId is null, the invariant doesn't have a test yet - this is allowed
    if (invariant.testId === null) {
      continue;
    }

    // If testId is provided but testPath is missing, that's a violation
    if (!invariant.testPath) {
      violations.push(
        `Invariant '${invariant.id}' (${invariant.name}) has testId but no testPath. ` +
        `Add testPath to locate the test file.`
      );
      continue;
    }

    // Check if test file exists when both testId and testPath are provided
    const testFullPath = path.join(productRoot, invariant.testPath);
    if (!existsSync(testFullPath)) {
      violations.push(
        `Invariant '${invariant.id}' (${invariant.name}) references non-existent test: ${invariant.testPath}`
      );
    }
  }

  return violations;
}

/**
 * Validates that invariant tests have execution evidence (test results)
 */
function validateInvariantTestExecution(invariantDeclaration, productRoot, productId) {
  const violations = [];
  const evidencePaths = [
    path.join(repoRoot, '.kernel/evidence', 'test-execution-summary.json'),
    path.join(repoRoot, '.kernel/evidence', `${productId}-test-results.json`),
    path.join(repoRoot, '.kernel/evidence', `product-release-readiness.${productId}.json`),
    path.join(repoRoot, '.kernel/evidence', `product-release-evidence-pack.${productId}.json`),
    path.join(repoRoot, '.kernel/evidence', 'product-feature-completeness-report.json'),
    path.join(productRoot, 'build', 'test-results', 'test'),
    path.join(productRoot, 'build', 'reports', 'tests'),
  ];

  if (!invariantDeclaration || !invariantDeclaration.invariants) {
    return violations;
  }

  // Check if any invariants have tests (testId is not null)
  const invariantsWithTests = invariantDeclaration.invariants.filter(inv => inv.testId !== null);
  
  // If no invariants have tests, skip execution evidence check
  if (invariantsWithTests.length === 0) {
    return violations;
  }

  // Check for any test execution evidence
  const hasEvidence = evidencePaths.some(p => existsSync(p));
  
  if (!hasEvidence) {
    violations.push(
      `Product ${productId} has invariants with tests but no test execution evidence. Run tests to generate result artifacts.`
    );
    return violations;
  }

  // Load test execution summary if available
  const summaryPath = path.join(repoRoot, '.kernel/evidence', 'test-execution-summary.json');
  if (existsSync(summaryPath)) {
    try {
      const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
      const productTests = summary[productId] || [];
      
      for (const invariant of invariantsWithTests) {
        const testExecuted = productTests.some(t => 
          t.testId === invariant.testId || 
          t.testName === invariant.name ||
          t.testPath === invariant.testPath
        );
        
        if (!testExecuted) {
          violations.push(
            `Invariant '${invariant.id}' (${invariant.name}) test '${invariant.testId}' has no execution evidence in test results.`
          );
        }
      }
    } catch (error) {
      console.warn(`Warning: Failed to parse test execution summary: ${error.message}`);
    }
  }

  return violations;
}

/**
 * Validates that invariant categories match required categories
 */
function validateInvariantCategories(invariantDeclaration, productId) {
  const violations = [];
  
  const REQUIRED_CATEGORIES = [
    'identity/authorization',
    'tenant/workspace_isolation',
    'lifecycle_state_transitions',
    'data_integrity',
    'audit/evidence',
    'privacy/consent',
    'product_specific_business_correctness',
  ];

  if (!invariantDeclaration || !invariantDeclaration.invariantCategories) {
    violations.push(
      `Product ${productId} has no invariantCategories defined. Must include: ${REQUIRED_CATEGORIES.join(', ')}`
    );
    return violations;
  }

  const declaredCategories = invariantDeclaration.invariantCategories;
  const missingCategories = REQUIRED_CATEGORIES.filter(cat => !declaredCategories.includes(cat));
  
  if (missingCategories.length > 0) {
    violations.push(
      `Product ${productId} is missing required invariant categories: ${missingCategories.join(', ')}`
    );
  }

  // Validate that each invariant's category is in the declared categories
  if (invariantDeclaration.invariants) {
    for (const invariant of invariantDeclaration.invariants) {
      if (!declaredCategories.includes(invariant.category)) {
        violations.push(
          `Invariant '${invariant.id}' has category '${invariant.category}' which is not in invariantCategories list`
        );
      }
    }
  }

  return violations;
}

export function runProductDomainInvariantCheck() {
  const registry = loadCanonicalRegistry(repoRoot);
  const activeBusinessProducts = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product' && product.metadata?.status === 'active')
    .map(([productId]) => productId)
    .sort();

  const violations = [];
  const coverage = [];

  for (const productId of activeBusinessProducts) {
    const productRoot = path.join(repoRoot, 'products', productId);
    if (!existsSync(productRoot)) {
      violations.push(`Missing product directory for ${productId}`);
      continue;
    }

    // Load invariant declaration if it exists
    const invariantDeclaration = loadInvariantDeclaration(productRoot);
    
    // Validate invariant-to-test mapping if declaration exists
    if (invariantDeclaration) {
      const mappingViolations = validateInvariantTestMapping(invariantDeclaration, productRoot);
      violations.push(...mappingViolations);
      
      // Validate invariant categories
      const categoryViolations = validateInvariantCategories(invariantDeclaration, productId);
      violations.push(...categoryViolations);
      
      // Validate test execution evidence
      const executionViolations = validateInvariantTestExecution(invariantDeclaration, productRoot, productId);
      violations.push(...executionViolations);
      
      coverage.push({
        productId,
        hasInvariantDeclaration: true,
        invariantCount: invariantDeclaration.invariants?.length || 0,
        mappingViolations: mappingViolations.length,
        categoryViolations: categoryViolations.length,
        executionViolations: executionViolations.length,
      });
    } else {
      // Fall back to filename-based heuristic if no declaration
      const javaTests = collectFiles(productRoot, (name) => name.endsWith('Test.java') || name.endsWith('IT.java'));
      const tsTests = collectFiles(productRoot, (name) => /\.(test|spec)\.(ts|tsx|js|jsx)$/.test(name));
      const invariantNamed = [...javaTests, ...tsTests].filter((file) => /invariant|domain|workflow|lifecycle/i.test(file));

      coverage.push({
        productId,
        hasInvariantDeclaration: false,
        javaTestCount: javaTests.length,
        tsTestCount: tsTests.length,
        invariantNamedTestCount: invariantNamed.length,
      });

      if (javaTests.length + tsTests.length === 0) {
        violations.push(`Product ${productId} has no executable tests to prove domain invariants`);
        continue;
      }

      if (invariantNamed.length === 0) {
        violations.push(`Product ${productId} has tests but no domain-invariant/workflow/lifecycle-oriented test coverage. Add domain-invariants.yaml for explicit invariant declaration.`);
      }
    }
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      coverage,
      violations,
    }, null, 2)}\n`,
    'utf8',
  );

  return {
    pass: violations.length === 0,
    violations,
    coverage,
  };
}

function main() {
  const result = runProductDomainInvariantCheck();
  if (!result.pass) {
    console.error('Product domain invariant check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Product domain invariant check passed.');
}

main();
