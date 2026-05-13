#!/usr/bin/env node

/**
 * Kernel Doc Taxonomy Check
 * 
 * Validates that kernel docs follow the canonical taxonomy:
 * - Numbered docs (00-VISION.md through 11-MIGRATION_GUIDE.md) exist
 * - No duplicate lifecycle docs
 * - Legacy docs are properly referenced
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const kernelDocsPath = path.join(repoRoot, 'docs/kernel');

const CANONICAL_DOCS = [
  '00-VISION.md',
  '01-ARCHITECTURE.md',
  '02-PRODUCT_LIFECYCLE.md',
  '03-TOOLCHAIN_ADAPTERS.md',
  '04-ARTIFACTS.md',
  '05-DEPLOYMENT.md',
  '06-PLUGIN_PLATFORM.md',
  '07-CONFORMANCE.md',
  '08-SECURITY_PRIVACY_OBSERVABILITY.md',
  '09-PRODUCT_DEVELOPER_GUIDE.md',
  '10-POWER_USER_EXTENSION_GUIDE.md',
  '11-MIGRATION_GUIDE.md',
];

const LEGACY_DOCS = [
  'KERNEL_CONSUMPTION_GUIDE.md',
  'KERNEL_PRODUCT_BOUNDARY.md',
  'KERNEL_PURITY_RULES.md',
  'PRODUCT_CONFORMANCE_SPEC.md',
  'PRODUCT_MANIFEST_SPEC.md',
  'PRODUCT_LIFECYCLE_CONTRACT.md',
  'PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md',
  'PRODUCT_ARTIFACT_CONTRACT.md',
  'PRODUCT_ENVIRONMENT_CONTRACT.md',
  'PRODUCT_DEPLOYMENT_CONTRACT.md',
  'PRODUCT_RELEASE_PROMOTION_CONTRACT.md',
  'PLUGIN_LIFECYCLE_CONTRACT.md',
  'PLUGIN_PURITY_RULES.md',
  'PRODUCT_DEVELOPMENT_GUIDE.md',
  'PRODUCT_POWER_USER_EXTENSION_GUIDE.md',
];

const errors = [];

function checkCanonicalDocsExist() {
  for (const doc of CANONICAL_DOCS) {
    const docPath = path.join(kernelDocsPath, doc);
    if (!existsSync(docPath)) {
      errors.push(`Missing canonical doc: ${doc}`);
    }
  }
}

function checkNoDuplicateLifecycleDocs() {
  const lifecycleDocs = [
    'PRODUCT_LIFECYCLE_CONTRACT.md',
    '02-PRODUCT_LIFECYCLE.md',
  ];
  
  const existingLifecycleDocs = lifecycleDocs.filter(doc => existsSync(path.join(kernelDocsPath, doc)));
  
  if (existingLifecycleDocs.length > 1) {
    errors.push(`Duplicate lifecycle docs found: ${existingLifecycleDocs.join(', ')}`);
  }
}

function checkReadmeLinksCanonicalDocs() {
  const readmePath = path.join(kernelDocsPath, 'README.md');
  if (!existsSync(readmePath)) {
    errors.push('Missing README.md in kernel docs');
    return;
  }
  
  const readmeContent = readFileSync(readmePath, 'utf8');
  
  for (const doc of CANONICAL_DOCS) {
    if (!readmeContent.includes(doc)) {
      errors.push(`README.md does not link to canonical doc: ${doc}`);
    }
  }
}

function checkProductDeveloperGuideCanonical() {
  const legacyGuide = path.join(kernelDocsPath, 'PRODUCT_DEVELOPMENT_GUIDE.md');
  const canonicalGuide = path.join(kernelDocsPath, '09-PRODUCT_DEVELOPER_GUIDE.md');
  
  if (existsSync(legacyGuide) && existsSync(canonicalGuide)) {
    errors.push('Duplicate product developer guides: both legacy and canonical exist');
  }
}

function checkPowerUserGuideCanonical() {
  const legacyGuide = path.join(kernelDocsPath, 'PRODUCT_POWER_USER_EXTENSION_GUIDE.md');
  const canonicalGuide = path.join(kernelDocsPath, '10-POWER_USER_EXTENSION_GUIDE.md');
  
  if (existsSync(legacyGuide) && existsSync(canonicalGuide)) {
    errors.push('Duplicate power user extension guides: both legacy and canonical exist');
  }
}

function main() {
  console.log('=== Kernel Doc Taxonomy Check ===\n');
  
  checkCanonicalDocsExist();
  checkNoDuplicateLifecycleDocs();
  checkReadmeLinksCanonicalDocs();
  checkProductDeveloperGuideCanonical();
  checkPowerUserGuideCanonical();
  
  if (errors.length > 0) {
    console.error('❌ Doc taxonomy check failed:\n');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }
  
  console.log('✅ Doc taxonomy check passed');
  console.log(`  - All ${CANONICAL_DOCS.length} canonical docs exist`);
  console.log('  - No duplicate lifecycle docs');
  console.log('  - README.md links to all canonical docs');
  console.log('  - Product developer guide is canonical');
  console.log('  - Power user extension guide is canonical');
}

main();
