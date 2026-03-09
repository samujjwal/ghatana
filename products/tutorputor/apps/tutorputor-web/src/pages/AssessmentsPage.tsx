import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Box, Card, Text, Button, Badge, Spinner } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";

interface AssessmentListItem {
    id: string;
    title: string;
    status: string;
    itemCount: number;
    timeLimitMinutes?: number;
}

/**
 * Page for viewing and taking assessments.
 * 
 * @doc.type component
 * @doc.purpose Display assessment list and allow students to take assessments
 * @doc.layer product
 * @doc.pattern Page
 */
export function AssessmentsPage() {
    const navigate = useNavigate();
    const [filter, setFilter] = useState<"all" | "pending" | "completed">("all");

    const { data, isLoading, error } = useQuery({
        queryKey: ["assessments", filter],
        queryFn: async (): Promise<{ items: AssessmentListItem[] }> => {
            // Placeholder - assessments endpoint to be implemented on apiClient
            return { items: [] };
        }
    });

    if (isLoading) {
        return (
            <Box className="flex items-center justify-center min-h-[400px]">
                <Spinner size="lg" />
            </Box>
        );
    }

    if (error) {
        return (
            <Box className="p-6">
                <Text className="text-red-600">Failed to load assessments. Please try again.</Text>
            </Box>
        );
    }

    const assessments = data?.items ?? [];

    return (
        <Box className="p-6">
            <Box className="max-w-4xl mx-auto">
                <PageHeader
                    title="Assessments"
                    description="View and take assessments"
                    actions={
                        <Box className="flex gap-2">
                            {(["all", "pending", "completed"] as const).map((f) => (
                                <Button
                                    key={f}
                                    variant={filter === f ? "solid" : "outline"}
                                    tone="primary"
                                    size="sm"
                                    onClick={() => setFilter(f)}
                                >
                                    {f.charAt(0).toUpperCase() + f.slice(1)}
                                </Button>
                            ))}
                        </Box>
                    }
                />

                {assessments.length === 0 ? (
                    <Card className="p-8 text-center">
                        <Text className="text-gray-500 dark:text-gray-300">No assessments found.</Text>
                    </Card>
                ) : (
                    <Box className="space-y-4">
                        {assessments.map((assessment: AssessmentListItem) => (
                            <Card
                                key={assessment.id}
                                className="p-4 hover:shadow-md transition-shadow cursor-pointer"
                                onClick={() => navigate(`/assessments/${assessment.id}`)}
                            >
                                <Box className="flex items-start justify-between">
                                    <Box>
                                        <Text className="font-semibold text-gray-900">
                                            {assessment.title}
                                        </Text>
                                        <Text className="text-sm text-gray-500 mt-1">
                                            {assessment.itemCount} questions • {assessment.timeLimitMinutes ?? "No"} time limit
                                        </Text>
                                    </Box>
                                    <Badge
                                        variant="soft"
                                        tone={
                                            assessment.status === "GRADED"
                                                ? "success"
                                                : assessment.status === "ACTIVE"
                                                    ? "warning"
                                                    : "neutral"
                                        }
                                    >
                                        {assessment.status}
                                    </Badge>
                                </Box>
                            </Card>
                        ))}
                    </Box>
                )}
            </Box>
        </Box>
    );
}
