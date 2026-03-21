import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import { ExplorerLayout } from "@/components/layout/ExplorerLayout";
import { Loader2 } from "lucide-react";

const ExplorePage = lazy(() =>
  import("./pages/ExplorePage").then((m) => ({ default: m.ExplorePage })),
);
const GeneratePage = lazy(() =>
  import("./pages/GeneratePage").then((m) => ({ default: m.GeneratePage })),
);
const ViewerPage = lazy(() =>
  import("./pages/ViewerPage").then((m) => ({ default: m.ViewerPage })),
);
const QualityPage = lazy(() =>
  import("./pages/QualityPage").then((m) => ({ default: m.QualityPage })),
);
const AnalyticsPage = lazy(() =>
  import("./pages/AnalyticsPage").then((m) => ({ default: m.AnalyticsPage })),
);
const AnimationEditorPage = lazy(() =>
  import("./pages/AnimationEditorPage").then((m) => ({ default: m.AnimationEditorPage })),
);
const SimulationAuthoringPage = lazy(() =>
  import("./pages/SimulationAuthoringPage").then((m) => ({ default: m.SimulationAuthoringPage })),
);
const EvidenceAnalyticsPage = lazy(() =>
  import("./pages/EvidenceAnalyticsPage").then((m) => ({ default: m.EvidenceAnalyticsPage })),
);

const TemplatesPage = lazy(() =>
  import("./pages/TemplatesPage").then((m) => ({ default: m.TemplatesPage })),
);

function PageLoader() {
  return (
    <div className="flex flex-1 items-center justify-center">
      <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
    </div>
  );
}

function Wrap({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

export const router = createBrowserRouter([
  { path: "/", element: <Navigate to="/explore" replace /> },
  {
    element: <ExplorerLayout />,
    children: [
      { path: "/explore", element: <Wrap><ExplorePage /></Wrap> },
      { path: "/generate", element: <Wrap><GeneratePage /></Wrap> },
      { path: "/view/:id", element: <Wrap><ViewerPage /></Wrap> },
      { path: "/quality", element: <Wrap><QualityPage /></Wrap> },
      { path: "/analytics", element: <Wrap><AnalyticsPage /></Wrap> },
      { path: "/animate", element: <Wrap><AnimationEditorPage /></Wrap> },
      { path: "/simulate", element: <Wrap><SimulationAuthoringPage /></Wrap> },
      { path: "/evidence", element: <Wrap><EvidenceAnalyticsPage /></Wrap> },
      { path: "/templates", element: <Wrap><TemplatesPage /></Wrap> },
    ],
  },
]);
