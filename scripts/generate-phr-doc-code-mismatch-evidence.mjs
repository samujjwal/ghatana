#!/usr/bin/env node

/**
 * PHR doc/code mismatch evidence generator.
 *
 * Compares the PHR use-case baseline against the canonical Kernel route
 * contract, registered web route elements, mobile screen files, and backend
 * route adapters.
 */

import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');
const PHR_ROOT = join(REPO_ROOT, 'products/phr');
const BASELINE_FILE = join(PHR_ROOT, 'config/phr-usecase-baseline.json');
const ROUTE_CONTRACT_FILE = join(PHR_ROOT, 'config/phr-route-contract.json');
const ROUTE_ELEMENTS_FILE = join(PHR_ROOT, 'apps/web/src/phrRouteElements.tsx');
const MOBILE_SCREENS_DIR = join(PHR_ROOT, 'apps/mobile/src/screens');
const WEB_PAGES_DIR = join(PHR_ROOT, 'apps/web/src/pages');
const BACKEND_ROUTES_DIR = join(PHR_ROOT, 'src/main/java/com/ghatana/phr/api/routes');
const HTTP_SERVER_FILE = join(PHR_ROOT, 'src/main/java/com/ghatana/phr/api/PhrHttpServer.java');
const EVIDENCE_FILE = join(REPO_ROOT, '.kernel/evidence/phr/doc-code-mismatch.json');

const MOBILE_SCREEN_FILE_BY_NAME = new Map([
  ['dashboard', 'DashboardScreen.tsx'],
  ['records', 'RecordsScreen.tsx'],
  ['record-detail', 'RecordDetailScreen.tsx'],
  ['consents', 'ConsentScreen.tsx'],
  ['notifications', 'NotificationsScreen.tsx'],
  ['emergency', 'EmergencyAccessScreen.tsx'],
  ['settings', 'SettingsScreen.tsx'],
]);

function loadJson(path) {
  return JSON.parse(readFileSync(path, 'utf-8'));
}

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

function splitRouteList(routeList) {
  if (!routeList) {
    return [];
  }
  return routeList.split(',').map((route) => route.trim()).filter(Boolean);
}

function normalizeRoutePath(route) {
  return route.replace(/\{([^}]+)\}/g, ':$1');
}

function implementedRoutePaths(routeContract) {
  return new Set(
    routeContract.routes
      .filter((route) => route.stability === 'stable' || route.stability === 'preview')
      .map((route) => route.path),
  );
}

function registeredRouteElementPaths() {
  const source = readFileSync(ROUTE_ELEMENTS_FILE, 'utf-8');
  const paths = new Set();
  const elementPathPattern = /^\s*['"](\/[^'"]+)['"]\s*:/gm;
  let match;
  while ((match = elementPathPattern.exec(source)) !== null) {
    paths.add(match[1]);
  }
  return paths;
}

function pageNames(screen) {
  if (!screen) {
    return [];
  }
  return screen.split('/').map((candidate) => candidate.trim()).filter((candidate) => candidate.endsWith('Page'));
}

function checkWebRoute(route, contractPaths, elementPaths) {
  const missing = [];
  for (const routePath of splitRouteList(route).map(normalizeRoutePath)) {
    if (!contractPaths.has(routePath)) {
      missing.push(`${routePath} not stable/preview in phr-route-contract.json`);
      continue;
    }
    if (!elementPaths.has(routePath)) {
      missing.push(`${routePath} not registered in phrRouteElements.tsx`);
    }
  }
  return {
    exists: missing.length === 0,
    reason: missing.length === 0 ? 'found in canonical route contract and route elements' : missing.join('; '),
  };
}

function checkWebPage(screen) {
  const missing = pageNames(screen).filter((pageName) => !existsSync(join(WEB_PAGES_DIR, `${pageName}.tsx`)));
  return {
    exists: missing.length === 0,
    reason: missing.length === 0 ? 'page file found' : `missing page file(s): ${missing.join(', ')}`,
  };
}

function checkMobileScreen(screen) {
  if (!screen) {
    return { exists: true, reason: 'no mobile screen defined' };
  }
  const fileName = MOBILE_SCREEN_FILE_BY_NAME.get(screen);
  if (!fileName) {
    return { exists: false, reason: `unknown mobile screen key '${screen}'` };
  }
  const exists = existsSync(join(MOBILE_SCREENS_DIR, fileName));
  return { exists, reason: exists ? `${fileName} found` : `${fileName} not found` };
}

function normalizeApiPath(path) {
  return path.split('?')[0].replace(/\/+$/, '') || '/';
}

function checkBackendApi(api, backendSource) {
  const [method, rawPath] = api.split(/\s+/);
  if (!method || !rawPath) {
    return { exists: false, reason: `invalid API declaration '${api}'` };
  }

  const path = normalizeApiPath(rawPath);
  const pathWithoutApiBase = path.replace(/^\/api\/v1/, '') || '/';
  const pathTokens = pathWithoutApiBase.split('/').filter((part) => part && !part.startsWith(':'));
  const methodExists = backendSource.includes(`HttpMethod.${method}`);
  const tokenCoverage = pathTokens.every((token) => (
    backendSource.includes(`/${token}`) ||
    backendSource.includes(`/${token}/*`) ||
    backendSource.includes(`/${token}:`)
  ));

  return {
    exists: methodExists && tokenCoverage,
    reason: methodExists && tokenCoverage
      ? 'method and path tokens found in backend route adapters'
      : `backend route handler not found for ${method} ${path}`,
  };
}

function backendRouteSource() {
  return [
    readFileSync(HTTP_SERVER_FILE, 'utf-8'),
    ...walkFiles(BACKEND_ROUTES_DIR, ['.java']).map((file) => readFileSync(file, 'utf-8')),
  ].join('\n');
}

function main() {
  console.log('Generating PHR doc/code mismatch evidence...\n');

  const baseline = loadJson(BASELINE_FILE);
  const routeContract = loadJson(ROUTE_CONTRACT_FILE);
  const contractPaths = implementedRoutePaths(routeContract);
  const elementPaths = registeredRouteElementPaths();
  const backendSource = backendRouteSource();
  const mismatches = [];

  for (const uc of baseline.usecases) {
    const ucMismatches = [];

    if (uc.webRoute && uc.status !== 'deferred') {
      const webCheck = checkWebRoute(uc.webRoute, contractPaths, elementPaths);
      if (!webCheck.exists) {
        ucMismatches.push({ type: 'web-route-missing', expected: uc.webRoute, reason: webCheck.reason });
      }

      const pageCheck = checkWebPage(uc.screen);
      if (!pageCheck.exists) {
        ucMismatches.push({ type: 'web-page-missing', expected: uc.screen, reason: pageCheck.reason });
      }
    }

    const mobileCheck = checkMobileScreen(uc.mobileScreen);
    if (!mobileCheck.exists) {
      ucMismatches.push({ type: 'mobile-screen-missing', expected: uc.mobileScreen, reason: mobileCheck.reason });
    }

    if (Array.isArray(uc.backendApis) && uc.backendApis.length > 0 && uc.status !== 'deferred') {
      for (const api of uc.backendApis) {
        const backendCheck = checkBackendApi(api, backendSource);
        if (!backendCheck.exists) {
          ucMismatches.push({ type: 'backend-api-missing', expected: api, reason: backendCheck.reason });
        }
      }
    }

    if (uc.status === 'implemented' && ucMismatches.length > 0) {
      ucMismatches.push({
        type: 'status-inconsistency',
        expected: 'implemented',
        actual: 'partial (missing implementations)',
        reason: 'status says implemented but code is missing',
      });
    }

    if (ucMismatches.length > 0) {
      mismatches.push({
        useCaseId: uc.id,
        useCase: uc.screen,
        persona: uc.persona,
        status: uc.status,
        mismatches: ucMismatches,
      });
    }
  }

  const evidence = {
    product: 'phr',
    version: baseline.version,
    generatedAt: new Date().toISOString(),
    totalUseCases: baseline.usecases.length,
    useCasesWithMismatches: mismatches.length,
    mismatches,
  };

  writeFileSync(EVIDENCE_FILE, JSON.stringify(evidence, null, 2), 'utf-8');

  console.log(`Generated doc/code mismatch evidence: ${EVIDENCE_FILE}`);
  console.log(`${baseline.usecases.length} use cases checked, ${mismatches.length} with mismatches\n`);

  if (mismatches.length > 0) {
    console.log('Mismatches found:');
    for (const mismatch of mismatches) {
      console.log(`  - ${mismatch.useCaseId} (${mismatch.persona}): ${mismatch.mismatches.length} mismatch(es)`);
    }
  } else {
    console.log('No mismatches found - documentation matches implementation');
  }
}

main();
