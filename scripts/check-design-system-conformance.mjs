#!/usr/bin/env node

/**
 * Design-System Token Usage Check
 * 
 * Validates that product UIs use design-system tokens instead of hardcoded values.
 * Uses the canonical product registry to determine which UI packages to check.
 * Replaces permanent allowlists with declarative product registration.
 * 
 * Usage: node scripts/check-design-system-conformance.mjs
 */

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function loadCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    throw new Error('Canonical product registry not found');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function getProductUiPaths(registry) {
  const uiPaths = [];
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    const surfaces = product.surfaces || [];
    const uiSurfaces = surfaces.filter(s => s.type === 'web' || s.type === 'mobile');
    
    for (const surface of uiSurfaces) {
      if (surface.path) {
        uiPaths.push(`${surface.path}/src`);
      }
    }
  }
  
  return uiPaths;
}

function findFiles(dirPath, extensions) {
  const files = [];
  
  if (!existsSync(dirPath)) {
    return files;
  }
  
  const entries = readdirSync(dirPath, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);
    
    if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules') {
      files.push(...findFiles(fullPath, extensions));
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name);
      if (extensions.includes(ext)) {
        files.push(fullPath);
      }
    }
  }
  
  return files;
}

const hardcodedValuePattern = /#[0-9a-fA-F]{3,8}\b|rgba?\(/;

function main() {
  console.log('=== Design-System Token Usage Check ===\n');
  
  try {
    const registry = loadCanonicalRegistry();
    const uiPaths = getProductUiPaths(registry);
    
    if (uiPaths.length === 0) {
      console.log('No UI surfaces found in registry');
      process.exit(0);
    }
    
    console.log(`Checking ${uiPaths.length} UI surfaces from registry\n`);
    
    const files = [];
    for (const uiPath of uiPaths) {
      const fullPath = path.join(repoRoot, uiPath);
      const foundFiles = findFiles(fullPath, ['.tsx', '.css']);
      files.push(...foundFiles);
    }
    
    const violations = [];
    
    for (const file of files) {
      const content = readFileSync(file, 'utf8');
      const hasHardcodedValue = hardcodedValuePattern.test(content);
      
      if (hasHardcodedValue) {
        const relativePath = path.relative(repoRoot, file);
        violations.push(`${relativePath}: hardcoded style value detected; prefer tokens from @ghatana/tokens`);
      }
    }
    
    if (violations.length > 0) {
      console.error('✗ Design-system conformance violations:\n');
      for (const violation of violations) {
        console.error(`  - ${violation}`);
      }
      process.exit(1);
    }
    
    console.log(`✓ Checked ${files.length} files`);
    console.log('✓ Design-system token usage validation passed');
    process.exit(0);
  } catch (error) {
    console.error('✗ Design-system conformance check failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
