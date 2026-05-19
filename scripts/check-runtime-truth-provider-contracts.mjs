#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const REQUIRED_PRODUCTS = ['digital-marketing', 'phr'];

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

export function validateRuntimeTruthProviderContracts(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const errors = [];
  for (const relativePath of [
    'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudEventProvider.java',
    'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudArtifactProvider.java',
    'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudHealthProvider.java',
    'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudRuntimeTruthProvider.java',
  ]) {
    if (!existsSync(path.join(root, relativePath))) {
      errors.push(`required Data Cloud provider bridge file missing: ${relativePath}`);
    }
  }

  for (const productId of REQUIRED_PRODUCTS) {
    const productRunRoot = path.join(root, '.kernel-runs', productId);
    const runtimeTruthFiles = listFiles(productRunRoot).filter((filePath) => path.basename(filePath) === 'runtime-truth-snapshots.json');
    if (runtimeTruthFiles.length === 0) {
      errors.push(`${productId} has no bootstrap runtime-truth-snapshots.json evidence`);
    }
    for (const filePath of runtimeTruthFiles) {
      const document = JSON.parse(readFileSync(filePath, 'utf8'));
      const snapshots = document.snapshots ?? [];
      if (!Array.isArray(snapshots) || snapshots.length === 0) {
        errors.push(`${path.relative(root, filePath)} must contain runtime truth snapshots`);
        continue;
      }
      for (const snapshot of snapshots) {
        if (snapshot.productUnitId !== productId) {
          errors.push(`${path.relative(root, filePath)} snapshot productUnitId must be ${productId}`);
        }
        if (!snapshot.correlationId) {
          errors.push(`${path.relative(root, filePath)} snapshot missing correlationId`);
        }
        if (!Array.isArray(snapshot.evidenceRefs) || snapshot.evidenceRefs.length === 0) {
          errors.push(`${path.relative(root, filePath)} snapshot missing evidenceRefs`);
        }
      }
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = validateRuntimeTruthProviderContracts();
  if (errors.length === 0) {
    console.log('Runtime truth provider contract check passed.');
    process.exit(0);
  }
  console.error(`Runtime truth provider contract check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
