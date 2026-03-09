import { useNavigate } from "react-router-dom";
import { Box } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";
import { StatCard } from "../components/StatCard";
import { usePathEnrollments } from "../hooks/usePathways";
import { PathwayExplorer } from "../components/pathways";
import { GamificationProgress } from "../components/gamification";

/**
 * Page for exploring and enrolling in personalized learning pathways.
 */
export function PathwaysPage() {
  const navigate = useNavigate();
  const { data: pathEnrollments } = usePathEnrollments();

  const enrollments = pathEnrollments?.enrollments ?? [];
  const totalPaths = enrollments.length;
  const activePaths = enrollments.filter((e) => e.status === "active").length;
  const completedPaths = enrollments.filter((e) => e.status === "completed").length;

  const handleSelectPath = (pathId: string) => {
    // Navigate to the first module in the path or a path detail view
    navigate(`/pathways/${pathId}`);
  };

  return (
    <Box className="p-6">
      <div className="max-w-6xl mx-auto">
        <PageHeader
          title="Learning Pathways"
          description="Get personalized learning paths based on your goals"
        />

        {totalPaths > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            <StatCard title="Active Paths" value={activePaths} color="blue" />
            <StatCard title="Completed Paths" value={completedPaths} color="green" />
            <StatCard title="Total Paths" value={totalPaths} color="purple" />
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2">
            <PathwayExplorer onSelectPath={handleSelectPath} />
          </div>

          <div className="lg:col-span-1">
            <GamificationProgress />
          </div>
        </div>
      </div>
    </Box>
  );
}
