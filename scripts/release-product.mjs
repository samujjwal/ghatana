#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Import from source for development
const { ProductRelease } = await import(join(__dirname, '../platform/typescript/kernel-release/src/ProductRelease.js'));
const { ProductReleaseManifest } = await import(join(__dirname, '../platform/typescript/kernel-release/src/ProductReleaseManifest.js'));

async function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Usage: release-product.mjs <command> [options]');
    console.log('');
    console.log('Commands:');
    console.log('  create <productId> <version> - Create a new release');
    console.log('  approve <releaseId> - Approve a release');
    console.log('  reject <releaseId> <reason> - Reject a release');
    console.log('  list <productId> - List releases for a product');
    console.log('  status <releaseId> - Get release status');
    return;
  }

  const command = args[0];

  try {
    switch (command) {
      case 'create':
        await createRelease(args[1], args[2]);
        break;
      case 'approve':
        await approveRelease(args[1]);
        break;
      case 'reject':
        await rejectRelease(args[1], args[2]);
        break;
      case 'list':
        await listReleases(args[1]);
        break;
      case 'status':
        await getReleaseStatus(args[1]);
        break;
      default:
        console.error(`Unknown command: ${command}`);
        process.exit(1);
    }
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

async function createRelease(productId, version) {
  if (!productId || !version) {
    console.error('Error: productId and version are required');
    process.exit(1);
  }

  const release = new ProductRelease();
  const manifest = await release.create(productId, version);

  const outputPath = join(__dirname, '../config/releases', `${productId}-${version}.json`);
  writeFileSync(outputPath, JSON.stringify(manifest, null, 2));

  console.log(`Release created: ${manifest.releaseId}`);
  console.log(`Product: ${productId}`);
  console.log(`Version: ${version}`);
  console.log(`Manifest saved to: ${outputPath}`);
}

async function approveRelease(releaseId) {
  if (!releaseId) {
    console.error('Error: releaseId is required');
    process.exit(1);
  }

  const release = new ProductRelease();
  await release.approve(releaseId, 'cli-user');

  console.log(`Release approved: ${releaseId}`);
}

async function rejectRelease(releaseId, reason) {
  if (!releaseId || !reason) {
    console.error('Error: releaseId and reason are required');
    process.exit(1);
  }

  const release = new ProductRelease();
  await release.reject(releaseId, reason);

  console.log(`Release rejected: ${releaseId}`);
  console.log(`Reason: ${reason}`);
}

async function listReleases(productId) {
  if (!productId) {
    console.error('Error: productId is required');
    process.exit(1);
  }

  const release = new ProductRelease();
  const releases = await release.list(productId);

  console.log(`Releases for ${productId}:`);
  for (const r of releases) {
    console.log(`  - ${r.releaseId}: ${r.version} (${r.status})`);
  }
}

async function getReleaseStatus(releaseId) {
  if (!releaseId) {
    console.error('Error: releaseId is required');
    process.exit(1);
  }

  const release = new ProductRelease();
  const status = await release.getStatus(releaseId);

  console.log(`Release Status: ${releaseId}`);
  console.log(`Status: ${status.status}`);
  console.log(`Version: ${status.version}`);
  console.log(`Created: ${status.createdAt}`);
}

main();
