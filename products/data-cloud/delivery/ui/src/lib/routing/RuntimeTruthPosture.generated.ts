/**
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * 
 * Generated from canonical route manifest by generate-route-manifest.mjs
 * To regenerate, run: npm run generate:route-manifest
 * 
 * Generated at: 2026-05-20T21:37:42.754Z
 * Source: RouteSecurityRegistry.java
 * 
 * DC-P0-03: Runtime truth for UI feature gating and route visibility
 */

export interface RuntimeRoute {
  method: string;
  path: string;
  operationId: string;
  sensitivity: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CRITICAL';
  runtimeTruthSurface: 'VISIBLE' | 'HIDDEN' | 'DEVELOPER_ONLY' | 'EXPERIMENTAL';
  requiresAuth: boolean;
  requiresTenant: boolean;
  requiresPolicy: boolean;
  idempotent: boolean;
  description: string;
}

/**
 * Canonical runtime truth for Data Cloud HTTP routes.
 * Used for:
 * - UI feature gating (show/hide actions based on sensitivity)
 * - Client SDK generation (route availability per profile)
 * - Documentation generation (API matrix)
 * - CI/CD validation (drift detection)
 */
export const RUNTIME_TRUTH_POSTURED: RuntimeRoute[] = [
  {
    method: 'DELETE',
    path: '/api/v1/action/memory/{agentId}/{memoryId}',
    operationId: 'deleteApiV1ActionMemory{agentId}{memoryId}',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: false,
    description: 'Delete memory entry (data lifecycle)'
  },
  {
    method: 'DELETE',
    path: '/api/v1/action/pipelines/{id}',
    operationId: 'deleteApiV1ActionPipelines{id}',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: false,
    description: 'Delete pipeline (high-impact)'
  },
  {
    method: 'GET',
    path: '/api/v1/action/agents/catalog',
    operationId: 'getApiV1ActionAgentsCatalog',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'List available agents in catalog'
  },
  {
    method: 'GET',
    path: '/api/v1/action/agents/catalog/{id}',
    operationId: 'getApiV1ActionAgentsCatalog{id}',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Get agent catalog entry by ID'
  },
  {
    method: 'GET',
    path: '/api/v1/action/autonomy/level',
    operationId: 'getApiV1ActionAutonomyLevel',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Get autonomy level'
  },
  {
    method: 'GET',
    path: '/api/v1/action/executions/{id}',
    operationId: 'getApiV1ActionExecutions{id}',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Get execution details'
  },
  {
    method: 'GET',
    path: '/api/v1/action/learning/review',
    operationId: 'getApiV1ActionLearningReview',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'List learning review items'
  },
  {
    method: 'GET',
    path: '/api/v1/action/memory',
    operationId: 'getApiV1ActionMemory',
    sensitivity: 'SENSITIVE',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Access agent memory (personal data)'
  },
  {
    method: 'GET',
    path: '/api/v1/action/pipelines',
    operationId: 'getApiV1ActionPipelines',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'List pipelines'
  },
  {
    method: 'GET',
    path: '/api/v1/action/pipelines/{id}',
    operationId: 'getApiV1ActionPipelines{id}',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Get pipeline by ID'
  },
  {
    method: 'GET',
    path: '/api/v1/action/plugins',
    operationId: 'getApiV1ActionPlugins',
    sensitivity: 'INTERNAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'List plugins'
  },
  {
    method: 'POST',
    path: '/api/v1/action/executions/{id}/cancel',
    operationId: 'postApiV1ActionExecutions{id}Cancel',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: true,
    description: 'Cancel execution (high-impact operation)'
  },
  {
    method: 'POST',
    path: '/api/v1/action/learning/review/{id}/approve',
    operationId: 'postApiV1ActionLearningReview{id}Approve',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: true,
    description: 'Approve learning review (model training)'
  },
  {
    method: 'POST',
    path: '/api/v1/action/learning/review/{id}/reject',
    operationId: 'postApiV1ActionLearningReview{id}Reject',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: true,
    description: 'Reject learning review'
  },
  {
    method: 'POST',
    path: '/api/v1/action/memory/search',
    operationId: 'postApiV1ActionMemorySearch',
    sensitivity: 'SENSITIVE',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: true,
    description: 'Search agent memory'
  },
  {
    method: 'POST',
    path: '/api/v1/action/pipelines',
    operationId: 'postApiV1ActionPipelines',
    sensitivity: 'SENSITIVE',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: false,
    idempotent: false,
    description: 'Create pipeline'
  },
  {
    method: 'POST',
    path: '/api/v1/action/plugins/{id}/disable',
    operationId: 'postApiV1ActionPlugins{id}Disable',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: false,
    description: 'Disable plugin'
  },
  {
    method: 'POST',
    path: '/api/v1/action/plugins/{id}/enable',
    operationId: 'postApiV1ActionPlugins{id}Enable',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: false,
    description: 'Enable plugin (system configuration)'
  },
  {
    method: 'PUT',
    path: '/api/v1/action/autonomy/level',
    operationId: 'putApiV1ActionAutonomyLevel',
    sensitivity: 'CRITICAL',
    runtimeTruthSurface: 'VISIBLE',
    requiresAuth: true,
    requiresTenant: true,
    requiresPolicy: true,
    idempotent: true,
    description: 'Set autonomy level (governance)'
  },
  {
    method: 'GET',
    path: '/health',
    operationId: 'getHealth',
    sensitivity: 'PUBLIC',
    runtimeTruthSurface: 'HIDDEN',
    requiresAuth: false,
    requiresTenant: false,
    requiresPolicy: false,
    idempotent: true,
    description: 'Health check endpoint'
  },
  {
    method: 'GET',
    path: '/live',
    operationId: 'getLive',
    sensitivity: 'PUBLIC',
    runtimeTruthSurface: 'HIDDEN',
    requiresAuth: false,
    requiresTenant: false,
    requiresPolicy: false,
    idempotent: true,
    description: 'Liveness probe'
  },
  {
    method: 'GET',
    path: '/ready',
    operationId: 'getReady',
    sensitivity: 'PUBLIC',
    runtimeTruthSurface: 'HIDDEN',
    requiresAuth: false,
    requiresTenant: false,
    requiresPolicy: false,
    idempotent: true,
    description: 'Readiness probe'
  }
];

/**
 * Get route by method and path pattern
 */
export function findRoute(method: string, pathPattern: string): RuntimeRoute | undefined {
  const methodUpper = method.toUpperCase();

  return RUNTIME_TRUTH_POSTURED.find(route => {
    if (route.method !== methodUpper) return false;

    const routePattern = route.path.replace(/{[^}]+}/g, '[^/]+');
    const regex = new RegExp('^' + routePattern + '$');
    return regex.test(pathPattern) || route.path === pathPattern;
  });
}

/**
 * Get all routes for a sensitivity level
 */
export function getRoutesBySensitivity(sensitivity: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CRITICAL'): RuntimeRoute[] {
  return RUNTIME_TRUTH_POSTURED.filter(r => r.sensitivity === sensitivity);
}

/**
 * Check if route requires policy evaluation
 */
export function requiresPolicy(method: string, path: string): boolean {
  const route = findRoute(method, path);
  return route?.requiresPolicy ?? false;
}
