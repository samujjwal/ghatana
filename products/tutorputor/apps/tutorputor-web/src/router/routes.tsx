import { Navigate, createBrowserRouter } from "react-router-dom";
import { AppLayout } from "../components/AppLayout";
import { LoginRoutePage } from "../pages/LoginRoutePage";

async function loadNamedComponent<TModule extends Record<string, unknown>>(
  loader: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  const module = await loader();
  return { Component: module[exportName] as React.ComponentType };
}

async function loadDefaultComponent<TModule extends { default: React.ComponentType }>(
  loader: () => Promise<TModule>,
) {
  const module = await loader();
  return { Component: module.default };
}

/**
 * Canonical learner route map.
 *
 * The web app owns learner consumption and teacher-side classroom surfaces.
 * Admin authoring remains canonical in the separate tutorputor-admin app.
 * Legacy aliases stay only where they preserve existing deep links.
 */
export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginRoutePage />,
  },
  {
    path: "/",
    element: <AppLayout />,
    children: [
      // === LEARN SECTION ===
      {
        index: true,
        lazy: async () =>
          loadNamedComponent(() => import("../pages/DashboardPage"), "DashboardPage"),
      },
      {
        path: "dashboard",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/DashboardPage"), "DashboardPage"),
      },
      {
        path: "home",
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: "modules",
        element: <Navigate to="/search" replace />,
      },
      {
        path: "modules/:slug",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/ModulePage"), "ModulePage"),
      },
      {
        path: "pathways",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/PathwaysPage"), "PathwaysPage"),
      },
      {
        path: "learning-paths",
        element: <Navigate to="/pathways" replace />,
      },

      // === PRACTICE SECTION ===
      {
        path: "assessments",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/AssessmentsPage"), "AssessmentsPage"),
      },
      {
        path: "assessments/:assessmentId",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/AssessmentDetailPage"), "AssessmentDetailPage"),
      },
      {
        path: "assessment-list",
        element: <Navigate to="/assessments" replace />,
      },

      // === EXPLORE SECTION ===
      {
        path: "search",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/SearchResultsPage"), "SearchResultsPage"),
      },
      {
        path: "content-explore",
        element: <Navigate to="/search" replace />,
      },
      {
        path: "content-explorer",
        element: <Navigate to="/search" replace />,
      },
      {
        path: "marketplace",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/MarketplacePage"), "MarketplacePage"),
      },

      // === CONNECT SECTION ===
      {
        path: "collaboration",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/CollaborationPage"), "CollaborationPage"),
      },

      // === PROFILE SECTION ===
      {
        path: "analytics",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/AnalyticsPage"), "AnalyticsPage"),
      },
      {
        path: "teacher",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/TeacherPage"), "TeacherPage"),
      },
      {
        path: "settings",
        lazy: async () => loadDefaultComponent(() => import("../pages/Settings")),
      },
      {
        path: "settings/privacy",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/PrivacySettingsPage"), "PrivacySettingsPage"),
      },

      // === LEARNER FLOW ===
      {
        path: "learn/:simulationId",
        lazy: async () =>
          loadNamedComponent(() => import("../pages/LearnerFlowPage"), "LearnerFlowPage"),
      },

      // === AI TUTOR COMPATIBILITY ALIAS ===
      {
        path: "ai-tutor",
        element: <Navigate to="/dashboard" replace />,
      },

      // === SIMULATIONS ===
      {
        path: "simulations",
        lazy: async () => loadDefaultComponent(() => import("../pages/SimulationList")),
      },
      {
        path: "simulations/studio/:id?",
        lazy: async () => {
          const module = await import("../pages/SimulationStudio");
          return { Component: module.default };
        },
      },
    ],
  },
]);
