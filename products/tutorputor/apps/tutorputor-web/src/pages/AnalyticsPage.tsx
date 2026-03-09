import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Box, Card, Text, Button, Spinner } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";

interface AnalyticsSummary {
    totalEvents: number;
    activeLearners: number;
    eventsByType: Record<string, number>;
}

interface TrendPeriod {
    periodStart: string;
    eventCount: number;
}

interface UsageTrends {
    periods: TrendPeriod[];
}

interface AtRiskStudent {
    userId: string;
    displayName: string;
    riskLevel: string;
    riskFactors?: string[];
}

/**
 * Analytics dashboard page for teachers and admins.
 * 
 * @doc.type component
 * @doc.purpose Display learning analytics and insights
 * @doc.layer product
 * @doc.pattern Page
 */
export function AnalyticsPage() {
    const [period, setPeriod] = useState<"daily" | "weekly" | "monthly">("weekly");

    const { data: summary, isLoading: summaryLoading } = useQuery<AnalyticsSummary>({
        queryKey: ["analytics", "summary"],
        queryFn: async (): Promise<AnalyticsSummary> => {
            // Placeholder - getAnalyticsSummary to be implemented on apiClient
            return { totalEvents: 0, activeLearners: 0, eventsByType: {} };
        }
    });

    const { data: usageTrends, isLoading: trendsLoading } = useQuery<UsageTrends>({
        queryKey: ["analytics", "usage-trends", period],
        queryFn: async (): Promise<UsageTrends> => {
            // Placeholder - getUsageTrends to be implemented on apiClient
            return { periods: [] };
        }
    });

    const { data: atRisk, isLoading: atRiskLoading } = useQuery<AtRiskStudent[]>({
        queryKey: ["analytics", "at-risk"],
        queryFn: async (): Promise<AtRiskStudent[]> => {
            // Placeholder - getAtRiskStudents to be implemented on apiClient
            return [];
        }
    });

    const isLoading = summaryLoading || trendsLoading || atRiskLoading;

    if (isLoading) {
        return (
            <Box className="flex items-center justify-center min-h-[400px]">
                <Spinner size="lg" />
            </Box>
        );
    }

    return (
        <Box className="p-6">
            <Box className="max-w-7xl mx-auto">
                <PageHeader
                    title="Learning Analytics"
                    description="Learning analytics and insights"
                />

                {/* KPI Cards */}
                <Box className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
                    <Card className="p-4">
                        <Text className="text-sm text-gray-500 dark:text-gray-400">Total Events</Text>
                        <Text className="text-2xl font-bold text-gray-900 dark:text-white">
                            {summary?.totalEvents?.toLocaleString() ?? 0}
                        </Text>
                    </Card>
                    <Card className="p-4">
                        <Text className="text-sm text-gray-500 dark:text-gray-400">Active Learners</Text>
                        <Text className="text-2xl font-bold text-gray-900 dark:text-white">
                            {summary?.activeLearners ?? 0}
                        </Text>
                    </Card>
                    <Card className="p-4">
                        <Text className="text-sm text-gray-500 dark:text-gray-400">Completions</Text>
                        <Text className="text-2xl font-bold text-green-600 dark:text-green-400">
                            {summary?.eventsByType?.module_completed ?? 0}
                        </Text>
                    </Card>
                    <Card className="p-4">
                        <Text className="text-sm text-gray-500 dark:text-gray-400">At-Risk Students</Text>
                        <Text className="text-2xl font-bold text-red-600 dark:text-red-400">
                            {atRisk?.length ?? 0}
                        </Text>
                    </Card>
                </Box>

                {/* Usage Trends */}
                <Card className="p-6 mb-8">
                    <Box className="flex items-center justify-between mb-4">
                        <Text className="text-lg font-semibold text-gray-900 dark:text-white">
                            Usage Trends
                        </Text>
                        <Box className="flex gap-2">
                            {(["daily", "weekly", "monthly"] as const).map((p) => (
                                <Button
                                    key={p}
                                    variant={period === p ? "solid" : "outline"}
                                    tone="primary"
                                    size="sm"
                                    onClick={() => setPeriod(p)}
                                >
                                    {p.charAt(0).toUpperCase() + p.slice(1)}
                                </Button>
                            ))}
                        </Box>
                    </Box>
                    {usageTrends?.periods && usageTrends.periods.length > 0 ? (
                        <Box className="h-[300px] flex items-end gap-1 p-4">
                            {usageTrends.periods.map((p: TrendPeriod, i: number) => (
                                <Box
                                    key={i}
                                    className="bg-blue-500 rounded-t flex-1 min-w-[20px]"
                                    style={{
                                        height: `${Math.min(100, (p.eventCount / Math.max(...usageTrends.periods.map(x => x.eventCount)) * 100))}%`
                                    }}
                                    title={`${p.periodStart}: ${p.eventCount} events`}
                                />
                            ))}
                        </Box>
                    ) : (
                        <Text className="text-gray-500 dark:text-gray-400 text-center py-12">
                            No trend data available yet.
                        </Text>
                    )}
                </Card>

                {/* Event Distribution */}
                <Box className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
                    <Card className="p-6">
                        <Text className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                            Event Distribution
                        </Text>
                        {summary?.eventsByType ? (
                            <Box className="space-y-2">
                                {Object.entries(summary.eventsByType).map(([key, value]) => (
                                    <Box key={key} className="flex items-center gap-2">
                                        <Box className="flex-1">
                                            <Text className="text-sm text-gray-600 dark:text-gray-300">{key.replace(/_/g, " ")}</Text>
                                        </Box>
                                        <Box
                                            className="h-6 bg-blue-500 rounded"
                                            style={{ width: `${Math.min(100, (value as number) / Math.max(...Object.values(summary.eventsByType) as number[]) * 100)}%` }}
                                        />
                                        <Text className="text-sm font-medium w-12 text-right text-gray-900 dark:text-white">{value as number}</Text>
                                    </Box>
                                ))}
                            </Box>
                        ) : (
                            <Text className="text-gray-500 dark:text-gray-400 text-center py-8">
                                No event data available.
                            </Text>
                        )}
                    </Card>

                    <Card className="p-6">
                        <Text className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                            At-Risk Students
                        </Text>
                        {atRisk && atRisk.length > 0 ? (
                            <Box className="space-y-3 max-h-[250px] overflow-y-auto">
                                {atRisk.slice(0, 10).map((student: AtRiskStudent) => (
                                    <Box
                                        key={student.userId}
                                        className="flex items-center justify-between p-2 bg-gray-50 dark:bg-gray-800 rounded"
                                    >
                                        <Box>
                                            <Text className="font-medium">{student.displayName}</Text>
                                            <Text className="text-xs text-gray-500 dark:text-gray-400">
                                                {student.riskFactors?.length ?? 0} risk factors
                                            </Text>
                                        </Box>
                                        <Box
                                            className={`px-2 py-1 rounded text-xs font-medium ${student.riskLevel === "critical"
                                                ? "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-200"
                                                : student.riskLevel === "high"
                                                    ? "bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-200"
                                                    : student.riskLevel === "medium"
                                                        ? "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-200"
                                                        : "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-200"
                                                }`}
                                        >
                                            {student.riskLevel}
                                        </Box>
                                    </Box>
                                ))}
                            </Box>
                        ) : (
                            <Text className="text-gray-500 dark:text-gray-400 text-center py-8">
                                No at-risk students detected.
                            </Text>
                        )}
                    </Card>
                </Box>
            </Box>
        </Box>
    );
}
