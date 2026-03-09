import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, Card } from "@/components/ui";
import { textStyles, badgeStyles, cn } from "../theme";
import { useModuleBySlug } from "../hooks/useModuleBySlug";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useProgressUpdate } from "../hooks/useProgressUpdate";
import { apiClient } from "../api/tutorputorClient";

// Local type definition
type ModuleId = string;

// Match the content block shape used by the API client
interface ContentBlock {
  id: string;
  blockType: "text" | "video" | "quiz" | "interactive" | "exercise";
  payload?: {
    markdown?: string;
    prompt?: string;
    videoUrl?: string;
    questions?: Array<{
      question: string;
      options: string[];
      correctIndex: number;
    }>;
    [key: string]: unknown;
  };
}

export function ModulePage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useModuleBySlug(slug || "");
  const progressMutation = useProgressUpdate();

  const enrollMutation = useMutation({
    mutationFn: (moduleId: ModuleId) => apiClient.enrollInModule(moduleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["module", slug] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    }
  });

  const handleStartModule = () => {
    if (!data) return;
    if (data.userEnrollment) {
      // Already enrolled, just navigate to content
      return;
    }
    enrollMutation.mutate(data.module.id);
  };

  const handleMarkStepComplete = () => {
    if (!data?.userEnrollment) return;

    const currentProgress = data.userEnrollment.progressPercent;
    const newProgress = Math.min(currentProgress + 10, 100);
    const timeDelta = 60; // 1 minute per step

    progressMutation.mutate({
      enrollmentId: data.userEnrollment.id,
      progressPercent: newProgress,
      timeSpentSecondsDelta: timeDelta
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading module...</div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-red-600">Error loading module</div>
      </div>
    );
  }

  const { module, userEnrollment } = data;
  const hasEnrollment = !!userEnrollment;

  return (
    <Box className="p-6">
      <Button
        variant="link"
        tone="primary"
        size="sm"
        onClick={() => navigate("/dashboard")}
        className="mb-6"
      >
        ← Back to Dashboard
      </Button>

      <Card className="mb-6">
        <div className="p-8">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h1 className={cn(textStyles.h1, "text-3xl mb-2")}>
                {module.title}
              </h1>
              <div className="flex items-center gap-3 text-sm text-gray-600 dark:text-gray-300">
                <span className={badgeStyles.info}>
                  {module.domain}
                </span>
                <span className="capitalize">{module.difficulty}</span>
                <span>•</span>
                <span>{module.estimatedTimeMinutes} minutes</span>
              </div>
            </div>

            <p className={cn(textStyles.body, "mb-6")}>
              {module.description}
            </p>

            {userEnrollment && (
              <div className="mb-6 p-4 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
                <div className="flex justify-between items-center mb-2">
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-200">
                    Your Progress
                  </span>
                  <span className="text-sm font-semibold text-blue-600 dark:text-blue-400">
                    {userEnrollment.progressPercent.toFixed(1)}%
                  </span>
                </div>
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3">
                  <div
                    className="bg-blue-600 h-3 rounded-full transition-all"
                    style={{ width: `${userEnrollment.progressPercent}%` }}
                  />
                </div>
                <div className="text-xs text-gray-600 dark:text-gray-300 mt-2">
                  Status: {userEnrollment.status} • Time spent:{" "}
                  {Math.floor(userEnrollment.timeSpentSeconds / 60)} minutes
                </div>
              </div>
            )}

            <Button
              tone="primary"
              onClick={handleStartModule}
              disabled={enrollMutation.isPending}
              className="px-6 py-3 rounded-lg font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {enrollMutation.isPending
                ? "Enrolling..."
                : hasEnrollment
                  ? "Continue Module"
                  : "Start Module"}
            </Button>
          </div>
        </div>
      </Card>

      <Card className="mb-6">
        <div className="p-8">
          <h2 className={cn(textStyles.h2, "mb-4")}>
            Learning Objectives
          </h2>
          <ul className="space-y-2">
            {module.learningObjectives.map((objective) => (
              <li
                key={objective.id}
                className={cn(textStyles.body, "flex items-start gap-2")}
              >
                <span className="text-blue-600 mt-1">✓</span>
                <span>{objective.label}</span>
                <span className={cn(textStyles.xs, "ml-auto capitalize")}>
                  {objective.taxonomyLevel}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </Card>

      <Card>
        <div className="p-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className={textStyles.h2}>Content</h2>
            {hasEnrollment && (
              <Button
                tone="primary"
                size="sm"
                onClick={handleMarkStepComplete}
                disabled={progressMutation.isPending}
                className="disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {progressMutation.isPending
                  ? "Updating..."
                  : "Mark Step Completed"}
              </Button>
            )}
          </div>
          <div className="space-y-4">
            {module.contentBlocks.map((block, index) => (
              <ContentBlockCard key={block.id} block={block} index={index} />
            ))}
          </div>
        </div>
      </Card>
    </Box>
  );
}

function ContentBlockCard({
  block,
  index,
}: {
  block: ContentBlock;
  index: number;
}) {
  const renderContent = () => {
    switch (block.blockType) {
      case "text":
        return (
          <div className="prose max-w-none">
            <p className="text-gray-700 dark:text-gray-200 whitespace-pre-wrap">
              {block.payload?.markdown || ""}
            </p>
          </div>
        );
      case "exercise":
        return (
          <div className="border-l-4 border-green-500 pl-4">
            <p className="font-medium text-gray-900 dark:text-white mb-2">Exercise</p>
            <p className="text-gray-700 dark:text-gray-200">{block.payload?.prompt || ""}</p>
          </div>
        );
      default:
        return (
          <div className="text-gray-500 dark:text-gray-400 italic">
            {block.blockType} content (not yet rendered)
          </div>
        );
    }
  };

  return (
    <div className="border rounded-lg p-4">
      <div className="text-xs text-gray-500 dark:text-gray-400 mb-2">
        Block {index + 1} • {block.blockType}
      </div>
      {renderContent()}
    </div>
  );
}

