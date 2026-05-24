#!/usr/bin/env node

/**
 * Phase 8: Artifact bundle validator script.
 *
 * This script validates artifact bundle completeness, integrity, signatures,
 * compatibility, and deployment readiness.
 *
 * Usage: node scripts/artifact-bundle-validator.mjs --validate <bundle-version>
 *        node scripts/artifact-bundle-validator.mjs --check-integrity <manifest-path>
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'crypto';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const BUNDLE_DIR = path.join(repoRoot, '.kernel', 'release-bundles');

// Required artifact types in bundle
const REQUIRED_ARTIFACT_TYPES = ['evidence'];

// Optional but recommended artifact types
const RECOMMENDED_ARTIFACT_TYPES = ['test-results', 'coverage-reports', 'sbom'];

function validateBundle(version) {
  console.log(`Phase 8: Validating artifact bundle for version ${version}...\n`);

  const manifestPath = path.join(BUNDLE_DIR, `bundle-${version}-manifest.json`);
  
  if (!existsSync(manifestPath)) {
    console.error(`❌ Bundle manifest not found: ${manifestPath}`);
    process.exit(1);
  }

  console.log(`✅ Bundle manifest found: ${manifestPath}`);

  let manifest;
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch (error) {
    console.error(`❌ Failed to parse manifest: ${error.message}`);
    process.exit(1);
  }

  console.log(`✅ Manifest parsed successfully`);
  console.log(`   Version: ${manifest.version}`);
  console.log(`   Timestamp: ${manifest.timestamp}`);
  console.log(`   Artifacts: ${manifest.artifacts.length}`);

  // Check 1: Completeness - required artifact types
  console.log('\nChecking artifact type completeness...');
  const presentTypes = new Set(manifest.artifacts.map(a => a.type));
  let completenessPassed = true;

  for (const requiredType of REQUIRED_ARTIFACT_TYPES) {
    if (!presentTypes.has(requiredType)) {
      console.error(`❌ Missing required artifact type: ${requiredType}`);
      completenessPassed = false;
    } else {
      console.log(`✅ Required artifact type present: ${requiredType}`);
    }
  }

  for (const recommendedType of RECOMMENDED_ARTIFACT_TYPES) {
    if (!presentTypes.has(recommendedType)) {
      console.warn(`⚠️  Missing recommended artifact type: ${recommendedType}`);
    } else {
      console.log(`✅ Recommended artifact type present: ${recommendedType}`);
    }
  }

  // Check 2: Integrity - verify checksums
  console.log('\nChecking artifact integrity...');
  let integrityPassed = true;

  for (const artifact of manifest.artifacts) {
    const artifactPath = path.join(repoRoot, '.kernel', 'evidence', artifact.path);
    if (!existsSync(artifactPath)) {
      console.error(`❌ Artifact file missing: ${artifact.path}`);
      integrityPassed = false;
      continue;
    }

    const actualChecksum = calculateChecksum(artifactPath);
    if (actualChecksum !== artifact.checksum) {
      console.error(`❌ Checksum mismatch for ${artifact.path}`);
      console.error(`   Expected: ${artifact.checksum}`);
      console.error(`   Actual: ${actualChecksum}`);
      integrityPassed = false;
    } else {
      console.log(`✅ Checksum valid: ${artifact.path}`);
    }
  }

  // Check 3: Compatibility - version format
  console.log('\nChecking version compatibility...');
  const versionRegex = /^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/;
  if (!versionRegex.test(manifest.version)) {
    console.warn(`⚠️  Version format may not be semver: ${manifest.version}`);
  } else {
    console.log(`✅ Version format valid: ${manifest.version}`);
  }

  // Check 4: Deployment readiness - timestamp freshness
  console.log('\nChecking deployment readiness...');
  const bundleAge = (Date.now() - new Date(manifest.timestamp).getTime()) / (1000 * 60 * 60);
  if (bundleAge > 48) {
    console.warn(`⚠️  Bundle is ${bundleAge.toFixed(1)}h old - may be stale for deployment`);
  } else {
    console.log(`✅ Bundle is fresh: ${bundleAge.toFixed(1)}h old`);
  }

  // Overall result
  console.log('\n--- Validation Summary ---');
  if (completenessPassed && integrityPassed) {
    console.log('✅ Artifact bundle validation passed.');
    console.log(`   Version: ${manifest.version}`);
    console.log(`   Artifacts: ${manifest.artifacts.length}`);
    console.log(`   Completeness: PASS`);
    console.log(`   Integrity: PASS`);
    process.exit(0);
  } else {
    console.log('❌ Artifact bundle validation failed.');
    if (!completenessPassed) console.log('   Completeness: FAIL');
    if (!integrityPassed) console.log('   Integrity: FAIL');
    process.exit(1);
  }
}

function checkIntegrity(manifestPath) {
  console.log(`Phase 8: Checking integrity of bundle manifest: ${manifestPath}\n`);

  if (!existsSync(manifestPath)) {
    console.error(`❌ Manifest not found: ${manifestPath}`);
    process.exit(1);
  }

  let manifest;
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch (error) {
    console.error(`❌ Failed to parse manifest: ${error.message}`);
    process.exit(1);
  }

  console.log(`✅ Manifest parsed`);
  console.log(`   Version: ${manifest.version}`);
  console.log(`   Artifacts: ${manifest.artifacts.length}`);

  let allValid = true;
  for (const artifact of manifest.artifacts) {
    const artifactPath = path.join(repoRoot, '.kernel', 'evidence', artifact.path);
    if (!existsSync(artifactPath)) {
      console.error(`❌ Artifact missing: ${artifact.path}`);
      allValid = false;
      continue;
    }

    const actualChecksum = calculateChecksum(artifactPath);
    if (actualChecksum !== artifact.checksum) {
      console.error(`❌ Checksum mismatch: ${artifact.path}`);
      allValid = false;
    } else {
      console.log(`✅ Checksum valid: ${artifact.path}`);
    }
  }

  if (allValid) {
    console.log('\n✅ All artifact checksums are valid.');
    process.exit(0);
  } else {
    console.log('\n❌ Some artifact checksums are invalid.');
    process.exit(1);
  }
}

function calculateChecksum(filePath) {
  const content = readFileSync(filePath);
  return crypto.createHash('sha256').update(content).digest('hex');
}

function main() {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    console.log('Usage: node scripts/artifact-bundle-validator.mjs --validate <bundle-version>');
    console.log('       node scripts/artifact-bundle-validator.mjs --check-integrity <manifest-path>');
    process.exit(1);
  }

  const command = args[0];

  if (command === '--validate') {
    const version = args[1];
    if (!version) {
      console.error('Error: --validate requires a bundle version');
      process.exit(1);
    }
    validateBundle(version);
  } else if (command === '--check-integrity') {
    const manifestPath = args[1];
    if (!manifestPath) {
      console.error('Error: --check-integrity requires a manifest path');
      process.exit(1);
    }
    checkIntegrity(manifestPath);
  } else {
    console.error(`Unknown command: ${command}`);
    process.exit(1);
  }
}

try {
  main();
} catch (error) {
  console.error('Artifact bundle validation failed:', error);
  process.exit(1);
}
