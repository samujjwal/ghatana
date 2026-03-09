/**
 * CMS Modules List Page
 * 
 * Lists all modules for content authors to manage drafts and published content.
 * 
 * @doc.type page
 * @doc.purpose CMS module listing
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Box, Card, Button, Text, Heading } from "@/components/ui";
import { tutorputorClient } from "../api/tutorputorClient";

/** Module status enum */
type ModuleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Summary of a module for listing purposes */
interface ModuleSummary {
    id: string;
    slug: string;
    title: string;
    domain: "MATH" | "SCIENCE" | "TECH";
    difficulty: "INTRO" | "INTERMEDIATE" | "ADVANCED";
    estimatedTimeMinutes: number;
    tags: string[];
    status: ModuleStatus;
    publishedAt?: string;
}

const STATUS_TABS: Array<{ value: ModuleStatus | "all"; label: string }> = [
    { value: "all", label: "All" },
    { value: "DRAFT", label: "Drafts" },
    { value: "PUBLISHED", label: "Published" },
    { value: "ARCHIVED", label: "Archived" },
];

export function CMSModulesPage() {
    const navigate = useNavigate();
    const [statusFilter, setStatusFilter] = useState<ModuleStatus | "all">("all");

    const { data, isLoading, error } = useQuery({
        queryKey: ["cms", "modules", statusFilter],
        queryFn: async () => {
            const params = statusFilter !== "all" ? `?status=${statusFilter}` : "";
            const response = await tutorputorClient.get(`/cms/modules${params}`);
            return response.data as { items: ModuleSummary[]; nextCursor: string | null };
        },
    });

    const modules = data?.items || [];

    const getStatusBadge = (status: ModuleStatus) => {
        const styles: Record<ModuleStatus, string> = {
            DRAFT: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
            PUBLISHED: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
            ARCHIVED: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300",
        };
        return (
            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${styles[status]}`}>
                {status}
            </span>
        );
    };

    const getDifficultyBadge = (difficulty: string) => {
        const styles: Record<string, string> = {
            beginner: "text-green-600",
            intermediate: "text-blue-600",
            advanced: "text-purple-600",
            expert: "text-red-600",
        };
        return (
            <span className={`text-sm ${styles[difficulty] || "text-gray-600"}`}>
                {difficulty.charAt(0).toUpperCase() + difficulty.slice(1)}
            </span>
        );
    };

    return (
        <Box>
            {/* Header */}
            <div className="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800">
                <div className="max-w-6xl mx-auto px-6 py-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <Heading level={1} className="text-2xl font-bold text-gray-900 dark:text-white">
                                Content Management
                            </Heading>
                            <Text className="text-gray-500 dark:text-gray-400 mt-1">
                                Create and manage learning modules
                            </Text>
                        </div>
                        <Button onClick={() => navigate("/cms/modules/new")}>
                            + New Module
                        </Button>
                    </div>

                    {/* Status Tabs */}
                    <div className="flex gap-1 mt-6">
                        {STATUS_TABS.map((tab) => (
                            <button
                                key={tab.value}
                                onClick={() => setStatusFilter(tab.value)}
                                className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${statusFilter === tab.value
                                    ? "bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                                    : "text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                                    }`}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Content */}
            <div className="max-w-6xl mx-auto px-6 py-8">
                {isLoading ? (
                    <div className="text-center py-12">
                        <Text className="text-gray-500 dark:text-gray-400">Loading modules...</Text>
                    </div>
                ) : error ? (
                    <Card className="p-8 text-center">
                        <Text className="text-red-600">
                            Error loading modules. Please try again.
                        </Text>
                    </Card>
                ) : modules.length === 0 ? (
                    <Card className="p-12 text-center">
                        <div className="text-5xl mb-4">📚</div>
                        <Heading level={2} className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                            No modules found
                        </Heading>
                        <Text className="text-gray-500 dark:text-gray-400 mb-6">
                            {statusFilter === "all"
                                ? "Get started by creating your first module"
                                : `No ${statusFilter.toLowerCase()} modules yet`}
                        </Text>
                        <Button onClick={() => navigate("/cms/modules/new")}>
                            Create Module
                        </Button>
                    </Card>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {modules.map((module) => (
                            <Card
                                key={module.id}
                                className="p-6 hover:shadow-lg transition-shadow cursor-pointer"
                                onClick={() => navigate(`/cms/modules/${module.id}/edit`)}
                            >
                                <div className="flex items-start justify-between mb-3">
                                    <div className="flex-1">
                                        <Heading level={3} className="text-lg font-semibold text-gray-900 dark:text-white truncate">
                                            {module.title}
                                        </Heading>
                                        <Text className="text-sm text-gray-500 dark:text-gray-400">
                                            {module.slug}
                                        </Text>
                                    </div>
                                    {getStatusBadge(module.status)}
                                </div>

                                <div className="flex items-center gap-4 text-sm text-gray-500 dark:text-gray-400 mb-4">
                                    <span className="flex items-center gap-1">
                                        📖 {module.domain.replace(/_/g, " ")}
                                    </span>
                                    {getDifficultyBadge(module.difficulty)}
                                    <span className="flex items-center gap-1">
                                        ⏱️ {module.estimatedTimeMinutes}m
                                    </span>
                                </div>

                                <div className="flex flex-wrap gap-1">
                                    {module.tags.slice(0, 3).map((tag) => (
                                        <span
                                            key={tag}
                                            className="px-2 py-0.5 bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300 text-xs rounded-full"
                                        >
                                            {tag}
                                        </span>
                                    ))}
                                    {module.tags.length > 3 && (
                                        <span className="px-2 py-0.5 text-gray-400 dark:text-gray-500 text-xs">
                                            +{module.tags.length - 3} more
                                        </span>
                                    )}
                                </div>

                                {module.publishedAt && (
                                    <Text className="text-xs text-gray-400 dark:text-gray-500 mt-3">
                                        Published: {new Date(module.publishedAt).toLocaleDateString()}
                                    </Text>
                                )}
                            </Card>
                        ))}
                    </div>
                )}
            </div>
        </Box>
    );
}

export default CMSModulesPage;
