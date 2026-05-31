/**
 * Route Surface Registry
 *
 * Single source of truth for canonical routes, route surfaces,
 * required roles, and discoverability. Consumed by navigation,
 * global search, and route guards to prevent drift.
 *
 * @doc.type module
 * @doc.purpose Centralized route metadata and runtime surface definitions
 * @doc.layer shared
 * @doc.pattern Registry
 */

import { z } from "zod";
import type { ShellRole } from "../auth/session";

export const RouteLifecycleSchema = z.enum([
  "active",
  "preview",
  "boundary",
  "deprecated",
  "redirect",
  "removed",
]);

export type RouteLifecycle = z.infer<typeof RouteLifecycleSchema>;

export const RouteSurfaceSchema = z.object({
  path: z.string().min(1),
  label: z.string().min(1),
  labelKey: z.string().min(1).optional(),
  minimumShellRole: z
    .enum(["primary-user", "operator", "admin"])
    .default("primary-user"),
  lifecycle: RouteLifecycleSchema.default("active"),
  capabilities: z.array(z.string()).default([]),
  discoverable: z.boolean().default(true),
  iconName: z.string().optional(),
  redirectTo: z.string().optional(),
  description: z.string().optional(),
  descriptionKey: z.string().min(1).optional(),
});

export type RouteSurface = z.infer<typeof RouteSurfaceSchema>;

export const RouteSurfaceRegistrySchema = z.record(
  z.string(),
  RouteSurfaceSchema,
);

export type RouteSurfaceRegistry = z.infer<typeof RouteSurfaceRegistrySchema>;

export const canonicalRouteSurfaceRegistry: RouteSurfaceRegistry = {
  home: {
    path: "/",
    label: "Home",
    labelKey: "routes.home.label",
    minimumShellRole: "primary-user",
    lifecycle: "active",
    capabilities: ["dashboard", "overview"],
    discoverable: true,
    iconName: "Home",
    description: "Dashboard overview and quick actions",
    descriptionKey: "routes.home.description",
  },
  data: {
    path: "/data",
    label: "Data",
    labelKey: "routes.data.label",
    minimumShellRole: "primary-user",
    lifecycle: "active",
    capabilities: ["data-explorer", "collections", "lineage"],
    discoverable: true,
    iconName: "Database",
    description: "Explore and manage data collections",
    descriptionKey: "routes.data.description",
  },
  connectors: {
    path: "/connectors",
    label: "Connectors",
    labelKey: "routes.connectors.label",
    minimumShellRole: "operator",
    lifecycle: "active",
    capabilities: ["data-connectors", "external-data-sources"],
    discoverable: false,
    iconName: "Network",
    description: "Manage external data source connectors",
    descriptionKey: "routes.connectors.description",
  },
  pipelines: {
    path: "/pipelines",
    label: "Pipelines",
    labelKey: "routes.pipelines.label",
    minimumShellRole: "primary-user",
    lifecycle: "active",
    capabilities: ["workflows", "plugin-execution"],
    discoverable: true,
    iconName: "Workflow",
    description: "Manage data-local plugin workflow execution and history",
    descriptionKey: "routes.pipelines.description",
  },
  query: {
    path: "/query",
    label: "Query",
    labelKey: "routes.query.label",
    minimumShellRole: "primary-user",
    lifecycle: "active",
    capabilities: ["query", "sql"],
    discoverable: true,
    iconName: "Terminal",
    description: "SQL query editor and execution",
    descriptionKey: "routes.query.description",
  },
  insights: {
    path: "/insights",
    label: "Insights",
    labelKey: "routes.insights.label",
    minimumShellRole: "operator",
    lifecycle: "active",
    capabilities: ["analytics", "automation-insights", "cost"],
    discoverable: false,
    iconName: "BarChart3",
    description: "Analytics, automation insights, and cost optimization",
    descriptionKey: "routes.insights.description",
  },
  trust: {
    path: "/trust",
    label: "Trust",
    labelKey: "routes.trust.label",
    minimumShellRole: "operator",
    lifecycle: "active",
    capabilities: ["governance", "compliance", "audit"],
    discoverable: true,
    iconName: "Shield",
    description: "Governance, compliance, and audit logs",
    descriptionKey: "routes.trust.description",
  },
  events: {
    path: "/events",
    label: "Events",
    labelKey: "routes.events.label",
    minimumShellRole: "operator",
    lifecycle: "active",
    capabilities: ["event-stream", "aep"],
    discoverable: true,
    iconName: "Activity",
    description: "Real-time event stream explorer",
    descriptionKey: "routes.events.description",
  },
  alerts: {
    path: "/alerts",
    label: "Alerts",
    labelKey: "routes.alerts.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["alert-triage", "monitoring"],
    discoverable: false,
    iconName: "Bell",
    description: "Operator alert triage console",
    descriptionKey: "routes.alerts.description",
  },
  memory: {
    path: "/memory",
    label: "Memory",
    labelKey: "routes.memory.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["memory-plane", "context"],
    discoverable: false,
    iconName: "Brain",
    description: "Memory plane viewer",
    descriptionKey: "routes.memory.description",
  },
  entities: {
    path: "/entities",
    label: "Entities",
    labelKey: "routes.entities.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["entity-browser"],
    discoverable: false,
    iconName: "Box",
    description: "Entity browser",
    descriptionKey: "routes.entities.description",
  },
  context: {
    path: "/context",
    label: "Context",
    labelKey: "routes.context.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["context-explorer"],
    discoverable: false,
    iconName: "Network",
    description: "Context explorer",
    descriptionKey: "routes.context.description",
  },
  fabric: {
    path: "/fabric",
    label: "Data Fabric",
    labelKey: "routes.fabric.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["data-fabric"],
    discoverable: false,
    iconName: "Network",
    description: "Data fabric topology and tier management",
    descriptionKey: "routes.fabric.description",
  },
  agents: {
    path: "/agents",
    label: "Agents",
    labelKey: "routes.agents.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["agent-catalog"],
    discoverable: false,
    iconName: "Bot",
    description: "Agent catalog and manager",
    descriptionKey: "routes.agents.description",
  },
  mediaArtifacts: {
    path: "/media/artifacts",
    label: "Media Artifacts",
    labelKey: "routes.mediaArtifacts.label",
    minimumShellRole: "operator",
    lifecycle: "preview",
    capabilities: ["media", "media-artifacts", "audio-video"],
    discoverable: false,
    iconName: "FileText",
    description: "Audio-video media lifecycle management",
    descriptionKey: "routes.mediaArtifacts.description",
  },
  operations: {
    path: "/operations",
    label: "Operations",
    labelKey: "routes.operations.label",
    minimumShellRole: "admin",
    lifecycle: "active",
    capabilities: ["ops-console", "diagnostics"],
    discoverable: true,
    iconName: "Settings",
    description: "Operations console and diagnostics",
    descriptionKey: "routes.operations.description",
  },
  operationsJobs: {
    path: "/operations/jobs",
    label: "Job Center",
    labelKey: "routes.operationsJobs.label",
    minimumShellRole: "admin",
    lifecycle: "active",
    capabilities: ["ops-jobs", "background-operations"],
    discoverable: false,
    iconName: "ListChecks",
    description: "Background operations timeline and status center",
    descriptionKey: "routes.operationsJobs.description",
  },
  operationsReleaseTruth: {
    path: "/operations/release-truth",
    label: "Release Truth",
    labelKey: "routes.operationsReleaseTruth.label",
    minimumShellRole: "admin",
    lifecycle: "boundary",
    capabilities: ["runtime-truth", "governance.audit", "health.eventStore"],
    discoverable: false,
    iconName: "ShieldCheck",
    description:
      "Runtime truth, release gates, and evidence dashboard (non-release-readiness scope)",
    descriptionKey: "routes.operationsReleaseTruth.description",
  },
  plugins: {
    path: "/plugins",
    label: "Plugins",
    labelKey: "routes.plugins.label",
    minimumShellRole: "operator",
    lifecycle: "active",
    capabilities: ["plugin-management"],
    discoverable: false,
    iconName: "Package",
    description: "Plugin management surfaces",
    descriptionKey: "routes.plugins.description",
  },
  settings: {
    path: "/settings",
    label: "Settings",
    labelKey: "routes.settings.label",
    minimumShellRole: "admin",
    lifecycle: "boundary",
    capabilities: ["settings"],
    discoverable: false,
    iconName: "Settings2",
    description: "System settings (surface under development)",
    descriptionKey: "routes.settings.description",
  },
};

export function getDiscoverableRouteSurfaces(
  role: ShellRole,
  includesBoundary = false,
): RouteSurface[] {
  return Object.values(canonicalRouteSurfaceRegistry).filter(
    (route) =>
      route.discoverable &&
      roleMeetsMinimum(role, route.minimumShellRole) &&
      (includesBoundary || route.lifecycle !== "boundary"),
  );
}

export function getRouteSurfacesByLifecycle(): Record<
  RouteLifecycle,
  RouteSurface[]
> {
  const result: Record<RouteLifecycle, RouteSurface[]> = {
    active: [],
    preview: [],
    boundary: [],
    deprecated: [],
    redirect: [],
    removed: [],
  };
  for (const route of Object.values(canonicalRouteSurfaceRegistry)) {
    result[route.lifecycle].push(route);
  }
  return result;
}

function roleMeetsMinimum(role: ShellRole, minimum: ShellRole): boolean {
  const hierarchy: Record<ShellRole, number> = {
    "primary-user": 0,
    operator: 1,
    admin: 2,
  };
  return hierarchy[role] >= hierarchy[minimum];
}

export function getRouteSurfaceByPath(path: string): RouteSurface | undefined {
  return Object.values(canonicalRouteSurfaceRegistry).find(
    (route) => route.path === path,
  );
}

export function getActiveRouteSurfaces(): RouteSurface[] {
  return Object.values(canonicalRouteSurfaceRegistry).filter(
    (route) => route.lifecycle === "active",
  );
}

// Backward-compat aliases for legacy imports.
export const RouteCapabilitySchema = RouteSurfaceSchema;
export type RouteCapability = RouteSurface;
export const RouteCapabilityRegistrySchema = RouteSurfaceRegistrySchema;
export type RouteCapabilityRegistry = RouteSurfaceRegistry;
export const canonicalRouteRegistry = canonicalRouteSurfaceRegistry;
export const getDiscoverableRoutes = getDiscoverableRouteSurfaces;
export const getRouteByPath = getRouteSurfaceByPath;
export const getActiveRoutes = getActiveRouteSurfaces;
export const getRoutesByLifecycle = getRouteSurfacesByLifecycle;
