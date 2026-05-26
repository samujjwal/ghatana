#!/usr/bin/env node

/**
 * KERNEL-P1-002: No duplicate lifecycle runtimes
 * 
 * Ensures products use Kernel lifecycle where required and do not have duplicate
 * lifecycle execution code in products.
 * 
 * @doc.type script
 * @doc.purpose Detect duplicate product lifecycle runtimes
 * @doc.layer scripts
 */

import { readFileSync } from 'fs';
import { readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..');

// Patterns that indicate duplicate lifecycle code
const DUPLICATE_LIFECYCLE_PATTERNS = [
  // Custom lifecycle orchestrators
  { pattern: /class.*LifecycleOrchestrator/i, name: 'custom lifecycle orchestrator' },
  { pattern: /class.*ProductLifecycleManager/i, name: 'custom product lifecycle manager' },
  { pattern: /class.*ReleaseManager/i, name: 'custom release manager' },
  { pattern: /class.*DeploymentOrchestrator/i, name: 'custom deployment orchestrator' },
  
  // Lifecycle execution paths that should use Kernel
  { pattern: /executeLifecycle/i, name: 'executeLifecycle method' },
  { pattern: /runProductLifecycle/i, name: 'runProductLifecycle method' },
  { pattern: /handleLifecycleTransition/i, name: 'handleLifecycleTransition method' },
  
  // Duplicate state management
  { pattern: /enum.*LifecycleState/i, name: 'custom lifecycle state enum' },
  { pattern: /class.*LifecycleState/i, name: 'custom lifecycle state class' },
];

// Allowed patterns (Kernel lifecycle is OK)
const ALLOWED_PATTERNS = [
  /platform-kernel/, // Kernel module
  /platform:java:kernel/, // Kernel dependency
  /KernelLifecycle/, // Kernel lifecycle
  /kernel-lifecycle/, // Kernel lifecycle package
];

const VIOLATIONS = [];

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    
    lines.forEach((line, index) => {
      // Skip comments
      if (line.trim().startsWith('//') || line.trim().startsWith('*')) {
        return;
      }
      
      // Check for duplicate lifecycle patterns
      for (const { pattern, name } of DUPLICATE_LIFECYCLE_PATTERNS) {
        if (pattern.test(line)) {
          // Check if it's in a Kernel module (allowed)
          let isAllowed = false;
          for (const allowedPattern of ALLOWED_PATTERNS) {
            if (allowedPattern.test(filePath) || allowedPattern.test(line)) {
              isAllowed = true;
              break;
            }
          }
          
          if (!isAllowed) {
            VIOLATIONS.push({
              file: relative(process.cwd(), filePath),
              line: index + 1,
              pattern: name,
              content: line.trim()
            });
          }
        }
      }
    });
  } catch (error) {
    // Skip files that can't be read
  }
}

function scanDirectory(dir, extensions = ['.java', '.ts', '.tsx', '.js', '.mjs']) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, build, and test directories
      if (
        entry.name === 'node_modules' ||
        entry.name === 'build' ||
        entry.name === 'dist' ||
        entry.name === 'target' ||
        entry.name === '.git' ||
        entry.name === '__tests__' ||
        entry.name === 'test' ||
        entry.name === 'tests' ||
        entry.name === 'platform-kernel' // Skip Kernel module itself
      ) {
        continue;
      }
      scanDirectory(fullPath, extensions);
    } else if (entry.isFile()) {
      const ext = entry.name.substring(entry.name.lastIndexOf('.'));
      if (extensions.includes(ext)) {
        scanFile(fullPath);
      }
    }
  }
}

function main() {
  const args = process.argv.slice(2);
  const targetDir = args[0] || join(process.cwd(), 'products');
  
  console.log(`Scanning ${targetDir} for duplicate lifecycle runtimes...`);
  
  scanDirectory(targetDir);
  
  if (VIOLATIONS.length === 0) {
    console.log('✓ No duplicate lifecycle runtimes found');
    process.exit(0);
  } else {
    console.error(`\n✗ Found ${VIOLATIONS.length} duplicate lifecycle runtime(s):\n`);
    
    VIOLATIONS.forEach(({ file, line, pattern, content }) => {
      console.error(`  ${file}:${line} - ${pattern}`);
      console.error(`    ${content}\n`);
    });
    
    console.error('KERNEL-P1-002: Products must use Kernel lifecycle where required.');
    console.error('No duplicate lifecycle execution code should exist in products.');
    
    process.exit(1);
  }
}

main();
