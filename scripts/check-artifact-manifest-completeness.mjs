#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ArtifactManifestSchema } from '../platform/typescript/kernel-artifacts/dist/index.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function listFiles(directory) {
  if (!existsSync(directory)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...listFiles(absolutePath));
    } else if (entry.isFile()) {
      files.push(absolutePath);
    }
  }
  return files;
}

function findArtifactManifest(runDirectory) {
  const matches = listFiles(runDirectory).filter((filePath) => path.basename(filePath) === 'artifact-manifest.json');
  return matches.sort((a, b) => b.length - a.length)[0];
}

export function validateArtifactManifestCompleteness(runDirectory, options = {}) {
  const absoluteRunDirectory = path.resolve(options.repoRoot ?? repoRoot, runDirectory);
  const manifestPath = findArtifactManifest(absoluteRunDirectory);
  if (!manifestPath) {
    return [`artifact-manifest.json not found under ${runDirectory}`];
  }

  const errors = [];
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const parsed = ArtifactManifestSchema.safeParse(manifest);
  if (!parsed.success) {
    errors.push(...parsed.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`));
    return errors;
  }

  for (const field of ['schemaVersion', 'runId', 'correlationId', 'productId', 'productUnitId', 'providerMode', 'phase', 'timestamp', 'artifacts']) {
    if (manifest[field] === undefined || manifest[field] === '') {
      errors.push(`artifact manifest missing required field ${field}`);
    }
  }

  for (const artifact of manifest.artifacts) {
    for (const field of ['id', 'path', 'metadata', 'fingerprint', 'expected', 'found']) {
      if (artifact[field] === undefined) {
        errors.push(`artifact ${artifact.id ?? '<unknown>'} missing ${field}`);
      }
    }
    if (!artifact.metadata?.timestamp) {
      errors.push(`artifact ${artifact.id ?? '<unknown>'} missing metadata.timestamp`);
    }
    if (!artifact.fingerprint?.hash) {
      errors.push(`artifact ${artifact.id ?? '<unknown>'} missing fingerprint.hash`);
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const runDirectory = process.argv[2];
  if (!runDirectory) {
    console.error('Usage: node scripts/check-artifact-manifest-completeness.mjs <run-directory>');
    process.exit(1);
  }
  const errors = validateArtifactManifestCompleteness(runDirectory);
  if (errors.length === 0) {
    console.log('Artifact manifest completeness check passed.');
    process.exit(0);
  }
  console.error(`Artifact manifest completeness check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
