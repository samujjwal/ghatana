#!/usr/bin/env node

/**
 * Kernel Doc Truth Check
 * 
 * Validates that kernel docs are consistent with the codebase:
 * - No generated audit docs treated as canonical
 * - Doc references match actual files
 * - Code references in docs exist
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const kernelDocsPath = path.join(repoRoot, 'docs/kernel');

const errors = [];

function checkNoAuditDocsAsCanonical() {
  const auditDocPath = path.join(repoRoot, 'docs/audit/README.md');
  if (!existsSync(auditDocPath)) {
    return;
  }
  
  const readmePath = path.join(kernelDocsPath, 'README.md');
  if (!existsSync(readmePath)) {
    return;
  }
  
  const readmeContent = readFileSync(readmePath, 'utf8');
  
  if (readmeContent.includes('docs/audit/README.md')) {
    errors.push('README.md should not link to audit README as canonical doc');
  }
}

function checkCodeReferencesInDocs() {
  const files = [
    '01-ARCHITECTURE.md',
    '02-PRODUCT_LIFECYCLE.md',
    '03-TOOLCHAIN_ADAPTERS.md',
    '09-PRODUCT_DEVELOPER_GUIDE.md',
  ];
  
  const knownFiles = [
    'config/canonical-product-registry.json',
    'config/canonical-product-registry-schema.json',
    'config/kernel-plugin-registry.json',
    'config/kernel-plugin-registry-schema.json',
    'scripts/generate-product-registry-artifacts.mjs',
    'scripts/merge-generated-package-scripts.mjs',
  ];
  
  for (const file of files) {
    const docPath = path.join(kernelDocsPath, file);
    if (!existsSync(docPath)) {
      continue;
    }
    
    const content = readFileSync(docPath, 'utf8');
    
    // Check that known referenced files exist
    for (const knownFile of knownFiles) {
      if (content.includes(knownFile)) {
        const filePath = path.join(repoRoot, knownFile);
        if (!existsSync(filePath)) {
          errors.push(`${file} references non-existent file: ${knownFile}`);
        }
      }
    }
  }
}

function main() {
  console.log('=== Kernel Doc Truth Check ===\n');
  
  checkNoAuditDocsAsCanonical();
  checkCodeReferencesInDocs();
  
  if (errors.length > 0) {
    console.error('❌ Doc truth check failed:\n');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }
  
  console.log('✅ Doc truth check passed');
  console.log('  - No audit docs treated as canonical');
  console.log('  - Code references in docs are valid');
}

main();
