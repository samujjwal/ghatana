/**
 * DevSecOps Stages Overview
 *
 * Displays all DevSecOps stages with their current health status,
 * KPIs, and quick actions. Provides a high-level view of the
 * entire software delivery pipeline.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { MainLayout } from '@/app/Layout';
import { Link } from 'react-router';
import { useState } from 'react';
import type { StageHealth } from '@/types/devsecops';
import { useStages } from '@/hooks/useConfig';
import { useStageHealth } from '@/hooks/useDevSecOpsApi';
import { getStageMetadata } from '@/lib/devsecops/stageMetadata';
import { DevSecOpsStageStrip } from '@/features/devsecops/DevSecOpsStageStrip';
import { ConnectionStatus } from '@/features/devsecops/ConnectionStatus';
import {
    ArrowRight,
    TrendingUp,
    TrendingDown,
    AlertCircle,
    CheckCircle,
    Clock,
} from 'lucide-react';
import { Badge, KpiCard } from '@/components/ui';

// Mock stage health data
const mockStageHealth: Record<string, StageHealth> = {
    plan: { stage: 'plan', status: 'on-track', itemsTotal: 12, itemsCompleted: 10, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    design: { stage: 'design', status: 'on-track', itemsTotal: 8, itemsCompleted: 6, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    develop: { stage: 'develop', status: 'at-risk', itemsTotal: 25, itemsCompleted: 18, itemsBlocked: 2, itemsInProgress: 5, criticalIssues: 1, lastUpdated: new Date().toISOString() },
    build: { stage: 'build', status: 'on-track', itemsTotal: 30, itemsCompleted: 28, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    test: { stage: 'test', status: 'at-risk', itemsTotal: 45, itemsCompleted: 35, itemsBlocked: 3, itemsInProgress: 7, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    secure: { stage: 'secure', status: 'blocked', itemsTotal: 15, itemsCompleted: 8, itemsBlocked: 5, itemsInProgress: 2, criticalIssues: 3, lastUpdated: new Date().toISOString() },
    compliance: { stage: 'compliance', status: 'on-track', itemsTotal: 10, itemsCompleted: 9, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    staging: { stage: 'staging', status: 'on-track', itemsTotal: 6, itemsCompleted: 5, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    deploy: { stage: 'deploy', status: 'blocked', itemsTotal: 8, itemsCompleted: 3, itemsBlocked: 4, itemsInProgress: 1, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    operate: { stage: 'operate', status: 'on-track', itemsTotal: 20, itemsCompleted: 18, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    monitor: { stage: 'monitor', status: 'on-track', itemsTotal: 15, itemsCompleted: 14, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
};

/**
 * Get status badge variant based on stage health
 */
function getStatusBadge(status: string) {
    switch (status) {
        case 'on-track':
            return { variant: 'success' as const, icon: CheckCircle, label: 'On Track' };
        case 'at-risk':
            return { variant: 'warning' as const, icon: AlertCircle, label: 'At Risk' };
        case 'blocked':
            return { variant: 'danger' as const, icon: AlertCircle, label: 'Blocked' };
        default:
            return { variant: 'neutral' as const, icon: Clock, label: 'Unknown' };
    }
}

/**
 * Calculate completion percentage
 */
function getCompletionPercentage(health: StageHealth): number {
    if (health.itemsTotal === 0) return 0;
    return Math.round((health.itemsCompleted / health.itemsTotal) * 100);
}

export default function StagesOverview() {
    const { data: stageMappings, isLoading } = useStages();
    const [selectedStage, setSelectedStage] = useState<string | null>(null);

    // Use mock data for now
    const stageHealthData = mockStageHealth;

    // Calculate overall metrics
    const totalItems = Object.values(stageHealthData).reduce((sum, health) => sum + health.itemsTotal, 0);
    const completedItems = Object.values(stageHealthData).reduce((sum, health) => sum + health.itemsCompleted, 0);
    const blockedItems = Object.values(stageHealthData).reduce((sum, health) => sum + health.itemsBlocked, 0);
    const criticalIssues = Object.values(stageHealthData).reduce((sum, health) => sum + health.criticalIssues, 0);

    const overallCompletion = totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0;

    if (isLoading) {
        return (
            <MainLayout title="DevSecOps Stages">
                <div className="flex items-center justify-center min-h-[400px]">
                    <div className="text-center">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                        <p className="text-gray-600 dark:text-gray-400">Loading stages...</p>
                    </div>
                </div>
            </MainLayout>
        );
    }

    return (
        <MainLayout
            title="DevSecOps Stages"
            subtitle="Monitor and manage all stages of your software delivery pipeline"
        >
            <div className="space-y-6">
                {/* Connection Status */}
                <ConnectionStatus />

                {/* Stage Flow Visualization */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                        Pipeline Overview
                    </h2>
                    <DevSecOpsStageStrip
                        stagesHealth={stageHealthData}
                        onStageClick={(stageKey: string) => setSelectedStage(stageKey)}
                    />
                </div>

                {/* Overall Metrics */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <KpiCard
                        title="Overall Completion"
                        value={`${overallCompletion}%`}
                        trend={overallCompletion > 75 ? 'up' : 'down'}
                        trendValue={`${completedItems}/${totalItems} items`}
                        icon={TrendingUp}
                    />
                    <KpiCard
                        title="In Progress"
                        value={totalItems - completedItems - blockedItems}
                        trend="neutral"
                        trendValue="Active work items"
                        icon={Clock}
                    />
                    <KpiCard
                        title="Blocked Items"
                        value={blockedItems}
                        trend={blockedItems > 0 ? 'down' : 'up'}
                        trendValue={blockedItems > 0 ? 'Needs attention' : 'All clear'}
                        icon={AlertCircle}
                        variant={blockedItems > 0 ? 'danger' : 'success'}
                    />
                    <KpiCard
                        title="Critical Issues"
                        value={criticalIssues}
                        trend={criticalIssues > 0 ? 'down' : 'up'}
                        trendValue={criticalIssues > 0 ? 'Requires action' : 'No issues'}
                        icon={AlertCircle}
                        variant={criticalIssues > 0 ? 'danger' : 'success'}
                    />
                </div>

                {/* Stage Details Grid */}
                <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
                    {Object.entries(stageHealthData).map(([stageKey, health]) => {
                        const metadata = getStageMetadata(stageKey);
                        const statusBadge = getStatusBadge(health.status);
                        const completion = getCompletionPercentage(health);
                        const StatusIcon = statusBadge.icon;

                        return (
                            <Link
                                key={stageKey}
                                to={`/operate/stages/${stageKey}`}
                                className="block bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md hover:border-blue-500 dark:hover:border-blue-400 transition-all duration-200 p-6 group"
                            >
                                {/* Stage Header */}
                                <div className="flex items-start justify-between mb-4">
                                    <div className="flex items-center space-x-3">
                                        <div className={`p-2 rounded-lg ${metadata.color}`}>
                                            <metadata.icon className="w-5 h-5 text-white" />
                                        </div>
                                        <div>
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                                                {metadata.label}
                                            </h3>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                                {metadata.description}
                                            </p>
                                        </div>
                                    </div>
                                    <ArrowRight className="w-5 h-5 text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors" />
                                </div>

                                {/* Status Badge */}
                                <div className="mb-4">
                                    <Badge variant={statusBadge.variant} className="inline-flex items-center space-x-1">
                                        <StatusIcon className="w-3 h-3" />
                                        <span>{statusBadge.label}</span>
                                    </Badge>
                                </div>

                                {/* Progress Bar */}
                                <div className="mb-4">
                                    <div className="flex items-center justify-between text-sm mb-2">
                                        <span className="text-gray-600 dark:text-gray-400">Completion</span>
                                        <span className="font-semibold text-gray-900 dark:text-white">{completion}%</span>
                                    </div>
                                    <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                                        <div
                                            className={`h-2 rounded-full transition-all duration-300 ${completion >= 75
                                                    ? 'bg-green-500'
                                                    : completion >= 50
                                                        ? 'bg-yellow-500'
                                                        : 'bg-red-500'
                                                }`}
                                            style={{ width: `${completion}%` }}
                                        />
                                    </div>
                                </div>

                                {/* Stage Metrics */}
                                <div className="grid grid-cols-2 gap-4 text-sm">
                                    <div>
                                        <div className="text-gray-600 dark:text-gray-400">Total Items</div>
                                        <div className="text-xl font-semibold text-gray-900 dark:text-white">
                                            {health.itemsTotal}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-gray-600 dark:text-gray-400">Completed</div>
                                        <div className="text-xl font-semibold text-green-600 dark:text-green-400">
                                            {health.itemsCompleted}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-gray-600 dark:text-gray-400">In Progress</div>
                                        <div className="text-xl font-semibold text-blue-600 dark:text-blue-400">
                                            {health.itemsInProgress}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-gray-600 dark:text-gray-400">Blocked</div>
                                        <div className="text-xl font-semibold text-red-600 dark:text-red-400">
                                            {health.itemsBlocked}
                                        </div>
                                    </div>
                                </div>

                                {/* Critical Issues Alert */}
                                {health.criticalIssues > 0 && (
                                    <div className="mt-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                                        <div className="flex items-center space-x-2 text-red-700 dark:text-red-400">
                                            <AlertCircle className="w-4 h-4" />
                                            <span className="text-sm font-medium">
                                                {health.criticalIssues} critical {health.criticalIssues === 1 ? 'issue' : 'issues'}
                                            </span>
                                        </div>
                                    </div>
                                )}
                            </Link>
                        );
                    })}
                </div>
            </div>
        </MainLayout>
    );
}
