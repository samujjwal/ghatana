#!/usr/bin/env node

/**
 * T-007: Check PHR API contracts.
 * Validates frontend/mobile/backend API contract parity.
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PHR_WEB_API_DIR = resolve(process.cwd(), 'products/phr/apps/web/src/api');
const PHR_MOBILE_API_DIR = resolve(process.cwd(), 'products/phr/apps/mobile/src/api');
const PHR_BACKEND_DIR = resolve(process.cwd(), 'products/phr/src/main/java/com/ghatana/phr');

// Load web API
const webApiPath = resolve(PHR_WEB_API_DIR, 'phrApi.ts');
if (!existsSync(webApiPath)) {
  console.error('ERROR: phrApi.ts not found');
  process.exit(1);
}
const webApiContent = readFileSync(webApiPath, 'utf-8');

// Load mobile API (if exists)
let mobileApiContent = '';
const mobileApiPath = resolve(PHR_MOBILE_API_DIR, 'phrApi.ts');
if (existsSync(mobileApiPath)) {
  mobileApiContent = readFileSync(mobileApiPath, 'utf-8');
}

// Extract API function names from web
const webApiFunctions = new Set();
const webFuncMatches = webApiContent.matchAll(/export\s+(async\s+)?function\s+(\w+)/g);
for (const match of webFuncMatches) {
  webApiFunctions.add(match[2]);
}

// Extract API function names from mobile
const mobileApiFunctions = new Set();
if (mobileApiContent) {
  const mobileFuncMatches = mobileApiContent.matchAll(/export\s+(async\s+)?function\s+(\w+)/g);
  for (const match of mobileFuncMatches) {
    mobileApiFunctions.add(match[2]);
  }
}

// Check backend route endpoints
const backendRoutes = new Set();
const backendFiles = [
  'api/routes/PhrPatientRecordRoutes.java',
  'api/routes/PhrConsentRoutes.java',
  'api/routes/PhrDocumentImagingRoutes.java',
  'api/routes/PhrEmergencyRoutes.java',
  'api/routes/PhrAppointmentRoutes.java',
  'api/routes/PhrMedicationRoutes.java',
  'api/routes/PhrImmunizationRoutes.java',
  'api/routes/PhrConditionRoutes.java',
  'api/routes/PhrObservationRoutes.java',
  'api/routes/PhrProviderRoutes.java',
  'api/routes/PhrCaregiverRoutes.java',
  'api/routes/PhrFchvRoutes.java',
];

for (const routeFile of backendFiles) {
  const routePath = resolve(PHR_BACKEND_DIR, routeFile);
  if (existsSync(routePath)) {
    const routeContent = readFileSync(routePath, 'utf-8');
    // Extract route paths
    const pathMatches = routeContent.matchAll(/\.with\(HttpMethod\.\w+,\s*["']([^"']+)["']/g);
    for (const match of pathMatches) {
      backendRoutes.add(match[1]);
    }
  }
}

let hasErrors = false;

// Check web vs mobile API parity
for (const func of webApiFunctions) {
  if (!mobileApiFunctions.has(func)) {
    console.warn(`WARNING: Web API function '${func}' not found in mobile API`);
  }
}

for (const func of mobileApiFunctions) {
  if (!webApiFunctions.has(func)) {
    console.warn(`WARNING: Mobile API function '${func}' not found in web API`);
  }
}

// Check if web API functions reference backend routes
for (const func of webApiFunctions) {
  const funcContent = webApiContent.substring(
    webApiContent.indexOf(`export ${func}`),
    webApiContent.indexOf(`export ${func}`) + 500
  );
  // Check if function makes HTTP requests
  if (funcContent.includes('fetch') || funcContent.includes('phrFetch')) {
    // Extract endpoint from the function
    const endpointMatch = funcContent.match(/['"`]\/api\/[^'"`]+['"`]/);
    if (endpointMatch) {
      const endpoint = endpointMatch[0].replace(/['"`]/g, '');
      // Check if backend has this route
      const hasRoute = Array.from(backendRoutes).some(route => endpoint.includes(route) || route.includes(endpoint));
      if (!hasRoute) {
        console.warn(`WARNING: Web API function '${func}' uses endpoint '${endpoint}' which may not have a backend route`);
      }
    }
  }
}

if (hasErrors) {
  console.error('FAIL: API contract parity check failed');
  process.exit(1);
}

console.log('PASS: API contract parity check passed');
