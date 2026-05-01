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

import React from 'react';
import type { RouteObject } from 'react-router';
import { Navigate, useNavigate } from 'react-router';
import { DefaultLayout } from './layouts/DefaultLayout';
import { LoadingState } from './components/common/LoadingState';
import { RouteErrorBoundary } from './components/common/RouteErrorBoundary';
import {
  isAgentCatalogSurfaceEnabled,
  isAlertsSurfaceEnabled,
  isContextSurfaceEnabled,
  isEntityBrowserSurfaceEnabled,
  isFabricSurfaceEnabled,
  isMemorySurfaceEnabled,
  isSettingsSurfaceEnabled,
} from './lib/feature-gates';

/** Warn in dev when a lazy chunk takes longer than this to load. */
const SLOW_LOAD_WARN_MS = 3_000;

// =============================================================================
// LAZY LOADED PAGES (Optimized with preloading)
// =============================================================================

// Core Pages - optimized lazy loading with preloading hints
const IntelligentHub = React.lazy(() =>
  import('./pages/IntelligentHub').then((m) => ({ default: m.IntelligentHub }))
);
const DataExplorer = React.lazy(() =>
  import('./pages/DataExplorer').then((m) => ({ default: m.DataExplorer }))
);
const SmartWorkflowBuilder = React.lazy(() =>
  import('./pages/SmartWorkflowBuilder').then((m) => ({ default: m.SmartWorkflowBuilder }))
);
const WorkflowsPage = React.lazy(() =>
  import('./pages/WorkflowsPage').then((m) => ({ default: m.WorkflowsPage }))
);
const WorkflowDesigner = React.lazy(() =>
  import('./pages/WorkflowDesigner').then((m) => ({ default: m.WorkflowDesigner }))
);
const SqlWorkspacePage = React.lazy(() =>
  import('./pages/SqlWorkspacePage').then((m) => ({ default: m.SqlWorkspacePage }))
);
const TrustCenter = React.lazy(() =>
  import('./pages/TrustCenter').then((m) => ({ default: m.TrustCenter }))
);
const OperationsConsole = React.lazy(() =>
  import('./pages/OperationsConsolePage').then((m) => ({ default: m.OperationsConsolePage }))
);
const OperationsJobCenterPage = React.lazy(() =>
  import('./pages/OperationsJobCenterPage').then((m) => ({ default: m.OperationsJobCenterPage }))
);
const InsightsPage = React.lazy(() =>
  import('./pages/InsightsPage').then((m) => ({ default: m.InsightsPage }))
);
const AlertsPage = React.lazy(() =>
  import('./pages/AlertsPage').then((m) => ({ default: m.AlertsPage }))
);
const SettingsPage = React.lazy(() =>
  import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage }))
);
const PluginsPage = React.lazy(() =>
  import('./pages/PluginsPage').then((m) => ({ default: m.PluginsPage }))
);
const PluginDetailsPage = React.lazy(() =>
  import('./pages/PluginDetailsPage').then((m) => ({ default: m.PluginDetailsPage }))
);
const NotFound = React.lazy(() =>
  import('./pages/NotFound').then((m) => ({ default: m.NotFound }))
);

// AEP Integration Pages — new in Track 4
const EventExplorerPage = React.lazy(() =>
  import('./pages/EventExplorerPage').then((m) => ({ default: m.EventExplorerPage }))
);
const MemoryPlaneViewerPage = React.lazy(() =>
  import('./pages/MemoryPlaneViewerPage').then((m) => ({ default: m.MemoryPlaneViewerPage }))
);
const EntityBrowserPage = React.lazy(() =>
  import('./pages/EntityBrowserPage').then((m) => ({ default: m.EntityBrowserPage }))
);
const ContextExplorerPage = React.lazy(() =>
  import('./pages/ContextExplorerPage').then((m) => ({ default: m.ContextExplorerPage }))
);
const DataFabricPage = React.lazy(() =>
  import('./pages/DataFabricPage').then((m) => ({ default: m.DataFabricPage }))
);
const AgentPluginManagerPage = React.lazy(() =>
  import('./pages/AgentPluginManagerPage').then((m) => ({ default: m.AgentPluginManagerPage }))
);
const DataConnectorsPage = React.lazy(() =>
  import('./features/data-fabric/components/DataConnectorsPage').then((m) => ({ default: m.DataConnectorsPage }))
);

// Wrapper component to provide required props to DataConnectorsPage
const DataConnectorsPageWrapper: React.FC = () => {
  const navigate = useNavigate();
  return React.createElement(DataConnectorsPage, {
    onCreateClick: () => navigate('/connectors/new'),
    onEditClick: (connector: any) => navigate(`/connectors/${connector.id}/edit`),
  });
};

// Compatibility alias pages (kept for deep-link continuity)
const CreateCollectionPage = React.lazy(() =>
  import('./pages/CreateCollectionPage').then((m) => ({ default: m.CreateCollectionPage }))
);
const EditCollectionPage = React.lazy(() =>
  import('./pages/EditCollectionPage').then((m) => ({ default: m.EditCollectionPage }))
);

// =============================================================================
// LOADING FALLBACK
// =============================================================================

function PageLoader(): React.ReactElement {
  React.useEffect(() => {
    if (!import.meta.env.DEV) return;
    const startTime = Date.now();
    const timer = setTimeout(() => {
      const elapsed = Date.now() - startTime;
      console.warn(
        `[PageLoader] Chunk still loading after ${elapsed}ms. Check for large bundles, circular deps, or network issues.`
      );
    }, SLOW_LOAD_WARN_MS);
    return () => clearTimeout(timer);
  }, []);

  return <LoadingState message="Loading..." className="w-full h-64" />;
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
      console.error('[LazyLoadErrorBoundary]', error);
    }
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('[LazyLoadErrorBoundary] Component stack:', info.componentStack);
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="flex items-center justify-center w-full min-h-64 p-6"
        >
          <div className="text-center max-w-md">
            <h2 className="text-lg font-semibold text-red-600 mb-2">
              Failed to load page
            </h2>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              {this.state.error?.message ?? 'Unknown error occurred while loading the component'}
            </p>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              Reload Page
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

/**
 * Wrap lazy component with Suspense and Error Boundary
 */
function withSuspense(Component: React.LazyExoticComponent<React.ComponentType>): React.ReactElement {
  return (
    <LazyLoadErrorBoundary>
      <React.Suspense fallback={<PageLoader />}>
        <Component />
      </React.Suspense>
    </LazyLoadErrorBoundary>
  );
}

// =============================================================================
// ROUTE CONFIGURATION
// =============================================================================

export const routes: RouteObject[] = [
  {
    path: '/',
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

      // Data - Unified Data Explorer
      {
        path: 'data',
        children: [
          {
            index: true,
            element: withSuspense(DataExplorer),
          },
          {
            path: 'new',
            element: withSuspense(CreateCollectionPage),
          },
          {
            path: ':id',
            element: withSuspense(DataExplorer),
          },
          {
            path: ':id/edit',
            element: withSuspense(EditCollectionPage),
          },
          {
            path: ':id/:view',
            element: withSuspense(DataExplorer),
          },
        ],
      },

      // Pipelines - Workflow Management
      {
        path: 'pipelines',
        children: [
          {
            index: true,
            element: withSuspense(WorkflowsPage),
          },
          {
            path: 'new',
            element: withSuspense(SmartWorkflowBuilder),
          },
          {
            path: ':id',
            element: withSuspense(WorkflowDesigner),
          },
          {
            path: ':id/edit',
            element: withSuspense(WorkflowDesigner),
          },
        ],
      },

      // Query - SQL Workspace
      {
        path: 'query',
        element: withSuspense(SqlWorkspacePage),
      },

      // Trust - Governance & Compliance
      {
        path: 'trust',
        element: withSuspense(TrustCenter),
      },

      // Insights - Unified Analytics
      {
        path: 'insights',
        element: withSuspense(InsightsPage),
      },

      // Alerts - operator-facing alert triage console (restored as canonical route)
      {
        path: 'alerts',
        element: isAlertsSurfaceEnabled() ? withSuspense(AlertsPage) : withSuspense(NotFound),
      },

      // Operations Console - Operator-facing diagnostics and tools
      {
        path: 'operations',
        element: withSuspense(OperationsConsole),
      },
      {
        path: 'operations/jobs',
        element: withSuspense(OperationsJobCenterPage),
      },

      // AEP Integration Pages
      // Event Explorer — real-time AEP event stream explorer (restored as canonical route)
      {
        path: 'events',
        element: withSuspense(EventExplorerPage),
      },
      // Memory Plane Viewer — restored as canonical route
      {
        path: 'memory',
        element: isMemorySurfaceEnabled() ? withSuspense(MemoryPlaneViewerPage) : withSuspense(NotFound),
      },
      // Entity Browser — restored as canonical route
      {
        path: 'entities',
        element: isEntityBrowserSurfaceEnabled() ? withSuspense(EntityBrowserPage) : withSuspense(NotFound),
      },
      // Context Explorer — restored as canonical route
      {
        path: 'context',
        element: isContextSurfaceEnabled() ? withSuspense(ContextExplorerPage) : withSuspense(NotFound),
      },
      // Data Fabric — restored as canonical operator-facing route
      {
        path: 'fabric',
        element: isFabricSurfaceEnabled() ? withSuspense(DataFabricPage) : withSuspense(NotFound),
      },
      // Agent Catalog — restored as canonical operator-facing route
      {
        path: 'agents',
        element: isAgentCatalogSurfaceEnabled() ? withSuspense(AgentPluginManagerPage) : withSuspense(NotFound),
      },

      // Settings
      {
        path: 'settings',
        element: isSettingsSurfaceEnabled() ? withSuspense(SettingsPage) : withSuspense(NotFound),
      },

      // Plugins
      {
        path: 'plugins',
        children: [
          {
            index: true,
            element: withSuspense(PluginsPage),
          },
          {
            path: ':id',
            element: withSuspense(PluginDetailsPage),
          },
        ],
      },

      // Connectors - Data source connector management
      {
        path: 'connectors',
        element: <LazyLoadErrorBoundary><React.Suspense fallback={<PageLoader />}><DataConnectorsPageWrapper /></React.Suspense></LazyLoadErrorBoundary>,
      },

      // =========================================
      // COMPATIBILITY ROUTES (Deep-link Continuity)
      // =========================================

      { path: 'dashboard', element: withSuspense(IntelligentHub) },
      { path: 'hub', element: withSuspense(IntelligentHub) },
      { path: 'collections', element: withSuspense(DataExplorer) },
      { path: 'collections/new', element: withSuspense(CreateCollectionPage) },
      { path: 'collections/:id', element: withSuspense(DataExplorer) },
      { path: 'collections/:id/edit', element: withSuspense(EditCollectionPage) },
      { path: 'datasets', element: withSuspense(DataExplorer) },
      { path: 'lineage', element: <Navigate to="/data?view=lineage" replace /> },
      { path: 'quality', element: <Navigate to="/data?view=quality" replace /> },
      { path: 'workflows', element: withSuspense(WorkflowsPage) },
      { path: 'workflows/new', element: withSuspense(SmartWorkflowBuilder) },
      { path: 'workflows/:id', element: withSuspense(WorkflowDesigner) },
      { path: 'sql', element: withSuspense(SqlWorkspacePage) },
      { path: 'governance', element: withSuspense(TrustCenter) },
      { path: 'brain', element: withSuspense(InsightsPage) },
      { path: 'dashboards', element: withSuspense(InsightsPage) },
      { path: 'cost', element: withSuspense(InsightsPage) },

      // 404
      {
        path: '*',
        element: withSuspense(NotFound),
      },
    ],
  },
];

export default routes;
