#!/usr/bin/env node

/**
 * Check Dependency Version Alignment Across Product UIs
 * 
 * Validates that critical dependencies are aligned across all product UI packages.
 * This prevents version drift and ensures consistent behavior across products.
 * 
 * Usage: node scripts/check-dependency-alignment.mjs
 */

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function readJson(absolutePath) {
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

function loadCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    throw new Error('Canonical product registry not found');
  }
  return readJson(registryPath);
}

function findPackageJsons(dirPath) {
  const packageJsons = [];
  
  if (!existsSync(dirPath)) {
    return packageJsons;
  }
  
  const entries = readdirSync(dirPath, { withFileTypes: true });
  
  // Check if this directory has a package.json
  const localPackageJson = path.join(dirPath, 'package.json');
  if (existsSync(localPackageJson)) {
    packageJsons.push(localPackageJson);
  }
  
  // Recursively search subdirectories
  for (const entry of entries) {
    if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules') {
      const subPath = path.join(dirPath, entry.name);
      packageJsons.push(...findPackageJsons(subPath));
    }
  }
  
  return packageJsons;
}

function loadProductPackageJsons() {
  const registry = loadCanonicalRegistry();
  const packageJsons = {};
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    const pnpmPackages = product.pnpmPackages || [];
    
    for (const pkgPath of pnpmPackages) {
      const fullPath = path.join(repoRoot, pkgPath);
      const foundPackageJsons = findPackageJsons(fullPath);
      
      for (const packageJsonPath of foundPackageJsons) {
        try {
          const pkg = readJson(packageJsonPath);
          const relativePath = path.relative(repoRoot, packageJsonPath);
          packageJsons[relativePath] = pkg;
        } catch (error) {
          // Silently skip packages that can't be loaded
        }
      }
    }
  }
  
  return packageJsons;
}

// Critical dependencies that should be aligned across all UI packages
const CRITICAL_DEPENDENCIES = [
  'react',
  'react-dom',
  'react-router',
  'react-router-dom',
  '@tanstack/react-query',
  'jotai',
  'zod',
  'typescript',
  'vite',
  'vitest',
  '@playwright/test',
  'tailwindcss',
  'eslint',
  'prettier'
];

function checkDependencyAlignment(packageJsons) {
  const violations = [];
  const dependencyVersions = {};
  
  // Collect all versions for each critical dependency
  for (const [pkgPath, pkg] of Object.entries(packageJsons)) {
    const allDeps = {
      ...(pkg.dependencies || {}),
      ...(pkg.devDependencies || {}),
      ...(pkg.peerDependencies || {})
    };
    
    for (const dep of CRITICAL_DEPENDENCIES) {
      if (allDeps[dep]) {
        if (!dependencyVersions[dep]) {
          dependencyVersions[dep] = new Map();
        }
        dependencyVersions[dep].set(pkgPath, allDeps[dep]);
      }
    }
  }
  
  // Check for version mismatches
  for (const [dep, versions] of Object.entries(dependencyVersions)) {
    const versionSet = new Set(versions.values());
    
    if (versionSet.size > 1) {
      violations.push({
        dependency: dep,
        versions: Array.from(versionSet),
        packages: Array.from(versions.keys())
      });
    }
  }
  
  return violations;
}

function main() {
  console.log('=== Dependency Version Alignment Check ===\n');
  
  try {
    const packageJsons = loadProductPackageJsons();
    console.log(`Loaded ${Object.keys(packageJsons).length} package.json files\n`);
    
    const violations = checkDependencyAlignment(packageJsons);
    
    if (violations.length > 0) {
      console.error('✗ Dependency version alignment check failed:\n');
      for (const violation of violations) {
        console.error(`  ${violation.dependency}:`);
        console.error(`    Versions: ${violation.versions.join(', ')}`);
        console.error(`    Packages: ${violation.packages.length}`);
      }
      console.error('\nRun: pnpm install to align dependencies');
      process.exit(1);
    }
    
    console.log('✓ All critical dependencies are aligned across product UIs');
    process.exit(0);
  } catch (error) {
    console.error('✗ Dependency alignment check failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
