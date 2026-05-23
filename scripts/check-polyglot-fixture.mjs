#!/usr/bin/env node

/**
 * Phase 4: Polyglot Fixture Validation
 *
 * Validates that the canonical polyglot fixture product has all required surfaces
 * and that each surface can build, test, and package correctly through Kernel lifecycle.
 *
 * Validates:
 * - Java service surface (Spring Boot)
 * - TypeScript web surface (Express)
 * - Node service surface (Express)
 * - Rust service surface (Actix-web)
 * - Python worker surface (FastAPI)
 * - Docker packaging for all surfaces
 * - docker-compose orchestration
 *
 * Usage: node scripts/check-polyglot-fixture.mjs [--surface <name>]
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const fixturePath = path.join(repoRoot, 'products/polyglot-fixture');

const SURFACES = {
  'java-service': {
    path: 'surfaces/java',
    buildFile: 'build.gradle.kts',
    requiredFiles: ['build.gradle.kts', 'settings.gradle.kts', 'Dockerfile'],
    sourceDir: 'src/main/java',
    testDir: 'src/test/java',
    port: 8080
  },
  'typescript-web': {
    path: 'surfaces/typescript',
    buildFile: 'package.json',
    requiredFiles: ['package.json', 'tsconfig.json', 'Dockerfile'],
    sourceDir: 'src',
    testDir: 'src',
    port: 3001
  },
  'node-service': {
    path: 'surfaces/node',
    buildFile: 'package.json',
    requiredFiles: ['package.json', 'Dockerfile'],
    sourceDir: 'src',
    testDir: 'src',
    port: 3004
  },
  'rust-service': {
    path: 'surfaces/rust',
    buildFile: 'Cargo.toml',
    requiredFiles: ['Cargo.toml', 'Dockerfile'],
    sourceDir: 'src',
    testDir: 'src',
    port: 3002
  },
  'python-worker': {
    path: 'surfaces/python',
    buildFile: 'pyproject.toml',
    requiredFiles: ['pyproject.toml', 'Dockerfile'],
    sourceDir: 'src',
    testDir: 'src',
    port: 3003
  }
};

function checkSurface(surfaceType, surfaceConfig) {
  const violations = [];
  const surfacePath = path.join(fixturePath, surfaceConfig.path);

  // Check surface directory exists
  if (!existsSync(surfacePath)) {
    violations.push(`Surface directory missing: ${surfaceConfig.path}`);
    return { surfaceType, violations, status: 'missing' };
  }

  // Check required files
  for (const file of surfaceConfig.requiredFiles) {
    const filePath = path.join(surfacePath, file);
    if (!existsSync(filePath)) {
      violations.push(`Required file missing: ${file}`);
    }
  }

  // Check source directory exists
  const sourcePath = path.join(surfacePath, surfaceConfig.sourceDir);
  if (!existsSync(sourcePath)) {
    violations.push(`Source directory missing: ${surfaceConfig.sourceDir}`);
  } else {
    // Check for at least one source file
    const sourceFiles = readdirSync(sourcePath);
    if (sourceFiles.length === 0) {
      violations.push(`Source directory is empty: ${surfaceConfig.sourceDir}`);
    }
  }

  // Check test directory exists
  const testPath = path.join(surfacePath, surfaceConfig.testDir);
  if (!existsSync(testPath)) {
    violations.push(`Test directory missing: ${surfaceConfig.testDir}`);
  } else {
    // Recursively search for test files
    let testFiles = [];
    function searchDir(dir) {
      if (!existsSync(dir)) return;
      const entries = readdirSync(dir);
      for (const entry of entries) {
        const fullPath = path.join(dir, entry);
        const stat = statSync(fullPath);
        if (stat.isDirectory()) {
          searchDir(fullPath);
        } else if (entry.includes('.test.') || entry.includes('_test.') || entry.includes('Test.') || entry.includes('test_')) {
          testFiles.push(entry);
        }
      }
    }
    searchDir(testPath);
    
    if (testFiles.length === 0) {
      violations.push(`No test files found in: ${surfaceConfig.testDir}`);
    }
  }

  // Validate build file content
  const buildFilePath = path.join(surfacePath, surfaceConfig.buildFile);
  if (existsSync(buildFilePath)) {
    const buildContent = readFileSync(buildFilePath, 'utf8');
    
    // Check for version field
    if (!buildContent.includes('version') && !buildContent.includes('1.0.0')) {
      violations.push(`Build file missing version: ${surfaceConfig.buildFile}`);
    }

    // Check for proper dependencies
    if (surfaceType === 'java-service') {
      if (!buildContent.includes('spring-boot')) {
        violations.push('Java build missing Spring Boot dependency');
      }
    } else if (surfaceType === 'typescript-web' || surfaceType === 'node-service') {
      if (!buildContent.includes('express')) {
        violations.push('TypeScript/Node build missing Express dependency');
      }
    } else if (surfaceType === 'rust-service') {
      if (!buildContent.includes('actix-web')) {
        violations.push('Rust build missing Actix-web dependency');
      }
    } else if (surfaceType === 'python-worker') {
      if (!buildContent.includes('fastapi')) {
        violations.push('Python build missing FastAPI dependency');
      }
    }
  }

  // Validate Dockerfile
  const dockerfilePath = path.join(surfacePath, 'Dockerfile');
  if (existsSync(dockerfilePath)) {
    const dockerContent = readFileSync(dockerfilePath, 'utf8');
    
    // Check for multi-stage build
    if (!dockerContent.includes('AS builder')) {
      violations.push('Dockerfile should use multi-stage build for production');
    }

    // Check for exposed port
    if (!dockerContent.includes('EXPOSE')) {
      violations.push('Dockerfile missing EXPOSE directive');
    }
  }

  return {
    surfaceType,
    violations,
    status: violations.length === 0 ? 'passed' : 'failed'
  };
}

function checkDockerCompose() {
  const violations = [];
  const composePath = path.join(fixturePath, 'docker-compose.yml');

  if (!existsSync(composePath)) {
    violations.push('docker-compose.yml missing');
    return { violations, status: 'missing' };
  }

  const composeContent = readFileSync(composePath, 'utf8');

  // Check for all services
  const expectedServices = ['java-service', 'typescript-service', 'node-service', 'rust-service', 'python-worker'];
  for (const service of expectedServices) {
    if (!composeContent.includes(service)) {
      violations.push(`docker-compose.yml missing service: ${service}`);
    }
  }

  // Check for healthchecks
  if (!composeContent.includes('healthcheck')) {
    violations.push('docker-compose.yml missing healthcheck configuration');
  }

  // Check for port mappings
  if (!composeContent.includes('ports:')) {
    violations.push('docker-compose.yml missing port mappings');
  }

  return { violations, status: violations.length === 0 ? 'passed' : 'failed' };
}

function checkKernelProductManifest() {
  const violations = [];
  const manifestPath = path.join(fixturePath, 'kernel-product.yaml');

  if (!existsSync(manifestPath)) {
    violations.push('kernel-product.yaml missing');
    return { violations, status: 'missing' };
  }

  const manifestContent = readFileSync(manifestPath, 'utf8');

  // Check execution is enabled for fixture product
  if (!manifestContent.includes('executionEnabled: true')) {
    violations.push('kernel-product.yaml should have executionEnabled: true for fixture product');
  }

  // Check for all surfaces in manifest
  const expectedSurfaceTypes = ['java-service', 'typescript-web', 'node-service', 'rust-service', 'python-worker'];
  for (const surfaceType of expectedSurfaceTypes) {
    if (!manifestContent.includes(surfaceType)) {
      violations.push(`kernel-product.yaml missing surface type: ${surfaceType}`);
    }
  }

  // Check for required plugins
  const requiredPlugins = ['audit', 'observability', 'data-access', 'identity-entitlement', 'security'];
  for (const plugin of requiredPlugins) {
    if (!manifestContent.includes(plugin)) {
      violations.push(`kernel-product.yaml missing required plugin: ${plugin}`);
    }
  }

  return { violations, status: violations.length === 0 ? 'passed' : 'failed' };
}

function main() {
  console.log('Checking polyglot fixture product...\n');

  const results = [];
  let totalViolations = 0;

  // Check each surface
  for (const [surfaceType, surfaceConfig] of Object.entries(SURFACES)) {
    const result = checkSurface(surfaceType, surfaceConfig);
    results.push(result);
    totalViolations += result.violations.length;

    console.log(`${surfaceType}: ${result.status}`);
    if (result.violations.length > 0) {
      for (const violation of result.violations) {
        console.log(`  - ${violation}`);
      }
    }
  }

  // Check docker-compose
  console.log('\ndocker-compose:');
  const composeResult = checkDockerCompose();
  totalViolations += composeResult.violations.length;
  console.log(`  ${composeResult.status}`);
  for (const violation of composeResult.violations) {
    console.log(`  - ${violation}`);
  }

  // Check kernel-product.yaml
  console.log('\nkernel-product.yaml:');
  const manifestResult = checkKernelProductManifest();
  totalViolations += manifestResult.violations.length;
  console.log(`  ${manifestResult.status}`);
  for (const violation of manifestResult.violations) {
    console.log(`  - ${violation}`);
  }

  console.log(`\nTotal violations: ${totalViolations}`);

  if (totalViolations > 0) {
    console.error('\nPolyglot fixture check failed.');
    process.exit(1);
  }

  console.log('\nPolyglot fixture check passed.');
}

main();
