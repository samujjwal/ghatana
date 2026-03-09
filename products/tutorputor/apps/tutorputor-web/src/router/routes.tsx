import { createBrowserRouter } from "react-router-dom";
import { DashboardPage } from "../pages/DashboardPage";
import { ModulePage } from "../pages/ModulePage";
import { PathwaysPage } from "../pages/PathwaysPage";
import { SearchResultsPage } from "../pages/SearchResultsPage";
import { AssessmentsPage } from "../pages/AssessmentsPage";
import { AssessmentDetailPage } from "../pages/AssessmentDetailPage";
import { AnalyticsPage } from "../pages/AnalyticsPage";
import { AITutorPage } from "../pages/AITutorPage";
import { MarketplacePage } from "../pages/MarketplacePage";
import { CollaborationPage } from "../pages/CollaborationPage";
import { AppLayout } from "../components/AppLayout";

/**
 * Student App Routes
 * 
 * Simplified navigation structure based on UI/UX audit:
 * - Learn: Dashboard, Modules, Pathways
 * - Practice: Assessments
 * - Explore: Search, Marketplace
 * - Connect: Collaboration
 * - Profile: Analytics
 * 
 * Legacy routes removed: CMS, Templates, Content Generation demos
 */
export const router = createBrowserRouter([
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

      // === AI TUTOR (Omnipresent) ===
      {
        path: "ai-tutor",
        element: <AITutorPage />,
      },
    ],
  },
]);
