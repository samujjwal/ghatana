#!/usr/bin/env node

/**
 * Generate Product Shape Capability Matrix
 *
 * Analyzes the canonical product registry, lifecycle profiles, and toolchain adapters
 * to generate a matrix showing which products can be represented by which lifecycle
 * profiles and what capabilities they require.
 *
 * Outputs:
 * - config/generated/product-shape-capability-matrix.json (machine-readable)
 * - docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md (human-readable)
 */

import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

function loadRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function loadLifecycleProfiles() {
  const profilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');
  return JSON.parse(readFileSync(profilesPath, 'utf8')).profiles;
}

function loadToolchainAdapters() {
  const adaptersPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
  return JSON.parse(readFileSync(adaptersPath, 'utf8')).adapters;
}

function analyzeProduct(productId, product, profiles, adapters) {
  const lifecycleProfile = product.lifecycleProfile;
  const profile = profiles[lifecycleProfile];
  const lifecycleStatus = product.lifecycleStatus || (product.lifecycle?.enabled ? 'enabled' : 'disabled');
  const surfaces = product.surfaces || [];
  const surfaceTypes = surfaces.map(s => s.type).sort();

  // Determine required capabilities based on profile and surfaces
  const requiredCapabilities = [];
  const findings = [];

  if (!profile) {
    findings.push(`Lifecycle profile "${lifecycleProfile}" not found in product-lifecycle-profiles.json`);
  }

  // Check adapter support for each surface
  for (const surface of surfaces) {
    const surfaceType = surface.type;
    const adapterKey = profile?.defaultAdapters?.[surfaceType];
    const adapter = adapterKey ? adapters[adapterKey] : null;

    if (!adapterKey) {
      findings.push(`Surface "${surfaceType}" has no default adapter defined in profile "${lifecycleProfile}"`);
    } else if (!adapter) {
      findings.push(`Adapter "${adapterKey}" for surface "${surfaceType}" not found in toolchain-adapter-registry.json`);
    } else if (adapter.status !== 'implemented') {
      findings.push(`Adapter "${adapterKey}" for surface "${surfaceType}" has status "${adapter.status}" (not fully implemented)`);
    }

    // Add capability requirements
    if (adapterKey) {
      requiredCapabilities.push(`${surfaceType}:${adapterKey}`);
    }
  }

  // Check deployment adapter if lifecycle is enabled
  if (lifecycleStatus === 'enabled' && profile) {
    const deployAdapterKey = profile.defaultAdapters?.['deploy.local'];
    const deployAdapter = deployAdapterKey ? adapters[deployAdapterKey] : null;

    if (!deployAdapterKey) {
      findings.push(`No deployment adapter defined in profile "${lifecycleProfile}"`);
    } else if (!deployAdapter) {
      findings.push(`Deployment adapter "${deployAdapterKey}" not found in toolchain-adapter-registry.json`);
    } else if (deployAdapter.status !== 'implemented') {
      findings.push(`Deployment adapter "${deployAdapterKey}" has status "${deployAdapter.status}" (not fully implemented)`);
    }

    if (deployAdapterKey) {
      requiredCapabilities.push(`deploy:${deployAdapterKey}`);
    }
  }

  // Determine status based on lifecycle status and findings
  let status;
  if (lifecycleStatus === 'enabled') {
    status = 'Pilot';
    if (findings.some(f => f.includes('not found') || f.includes('not fully implemented'))) {
      status = 'Enabled (with findings)';
    }
  } else if (lifecycleStatus === 'planned') {
    status = 'Shape-only';
  } else if (lifecycleStatus === 'disabled') {
    status = 'Not enabled';
  } else {
    status = 'Unknown';
  }

  // Determine shape description
  const shapeDescription = getShapeDescription(surfaceTypes, lifecycleProfile);

  return {
    productId,
    name: product.name || productId,
    shape: shapeDescription,
    lifecycleStatus,
    requiredCapabilities: requiredCapabilities.sort(),
    status,
    findings: findings.length > 0 ? findings : undefined,
  };
}

function getShapeDescription(surfaceTypes, lifecycleProfile) {
  const surfaceDesc = surfaceTypes.join(' + ');
  return `${surfaceDesc} (${lifecycleProfile})`;
}

function generateMarkdown(matrix) {
  const lines = [
    '# Product Shape Capability Matrix',
    '',
    'This document shows which products can be represented by which lifecycle profiles and what capabilities they require.',
    '',
    'Generated from:',
    '- `config/canonical-product-registry.json`',
    '- `config/product-lifecycle-profiles.json`',
    '- `config/toolchain-adapter-registry.json`',
    '',
    '## Matrix',
    '',
    '| Product | Shape | Lifecycle Status | Required Kernel Capabilities | Status |',
    '|---------|-------|------------------|------------------------------|--------|',
  ];

  for (const row of matrix) {
    const capabilities = row.requiredCapabilities.join(', ') || 'None';
    lines.push(`| ${row.productId} | ${row.shape} | ${row.lifecycleStatus} | ${capabilities} | ${row.status} |`);
  }

  lines.push('');
  lines.push('## Findings');
  lines.push('');

  let hasFindings = false;
  for (const row of matrix) {
    if (row.findings && row.findings.length > 0) {
      hasFindings = true;
      lines.push(`### ${row.productId}`);
      lines.push('');
      for (const finding of row.findings) {
        lines.push(`- ${finding}`);
      }
      lines.push('');
    }
  }

  if (!hasFindings) {
    lines.push('No findings - all products have required profile and adapter support.');
  }

  return lines.join('\n');
}

async function main() {
  const registry = loadRegistry();
  const profiles = loadLifecycleProfiles();
  const adapters = loadToolchainAdapters();

  const matrix = [];
  for (const [productId, product] of Object.entries(registry.registry)) {
    const analysis = analyzeProduct(productId, product, profiles, adapters);
    matrix.push(analysis);
  }

  // Sort by product ID
  matrix.sort((a, b) => a.productId.localeCompare(b.productId));

  // Write JSON output
  const jsonOutputPath = join(repoRoot, 'config/generated/product-shape-capability-matrix.json');
  mkdirSync(dirname(jsonOutputPath), { recursive: true });
  writeFileSync(jsonOutputPath, JSON.stringify({ version: '1.0.0', generated: new Date().toISOString(), matrix }, null, 2));
  console.log(`Generated ${jsonOutputPath}`);

  // Write Markdown output
  const mdOutputPath = join(repoRoot, 'docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md');
  mkdirSync(dirname(mdOutputPath), { recursive: true });
  writeFileSync(mdOutputPath, generateMarkdown(matrix));
  console.log(`Generated ${mdOutputPath}`);

  console.log(`Product shape capability matrix generated for ${matrix.length} products`);
}

try {
  await main();
} catch (error) {
  console.error(`Generation failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
