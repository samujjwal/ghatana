import { useAtomValue } from 'jotai';
import { Link } from 'react-router';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useDashboardStats, useRecentActivity } from '@/hooks/useOperateApi';
import {
    AlertTriangle,
    CheckCircle2,
    Clock,
    Activity,
    ArrowRight,
    TrendingUp,
    TrendingDown,
    Zap,
    Package,
    BarChart3,
} from 'lucide-react';
import { Badge } from '@/components/ui';

/**
 * Operations Dashboard
 *
 * <p><b>Purpose</b><br>
 * Primary operational hub showing real-time health, pending actions,
 * and quick access to incidents, queue, and key metrics.
 *
 * <p><b>Features</b><br>
 * - Real-time stats (active incidents, pending approvals, workflows)
 * - Recent activity feed
 * - Key metrics (DORA metrics)
 * - Quick actions and navigation
 *
 * @doc.type component
 * @doc.purpose Operations dashboard
 * @doc.layer product
 * @doc.pattern Page
 */
export function OperationsDashboard() {
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';

    const { data: statsData, isLoading: statsLoading } = useDashboardStats(tenantId);
    const { data: activityData, isLoading: activityLoading } = useRecentActivity(tenantId);

    const stats = statsData?.data;
    const recentActivity = activityData?.data || [];

    const activityStatusConfig = {
        success: { icon: CheckCircle2, color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
        warning: { icon: AlertTriangle, color: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/20' },
        pending: { icon: Clock, color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
        error: { icon: AlertTriangle, color: 'text-red-500', bg: 'bg-red-50 dark:bg-red-900/20' },
        critical: { icon: AlertTriangle, color: 'text-red-600', bg: 'bg-red-100 dark:bg-red-900/30' },
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Operations Dashboard</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">
                    Real-time operational health and pending actions
                </p>
            </div>

            {/* Stats Cards */}
            {statsLoading ? (
                <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                    Loading stats...
                </div>
            ) : stats ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard
                        title="Active Incidents"
                        value={stats.activeIncidents}
                        icon={AlertTriangle}
                        color="text-red-500"
                        link="/operate/incidents"
                    />
                    <StatCard
                        title="Pending Approvals"
                        value={stats.pendingApprovals}
                        icon={Clock}
                        color="text-amber-500"
                        link="/operate/queue"
                    />
                    <StatCard
                        title="Workflows Running"
                        value={stats.workflowsRunning}
                        icon={Activity}
                        color="text-blue-500"
                        link="/build/workflows"
                    />
                    <StatCard
                        title="System Health"
                        value={`${stats.systemHealth.toFixed(1)}%`}
                        icon={CheckCircle2}
                        color="text-green-500"
                        link="/observe/metrics"
                    />
                </div>
            ) : null}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* DORA Metrics */}
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 flex items-center gap-2">
                            <BarChart3 className="h-5 w-5" />
                            Key Metrics
                        </h2>
                        <Link
                            to="/observe/metrics"
                            className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                        >
                            View all
                            <ArrowRight className="h-3 w-3" />
                        </Link>
                    </div>

                    {statsLoading ? (
                        <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                            Loading metrics...
                        </div>
                    ) : stats ? (
                        <div className="space-y-4">
                            <MetricRow
                                name="Deployment Frequency"
                                value={`${stats.deploymentFrequency}/day`}
                                trend={((stats.deploymentFrequency - 10) / 10) * 100}
                                target="10/day"
                            />
                            <MetricRow
                                name="Lead Time"
                                value={`${stats.avgLeadTime} min`}
                                trend={-((stats.avgLeadTime - 45) / 45) * 100}
                                target="< 60 min"
                                lowerIsBetter
                            />
                            <MetricRow
                                name="MTTR"
                                value={`${stats.mttr} min`}
                                trend={-((stats.mttr - 25) / 25) * 100}
                                target="< 30 min"
                                lowerIsBetter
                            />
                            <MetricRow
                                name="Change Failure Rate"
                                value={`${stats.changeFailureRate.toFixed(1)}%`}
                                trend={-((stats.changeFailureRate - 3) / 3) * 100}
                                target="< 5%"
                                lowerIsBetter
                            />
                        </div>
                    ) : null}
                </div>

                {/* Recent Activity */}
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 flex items-center gap-2">
                            <Activity className="h-5 w-5" />
                            Recent Activity
                        </h2>
                        <button className="text-sm text-blue-600 dark:text-blue-400 hover:underline">
                            Refresh
                        </button>
                    </div>

                    {activityLoading ? (
                        <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                            Loading activity...
                        </div>
                    ) : recentActivity.length === 0 ? (
                        <div className="text-center py-8 text-slate-500 dark:text-neutral-500">
                            No recent activity
                        </div>
                    ) : (
                        <div className="space-y-3 max-h-96 overflow-y-auto">
                            {recentActivity.map((activity) => {
                                const config = activityStatusConfig[activity.status];
                                const Icon = config.icon;
                                return (
                                    <div
                                        key={activity.id}
                                        className="flex items-start gap-3 p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                                    >
                                        <div className={`p-2 rounded-lg ${config.bg}`}>
                                            <Icon className={`h-4 w-4 ${config.color}`} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                                {activity.message}
                                            </div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                                {activity.time}
                                            </div>
                                        </div>
                                        <Badge variant={activity.status === 'success' ? 'success' : activity.status === 'warning' || activity.status === 'pending' ? 'warning' : 'danger'}>
                                            {activity.type}
                                        </Badge>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* Quick Actions */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">Quick Actions</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <QuickActionCard
                        title="View Incidents"
                        description="Manage active incidents"
                        icon={AlertTriangle}
                        link="/operate/incidents"
                        color="text-red-500"
                    />
                    <QuickActionCard
                        title="Process Queue"
                        description="Review pending approvals"
                        icon={Clock}
                        link="/operate/queue"
                        color="text-amber-500"
                    />
                    <QuickActionCard
                        title="Run Workflows"
                        description="Execute automation"
                        icon={Zap}
                        link="/build/workflows"
                        color="text-purple-500"
                    />
                    <QuickActionCard
                        title="View Services"
                        description="Manage service catalog"
                        icon={Package}
                        link="/admin/services"
                        color="text-blue-500"
                    />
                </div>
            </div>
        </div>
    );
}

// Helper Components

function StatCard({
    title,
    value,
    icon: Icon,
    color,
    link,
}: {
    title: string;
    value: number | string;
    icon: typeof AlertTriangle;
    color: string;
    link: string;
}) {
    return (
        <Link
            to={link}
            className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6 hover:shadow-lg dark:hover:shadow-slate-700/30 transition-all"
        >
            <div className="flex items-center justify-between mb-4">
                <div className={`p-3 rounded-lg bg-slate-50 dark:bg-slate-800`}>
                    <Icon className={`h-6 w-6 ${color}`} />
                </div>
            </div>
            <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
            <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{title}</div>
        </Link>
    );
}

function MetricRow({
    name,
    value,
    trend,
    target,
    lowerIsBetter = false,
}: {
    name: string;
    value: string;
    trend: number;
    target: string;
    lowerIsBetter?: boolean;
}) {
    const isPositiveTrend = lowerIsBetter ? trend < 0 : trend > 0;
    const TrendIcon = isPositiveTrend ? TrendingUp : TrendingDown;
    const trendColor = isPositiveTrend ? 'text-green-500' : 'text-red-500';

    return (
        <div className="flex items-center justify-between py-2 border-b border-slate-200 dark:border-slate-700 last:border-0">
            <div>
                <div className="font-medium text-slate-900 dark:text-neutral-100">{name}</div>
                <div className="text-xs text-slate-500 dark:text-neutral-500">Target: {target}</div>
            </div>
            <div className="flex items-center gap-3">
                <div className="text-right">
                    <div className="font-semibold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className={`text-xs flex items-center gap-1 ${trendColor}`}>
                        <TrendIcon className="h-3 w-3" />
                        {Math.abs(trend).toFixed(0)}%
                    </div>
                </div>
            </div>
        </div>
    );
}

function QuickActionCard({
    title,
    description,
    icon: Icon,
    link,
    color,
}: {
    title: string;
    description: string;
    icon: typeof AlertTriangle;
    link: string;
    color: string;
}) {
    return (
        <Link
            to={link}
            className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6 hover:shadow-lg dark:hover:shadow-slate-700/30 transition-all group"
        >
            <div className="flex items-start justify-between mb-3">
                <Icon className={`h-6 w-6 ${color}`} />
                <ArrowRight className="h-4 w-4 text-slate-400 group-hover:text-blue-500 group-hover:translate-x-1 transition-all" />
            </div>
            <div className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">{title}</div>
            <div className="text-sm text-slate-600 dark:text-neutral-400">{description}</div>
        </Link>
    );
}
