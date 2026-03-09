/**
 * Admin App Router Configuration
 *
 * NAVIGATION CONSOLIDATION (11 → 5):
 * - /authoring - Unified Content Studio + AI Kernel + Dashboard (merged)
 * - /analytics - Performance metrics + Dashboards + Reports (merged)
 * - /users - User management (unchanged)
 * - /settings - All settings including SSO, Compliance, Audit (merged)
 *
 * Legacy routes redirect to new consolidated paths.
 *
 * CODE SPLITTING:
 * - All page components are lazy loaded for optimal bundle size
 * - Each route loads only when navigated to
 *
 * @doc.type router
 * @doc.purpose Consolidated admin navigation with code splitting
 * @doc.layer product
 */

import React from "react";
import { lazy, Suspense, ComponentType } from "react";
import { Navigate, createBrowserRouter, redirect } from "react-router-dom";
import { AdminLayout } from "./components/layout/AdminLayout";
import { Skeleton } from "@ghatana/ui";
import { useAuth } from "./hooks/useAuth";

// Lazy load all page components for code splitting
const AuthoringPage = lazy(() =>
  import("./pages/AuthoringPage").then((m) => ({ default: m.AuthoringPage })),
);
const UsersPage = lazy(() =>
  import("./pages/UsersPage").then((m) => ({ default: m.UsersPage })),
);
const SsoConfigPage = lazy(() =>
  import("./pages/SsoConfigPage").then((m) => ({ default: m.SsoConfigPage })),
);
const AuditPage = lazy(() =>
  import("./pages/AuditPage").then((m) => ({ default: m.AuditPage })),
);
const AnalyticsPage = lazy(() =>
  import("./pages/AnalyticsPage").then((m) => ({ default: m.AnalyticsPage })),
);
const AnalyticsDashboardPage = lazy(() =>
  import("./pages/AnalyticsDashboardPage").then((m) => ({
    default: m.AnalyticsDashboardPage,
  })),
);
const CompliancePage = lazy(() =>
  import("./pages/CompliancePage").then((m) => ({ default: m.CompliancePage })),
);
const SettingsPage = lazy(() =>
  import("./pages/SettingsPage").then((m) => ({ default: m.SettingsPage })),
);
const MarketplaceAdminPage = lazy(() =>
  import("./pages/MarketplaceAdminPage").then((m) => ({
    default: m.MarketplaceAdminPage,
  })),
);
const ConceptManagementPage = lazy(() =>
  import("./pages/ConceptManagementPage").then((m) => ({
    default: m.ConceptManagementPage,
  })),
);
const ExamplesGallery = lazy(() =>
  import("./pages/ExamplesGallery").then((m) => ({
    default: m.ExamplesGallery,
  })),
);

// Loading fallback component for lazy-loaded pages
function PageLoadingFallback() {
  return (
    <div className="p-6 space-y-6">
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-4 w-96" />
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Skeleton className="h-32 rounded-lg" />
        <Skeleton className="h-32 rounded-lg" />
        <Skeleton className="h-32 rounded-lg" />
        <Skeleton className="h-32 rounded-lg" />
      </div>
      <Skeleton className="h-64 rounded-lg" />
    </div>
  );
}

// Wrapper to add Suspense to lazy components
function withSuspense(Component: ComponentType) {
  return (
    <Suspense fallback={<PageLoadingFallback />}>
      <Component />
    </Suspense>
  );
}

function AppShell() {
  const { isAuthenticated, isAdmin, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    );
  }

  // Allow bypass in development mode for easier testing
  const isDevelopment = import.meta.env.DEV;
  const allowBypass =
    isDevelopment &&
    typeof window !== "undefined" &&
    !window.location.search.includes("?requireAuth");

  if (!isAuthenticated && !allowBypass) {
    // Redirect to main app login
    window.location.href = "/login?redirect=/admin";
    return null;
  }

  if (!isAdmin && !allowBypass) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900">Access Denied</h1>
          <p className="mt-2 text-gray-600">
            You don't have permission to access the admin console.
          </p>
          <a
            href="/"
            className="mt-4 inline-block text-primary-600 hover:underline"
          >
            Return to Dashboard
          </a>
        </div>
      </div>
    );
  }

  return <AdminLayout />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      // Default route → Authoring (unified canvas)
      {
        index: true,
        loader: async () => redirect("/authoring"),
      },

      // === PRIMARY ROUTES (Consolidated 5-item navigation) ===

      // 1. AUTHORING - Unified content creation canvas
      {
        path: "authoring",
        element: withSuspense(AuthoringPage),
      },
      {
        path: "authoring/new",
        element: withSuspense(AuthoringPage),
      },
      {
        path: "authoring/:id",
        element: withSuspense(AuthoringPage),
      },

      // 2. ANALYTICS - All metrics and reports
      {
        path: "analytics",
        element: withSuspense(AnalyticsDashboardPage),
      },
      {
        path: "analytics/overview",
        element: withSuspense(AnalyticsPage),
      },
      {
        path: "analytics/reports",
        element: withSuspense(AnalyticsPage),
      },

      // 3. USERS - User management
      {
        path: "users",
        element: withSuspense(UsersPage),
      },

      // 4. SETTINGS - Consolidated settings (SSO, Audit, Compliance, Marketplace)
      {
        path: "settings",
        element: withSuspense(SettingsPage),
      },
      {
        path: "settings/sso",
        element: withSuspense(SsoConfigPage),
      },
      {
        path: "settings/audit",
        element: withSuspense(AuditPage),
      },
      {
        path: "settings/compliance",
        element: withSuspense(CompliancePage),
      },
      {
        path: "settings/marketplace",
        element: withSuspense(MarketplaceAdminPage),
      },

      // === LEGACY ROUTE REDIRECTS (for backwards compatibility) ===

      // Dashboard → Authoring
      {
        path: "dashboard",
        loader: async () => redirect("/authoring"),
      },

      // Content Studio → Authoring
      {
        path: "content-studio",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "content-studio/create",
        loader: async () => redirect("/authoring/new"),
      },
      {
        path: "content-studio/edit/:id",
        loader: async ({ params }) => redirect(`/authoring/${params.id}`),
      },
      {
        path: "content-studio/validate/:id",
        loader: async ({ params }) => redirect(`/authoring/${params.id}`),
      },

      // AI Kernel → Authoring (AI is now embedded, not separate)
      {
        path: "ai-kernel",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "ai-kernel/editor",
        loader: async () => redirect("/authoring/new"),
      },

      // Old content routes → Authoring
      {
        path: "content",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "content/*",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "content-creation",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "learning-hub",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "learning-hub/new",
        loader: async () => redirect("/authoring/new"),
      },
      {
        path: "learning-hub/:id",
        loader: async ({ params }) => redirect(`/authoring/${params.id}`),
      },

      // Settings-related legacy routes → Settings
      {
        path: "sso",
        loader: async () => redirect("/settings/sso"),
      },
      {
        path: "audit",
        loader: async () => redirect("/settings/audit"),
      },
      {
        path: "compliance",
        loader: async () => redirect("/settings/compliance"),
      },
      {
        path: "marketplace",
        loader: async () => redirect("/settings/marketplace"),
      },

      // Simulation Builder → Authoring
      {
        path: "simulation-builder",
        loader: async () => redirect("/authoring"),
      },
      {
        path: "simulation-builder/*",
        loader: async () => redirect("/authoring"),
      },

      // Assets → Authoring
      {
        path: "assets",
        loader: async () => redirect("/authoring"),
      },

      // === UTILITY ROUTES (kept for specific features) ===
      {
        path: "concepts",
        element: withSuspense(ConceptManagementPage),
      },
      {
        path: "examples",
        element: withSuspense(ExamplesGallery),
      },

      // Catch-all → Authoring
      {
        path: "*",
        element: <Navigate to="/authoring" replace />,
      },
    ],
  },
]);
