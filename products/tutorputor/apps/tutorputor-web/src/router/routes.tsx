import { Navigate, createBrowserRouter } from "react-router-dom";
import { DashboardPage } from "../pages/DashboardPage";
import { ModulePage } from "../pages/ModulePage";
import { PathwaysPage } from "../pages/PathwaysPage";
import { SearchResultsPage } from "../pages/SearchResultsPage";
import { AssessmentsPage } from "../pages/AssessmentsPage";
import { AssessmentDetailPage } from "../pages/AssessmentDetailPage";
import { AnalyticsPage } from "../pages/AnalyticsPage";
import { MarketplacePage } from "../pages/MarketplacePage";
import { CollaborationPage } from "../pages/CollaborationPage";
import { TeacherPage } from "../pages/TeacherPage";
import SettingsPage from "../pages/Settings";
import SimulationListPage from "../pages/SimulationList";
import { AppLayout } from "../components/AppLayout";
import { LoginRoutePage } from "../pages/LoginRoutePage";
import { canonicalLearnerRoutes } from "./canonicalRouteMap";

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
        element: <DashboardPage />,
      },
      {
        path: "dashboard",
        element: <DashboardPage />,
      },
      {
        path: "modules",
        element: <Navigate to="/search" replace />,
      },
      {
        path: "modules/:slug",
        element: <ModulePage />,
      },
      {
        path: "pathways",
        element: <PathwaysPage />,
      },

      // === PRACTICE SECTION ===
      {
        path: "assessments",
        element: <AssessmentsPage />,
      },
      {
        path: "assessments/:assessmentId",
        element: <AssessmentDetailPage />,
      },

      // === EXPLORE SECTION ===
      {
        path: "search",
        element: <SearchResultsPage />,
      },
      {
        path: "marketplace",
        element: <MarketplacePage />,
      },

      // === CONNECT SECTION ===
      {
        path: "collaboration",
        element: <CollaborationPage />,
      },

      // === PROFILE SECTION ===
      {
        path: "analytics",
        element: <AnalyticsPage />,
      },
      {
        path: "teacher",
        element: <TeacherPage />,
      },
      {
        path: "settings",
        element: <SettingsPage />,
      },

      // === AI TUTOR COMPATIBILITY ALIAS ===
      {
        path: "ai-tutor",
        element: <Navigate to="/dashboard" replace />,
      },

      // === SIMULATIONS ===
      {
        path: "simulations",
        element: <SimulationListPage />,
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
