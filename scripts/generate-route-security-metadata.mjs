#!/usr/bin/env node

/**
 * DC-P1-04: Generate route/security/runtime-truth metadata from OpenAPI contracts.
 *
 * This script reads the OpenAPI contracts (data-cloud.yaml and action-plane.yaml),
 * extracts route metadata including security requirements, and generates the
 * RouteActionAccessRegistry.java file with auto-generated entries.
 *
 * Usage: node scripts/generate-route-security-metadata.mjs
 *
 * @doc.type script
 * @doc.purpose Generate route security metadata from OpenAPI contracts
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
const REGISTRY_OUTPUT = path.join(REPO_ROOT, 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteActionAccessRegistry.java');

// Access level mapping based on route patterns
const ACCESS_LEVEL_RULES = [
  // Governance operations - ADMIN
  { pattern: /\/governance\/(retention\/purge|privacy\/redact|policies)/, level: 'ADMIN' },
  { pattern: /\/governance\/policies\/.*/, level: 'ADMIN' },
  
  // Settings operations - ADMIN
  { pattern: /\/settings\/.*/, level: 'ADMIN' },
  
  // Connector operations - ADMIN for destructive, OPERATOR for non-destructive
  { pattern: /\/connectors\/(rotate-credentials|enable|disable)/, level: 'ADMIN' },
  { pattern: /\/connectors\/.*/, level: 'ADMIN' },
  
  // Plugin operations - ADMIN
  { pattern: /\/api\/v1\/action\/plugins\/.*/, level: 'ADMIN' },
  
  // Learning approval - ADMIN
  { pattern: /\/api\/v1\/action\/learning\/review\/.*\/(approve|reject)/, level: 'ADMIN' },
  
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
 * Parses OpenAPI YAML and extracts path/method combinations.
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
 * Generates Java Map.entry lines for RouteActionAccessRegistry.
 */
function generateRegistryEntries(routes) {
  const entries = [];
  
  // Sort routes for consistent output
  const sortedRoutes = Array.from(routes.entries()).sort(([a], [b]) => a.localeCompare(b));
  
  for (const [routeKey, route] of sortedRoutes) {
    const { method, path } = route;
    const accessLevel = determineAccessLevel(method, path);
    const normalizedPath = normalizePath(path);
    entries.push(`        Map.entry("${method} ${normalizedPath}", DataCloudSecurityFilter.AccessLevel.${accessLevel})`);
  }
  
  return entries;
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
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
        // P1-03: DO NOT normalize /api/v1/action/* to /api/v1/* - preserve canonical namespace
        // This ensures action routes are looked up with their canonical paths
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/action/learning/review/[^/]+/(approve|reject)$", "/action/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/rotate$", "/settings/keys/{id}/rotate");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/revoke$", "/settings/keys/{id}/revoke");
        normalized = normalized.replaceAll("/settings/keys/[^/]+$", "/settings/keys/{id}");
        normalized = normalized.replaceAll("/settings/approvals/[^/]+/(approve|reject)$", "/settings/approvals/{id}/$1");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/{id}");
        normalized = normalized.replaceAll("/governance/policies/[^/]+/toggle$", "/governance/policies/{id}/toggle");
        normalized = normalized.replaceAll("/governance/policies/[^/]+$", "/governance/policies/{id}");
        normalized = normalized.replaceAll("/autonomy/plan/[^/]+$", "/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/action/autonomy/plan/[^/]+$", "/action/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/context/keys/[^/]+$", "/context/keys/{id}");
        normalized = normalized.replaceAll("/context/[^/]+/rag-policy-check$", "/context/{collection}/rag-policy-check");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/execute$", "/action/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/cancel$", "/pipelines/{id}/executions/{id}/cancel");
        normalized = normalized.replaceAll("/pipelines/[^/]+$", "/pipelines/{id}");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+$", "/action/pipelines/{id}");
        normalized = normalized.replaceAll("/executions/[^/]+/(cancel|retry|rollback|restore)$", "/executions/{id}/$1");
        normalized = normalized.replaceAll("/action/executions/[^/]+/(cancel|retry|rollback|restore)$", "/action/executions/{id}/$1");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+/resolve$", "/alerts/groups/{id}/resolve");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+/apply$", "/alerts/suggestions/{id}/apply");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+$", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+/(remediate|auto-remediate|escalate|acknowledge|resolve)$", "/alerts/{id}/$1");
        normalized = normalized.replaceAll("/models/[^/]+$", "/models/{id}");
        normalized = normalized.replaceAll("/action/memory/[^/]+/[^/]+", "/action/memory/{agentId}/{memoryId}");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+$", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");
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
  console.log('DC-P1-04: Generating route/security metadata from OpenAPI contracts...');
  
  // Read OpenAPI contracts
  if (!fs.existsSync(DATA_CLOUD_YAML)) {
    console.error(`Error: ${DATA_CLOUD_YAML} not found`);
    process.exit(1);
  }
  
  if (!fs.existsSync(ACTION_PLANE_YAML)) {
    console.error(`Error: ${ACTION_PLANE_YAML} not found`);
    process.exit(1);
  }
  
  const dataCloudContent = fs.readFileSync(DATA_CLOUD_YAML, 'utf-8');
  const actionPlaneContent = fs.readFileSync(ACTION_PLANE_YAML, 'utf-8');
  
  // Extract routes from both contracts
  const dataCloudRoutes = extractRoutesFromOpenAPI(dataCloudContent);
  const actionPlaneRoutes = extractRoutesFromOpenAPI(actionPlaneContent);
  
  // Merge routes (action-plane routes take precedence for duplicates)
  const allRoutes = new Map([...dataCloudRoutes, ...actionPlaneRoutes]);
  
  console.log(`Extracted ${allRoutes.size} routes from OpenAPI contracts`);
  
  // Generate registry entries
  const entries = generateRegistryEntries(allRoutes);
  
  // Generate complete file content
  const content = generateRegistryContent(entries);
  
  // Write to output file
  fs.writeFileSync(REGISTRY_OUTPUT, content, 'utf-8');
  
  console.log(`Generated ${REGISTRY_OUTPUT}`);
  console.log(`✅  Route security metadata generation complete (${entries.length} entries)`);
}

// Always run main when executed
main();
