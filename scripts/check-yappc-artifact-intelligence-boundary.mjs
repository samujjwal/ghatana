#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];

function read(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
    return '';
  }
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireFile(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

function requireIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (!source.includes(needle)) {
    errors.push(`${label} must include ${needle}`);
  }
}

requireFile('platform/typescript/kernel-product-contracts/src/artifact-intelligence/ArtifactIntelligence.ts');
requireFile('platform/typescript/kernel-product-contracts/src/artifact-intelligence/__tests__/ArtifactIntelligence.test.ts');
requireFile('products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcPluginBridgeExtension.java');
requireFile('products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts');

requireIncludes(
  'platform/typescript/kernel-product-contracts/src/artifact-intelligence/ArtifactIntelligence.ts',
  'SemanticArtifactReferenceSchema',
);
requireIncludes(
  'platform/typescript/kernel-product-contracts/src/artifact-intelligence/ArtifactIntelligence.ts',
  'ArtifactIntelligenceEvidenceBaseSchema',
);
requireIncludes(
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcPluginBridgeExtension.java',
  'YappcSemanticArtifactEvidenceProvider',
);
requireIncludes(
  'products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts',
  'ProductUnitIntentSchema',
);

try {
  execFileSync(process.execPath, [join(repoRoot, 'scripts/check-kernel-yappc-boundary.mjs')], {
    cwd: repoRoot,
    stdio: 'inherit',
  });
} catch (error) {
  errors.push(`Kernel/YAPPC boundary check failed: ${error instanceof Error ? error.message : String(error)}`);
}

if (errors.length > 0) {
  console.error('YAPPC artifact intelligence boundary check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('YAPPC artifact intelligence boundary check passed.');
