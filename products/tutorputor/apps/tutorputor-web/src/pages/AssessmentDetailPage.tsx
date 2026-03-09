import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Box, Card, Text, Button, Spinner, Progress } from "@/components/ui";

interface Choice {
    id: string;
    text: string;
}

interface AssessmentItem {
    id: string;
    stem: string;
    type: string;
    choices?: Choice[];
}

interface Assessment {
    id: string;
    title: string;
    description?: string;
    timeLimitMinutes?: number;
    items: AssessmentItem[];
}

interface AttemptResponse {
    id: string;
}

/**
 * Page for taking a specific assessment.
 * 
 * @doc.type component
 * @doc.purpose Allow students to take an assessment and view results
 * @doc.layer product
 * @doc.pattern Page
 */
export function AssessmentDetailPage() {
    const { assessmentId } = useParams<{ assessmentId: string }>();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [currentItemIndex, setCurrentItemIndex] = useState(0);
    const [responses, setResponses] = useState<Record<string, { type: string; selectedChoiceIds?: string[]; answer?: string }>>({});
    const [attemptId, setAttemptId] = useState<string | null>(null);

    const { data: assessment, isLoading } = useQuery<Assessment>({
        queryKey: ["assessment", assessmentId],
        queryFn: async (): Promise<Assessment> => {
            // Placeholder - getAssessment to be implemented on apiClient
            return { id: assessmentId!, title: "", items: [] };
        },
        enabled: !!assessmentId
    });

    const startAttemptMutation = useMutation<AttemptResponse, Error>({
        mutationFn: async (): Promise<AttemptResponse> => {
            // Placeholder - startAssessmentAttempt to be implemented on apiClient
            return { id: `attempt-${Date.now()}` };
        },
        onSuccess: (data) => {
            setAttemptId(data.id);
        }
    });

    const submitAttemptMutation = useMutation({
        mutationFn: async () => {
            // Placeholder - submitAssessmentAttempt to be implemented on apiClient
            return {};
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["assessments"] });
            navigate(`/assessments/${assessmentId}/results`);
        }
    });

    if (isLoading) {
        return (
            <Box className="flex items-center justify-center min-h-[400px]">
                <Spinner size="lg" />
            </Box>
        );
    }

    if (!assessment) {
        return (
            <Box className="p-6">
                <Text className="text-red-600">Assessment not found.</Text>
            </Box>
        );
    }

    const items = assessment.items ?? [];
    const currentItem = items[currentItemIndex];
    const progress = items.length > 0 ? ((currentItemIndex + 1) / items.length) * 100 : 0;

    // If no attempt started, show start screen
    if (!attemptId) {
        return (
            <Box className="p-6">
                <Box className="max-w-2xl mx-auto">
                    <Card className="p-8">
                        <Text as="h1" className="text-2xl font-bold text-gray-900 mb-4">
                            {assessment.title}
                        </Text>
                        <Text className="text-gray-600 mb-6">
                            {assessment.description ?? "Complete this assessment to test your knowledge."}
                        </Text>
                        <Box className="space-y-2 mb-6">
                            <Text className="text-sm text-gray-500">
                                <strong>Questions:</strong> {items.length}
                            </Text>
                            {assessment.timeLimitMinutes && (
                                <Text className="text-sm text-gray-500">
                                    <strong>Time Limit:</strong> {assessment.timeLimitMinutes} minutes
                                </Text>
                            )}
                        </Box>
                        <Button
                            onClick={() => startAttemptMutation.mutate()}
                            disabled={startAttemptMutation.isPending}
                        >
                            {startAttemptMutation.isPending ? "Starting..." : "Start Assessment"}
                        </Button>
                    </Card>
                </Box>
            </Box>
        );
    }

    // Assessment in progress
    return (
        <Box className="p-6">
            <Box className="max-w-3xl mx-auto">
                <Box className="mb-6">
                    <Box className="flex justify-between items-center mb-2">
                        <Text className="text-sm text-gray-500">
                            Question {currentItemIndex + 1} of {items.length}
                        </Text>
                        <Text className="text-sm text-gray-500">
                            {Math.round(progress)}% complete
                        </Text>
                    </Box>
                    <Progress value={progress} />
                </Box>

                <Card className="p-6 mb-6">
                    {currentItem && (
                        <>
                            <Text className="text-lg font-medium text-gray-900 mb-4">
                                {currentItem.stem}
                            </Text>

                            {currentItem.type === "multiple_choice_single" && currentItem.choices && (
                                <Box className="space-y-3">
                                    {currentItem.choices.map((choice: Choice) => (
                                        <Box
                                            key={choice.id}
                                            className={`p-3 border rounded-lg cursor-pointer transition-colors ${responses[currentItem.id]?.selectedChoiceIds?.includes(choice.id)
                                                ? "border-blue-500 bg-blue-50"
                                                : "border-gray-200 hover:border-gray-300"
                                                }`}
                                            onClick={() => {
                                                setResponses({
                                                    ...responses,
                                                    [currentItem.id]: {
                                                        type: "multiple_choice",
                                                        selectedChoiceIds: [choice.id]
                                                    }
                                                });
                                            }}
                                        >
                                            <Text>{choice.text}</Text>
                                        </Box>
                                    ))}
                                </Box>
                            )}

                            {currentItem.type === "short_answer" && (
                                <textarea
                                    className="w-full p-3 border border-gray-200 rounded-lg"
                                    rows={4}
                                    placeholder="Type your answer here..."
                                    value={responses[currentItem.id]?.answer ?? ""}
                                    onChange={(e) => {
                                        setResponses({
                                            ...responses,
                                            [currentItem.id]: {
                                                type: "short_answer",
                                                answer: e.target.value
                                            }
                                        });
                                    }}
                                />
                            )}
                        </>
                    )}
                </Card>

                <Box className="flex justify-between">
                    <Button
                        variant="outline"
                        onClick={() => setCurrentItemIndex(Math.max(0, currentItemIndex - 1))}
                        disabled={currentItemIndex === 0}
                    >
                        Previous
                    </Button>

                    {currentItemIndex < items.length - 1 ? (
                        <Button
                            onClick={() => setCurrentItemIndex(currentItemIndex + 1)}
                        >
                            Next
                        </Button>
                    ) : (
                        <Button
                            onClick={() => submitAttemptMutation.mutate()}
                            disabled={submitAttemptMutation.isPending}
                        >
                            {submitAttemptMutation.isPending ? "Submitting..." : "Submit Assessment"}
                        </Button>
                    )}
                </Box>
            </Box>
        </Box>
    );
}
