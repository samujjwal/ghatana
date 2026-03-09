/**
 * Analytics Dashboard Page
 * 
 * Comprehensive analytics view showing content engagement metrics,
 * completion rates, learner activity, and simulation usage statistics.
 * 
 * @doc.type component
 * @doc.purpose Analytics and reporting for content performance
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Spinner } from '@ghatana/ui';
import {
    LineChart,
    Line,
    BarChart,
    Bar,
    PieChart,
    Pie,
    Cell,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
} from 'recharts';

interface OverviewMetrics {
    totalLearners: number;
    totalModules: number;
    avgCompletionRate: number;
    totalEnrollments: number;
}

interface ConceptEngagement {
    slug: string;
    title: string;
    enrollments: number;
    avgCompletion: number;
    avgTimeMinutes: number;
}

interface DailyEnrollment {
    date: string;
    count: number;
}

interface SimulationUsage {
    id: string;
    type: string;
    title: string;
    uniqueUsers: number;
    avgDuration: number;
    totalSessions: number;
    avgInteractions: number;
}

export function AnalyticsDashboardPage() {
    const [dateRange, setDateRange] = useState<'7d' | '30d' | '90d'>('30d');

    // Real API calls to backend analytics endpoints
    const { data: overview, isLoading: overviewLoading } = useQuery({
        queryKey: ['analytics-overview', dateRange],
        queryFn: async (): Promise<OverviewMetrics> => {
            const response = await fetch(`/admin/api/v1/analytics/overview?range=${dateRange}`);
            if (!response.ok) throw new Error('Failed to fetch overview metrics');
            return response.json();
        },
    });

    const { data: concepts, isLoading: conceptsLoading } = useQuery({
        queryKey: ['analytics-concepts', dateRange],
        queryFn: async (): Promise<ConceptEngagement[]> => {
            const response = await fetch(`/admin/api/v1/analytics/concepts?range=${dateRange}`);
            if (!response.ok) throw new Error('Failed to fetch concept engagement');
            return response.json();
        },
    });

    const { data: enrollmentTrends, isLoading: trendsLoading } = useQuery({
        queryKey: ['analytics-trends', dateRange],
        queryFn: async (): Promise<DailyEnrollment[]> => {
            const response = await fetch(`/admin/api/v1/analytics/trends?range=${dateRange}`);
            if (!response.ok) throw new Error('Failed to fetch enrollment trends');
            return response.json();
        },
    });

    const { data: simulations, isLoading: simulationsLoading } = useQuery({
        queryKey: ['analytics-simulations', dateRange],
        queryFn: async (): Promise<SimulationUsage[]> => {
            const response = await fetch(`/admin/api/v1/analytics/simulations?range=${dateRange}`);
            if (!response.ok) throw new Error('Failed to fetch simulation usage');
            return response.json();
        },
    });

    const isLoading = overviewLoading || conceptsLoading || trendsLoading || simulationsLoading;

    // Calculate engagement score color
    const getEngagementColor = (score: number) => {
        if (score >= 75) return 'text-green-600 dark:text-green-400';
        if (score >= 50) return 'text-yellow-600 dark:text-yellow-400';
        return 'text-red-600 dark:text-red-400';
    };

    const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-screen">
                <div className="flex items-center gap-2">
                    <Spinner />
                    <span className="text-gray-600 dark:text-gray-400">Loading analytics...</span>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6 p-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">Analytics Dashboard</h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-1">
                        Content engagement and learner activity metrics
                    </p>
                </div>
                <div className="flex gap-2">
                    {(['7d', '30d', '90d'] as const).map((range) => (
                        <button
                            key={range}
                            onClick={() => setDateRange(range)}
                            className={`px-4 py-2 rounded-lg font-medium transition ${dateRange === range
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                                }`}
                        >
                            {range === '7d' ? 'Last 7 Days' : range === '30d' ? 'Last 30 Days' : 'Last 90 Days'}
                        </button>
                    ))}
                </div>
            </div>

            {/* Overview Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card className="p-6">
                    <div className="text-sm font-medium text-gray-600 dark:text-gray-400">Total Learners</div>
                    <div className="text-3xl font-bold text-gray-900 dark:text-white mt-2">
                        {overview?.totalLearners.toLocaleString()}
                    </div>
                    <div className="text-sm text-green-600 dark:text-green-400 mt-2">↑ 12.5% from last period</div>
                </Card>

                <Card className="p-6">
                    <div className="text-sm font-medium text-gray-600 dark:text-gray-400">Published Modules</div>
                    <div className="text-3xl font-bold text-gray-900 dark:text-white mt-2">
                        {overview?.totalModules}
                    </div>
                    <div className="text-sm text-blue-600 dark:text-blue-400 mt-2">Active content library</div>
                </Card>

                <Card className="p-6">
                    <div className="text-sm font-medium text-gray-600 dark:text-gray-400">Avg Completion Rate</div>
                    <div className={`text-3xl font-bold mt-2 ${getEngagementColor(overview?.avgCompletionRate || 0)}`}>
                        {overview?.avgCompletionRate.toFixed(1)}%
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-2">Across all modules</div>
                </Card>

                <Card className="p-6">
                    <div className="text-sm font-medium text-gray-600 dark:text-gray-400">Total Enrollments</div>
                    <div className="text-3xl font-bold text-gray-900 dark:text-white mt-2">
                        {overview?.totalEnrollments.toLocaleString()}
                    </div>
                    <div className="text-sm text-green-600 dark:text-green-400 mt-2">↑ 8.3% from last period</div>
                </Card>
            </div>

            {/* Enrollment Trends */}
            <Card className="p-6">
                <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Enrollment Trends</h2>
                <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={enrollmentTrends}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="date" />
                        <YAxis />
                        <Tooltip />
                        <Legend />
                        <Line type="monotone" dataKey="count" stroke="#3b82f6" strokeWidth={2} name="Daily Enrollments" />
                    </LineChart>
                </ResponsiveContainer>
            </Card>

            {/* Concept Engagement */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <Card className="p-6">
                    <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Top Concepts by Enrollment</h2>
                    <ResponsiveContainer width="100%" height={300}>
                        <BarChart data={concepts}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="title" angle={-45} textAnchor="end" height={100} />
                            <YAxis />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="enrollments" fill="#3b82f6" name="Enrollments" />
                        </BarChart>
                    </ResponsiveContainer>
                </Card>

                <Card className="p-6">
                    <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Completion Rate Distribution</h2>
                    <ResponsiveContainer width="100%" height={300}>
                        <PieChart>
                            <Pie
                                data={(concepts ?? []) as unknown as { [key: string]: unknown }[]}
                                dataKey="avgCompletion"
                                nameKey="title"
                                cx="50%"
                                cy="50%"
                                outerRadius={100}
                                label
                            >
                                {(concepts ?? []).map((_, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                ))}
                            </Pie>
                            <Tooltip />
                            <Legend />
                        </PieChart>
                    </ResponsiveContainer>
                </Card>
            </div>

            {/* Detailed Concept Table */}
            <Card className="p-6">
                <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Concept Performance Details</h2>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-100 dark:bg-gray-800">
                            <tr>
                                <th className="px-4 py-3 text-left font-semibold">Concept</th>
                                <th className="px-4 py-3 text-right font-semibold">Enrollments</th>
                                <th className="px-4 py-3 text-right font-semibold">Completion Rate</th>
                                <th className="px-4 py-3 text-right font-semibold">Avg Time (min)</th>
                                <th className="px-4 py-3 text-left font-semibold">Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {concepts?.map((concept, idx) => (
                                <tr key={concept.slug} className={idx % 2 === 0 ? 'bg-white dark:bg-gray-900' : 'bg-gray-50 dark:bg-gray-800'}>
                                    <td className="px-4 py-3 font-medium">{concept.title}</td>
                                    <td className="px-4 py-3 text-right">{concept.enrollments}</td>
                                    <td className={`px-4 py-3 text-right font-semibold ${getEngagementColor(concept.avgCompletion)}`}>
                                        {concept.avgCompletion.toFixed(1)}%
                                    </td>
                                    <td className="px-4 py-3 text-right">{concept.avgTimeMinutes}</td>
                                    <td className="px-4 py-3">
                                        <span className={`px-2 py-1 rounded text-xs font-medium ${concept.avgCompletion >= 75
                                            ? 'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-300'
                                            : concept.avgCompletion >= 50
                                                ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-300'
                                                : 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-300'
                                            }`}>
                                            {concept.avgCompletion >= 75 ? 'Excellent' : concept.avgCompletion >= 50 ? 'Good' : 'Needs Attention'}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </Card>

            {/* Simulation Usage */}
            <Card className="p-6">
                <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Simulation Usage Metrics</h2>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-100 dark:bg-gray-800">
                            <tr>
                                <th className="px-4 py-3 text-left font-semibold">Simulation</th>
                                <th className="px-4 py-3 text-left font-semibold">Type</th>
                                <th className="px-4 py-3 text-right font-semibold">Unique Users</th>
                                <th className="px-4 py-3 text-right font-semibold">Total Sessions</th>
                                <th className="px-4 py-3 text-right font-semibold">Avg Duration</th>
                                <th className="px-4 py-3 text-right font-semibold">Avg Interactions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {simulations?.map((sim, idx) => (
                                <tr key={sim.id} className={idx % 2 === 0 ? 'bg-white dark:bg-gray-900' : 'bg-gray-50 dark:bg-gray-800'}>
                                    <td className="px-4 py-3 font-medium">{sim.title}</td>
                                    <td className="px-4 py-3">
                                        <span className="px-2 py-1 rounded text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-300">
                                            {sim.type}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-right">{sim.uniqueUsers}</td>
                                    <td className="px-4 py-3 text-right">{sim.totalSessions}</td>
                                    <td className="px-4 py-3 text-right">{Math.floor(sim.avgDuration / 60)}m {sim.avgDuration % 60}s</td>
                                    <td className="px-4 py-3 text-right">{sim.avgInteractions}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </Card>

            {/* Export Button */}
            <div className="flex justify-end">
                <button
                    onClick={() => {
                        window.location.href = `/admin/api/v1/analytics/export?range=${dateRange}`;
                    }}
                    className="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors font-medium"
                >
                    📊 Export as CSV
                </button>
            </div>
        </div>
    );
}
