/**
 * Lazy-loaded heavy route components.
 *
 * Import these instead of the direct module paths in route files to
 * enable React's code-splitting via dynamic `import()`. Combined with
 * the Vite `manualChunks` strategy in vite.config.ts, these ensure
 * heavy feature modules are only downloaded when the user first navigates
 * to the corresponding route.
 *
 * Usage:
 *   import { LazyCanvas, LazyAIPanel } from '@/routes/lazyRoutes';
 *   // Wrap with <Suspense fallback={<PageSkeleton />}> at the route level.
 *
 * @doc.type module
 * @doc.purpose Lazy-loaded route component exports for code splitting
 * @doc.layer routes
 * @doc.pattern LazyLoading
 */

import { lazy } from 'react';

// ---------------------------------------------------------------------------
// Heavy canvas sub-features
// ---------------------------------------------------------------------------

/**
 * AI assistance panel rendered inside the canvas view.
 * Chunk: app-canvas (see vite.config.ts manualChunks).
 */
export const LazyAIPanel = lazy(
  () => import('./app/project/_canvas/CanvasAIPanel'),
);

/**
 * Canvas collaboration banner (live cursors, avatars).
 * Deferred so the main canvas shell renders immediately.
 */
export const LazyCollaborationBanner = lazy(
  () => import('./app/project/_canvas/CanvasCollaborationBanner'),
);

/**
 * Node context menu (only needed on right-click).
 */
export const LazyNodeContextMenu = lazy(
  () => import('./app/project/_canvas/CanvasNodeContextMenu'),
);

/**
 * Outline and layers panel (large component tree).
 */
export const LazyOutlinePanel = lazy(
  () => import('./app/project/_canvas/CanvasOutlinePanel'),
);

// ---------------------------------------------------------------------------
// Heavy project sub-features
// ---------------------------------------------------------------------------

/**
 * AI Explainability panel (approval detail view).
 * Chunk: app-project.
 */
export const LazyAIExplainability = lazy(
  () =>
    import('../components/approval/AIExplainabilityPanel').then((m) => ({
      default: m.AIExplainabilityPanel,
    })),
);

/**
 * Observability dashboard.
 * Chunk: app-settings / app-project.
 */
export const LazyObservabilityDashboard = lazy(
  () =>
    import('../components/observability/ObservabilityDashboard').then((m) => ({
      default: m.ObservabilityDashboard,
    })),
);

/**
 * Bulk operations dialog (heavy mutation surface).
 */
export const LazyBulkOperationsDialog = lazy(
  () =>
    import('../components/project/BulkOperationsDialog').then((m) => ({
      default: m.BulkOperationsDialog,
    })),
);

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

/**
 * Workspace member management page.
 */
export const LazyWorkspaceMembers = lazy(
  () => import('./app/workspaces'),
);

// ---------------------------------------------------------------------------
// Export map type (for documentation / discovery)
// ---------------------------------------------------------------------------

export type LazyRouteKey =
  | 'LazyAIPanel'
  | 'LazyCollaborationBanner'
  | 'LazyNodeContextMenu'
  | 'LazyOutlinePanel'
  | 'LazyAIExplainability'
  | 'LazyObservabilityDashboard'
  | 'LazyBulkOperationsDialog'
  | 'LazyWorkspaceMembers';
