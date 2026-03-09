import { useState } from "react";
import { Box, Card } from "@/components/ui";
import { useRecommendPath, useEnrollInPath, usePathEnrollments } from "../../hooks/usePathways";
import { cardStyles, textStyles, buttonStyles, badgeStyles, cn } from "../../theme";

// Local type definitions to avoid contract resolution issues
interface PathNode {
    id: string;
    title: string;
    type: string;
    estimatedMinutes: number;
    order: number;
}

interface LearningPath {
    id: string;
    title: string;
    description: string;
    nodes?: PathNode[];
}

interface NodeProgress {
    nodeId: string;
    status: "not_started" | "in_progress" | "completed";
    completedAt?: string;
}

interface LearningPathEnrollment {
    id: string;
    userId: string;
    pathId: string;
    path?: LearningPath;
    status: "active" | "completed" | "paused";
    currentNodeId?: string;
    nodeProgress?: NodeProgress[];
    enrolledAt: string;
    completedAt?: string;
}

interface PathwayExplorerProps {
    onSelectPath?: (pathId: string) => void;
}

/**
 * Component for exploring and enrolling in learning pathways.
 * Shows personalized recommendations based on user goals.
 */
export function PathwayExplorer({ onSelectPath }: PathwayExplorerProps) {
    const [goals, setGoals] = useState<string[]>([]);
    const [goalInput, setGoalInput] = useState("");
    const [recommendedPath, setRecommendedPath] = useState<LearningPath | null>(null);

    const { data: enrollments } = usePathEnrollments();
    const recommendMutation = useRecommendPath();
    const enrollMutation = useEnrollInPath();

    const handleAddGoal = () => {
        if (goalInput.trim() && !goals.includes(goalInput.trim())) {
            setGoals([...goals, goalInput.trim()]);
            setGoalInput("");
        }
    };

    const handleRemoveGoal = (goal: string) => {
        setGoals(goals.filter((g) => g !== goal));
    };

    const handleGetRecommendation = async () => {
        if (goals.length === 0) return;

        try {
            const path = await recommendMutation.mutateAsync({ goals });
            setRecommendedPath(path);
        } catch (error) {
            console.error("Failed to get recommendation:", error);
        }
    };

    const handleEnroll = async (pathId: string) => {
        try {
            await enrollMutation.mutateAsync(pathId);
            onSelectPath?.(pathId);
        } catch (error) {
            console.error("Failed to enroll:", error);
        }
    };

    return (
        <Box className="space-y-8">
            {/* Goal Input Section */}
            <Card className={cn(cardStyles.base, "p-6")}>
                <h2 className={cn(textStyles.h2, "mb-4")}>
                    What do you want to learn?
                </h2>
                <p className={cn(textStyles.body, "mb-4")}>
                    Add your learning goals and we'll create a personalized pathway for you.
                </p>

                <div className="flex gap-2 mb-4">
                    <input
                        type="text"
                        value={goalInput}
                        onChange={(e) => setGoalInput(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleAddGoal()}
                        placeholder="e.g., Learn machine learning basics"
                        className={cn(
                            "flex-1",
                            "px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        )}
                    />
                    <button
                        onClick={handleAddGoal}
                        className={cn(buttonStyles.primary)}
                    >
                        Add
                    </button>
                </div>

                {goals.length > 0 && (
                    <div className="flex flex-wrap gap-2 mb-4">
                        {goals.map((goal) => (
                            <span
                                key={goal}
                                className={cn(
                                    badgeStyles.info,
                                    "inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm"
                                )}
                            >
                                {goal}
                                <button
                                    onClick={() => handleRemoveGoal(goal)}
                                    className="ml-1 text-blue-600 hover:text-blue-800"
                                >
                                    ×
                                </button>
                            </span>
                        ))}
                    </div>
                )}

                <button
                    onClick={handleGetRecommendation}
                    disabled={goals.length === 0 || recommendMutation.isPending}
                    className={cn(
                        "w-full py-3 bg-gradient-to-r from-purple-600 to-blue-600 text-white font-medium rounded-lg",
                        "hover:from-purple-700 hover:to-blue-700 disabled:opacity-50"
                    )}
                >
                    {recommendMutation.isPending ? "Generating..." : "Get Personalized Pathway"}
                </button>
            </Card>

            {/* Recommended Path */}
            {recommendedPath && (
                <Card className={cn(cardStyles.base, "p-6")}>
                    <div className="flex justify-between items-start mb-4">
                        <div>
                            <h3 className={textStyles.h3}>{recommendedPath.title}</h3>
                            <p className={textStyles.muted}>{recommendedPath.description}</p>
                        </div>
                        <button
                            onClick={() => handleEnroll(recommendedPath.id)}
                            disabled={enrollMutation.isPending}
                            className={cn(buttonStyles.success)}
                        >
                            {enrollMutation.isPending ? "Enrolling..." : "Start Learning"}
                        </button>
                    </div>

                    <div className="mt-4">
                        <h4 className={cn(textStyles.h4, "mb-2")}>Learning Path:</h4>
                        <div className="space-y-2">
                            {recommendedPath.nodes?.map((node: PathNode, index: number) => (
                                <div
                                    key={node.id}
                                    className="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-800 rounded-lg"
                                >
                                    <span className="w-8 h-8 flex items-center justify-center bg-blue-100 text-blue-800 rounded-full font-medium">
                                        {index + 1}
                                    </span>
                                    <div>
                                        <p className={textStyles.body}>{node.title}</p>
                                        <p className={cn(textStyles.small, "text-gray-600 dark:text-gray-400")}>
                                            {node.estimatedMinutes} min • {node.type}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </Card>
            )}

            {/* Current Enrollments */}
            {enrollments?.enrollments && enrollments.enrollments.length > 0 && (
                <Card className={cn(cardStyles.base, "p-6")}>
                    <h3 className={cn(textStyles.h3, "mb-4")}>Your Learning Paths</h3>
                    <div className="space-y-4">
                        {enrollments.enrollments.map((enrollment) => (
                            <PathEnrollmentCard
                                key={enrollment.id}
                                enrollment={enrollment}
                                onClick={() => onSelectPath?.(enrollment.pathId)}
                            />
                        ))}
                    </div>
                </Card>
            )}
        </Box>
    );
}

interface PathEnrollmentCardProps {
    enrollment: LearningPathEnrollment;
    onClick?: () => void;
}

function PathEnrollmentCard({ enrollment, onClick }: PathEnrollmentCardProps) {
    const completedNodes = enrollment.nodeProgress?.filter(
        (n: NodeProgress) => n.status === "completed"
    ).length ?? 0;
    const totalNodes = enrollment.nodeProgress?.length ?? 0;
    const progressPercent = totalNodes > 0 ? (completedNodes / totalNodes) * 100 : 0;

    return (
        <div
            onClick={onClick}
            className={cn(
                cardStyles.interactive,
                "p-4 cursor-pointer hover:border-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
            )}
        >
            <div className="flex justify-between items-center mb-2">
                <h4 className={textStyles.h4}>{enrollment.path?.title ?? "Learning Path"}</h4>
                <span className={cn(textStyles.small, "text-gray-600 dark:text-gray-400")}>
                    {completedNodes}/{totalNodes} modules
                </span>
            </div>
            <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                <div
                    className="bg-blue-600 h-2 rounded-full transition-all"
                    style={{ width: `${progressPercent}%` }}
                />
            </div>
        </div>
    );
}
