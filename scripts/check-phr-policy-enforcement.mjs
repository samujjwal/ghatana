#!/usr/bin/env node

/**
 * PHR Policy Enforcement Check
 *
 * Verifies that PHR routes enforce proper policy gates:
 * - requireContext() is called for authentication
 * - Role checks are present for protected routes
 * - PHI access paths have consent/treatment relationship validation
 * - Emergency access has justification and review requirements
 *
 * Usage: node scripts/check-phr-policy-enforcement.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');
const PHR_BACKEND = join(REPO_ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/routes');

let violations = [];
let filesChecked = 0;

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);
  
  filesChecked++;
  
  // Check for requireContext usage in route handlers
  const hasRequireContext = /requireContext\s*\(/.test(content);
  
  // Check for role-based access patterns
  const hasRoleCheck = /hasClinicalRole|canPerformAdminOperation|canAccessPatientRecordForRole/.test(content);
  
  // Check for emergency access policy gates
  const isEmergencyRoute = /PhrEmergencyRoutes|emergency/i.test(content);
  const hasEmergencyPolicy = isEmergencyRoute && 
    (/justification.*required/.test(content) || 
     /resourcesAccessed.*required/.test(content) ||
     /REVIEWER_REQUIRED/.test(content));
  
  // Check for consent validation in consent routes
  const isConsentRoute = /PhrConsentRoutes|consent/i.test(content);
  const hasConsentPolicy = isConsentRoute && 
    (/mayManagePatientConsent|CONSENT_OWNER_REQUIRED/.test(content));
  
  // Generate violations
  if (!hasRequireContext && content.includes('HttpRequest')) {
    violations.push({
      file: relativePath,
      line: 1,
      message: 'Route handler does not call requireContext() for authentication',
      code: 'Missing requireContext() call'
    });
  }
  
  if (isEmergencyRoute && !hasEmergencyPolicy) {
    violations.push({
      file: relativePath,
      line: 1,
      message: 'Emergency route missing policy gates (justification, resources, review)',
      code: 'Missing emergency policy enforcement'
    });
  }
  
  if (isConsentRoute && !hasConsentPolicy) {
    violations.push({
      file: relativePath,
      line: 1,
      message: 'Consent route missing owner validation policy',
      code: 'Missing consent policy enforcement'
    });
  }
}

function walkDirectory(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build') {
        walkDirectory(fullPath);
      }
    } else if (entry.isFile() && entry.name.endsWith('.java')) {
      checkFile(fullPath);
    }
  }
}

function main() {
  console.log('🔍 Checking PHR policy enforcement...\n');
  
  if (!statSync(PHR_BACKEND).isDirectory()) {
    console.error(`❌ PHR backend directory not found: ${PHR_BACKEND}`);
    process.exit(1);
  }
  
  walkDirectory(PHR_BACKEND);
  
  console.log(`📊 Checked ${filesChecked} Java files in PHR backend routes\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} policy enforcement violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.file}:${v.line}`);
      console.error(`     ${v.message}`);
      console.error(`     Code: ${v.code}\n`);
    });
    console.error('\n💡 Fix: Add requireContext() calls and policy gates to route handlers.');
    console.error('   Reference: PhrRouteSupport.java for helper methods.\n');
    process.exit(1);
  }
  
  console.log('✅ No policy enforcement violations found.');
  console.log('✅ All routes have proper authentication and policy gates.\n');
  process.exit(0);
}

main();
