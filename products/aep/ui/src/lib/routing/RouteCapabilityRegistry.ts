/**
 * Route Capability Registry
 *
 * Single source of truth for canonical AEP routes, their capabilities,
 * required roles, and discoverability. Consumed by navigation,
 * global search, and route guards to prevent drift.
 *
 * @doc.type module
 * @doc.purpose Centralized route metadata and capability definitions for AEP
 * @doc.layer shared
 * @doc.pattern Registry
 */

import { z } from "zod";

export type UserRole = "admin" | "operator" | "viewer" | "auditor";

/**
 * Route lifecycle states
 */
export const RouteLifecycleSchema = z.enum([
  "active",
  "preview",
  "boundary",
  "deprecated",
  "redirect",
  "removed",
]);

export type RouteLifecycle = z.infer<typeof RouteLifecycleSchema>;

/**
 * Canonical route entry — single source of truth for a route.
 */
export const RouteCapabilitySchema = z.object({
  /** URL path segment (e.g., '/operate') */
  path: z.string().min(1),
  /** Human-readable label */
  label: z.string().min(1),
  /** Required role for access */
  minimumRole: z.enum(["viewer", "operator", "admin"]).default("viewer"),
  /**
   * Whether auditor role can access this route.
   * Auditors have read-only access to governance, audit, compliance, lineage,
   * operations history, and privacy-status surfaces, but cannot mutate
   * pipelines, policies, privacy actions, connectors, plugins, or executions.
   * Defaults to true for viewer-minimum routes, false for operator-minimum routes.
   */
  auditorAllowed: z.boolean().optional(),
  /** Functional lifecycle state */
  lifecycle: RouteLifecycleSchema.default("active"),
  /** Capabilities this route provides */
  capabilities: z.array(z.string()).default([]),
  /** Discoverable in nav/search */
  discoverable: z.boolean().default(true),
  /** Icon name from lucide-react (optional) */
  iconName: z.string().optional(),
  /** Redirect target if lifecycle is 'redirect' */
  redirectTo: z.string().optional(),
  /** Description for search/help */
  description: z.string().optional(),
  /** Outcome group for navigation */
  group: z.enum(["operate", "build", "learn", "govern", "catalog"]).optional(),
});

export type RouteCapability = z.infer<typeof RouteCapabilitySchema>;

/**
 * Route Capability Registry type
 */
export const RouteCapabilityRegistrySchema = z.record(
  z.string(),
  RouteCapabilitySchema
);

export type RouteCapabilityRegistry = z.infer<
  typeof RouteCapabilityRegistrySchema
>;

// ---------------------------------------------------------------------------
// Canonical registry — single source of truth for AEP routes
// ---------------------------------------------------------------------------

export const aepRouteRegistry: RouteCapabilityRegistry = {
  operate: {
    path: "/operate",
    label: "Runs & Alerts",
    minimumRole: "viewer",
    // Auditor can observe run status and alerts (read-only).
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["monitoring", "runs", "alerts"],
    discoverable: true,
    iconName: "BarChart3",
    group: "operate",
    description: "Monitoring dashboard for runs and alerts",
  },
  operateCosts: {
    path: "/operate/costs",
    label: "Costs",
    minimumRole: "operator",
    // Auditor does not have access to budget/cost data by default.
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["costs", "budget"],
    discoverable: true,
    iconName: "BarChart3",
    group: "operate",
    description: "Tenant spend and budget visibility",
  },
  operateReviews: {
    path: "/operate/reviews",
    label: "Review Queue",
    minimumRole: "operator",
    // Auditor cannot participate in HITL decision-making (mutations only).
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["hitl", "review"],
    discoverable: true,
    iconName: "FileText",
    group: "operate",
    description: "Human review queue",
  },
  operateRuns: {
    path: "/operate/runs/:runId",
    label: "Run Detail",
    minimumRole: "viewer",
    // Auditor can inspect run lineage, decisions, and evidence.
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["run-detail", "lineage"],
    discoverable: false,
    description: "Unified run detail + lineage + decisions",
  },
  operateOperations: {
    path: "/operate/operations",
    label: "Operations",
    minimumRole: "operator",
    // Auditor can review historical operations for compliance traceability.
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["operations-center"],
    discoverable: true,
    iconName: "FileText",
    group: "operate",
    description: "Active and historical operations",
  },
  buildPipelines: {
    path: "/build/pipelines",
    label: "Pipelines",
    minimumRole: "viewer",
    // Auditor can view pipeline list (read-only).
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["pipeline-list"],
    discoverable: true,
    iconName: "FileText",
    group: "build",
    description: "Pipeline list",
  },
  buildNewPipeline: {
    path: "/build/pipelines/new",
    label: "Pipeline Builder",
    minimumRole: "operator",
    // Auditor cannot create or modify pipelines.
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["pipeline-builder"],
    discoverable: true,
    iconName: "FileText",
    group: "build",
    description: "Visual pipeline builder",
  },
  buildPatterns: {
    path: "/build/patterns",
    label: "Patterns",
    minimumRole: "operator",
    // Auditor cannot create patterns.
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["pattern-studio"],
    discoverable: true,
    iconName: "FileText",
    group: "build",
    description: "Pattern studio",
  },
  learnEpisodes: {
    path: "/learn/episodes",
    label: "Episodes",
    minimumRole: "viewer",
    // Auditor can review learning episodes (read-only).
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["learning"],
    discoverable: true,
    iconName: "Database",
    group: "learn",
    description: "Learning episodes and reflection",
  },
  learnMemory: {
    path: "/learn/memory",
    label: "Memory",
    minimumRole: "operator",
    // Auditor cannot view agent memory (potential PII).
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["memory-explorer"],
    discoverable: true,
    iconName: "Database",
    group: "learn",
    description: "Agent memory explorer",
  },
  govern: {
    path: "/govern",
    label: "Governance",
    minimumRole: "operator",
    // Auditor has full read access to governance: policies, compliance, audit trail.
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["governance", "policies", "compliance", "audit"],
    discoverable: true,
    iconName: "Shield",
    group: "govern",
    description: "Policies, compliance, and audit",
  },
  governPrivacy: {
    path: "/govern/privacy",
    label: "Privacy Requests",
    minimumRole: "operator",
    // Auditor can view privacy request status but cannot submit or approve.
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["privacy", "gdpr", "ccpa"],
    discoverable: true,
    iconName: "Shield",
    group: "govern",
    description: "GDPR/CCPA fulfilment workbench",
  },
  catalogAgents: {
    path: "/catalog/agents",
    label: "Agents",
    minimumRole: "viewer",
    // Auditor can view agent registry.
    auditorAllowed: true,
    lifecycle: "active",
    capabilities: ["agent-registry"],
    discoverable: true,
    iconName: "Settings",
    group: "catalog",
    description: "Agent registry",
  },
  catalogMarketplace: {
    path: "/catalog/marketplace",
    label: "Marketplace",
    minimumRole: "viewer",
    // Auditor cannot acquire or enable marketplace agents.
    auditorAllowed: false,
    lifecycle: "active",
    capabilities: ["marketplace"],
    discoverable: true,
    iconName: "Settings",
    group: "catalog",
    description: "Agent marketplace",
  },
  catalogWorkflows: {
    path: "/catalog/workflows",
    label: "Workflows",
    minimumRole: "viewer",
    lifecycle: "active",
    capabilities: ["workflow-catalog"],
    discoverable: true,
    iconName: "FileText",
    group: "catalog",
    description: "Workflow catalog",
  },
};

// ---------------------------------------------------------------------------
// Registry queries
// ---------------------------------------------------------------------------

/**
 * Role hierarchy for non-auditor roles.
 * Auditor access is route-specific via `auditorAllowed` and is handled
 * separately in `canAccessRoute` and `getDiscoverableRoutes`.
 */
const roleHierarchy: Record<Exclude<UserRole, "auditor">, number> = {
  viewer: 0,
  operator: 1,
  admin: 2,
};

function roleMeetsMinimum(
  role: UserRole,
  minimum: Exclude<UserRole, "auditor">,
  route: RouteCapability
): boolean {
  if (role === "auditor") {
    // Auditor access is determined per-route by the `auditorAllowed` flag.
    // When not explicitly set, auditors can access viewer-minimum routes only.
    if (route.auditorAllowed !== undefined) {
      return route.auditorAllowed;
    }
    return minimum === "viewer";
  }
  return roleHierarchy[role] >= roleHierarchy[minimum];
}

/**
 * Get all discoverable routes for a given role.
 * Excludes 'boundary' routes from navigation by default;
 * pass includesBoundary=true to surface them (e.g., for admin tooling).
 */
export function getDiscoverableRoutes(
  role: UserRole,
  includesBoundary = false
): RouteCapability[] {
  return Object.values(aepRouteRegistry).filter(
    (route) =>
      route.discoverable &&
      roleMeetsMinimum(role, route.minimumRole, route) &&
      (includesBoundary || route.lifecycle !== "boundary")
  );
}

/**
 * Get all routes grouped by lifecycle state.
 */
export function getRoutesByLifecycle(): Record<
  RouteLifecycle,
  RouteCapability[]
> {
  const result: Record<RouteLifecycle, RouteCapability[]> = {
    active: [],
    preview: [],
    boundary: [],
    deprecated: [],
    redirect: [],
    removed: [],
  };
  for (const route of Object.values(aepRouteRegistry)) {
    result[route.lifecycle].push(route);
  }
  return result;
}

/**
 * Convert a registry path with parameters into a RegExp for matching.
 * E.g., "/operate/runs/:runId" → /^\/operate\/runs\/[^\/]+$/
 */
function pathToRegExp(path: string): RegExp {
  const pattern = path
    .replace(/:[^/]+/g, "[^/]+")
    .replace(/\*/g, ".*");
  return new RegExp(`^${pattern}$`);
}

/**
 * Get route by path. Supports parameterized paths.
 */
export function getRouteByPath(
  path: string
): RouteCapability | undefined {
  return Object.values(aepRouteRegistry).find(
    (route) => route.path === path || pathToRegExp(route.path).test(path)
  );
}

/**
 * Check if a role can access a specific path.
 * Supports parameterized paths via pattern matching.
 * Auditor access is determined per-route by the `auditorAllowed` flag.
 */
export function canAccessRoute(
  role: UserRole,
  path: string
): boolean {
  const route = getRouteByPath(path);
  if (!route) return false;
  return roleMeetsMinimum(role, route.minimumRole, route);
}

/**
 * Get all active routes (not redirect/removed/deprecated).
 */
export function getActiveRoutes(): RouteCapability[] {
  return Object.values(aepRouteRegistry).filter(
    (route) => route.lifecycle === "active"
  );
}
