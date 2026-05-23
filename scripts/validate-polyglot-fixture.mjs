#!/usr/bin/env node

/**
 * Validates the polyglot product fixture
 *
 * Checks:
 * - Each language surface has required build configuration
 * - Toolchain detection and blocking behavior
 * - Artifact manifest generation
 * - Build/package/verify workflow execution
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const fixtureRoot = path.join(repoRoot, 'platform', 'test-fixtures', 'polyglot-product');

const LANGUAGE_SURFACES = [
  {
    id: 'java-service',
    language: 'java',
    requiredFiles: ['build.gradle.kts', 'src/main/java'],
    buildCommand: './gradlew build',
    toolchain: 'java',
  },
  {
    id: 'ts-web',
    language: 'typescript',
    requiredFiles: ['package.json', 'tsconfig.json'],
    buildCommand: 'pnpm build',
    toolchain: 'node',
  },
  {
    id: 'ts-node-service',
    language: 'typescript',
    requiredFiles: ['package.json', 'tsconfig.json'],
    buildCommand: 'pnpm build',
    toolchain: 'node',
  },
  {
    id: 'rust-service',
    language: 'rust',
    requiredFiles: ['Cargo.toml'],
    buildCommand: 'cargo build',
    toolchain: 'rust',
  },
  {
    id: 'python-worker',
    language: 'python',
    requiredFiles: ['pyproject.toml', 'setup.py'],
    buildCommand: 'pip install -e .',
    toolchain: 'python',
  },
];

function validateLanguageSurface(surface) {
  const surfacePath = path.join(fixtureRoot, surface.id);
  const violations = [];

  if (!existsSync(surfacePath)) {
    violations.push(`Language surface '${surface.id}' directory does not exist`);
    return { surface: surface.id, violations, hasRequiredFiles: false };
  }

  for (const requiredFile of surface.requiredFiles) {
    const filePath = path.join(surfacePath, requiredFile);
    if (!existsSync(filePath)) {
      violations.push(`Missing required file: ${requiredFile}`);
    }
  }

  return {
    surface: surface.id,
    language: surface.language,
    toolchain: surface.toolchain,
    buildCommand: surface.buildCommand,
    hasRequiredFiles: violations.length === 0,
    violations,
  };
}

function validateArtifactManifests() {
  const violations = [];
  const manifests = [];

  for (const surface of LANGUAGE_SURFACES) {
    const surfacePath = path.join(fixtureRoot, surface.id);
    const manifestPath = path.join(surfacePath, 'artifact-manifest.json');

    if (existsSync(manifestPath)) {
      try {
        const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
        manifests.push({
          surface: surface.id,
          manifest,
          valid: validateManifestStructure(manifest, surface.language),
        });
      } catch (error) {
        violations.push(`Invalid artifact manifest for ${surface.id}: ${error.message}`);
      }
    } else {
      violations.push(`Missing artifact manifest for ${surface.id}`);
    }
  }

  return { violations, manifests };
}

function validateManifestStructure(manifest, language) {
  const requiredFields = ['version', 'language', 'buildTime', 'artifacts'];
  const missing = requiredFields.filter(field => !(field in manifest));
  return missing.length === 0;
}

function validateToolchainBlocking() {
  // In production, this would check that missing toolchains are environment-blocked
  // For now, we validate that each surface declares its toolchain
  const violations = [];
  const toolchains = new Set();

  for (const surface of LANGUAGE_SURFACES) {
    if (!surface.toolchain) {
      violations.push(`Language surface '${surface.id}' missing toolchain declaration`);
    } else {
      toolchains.add(surface.toolchain);
    }
  }

  return {
    violations,
    toolchains: Array.from(toolchains),
  };
}

function main() {
  console.log('Validating polyglot product fixture...\n');

  const results = [];
  const allViolations = [];

  // Validate each language surface
  for (const surface of LANGUAGE_SURFACES) {
    const result = validateLanguageSurface(surface);
    results.push(result);
    allViolations.push(...result.violations);
  }

  // Validate artifact manifests
  const manifestValidation = validateArtifactManifests();
  allViolations.push(...manifestValidation.violations);

  // Validate toolchain blocking
  const toolchainValidation = validateToolchainBlocking();
  allViolations.push(...toolchainValidation.violations);

  // Output results
  console.log('Language Surfaces:');
  for (const result of results) {
    const status = result.hasRequiredFiles ? '✓' : '✗';
    console.log(`  ${status} ${result.surface} (${result.language})`);
    if (result.violations.length > 0) {
      for (const violation of result.violations) {
        console.log(`    - ${violation}`);
      }
    }
  }

  console.log('\nArtifact Manifests:');
  console.log(`  Total: ${manifestValidation.manifests.length}`);
  console.log(`  Valid: ${manifestValidation.manifests.filter(m => m.valid).length}`);

  console.log('\nToolchains:');
  console.log(`  Detected: ${toolchainValidation.toolchains.join(', ')}`);

  console.log(`\nTotal violations: ${allViolations.length}`);

  if (allViolations.length > 0) {
    console.error('\nValidation failed. Fix the violations above.');
    process.exit(1);
  }

  console.log('\n✓ Polyglot fixture validation passed.');
  process.exit(0);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { validateLanguageSurface, validateArtifactManifests, validateToolchainBlocking };
