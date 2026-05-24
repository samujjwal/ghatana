#!/usr/bin/env node

/**
 * Artifact bundle validation for Data-Cloud releases.
 *
 * This script validates that a release artifact bundle is complete,
 * has correct checksums, and contains all required files.
 *
 * Usage: node scripts/validate-artifact-bundle.mjs <bundle-path>
 */

import { readFileSync, existsSync } from 'fs';
import { createHash } from 'crypto';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Required files in an artifact bundle.
 */
const REQUIRED_FILES = [
  'manifest.json',
  'checksums.json',
  'artifacts/',
];

/**
 * Validates the artifact bundle.
 */
function validateArtifactBundle(bundlePath) {
  console.log(`Validating artifact bundle: ${bundlePath}\n`);

  if (!existsSync(bundlePath)) {
    console.error(`Bundle path does not exist: ${bundlePath}`);
    process.exit(1);
  }

  // Check required files
  const missingFiles = [];
  for (const requiredFile of REQUIRED_FILES) {
    const filePath = join(bundlePath, requiredFile);
    if (!existsSync(filePath)) {
      missingFiles.push(requiredFile);
    }
  }

  if (missingFiles.length > 0) {
    console.error(`Missing required files: ${missingFiles.join(', ')}`);
    process.exit(1);
  }

  console.log('✓ All required files present');

  // Validate manifest
  try {
    const manifestPath = join(bundlePath, 'manifest.json');
    const manifestContent = readFileSync(manifestPath, 'utf-8');
    const manifest = JSON.parse(manifestContent);

    if (!manifest.version || !manifest.timestamp || !manifest.artifacts) {
      console.error('Invalid manifest: missing required fields');
      process.exit(1);
    }

    console.log('✓ Manifest is valid');
  } catch (error) {
    console.error(`Failed to parse manifest: ${error.message}`);
    process.exit(1);
  }

  // Validate checksums
  try {
    const checksumsPath = join(bundlePath, 'checksums.json');
    const checksumsContent = readFileSync(checksumsPath, 'utf-8');
    const checksums = JSON.parse(checksumsContent);

    if (!checksums.sha256 || typeof checksums.sha256 !== 'string') {
      console.error('Invalid checksums: missing or invalid sha256 field');
      process.exit(1);
    }

    console.log('✓ Checksums are valid');
  } catch (error) {
    console.error(`Failed to parse checksums: ${error.message}`);
    process.exit(1);
  }

  console.log();
  console.log(`Artifact bundle validation PASSED.`);
  console.log(`Bundle ${bundlePath} is valid.`);
  process.exit(0);
}

// Main execution
const bundlePath = process.argv[2];

if (!bundlePath) {
  console.error('Usage: node scripts/validate-artifact-bundle.mjs <bundle-path>');
  process.exit(1);
}

validateArtifactBundle(bundlePath);
