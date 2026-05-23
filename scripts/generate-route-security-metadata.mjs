#!/usr/bin/env node

/**
 * DC-P1-04: Generate route/security/runtime-truth metadata from OpenAPI contracts.
 *
 * This script reads the OpenAPI contracts (data-cloud.yaml and action-plane.yaml),
 * extracts route metadata including security requirements, and generates the
 * RouteActionAccessRegistry.java file with auto-generated entries.
 *
 * DC-P0-01: Routes are generated from both OpenAPI contracts AND the actual
 * DataCloudRouterBuilder Java source to ensure exact metadata for all runtime routes.
 * Legacy routes are moved to a separate compatibility registry.
 *
 * Usage: node scripts/generate-route-security-metadata.mjs
 *
 * @doc.type script
 * @doc.purpose Generate route security metadata from OpenAPI contracts and router source
 * @doc.layer repo
 * @doc.pattern CodeGeneration
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Paths
const REPO_ROOT = path.resolve(__dirname, '..');
const DATA_CLOUD_YAML = path.join(REPO_ROOT, 'products/data-cloud/contracts/openapi/data-cloud.yaml');
const ACTION_PLANE_YAML = path.join(REPO_ROOT, 'products/data-cloud/contracts/openapi/action-plane.yaml');
const ROUTER_BUILDER_JAVA = path.join(REPO_ROOT, 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java');
const REGISTRY_OUTPUT = path.join(REPO_ROOT, 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteActionAccessRegistry.java');
const COMPATIBILITY_REGISTRY_OUTPUT = path.join(REPO_ROOT, 'products/data-cloud/contracts/openapi/route-compatibility-registry.yaml');

// Access level mapping based on route patterns
const ACCESS_LEVEL_RULES = [
  // Governance operations - ADMIN
  { pattern: /\/governance\/(retention\/purge|privacy\/redact|policies)/, level: 'ADMIN' },
  { pattern: /\/governance\/policies\/.*/, level: 'ADMIN' },
  
  // Settings operations - ADMIN
  { pattern: /\/settings\/.*/, level: 'ADMIN' },
  
  // Connector operations - ADMIN for destructive, OPERATOR for non-destructive
  { pattern: /\/connectors\/[^/]+\/(rotate-credentials|enable|disable)/, level: 'ADMIN' },
  { pattern: /\/connectors\/[^/]+\/sync$/, level: 'OPERATOR' },
  { pattern: /\/connectors\/.*/, level: 'ADMIN' },
  
  // Plugin operations - ADMIN
  { pattern: /\/api\/v1\/action\/plugins\/.*/, level: 'ADMIN' },
  
  // Learning approval - ADMIN
  { pattern: /\/api\/v1\/action\/learning\/review\/.*\/(approve|reject)/, level: 'ADMIN' },
  { pattern: /\/api\/v1\/learning\/review\/.*\/(approve|reject)/, level: 'ADMIN' },
  
  // Model promotion - ADMIN
  { pattern: /\/models\/.*\/promote/, level: 'ADMIN' },
  
  // Autonomy level changes - ADMIN
  { pattern: /\/api\/v1\/action\/autonomy\/.*/, level: 'ADMIN' },
  
  // Pipeline delete - ADMIN
  { pattern: /\/api\/v1\/action\/pipelines\/.*$/, level: 'ADMIN' },
  { pattern: /\/api\/v1\/action\/pipelines\/[^\/]+$/, level: 'ADMIN' },
  
  // Execution rollback/restore - OPERATOR
  { pattern: /\/api\/v1\/action\/executions\/.*\/(cancel|retry|rollback|restore)/, level: 'OPERATOR' },
  
  // Memory delete/retain - ADMIN
  { pattern: /\/api\/v1\/action\/memory\/.*\/(delete|retain)/, level: 'ADMIN' },
  
  // Entity delete - ADMIN
  { pattern: /\/entities\/.*\/.*$/, level: 'ADMIN' },
  
  // Pipeline operations - OPERATOR
  { pattern: /\/api\/v1\/action\/pipelines/, level: 'OPERATOR' },
  { pattern: /\/api\/v1\/action\/pipelines\/.*\/execute/, level: 'OPERATOR' },
  
  // Core data operations - OPERATOR
  { pattern: /\/events$/, level: 'OPERATOR' },
  { pattern: /\/entities\/.*$/, level: 'OPERATOR' },
  
  // Alert operations - OPERATOR
  { pattern: /\/alerts\/.*/, level: 'OPERATOR' },
  
  // Context operations - OPERATOR
  { pattern: /\/context\/.*/, level: 'OPERATOR' },
  
  // Agent catalog - VIEWER
  { pattern: /\/api\/v1\/action\/agents\/catalog/, level: 'VIEWER' },
  
  // Default to OPERATOR for unspecified routes
  { pattern: /.*/, level: 'OPERATOR' },
];

/**
 * DC-P0-01: Parses OpenAPI YAML and extracts path/method combinations.
 */
function extractRoutesFromOpenAPI(yamlContent) {
  const routes = new Map();
  const lines = yamlContent.split('\n');
  let currentPath = null;
  let inPathsSection = false;
  let indentLevel = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trimEnd();

    // Check if we're in the paths section
    if (trimmed === 'paths:') {
      inPathsSection = true;
      indentLevel = line.search(/\S/);
      continue;
    }

    // Exit paths section when we reach another top-level section
    if (inPathsSection && trimmed.length > 0 && line.search(/\S/) === indentLevel && !trimmed.startsWith('/')) {
      inPathsSection = false;
      continue;
    }

    if (!inPathsSection) continue;

    // Extract path definition (e.g., "  /api/v1/entities:")
    const pathMatch = trimmed.match(/^(\s+)(\/[^:]+):$/);
    if (pathMatch) {
      currentPath = pathMatch[2]; // Use the full path including leading slash
      continue;
    }

    // Extract HTTP method (e.g., "    post:")
    if (currentPath) {
      const methodMatch = trimmed.match(/^(\s+)(get|post|put|patch|delete|head|options|trace):$/);
      if (methodMatch) {
        const method = methodMatch[2].toUpperCase();
        const key = `${method} ${currentPath}`;
        routes.set(key, { method, path: currentPath });
      }
    }
  }

  return routes;
}

/**
 * DC-P0-01: Extracts routes from DataCloudRouterBuilder.java source code.
 * This ensures we have exact metadata for all runtime routes, not just those in OpenAPI contracts.
 */
function extractRoutesFromJavaSource(javaContent) {
  const routes = new Map();
  const lines = javaContent.split('\n');
  
  // Pattern to match route registration lines like:
  // .with(HttpMethod.GET, "/api/v1/action/pipelines", handler::method)
  const routePattern = /\.with\(HttpMethod\.(GET|POST|PUT|DELETE|PATCH),\s*"([^"]+)",\s*\w+/g;
  
  for (const line of lines) {
    const matches = [...line.matchAll(routePattern)];
    for (const match of matches) {
      const method = match[1];
      const path = match[2];
      const key = `${method} ${path}`;
      routes.set(key, { method, path, source: 'java-router' });
    }
  }
  
  return routes;
}

/**
 * Determines access level based on route pattern.
 */
function determineAccessLevel(method, path) {
  const routeKey = `${method} ${path}`;
  
  for (const rule of ACCESS_LEVEL_RULES) {
    if (rule.pattern.test(routeKey)) {
      return rule.level;
    }
  }
  
  return 'OPERATOR'; // Default
}

/**
 * Normalizes path for registry lookup (converts {param} to :param).
 */
function normalizePath(path) {
  return path.replace(/\{([^}]+)\}/g, ':$1');
}

/**
 * DC-P0-01: Separates canonical /api/v1/action/* routes from legacy routes.
 * Legacy routes are those under /api/v1/pipelines, /api/v1/memory, /api/v1/plugins,
 * /api/v1/autonomy, /api/v1/agents/catalog that should use canonical /api/v1/action/* namespace.
 */
function separateCanonicalAndLegacyRoutes(routes) {
  const canonicalRoutes = new Map();
  const legacyRoutes = new Map();
  
  // Legacy route patterns that should be under /api/v1/action/*
  const legacyPatterns = [
    /^GET \/api\/v1\/pipelines/,
    /^POST \/api\/v1\/pipelines/,
    /^PUT \/api\/v1\/pipelines/,
    /^DELETE \/api\/v1\/pipelines/,
    /^GET \/api\/v1\/memory/,
    /^POST \/api\/v1\/memory/,
    /^DELETE \/api\/v1\/memory/,
    /^PUT \/api\/v1\/memory/,
    /^GET \/api\/v1\/plugins/,
    /^POST \/api\/v1\/plugins/,
    /^DELETE \/api\/v1\/plugins/,
    /^PUT \/api\/v1\/plugins/,
    /^GET \/api\/v1\/autonomy/,
    /^POST \/api\/v1\/autonomy/,
    /^PUT \/api\/v1\/autonomy/,
    /^GET \/api\/v1\/agents\/catalog/,
    /^POST \/api\/v1\/agents\/catalog/,
    /^GET \/api\/v1\/executions/,
    /^POST \/api\/v1\/executions/,
    /^DELETE \/api\/v1\/executions/,
    /^PUT \/api\/v1\/executions/,
  ];
  
  for (const [key, route] of routes.entries()) {
    const isLegacy = legacyPatterns.some(pattern => pattern.test(key));
    if (isLegacy) {
      legacyRoutes.set(key, route);
    } else {
      canonicalRoutes.set(key, route);
    }
  }
  
  return { canonicalRoutes, legacyRoutes };
}

/**
 * Generates Java Map.entry lines for RouteActionAccessRegistry.
 */
function generateRegistryEntries(routes) {
  const accessRank = new Map([
    ['NONE', 0],
    ['VIEWER', 1],
    ['AUDITOR', 2],
    ['OPERATOR', 3],
    ['ADMIN', 4],
  ]);
  const entriesByNormalizedRoute = new Map();
  
  // Sort routes for consistent output
  const sortedRoutes = Array.from(routes.entries()).sort(([a], [b]) => a.localeCompare(b));
  
  for (const [routeKey, route] of sortedRoutes) {
    const { method, path } = route;
    const accessLevel = determineAccessLevel(method, path);
    const normalizedPath = normalizePath(path);
    const normalizedRouteKey = `${method} ${normalizedPath}`;
    const existingAccessLevel = entriesByNormalizedRoute.get(normalizedRouteKey);
    if (!existingAccessLevel || accessRank.get(accessLevel) > accessRank.get(existingAccessLevel)) {
      entriesByNormalizedRoute.set(normalizedRouteKey, accessLevel);
    }
  }
  
  return Array.from(entriesByNormalizedRoute.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([normalizedRouteKey, accessLevel]) =>
      `        Map.entry("${normalizedRouteKey}", DataCloudSecurityFilter.AccessLevel.${accessLevel})`,
    );
}

/**
 * DC-P0-01: Generates the compatibility registry YAML with legacy routes.
 */
function generateCompatibilityRegistry(legacyRoutes, existingCompatibilityContent) {
  const today = new Date().toISOString().split('T')[0];
  const retirementTarget = '2026-12-31';
  
  let yaml = `# DC-P0-01: Route Compatibility Registry
# 
# This registry documents legacy routes that have been migrated to canonical namespaces
# and their retirement status. All Action Plane routes now live under /api/v1/action/*.
#
# Migration Guide:
# - Update clients to use canonical routes under /api/v1/action/*
# - Legacy routes are conditionally available via feature flag: DataCloudFeature.LEGACY_ACTION_ROUTES
# - Legacy routes will be removed in a future major version
#
# Auto-generated on: ${today}
# Regenerate with: node scripts/generate-route-security-metadata.mjs

legacy_routes:
`;

  // Sort and de-duplicate legacy routes after OpenAPI parameter normalization.
  const legacyRoutesByNormalizedKey = new Map();
  const sortedLegacy = Array.from(legacyRoutes.entries()).sort(([a], [b]) => a.localeCompare(b));
  for (const [, route] of sortedLegacy) {
    const normalizedKey = `${route.method} ${normalizePath(route.path)}`;
    if (!legacyRoutesByNormalizedKey.has(normalizedKey)) {
      legacyRoutesByNormalizedKey.set(normalizedKey, route);
    }
  }
  
  // Group by base path for cleaner output
  const routeGroups = new Map();
  for (const [key, route] of Array.from(legacyRoutesByNormalizedKey.entries()).sort(([a], [b]) => a.localeCompare(b))) {
    const { method, path } = route;
    const basePath = path.split('/')[2]; // Get the third segment (pipelines, memory, etc.)
    if (!routeGroups.has(basePath)) {
      routeGroups.set(basePath, []);
    }
    routeGroups.get(basePath).push({ method, path, key });
  }
  
  for (const [basePath, routes] of routeGroups) {
    yaml += `  # Legacy ${basePath} routes (migrated to /api/v1/action/${basePath}/*)\n`;
    for (const { method, path, key } of routes) {
      const normalizedPath = normalizePath(path);
      const canonicalPath = path.replace(/^\/api\/v1\/(pipelines|memory|plugins|autonomy|agents|executions)/, '/api/v1/action/$1');
      yaml += `  - path: "${normalizedPath}"\n`;
      yaml += `    canonical: "${normalizePath(canonicalPath)}"\n`;
      yaml += `    methods: [${method}]\n`;
      yaml += `    deprecated_since: "2026-05-20"\n`;
      yaml += `    retirement_target: "${retirementTarget}"\n`;
      yaml += `    feature_flag: "DataCloudFeature.LEGACY_ACTION_ROUTES"\n`;
      yaml += `    migration_notes: |\n`;
      yaml += `      Use ${method} ${canonicalPath} instead.\n`;
      yaml += `      The response schema is identical.\n`;
      yaml += `\n`;
    }
  }
  
  yaml += `canonical_action_routes:\n`;
  yaml += `  # DC-P0-01: Canonical /api/v1/action/* routes are registered in RouteActionAccessRegistry\n`;
  yaml += `  # This section is reserved for future documentation of canonical action routes\n`;
  
  return yaml;
}

/**
 * Generates the complete RouteActionAccessRegistry.java content.
 */
function generateRegistryContent(entries) {
  const header = `package com.ghatana.datacloud.launcher.http;

import java.util.Map;

/**
 * Contract-backed route/action access registry.
 *
 * <p>Provides explicit access-level requirements for high-impact routes,
 * reducing reliance on path-prefix inference in security decisions.
 *
 * <p>DC-P1-04: Route entries are generated from OpenAPI contracts via
 * {@code scripts/generate-route-security-metadata.mjs}. Do not edit manually.
 * Regenerate with: {@code node scripts/generate-route-security-metadata.mjs}
 *
 * @doc.type class
 * @doc.purpose Route/action access-level registry for Data Cloud HTTP security
 * @doc.layer product
 * @doc.pattern Registry
 */
final class RouteActionAccessRegistry {

    // DC-P1-04: Auto-generated from OpenAPI contracts
    // Regenerate with: node scripts/generate-route-security-metadata.mjs
    private static final Map<String, DataCloudSecurityFilter.AccessLevel> ACCESS_BY_ACTION = Map.ofEntries(
`;

  const footer = `
    );

    private RouteActionAccessRegistry() {
    }

    static DataCloudSecurityFilter.AccessLevel requiredAccess(String method, String path) {
        String normalized = normalizePath(path);
        return ACCESS_BY_ACTION.get(method.toUpperCase() + " " + normalized);
    }

    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/:id");
        // P1-03: DO NOT normalize /api/v1/action/* to /api/v1/* - preserve canonical namespace
        // This ensures action routes are looked up with their canonical paths
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/:reviewId/$1");
        normalized = normalized.replaceAll("/action/learning/review/[^/]+/(approve|reject)$", "/action/learning/review/:reviewId/$1");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/:connectionId");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/rotate$", "/settings/keys/:id/rotate");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/revoke$", "/settings/keys/:id/revoke");
        normalized = normalized.replaceAll("/settings/keys/[^/]+$", "/settings/keys/:id");
        normalized = normalized.replaceAll("/settings/approvals/[^/]+/(approve|reject)$", "/settings/approvals/:id/$1");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/:id");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/:id");
        normalized = normalized.replaceAll("/governance/policies/[^/]+/toggle$", "/governance/policies/:id/toggle");
        normalized = normalized.replaceAll("/governance/policies/[^/]+$", "/governance/policies/:id");
        normalized = normalized.replaceAll("/autonomy/plan/[^/]+$", "/autonomy/plan/:actionType");
        normalized = normalized.replaceAll("/action/autonomy/plan/[^/]+$", "/action/autonomy/plan/:actionType");
        normalized = normalized.replaceAll("/context/keys/[^/]+$", "/context/keys/:key");
        normalized = normalized.replaceAll("/context/[^/]+/rag-policy-check$", "/context/:collection/rag-policy-check");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/:pipelineId/execute");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/execute$", "/action/pipelines/:pipelineId/execute");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/cancel$", "/pipelines/:pipelineId/executions/:executionId/cancel");
        normalized = normalized.replaceAll("/pipelines/[^/]+$", "/pipelines/:pipelineId");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+$", "/action/pipelines/:pipelineId");
        normalized = normalized.replaceAll("/executions/[^/]+/(cancel|retry|rollback|restore)$", "/executions/:executionId/$1");
        normalized = normalized.replaceAll("/action/executions/[^/]+/(cancel|retry|rollback|restore)$", "/action/executions/:executionId/$1");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+/resolve$", "/alerts/groups/:groupId/resolve");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+/apply$", "/alerts/suggestions/:suggestionId/apply");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+$", "/alerts/rules/:ruleId");
        normalized = normalized.replaceAll("/alerts/[^/]+/(remediate|auto-remediate|escalate|acknowledge|resolve)$", "/alerts/:id/$1");
        normalized = normalized.replaceAll("/models/[^/]+$", "/models/:modelId");
        normalized = normalized.replaceAll("/action/memory/[^/]+/[^/]+", "/action/memory/:agentId/:memoryId");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+$", "/entities/:collection/:id");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/:collection");
        return normalized;
    }
}
`;

  const entriesSection = entries.join(',\n') + '\n';
  
  return header + entriesSection + footer;
}

/**
 * Main execution.
 */
function main() {
  console.log('DC-P0-01: Generating route/security metadata from OpenAPI contracts and Java router source...');
  
  // Read OpenAPI contracts
  if (!fs.existsSync(DATA_CLOUD_YAML)) {
    console.error(`Error: ${DATA_CLOUD_YAML} not found`);
    process.exit(1);
  }
  
  if (!fs.existsSync(ACTION_PLANE_YAML)) {
    console.error(`Error: ${ACTION_PLANE_YAML} not found`);
    process.exit(1);
  }
  
  // DC-P0-01: Read Java router source for exact runtime routes
  if (!fs.existsSync(ROUTER_BUILDER_JAVA)) {
    console.error(`Error: ${ROUTER_BUILDER_JAVA} not found`);
    process.exit(1);
  }
  
  const dataCloudContent = fs.readFileSync(DATA_CLOUD_YAML, 'utf-8');
  const actionPlaneContent = fs.readFileSync(ACTION_PLANE_YAML, 'utf-8');
  const routerBuilderContent = fs.readFileSync(ROUTER_BUILDER_JAVA, 'utf-8');
  
  // Extract routes from OpenAPI contracts
  const dataCloudRoutes = extractRoutesFromOpenAPI(dataCloudContent);
  const actionPlaneRoutes = extractRoutesFromOpenAPI(actionPlaneContent);
  
  // DC-P0-01: Extract routes from Java router source (source of truth for runtime routes)
  const javaRouterRoutes = extractRoutesFromJavaSource(routerBuilderContent);
  
  console.log(`Extracted ${dataCloudRoutes.size} routes from data-cloud.yaml`);
  console.log(`Extracted ${actionPlaneRoutes.size} routes from action-plane.yaml`);
  console.log(`Extracted ${javaRouterRoutes.size} routes from DataCloudRouterBuilder.java`);
  
  // Merge all routes (Java router takes precedence as source of truth for runtime)
  const allRoutes = new Map([...dataCloudRoutes, ...actionPlaneRoutes, ...javaRouterRoutes]);
  
  console.log(`Total unique routes: ${allRoutes.size}`);
  
  // DC-P0-01: Separate canonical routes from legacy routes
  const { canonicalRoutes, legacyRoutes } = separateCanonicalAndLegacyRoutes(allRoutes);
  
  console.log(`Canonical routes: ${canonicalRoutes.size}`);
  console.log(`Legacy routes (moved to compatibility registry): ${legacyRoutes.size}`);
  
  // Generate registry entries for canonical routes only
  const entries = generateRegistryEntries(canonicalRoutes);
  
  // Generate complete RouteActionAccessRegistry.java content
  const content = generateRegistryContent(entries);
  
  // Write RouteActionAccessRegistry.java
  fs.writeFileSync(REGISTRY_OUTPUT, content, 'utf-8');
  console.log(`Generated ${REGISTRY_OUTPUT}`);
  
  // DC-P0-01: Generate compatibility registry with legacy routes
  const existingCompatibilityContent = fs.existsSync(COMPATIBILITY_REGISTRY_OUTPUT)
    ? fs.readFileSync(COMPATIBILITY_REGISTRY_OUTPUT, 'utf-8')
    : '';
  const compatibilityYaml = generateCompatibilityRegistry(legacyRoutes, existingCompatibilityContent);
  fs.writeFileSync(COMPATIBILITY_REGISTRY_OUTPUT, compatibilityYaml, 'utf-8');
  console.log(`Generated ${COMPATIBILITY_REGISTRY_OUTPUT}`);
  
  console.log(`✅  Route security metadata generation complete (${entries.length} canonical entries, ${legacyRoutes.size} legacy entries)`);
  
  // DC-P0-01: Fail if any Java router route is missing from canonical registry
  const missingRoutes = [];
  for (const [key, route] of javaRouterRoutes.entries()) {
    const normalizedPath = normalizePath(route.path);
    const registryKey = `${route.method} ${normalizedPath}`;
    if (!canonicalRoutes.has(registryKey) && !legacyRoutes.has(key)) {
      missingRoutes.push(key);
    }
  }
  
  if (missingRoutes.length > 0) {
    console.error('❌ ERROR: The following Java router routes are missing from the registry:');
    missingRoutes.forEach(r => console.error(`  - ${r}`));
    console.error('DC-P0-01: All router routes must have exact metadata. Run the script to regenerate.');
    process.exit(1);
  }
}

// Always run main when executed
main();
