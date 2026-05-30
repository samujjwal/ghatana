#!/usr/bin/env node
/**
 * DC-P1-07: Generate route/security capability metadata from OpenAPI contracts.
 *
 * This script extracts route sensitivity and access level requirements from OpenAPI
 * contract files and generates UI capability/legacy mapping artifacts.
 *
 * Runtime truth posture is generated from DataCloudRouterBuilder + RouteSecurityRegistry
 * by generate-route-manifest.mjs.
 *
 * Usage:
 *   node scripts/generate-route-security-metadata.mjs [--check]
 *
 * @doc.type script
 * @doc.purpose Generate route/security capability metadata from contracts
 * @doc.layer product
 * @doc.pattern Generator
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.join(__dirname, '..');
const CONTRACTS_DIR = path.join(PROJECT_ROOT, 'contracts/openapi');
const OUTPUT_DIR = path.join(PROJECT_ROOT, 'delivery/ui/src/lib/routing');
const COMPATIBILITY_REGISTRY = path.join(CONTRACTS_DIR, 'route-compatibility-registry.yaml');

// Check mode flag
const CHECK_MODE = process.argv.includes('--check');

/**
 * Parsed route metadata from OpenAPI contract
 */
class RouteMetadata {
  constructor(path, method, operationId, sensitivity, accessLevel, requiresAuth, tenantRequired) {
    this.path = path;
    this.method = method.toUpperCase();
    this.operationId = operationId;
    this.sensitivity = sensitivity || 'INTERNAL';
    this.accessLevel = accessLevel || 'VIEWER';
    this.requiresAuth = requiresAuth !== false;
    this.tenantRequired = tenantRequired !== false;
  }
}

/**
 * DC-P1-07: Parse route compatibility registry YAML
 * Extracts canonical action routes and legacy route mappings
 */
function parseCompatibilityRegistry() {
  if (!fs.existsSync(COMPATIBILITY_REGISTRY)) {
    console.warn(`[DC-P1-07] Compatibility registry not found: ${COMPATIBILITY_REGISTRY}`);
    return { canonicalRoutes: [], legacyRoutes: [] };
  }

  const content = fs.readFileSync(COMPATIBILITY_REGISTRY, 'utf-8');
  const lines = content.split('\n');
  
  const canonicalRoutes = [];
  const legacyRoutes = [];
  let currentSection = null;
  let currentRoute = null;
  
  const readYamlScalar = (text) => {
    const idx = text.indexOf(':');
    if (idx < 0) {
      return '';
    }
    const raw = text.slice(idx + 1).trim();
    if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) {
      return raw.slice(1, -1);
    }
    return raw;
  };

  const readYamlInlineList = (text) => {
    const value = readYamlScalar(text);
    const match = value.match(/^\[([^\]]*)\]$/);
    if (!match) {
      return [];
    }
    return match[1]
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => {
        if ((item.startsWith('"') && item.endsWith('"')) || (item.startsWith("'") && item.endsWith("'"))) {
          return item.slice(1, -1);
        }
        return item;
      });
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    
    if (trimmed === 'legacy_routes:') {
      currentSection = 'legacy';
      continue;
    }
    
    if (trimmed === 'canonical_action_routes:') {
      currentSection = 'canonical';
      continue;
    }
    
    if (trimmed.startsWith('- path:')) {
      const path = readYamlScalar(trimmed);
      currentRoute = { path };
      if (currentSection === 'canonical') {
        canonicalRoutes.push(currentRoute);
      } else if (currentSection === 'legacy') {
        legacyRoutes.push(currentRoute);
      }
      continue;
    }
    
    if (currentRoute && trimmed.startsWith('methods:')) {
      currentRoute.methods = readYamlInlineList(trimmed);
      continue;
    }
    
    if (currentRoute && trimmed.startsWith('canonical:')) {
      currentRoute.canonical = readYamlScalar(trimmed);
      continue;
    }
    
    if (currentRoute && trimmed.startsWith('owner:')) {
      currentRoute.owner = readYamlScalar(trimmed);
      continue;
    }
    
    if (currentRoute && trimmed.startsWith('purpose:')) {
      currentRoute.purpose = readYamlScalar(trimmed);
      continue;
    }

    if (currentRoute && trimmed.startsWith('deprecated_since:')) {
      currentRoute.deprecated_since = readYamlScalar(trimmed);
      continue;
    }

    if (currentRoute && trimmed.startsWith('retirement_target:')) {
      currentRoute.retirement_target = readYamlScalar(trimmed);
      continue;
    }

    if (currentRoute && trimmed.startsWith('feature_flag:')) {
      currentRoute.feature_flag = readYamlScalar(trimmed);
      continue;
    }
  }
  
  console.log(`[DC-P1-07] Parsed ${canonicalRoutes.length} canonical routes from compatibility registry`);
  console.log(`[DC-P1-07] Parsed ${legacyRoutes.length} legacy routes from compatibility registry`);
  
  return { canonicalRoutes, legacyRoutes };
}

/**
 * Simple YAML parser for OpenAPI paths section
 * Extracts paths, methods, and extensions without requiring external dependencies
 */
function parseOpenApiContract(filePath) {
  console.log(`[DC-P1-07] Reading file: ${filePath}`);
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  console.log(`[DC-P1-07] File has ${lines.length} lines`);
  
  const routes = [];
  let currentPath = null;
  let currentMethod = null;
  let currentOperationId = null;
  let currentIndent = 0;
  let inPathsSection = false;
  let operationExtensions = {};
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    const indent = line.search(/\S|$/);
    
    // Detect paths section
    if (trimmed === 'paths:') {
      inPathsSection = true;
      continue;
    }
    
    // Exit paths section when we reach a top-level section
    if (inPathsSection && indent === 0 && trimmed && !trimmed.startsWith('#') && trimmed !== 'paths:') {
      break;
    }
    
    if (!inPathsSection) continue;
    
    // Detect path definition (2-space indent + /path:)
    const pathMatch = trimmed.match(/^(\/[^:]+):$/);
    if (pathMatch && indent === 2) {
      // Save previous operation if exists
      if (currentPath && currentMethod) {
        routes.push(new RouteMetadata(
          currentPath,
          currentMethod,
          currentOperationId,
          operationExtensions.sensitivity || inferSensitivity(currentPath, currentMethod),
          operationExtensions.accessLevel || inferAccessLevel(currentPath, currentMethod),
          inferRequiresAuth(currentPath),
          inferTenantRequired(currentPath)
        ));
      }
      currentPath = pathMatch[1];
      currentMethod = null;
      currentOperationId = null;
      operationExtensions = {};
      continue;
    }
    
    // Detect HTTP method (4-space indent + get:/post:/etc)
    const methodMatch = trimmed.match(/^(get|post|put|patch|delete|head|options|trace):$/);
    if (methodMatch && indent === 4 && currentPath) {
      // Save previous operation if exists
      if (currentMethod) {
        routes.push(new RouteMetadata(
          currentPath,
          currentMethod,
          currentOperationId,
          operationExtensions.sensitivity || inferSensitivity(currentPath, currentMethod),
          operationExtensions.accessLevel || inferAccessLevel(currentPath, currentMethod),
          inferRequiresAuth(currentPath),
          inferTenantRequired(currentPath)
        ));
      }
      currentMethod = methodMatch[1];
      currentOperationId = null;
      operationExtensions = {};
      continue;
    }
    
    // Extract operationId and extensions (6-space indent or deeper)
    if (currentMethod && indent >= 6) {
      if (trimmed.startsWith('operationId:')) {
        currentOperationId = trimmed.split(':')[1].trim();
      }
      if (trimmed.startsWith('x-ghatana-sensitivity:')) {
        operationExtensions.sensitivity = trimmed.split(':')[1].trim();
      }
      if (trimmed.startsWith('x-ghatana-access-level:')) {
        operationExtensions.accessLevel = trimmed.split(':')[1].trim();
      }
    }
    
    // Exit operation when indentation returns to 4 spaces (new method)
    if (currentMethod && indent === 4 && !methodMatch) {
      routes.push(new RouteMetadata(
        currentPath,
        currentMethod,
        currentOperationId,
        operationExtensions.sensitivity || inferSensitivity(currentPath, currentMethod),
        operationExtensions.accessLevel || inferAccessLevel(currentPath, currentMethod),
        inferRequiresAuth(currentPath),
        inferTenantRequired(currentPath)
      ));
      currentMethod = null;
      currentOperationId = null;
      operationExtensions = {};
    }
  }
  
  // Handle last operation
  if (currentPath && currentMethod) {
    routes.push(new RouteMetadata(
      currentPath,
      currentMethod,
      currentOperationId,
      operationExtensions.sensitivity || inferSensitivity(currentPath, currentMethod),
      operationExtensions.accessLevel || inferAccessLevel(currentPath, currentMethod),
      inferRequiresAuth(currentPath),
      inferTenantRequired(currentPath)
    ));
  }
  
  console.log(`[DC-P1-07] Parsed ${routes.length} routes from ${filePath}`);
  return routes;
}

/**
 * Infer sensitivity from method/path
 */
function inferSensitivity(path, method) {
  const lowerPath = path.toLowerCase();
  
  if (lowerPath.includes('/governance/') || lowerPath.includes('/learning/review/')) {
    return 'CRITICAL';
  }
  return 'INTERNAL';
}

/**
 * Infer access level from path/method patterns
 */
function inferAccessLevel(path, method) {
  const lowerPath = path.toLowerCase();
  const lowerMethod = method.toLowerCase();
  
  // ADMIN routes: governance mutations, settings, keys, approvals, model promote
  if (lowerPath.includes('/governance/') && 
      (lowerPath.includes('purge') || lowerPath.includes('redact') || 
       lowerPath.includes('policies') || lowerPath.includes('toggle'))) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/settings/') || lowerPath.includes('/keys/') || 
      lowerPath.includes('/approvals/')) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/promote')) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/connectors') && 
      (lowerPath.includes('rotate') || lowerPath.includes('enable') || 
       lowerPath.includes('disable'))) {
    return 'ADMIN';
  }
  
  // AUDITOR routes: compliance summaries
  if (lowerPath.includes('/compliance/summary')) {
    return 'AUDITOR';
  }
  
  // OPERATOR routes: mutations, pipeline operations, learning approvals, memory ops
  if (lowerMethod === 'delete' && lowerPath.includes('/entities/')) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/pipelines/') && 
      (lowerPath.includes('execute') || lowerPath.includes('cancel'))) {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/executions/') && 
      (lowerPath.includes('cancel') || lowerPath.includes('retry') || 
       lowerPath.includes('rollback') || lowerPath.includes('restore'))) {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/learning/review/') && 
      (lowerPath.includes('approve') || lowerPath.includes('reject'))) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/memory/') && 
      (lowerPath.includes('delete') || lowerPath.includes('retain'))) {
    return 'ADMIN';
  }
  if (lowerPath.includes('/memory/') && lowerPath.includes('search')) {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/events') && lowerMethod === 'post') {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/entities/') && lowerMethod === 'post') {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/alerts/') && 
      (lowerPath.includes('remediate') || lowerPath.includes('acknowledge') || 
       lowerPath.includes('resolve') || lowerPath.includes('escalate'))) {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/alerts/rules')) {
    return 'OPERATOR';
  }
  if (lowerPath.includes('/context') && 
      (lowerMethod === 'put' || lowerMethod === 'delete')) {
    return 'OPERATOR';
  }
  
  // VIEWER routes: catalog reads, agents catalog
  if (lowerPath.includes('/agents/catalog')) {
    return 'VIEWER';
  }
  
  return 'VIEWER';
}

/**
 * Infer whether auth is required
 */
function inferRequiresAuth(path) {
  const lowerPath = path.toLowerCase();
  if (lowerPath.includes('/health') || lowerPath.includes('/ready') || lowerPath.includes('/live') ||
      lowerPath.includes('/metrics') || lowerPath.includes('/info')) {
    return false;
  }
  return true;
}

/**
 * Infer whether tenant is required
 */
function inferTenantRequired(path) {
  const lowerPath = path.toLowerCase();
  if (lowerPath.includes('/health') || lowerPath.includes('/ready') || lowerPath.includes('/live') ||
      lowerPath.includes('/metrics') || lowerPath.includes('/info')) {
    return false;
  }
  return true;
}


/**
 * Generate TypeScript route registry from parsed metadata
 */
function generateTypeScriptRegistry(routes, contractName) {
  const imports = [
    `// Auto-generated from ${contractName} by generate-route-security-metadata.mjs`,
    '// DC-P1-07: Do not edit manually - regenerate with the script',
    '',
    'export interface RouteCapability {',
    '  path: string;',
    '  method: string;',
    '  sensitivity: \'PUBLIC\' | \'INTERNAL\' | \'SENSITIVE\' | \'CRITICAL\';',
    '  accessLevel: \'VIEWER\' | \'OPERATOR\' | \'AUDITOR\' | \'ADMIN\';',
    '  requiresAuth: boolean;',
    '  tenantRequired: boolean;',
    '}',
    '',
    'export const routeCapabilities: RouteCapability[] = [',
  ];
  
  for (const route of routes) {
    imports.push(`  {`);
    imports.push(`    path: '${route.path}',`);
    imports.push(`    method: '${route.method}',`);
    imports.push(`    sensitivity: '${route.sensitivity}',`);
    imports.push(`    accessLevel: '${route.accessLevel}',`);
    imports.push(`    requiresAuth: ${route.requiresAuth},`);
    imports.push(`    tenantRequired: ${route.tenantRequired},`);
    imports.push(`  },`);
  }
  
  imports.push('];');
  imports.push('');
  
  // Generate sensitivity lookup
  const sensitivities = [...new Set(routes.map(r => r.sensitivity))];
  imports.push('export const sensitivityLevels = {');
  for (const sens of sensitivities) {
    imports.push(`  ${sens}: '${sens}',`);
  }
  imports.push('} as const;');
  imports.push('');
  
  // Generate access level lookup
  const accessLevels = [...new Set(routes.map(r => r.accessLevel))];
  imports.push('export const accessLevels = {');
  for (const level of accessLevels) {
    imports.push(`  ${level}: '${level}',`);
  }
  imports.push('} as const;');
  
  return imports.join('\n');
}

/**
 * Generate Java RouteActionAccessRegistry entries
 */
function generateJavaRegistry(routes, contractName) {
  const lines = [
    `// Auto-generated from ${contractName} by generate-route-security-metadata.mjs`,
    '// DC-P1-07: Do not edit manually - regenerate with the script',
    '',
    '// Add these entries to RouteActionAccessRegistry.ACCESS_BY_ACTION',
    '',
  ];
  
  for (const route of routes) {
    const normalizedPath = route.path.replace(/\{([^}]+)\}/g, ':$1');
    const actionKey = `${route.method} ${normalizedPath}`;
    
    // Map sensitivity to access level
    let accessLevel = 'VIEWER';
    if (route.sensitivity === 'CRITICAL') accessLevel = 'ADMIN';
    else if (route.sensitivity === 'SENSITIVE') accessLevel = 'OPERATOR';
    else if (route.sensitivity === 'INTERNAL') accessLevel = 'VIEWER';
    
    // Override with explicit access level if different
    if (route.accessLevel === 'ADMIN') accessLevel = 'ADMIN';
    else if (route.accessLevel === 'AUDITOR') accessLevel = 'AUDITOR';
    else if (route.accessLevel === 'OPERATOR') accessLevel = 'OPERATOR';
    
    lines.push(`Map.entry("${actionKey}", DataCloudSecurityFilter.AccessLevel.${accessLevel}),`);
  }
  
  return lines.join('\n');
}

/**
 * DC-P1-07: Validate that all canonical routes from registry exist in OpenAPI contracts
 */
function validateCanonicalRoutesInContracts(canonicalRoutes, contractRoutes) {
  const contractRouteKeys = new Set(
    contractRoutes.map(r => `${r.method.toLowerCase()} ${r.path}`)
  );
  
  const missingRoutes = [];
  for (const canonical of canonicalRoutes) {
    for (const method of canonical.methods) {
      const key = `${method.toLowerCase()} ${canonical.path}`;
      if (!contractRouteKeys.has(key)) {
        missingRoutes.push({
          path: canonical.path,
          method: method,
          owner: canonical.owner,
          purpose: canonical.purpose
        });
      }
    }
  }
  
  if (missingRoutes.length > 0) {
    console.error('[DC-P1-07] ERROR: Canonical routes missing from OpenAPI contracts:');
    for (const route of missingRoutes) {
      console.error(`  - ${route.method} ${route.path} (${route.owner}: ${route.purpose})`);
    }
    console.error('[DC-P1-07] All canonical routes must be defined in OpenAPI contracts.');
    process.exit(1);
  }
  
  console.log(`[DC-P1-07] All ${canonicalRoutes.length} canonical routes found in OpenAPI contracts`);
}

/**
 * DC-P1-07: Generate compatibility metadata for legacy routes
 */
function generateLegacyCompatibilityMetadata(legacyRoutes) {
  const lines = [
    '// Auto-generated from route-compatibility-registry.yaml by generate-route-security-metadata.mjs',
    '// DC-P1-07: Legacy route compatibility metadata - do not edit manually',
    '',
    'export interface LegacyRouteMapping {',
    '  path: string;',
    '  canonical: string;',
    '  methods: string[];',
    '  deprecatedSince: string;',
    '  retirementTarget: string;',
    '  featureFlag: string;',
    '}',
    '',
    'export const legacyRouteMappings: LegacyRouteMapping[] = [',
  ];
  
  const seen = new Set();
  for (const route of legacyRoutes) {
    const methods = Array.isArray(route.methods) ? route.methods : [];
    const routeKey = `${route.path}|${route.canonical}|${methods.slice().sort().join(',')}`;
    if (seen.has(routeKey)) {
      continue;
    }
    seen.add(routeKey);

    lines.push('  {');
    lines.push(`    path: '${route.path}',`);
    lines.push(`    canonical: '${route.canonical}',`);
    lines.push(`    methods: [${methods.map(m => `'${m}'`).join(', ')}],`);
    lines.push(`    deprecatedSince: '${route.deprecated_since || '2026-03-27'}',`);
    lines.push(`    retirementTarget: '${route.retirement_target || '2026-12-31'}',`);
    lines.push(`    featureFlag: '${route.feature_flag || 'DataCloudFeature.LEGACY_ACTION_ROUTES'}',`);
    lines.push('  },');
  }
  
  lines.push('];');
  
  return lines.join('\n');
}

/**
 * Main execution
 */
function main() {
  console.log('[DC-P1-07] Generating route/security capability metadata from contracts...');
  
  // DC-P1-07: Parse compatibility registry first
  const { canonicalRoutes, legacyRoutes } = parseCompatibilityRegistry();
  
  const dataCloudYaml = path.join(CONTRACTS_DIR, 'data-cloud.yaml');
  const actionPlaneYaml = path.join(CONTRACTS_DIR, 'action-plane.yaml');
  
  if (!fs.existsSync(dataCloudYaml)) {
    console.error(`[ERROR] Contract file not found: ${dataCloudYaml}`);
    process.exit(1);
  }
  
  if (!fs.existsSync(actionPlaneYaml)) {
    console.error(`[ERROR] Contract file not found: ${actionPlaneYaml}`);
    process.exit(1);
  }
  
  console.log(`[DC-P1-07] Parsing ${dataCloudYaml}...`);
  const dataCloudRoutes = parseOpenApiContract(dataCloudYaml);
  console.log(`[DC-P1-07] Found ${dataCloudRoutes.length} routes in data-cloud.yaml`);
  
  console.log(`[DC-P1-07] Parsing ${actionPlaneYaml}...`);
  const actionPlaneRoutes = parseOpenApiContract(actionPlaneYaml);
  console.log(`[DC-P1-07] Found ${actionPlaneRoutes.length} routes in action-plane.yaml`);
  
  const allRoutes = [...dataCloudRoutes, ...actionPlaneRoutes];
  console.log(`[DC-P1-07] Total routes: ${allRoutes.length}`);
  
  // DC-P1-07: Validate all canonical routes exist in contracts
  if (canonicalRoutes.length > 0) {
    validateCanonicalRoutesInContracts(canonicalRoutes, allRoutes);
  }
  
  // Generate TypeScript registry
  const tsRegistry = generateTypeScriptRegistry(allRoutes, 'data-cloud.yaml + action-plane.yaml');
  const tsOutputPath = path.join(OUTPUT_DIR, 'RouteCapabilities.generated.ts');
  
  if (CHECK_MODE) {
    if (fs.existsSync(tsOutputPath)) {
      const existing = fs.readFileSync(tsOutputPath, 'utf-8');
      if (existing !== tsRegistry) {
        console.error('[ERROR] Generated TypeScript registry does not match existing file');
        console.error('[ERROR] Run without --check to regenerate: node scripts/generate-route-security-metadata.mjs');
        process.exit(1);
      }
    } else {
      console.error('[ERROR] Generated TypeScript registry does not exist');
      console.error('[ERROR] Run without --check to generate: node scripts/generate-route-security-metadata.mjs');
      process.exit(1);
    }
  } else {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    fs.writeFileSync(tsOutputPath, tsRegistry);
    console.log(`[DC-P1-07] Generated TypeScript registry: ${tsOutputPath}`);
  }
  
  // DC-P1-07: Generate legacy route compatibility metadata
  if (legacyRoutes.length > 0) {
    const legacyMetadata = generateLegacyCompatibilityMetadata(legacyRoutes);
    const legacyOutputPath = path.join(OUTPUT_DIR, 'LegacyRouteMappings.generated.ts');
    
    if (CHECK_MODE) {
      if (fs.existsSync(legacyOutputPath)) {
        const existing = fs.readFileSync(legacyOutputPath, 'utf-8');
        if (existing !== legacyMetadata) {
          console.error('[ERROR] Generated legacy route compatibility metadata does not match existing file');
          console.error('[ERROR] Run without --check to regenerate: node scripts/generate-route-security-metadata.mjs');
          process.exit(1);
        }
      }
    } else {
      fs.mkdirSync(OUTPUT_DIR, { recursive: true });
      fs.writeFileSync(legacyOutputPath, legacyMetadata);
      console.log(`[DC-P1-07] Generated legacy route compatibility metadata: ${legacyOutputPath}`);
    }
  }
  
  console.log('[DC-P1-07] Route/security capability metadata generation completed successfully');
}

/**
 * Generate runtime-truth posture metadata
 */
function generateRuntimeTruthPosture(routes) {
  const lines = [
    '// Auto-generated from contracts by generate-route-security-metadata.mjs',
    '// DC-P1-07: Runtime-truth posture metadata for route gating',
    '',
    'export interface RuntimeTruthRoutePosture {',
    '  path: string;',
    '  method: string;',
    '  sensitivity: string;',
    '  requiresAuth: boolean;',
    '  requiresTenant: boolean;',
    '  requiredCapabilities: string[];',
    '}',
    '',
    'export const runtimeTruthRoutePosture: RuntimeTruthRoutePosture[] = [',
  ];
  
  for (const route of routes) {
    const capabilities = [];
    if (route.requiresAuth) capabilities.push('auth');
    if (route.tenantRequired) capabilities.push('tenant');
    if (route.sensitivity === 'CRITICAL') capabilities.push('policy');
    if (route.sensitivity === 'SENSITIVE' || route.sensitivity === 'CRITICAL') capabilities.push('audit');
    
    lines.push(`  {`);
    lines.push(`    path: '${route.path}',`);
    lines.push(`    method: '${route.method}',`);
    lines.push(`    sensitivity: '${route.sensitivity}',`);
    lines.push(`    requiresAuth: ${route.requiresAuth},`);
    lines.push(`    requiresTenant: ${route.tenantRequired},`);
    lines.push(`    requiredCapabilities: [${capabilities.map(c => `'${c}'`).join(', ')}],`);
    lines.push(`  },`);
  }
  
  lines.push('];');
  
  return lines.join('\n');
}

// Run main
main();
