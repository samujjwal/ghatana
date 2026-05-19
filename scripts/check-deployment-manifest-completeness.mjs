#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DeploymentManifestSchema } from '../platform/typescript/kernel-deployment/dist/index.js';

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

function findDeploymentManifest(runDirectory) {
  const matches = listFiles(runDirectory).filter((filePath) => path.basename(filePath) === 'deployment-manifest.json');
  return matches.sort((a, b) => b.length - a.length)[0];
}

export function validateDeploymentManifestCompleteness(runDirectory, options = {}) {
  const absoluteRunDirectory = path.resolve(options.repoRoot ?? repoRoot, runDirectory);
  const manifestPath = findDeploymentManifest(absoluteRunDirectory);
  if (!manifestPath) {
    return [`deployment-manifest.json not found under ${runDirectory}`];
  }

  const errors = [];
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const parsed = DeploymentManifestSchema.safeParse(manifest);
  if (!parsed.success) {
    errors.push(...parsed.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`));
    return errors;
  }

  for (const field of ['schemaVersion', 'runId', 'correlationId', 'productId', 'productUnitId', 'version', 'environment', 'deploymentId', 'surfaces', 'deployedAt', 'rollbackPlan']) {
    if (manifest[field] === undefined || manifest[field] === '') {
      errors.push(`deployment manifest missing required field ${field}`);
    }
  }
  if (!Array.isArray(manifest.surfaces) || manifest.surfaces.length === 0) {
    errors.push('deployment manifest must include at least one surface');
  }
  for (const surface of manifest.surfaces ?? []) {
    for (const field of ['surface', 'status', 'artifactId', 'deploymentTarget', 'healthCheckPassed']) {
      if (surface[field] === undefined || surface[field] === '') {
        errors.push(`deployment surface ${surface.surface ?? '<unknown>'} missing ${field}`);
      }
    }
  }
  if (!manifest.rollbackPlan?.strategy || !Array.isArray(manifest.rollbackPlan?.steps)) {
    errors.push('deployment manifest rollbackPlan must include strategy and steps');
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const runDirectory = process.argv[2];
  if (!runDirectory) {
    console.error('Usage: node scripts/check-deployment-manifest-completeness.mjs <run-directory>');
    process.exit(1);
  }
  const errors = validateDeploymentManifestCompleteness(runDirectory);
  if (errors.length === 0) {
    console.log('Deployment manifest completeness check passed.');
    process.exit(0);
  }
  console.error(`Deployment manifest completeness check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
