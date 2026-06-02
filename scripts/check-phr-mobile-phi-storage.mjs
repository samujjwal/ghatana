#!/usr/bin/env node

/**
 * PHR Mobile PHI Storage Safety Check (MOB-02)
 *
 * Enforces that PHI modules use Kernel EncryptedPhiCacheAdapter instead of direct AsyncStorage.
 * Direct AsyncStorage.setItem/getItem with PHI must fail CI.
 *
 * Usage: node scripts/check-phr-mobile-phi-storage.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');
const MOBILE_SRC = join(REPO_ROOT, 'products/phr/apps/mobile/src');

// PHI-related patterns that must use encrypted storage
const PHI_PATTERNS = [
  /\bpatient\b/i,
  /\bmedical\b/i,
  /\bhealth\b/i,
  /\bcondition\b/i,
  /\bmedication\b/i,
  /\bdiagnosis\b/i,
  /\btreatment\b/i,
  /\bconsent\b/i,
  /\brecord\b/i,
  /\bobservation\b/i,
  /\blab\b/i,
  /\bimmunization\b/i,
  /\bdocument\b/i,
  /\bprofile\b/i,
  /\bemergency\b/i,
  /\bvital\b/i,
  /\bsymptom\b/i,
  /\ballergy\b/i,
];

// Files that are allowed to use AsyncStorage directly (Kernel adapter implementations)
const ALLOWED_DIRECT_ASYNCSTORAGE = [
  'services/EncryptedPhiCacheAdapter.ts', // Kernel adapter implementation - allowed to use AsyncStorage
  'services/__tests__/EncryptedPhiCacheAdapter.test.ts',
  'services/__tests__/offlineStore.test.ts',
];

let violations = [];
let filesChecked = 0;

function isPhiFile(filePath) {
  const relativePath = relative(MOBILE_SRC, filePath);
  
  // Skip test files
  if (relativePath.includes('__tests__')) return false;
  
  // Skip allowed files - exact match for implementation file
  if (ALLOWED_DIRECT_ASYNCSTORAGE.some(allowed => relativePath.endsWith(allowed))) {
    return false;
  }
  
  // Check if file is in a PHI-related directory
  const phiDirectories = [
    'screens',
    'services',
    'components',
  ];
  
  return phiDirectories.some(dir => relativePath.startsWith(dir));
}

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);
  
  // Skip the implementation file entirely - it's allowed to use AsyncStorage
  // Normalize path separators for cross-platform compatibility
  const normalizedPath = relativePath.replace(/\\/g, '/');
  if (normalizedPath.includes('EncryptedPhiCacheAdapter.ts') && !normalizedPath.includes('__tests__')) {
    return;
  }
  
  filesChecked++;
  
  // Check for direct AsyncStorage.setItem
  const setItemPattern = /AsyncStorage\.setItem\s*\(/g;
  const setItemMatches = content.match(setItemPattern);
  
  if (setItemMatches) {
    // Check if this file imports Kernel EncryptedPhiCacheAdapter
    const hasKernelImport = /from\s+['"].*kernel.*MobilePrivacyPlugin['"]|import.*EncryptedPhiCacheAdapter/.test(content);
    const hasLocalImport = /from\s+['"].*EncryptedPhiCacheAdapter['"]|import.*EncryptedPhiCacheAdapter/.test(content);
    
    // If it has Kernel adapter import, check if all setItem calls are through it
    if (hasKernelImport || hasLocalImport) {
      // Check for direct AsyncStorage.setItem that's not inside the adapter
      const lines = content.split('\n');
      lines.forEach((line, index) => {
        if (line.includes('AsyncStorage.setItem') && !line.includes('EncryptedPhiCacheAdapter')) {
          violations.push({
            file: relativePath,
            line: index + 1,
            message: 'Direct AsyncStorage.setItem found in file with EncryptedPhiCacheAdapter import',
            code: line.trim(),
          });
        }
      });
    } else {
      // No Kernel adapter import - check if this is a PHI file
      if (isPhiFile(filePath)) {
        violations.push({
          file: relativePath,
          line: 1,
          message: 'PHI-related file uses AsyncStorage without Kernel EncryptedPhiCacheAdapter',
          code: 'File imports AsyncStorage but not EncryptedPhiCacheAdapter',
        });
      }
    }
  }
  
  // Check for direct AsyncStorage.getItem in PHI files
  const getItemPattern = /AsyncStorage\.getItem\s*\(/g;
  const getItemMatches = content.match(getItemPattern);
  
  if (getItemMatches && isPhiFile(filePath)) {
    const hasKernelImport = /from\s+['"].*kernel.*MobilePrivacyPlugin['"]|import.*EncryptedPhiCacheAdapter/.test(content);
    const hasLocalImport = /from\s+['"].*EncryptedPhiCacheAdapter['"]|import.*EncryptedPhiCacheAdapter/.test(content);
    if (!hasKernelImport && !hasLocalImport) {
      violations.push({
        file: relativePath,
        line: 1,
        message: 'PHR-related file uses AsyncStorage.getItem without Kernel EncryptedPhiCacheAdapter',
        code: 'File uses AsyncStorage.getItem but not EncryptedPhiCacheAdapter',
      });
    }
  }
}

function walkDirectory(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules and .git
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build') {
        walkDirectory(fullPath);
      }
    } else if (entry.isFile() && (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx'))) {
      checkFile(fullPath);
    }
  }
}

function main() {
  console.log('🔍 Checking PHR mobile PHI storage safety (MOB-02)...\n');
  
  if (!statSync(MOBILE_SRC).isDirectory()) {
    console.error(`❌ Mobile source directory not found: ${MOBILE_SRC}`);
    process.exit(1);
  }
  
  walkDirectory(MOBILE_SRC);
  
  console.log(`📊 Checked ${filesChecked} TypeScript files in mobile source\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} PHI storage violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.file}:${v.line}`);
      console.error(`     ${v.message}`);
      console.error(`     Code: ${v.code}\n`);
    });
    console.error('\n💡 Fix: Import and use Kernel EncryptedPhiCacheAdapter instead of direct AsyncStorage for PHI data.');
    console.error('   Reference: platform/typescript/kernel-product-contracts/src/privacy/MobilePrivacyPlugin.ts\n');
    process.exit(1);
  }
  
  console.log('✅ No PHI storage violations found.');
  console.log('✅ All PHI data uses Kernel EncryptedPhiCacheAdapter.\n');
  process.exit(0);
}

main();
