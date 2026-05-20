#!/usr/bin/env node
/**
 * DC-P1-07: Generate route/security/runtime-truth metadata from OpenAPI contracts.
 *
 * This script extracts route sensitivity, access level requirements, and runtime-truth
 * posture metadata from OpenAPI contract files and generates TypeScript/Java artifacts
 * for use in the Data Cloud launcher and UI.
 *
 * Usage:
 *   node scripts/generate-route-security-metadata.mjs [--check]
 *
 * @doc.type script
 * @doc.purpose Generate route/security/runtime-truth metadata from contracts
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
 * Parse OpenAPI YAML file and extract route metadata
 */
function parseOpenApiContract(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  
  const routes = [];
  let currentPath = null;
  let currentMethod = null;
  let currentOperationId = null;
  let inOperation = false;
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    
    // Detect path definition (2-space indent + /path:)
    const pathMatch = trimmed.match(/^(\/[^:]+):$/);
    if (pathMatch && line.startsWith('  ')) {
      currentPath = pathMatch[1];  // Preserve the leading slash
      continue;
    }
    
    // Detect HTTP method (4-space indent + get:/post:/etc)
    const methodMatch = trimmed.match(/^(get|post|put|patch|delete|head|options|trace):$/);
    if (methodMatch && line.startsWith('    ')) {
      currentMethod = methodMatch[1];
      currentOperationId = null;
      inOperation = true;
      continue;
    }
    
    // Exit operation when indentation returns to 2 spaces
    if (inOperation && line.startsWith('  ') && !line.startsWith('    ')) {
      if (currentPath && currentMethod) {
        routes.push(new RouteMetadata(
          currentPath,
          currentMethod,
          currentOperationId,
          extractSensitivity(lines, i, currentPath),
          extractAccessLevel(lines, i, currentPath, currentMethod),
          extractRequiresAuth(lines, i, currentPath),
          extractTenantRequired(lines, i, currentPath)
        ));
      }
      inOperation = false;
      currentMethod = null;
      continue;
    }
    
    // Extract operationId
    if (inOperation && trimmed.startsWith('operationId:')) {
      currentOperationId = trimmed.split(':')[1].trim();
    }
  }
  
  // Handle last operation
  if (currentPath && currentMethod && inOperation) {
    routes.push(new RouteMetadata(
      currentPath,
      currentMethod,
      currentOperationId,
      extractSensitivity(lines, lines.length, currentPath),
      extractAccessLevel(lines, lines.length, currentPath, currentMethod),
      extractRequiresAuth(lines, lines.length, currentPath),
      extractTenantRequired(lines, lines.length, currentPath)
    ));
  }
  
  return routes;
}

/**
 * Extract sensitivity from OpenAPI extension or infer from method/path
 */
function extractSensitivity(lines, currentIndex, currentPath) {
  // Look backward for x-ghatana-sensitivity extension
  for (let i = currentIndex - 1; i >= Math.max(0, currentIndex - 20); i--) {
    const line = lines[i];
    if (line && line.includes('x-ghatana-sensitivity:')) {
      return line.split(':')[1].trim();
    }
  }
  
  // Infer from method/path if not explicitly set
  if (!currentPath) return 'INTERNAL';
  const lowerPath = currentPath.toLowerCase();
  
  if (lowerPath.includes('/governance/') || lowerPath.includes('/learning/review/')) {
    return 'CRITICAL';
  }
  return 'INTERNAL';
}

/**
 * Extract access level from OpenAPI extension or infer from path/method patterns
 * Based on existing RouteActionAccessRegistry patterns
 */
function extractAccessLevel(lines, currentIndex, currentPath, currentMethod) {
  // Look backward for x-ghatana-access-level extension
  for (let i = currentIndex - 1; i >= Math.max(0, currentIndex - 20); i++) {
    const line = lines[i];
    if (line && line.includes('x-ghatana-access-level:')) {
      return line.split(':')[1].trim();
    }
  }
  
  // Infer from path/method patterns based on existing registry
  if (!currentPath) return 'VIEWER';
  const lowerPath = currentPath.toLowerCase();
  const lowerMethod = currentMethod ? currentMethod.toLowerCase() : '';
  
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
 * Extract whether auth is required
 */
function extractRequiresAuth(lines, currentIndex, currentPath) {
  // Public paths: /health, /ready, /live, /metrics, /info
  if (!currentPath) return true;
  const lowerPath = currentPath.toLowerCase();
  
  if (lowerPath.includes('/health') || lowerPath.includes('/ready') || lowerPath.includes('/live') ||
      lowerPath.includes('/metrics') || lowerPath.includes('/info')) {
    return false;
  }
  return true;
}

/**
 * Extract whether tenant is required
 */
function extractTenantRequired(lines, currentIndex, currentPath) {
  // Most API routes require tenant
  if (!currentPath) return true;
  const lowerPath = currentPath.toLowerCase();
  
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
 * Main execution
 */
function main() {
  console.log('[DC-P1-07] Generating route/security/runtime-truth metadata from contracts...');
  
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
  
  // Generate Java registry snippet
  const javaRegistry = generateJavaRegistry(allRoutes, 'data-cloud.yaml + action-plane.yaml');
  const javaOutputPath = path.join(PROJECT_ROOT, 'delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteActionAccessRegistry.generated.java');
  
  if (CHECK_MODE) {
    if (fs.existsSync(javaOutputPath)) {
      const existing = fs.readFileSync(javaOutputPath, 'utf-8');
      if (existing !== javaRegistry) {
        console.error('[ERROR] Generated Java registry does not match existing file');
        console.error('[ERROR] Run without --check to regenerate: node scripts/generate-route-security-metadata.mjs');
        process.exit(1);
      }
    }
  } else {
    fs.mkdirSync(path.dirname(javaOutputPath), { recursive: true });
    fs.writeFileSync(javaOutputPath, javaRegistry);
    console.log(`[DC-P1-07] Generated Java registry snippet: ${javaOutputPath}`);
  }
  
  // Generate runtime-truth posture metadata
  const postureMetadata = generateRuntimeTruthPosture(allRoutes);
  const postureOutputPath = path.join(OUTPUT_DIR, 'RuntimeTruthPosture.generated.ts');
  
  if (CHECK_MODE) {
    if (fs.existsSync(postureOutputPath)) {
      const existing = fs.readFileSync(postureOutputPath, 'utf-8');
      if (existing !== postureMetadata) {
        console.error('[ERROR] Generated runtime-truth posture does not match existing file');
        console.error('[ERROR] Run without --check to regenerate: node scripts/generate-route-security-metadata.mjs');
        process.exit(1);
      }
    }
  } else {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    fs.writeFileSync(postureOutputPath, postureMetadata);
    console.log(`[DC-P1-07] Generated runtime-truth posture: ${postureOutputPath}`);
  }
  
  console.log('[DC-P1-07] Route/security/runtime-truth metadata generation completed successfully');
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
