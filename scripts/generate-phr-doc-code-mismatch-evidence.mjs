#!/usr/bin/env node

/**
 * PHR Doc/Code Mismatch Evidence Generator
 *
 * Compares the PHR use-case baseline against actual code implementation
 * to identify discrepancies between documented IA and actual implementation.
 *
 * Checks:
 * 1. Web routes in baseline exist in phrRouteContracts.ts
 * 2. Mobile screens in baseline exist in mobile screens directory
 * 3. Backend APIs in baseline have corresponding route handlers
 * 4. Status in baseline matches actual implementation state
 *
 * Usage:
 *   node scripts/generate-phr-doc-code-mismatch-evidence.mjs
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, resolve, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const BASELINE_FILE = resolve(__dirname, '../products/phr/config/phr-usecase-baseline.json');
const EVIDENCE_FILE = resolve(__dirname, '../.kernel/evidence/phr/doc-code-mismatch.json');
const PHR_ROOT = resolve(__dirname, '../products/phr');

// ─── File utilities ─────────────────────────────────────────────────────────────

function walkFiles(dir, extensions) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== 'build' && entry !== '.gradle') {
      results.push(...walkFiles(full, extensions));
    } else if (stat.isFile() && extensions.some((ext) => full.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

function fileExists(path) {
  try {
    statSync(path);
    return true;
  } catch {
    return false;
  }
}

// ─── Validation checks ─────────────────────────────────────────────────────────

function checkWebRoute(route) {
  const routeContractsFile = join(PHR_ROOT, 'apps/web/src/phrRouteContracts.ts');
  if (!fileExists(routeContractsFile)) {
    return { exists: false, reason: 'phrRouteContracts.ts not found' };
  }
  
  const content = readFileSync(routeContractsFile, 'utf-8');
  const routePattern = new RegExp(`path\\s*:\\s*["']${route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["']`);
  const exists = routePattern.test(content);
  
  return { exists, reason: exists ? 'found in phrRouteContracts.ts' : 'not found in phrRouteContracts.ts' };
}

function checkMobileScreen(screen) {
  if (!screen) return { exists: true, reason: 'no mobile screen defined' };
  
  const screensDir = join(PHR_ROOT, 'apps/mobile/src/screens');
  if (!fileExists(screensDir)) {
    return { exists: false, reason: 'mobile screens directory not found' };
  }
  
  const screenFiles = walkFiles(screensDir, ['.tsx', '.ts']);
  const screenName = screen.replace(/-/g, '').toLowerCase();
  const exists = screenFiles.some(f => f.toLowerCase().includes(screenName));
  
  return { exists, reason: exists ? 'screen file found' : 'screen file not found' };
}

function checkBackendApi(api) {
  const routesDir = join(PHR_ROOT, 'src/main/java/com/ghatana/phr/api/routes');
  if (!fileExists(routesDir)) {
    return { exists: false, reason: 'backend routes directory not found' };
  }
  
  const routeFiles = walkFiles(routesDir, ['.java']);
  const apiMethod = api.split(' ')[0]; // GET, POST, etc.
  const apiPath = api.split(' ')[1]; // /path/param
  
  for (const file of routeFiles) {
    const content = readFileSync(file, 'utf-8');
    if (content.includes(apiMethod) && content.includes(apiPath)) {
      return { exists: true, reason: `found in ${relative(PHR_ROOT, file)}` };
    }
  }
  
  return { exists: false, reason: 'backend route handler not found' };
}

// ─── Main ─────────────────────────────────────────────────────────────────────

function main() {
  console.log('🔍 Generating PHR doc/code mismatch evidence...\n');

  const baseline = JSON.parse(readFileSync(BASELINE_FILE, 'utf-8'));
  const usecases = baseline.usecases;

  const mismatches = [];

  for (const uc of usecases) {
    const ucMismatches = [];

    // Check web route
    if (uc.webRoute) {
      const webCheck = checkWebRoute(uc.webRoute);
      if (!webCheck.exists) {
        ucMismatches.push({
          type: 'web-route-missing',
          expected: uc.webRoute,
          reason: webCheck.reason
        });
      }
    }

    // Check mobile screen
    if (uc.mobileScreen) {
      const mobileCheck = checkMobileScreen(uc.mobileScreen);
      if (!mobileCheck.exists) {
        ucMismatches.push({
          type: 'mobile-screen-missing',
          expected: uc.mobileScreen,
          reason: mobileCheck.reason
        });
      }
    }

    // Check backend APIs
    if (uc.backendApis && uc.backendApis.length > 0) {
      for (const api of uc.backendApis) {
        const backendCheck = checkBackendApi(api);
        if (!backendCheck.exists) {
          ucMismatches.push({
            type: 'backend-api-missing',
            expected: api,
            reason: backendCheck.reason
          });
        }
      }
    }

    // Check status consistency
    if (uc.status === 'implemented') {
      const hasMismatches = ucMismatches.length > 0;
      if (hasMismatches) {
        ucMismatches.push({
          type: 'status-inconsistency',
          expected: 'implemented',
          actual: 'partial (missing implementations)',
          reason: 'status says implemented but code is missing'
        });
      }
    }

    if (ucMismatches.length > 0) {
      mismatches.push({
        useCaseId: uc.id,
        useCase: uc.screen,
        persona: uc.persona,
        mismatches: ucMismatches
      });
    }
  }

  const evidence = {
    product: 'phr',
    version: baseline.version,
    generatedAt: new Date().toISOString(),
    totalUseCases: usecases.length,
    useCasesWithMismatches: mismatches.length,
    mismatches
  };

  writeFileSync(EVIDENCE_FILE, JSON.stringify(evidence, null, 2), 'utf-8');
  
  console.log(`✅ Generated doc/code mismatch evidence: ${EVIDENCE_FILE}`);
  console.log(`📊 ${usecases.length} use cases checked, ${mismatches.length} with mismatches\n`);
  
  if (mismatches.length > 0) {
    console.log('⚠️  Mismatches found:');
    for (const m of mismatches) {
      console.log(`  - ${m.useCaseId} (${m.persona}): ${m.mismatches.length} mismatch(es)`);
    }
  } else {
    console.log('✨ No mismatches found - documentation matches implementation');
  }
}

main();
