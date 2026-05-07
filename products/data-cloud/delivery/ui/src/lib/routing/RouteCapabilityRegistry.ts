/**
 * Route Capability Registry
 *
 * Single source of truth for canonical routes, their capabilities,
 * required roles, and discoverability. Consumed by navigation,
 * global search, and route guards to prevent drift.
 *
 * @doc.type module
 * @doc.purpose Centralized route metadata and capability definitions
 * @doc.layer shared
 * @doc.pattern Registry
 */

import { z } from 'zod';
import type { ShellRole } from '../auth/session';

/**
 * Route lifecycle states
 */
export const RouteLifecycleSchema = z.enum([
  'active',      // Fully functional and fully implemented
  'preview',     // Functional but explicitly flagged as preview
  'boundary',    // Route exists but surface is handled by UnsupportedSurfaceBoundary
  'deprecated',  // Still routes but scheduled for removal
  'redirect',    // Redirects to another route
  'removed',     // No longer available
]);

export type RouteLifecycle = z.infer<typeof RouteLifecycleSchema>;

/**
 * Canonical route entry — single source of truth for a route.
 */
export const RouteCapabilitySchema = z.object({
  /** URL path segment (e.g., '/alerts') */
  path: z.string().min(1),
  /** Human-readable label */
  label: z.string().min(1),
  /** Required shell role for access */
  minimumShellRole: z.enum(['primary-user', 'operator', 'admin']).default('primary-user'),
  /** Functional lifecycle state */
  lifecycle: RouteLifecycleSchema.default('active'),
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
});

export type RouteCapability = z.infer<typeof RouteCapabilitySchema>;

/**
 * Route Capability Registry type
 */
export const RouteCapabilityRegistrySchema = z.record(z.string(), RouteCapabilitySchema);

export type RouteCapabilityRegistry = z.infer<typeof RouteCapabilityRegistrySchema>;

// ---------------------------------------------------------------------------
// Canonical registry — single source of truth for Data Cloud routes
// ---------------------------------------------------------------------------

export const canonicalRouteRegistry: RouteCapabilityRegistry = {
  home: {
    path: '/',
    label: 'Home',
    minimumShellRole: 'primary-user',
    lifecycle: 'active',
    capabilities: ['dashboard', 'overview'],
    discoverable: true,
    iconName: 'BarChart3',
    description: 'Dashboard overview and quick actions',
  },
  data: {
    path: '/data',
    label: 'Data',
    minimumShellRole: 'primary-user',
    lifecycle: 'active',
    capabilities: ['data-explorer', 'collections', 'lineage'],
    discoverable: true,
    iconName: 'Database',
    description: 'Explore and manage data collections',
  },
  connectors: {
    path: '/connectors',
    label: 'Connectors',
    minimumShellRole: 'operator',
    lifecycle: 'active',
    capabilities: ['data-connectors', 'external-data-sources'],
    discoverable: true,
    iconName: 'Network',
    description: 'Manage external data source connectors',
  },
  pipelines: {
    path: '/pipelines',
    label: 'Pipelines',
    minimumShellRole: 'primary-user',
    lifecycle: 'active',
    capabilities: ['workflows', 'plugin-execution'],
    discoverable: true,
    iconName: 'Workflow',
    description: 'Manage data-local plugin workflow execution and history',
  },
  query: {
    path: '/query',
    label: 'Query',
    minimumShellRole: 'primary-user',
    lifecycle: 'active',
    capabilities: ['query', 'sql'],
    discoverable: true,
    iconName: 'Terminal',
    description: 'SQL query editor and execution',
  },
  insights: {
    path: '/insights',
    label: 'Insights',
    minimumShellRole: 'operator',
    lifecycle: 'active',
    capabilities: ['analytics', 'automation-insights', 'cost'],
    discoverable: true,
    iconName: 'BarChart3',
    description: 'Analytics, automation insights, and cost optimization',
  },
  trust: {
    path: '/trust',
    label: 'Trust',
    minimumShellRole: 'operator',
    lifecycle: 'active',
    capabilities: ['governance', 'compliance', 'audit'],
    discoverable: true,
    iconName: 'Shield',
    description: 'Governance, compliance, and audit logs',
  },
  events: {
    path: '/events',
    label: 'Events',
    minimumShellRole: 'operator',
    lifecycle: 'active',
    capabilities: ['event-stream', 'aep'],
    discoverable: true,
    iconName: 'Activity',
    description: 'Real-time AEP event stream explorer',
  },
  // DC-UX-035: alerts — AlertsPage has full API integration (useQuery + useMutation). Promoted to 'preview'.
  alerts: {
    path: '/alerts',
    label: 'Alerts',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['alert-triage', 'monitoring'],
    discoverable: true,
    iconName: 'Bell',
    description: 'Operator alert triage console',
  },
  // DC-UX-035: memory — MemoryPlaneViewerPage is fully implemented. Promoted to 'preview' + discoverable.
  memory: {
    path: '/memory',
    label: 'Memory',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['memory-plane', 'context'],
    discoverable: true,
    iconName: 'Brain',
    description: 'Memory plane viewer',
  },
  // DC-UX-035: entities — EntityBrowserPage is implemented. Discoverable promoted to true.
  entities: {
    path: '/entities',
    label: 'Entities',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['entity-browser'],
    discoverable: true,
    iconName: 'Box',
    description: 'Entity browser',
  },
  // DC-UX-035: context — ContextExplorerPage is implemented. Discoverable promoted to true.
  context: {
    path: '/context',
    label: 'Context',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['context-explorer'],
    discoverable: true,
    iconName: 'Network',
    description: 'Context explorer',
  },
  // DC-UX-035: fabric — DataFabricPage has real API integration but uses runtime boundary gating.
  // Promoted to 'preview'; individual sections use UnsupportedSurfaceBoundary at runtime.
  fabric: {
    path: '/fabric',
    label: 'Data Fabric',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['data-fabric'],
    discoverable: true,
    iconName: 'Network',
    description: 'Data fabric topology and tier management',
  },
  // DC-UX-035: agents — AgentPluginManagerPage exists. Discoverable promoted to true.
  agents: {
    path: '/agents',
    label: 'Agents',
    minimumShellRole: 'operator',
    lifecycle: 'preview',
    capabilities: ['agent-catalog'],
    discoverable: true,
    iconName: 'Bot',
    description: 'Agent plugin catalog and manager',
  },
  operations: {
    path: '/operations',
    label: 'Operations',
    minimumShellRole: 'admin',
    lifecycle: 'active',
    capabilities: ['ops-console', 'diagnostics'],
    discoverable: true,
    iconName: 'Settings',
    description: 'Operations console and diagnostics',
  },
  operationsJobs: {
    path: '/operations/jobs',
    label: 'Job Center',
    minimumShellRole: 'admin',
    lifecycle: 'active',
    capabilities: ['ops-jobs', 'background-operations'],
    discoverable: false,
    description: 'Background operations timeline and status center',
  },
  // DC-UX-035: plugins page is active — boundary states are inside the page (catalog/delivery tabs)
  plugins: {
    path: '/plugins',
    label: 'Plugins',
    minimumShellRole: 'operator',
    lifecycle: 'active',
    capabilities: ['plugin-management'],
    discoverable: true,
    iconName: 'Package',
    description: 'Plugin management (operator preview — catalog and delivery tabs are boundary-scoped)',
  },
  // DC-UX-035: settings surface has UnavailablePanel in all sections
  settings: {
    path: '/settings',
    label: 'Settings',
    minimumShellRole: 'admin',
    lifecycle: 'boundary',
    capabilities: ['settings'],
    discoverable: false,
    description: 'System settings (surface under development)',
  },
};

// ---------------------------------------------------------------------------
// Registry queries
// ---------------------------------------------------------------------------

/**
 * Get all discoverable routes for a given role.
 * DC-UX-035: Excludes 'boundary' routes from navigation by default;
 * pass includesBoundary=true to surface them (e.g., for admin tooling).
 */
export function getDiscoverableRoutes(role: ShellRole, includesBoundary = false): RouteCapability[] {
  return Object.values(canonicalRouteRegistry).filter(
    (route) =>
      route.discoverable &&
      roleMeetsMinimum(role, route.minimumShellRole) &&
      (includesBoundary || route.lifecycle !== 'boundary')
  );
}

/**
 * DC-UX-034: Get all routes grouped by lifecycle state.
 * Used to generate the route truth matrix doc and CI validation.
 */
export function getRoutesByLifecycle(): Record<RouteLifecycle, RouteCapability[]> {
  const result: Record<RouteLifecycle, RouteCapability[]> = {
    active: [],
    preview: [],
    boundary: [],
    deprecated: [],
    redirect: [],
    removed: [],
  };
  for (const route of Object.values(canonicalRouteRegistry)) {
    result[route.lifecycle].push(route);
  }
  return result;
}

/**
 * Check if a role meets the minimum required role.
 */
function roleMeetsMinimum(role: ShellRole, minimum: ShellRole): boolean {
  const hierarchy: Record<ShellRole, number> = {
    'primary-user': 0,
    operator: 1,
    admin: 2,
  };
  return hierarchy[role] >= hierarchy[minimum];
}

/**
 * Get route by path.
 */
export function getRouteByPath(path: string): RouteCapability | undefined {
  return Object.values(canonicalRouteRegistry).find((route) => route.path === path);
}

/**
 * Get all active routes (not redirect/removed/deprecated).
 */
export function getActiveRoutes(): RouteCapability[] {
  return Object.values(canonicalRouteRegistry).filter(
    (route) => route.lifecycle === 'active'
  );
}
