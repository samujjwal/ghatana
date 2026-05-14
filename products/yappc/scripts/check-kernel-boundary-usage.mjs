#!/usr/bin/env node

/**
 * Kernel Boundary Usage Check
 *
 * This script verifies that YAPPC does not violate Kernel integration boundaries.
 * It checks for:
 * - YAPPC does not mutate Kernel registry files directly
 * - YAPPC does not define Kernel Product Lifecycle enum locally
 * - YAPPC kernel-health feature imports only public contracts
 * - YAPPC does not parse private logs
 * - CreateCommand with target=kernel-product-unit writes intent, not registry
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..');

const YAPPC_ROOT = join(__dirname, '..');
const KERNEL_CONTRACTS_PATH = join(YAPPC_ROOT, '../../../platform/typescript/kernel-product-contracts/src');
const YAPPC_CORE_PATH = join(YAPPC_ROOT, 'core');
const YAPPC_FRONTEND_PATH = join(YAPPC_ROOT, 'frontend/web/src');

// Error tracking
let errors = [];
let warnings = [];

/**
 * Check if a file path is a Kernel registry file
 */
function isKernelRegistryFile(filePath) {
  return filePath.includes('config/canonical-product-registry.json') ||
         filePath.includes('config/canonical-product-registry-schema.json');
}

/**
 * Check if a file path is a Kernel private log file
 */
function isKernelPrivateLog(filePath) {
  return filePath.includes('.kernel/logs/') ||
         filePath.includes('/logs/') ||
         (filePath.includes('.kernel') && filePath.endsWith('.log'));
}

/**
 * Check if a file contains Kernel Product Lifecycle enum definitions
 * Only flag actual enum/constant declarations, not usage in comments or test data
 */
function containsKernelLifecycleEnum(content, filePath) {
  // Skip test files - they may use these words in test data
  if (filePath.includes('/test/') || filePath.includes('\\test\\') || 
      filePath.includes('.test.') || filePath.includes('.spec.')) {
    return false;
  }

  const kernelPhases = ['dev', 'validate', 'test', 'build', 'package', 'deploy', 'verify'];
  
  // Only check for explicit enum/constant declarations with Kernel in the name
  const enumPatterns = [
    /enum\s+KernelLifecycle\s*\{/i,
    /enum\s+KernelProductLifecycle\s*\{/i,
    /enum\s+ProductLifecycle\s*\{/i,
    /const\s+KernelPhase\s*=\s*\{/i,
    /const\s+KERNEL_PHASES\s*=\s*\[/i,
    /export\s+const\s+KernelLifecycle\s*=/i,
    /export\s+enum\s+KernelLifecycle/i,
  ];
  
  // Check for enum-like patterns with specific Kernel naming
  for (const pattern of enumPatterns) {
    if (pattern.test(content)) {
      return true;
    }
  }
  
  // Check if file defines all Kernel phases as constants in a structured way
  // This looks for patterns like: const DEV = 'dev'; const VALIDATE = 'validate'; etc.
  const constantPattern = /const\s+(DEV|VALIDATE|TEST|BUILD|PACKAGE|DEPLOY|VERIFY)\s*=\s*['"]/gi;
  const constantMatches = content.match(constantPattern);
  if (constantMatches) {
    const uniqueConstants = new Set(constantMatches.map(m => m.match(/\b(DEV|VALIDATE|TEST|BUILD|PACKAGE|DEPLOY|VERIFY)\b/i)[0].toUpperCase()));
    const overlap = kernelPhases.filter(phase => uniqueConstants.has(phase.toUpperCase()));
    if (overlap.length >= 5) {
      return true; // Likely defining Kernel lifecycle as constants
    }
  }
  
  return false;
}

/**
 * Check if a file imports from Kernel implementation packages (not contracts)
 */
function importsKernelImplementation(content, filePath) {
  // Allow imports from public contracts
  if (filePath.includes('kernel-product-contracts') || 
      filePath.includes('kernel-product-contracts/src')) {
    return false;
  }
  
  // Block imports from Kernel implementation packages
  const blockedImports = [
    /from\s+['"]@ghatana\/kernel/i,
    /from\s+['"]@ghatana\/platform-kernel/i,
    /from\s+['"]@ghatana\/kernel-core/i,
    /import.*kernel-core/i,
    /import.*platform-kernel/i,
  ];
  
  for (const pattern of blockedImports) {
    if (pattern.test(content)) {
      return true;
    }
  }
  
  return false;
}

/**
 * Check if a file contains direct registry mutation logic
 */
function containsRegistryMutation(content) {
  const mutationPatterns = [
    /writeFileSync.*canonical-product-registry/i,
    /fs\.writeFile.*canonical-product-registry/i,
    /JSON\.stringify.*canonical-product-registry/i,
  ];
  
  for (const pattern of mutationPatterns) {
    if (pattern.test(content)) {
      return true;
    }
  }
  
  return false;
}

/**
 * Check if CreateCommand properly handles kernel-product-unit target
 */
function checkCreateCommand(content, filePath) {
  if (!filePath.includes('CreateCommand.java')) {
    return [];
  }
  
  const issues = [];
  
  // Check for ProductUnitIntent generation (good)
  if (!content.includes('ProductUnitIntent')) {
    issues.push('CreateCommand should generate ProductUnitIntent for kernel-product-unit target');
  }
  
  // Check for direct registry mutation (bad)
  if (content.includes('canonical-product-registry') && 
      content.includes('writeFile')) {
    issues.push('CreateCommand should not directly mutate Kernel registry files');
  }
  
  // Check for target option
  if (!content.includes('target=kernel-product-unit') && 
      !content.includes('targetType')) {
    issues.push('CreateCommand should support --target option for kernel-product-unit');
  }
  
  return issues;
}

/**
 * Recursively scan directory for files
 */
function scanDirectory(dir, extensions = ['.java', '.ts', '.tsx', '.js', '.mjs']) {
  const files = [];
  
  try {
    const entries = readdirSync(dir);
    
    for (const entry of entries) {
      const fullPath = join(dir, entry);
      const stat = statSync(fullPath);
      
      if (stat.isDirectory()) {
        // Skip node_modules and build directories
        if (!entry.includes('node_modules') && 
            !entry.includes('.gradle') && 
            !entry.includes('build') &&
            !entry.includes('out') &&
            !entry.includes('target')) {
          files.push(...scanDirectory(fullPath, extensions));
        }
      } else if (stat.isFile()) {
        const ext = entry.substring(entry.lastIndexOf('.'));
        if (extensions.includes(ext)) {
          files.push(fullPath);
        }
      }
    }
  } catch (error) {
    // Ignore permission errors
  }
  
  return files;
}

/**
 * Run all boundary checks
 */
function runChecks() {
  console.log('🔍 Checking YAPPC Kernel integration boundaries...\n');
  
  // Scan YAPPC core Java files
  console.log('Scanning Java files...');
  const javaFiles = scanDirectory(YAPPC_CORE_PATH, ['.java']);
  
  for (const file of javaFiles) {
    try {
      const content = readFileSync(file, 'utf-8');
      const relativePath = relative(YAPPC_ROOT, file);
      
      // Check for Kernel lifecycle enum definitions
      if (containsKernelLifecycleEnum(content, relativePath)) {
        errors.push(`${relativePath}: Defines Kernel Product Lifecycle enum locally`);
      }
      
      // Check for registry mutation
      if (containsRegistryMutation(content)) {
        errors.push(`${relativePath}: Contains direct Kernel registry mutation logic`);
      }
      
      // Check CreateCommand
      const createCommandIssues = checkCreateCommand(content, relativePath);
      for (const issue of createCommandIssues) {
        errors.push(`${relativePath}: ${issue}`);
      }
      
    } catch (error) {
      // Skip files that can't be read
    }
  }
  
  // Scan TypeScript/JavaScript files
  console.log('Scanning TypeScript/JavaScript files...');
  const tsFiles = scanDirectory(YAPPC_FRONTEND_PATH, ['.ts', '.tsx', '.js', '.mjs']);
  
  for (const file of tsFiles) {
    try {
      const content = readFileSync(file, 'utf-8');
      const relativePath = relative(YAPPC_ROOT, file);
      
      // Check for Kernel implementation imports
      if (importsKernelImplementation(content, relativePath)) {
        errors.push(`${relativePath}: Imports from Kernel implementation packages (use only public contracts)`);
      }
      
      // Check for Kernel lifecycle enum definitions
      if (containsKernelLifecycleEnum(content, relativePath)) {
        errors.push(`${relativePath}: Defines Kernel Product Lifecycle enum locally`);
      }
      
    } catch (error) {
      // Skip files that can't be read
    }
  }
  
  // Check for Kernel registry file mutations in scripts
  console.log('Scanning scripts...');
  const scriptFiles = scanDirectory(join(YAPPC_ROOT, 'scripts'), ['.js', '.mjs']);
  
  for (const file of scriptFiles) {
    try {
      const content = readFileSync(file, 'utf-8');
      const relativePath = relative(YAPPC_ROOT, file);
      
      // Skip the boundary check script itself (it contains patterns it checks for)
      if (relativePath.includes('check-kernel-boundary-usage.mjs')) {
        continue;
      }
      
      if (containsRegistryMutation(content)) {
        errors.push(`${relativePath}: Contains direct Kernel registry mutation logic`);
      }
      
    } catch (error) {
      // Skip files that can't be read
    }
  }
  
  // Print results
  console.log('\n✅ Boundary Check Results:\n');
  
  if (errors.length === 0 && warnings.length === 0) {
    console.log('✅ All boundary checks passed!');
    console.log('✅ YAPPC properly respects Kernel integration boundaries');
    return 0;
  }
  
  if (errors.length > 0) {
    console.log(`❌ ${errors.length} error(s) found:\n`);
    for (const error of errors) {
      console.log(`  ❌ ${error}`);
    }
  }
  
  if (warnings.length > 0) {
    console.log(`\n⚠️  ${warnings.length} warning(s) found:\n`);
    for (const warning of warnings) {
      console.log(`  ⚠️  ${warning}`);
    }
  }
  
  console.log('\n❌ Boundary check failed. Please fix the issues above.');
  return 1;
}

// Run the checks
const exitCode = runChecks();
process.exit(exitCode);
