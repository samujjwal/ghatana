/**
 * React Router v7 Route Configuration
 *
 * Simplified route structure following the improvement plan:
 * - Unified Data Explorer (replaces Collections, Datasets, Lineage, Quality)
 * - Unified Pipeline Center (replaces Workflows)
 * - Unified Insights (operator analytics, brain status, cost review)
 * - Trust Center (replaces Governance)
 *
 * @doc.type config
 * @doc.purpose Application routing configuration
 * @doc.layer frontend
 */

import React from "react";
import type { RouteObject } from "react-router";
import { Navigate, useNavigate, Outlet } from "react-router";
import { useTranslation } from "react-i18next";
import { DefaultLayout } from "./layouts/DefaultLayout";
import { LoadingState } from "./components/common/LoadingState";
import { RouteErrorBoundary } from "./components/common/RouteErrorBoundary";
import { RoleProtectedRoute } from "./components/security/RoleProtectedRoute";
import { RuntimeCapabilityRouteGate } from "./components/security/RuntimeCapabilityRouteGate";
import { emitDataCloudDiagnostic } from "./diagnostics";
import {
  isAgentCatalogSurfaceEnabled,
  isAlertsSurfaceEnabled,
  isContextSurfaceEnabled,
  isEntityBrowserSurfaceEnabled,
  isFabricSurfaceEnabled,
  isMemorySurfaceEnabled,
  isSettingsSurfaceEnabled,
} from "./lib/feature-gates";

/** Warn in dev when a lazy chunk takes longer than this to load. */
const SLOW_LOAD_WARN_MS = 3_000;

// =============================================================================
// LAZY LOADED PAGES (Optimized with preloading)
// =============================================================================

// Core Pages - optimized lazy loading with preloading hints
const IntelligentHub = React.lazy(() =>
  import("./pages/IntelligentHub").then((m) => ({ default: m.IntelligentHub })),
);
const DataExplorer = React.lazy(() =>
  import("./pages/DataExplorer").then((m) => ({ default: m.DataExplorer })),
);
const DataPage = React.lazy(() =>
  import("./pages/DataPage").then((m) => ({ default: m.default })),
);
const SmartWorkflowBuilder = React.lazy(() =>
  import("./pages/SmartWorkflowBuilder").then((m) => ({
    default: m.SmartWorkflowBuilder,
  })),
);
const WorkflowsPage = React.lazy(() =>
  import("./pages/WorkflowsPage").then((m) => ({ default: m.WorkflowsPage })),
);
const WorkflowDesigner = React.lazy(() =>
  import("./pages/WorkflowDesigner").then((m) => ({
    default: m.WorkflowDesigner,
  })),
);
const SqlWorkspacePage = React.lazy(() =>
  import("./pages/SqlWorkspacePage").then((m) => ({
    default: m.SqlWorkspacePage,
  })),
);
const TrustCenter = React.lazy(() =>
  import("./pages/TrustCenter").then((m) => ({ default: m.TrustCenter })),
);
const OperationsConsole = React.lazy(() =>
  import("./pages/OperationsConsolePage").then((m) => ({
    default: m.OperationsConsolePage,
  })),
);
const OperationsJobCenterPage = React.lazy(() =>
  import("./pages/OperationsJobCenterPage").then((m) => ({
    default: m.OperationsJobCenterPage,
  })),
);
const MediaArtifactPage = React.lazy(() =>
  import("./pages/MediaArtifactPage").then((m) => ({
    default: m.MediaArtifactPage,
  })),
);
const ReleaseTruthDashboardPage = React.lazy(() =>
  import("./pages/ReleaseTruthDashboardPage").then((m) => ({
    default: m.ReleaseTruthDashboardPage,
  })),
);
// DC-P3-002: Runtime Truth page with plane/surface/dependency drilldown
const RuntimeTruthPage = React.lazy(() =>
  import("./pages/RuntimeTruthPage").then((m) => ({
    default: m.RuntimeTruthPage,
  })),
);
const DataQualityTrustPage = React.lazy(() =>
  import("./pages/DataQualityTrustPage").then((m) => ({
    default: m.DataQualityTrustPage,
  })),
);
const PolicySimulationPage = React.lazy(() =>
  import("./pages/PolicySimulationPage").then((m) => ({
    default: m.PolicySimulationPage,
  })),
);
const TenantGovernancePage = React.lazy(() =>
  import("./pages/TenantGovernancePage").then((m) => ({
    default: m.TenantGovernancePage,
  })),
);
const InsightsPage = React.lazy(() =>
  import("./pages/InsightsPage").then((m) => ({ default: m.InsightsPage })),
);
const AlertsPage = React.lazy(() =>
  import("./pages/AlertsPage").then((m) => ({ default: m.AlertsPage })),
);
const SettingsPage = React.lazy(() =>
  import("./pages/SettingsPage").then((m) => ({ default: m.SettingsPage })),
);
const PluginsPage = React.lazy(() =>
  import("./pages/PluginsPage").then((m) => ({ default: m.PluginsPage })),
);
const PluginDetailsPage = React.lazy(() =>
  import("./pages/PluginDetailsPage").then((m) => ({
    default: m.PluginDetailsPage,
  })),
);
const NotFound = React.lazy(() =>
  import("./pages/NotFound").then((m) => ({ default: m.NotFound })),
);
const DisabledSurfacePage = React.lazy(() =>
  import("./pages/DisabledSurfacePage").then((m) => ({
    default: m.DisabledSurfacePage,
  })),
);

// AEP Integration Pages — new in Track 4
const EventExplorerPage = React.lazy(() =>
  import("./pages/EventExplorerPage").then((m) => ({
    default: m.EventExplorerPage,
  })),
);
const MemoryPlaneViewerPage = React.lazy(() =>
  import("./pages/MemoryPlaneViewerPage").then((m) => ({
    default: m.MemoryPlaneViewerPage,
  })),
);
const EntityBrowserPage = React.lazy(() =>
  import("./pages/EntityBrowserPage").then((m) => ({
    default: m.EntityBrowserPage,
  })),
);
const ContextExplorerPage = React.lazy(() =>
  import("./pages/ContextExplorerPage").then((m) => ({
    default: m.ContextExplorerPage,
  })),
);
const DataFabricPage = React.lazy(() =>
  import("./pages/DataFabricPage").then((m) => ({ default: m.DataFabricPage })),
);
const AgentPluginManagerPage = React.lazy(() =>
  import("./pages/AgentPluginManagerPage").then((m) => ({
    default: m.AgentPluginManagerPage,
  })),
);
const DataConnectorsPage = React.lazy(() =>
  import("./features/data-fabric/components/DataConnectorsPage").then((m) => ({
    default: m.DataConnectorsPage,
  })),
);

// Wrapper component to provide required props to DataConnectorsPage
const DataConnectorsPageWrapper: React.FC = () => {
  const navigate = useNavigate();
  const resolveConnectorId = (connector: unknown): string | null => {
    if (typeof connector !== "object" || connector === null) {
      return null;
    }
    const maybeId = (connector as { id?: unknown }).id;
    return typeof maybeId === "string" && maybeId.trim().length > 0
      ? maybeId
      : null;
  };

  return React.createElement(DataConnectorsPage, {
    onCreateClick: () => navigate("/connectors?mode=create"),
    onEditClick: (connector: unknown) => {
      const connectorId = resolveConnectorId(connector);
      if (connectorId) {
        navigate(`/connectors?mode=edit&id=${encodeURIComponent(connectorId)}`);
      }
    },
  });
};

// Compatibility alias pages (kept for deep-link continuity)
const CreateCollectionPage = React.lazy(() =>
  import("./pages/CreateCollectionPage").then((m) => ({
    default: m.CreateCollectionPage,
  })),
);
const EditCollectionPage = React.lazy(() =>
  import("./pages/EditCollectionPage").then((m) => ({
    default: m.EditCollectionPage,
  })),
);

// =============================================================================
// LOADING FALLBACK
// =============================================================================

function PageLoader(): React.ReactElement {
  const { t } = useTranslation();

  React.useEffect(() => {
    if (!import.meta.env.DEV) return;
    const startTime = Date.now();
    const timer = setTimeout(() => {
      const elapsed = Date.now() - startTime;
      emitDataCloudDiagnostic("PageLoader", "warn", "Chunk still loading", {
        elapsedMs: elapsed,
      });
    }, SLOW_LOAD_WARN_MS);
    return () => clearTimeout(timer);
  }, []);

  return <LoadingState message={t('routes.loading')} className="w-full h-64" />;
}

/**
 * Error Boundary scoped to lazy-loaded page chunks.
 * Provides an inline recovery UI (not full-screen) when a page chunk fails.
 */
class LazyLoadErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error?: Error }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error) {
    if (import.meta.env.DEV) {
      emitDataCloudDiagnostic("LazyLoadErrorBoundary", "error", "Lazy page failed to load", {
        error,
      });
    }
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    if (import.meta.env.DEV) {
      emitDataCloudDiagnostic("LazyLoadErrorBoundary", "error", "Lazy page component stack captured", {
        error,
        componentStack: info.componentStack,
      });
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <LazyLoadErrorBoundaryContent error={this.state.error} />
      );
    }
    return this.props.children;
  }
}

function LazyLoadErrorBoundaryContent({ error }: { error?: Error }): React.ReactElement {
  const { t } = useTranslation();

  return (
    <div
      role="alert"
      className="flex items-center justify-center w-full min-h-64 p-6"
    >
      <div className="text-center max-w-md">
        <h2 className="text-lg font-semibold text-red-600 mb-2">
          {t('routes.loadFailed')}
        </h2>
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
          {error?.message ?? t('routes.unknownError')}
        </p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          {t('routes.reload')}
        </button>
      </div>
    </div>
  );
}

/**
 * Wrap lazy or regular component with Suspense and Error Boundary.
 * DC-P1-004: accepts both lazy exotic components and plain function components
 * so that inline disabled-surface fallbacks compile without type errors.
 */
function withSuspense(
  Component:
    | React.ComponentType
    | React.LazyExoticComponent<React.ComponentType>,
): React.ReactElement {
  return (
    <LazyLoadErrorBoundary>
      <React.Suspense fallback={<PageLoader />}>
        <Component />
      </React.Suspense>
    </LazyLoadErrorBoundary>
  );
}

/**
 * Runtime-truth-driven route gating.
 * 
 * Instead of returning a generic 404 when disabled, this now renders
 * DisabledSurfacePage with meaningful context about why the surface
 * is unavailable. This provides progressive disclosure based on runtime
 * truth rather than silent failures.
 * 
 * DC-P1-004: Replaced NotFound with DisabledSurfacePage for better UX.
 */
function featureGatedRoute(
  enabled: boolean,
  element: React.ReactElement,
  surfaceName: string,
  surfaceDescription: string,
): React.ReactElement {
  if (!enabled) {
    return withSuspense(() => (
      <DisabledSurfacePageContent
        surfaceName={surfaceName}
        surfaceDescription={surfaceDescription}
      />
    ));
  }
  return element;
}

function DisabledSurfacePageContent({ surfaceName, surfaceDescription }: { surfaceName: string; surfaceDescription: string }): React.ReactElement {
  const { t } = useTranslation();

  return (
    <DisabledSurfacePage
      surfaceName={surfaceName}
      surfaceDescription={surfaceDescription}
      status="DISABLED"
      nextAction={t('disabledSurface.nextAction')}
    />
  );
}

// =============================================================================
// ROUTE CONFIGURATION
// =============================================================================

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <DefaultLayout />,
    errorElement: <RouteErrorBoundary />,
    children: [
      // =========================================
      // PRIMARY ROUTES (New Simplified IA)
      // =========================================

      // Home - Intelligent Hub
      {
        index: true,
        element: withSuspense(IntelligentHub),
      },

      // Data - Unified Data Surface (Collections, Entities, Context, Fabric)
      {
        path: "data",
        element: withSuspense(DataPage),
      },
      // Data sub-routes for collection management (kept for deep-link compatibility)
      {
        path: "data/new",
        element: withSuspense(CreateCollectionPage),
      },
      {
        path: "data/:id",
        element: withSuspense(DataExplorer),
      },
      {
        path: "data/:id/edit",
        element: withSuspense(EditCollectionPage),
      },
      {
        path: "data/:id/:view",
        element: withSuspense(DataExplorer),
      },

      // Pipelines - Workflow Management
      {
        path: "pipelines",
        children: [
          {
            index: true,
            element: withSuspense(WorkflowsPage),
          },
          {
            path: "new",
            element: withSuspense(SmartWorkflowBuilder),
          },
          {
            path: ":id",
            element: withSuspense(WorkflowDesigner),
          },
          {
            path: ":id/edit",
            element: withSuspense(WorkflowDesigner),
          },
        ],
      },

      // Query - SQL Workspace
      {
        path: "query",
        element: withSuspense(SqlWorkspacePage),
      },

      // Trust - Governance & Compliance
      {
        path: "trust",
        element: (
          <RoleProtectedRoute routePath="/trust">
            {withSuspense(TrustCenter)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "trust/simulation",
        element: (
          <RoleProtectedRoute routePath="/trust">
            {withSuspense(PolicySimulationPage)}
          </RoleProtectedRoute>
        ),
      },

      // Insights - Unified Analytics
      {
        path: "insights",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(InsightsPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "insights/data-quality-trust",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(DataQualityTrustPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "insights/tenant-governance",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(TenantGovernancePage)}
          </RoleProtectedRoute>
        ),
      },

      // Alerts - operator-facing alert triage console (restored as canonical route)
      {
        path: "alerts",
        element: featureGatedRoute(
          isAlertsSurfaceEnabled(),
          <RoleProtectedRoute routePath="/alerts">
            <RuntimeCapabilityRouteGate
              aliases={["alert-triage", "monitoring", "alerts"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Alerts"
                  surfaceDescription="The Alerts surface provides real-time alert triage and monitoring for your Data Cloud deployment."
                />
              ))}
            >
              {withSuspense(AlertsPage)}
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>,
          "Alerts",
          "The Alerts surface provides real-time alert triage and monitoring for your Data Cloud deployment.",
        ),
      },

      // Operations Console - Operator-facing diagnostics and tools
      {
        path: "operations",
        element: (
          <RoleProtectedRoute routePath="/operations">
            {withSuspense(OperationsConsole)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "operations/jobs",
        element: (
          <RoleProtectedRoute routePath="/operations/jobs">
            {withSuspense(OperationsJobCenterPage)}
          </RoleProtectedRoute>
        ),
      },
      // Media Artifacts - Audio-video lifecycle management
      {
        path: "media/artifacts",
        element: (
          <RoleProtectedRoute routePath="/media/artifacts">
            {withSuspense(MediaArtifactPage)}
          </RoleProtectedRoute>
        ),
      },
      // Release-truth route hidden per Group 7 requirement - not discoverable in this iteration

      // DC-P3-002: Runtime Truth — plane/surface/dependency drilldown
      {
        path: "operations/runtime-truth",
        element: (
          <RoleProtectedRoute routePath="/operations/runtime-truth">
            {withSuspense(RuntimeTruthPage)}
          </RoleProtectedRoute>
        ),
      },

      // AEP Integration Pages
      // Event Explorer — real-time AEP event stream explorer
      // DC-P1-003: gated on runtime truth
      {
        path: "events",
        element: (
          <RoleProtectedRoute routePath="/events">
            <RuntimeCapabilityRouteGate
              aliases={["event-stream", "aep", "event-explorer", "events"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Event Explorer"
                  surfaceDescription="The Event Explorer surface provides real-time AEP event stream inspection and replay."
                />
              ))}
            >
              {withSuspense(EventExplorerPage)}
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>
        ),
      },
      // Memory Plane Viewer — restored as canonical route
      {
        path: "memory",
        element: featureGatedRoute(
          isMemorySurfaceEnabled(),
          <RoleProtectedRoute routePath="/memory">
            <RuntimeCapabilityRouteGate
              aliases={["memory-plane", "memory"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Memory Plane"
                  surfaceDescription="The Memory Plane surface provides persistent memory and context management for AI agent workloads."
                />
              ))}
            >
              {withSuspense(MemoryPlaneViewerPage)}
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>,
          "Memory Plane",
          "The Memory Plane surface provides persistent memory and context management for AI agent workloads.",
        ),
      },
      // Entity Browser — redirect to unified Data surface with entities tab
      {
        path: "entities",
        element: <Navigate to="/data?tab=entities" replace />,
      },
      // Context Explorer — redirect to unified Data surface with context tab
      {
        path: "context",
        element: <Navigate to="/data?tab=context" replace />,
      },
      // Data Fabric — redirect to unified Data surface with fabric tab
      {
        path: "fabric",
        element: <Navigate to="/data?tab=fabric" replace />,
      },
      // Agent Catalog — restored as canonical operator-facing route
      {
        path: "agents",
        element: featureGatedRoute(
          isAgentCatalogSurfaceEnabled(),
          <RoleProtectedRoute routePath="/agents">
            <RuntimeCapabilityRouteGate
              aliases={["agent-catalog", "agents"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Agent Catalog"
                  surfaceDescription="The Agent Catalog surface provides discovery, registration, and management of AI agents in your deployment."
                />
              ))}
            >
              {withSuspense(AgentPluginManagerPage)}
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>,
          "Agent Catalog",
          "The Agent Catalog surface provides discovery, registration, and management of AI agents in your deployment.",
        ),
      },

      // Settings
      {
        path: "settings",
        element: featureGatedRoute(
          isSettingsSurfaceEnabled(),
          <RoleProtectedRoute routePath="/settings">
            <RuntimeCapabilityRouteGate
              aliases={["settings", "config"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Settings"
                  surfaceDescription="The Settings surface provides configuration management for your Data Cloud tenant."
                />
              ))}
            >
              {withSuspense(SettingsPage)}
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>,
          "Settings",
          "The Settings surface provides configuration management for your Data Cloud tenant.",
        ),
      },

      // Plugins — DC-P1-003: gated on runtime truth
      // DC-P1-009: Fixed duplicate rendering by using Outlet pattern
      {
        path: "plugins",
        element: (
          <RoleProtectedRoute routePath="/plugins">
            <RuntimeCapabilityRouteGate
              aliases={["plugin-management", "plugins", "extensions"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Plugins"
                  surfaceDescription="The Plugins surface provides extension and integration management for your Data Cloud tenant."
                />
              ))}
            >
              <React.Suspense fallback={<PageLoader />}>
                <Outlet />
              </React.Suspense>
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>
        ),
        children: [
          {
            index: true,
            element: withSuspense(PluginsPage),
          },
          {
            path: ":id",
            element: withSuspense(PluginDetailsPage),
          },
        ],
      },

      // Connectors - Data source connector management
      {
        path: "connectors",
        element: (
          <RoleProtectedRoute routePath="/connectors">
            <RuntimeCapabilityRouteGate
              aliases={["data-connectors", "connectors"]}
              fallback={withSuspense(() => (
                <DisabledSurfacePage
                  surfaceName="Data Connectors"
                  surfaceDescription="The Data Connectors surface provides management of external data source integrations for your deployment."
                />
              ))}
            >
              <LazyLoadErrorBoundary>
                <React.Suspense fallback={<PageLoader />}>
                  <DataConnectorsPageWrapper />
                </React.Suspense>
              </LazyLoadErrorBoundary>
            </RuntimeCapabilityRouteGate>
          </RoleProtectedRoute>
        ),
      },

      // =========================================
      // COMPATIBILITY ROUTES (Deep-link Continuity)
      // =========================================

      // =========================================
      // COMPATIBILITY ROUTES (DC-P1-008)
      // Classification: redirect/remove/keep
      //
      // KEEP (Permanent redirects for backward compatibility):
      // - dashboard, hub -> / (home)
      // - collections/* -> /data/* (unified Data Explorer)
      // - datasets -> /data (unified Data Explorer)
      // - workflows/* -> /pipelines/* (unified Pipeline Center)
      // - sql -> /query (canonical naming)
      // - governance -> /trust (Trust Center rebrand)
      // - brain, dashboards, cost -> /insights (unified Insights)
      //
      // REDIRECT (View-specific redirects within Data Explorer):
      // - lineage -> /data?view=lineage (lineage view mode)
      // - quality -> /data?view=quality (quality view mode)
      //
      // DECISION: All compatibility routes are kept as permanent redirects to support
      // existing bookmarks, documentation, and user workflows. These should remain
      // indefinitely as they provide zero-cost backward compatibility.
      // =========================================

      {
        path: "dashboard",
        element: (
          <RoleProtectedRoute routePath="/">
            {withSuspense(IntelligentHub)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "hub",
        element: (
          <RoleProtectedRoute routePath="/">
            {withSuspense(IntelligentHub)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "collections",
        element: (
          <RoleProtectedRoute routePath="/data">
            {withSuspense(DataExplorer)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "collections/new",
        element: (
          <RoleProtectedRoute routePath="/data">
            {withSuspense(CreateCollectionPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "collections/:id",
        element: (
          <RoleProtectedRoute routePath="/data">
            {withSuspense(DataExplorer)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "collections/:id/edit",
        element: (
          <RoleProtectedRoute routePath="/data">
            {withSuspense(EditCollectionPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "datasets",
        element: (
          <RoleProtectedRoute routePath="/data">
            {withSuspense(DataExplorer)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "lineage",
        element: <Navigate to="/data?view=lineage" replace />,
      },
      {
        path: "quality",
        element: <Navigate to="/data?view=quality" replace />,
      },
      {
        path: "workflows",
        element: (
          <RoleProtectedRoute routePath="/pipelines">
            {withSuspense(WorkflowsPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "workflows/new",
        element: (
          <RoleProtectedRoute routePath="/pipelines">
            {withSuspense(SmartWorkflowBuilder)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "workflows/:id",
        element: (
          <RoleProtectedRoute routePath="/pipelines">
            {withSuspense(WorkflowDesigner)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "sql",
        element: (
          <RoleProtectedRoute routePath="/query">
            {withSuspense(SqlWorkspacePage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "governance",
        element: (
          <RoleProtectedRoute routePath="/trust">
            {withSuspense(TrustCenter)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "brain",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(InsightsPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "dashboards",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(InsightsPage)}
          </RoleProtectedRoute>
        ),
      },
      {
        path: "cost",
        element: (
          <RoleProtectedRoute routePath="/insights">
            {withSuspense(InsightsPage)}
          </RoleProtectedRoute>
        ),
      },

      // 404
      {
        path: "*",
        element: withSuspense(NotFound),
      },
    ],
  },
];

export default routes;
