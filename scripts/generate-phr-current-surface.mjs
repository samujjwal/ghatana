#!/usr/bin/env node

/**
 * Generate PHR Current Surface Documentation
 *
 * This script generates a human-readable document describing the current
 * implementation surface of the PHR product, including web routes, mobile screens,
 * backend APIs, and their implementation status.
 */

import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

function loadJson(relativePath) {
  try {
    const content = readFileSync(join(REPO_ROOT, relativePath), 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Failed to load ${relativePath}:`, error.message);
    return null;
  }
}

function loadTs(relativePath) {
  try {
    const content = readFileSync(join(REPO_ROOT, relativePath), 'utf-8');
    return content;
  } catch (error) {
    console.error(`Failed to load ${relativePath}:`, error.message);
    return null;
  }
}

function extractRouteContracts(tsContent) {
  const routes = [];
  const routePattern = /{\s*path:\s*['"`]([^'"`]+)['"`],\s*label:\s*t\(['"`]([^'"`]+)['"`]\)/g;
  let match;
  
  while ((match = routePattern.exec(tsContent)) !== null) {
    routes.push({
      path: match[1],
      label: match[2]
    });
  }
  
  return routes;
}

function generateMarkdown(baseline, webRoutes) {
  const lines = [];
  
  lines.push('# PHR Current Implementation Surface');
  lines.push('');
  lines.push('**Generated:** ' + new Date().toISOString());
  lines.push('');
  lines.push('This document describes the current implementation surface of the PHR product,');
  lines.push('including web routes, mobile screens, backend APIs, and their implementation status.');
  lines.push('');
  lines.push('---');
  lines.push('');
  
  // Summary Statistics
  lines.push('## Summary');
  lines.push('');
  const totalUsecases = baseline.usecases.length;
  const implemented = baseline.usecases.filter(u => u.status === 'implemented').length;
  const partial = baseline.usecases.filter(u => u.status === 'partial').length;
  const featureFlagged = baseline.usecases.filter(u => u.status === 'feature_flagged').length;
  
  lines.push(`- **Total Use Cases:** ${totalUsecases}`);
  lines.push(`- **Fully Implemented:** ${implemented} (${Math.round(implemented/totalUsecases*100)}%)`);
  lines.push(`- **Partial Implementation:** ${partial} (${Math.round(partial/totalUsecases*100)}%)`);
  lines.push(`- **Feature-Flagged:** ${featureFlagged} (${Math.round(featureFlagged/totalUsecases*100)}%)`);
  lines.push(`- **Web Routes:** ${webRoutes.length}`);
  lines.push('');
  lines.push('---');
  lines.push('');
  
  // Web Routes
  lines.push('## Web Routes');
  lines.push('');
  lines.push('The following routes are currently implemented in the PHR web application:');
  lines.push('');
  lines.push('| Path | Label | Minimum Role | Status |');
  lines.push('|------|-------|--------------|--------|');
  
  for (const route of webRoutes) {
    const baselineEntry = baseline.usecases.find(u => u.webRoute === route.path);
    const status = baselineEntry ? baselineEntry.status : 'unknown';
    lines.push(`| ${route.path} | ${route.label} | patient | ${status} |`);
  }
  lines.push('');
  lines.push('---');
  lines.push('');
  
  // Mobile Screens
  lines.push('## Mobile Screens');
  lines.push('');
  lines.push('The following screens are currently implemented in the PHR mobile application:');
  lines.push('');
  lines.push('| Screen | Status | Offline Support |');
  lines.push('|--------|--------|----------------|');
  
  const mobileScreens = [
    { name: 'DashboardScreen', status: 'implemented', offline: true },
    { name: 'RecordsScreen', status: 'implemented', offline: true },
    { name: 'RecordDetailScreen', status: 'implemented', offline: true },
    { name: 'ConsentScreen', status: 'implemented', offline: false },
    { name: 'NotificationsScreen', status: 'implemented', offline: true },
    { name: 'EmergencyAccessScreen', status: 'implemented', offline: false },
    { name: 'SettingsScreen', status: 'implemented', offline: false },
    { name: 'LoginScreen', status: 'implemented', offline: false },
  ];
  
  for (const screen of mobileScreens) {
    lines.push(`| ${screen.name} | ${screen.status} | ${screen.offline ? 'Yes' : 'No'} |`);
  }
  lines.push('');
  lines.push('---');
  lines.push('');
  
  // Backend APIs
  lines.push('## Backend APIs');
  lines.push('');
  lines.push('The following backend API endpoints are currently implemented:');
  lines.push('');
  lines.push('| Endpoint | Method | Purpose |');
  lines.push('|----------|--------|---------|');
  
  const backendApis = [
    { endpoint: '/auth/login', method: 'POST', purpose: 'Session bootstrap via credentials' },
    { endpoint: '/auth/logout', method: 'POST', purpose: 'Session termination' },
    { endpoint: '/auth/me', method: 'GET', purpose: 'Current session validation' },
    { endpoint: '/route-entitlements', method: 'GET', purpose: 'Route/content entitlement payload' },
    { endpoint: '/release-readiness', method: 'GET', purpose: 'Admin release readiness runtime truth' },
    { endpoint: '/audit/events', method: 'GET', purpose: 'Paginated audit event trail' },
    { endpoint: '/emergency/access', method: 'POST', purpose: 'Log emergency break-glass access' },
    { endpoint: '/emergency/reviews', method: 'GET', purpose: 'Retrieve emergency access reviews' },
  ];
  
  for (const api of backendApis) {
    lines.push(`| ${api.endpoint} | ${api.method} | ${api.purpose} |`);
  }
  lines.push('');
  lines.push('---');
  lines.push('');
  
  // Implementation Status by Persona
  lines.push('## Implementation Status by Persona');
  lines.push('');
  
  const personas = ['patient', 'clinician', 'caregiver', 'admin', 'fchv'];
  
  for (const persona of personas) {
    const personaUsecases = baseline.usecases.filter(u => u.persona === persona);
    const personaImplemented = personaUsecases.filter(u => u.status === 'implemented').length;
    
    lines.push(`### ${persona.charAt(0).toUpperCase() + persona.slice(1)}`);
    lines.push('');
    lines.push(`- **Total Use Cases:** ${personaUsecases.length}`);
    lines.push(`- **Implemented:** ${personaImplemented} (${personaUsecases.length > 0 ? Math.round(personaImplemented/personaUsecases.length*100) : 0}%)`);
    lines.push('');
  }
  
  lines.push('---');
  lines.push('');
  lines.push('*This document is auto-generated. Do not edit manually.*');
  
  return lines.join('\n');
}

function main() {
  console.log('Generating PHR current surface documentation...');
  
  // Load IA baseline
  const baseline = loadJson('products/phr/config/phr-usecase-baseline.json');
  if (!baseline) {
    console.error('Failed to load IA baseline');
    process.exit(1);
  }
  
  // Load web route contracts
  const routeContractsTs = loadTs('products/phr/apps/web/src/phrRouteContracts.ts');
  if (!routeContractsTs) {
    console.error('Failed to load route contracts');
    process.exit(1);
  }
  
  const webRoutes = extractRouteContracts(routeContractsTs);
  
  // Generate markdown
  const markdown = generateMarkdown(baseline, webRoutes);
  
  // Ensure output directory exists
  const outputDir = join(REPO_ROOT, 'products/phr/docs/current-state');
  mkdirSync(outputDir, { recursive: true });
  
  // Write output
  const outputPath = join(outputDir, 'generated-current-surface.md');
  writeFileSync(outputPath, markdown, 'utf-8');
  
  console.log(`Generated: ${outputPath}`);
  console.log(`  - ${webRoutes.length} web routes`);
  console.log(`  - ${baseline.usecases.length} use cases`);
}

main();
