import { useNavigate } from "react-router-dom";
import { Box } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";
import { StatCard } from "../components/StatCard";
import { useActivePathway, useGeneratePathway, useAdvancePathway } from "../hooks/usePathways";
import { PathwayExplorer } from "../components/pathways";
import { GamificationProgress } from "../components/gamification";

/**
 * Page for exploring and managing personalized learning pathways.
 * Aligned with adaptive learning model: diagnostic → learner profile → prerequisite graph → pathway → mastery updates → remediation → next best lesson
 */
export function PathwaysPage() {
  const navigate = useNavigate();
  const { data: activePathway, isLoading } = useActivePathway();
  const generatePathway = useGeneratePathway();
  const advancePathway = useAdvancePathway();

  const handleGeneratePath = (goal: string) => {
    generatePathway.mutate({ goal });
  };

  const handleAdvancePath = (completedModuleId: string) => {
    advancePathway.mutate({ completedModuleId });
  };

  const handleSelectModule = (moduleId: string) => {
    navigate(`/modules/${moduleId}`);
  };

  if (isLoading) {
    return (
      <Box className="p-6">
        <div className="max-w-6xl mx-auto">
          <div className="text-center py-12">Loading pathways...</div>
        </div>
      </Box>
    );
  }

  return (
    <Box className="p-6">
      <div className="max-w-6xl mx-auto">
        <PageHeader
          title="Learning Pathways"
          description="Get personalized learning paths based on your goals and mastery evidence"
        />

        {activePathway ? (
          <div className="space-y-6">
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <h3 className="font-semibold text-blue-900 mb-2">Active Pathway</h3>
              <p className="text-blue-700 text-sm mb-4">{activePathway.title || "Custom Learning Path"}</p>
              {activePathway.description && (
                <p className="text-blue-600 text-xs">{activePathway.description}</p>
              )}
            </div>

            <PathwayExplorer
              pathway={activePathway}
              onAdvance={handleAdvancePath}
              onSelectModule={handleSelectModule}
            />
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-600 mb-4">No active pathway found</p>
            <button
              onClick={() => handleGeneratePath("Learn new skills")}
              className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
            >
              Generate New Pathway
            </button>
          </div>
        )}
      </div>
    </Box>
  );
}
