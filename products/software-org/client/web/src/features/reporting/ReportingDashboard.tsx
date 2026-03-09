import { useState, useMemo } from "react";
import { useSearchParams, useNavigate, Link } from "react-router";
import { ENGINEER_DEVSECOPS_FLOW, DEVSECOPS_PHASE_LABELS, resolveDevSecOpsRoute, type DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import { DevSecOpsPipelineStrip, AiHintBanner, GlobalFilterBar, ContextualHints } from '@/shared/components';
import { getEngineerPhaseRouteForStory } from '@/lib/devsecops/getEngineerPhaseRouteForStory';

/**
 * Reporting Dashboard
 *
 * <p><b>Purpose</b><br>
 * Comprehensive reporting hub with pre-built report templates, metrics visualization,
 * scheduling, and export capabilities. Provides executive dashboards and operational
 * reports for organization-wide insights and KPI tracking.
 *
 * <p><b>Features</b><br>
 * - Pre-built report templates (Weekly KPIs, Security, Deployment, Team Performance)
 * - Metrics visualization with trend indicators
 * - Report scheduling and automation
 * - Export in multiple formats (PDF, CSV, Excel)
 * - Report history and audit trail
 * - Role-based access control
 * - Story-based staging validation via query params (Engineer Flow Phase 4)
 *
 * <p><b>Mock Data</b><br>
 * All data is currently mocked. Replace with API calls to `/api/v1/reports`
 * using the `apiClient` from `@/services/api/index.ts`.
 *
 * @doc.type component
 * @doc.purpose Comprehensive reporting dashboard
 * @doc.layer product
 * @doc.pattern Page
 */

/**
 * Mock staging metrics for story validation
 */
interface StagingMetric {
    label: string;
    value: string | number;
    status: 'healthy' | 'warning' | 'critical';
    threshold?: string;
}

const STORY_STAGING_METRICS: Record<string, StagingMetric[]> = {
    'WI-1234': [
        { label: 'Auth Success Rate', value: '99.8%', status: 'healthy', threshold: '> 99%' },
        { label: 'Login Latency (p95)', value: '145ms', status: 'healthy', threshold: '< 200ms' },
        { label: 'Token Refresh Errors', value: 0, status: 'healthy', threshold: '< 5/hr' },
        { label: 'Session Timeouts', value: '0.1%', status: 'healthy', threshold: '< 1%' },
    ],
    'WI-1235': [
        { label: 'Payment Success Rate', value: '99.5%', status: 'healthy', threshold: '> 99%' },
        { label: 'Gateway Latency (p95)', value: '320ms', status: 'healthy', threshold: '< 500ms' },
        { label: 'Timeout Errors', value: 2, status: 'healthy', threshold: '< 10/hr' },
        { label: 'Circuit Breaker Trips', value: 0, status: 'healthy', threshold: '< 3/hr' },
    ],
    'WI-1236': [
        { label: 'WebSocket Connection Rate', value: '98.2%', status: 'healthy', threshold: '> 95%' },
        { label: 'Notification Delivery Latency', value: '230ms', status: 'healthy', threshold: '< 500ms' },
        { label: 'Push Notification Success', value: '97.5%', status: 'healthy', threshold: '> 95%' },
        { label: 'Message Queue Depth', value: 12, status: 'healthy', threshold: '< 100' },
    ],
    'WI-1237': [
        { label: 'Search Latency (p95)', value: '380ms', status: 'healthy', threshold: '< 500ms' },
        { label: 'Cache Hit Rate', value: '87%', status: 'healthy', threshold: '> 80%' },
        { label: 'Query Success Rate', value: '99.9%', status: 'healthy', threshold: '> 99%' },
        { label: 'Database Connection Pool', value: '45%', status: 'healthy', threshold: '< 80%' },
    ],
    'WI-1238': [
        { label: 'Rate Limit Hit Rate', value: '2.1%', status: 'healthy', threshold: '< 5%' },
        { label: '429 Responses/min', value: 12, status: 'healthy', threshold: '< 50' },
        { label: 'Gateway Latency (p95)', value: '45ms', status: 'healthy', threshold: '< 100ms' },
        { label: 'Redis Connection Health', value: '100%', status: 'healthy', threshold: '> 99%' },
    ],
    // WI-1239: Not yet in staging (CI is failing)
    'WI-1239': [
        { label: 'Subscription Connect Rate', value: '85%', status: 'warning', threshold: '> 95%' },
        { label: 'Message Latency (p95)', value: '450ms', status: 'warning', threshold: '< 200ms' },
        { label: 'Connection Pool Usage', value: '78%', status: 'healthy', threshold: '< 80%' },
        { label: 'Error Rate', value: '3.2%', status: 'critical', threshold: '< 1%' },
    ],
    // WI-1240: Has warning metrics in staging
    'WI-1240': [
        { label: 'Log Write Rate', value: '2,456/s', status: 'healthy', threshold: '> 1000/s' },
        { label: 'Log Processing Latency', value: '450ms', status: 'warning', threshold: '< 300ms' },
        { label: 'Storage Usage', value: '72%', status: 'warning', threshold: '< 70%' },
        { label: 'Encryption Success', value: '100%', status: 'healthy', threshold: '> 99.9%' },
    ],
};

export function ReportingDashboard() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const view = searchParams.get('view');
    const storyId = searchParams.get('storyId');

    const [selectedReport, setSelectedReport] = useState<string>('r-001');

    const stagingStep = ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === 'engineer-staging');
    const stagingPhaseLabel = stagingStep ? DEVSECOPS_PHASE_LABELS[stagingStep.phaseId] : DEVSECOPS_PHASE_LABELS.staging;
    const stagingNextStep = stagingStep?.nextStepId
        ? ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === stagingStep.nextStepId)
        : undefined;
    const promoteRoute = storyId && stagingNextStep
        ? resolveDevSecOpsRoute(stagingNextStep.route, { storyId })
        : storyId
            ? `/models?action=deploy&storyId=${storyId}`
            : undefined;

    // Get staging metrics for the story
    const stagingMetrics = useMemo(() => {
        if (view !== 'staging' || !storyId) return [];
        return STORY_STAGING_METRICS[storyId] || [];
    }, [view, storyId]);

    // Check if all staging metrics are healthy
    const allMetricsHealthy = useMemo(() => {
        if (stagingMetrics.length === 0) return false;
        return stagingMetrics.every(m => m.status === 'healthy');
    }, [stagingMetrics]);

    const getStatusColor = (status: 'healthy' | 'warning' | 'critical'): string => {
        const colors = {
            healthy: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
            warning: 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/30',
            critical: 'text-red-600 dark:text-rose-400 bg-red-100 dark:bg-red-900/30',
        };
        return colors[status];
    };

    const handlePhaseClick = (phaseId: DevSecOpsPhaseId) => {
        const targetRoute = getEngineerPhaseRouteForStory(phaseId, storyId || undefined);
        if (!targetRoute) return;
        navigate(targetRoute);
    };

    // Mock data: report templates
    const reports = [
        {
            id: 'r-001',
            title: 'Weekly KPIs',
            category: 'Executive',
            updated: '2h ago',
            metrics: [
                { label: 'Deployments', value: 42, trend: '+12%' },
                { label: 'Uptime', value: '99.98%', trend: '+0.02%' },
                { label: 'Incidents', value: 2, trend: '-50%' },
                { label: 'Avg Response Time', value: '245ms', trend: '-18%' },
            ],
        },
        {
            id: 'r-002',
            title: 'Security Findings',
            category: 'Security',
            updated: '1d ago',
            metrics: [
                { label: 'Critical Issues', value: 0, trend: '✓ Clear' },
                { label: 'High Severity', value: 3, trend: '-2' },
                { label: 'Medium Severity', value: 12, trend: '+1' },
                { label: 'CVE Coverage', value: '100%', trend: 'Compliant' },
            ],
        },
        {
            id: 'r-003',
            title: 'Deployment Trends',
            category: 'Engineering',
            updated: '3d ago',
            metrics: [
                { label: 'Avg Deploy Time', value: '8m 32s', trend: '-22%' },
                { label: 'Success Rate', value: '98.7%', trend: '+1.2%' },
                { label: 'Rollback Rate', value: '0.3%', trend: '-0.1%' },
                { label: 'Deploys/Week', value: 156, trend: '+28' },
            ],
        },
        {
            id: 'r-004',
            title: 'Team Performance',
            category: 'Operations',
            updated: '4h ago',
            metrics: [
                { label: 'Avg Resolution Time', value: '2.4h', trend: '-15%' },
                { label: 'MTTR', value: '45m', trend: '-12%' },
                { label: 'On-Call Efficiency', value: '94%', trend: '+3%' },
                { label: 'Issues Resolved', value: 284, trend: '+41' },
            ],
        },
    ];

    const getTrendColor = (trend: string): string => {
        if (trend.startsWith('+')) return 'text-green-600 dark:text-green-400';
        if (trend.startsWith('-')) return 'text-red-600 dark:text-rose-400';
        return 'text-blue-600 dark:text-indigo-400';
    };

    const buildDevSecOpsBoardHref = (
        persona?: 'engineer' | 'lead' | 'sre' | 'security',
        status?: 'blocked' | 'in-review',
    ): string => {
        const params = new URLSearchParams(searchParams);

        // These are specific to the reporting view; they don't apply on the DevSecOps board.
        params.delete('view');
        params.delete('storyId');

        if (persona) {
            params.set('persona', persona);
        }

        if (status) {
            params.set('status', status);
        }

        const query = params.toString();
        return query ? `/devsecops/board?${query}` : '/devsecops/board';
    };

    const selected = reports.find((r) => r.id === selectedReport);

    let aiInsightTitle = '';
    let aiInsightBody = '';
    let aiInsightCtaLabel = '';
    let aiInsightCtaHref: string | null = null;

    if (selected) {
        if (selected.id === 'r-002') {
            const criticalMetric = selected.metrics.find((m) => m.label === 'Critical Issues');
            const criticalValue = typeof criticalMetric?.value === 'number' ? criticalMetric.value : 0;

            if (criticalValue > 0) {
                aiInsightTitle = 'Security risk: critical findings present';
                aiInsightBody = 'AI-style hint: Focus the DevSecOps board on the security persona and blocked work until all critical issues are cleared.';
                aiInsightCtaLabel = 'Open security view in DevSecOps board';
                aiInsightCtaHref = buildDevSecOpsBoardHref('security', 'blocked');
            } else {
                aiInsightTitle = 'Security posture currently clear';
                aiInsightBody = 'AI-style hint: Use the security view in the DevSecOps board and compliance reports to catch regressions early.';
                aiInsightCtaLabel = 'View security items in DevSecOps board';
                aiInsightCtaHref = buildDevSecOpsBoardHref('security');
            }
        } else if (selected.id === 'r-003') {
            aiInsightTitle = 'Deployment throughput and rollback risk';
            aiInsightBody = 'AI-style hint: Keep deployment success high by clearing blocked or in-review stories on the engineer DevSecOps board.';
            aiInsightCtaLabel = 'Open engineer DevSecOps board';
            aiInsightCtaHref = buildDevSecOpsBoardHref('engineer', 'blocked');
        } else if (selected.id === 'r-004') {
            aiInsightTitle = 'Operational performance and MTTR';
            aiInsightBody = 'AI-style hint: Drill into the SRE persona view of the DevSecOps board to see which incidents or follow-ups are still open.';
            aiInsightCtaLabel = 'Open SRE view in DevSecOps board';
            aiInsightCtaHref = buildDevSecOpsBoardHref('sre', 'blocked');
        } else {
            aiInsightTitle = 'Overall delivery health';
            aiInsightBody = 'AI-style hint: Use DevSecOps board persona filters to see where work is piling up and prevent future bottlenecks.';
            aiInsightCtaLabel = 'Open DevSecOps board';
            aiInsightCtaHref = buildDevSecOpsBoardHref();
        }
    }

    const [showReportingOnboarding, setShowReportingOnboarding] = useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.reporting.onboarding.dismissed');
        return stored !== 'true';
    });

    const [showReportingAiHint, setShowReportingAiHint] = useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.reporting.aiHint.dismissed');
        return stored !== 'true';
    });

    return (
        <div className="space-y-6">
            {/* Global Filter Bar */}
            <GlobalFilterBar
                showPersonaFilter
                showTimeRangeFilter
                showTenantFilter
                showCompareMode={false}
                compact
            />

            {/* Contextual Navigation Hints */}
            <ContextualHints context="reports" size="sm" />

            {/* Staging Validation Banner (when view=staging and storyId are present) */}
            {view === 'staging' && storyId && (
                <div className="bg-cyan-50 dark:bg-cyan-900/20 border border-cyan-200 dark:border-cyan-800 rounded-lg p-4">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <div className="flex items-center gap-2">
                                <span className="text-lg">🔬</span>
                                <h2 className="font-semibold text-cyan-900 dark:text-cyan-100">
                                    Staging Validation for {storyId}
                                </h2>
                            </div>
                            <p className="text-sm text-cyan-700 dark:text-cyan-300 mt-1">
                                Verify metrics and behavior in staging environment before production deployment.
                            </p>
                            <p className="text-xs text-cyan-700 dark:text-cyan-300 mt-1">
                                Phase: {stagingPhaseLabel}
                                {stagingNextStep && ` • Next: ${stagingNextStep.label}`}
                            </p>
                        </div>
                        <div className="flex items-center gap-3">
                            <Link
                                to={`/work-items/${storyId}/review`}
                                className="text-sm text-cyan-600 dark:text-cyan-400 hover:underline"
                            >
                                ← Back to Review
                            </Link>
                            {allMetricsHealthy && (
                                <button
                                    onClick={() => promoteRoute && navigate(promoteRoute)}
                                    className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium transition flex items-center gap-2"
                                >
                                    <span>🚀</span>
                                    Ready → Promote to production
                                </button>
                            )}
                        </div>
                    </div>
                    <div className="mt-3">
                        <DevSecOpsPipelineStrip
                            phases={ENGINEER_DEVSECOPS_FLOW.phases}
                            currentPhaseId={stagingStep?.phaseId ?? 'staging'}
                            onPhaseClick={handlePhaseClick}
                        />
                    </div>

                    {/* Staging Metrics */}
                    {stagingMetrics.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
                            {stagingMetrics.map((metric, idx) => (
                                <div
                                    key={idx}
                                    className="bg-white dark:bg-neutral-800 rounded-lg border border-cyan-100 dark:border-neutral-600 p-3"
                                >
                                    <div className="text-xs text-slate-500 dark:text-neutral-400 mb-1">
                                        {metric.label}
                                    </div>
                                    <div className="text-xl font-bold text-slate-900 dark:text-neutral-100">
                                        {metric.value}
                                    </div>
                                    <div className="flex items-center justify-between mt-2">
                                        <span className={`text-xs px-2 py-0.5 rounded-full ${getStatusColor(metric.status)}`}>
                                            {metric.status === 'healthy' ? '✅' : metric.status === 'warning' ? '⚠️' : '❌'} {metric.status}
                                        </span>
                                        {metric.threshold && (
                                            <span className="text-xs text-slate-400 dark:text-slate-500">
                                                {metric.threshold}
                                            </span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="bg-white dark:bg-neutral-800 rounded-lg border border-cyan-100 dark:border-neutral-600 p-6 text-center text-slate-500 dark:text-neutral-400">
                            No staging metrics available for this story. Configure metrics collection for the affected services.
                        </div>
                    )}

                    {/* Validation Checklist */}
                    <div className="mt-4 bg-white dark:bg-neutral-800 rounded-lg border border-cyan-100 dark:border-neutral-600 p-4">
                        <h3 className="font-medium text-slate-900 dark:text-neutral-100 mb-3">Validation Checklist</h3>
                        <div className="space-y-2">
                            {[
                                { label: 'All metrics within healthy thresholds', checked: allMetricsHealthy },
                                { label: 'Manual QA testing completed', checked: false },
                                { label: 'No new errors in logs', checked: true },
                                { label: 'Feature flag enabled for staging', checked: true },
                            ].map((item, idx) => (
                                <label key={idx} className="flex items-center gap-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={item.checked}
                                        onChange={() => { }}
                                        className="rounded border-slate-300 dark:border-neutral-600"
                                    />
                                    <span className="text-sm text-slate-700 dark:text-neutral-300">{item.label}</span>
                                </label>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Reporting Dashboard</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">Run, schedule and preview reports for the organization</p>
            </div>

            {showReportingOnboarding && (
                <section className="rounded-lg border border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 px-4 py-3 text-xs text-slate-700 dark:text-neutral-300 flex flex-col md:flex-row md:items-start md:justify-between gap-3">
                    <div className="space-y-1">
                        <div className="font-semibold text-slate-900 dark:text-neutral-100 text-sm">
                            How to use Reporting
                        </div>
                        <div>
                            Pick a template on the left to view metrics and trends. Use the staging view for a specific story to decide when
                            to promote to production.
                        </div>
                        <div>
                            The AI insight banner and drill-down chips under each report header suggest which DevSecOps persona view to open
                            when you see concerning trends.
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => {
                            setShowReportingOnboarding(false);
                            if (typeof window !== 'undefined') {
                                window.localStorage.setItem('softwareOrg.reporting.onboarding.dismissed', 'true');
                            }
                        }}
                        className="self-start md:self-center px-3 py-1.5 rounded-md text-xs font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700"
                    >
                        Hide help
                    </button>
                </section>
            )}

            {/* AI insight banner */}
            {selected && aiInsightTitle && showReportingAiHint && (
                <AiHintBanner
                    icon="✨"
                    title={aiInsightTitle}
                    body={aiInsightBody}
                    ctaLabel={aiInsightCtaLabel}
                    onCtaClick={aiInsightCtaHref ? () => navigate(aiInsightCtaHref as string) : undefined}
                    onDismiss={() => {
                        setShowReportingAiHint(false);
                        if (typeof window !== 'undefined') {
                            window.localStorage.setItem('softwareOrg.reporting.aiHint.dismissed', 'true');
                        }
                    }}
                />
            )}

            {/* Main Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
                {/* Report Templates Sidebar */}
                <div className="space-y-3">
                    <div className="text-sm font-semibold text-slate-700 dark:text-neutral-300 uppercase tracking-wide">Report Templates</div>
                    {reports.map((r) => (
                        <button
                            key={r.id}
                            onClick={() => setSelectedReport(r.id)}
                            className={`w-full text-left p-4 rounded-lg border transition ${selectedReport === r.id
                                ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-slate-300'
                                }`}
                        >
                            <div className="font-semibold text-slate-900 dark:text-neutral-100">{r.title}</div>
                            <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">{r.category}</div>
                            <div className="text-xs text-slate-400 dark:text-slate-500 dark:text-neutral-400 mt-2">Updated {r.updated}</div>
                        </button>
                    ))}
                </div>

                {/* Report Viewer */}
                <div className="lg:col-span-3 space-y-6">
                    {selected && (
                        <>
                            {/* Report Header */}
                            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{selected.title}</h2>
                                        <div className="flex gap-3 mt-3">
                                            <span className="px-3 py-1 bg-slate-100 dark:bg-neutral-800 text-xs font-medium rounded">
                                                {selected.category}
                                            </span>
                                            <span className="px-3 py-1 bg-slate-100 dark:bg-neutral-800 text-xs font-medium rounded">
                                                Updated {selected.updated}
                                            </span>
                                        </div>
                                        {/* Contextual drill-down into DevSecOps views */}
                                        <div className="mt-4 flex flex-wrap gap-2 text-xs">
                                            {selected.id === 'r-002' && (
                                                <button
                                                    type="button"
                                                    onClick={() => navigate(buildDevSecOpsBoardHref('security', 'blocked'))}
                                                    className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-300 border border-red-200 dark:border-red-700 hover:bg-red-100 dark:hover:bg-red-900/50 transition"
                                                >
                                                    <span>🔒</span>
                                                    <span>View security blockers in DevSecOps board</span>
                                                </button>
                                            )}
                                            {selected.id === 'r-003' && (
                                                <button
                                                    type="button"
                                                    onClick={() => navigate(buildDevSecOpsBoardHref('engineer'))}
                                                    className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 border border-blue-200 dark:border-blue-700 hover:bg-blue-100 dark:hover:bg-blue-900/50 transition"
                                                >
                                                    <span>🚀</span>
                                                    <span>Open engineer DevSecOps board</span>
                                                </button>
                                            )}
                                            {selected.id === 'r-004' && (
                                                <button
                                                    type="button"
                                                    onClick={() => navigate(buildDevSecOpsBoardHref('lead'))}
                                                    className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-700 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 transition"
                                                >
                                                    <span>📊</span>
                                                    <span>View portfolio in DevSecOps board</span>
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    <div className="flex gap-2">
                                        <button className="px-4 py-2 text-sm rounded bg-slate-200 dark:bg-neutral-800 hover:bg-slate-300">
                                            Export PDF
                                        </button>
                                        <button className="px-4 py-2 text-sm rounded bg-slate-200 dark:bg-neutral-800 hover:bg-slate-300">
                                            Download CSV
                                        </button>
                                        <button className="px-4 py-2 text-sm rounded bg-blue-600 text-white hover:bg-blue-700">
                                            Schedule
                                        </button>
                                    </div>
                                </div>
                            </div>

                            {/* Metrics Grid */}
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {selected.metrics.map((metric, idx) => (
                                    <div
                                        key={idx}
                                        className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4"
                                    >
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">{metric.label}</div>
                                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mt-2">{metric.value}</div>
                                        <div className={`text-sm mt-2 ${getTrendColor(metric.trend)}`}>{metric.trend}</div>
                                    </div>
                                ))}
                            </div>

                            {/* Data Visualization Placeholder */}
                            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                                <div className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-4">Detailed Trends</div>
                                <div className="h-64 bg-gradient-to-b from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 rounded flex items-center justify-center">
                                    <div className="text-center">
                                        <div className="text-slate-500">
                                            Chart placeholder - integrate charting library (recharts/plotly)
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ReportingDashboard;
