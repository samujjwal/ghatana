#!/usr/bin/env node

/**
 * Merge generated package scripts into root package.json
 * 
 * Replaces product-specific scripts in package.json with generated ones from
 * config/generated/package-scripts.json. Generic platform scripts are preserved.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

const PACKAGE_JSON_PATH = join(repoRoot, 'package.json');
const GENERATED_SCRIPTS_PATH = join(repoRoot, 'config/generated/package-scripts.json');
const checkMode = process.argv.includes('--check');

// Generic platform scripts that should NOT be replaced by generated scripts
const GENERIC_SCRIPTS = new Set([
  'build',
  'build:platform',
  'dev',
  'test',
  'test:ui',
  'lint',
  'format',
  'clean',
  'typecheck',
  'typecheck:workspace',
  'check:deprecated-ui',
  'check:duplicate-packages',
  'check:platform-package-governance',
  'check:truth-surfaces',
  'check:tsconfig-strict',
  'check:production-readiness',
  'check:test-authenticity',
  'check:audit-todo-burndown',
  'check:jwt-policy',
  'check:license-policy',
  'check:cross-workspace-deps',
  'check:capability-schema-drift',
  'check:openapi-canonical',
  'check:production-stubs',
  'check:doc-truth',
  'check:product-registry',
  'check:domain-registry',
  'check:domain-boundaries',
  'check:deprecated-imports',
  'check:current-state-claims',
  'check:duplication-exceptions',
  'check:doc-claims-evidence',
  'check:circular-deps',
  'check:kernel-boundaries',
  'check:product-ui-contracts',
  'check:product-doc-taxonomy',
  'check:product-manifest-contracts',
  'check:product-ci-matrices',
  'check:product-workspace-registration',
  'check:product-scaffolder',
  'check:product-kernel-audit-progress',
  'generate:product-kernel-audit-progress',
  'check:security-workflow-coverage',
  'check:digital-marketing-root-docs',
  'check:bridge-compliance',
  'check:cleanup-gate',
  'check:data-access-contract',
  'check:observability-conformance',
  'check:finance-transaction-workflow-proof',
  'check:finance-lifecycle-readiness',
  'check:phr-lifecycle-readiness',
  'check:phr-lifecycle-pilot',
  'check:flashit-lifecycle-readiness',
  'check:yappc-platform-provider-readiness',
  'check:data-cloud-platform-provider-readiness',
  'check:dmos-boundary-workflow-coverage',
  'check:inmemory-policy-store-usage',
  'check:secret-default-credentials',
  'check:route-entitlement-contracts',
  'check:flashit-client-conformance',
  'check:local-dev-port-allocations',
  'check:runtime-template-conformance',
  'check:flashit-doc-content',
  'check:audited-ui-workflows',
  'check:audited-e2e-workflow',
  'check:audited-performance-workflows',
  'check:data-cloud-release-runtime-profile',
  'check:data-cloud-ui-a11y',
  'check:product-a11y-route-matrix',
  'check:i18n-conformance',
  'check:ai-governance-conformance',
  'check:runtime-failure-injection',
  'check:kernel-implementation-plan-coverage',
  'generate:wave2-product-quality-scorecard',
  'check:atomic-workflow-proof',
  'check:affected-product-strict-release-profile',
  'check:product-release-readiness',
  'check:openapi-release-quality',
  'check:release-gate',
  'check:design-system-conformance',
  'check:shared-product-shells',
  'check:shared-layout-primitives',
  'check:shared-ui-state-coverage',
  'check:workspace-source-aliases',
  'check:router-strategy',
  'check:product-package-metadata',
  'check:flashit-package-manager',
  'check:kernel-lifecycle-service',
  'check:kernel-api-contracts',
  'check:kernel-product-boundary-audit',
  'check:kernel-provider-mode',
  'check:data-cloud-platform-providers',
  'check:kernel-plugin-interactions',
  'check:plugin-interaction-broker',
  'check:product-interaction-contracts',
  'check:cross-product-interaction-boundaries',
  'check:interaction-runtime-truth',
  'check:product-interaction-broker',
  'check:interaction-performance',
  'check:cross-product-interaction-flows',
  'check:kernel-product-unit-provider-contracts',
  'check:kernel-lifecycle-truth',
  'check:agentic-lifecycle-action-contracts',
  'check:studio-kernel-api',
  'check:kernel-authoring-pipeline',
  'check:kernel-product-cli',
  'check:generated-artifact-validation-pipeline',
  'check:artifact-roundtrip',
  'check:studio-deep-interactions',
  'check:canvas-history',
  'check:builder-canonical-document',
  'check:builder-canvas-adapter',
  'check:studio-artifact-workflow-e2e',
  'check:studio-production-profile',
  'check:studio-production-profile:strict',
  'check:studio-workflow-persistence-contracts',
  'check:studio-source-acquisition-worker',
  'check:ds-generator-golden',
  'check:yappc-product-unit-intent-handoff',
  'check:yappc-artifact-intelligence-boundary',
  'scaffold:product',
  'test:eslint-rules',
  'prepare',
  'generate:data-cloud-feature-gates',
  'check:data-cloud-feature-gates',
  'generate:data-cloud-architecture-scorecard',
  'check:data-cloud-architecture-scorecard',
  'check:data-cloud-ui-contracts',
  'check:data-cloud-sdk-drift',
  'check:data-cloud-runbook-smoke',
  'check:platform-product-boundaries',
  'check:architecture-boundaries',
  'check:deprecated-packages',
  'check:orphan-modules',
  'check:product-shape-capability-matrix',
  'check:product-registry-drift',
  'check:digital-marketing-lifecycle-pilot',
  'check:toolchain-adapter-contracts',
  'check:java-adapter-conformance',
  'check:typescript-web-adapter-conformance',
  'check:rust-adapter-conformance',
  'check:python-adapter-conformance',
  'check:polyglot-product-fixture',
  'check:affected-surface-execution',
  'check:lifecycle-explain-recover',
  'check:lifecycle-run-history',
  'check:i18n-conformance',
  'check:product-feature-completeness',
  'check:product-release-readiness',
  'check:dmos-production-workflows',
  'check:phr-production-workflows',
  'check:release-rollback-drill',
  'check:studio-lifecycle-control-plane',
  'check:interaction-durable-event-provider',
  'check:evidence-retention-policy',
  'check:production-deployment-provenance',
  'check:runtime-dependency-failure-injection',
  'check:atomic-workflow-proof',
  'check:ai-governance-behavioral-proof',
  'check:product-artifact-contracts',
  'check:product-deployment-contracts',
  'check:product-environment-contracts',
  'check:aggregate-gate-integrity',
  'check:data-cloud-release-gate',
  'check:release-gate',
  'check:kernel-implementation-plan-coverage',
  'check:phase0',
  'check:phase1',
  'check:phase2',
  'check:phase3',
  'check:phase4',
  'check:phase5',
  'check:phase6',
  'check:phase7',
  'check:phase8',
  'check:world-class-platform-readiness',
]);

function main() {
  console.log('=== Merging generated package scripts into package.json ===\n');

  try {
    // Read package.json
    const packageJson = JSON.parse(readFileSync(PACKAGE_JSON_PATH, 'utf8'));
    
    // Read generated scripts
    const generatedScripts = JSON.parse(readFileSync(GENERATED_SCRIPTS_PATH, 'utf8'));
    
    console.log(`Found ${Object.keys(generatedScripts).length} generated scripts`);
    console.log(`Preserving ${GENERIC_SCRIPTS.size} generic platform scripts\n`);
    
    // Remove product-specific scripts (those not in GENERIC_SCRIPTS)
    const currentScripts = packageJson.scripts || {};
    const newScripts = {};
    
    // Preserve generic scripts
    for (const [key, value] of Object.entries(currentScripts)) {
      if (GENERIC_SCRIPTS.has(key)) {
        newScripts[key] = value;
      }
    }
    
    // Add generated scripts
    let addedCount = 0;
    let replacedCount = 0;
    
    for (const [key, value] of Object.entries(generatedScripts)) {
      if (GENERIC_SCRIPTS.has(key)) {
        console.log(`  Skipping generic script: ${key}`);
        continue;
      }
      
      if (currentScripts[key]) {
        console.log(`  Replacing: ${key}`);
        replacedCount++;
      } else {
        console.log(`  Adding: ${key}`);
        addedCount++;
      }
      
      newScripts[key] = value;
    }
    
    packageJson.scripts = newScripts;
    const nextPackageJson = JSON.stringify(packageJson, null, 2) + '\n';
    const currentPackageJson = readFileSync(PACKAGE_JSON_PATH, 'utf8');

    if (checkMode) {
      if (currentPackageJson !== nextPackageJson) {
        throw new Error('package.json product scripts are stale. Run: node scripts/generate-product-registry-artifacts.mjs');
      }
    } else {
      writeFileSync(PACKAGE_JSON_PATH, nextPackageJson);
    }
    
    console.log(`\n✓ Merged ${addedCount} new scripts and replaced ${replacedCount} existing scripts`);
    console.log(`✓ Total scripts in package.json: ${Object.keys(newScripts).length}`);
    
  } catch (error) {
    console.error('\n✗ Merge failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
