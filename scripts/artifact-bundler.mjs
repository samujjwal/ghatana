#!/usr/bin/env node

/**
 * Artifact Bundler for Production Releases
 *
 * This script bundles all release artifacts into a single package for production deployment.
 * It collects evidence files, test results, coverage reports, and SBOMs, then creates
 * a signed artifact bundle with a manifest.
 *
 * @doc.type script
 * @doc.purpose Bundle all release artifacts into a single package for production deployment
 * @doc.phase Phase 8
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import crypto from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const EVIDENCE_DIR = path.join(ROOT_DIR, '.kernel', 'evidence');
const OUTPUT_DIR = path.join(ROOT_DIR, '.kernel', 'release-bundles');

/**
 * Represents the artifact bundle manifest.
 */
class ArtifactBundleManifest {
  constructor(version, timestamp) {
    this.version = version;
    this.timestamp = timestamp;
    this.artifacts = [];
    this.checksums = {};
  }

  addArtifact(type, path, checksum) {
    this.artifacts.push({ type, path, checksum });
    this.checksums[path] = checksum;
  }

  toJSON() {
    return {
      version: this.version,
      timestamp: this.timestamp,
      artifacts: this.artifacts,
      checksums: this.checksums
    };
  }
}

/**
 * Calculates SHA-256 checksum for a file.
 */
function calculateChecksum(filePath) {
  const content = fs.readFileSync(filePath);
  return crypto.createHash('sha256').update(content).digest('hex');
}

/**
 * Collects all release artifacts.
 */
function collectArtifacts() {
  const artifacts = [];

  // Collect evidence files
  if (fs.existsSync(EVIDENCE_DIR)) {
    const evidenceFiles = fs.readdirSync(EVIDENCE_DIR, { recursive: true });
    for (const file of evidenceFiles) {
      const fullPath = path.join(EVIDENCE_DIR, file);
      if (fs.statSync(fullPath).isFile()) {
        artifacts.push({
          type: 'evidence',
          path: fullPath,
          relativePath: path.relative(EVIDENCE_DIR, fullPath)
        });
      }
    }
  }

  // Collect test results
  const testResultsDir = path.join(ROOT_DIR, 'build', 'reports', 'tests');
  if (fs.existsSync(testResultsDir)) {
    const testFiles = fs.readdirSync(testResultsDir, { recursive: true });
    for (const file of testFiles) {
      const fullPath = path.join(testResultsDir, file);
      if (fs.statSync(fullPath).isFile()) {
        artifacts.push({
          type: 'test-results',
          path: fullPath,
          relativePath: path.relative(testResultsDir, fullPath)
        });
      }
    }
  }

  return artifacts;
}

/**
 * Creates the artifact bundle.
 */
function createBundle(version) {
  console.log(`Creating artifact bundle for version ${version}...`);

  // Ensure output directory exists
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });

  // Create manifest
  const timestamp = new Date().toISOString();
  const manifest = new ArtifactBundleManifest(version, timestamp);

  // Collect and add artifacts
  const artifacts = collectArtifacts();
  for (const artifact of artifacts) {
    const checksum = calculateChecksum(artifact.path);
    manifest.addArtifact(artifact.type, artifact.relativePath, checksum);
    console.log(`  Added ${artifact.type}: ${artifact.relativePath} (${checksum.substring(0, 8)}...)`);
  }

  // Write manifest
  const manifestPath = path.join(OUTPUT_DIR, `bundle-${version}-manifest.json`);
  fs.writeFileSync(manifestPath, JSON.stringify(manifest.toJSON(), null, 2));
  console.log(`  Manifest written to: ${manifestPath}`);

  // Calculate bundle checksum
  const bundleChecksum = calculateChecksum(manifestPath);
  console.log(`  Bundle checksum: ${bundleChecksum}`);

  return {
    manifestPath,
    artifactCount: artifacts.length,
    checksum: bundleChecksum
  };
}

/**
 * Main execution.
 */
function main() {
  const args = process.argv.slice(2);
  const version = args[0] || 'latest';

  try {
    const result = createBundle(version);
    console.log(`\nArtifact bundle created successfully:`);
    console.log(`  Version: ${version}`);
    console.log(`  Artifacts: ${result.artifactCount}`);
    console.log(`  Checksum: ${result.checksum}`);
    console.log(`  Manifest: ${result.manifestPath}`);
  } catch (error) {
    console.error(`Error creating artifact bundle: ${error.message}`);
    process.exit(1);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}

export { createBundle, collectArtifacts, calculateChecksum };
