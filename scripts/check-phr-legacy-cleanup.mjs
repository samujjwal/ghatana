#!/usr/bin/env node

/**
 * G12-T11: Orphan-file check for PHR pages/API modules/routes.
 * 
 * This script checks for truly orphan files in the PHR product.
 * It only flags files that are clearly unused (e.g., old backup files, 
 * temporary files, or files with "old", "backup", "deprecated" in the name).
 * 
 * This is a conservative check - it does not try to analyze imports
 * because many files are used through routing, configuration, or reflection.
 */

import { readFileSync, existsSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const PHR_DIR = join(__dirname, '..', 'products', 'phr');
const SCRIPTS_DIR = join(__dirname, '..', 'scripts');

// Patterns that indicate a file is likely orphan/unused
const ORPHAN_PATTERNS = [
  /\.old\./,
  /\.backup\./,
  /\.deprecated\./,
  /\.tmp\./,
  /\.bak\./,
  /-old\./,
  /-backup\./,
  /-deprecated\./,
  /_old\./,
  /_backup\./,
  /_deprecated\./,
  /\.copy\./,
  /-copy\./,
  /_copy\./,
];

function isOrphanFile(filename) {
  for (const pattern of ORPHAN_PATTERNS) {
    if (pattern.test(filename)) {
      return true;
    }
  }
  return false;
}

// Check web pages
const webPagesDir = join(PHR_DIR, 'apps', 'web', 'src', 'pages');
const orphanWebPages = [];

if (existsSync(webPagesDir)) {
  for (const file of readdirSync(webPagesDir)) {
    if (isOrphanFile(file)) {
      orphanWebPages.push(file);
    }
  }
}

// Check API modules
const apiDir = join(PHR_DIR, 'apps', 'web', 'src', 'api');
const orphanApiModules = [];

if (existsSync(apiDir)) {
  for (const file of readdirSync(apiDir)) {
    if (isOrphanFile(file)) {
      orphanApiModules.push(file);
    }
  }
}

// Check backend route files
const routesDir = join(PHR_DIR, 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'routes');
const orphanRouteFiles = [];

if (existsSync(routesDir)) {
  for (const file of readdirSync(routesDir)) {
    if (isOrphanFile(file)) {
      orphanRouteFiles.push(file);
    }
  }
}

// Check mobile screens
const mobileScreensDir = join(PHR_DIR, 'apps', 'mobile', 'src', 'screens');
const orphanMobileScreens = [];

if (existsSync(mobileScreensDir)) {
  for (const file of readdirSync(mobileScreensDir)) {
    if (isOrphanFile(file)) {
      orphanMobileScreens.push(file);
    }
  }
}

// Check one-off mutation scripts. PHR repair scripts must be converted into
// deterministic checks or removed after the production path is fixed.
const orphanMutationScripts = [];
for (const file of readdirSync(SCRIPTS_DIR)) {
  if (/^fix-(phr|mobile|web|consent).*\.mjs$/.test(file)) {
    orphanMutationScripts.push(file);
  }
}

// Report findings
let hasOrphans = false;

if (orphanWebPages.length > 0) {
  console.log('[phr-legacy-cleanup] Orphan web pages found:');
  for (const file of orphanWebPages) {
    console.log(`  - ${file}`);
  }
  hasOrphans = true;
}

if (orphanApiModules.length > 0) {
  console.log('[phr-legacy-cleanup] Orphan API modules found:');
  for (const module of orphanApiModules) {
    console.log(`  - ${module}`);
  }
  hasOrphans = true;
}

if (orphanRouteFiles.length > 0) {
  console.log('[phr-legacy-cleanup] Orphan backend route files found:');
  for (const file of orphanRouteFiles) {
    console.log(`  - ${file}`);
  }
  hasOrphans = true;
}

if (orphanMobileScreens.length > 0) {
  console.log('[phr-legacy-cleanup] Orphan mobile screens found:');
  for (const screen of orphanMobileScreens) {
    console.log(`  - ${screen}`);
  }
  hasOrphans = true;
}

if (orphanMutationScripts.length > 0) {
  console.log('[phr-legacy-cleanup] One-off mutation scripts found:');
  for (const script of orphanMutationScripts) {
    console.log(`  - ${script}`);
  }
  hasOrphans = true;
}

if (!hasOrphans) {
  console.log('[phr-legacy-cleanup] PASS: No orphan files found');
  process.exit(0);
} else {
  console.log('[phr-legacy-cleanup] FAIL: Orphan files detected');
  process.exit(1);
}
