#!/usr/bin/env node

/**
 * PHR Doc/Code Mismatch Evidence Generator
 * 
 * Compares the IA baseline (phr-usecase-baseline.json) against actual code implementation
 * to identify mismatches between documented routes/screens/APIs and what exists in the codebase.
 * 
 * Usage: node scripts/generate-phr-doc-code-mismatch.mjs
 */

import { readFileSync, writeFileSync, existsSync } from 'fs';
import { resolve, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');

// Paths
const BASELINE_PATH = join(REPO_ROOT, 'products/phr/config/phr-usecase-baseline.json');
const WEB_ROUTE_CONTRACTS_PATH = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteContracts.ts');
const MOBILE_SCREEN_DIR = join(REPO_ROOT, 'products/phr/apps/mobile/src/screens');
const WEB_PAGE_DIR = join(REPO_ROOT, 'products/phr/apps/web/src/pages');
const OUTPUT_PATH = join(REPO_ROOT, '.kernel/evidence/phr/doc-code-mismatch.json');

// Load baseline
function loadBaseline() {
  try {
    const content = readFileSync(BASELINE_PATH, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Failed to load baseline from ${BASELINE_PATH}:`, error.message);
    process.exit(1);
  }
}

// Extract routes from web route contracts
function extractWebRoutes() {
  try {
    const content = readFileSync(WEB_ROUTE_CONTRACTS_PATH, 'utf-8');
    const routes = [];
    
    // Extract route patterns from the TypeScript file
    const routePattern = /['"`]\/[^'"`]+['"`]/g;
    const matches = content.match(routePattern) || [];
    
    matches.forEach(match => {
      const route = match.replace(/['"`]/g, '');
      if (route.startsWith('/') && !route.includes(':')) {
        routes.push(route);
      }
    });
    
    return [...new Set(routes)]; // Deduplicate
  } catch (error) {
    console.warn(`Failed to extract web routes: ${error.message}`);
    return [];
  }
}

// Check if mobile screen exists
function mobileScreenExists(screenName) {
  if (!screenName) return null; // Not applicable
  
  const screenFiles = [
    join(MOBILE_SCREEN_DIR, `${screenName}.tsx`),
    join(MOBILE_SCREEN_DIR, `${screenName}.ts`),
    join(MOBILE_SCREEN_DIR, `${screenName}.js`),
  ];
  
  return screenFiles.some(file => existsSync(file));
}

// Check if web page exists
function webPageExists(pageName) {
  if (!pageName) return null; // Not applicable
  
  const pageFiles = [
    join(WEB_PAGE_DIR, `${pageName}.tsx`),
    join(WEB_PAGE_DIR, `${pageName}.ts`),
    join(WEB_PAGE_DIR, `${pageName}.jsx`),
  ];
  
  return pageFiles.some(file => existsSync(file));
}

// Check if route exists in web contracts
function webRouteExists(route) {
  if (!route) return null; // Not applicable
  
  try {
    const content = readFileSync(WEB_ROUTE_CONTRACTS_PATH, 'utf-8');
    // Handle comma-separated routes
    const routes = route.split(',').map(r => r.trim());
    
    return routes.every(r => {
      const pattern = r.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
      const regex = new RegExp(`['"\`]${pattern}['"\`]`);
      return regex.test(content);
    });
  } catch (error) {
    return false;
  }
}

// Analyze mismatches
function analyzeMismatches(baseline) {
  const mismatches = [];
  const webRoutes = extractWebRoutes();
  
  baseline.usecases.forEach(usecase => {
    const issues = [];
    
    // Check web route
    if (usecase.webRoute) {
      const routeExists = webRouteExists(usecase.webRoute);
      if (routeExists === false) {
        issues.push({
          type: 'missing_web_route',
          documented: usecase.webRoute,
          severity: 'high'
        });
      }
    }
    
    // Check web page
    if (usecase.screen) {
      const pageExists = webPageExists(usecase.screen.split(' / ')[0]);
      if (pageExists === false) {
        issues.push({
          type: 'missing_web_page',
          documented: usecase.screen,
          severity: 'high'
        });
      }
    }
    
    // Check mobile screen
    if (usecase.mobileScreen) {
      const screenExists = mobileScreenExists(usecase.mobileScreen);
      if (screenExists === false) {
        issues.push({
          type: 'missing_mobile_screen',
          documented: usecase.mobileScreen,
          severity: usecase.status === 'implemented' ? 'high' : 'medium'
        });
      }
    }
    
    // Check status consistency
    if (usecase.status === 'implemented' && issues.length > 0) {
      issues.forEach(issue => {
        issue.inconsistency = 'marked_implemented_but_missing_code';
      });
    }
    
    if (issues.length > 0) {
      mismatches.push({
        usecaseId: usecase.id,
        persona: usecase.persona,
        screen: usecase.screen,
        iaRoute: usecase.iaRoute,
        webRoute: usecase.webRoute,
        mobileScreen: usecase.mobileScreen,
        status: usecase.status,
        phase: usecase.phase,
        issues
      });
    }
  });
  
  // Check for undocumented routes in web contracts
  const documentedRoutes = new Set();
  baseline.usecases.forEach(uc => {
    if (uc.webRoute) {
      uc.webRoute.split(',').forEach(r => documentedRoutes.add(r.trim()));
    }
  });
  
  const undocumentedRoutes = webRoutes.filter(route => 
    !documentedRoutes.has(route) && 
    !route.startsWith('/api') &&
    route !== '/' &&
    route !== '/login' &&
    route !== '/forbidden' &&
    route !== '/not-found'
  );
  
  return {
    mismatches,
    undocumentedRoutes,
    summary: {
      totalUsecases: baseline.usecases.length,
      usecasesWithMismatches: mismatches.length,
      undocumentedWebRoutes: undocumentedRoutes.length
    }
  };
}

// Generate evidence
function generateEvidence(baseline, analysis) {
  const evidence = {
    product: 'phr',
    version: baseline.version,
    generatedAt: new Date().toISOString(),
    baselinePath: BASELINE_PATH,
    summary: analysis.summary,
    mismatches: analysis.mismatches,
    undocumentedRoutes: analysis.undocumentedRoutes,
    severityBreakdown: {
      high: analysis.mismatches.filter(m => 
        m.issues.some(i => i.severity === 'high')
      ).length,
      medium: analysis.mismatches.filter(m => 
        m.issues.every(i => i.severity === 'medium')
      ).length
    }
  };
  
  return evidence;
}

// Main execution
function main() {
  console.log('Generating PHR doc/code mismatch evidence...');
  
  const baseline = loadBaseline();
  const analysis = analyzeMismatches(baseline);
  const evidence = generateEvidence(baseline, analysis);
  
  // Ensure output directory exists
  const outputDir = join(REPO_ROOT, '.kernel/evidence/phr');
  try {
    writeFileSync(OUTPUT_PATH, JSON.stringify(evidence, null, 2), 'utf-8');
    console.log(`✅ Evidence written to ${OUTPUT_PATH}`);
    console.log(`   Summary: ${analysis.summary.usecasesWithMismatches}/${analysis.summary.totalUsecases} usecases have mismatches`);
    console.log(`   Undocumented web routes: ${analysis.summary.undocumentedWebRoutes}`);
  } catch (error) {
    console.error(`Failed to write evidence: ${error.message}`);
    process.exit(1);
  }
}

main();
