import { useState, useMemo, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router';
import { Box, KpiCard } from "@/components/ui";
import { useAtom } from "jotai";
import { compareEnabledAtom } from "@/state/jotai/atoms";
import { KpiGrid } from "@/shared/components/KpiGrid";
import { InsightCard } from "@/shared/components/InsightCard";
import { TimelineChart } from "@/shared/components/TimelineChart";
import { GlobalFilterBar, DevSecOpsPipelineStrip } from "@/shared/components";
import { useOrgKpis } from "./hooks/useOrgKpis";
import type { OrgKpiItem } from "./hooks/useOrgKpis";
import { useCompleteWorkItem } from "@/hooks/useMyWorkItems";
import { ENGINEER_DEVSECOPS_FLOW, DEVSECOPS_PHASE_LABELS, type DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import { getEngineerPhaseRouteForStory } from '@/lib/devsecops/getEngineerPhaseRouteForStory';

/**
 * Dashboard page - Main KPI visualization and AI insights
 *
 * <p><b>Purpose</b><br>
 * Organization-wide control tower displaying KPI metrics, AI-driven insights,
 * and real-time event timeline. Users can filter by time range, tenant, and
 * enable period-over-period comparisons.
 *
 * <p><b>Features</b><br>
 * - 6 KPI cards (deployments, CFR, lead time, MTTR, security, cost)
 * - AI insights panel with confidence scores and HITL status
 * - Event timeline with scrubber for time navigation
 * - Filters: time range, tenant selector, comparison mode toggle
 * - Real-time data updates via polling
 * - Responsive layout (single column on mobile, 3-column grid on desktop)
 * - Story-based monitoring via storyId query param (Engineer Flow Phase 5)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import Dashboard from '@/features/dashboard/Dashboard';
 * <Route path="/" element={<Dashboard />} />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Organization dashboard with KPI metrics and AI insights
 * @doc.layer product
 * @doc.pattern Page
 */

/**
 * Mock story production metrics
 */
interface StoryProductionMetric {
    label: string;
    value: string | number;
    trend: string;
    status: 'healthy' | 'warning' | 'critical';
}

const STORY_PRODUCTION_METRICS: Record<string, { service: string; metrics: StoryProductionMetric[] }> = {
    'WI-1234': {
        service: 'auth-service',
        metrics: [
            { label: 'Auth Success Rate', value: '99.9%', trend: '+0.1%', status: 'healthy' },
            { label: 'Login Latency (p95)', value: '132ms', trend: '-8%', status: 'healthy' },
            { label: 'Active Sessions', value: '45,234', trend: '+12%', status: 'healthy' },
            { label: 'Error Rate', value: '0.02%', trend: '-0.01%', status: 'healthy' },
        ],
    },
    'WI-1235': {
        service: 'payment-service',
        metrics: [
            { label: 'Payment Success Rate', value: '99.7%', trend: '+0.3%', status: 'healthy' },
            { label: 'Gateway Latency (p95)', value: '285ms', trend: '-15%', status: 'healthy' },
            { label: 'Transactions/min', value: '1,245', trend: '+8%', status: 'healthy' },
            { label: 'Timeout Rate', value: '0.05%', trend: '-90%', status: 'healthy' },
        ],
    },
    'WI-1236': {
        service: 'notification-service',
        metrics: [
            { label: 'Delivery Success', value: '98.5%', trend: '+1.2%', status: 'healthy' },
            { label: 'WebSocket Connections', value: '23,456', trend: '+45%', status: 'healthy' },
            { label: 'Push Success Rate', value: '97.8%', trend: '+2.1%', status: 'healthy' },
            { label: 'Avg Delivery Time', value: '180ms', trend: '-22%', status: 'healthy' },
        ],
    },
    'WI-1237': {
        service: 'product-service',
        metrics: [
            { label: 'Search Latency (p95)', value: '245ms', trend: '-52%', status: 'healthy' },
            { label: 'Cache Hit Rate', value: '92%', trend: '+15%', status: 'healthy' },
            { label: 'Queries/sec', value: '3,456', trend: '+28%', status: 'healthy' },
            { label: 'Error Rate', value: '0.01%', trend: '-0.05%', status: 'healthy' },
        ],
    },
    'WI-1238': {
        service: 'api-gateway',
        metrics: [
            { label: 'Requests Blocked', value: '1,234', trend: '+45%', status: 'healthy' },
            { label: 'Gateway Latency (p95)', value: '38ms', trend: '-5%', status: 'healthy' },
            { label: 'Rate Limit Hits', value: '2.3%', trend: '-0.5%', status: 'healthy' },
            { label: 'Abuse Prevention', value: '99.8%', trend: '+0.3%', status: 'healthy' },
        ],
    },
    // WI-1239: Has issues (not ready for prod)
    'WI-1239': {
        service: 'graphql-gateway',
        metrics: [
            { label: 'Subscription Success', value: '92%', trend: '-3%', status: 'warning' },
            { label: 'Message Latency', value: '380ms', trend: '+25%', status: 'warning' },
            { label: 'Active Subscriptions', value: '1,234', trend: '+120%', status: 'healthy' },
            { label: 'Error Rate', value: '2.1%', trend: '+1.5%', status: 'critical' },
        ],
    },
    // WI-1240: Minor warnings but acceptable
    'WI-1240': {
        service: 'audit-service',
        metrics: [
            { label: 'Log Events/sec', value: '3,456', trend: '+28%', status: 'healthy' },
            { label: 'Processing Latency', value: '320ms', trend: '+15%', status: 'warning' },
            { label: 'Storage Growth', value: '2.1GB/day', trend: '+8%', status: 'healthy' },
            { label: 'Compliance Score', value: '98%', trend: '+2%', status: 'healthy' },
        ],
    },
};

export function Dashboard() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const storyId = searchParams.get('storyId');

    const [compareEnabled] = useAtom(compareEnabledAtom);
    const [isMarkingDone, setIsMarkingDone] = useState(false);
    const [storyMarkedDone, setStoryMarkedDone] = useState(false);
    const completeWorkItem = useCompleteWorkItem();

    const operateStep = ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === 'engineer-operate');
    const operatePhaseLabel = operateStep ? DEVSECOPS_PHASE_LABELS[operateStep.phaseId] : DEVSECOPS_PHASE_LABELS.operate;

    // Get production metrics for the story
    const storyData = useMemo(() => {
        if (!storyId) return null;
        return STORY_PRODUCTION_METRICS[storyId] || null;
    }, [storyId]);

    // Check if metrics are stable (all healthy)
    const metricsStable = useMemo(() => {
        if (!storyData) return false;
        return storyData.metrics.every(m => m.status === 'healthy');
    }, [storyData]);

    const handleMarkDone = async () => {
        if (!storyId) return;
        setIsMarkingDone(true);
        try {
            await completeWorkItem.mutateAsync(storyId);
            setStoryMarkedDone(true);
        } finally {
            setIsMarkingDone(false);
            setTimeout(() => {
                navigate('/persona-dashboard');
            }, 2000);
        }
    };

    const { data: kpis, isLoading, error } = useOrgKpis();

    // Use client-side rendering for timeline to avoid hydration mismatch
    const [isClient, setIsClient] = useState(false);

    useEffect(() => {
        setIsClient(true);
    }, []);

    // Mock timeline events with static timestamps to avoid hydration mismatch
    const timelineEvents = useMemo(() => {
        if (!isClient) return [];
        const now = Math.floor(Date.now() / 1000);
        return [
            { timestamp: now - 3600, type: "deploy" as const },
            { timestamp: now - 1800, type: "test" as const },
            { timestamp: now - 900, type: "feature" as const },
            { timestamp: now, type: "incident" as const },
        ];
    }, [isClient]);

    // Mock insights
    const insights = [
        {
            id: "insight-1",
            title: "QA Pipeline Analysis",
            insight: "15% flakiness detected in auth tests",
            confidence: 0.92,
            reasoning: "Based on 1000 test runs in last 7 days. Root cause: race condition in token validation.",
            status: "pending" as const,
        },
        {
            id: "insight-2",
            title: "Deployment Optimization",
            insight: "Deploy time increased 18% vs last week",
            confidence: 0.87,
            reasoning: "Infrastructure scaling needed. Recommend auto-scaling policy adjustment.",
            status: "pending" as const,
        },
        {
            id: "insight-3",
            title: "Security Posture",
            insight: "0 critical vulnerabilities - all systems healthy",
            confidence: 0.99,
            reasoning: "Last scan completed 2h ago. No new issues detected.",
            status: "approved" as const,
        },
    ];

    const handleApproveInsight = (id: string) => {
        console.log("Approved insight:", id);
    };

    const handleDeferInsight = (id: string) => {
        console.log("Deferred insight:", id);
    };

    const handleRejectInsight = (id: string) => {
        console.log("Rejected insight:", id);
    };

    const handlePhaseClick = (phaseId: DevSecOpsPhaseId) => {
        const targetRoute = getEngineerPhaseRouteForStory(phaseId, storyId || undefined);
        if (!targetRoute) return;
        navigate(targetRoute);
    };

    return (
        <Box className="space-y-8 pb-8">
            {/* Story Monitoring Banner (when storyId is present) */}
            {storyId && (
                <div className="bg-purple-50 dark:bg-violet-600/30 border border-purple-200 dark:border-purple-800 rounded-lg p-4">
                    {storyMarkedDone ? (
                        <div className="text-center py-4">
                            <div className="text-4xl mb-2">🎉</div>
                            <h2 className="text-xl font-semibold text-purple-900 dark:text-purple-100">
                                Story {storyId} marked as Done!
                            </h2>
                            <p className="text-purple-700 dark:text-purple-300 mt-1">
                                Redirecting to dashboard...
                            </p>
                        </div>
                    ) : (
                        <>
                            <div className="flex items-center justify-between mb-4">
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="text-lg">📊</span>
                                        <h2 className="font-semibold text-purple-900 dark:text-purple-100">
                                            Production Monitoring: {storyId}
                                        </h2>
                                    </div>
                                    <p className="text-sm text-purple-700 dark:text-purple-300 mt-1">
                                        {storyData
                                            ? `Monitoring ${storyData.service} metrics after deployment.`
                                            : 'No metrics available for this story.'}
                                    </p>
                                    <p className="text-xs text-purple-700 dark:text-purple-300 mt-1">
                                        Phase: {operatePhaseLabel}
                                    </p>
                                </div>
                                <div className="flex items-center gap-3">
                                    <Link
                                        to={`/models?action=deploy&storyId=${storyId}`}
                                        className="text-sm text-purple-600 dark:text-violet-400 hover:underline"
                                    >
                                        ← Back to Deploy
                                    </Link>
                                    {metricsStable && (
                                        <button
                                            onClick={handleMarkDone}
                                            disabled={isMarkingDone}
                                            className="px-4 py-2 bg-green-600 hover:bg-green-700 disabled:bg-green-400 text-white rounded-lg font-medium transition flex items-center gap-2"
                                        >
                                            {isMarkingDone ? (
                                                <>
                                                    <span className="animate-spin">⏳</span>
                                                    Marking...
                                                </>
                                            ) : (
                                                <>
                                                    <span>✅</span>
                                                    Mark story as Done
                                                </>
                                            )}
                                        </button>
                                    )}
                                </div>
                            </div>
                            <div className="mt-3">
                                <DevSecOpsPipelineStrip
                                    phases={ENGINEER_DEVSECOPS_FLOW.phases}
                                    currentPhaseId={operateStep?.phaseId ?? 'operate'}
                                    onPhaseClick={handlePhaseClick}
                                />
                            </div>

                            {/* Story Metrics */}
                            {storyData && (
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
                                    {storyData.metrics.map((metric, idx) => (
                                        <div
                                            key={idx}
                                            className="bg-white dark:bg-neutral-800 rounded-lg border border-purple-100 dark:border-neutral-600 p-3"
                                        >
                                            <div className="text-xs text-slate-500 dark:text-neutral-400 mb-1">
                                                {metric.label}
                                            </div>
                                            <div className="flex items-baseline gap-2">
                                                <span className="text-xl font-bold text-slate-900 dark:text-neutral-100">
                                                    {metric.value}
                                                </span>
                                                <span className={`text-sm ${metric.trend.startsWith('+')
                                                    ? 'text-green-600 dark:text-green-400'
                                                    : metric.trend.startsWith('-')
                                                        ? 'text-red-600 dark:text-rose-400'
                                                        : 'text-slate-500'
                                                    }`}>
                                                    {metric.trend}
                                                </span>
                                            </div>
                                            <div className="mt-1">
                                                <span className={`text-xs px-2 py-0.5 rounded-full ${metric.status === 'healthy'
                                                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                                                    : metric.status === 'warning'
                                                        ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
                                                        : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-rose-400'
                                                    }`}>
                                                    {metric.status === 'healthy' ? '✅' : metric.status === 'warning' ? '⚠️' : '❌'} {metric.status}
                                                </span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}

                            {/* Monitoring Status */}
                            <div className="mt-4 p-3 bg-white dark:bg-neutral-800 rounded-lg border border-purple-100 dark:border-neutral-600">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <span className={`w-2 h-2 rounded-full ${metricsStable ? 'bg-green-500 animate-pulse' : 'bg-yellow-500'}`}></span>
                                        <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                            {metricsStable
                                                ? 'All metrics stable - ready to close story'
                                                : 'Monitoring metrics...'}
                                        </span>
                                    </div>
                                    <span className="text-xs text-slate-500 dark:text-neutral-400">
                                        Last updated: just now
                                    </span>
                                </div>
                            </div>
                        </>
                    )}
                </div>
            )}

            {/* Page Header */}
            <div className="flex flex-col md:flex-row md:justify-between md:items-center gap-4">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        Control Tower
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Organization-wide metrics and AI insights
                    </p>
                </div>
            </div>

            {/* Filters Bar */}
            <GlobalFilterBar />

            {/* Main Content Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* KPI Cards Column */}
                <div className="lg:col-span-2 space-y-6">
                    <div>
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Key Metrics
                        </h2>
                        {isLoading ? (
                            <div className="space-y-4">
                                {Array(6)
                                    .fill(0)
                                    .map((_, i) => (
                                        <div
                                            key={i}
                                            className="h-24 bg-slate-200 dark:bg-neutral-800 rounded-lg animate-pulse"
                                        />
                                    ))}
                            </div>
                        ) : error ? (
                            <div className="p-4 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-200">
                                Failed to load KPI data. Please try again.
                            </div>
                        ) : (
                            <KpiGrid>
                                {(Array.isArray(kpis) ? (kpis as OrgKpiItem[]) : []).map((kpi, idx: number) => (
                                    <KpiCard
                                        key={idx}
                                        title={kpi.title}
                                        value={kpi.value}
                                        subtitle={kpi.unit}
                                        trend={kpi.trend?.direction}
                                    />
                                ))}
                            </KpiGrid>
                        )}
                    </div>

                    {/* Timeline */}
                    <div>
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Event Timeline
                        </h2>
                        <div className="bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <TimelineChart
                                events={timelineEvents}
                                onTimeChange={(time) => console.log("Time changed:", time)}
                            />
                        </div>
                    </div>
                </div>

                {/* Insights Sidebar */}
                <div className="space-y-4">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        AI Insights
                    </h2>
                    <div className="space-y-4">
                        {insights.map((insight) => (
                            <InsightCard
                                key={insight.id}
                                title={insight.title}
                                insight={insight.insight}
                                confidence={insight.confidence}
                                reasoning={insight.reasoning}
                                status={insight.status}
                                onApprove={() => handleApproveInsight(insight.id)}
                                onDefer={() => handleDeferInsight(insight.id)}
                                onReject={() => handleRejectInsight(insight.id)}
                            />
                        ))}
                    </div>
                </div>
            </div>

            {/* Comparison Mode Info */}
            {compareEnabled && (
                <div className="p-4 bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800 rounded-lg text-blue-800 dark:text-blue-200">
                    <p className="font-medium">Comparison Mode Active</p>
                    <p className="text-sm mt-1">
                        Viewing metrics compared to the previous period. Trends show
                        period-over-period changes.
                    </p>
                </div>
            )}
        </Box>
    );
}

export default Dashboard;
