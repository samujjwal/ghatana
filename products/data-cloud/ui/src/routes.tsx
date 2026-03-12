/**
 * React Router v7 Route Configuration
 *
 * Simplified route structure following the improvement plan:
 * - Unified Data Explorer (replaces Collections, Datasets, Lineage, Quality)
 * - Unified Pipeline Center (replaces Workflows)
 * - Unified Insights (replaces Dashboards, Brain, Cost)
 * - Trust Center (replaces Governance)
 *
 * @doc.type config
 * @doc.purpose Application routing configuration
 * @doc.layer frontend
 */

import React from 'react';
import type { RouteObject } from 'react-router';
import { DefaultLayout } from './layouts/DefaultLayout';

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
const DataFabricPage = React.lazy(() =>
  import('./pages/DataFabricPage').then((m) => ({ default: m.DataFabricPage }))
);
const AgentPluginManagerPage = React.lazy(() =>
  import('./pages/AgentPluginManagerPage').then((m) => ({ default: m.AgentPluginManagerPage }))
);

// Legacy pages (kept for backward compatibility)
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
    const startTime = Date.now();
    if (import.meta.env.DEV) {
      console.log('[PageLoader] Suspense fallback triggered');
    }

    const timer = setTimeout(() => {
      const loadTime = Date.now() - startTime;
      if (import.meta.env.DEV) {
        console.warn(
          `[PageLoader] Still loading after ${loadTime}ms - check for:`,
          '\n  - Large component bundles',
          '\n  - Circular dependencies',
          '\n  - Heavy synchronous imports',
          '\n  - Network issues (if loading chunks over network)'
        );
      }
    }, 3000); // Reduced to 3s for faster feedback

    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="flex items-center justify-center w-full h-64">
      <div className="text-center">
        <div className="inline-block">
          <div className="w-8 h-8 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
        </div>
        <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">Loading...</p>
      </div>
    </div>
  );
}

/**
 * Error Boundary for lazy loaded components
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
    // Use try-catch to prevent error boundary from failing
    try {
      console.error('[LazyLoadErrorBoundary] Caught error:', error);
      console.error('[LazyLoadErrorBoundary] Error name:', error?.name);
      console.error('[LazyLoadErrorBoundary] Error message:', error?.message);
      console.error('[LazyLoadErrorBoundary] Error stack:', error?.stack);
    } catch (e) {
      console.error('[LazyLoadErrorBoundary] Failed to log error:', e);
    }
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    try {
      console.error('[LazyLoadErrorBoundary] Component stack:', errorInfo?.componentStack);
    } catch (e) {
      console.error('[LazyLoadErrorBoundary] Failed to log component stack:', e);
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '100%',
          minHeight: '16rem',
          padding: '1.5rem'
        }}>
          <div style={{ textAlign: 'center', maxWidth: '28rem' }}>
            <h2 style={{
              fontSize: '1.25rem',
              fontWeight: '600',
              color: '#dc2626',
              marginBottom: '0.5rem'
            }}>
              Failed to load page
            </h2>
            <p style={{
              color: '#6b7280',
              marginBottom: '1rem',
              fontSize: '0.875rem'
            }}>
              {this.state.error?.message || 'Unknown error occurred while loading the component'}
            </p>
            <button
              onClick={() => window.location.reload()}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: '#2563eb',
                color: 'white',
                borderRadius: '0.5rem',
                border: 'none',
                cursor: 'pointer',
                fontSize: '0.875rem'
              }}
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
            path: ':id',
            element: withSuspense(DataExplorer),
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

      // Alerts
      {
        path: 'alerts',
        element: withSuspense(AlertsPage),
      },

      // AEP Integration Pages
      // Event Explorer — browse/tail events across four-tier fabric
      {
        path: 'events',
        element: withSuspense(EventExplorerPage),
      },
      // Memory Plane Viewer — browse agent memory items by type
      {
        path: 'memory',
        element: withSuspense(MemoryPlaneViewerPage),
      },
      // Entity Browser — inspects entities with schema
      {
        path: 'entities',
        element: withSuspense(EntityBrowserPage),
      },
      // Data Fabric — four-tier topology canvas using @ghatana/flow-canvas
      {
        path: 'fabric',
        element: withSuspense(DataFabricPage),
      },
      // Agent Registry — manage and monitor registered agents
      {
        path: 'agents',
        element: withSuspense(AgentPluginManagerPage),
      },

      // Settings
      {
        path: 'settings',
        element: withSuspense(SettingsPage),
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

      // =========================================
      // LEGACY ROUTES (Backward Compatibility)
      // =========================================

      { path: 'dashboard', element: withSuspense(IntelligentHub) },
      { path: 'hub', element: withSuspense(IntelligentHub) },
      { path: 'collections', element: withSuspense(DataExplorer) },
      { path: 'collections/new', element: withSuspense(CreateCollectionPage) },
      { path: 'collections/:id', element: withSuspense(DataExplorer) },
      { path: 'collections/:id/edit', element: withSuspense(EditCollectionPage) },
      { path: 'datasets', element: withSuspense(DataExplorer) },
      { path: 'lineage', element: withSuspense(DataExplorer) },
      { path: 'quality', element: withSuspense(DataExplorer) },
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
