#!/usr/bin/env node

/**
 * Test for G5-T02: Backend route existence test
 * Validates that every stable route contract apiEndpoint is mounted in PhrHttpServer
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');

function loadJson(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    return JSON.parse(content);
  } catch (err) {
    throw new Error(`Failed to load ${filePath}: ${err.message}`);
  }
}

function testBackendRouteMounts() {
  console.log('Testing backend route mounts against route contract...\n');

  const contractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.json');
  const httpServerPath = join(REPO_ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java');

  if (!existsSync(contractPath)) {
    console.error(`Route contract not found: ${contractPath}`);
    process.exit(1);
  }

  if (!existsSync(httpServerPath)) {
    console.error(`PhrHttpServer.java not found: ${httpServerPath}`);
    process.exit(1);
  }

  const contract = loadJson(contractPath);
  const httpServerContent = readFileSync(httpServerPath, 'utf-8');

  const stableRoutes = contract.routes.filter(r => r.stability === 'stable');
  const missingMounts = [];

  for (const route of stableRoutes) {
    if (!route.apiEndpoint) {
      continue;
    }

    const apiEndpoint = route.apiEndpoint;
    // Extract the base path from apiEndpoint
    // /api/v1/dashboard -> /api/v1/dashboard
    // /api/v1/records/:recordId -> /api/v1/records
    const basePath = apiEndpoint.split('/').slice(0, 3).join('/');
    const escapedPath = basePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`['"]${escapedPath}`, 'm');

    if (!regex.test(httpServerContent)) {
      missingMounts.push(`${route.path}: ${apiEndpoint}`);
    }
  }

  if (missingMounts.length > 0) {
    console.error('Stable route API endpoints not mounted in PhrHttpServer:');
    missingMounts.forEach(mount => console.error(`  ${mount}`));
    process.exit(1);
  }

  console.log(`✓ All ${stableRoutes.length} stable route API endpoints are mounted in PhrHttpServer`);
}

testBackendRouteMounts();
