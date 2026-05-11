#!/usr/bin/env node
/**
 * DMOS duplicate runtime guard.
 *
 * @doc.type tooling
 * @doc.purpose Detect duplicate production UI apps, route registries, permission registries, and dashboard runtimes
 * @doc.layer infrastructure
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const productRoot = join(repoRoot, 'products/digital-marketing');
const violations = [];

function walk(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'build' || entry === 'dist' || entry === '.gradle') {
      continue;
    }
    const full = join(dir, entry);
    let stat;
    try {
      stat = statSync(full);
    } catch (error) {
      if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
        continue;
      }
      throw error;
    }
    if (stat.isDirectory()) {
      files.push(...walk(full));
    } else {
      files.push(full);
    }
  }
  return files;
}

function rel(path) {
  return relative(repoRoot, path).replace(/\\/g, '/');
}

function isArchivedDmUi(path) {
  return rel(path).startsWith('products/digital-marketing/dm-ui/');
}

const packageJsons = walk(productRoot).filter((file) => basename(file) === 'package.json');
const productionUiPackages = packageJsons.filter((file) => !isArchivedDmUi(file));
if (productionUiPackages.length !== 1 || !rel(productionUiPackages[0]).endsWith('/ui/package.json')) {
  violations.push(`expected one production UI package at products/digital-marketing/ui/package.json, found: ${productionUiPackages.map(rel).join(', ')}`);
}

const dmUiReadme = join(productRoot, 'dm-ui/README.md');
if (existsSync(join(productRoot, 'dm-ui')) && (!existsSync(dmUiReadme) || !/archived, non-built UI surface/i.test(readFileSync(dmUiReadme, 'utf8')))) {
  violations.push('products/digital-marketing/dm-ui must remain explicitly archived and non-built');
}

const routeManifests = walk(productRoot).filter((file) => /routeManifest\.tsx$/.test(file) && !isArchivedDmUi(file));
if (routeManifests.length !== 1 || !rel(routeManifests[0]).endsWith('/ui/src/routeManifest.tsx')) {
  violations.push(`expected one production route manifest, found: ${routeManifests.map(rel).join(', ')}`);
}

const permissionRegistries = walk(productRoot).filter((file) =>
  /src\/main\/java\/.*DmosActionPermissionRegistry\.java$/.test(rel(file)),
);
const allowedPermissionRegistry = 'products/digital-marketing/dm-core-contracts/src/main/java/com/ghatana/digitalmarketing/api/security/DmosActionPermissionRegistry.java';
if (permissionRegistries.length !== 1 || rel(permissionRegistries[0]) !== allowedPermissionRegistry) {
  violations.push(`expected one generated backend permission registry at ${allowedPermissionRegistry}, found: ${permissionRegistries.map(rel).join(', ')}`);
}

const dashboardPages = walk(productRoot).filter((file) =>
  /src\/pages\/Dashboard(Page|CommandCenter)\.tsx$/.test(rel(file)) && !isArchivedDmUi(file),
);
if (dashboardPages.length !== 1 || !rel(dashboardPages[0]).endsWith('/ui/src/pages/DashboardPage.tsx')) {
  violations.push(`expected one production dashboard page, found: ${dashboardPages.map(rel).join(', ')}`);
}

if (violations.length > 0) {
  console.error('DMOS duplicate runtime guard failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('DMOS duplicate runtime guard passed.');
