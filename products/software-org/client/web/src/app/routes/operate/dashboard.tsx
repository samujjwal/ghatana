/**
 * Dashboard - Main Operational Hub
 *
 * The primary entry point showing real-time health, pending actions,
 * and quick access to common tasks.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { Link } from 'react-router';
import { MainLayout } from '@/app/Layout';
import { DevSecOpsStageStrip } from '@/features/devsecops/DevSecOpsStageStrip';
import type { StageHealth } from '@/types/devsecops';
import {
    AlertTriangle,
    CheckCircle2,
    Clock,
    Activity,
    ArrowRight,
    Plus,
    RefreshCw,
    TrendingUp,
    TrendingDown,
    Zap,
} from 'lucide-react';

// Mock stage health data
const mockStageHealth: StageHealth[] = [
    { stage: 'plan', status: 'on-track', itemsTotal: 12, itemsCompleted: 10, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'design', status: 'on-track', itemsTotal: 8, itemsCompleted: 6, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'develop', status: 'at-risk', itemsTotal: 25, itemsCompleted: 18, itemsBlocked: 2, itemsInProgress: 5, criticalIssues: 1, lastUpdated: new Date().toISOString() },
    { stage: 'build', status: 'on-track', itemsTotal: 30, itemsCompleted: 28, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'test', status: 'at-risk', itemsTotal: 45, itemsCompleted: 35, itemsBlocked: 3, itemsInProgress: 7, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    { stage: 'secure', status: 'blocked', itemsTotal: 15, itemsCompleted: 8, itemsBlocked: 5, itemsInProgress: 2, criticalIssues: 3, lastUpdated: new Date().toISOString() },
    { stage: 'compliance', status: 'on-track', itemsTotal: 10, itemsCompleted: 9, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'staging', status: 'on-track', itemsTotal: 6, itemsCompleted: 5, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'deploy', status: 'blocked', itemsTotal: 8, itemsCompleted: 3, itemsBlocked: 4, itemsInProgress: 1, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    { stage: 'operate', status: 'on-track', itemsTotal: 20, itemsCompleted: 18, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    { stage: 'monitor', status: 'on-track', itemsTotal: 15, itemsCompleted: 14, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
];

// Calculate stage-aware aggregates
const totalItems = mockStageHealth.reduce((sum, stage) => sum + stage.itemsTotal, 0);
const completedItems = mockStageHealth.reduce((sum, stage) => sum + stage.itemsCompleted, 0);
const blockedItems = mockStageHealth.reduce((sum, stage) => sum + stage.itemsBlocked, 0);
const criticalIssues = mockStageHealth.reduce((sum, stage) => sum + stage.criticalIssues, 0);
const completionRate = Math.round((completedItems / totalItems) * 100);

// Mock data for dashboard (now derived from stage health)
const stats = {
    activeIncidents: criticalIssues,
    pendingApprovals: blockedItems,
    workflowsRunning: mockStageHealth.reduce((sum, stage) => sum + stage.itemsInProgress, 0),
    systemHealth: completionRate,
};

const recentActivity = [
    { id: 1, type: 'workflow', message: 'CI/CD Pipeline completed successfully', time: '2 min ago', status: 'success' },
    { id: 2, type: 'alert', message: 'High CPU usage on prod-api-3', time: '15 min ago', status: 'warning' },
    { id: 3, type: 'approval', message: 'Deployment to production pending', time: '1 hour ago', status: 'pending' },
    { id: 4, type: 'agent', message: 'Security Agent detected vulnerability', time: '2 hours ago', status: 'warning' },
];

const keyMetrics = [
    { name: 'Deployment Frequency', value: '12/day', trend: 8, target: '10/day' },
    { name: 'Lead Time', value: '45 min', trend: -12, target: '< 1 hour' },
    { name: 'MTTR', value: '23 min', trend: -5, target: '< 30 min' },
    { name: 'Change Failure Rate', value: '2.1%', trend: -15, target: '< 5%' },
];

// Cross-cutting widgets data - Incidents by stage
const incidentsByStage = [
    { stage: 'develop', incidents: 1, severity: 'high', stageName: 'Develop' },
    { stage: 'test', incidents: 2, severity: 'critical', stageName: 'Test' },
    { stage: 'secure', incidents: 3, severity: 'critical', stageName: 'Secure' },
    { stage: 'deploy', incidents: 2, severity: 'critical', stageName: 'Deploy' },
    { stage: 'operate', incidents: 1, severity: 'high', stageName: 'Operate' },
];

// Queue items by stage
const queueByStage = [
    { stage: 'test', items: 3, status: 'blocked', stageName: 'Test' },
    { stage: 'secure', items: 5, status: 'blocked', stageName: 'Secure' },
    { stage: 'deploy', items: 4, status: 'blocked', stageName: 'Deploy' },
];

function StatCard({ title, value, icon: Icon, color, link }: {
    title: string;
    value: number | string;
    icon: React.ElementType;
    color: string;
    link: string;
}) {
    return (
        <Link
            to={link}
            className={`bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-5 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all group`}
        >
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{title}</p>
                    <p className="text-3xl font-bold text-gray-900 dark:text-white mt-1">{value}</p>
                </div>
                <div className={`p-3 rounded-xl ${color}`}>
                    <Icon className="h-6 w-6 text-white" />
                </div>
            </div>
            <div className="mt-4 flex items-center text-sm text-blue-600 dark:text-blue-400 opacity-0 group-hover:opacity-100 transition-opacity">
                View details <ArrowRight className="h-4 w-4 ml-1" />
            </div>
        </Link>
    );
}

function ActivityItem({ item }: { item: typeof recentActivity[0] }) {
    const statusColors = {
        success: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300',
        warning: 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300',
        pending: 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300',
    };

    const statusIcons = {
        success: CheckCircle2,
        warning: AlertTriangle,
        pending: Clock,
    };

    const StatusIcon = statusIcons[item.status as keyof typeof statusIcons];

    return (
        <div className="flex items-start gap-3 py-3 border-b border-gray-100 dark:border-slate-700 last:border-0">
            <div className={`p-2 rounded-lg ${statusColors[item.status as keyof typeof statusColors]}`}>
                <StatusIcon className="h-4 w-4" />
            </div>
            <div className="flex-1 min-w-0">
                <p className="text-sm text-gray-900 dark:text-gray-100 truncate">{item.message}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{item.time}</p>
            </div>
        </div>
    );
}

function MetricCard({ metric }: { metric: typeof keyMetrics[0] }) {
    const isPositive = metric.trend > 0;
    const TrendIcon = isPositive ? TrendingUp : TrendingDown;
    const trendColor = metric.name.includes('Failure') || metric.name.includes('Time')
        ? (isPositive ? 'text-red-500' : 'text-green-500')
        : (isPositive ? 'text-green-500' : 'text-red-500');

    return (
        <div className="bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{metric.name}</p>
                <div className={`flex items-center gap-1 text-xs ${trendColor}`}>
                    <TrendIcon className="h-3 w-3" />
                    {Math.abs(metric.trend)}%
                </div>
            </div>
            <p className="text-2xl font-bold text-gray-900 dark:text-white mt-2">{metric.value}</p>
            <p className="text-xs text-gray-500 dark:text-gray-500 mt-1">Target: {metric.target}</p>
        </div>
    );
}

export default function DashboardPage() {
    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Header */}
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">DevSecOps Operations</h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            Real-time overview of your DevSecOps pipeline and operations
                        </p>
                    </div>
                    <button className="inline-flex items-center gap-2 px-4 py-2 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-slate-700 transition-colors">
                        <RefreshCw className="h-4 w-4" />
                        Refresh
                    </button>
                </div>

                {/* DevSecOps Stage Strip */}
                <DevSecOpsStageStrip stagesHealth={mockStageHealth} />

                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard
                        title="Active Incidents"
                        value={stats.activeIncidents}
                        icon={AlertTriangle}
                        color="bg-red-500"
                        link="/operate/incidents"
                    />
                    <StatCard
                        title="Pending Approvals"
                        value={stats.pendingApprovals}
                        icon={Clock}
                        color="bg-amber-500"
                        link="/operate/queue"
                    />
                    <StatCard
                        title="Workflows Running"
                        value={stats.workflowsRunning}
                        icon={Zap}
                        color="bg-blue-500"
                        link="/build/workflows"
                    />
                    <StatCard
                        title="System Health"
                        value={`${stats.systemHealth}%`}
                        icon={Activity}
                        color="bg-green-500"
                        link="/observe/metrics"
                    />
                </div>

                {/* Main Content Grid */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Recent Activity */}
                    <div className="lg:col-span-2 bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-6 shadow-sm">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Recent Activity</h2>
                            <Link
                                to="/operate/queue"
                                className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                            >
                                View all <ArrowRight className="h-4 w-4" />
                            </Link>
                        </div>
                        <div className="space-y-1">
                            {recentActivity.map((item) => (
                                <ActivityItem key={item.id} item={item} />
                            ))}
                        </div>
                    </div>

                    {/* Quick Actions */}
                    <div className="bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-6 shadow-sm">
                        <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Quick Actions</h2>
                        <div className="space-y-3">
                            <Link
                                to="/build/workflows"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 text-slate-800 dark:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <Plus className="h-5 w-5" />
                                <span className="font-medium">New Workflow</span>
                            </Link>
                            <Link
                                to="/operate/queue"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 text-slate-800 dark:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <CheckCircle2 className="h-5 w-5" />
                                <span className="font-medium">Review Approvals</span>
                            </Link>
                            <Link
                                to="/operate/incidents"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 text-slate-800 dark:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <AlertTriangle className="h-5 w-5" />
                                <span className="font-medium">View Incidents</span>
                            </Link>
                            <Link
                                to="/build/simulator"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 text-slate-800 dark:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <Zap className="h-5 w-5" />
                                <span className="font-medium">Run Simulation</span>
                            </Link>
                        </div>
                    </div>
                </div>

                {/* Cross-cutting widgets: Incidents & Queue by Stage */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    {/* Incidents by Stage */}
                    <div className="bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-6 shadow-sm">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Incidents by Stage</h2>
                            <Link
                                to="/operate/incidents"
                                className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                            >
                                View all <ArrowRight className="h-4 w-4" />
                            </Link>
                        </div>
                        <div className="space-y-3">
                            {incidentsByStage.map((item) => (
                                <Link
                                    key={item.stage}
                                    to={`/operate/stages/${item.stage}`}
                                    className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-slate-800/50 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors group"
                                >
                                    <div className="flex items-center gap-3">
                                        <AlertTriangle className={`h-5 w-5 ${item.severity === 'critical' ? 'text-red-500' : 'text-amber-500'}`} />
                                        <div>
                                            <p className="font-medium text-gray-900 dark:text-white">{item.stageName}</p>
                                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                                {item.incidents} {item.incidents === 1 ? 'incident' : 'incidents'}
                                            </p>
                                        </div>
                                    </div>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                                        item.severity === 'critical' 
                                            ? 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300'
                                            : 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300'
                                    }`}>
                                        {item.severity}
                                    </span>
                                </Link>
                            ))}
                        </div>
                    </div>

                    {/* Blocked Items by Stage */}
                    <div className="bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-6 shadow-sm">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Blocked Items by Stage</h2>
                            <Link
                                to="/operate/queue"
                                className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                            >
                                View queue <ArrowRight className="h-4 w-4" />
                            </Link>
                        </div>
                        <div className="space-y-3">
                            {queueByStage.map((item) => (
                                <Link
                                    key={item.stage}
                                    to={`/operate/stages/${item.stage}`}
                                    className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-slate-800/50 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors group"
                                >
                                    <div className="flex items-center gap-3">
                                        <Clock className="h-5 w-5 text-blue-500" />
                                        <div>
                                            <p className="font-medium text-gray-900 dark:text-white">{item.stageName}</p>
                                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                                {item.items} {item.items === 1 ? 'item' : 'items'} blocked
                                            </p>
                                        </div>
                                    </div>
                                    <span className="px-2 py-1 rounded text-xs font-medium bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300">
                                        {item.status}
                                    </span>
                                </Link>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Key Metrics */}
                <div className="bg-white/90 dark:bg-slate-900/70 rounded-xl border border-gray-200/70 dark:border-slate-700/70 p-6 shadow-sm">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Key Metrics</h2>
                        <Link
                            to="/observe/metrics"
                            className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                        >
                            View all metrics <ArrowRight className="h-4 w-4" />
                        </Link>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {keyMetrics.map((metric) => (
                            <MetricCard key={metric.name} metric={metric} />
                        ))}
                    </div>
                </div>
            </div>
        </MainLayout>
    );
}
