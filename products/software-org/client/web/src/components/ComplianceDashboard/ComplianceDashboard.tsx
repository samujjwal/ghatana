/**
 * Compliance Dashboard Component
 * 
 * Displays compliance overview with:
 * - Overall status and score
 * - Standards status cards (SOC2, HIPAA, GDPR)
 * - Critical and open issues
 * - Recent audit activity
 * - Upcoming deadlines
 * - Compliance trends chart
 * - Quick actions (generate reports, run assessments, export)
 */

import React, { useMemo } from 'react';
import {
    useComplianceDashboard,
    useComplianceReport,
    useComplianceAssessment,
    useComplianceExport,
} from '../../hooks/useCompliance';
import {
    ComplianceStandard,
    ComplianceStatus,
    ExportFormat,
    ComplianceIssueSeverity,
} from '../../types/compliance';

interface ComplianceDashboardProps {
    onReportGenerated?: (reportId: string) => void;
    onAssessmentCompleted?: (assessmentId: string) => void;
}

export function ComplianceDashboard({
    onReportGenerated,
    onAssessmentCompleted,
}: ComplianceDashboardProps) {
    const { dashboard, loading, error, refresh } = useComplianceDashboard();
    const { generate: generateReport, loading: generatingReport } = useComplianceReport();
    const { runAssessment, loading: runningAssessment } = useComplianceAssessment();
    const { exportReport, exporting } = useComplianceExport();

    const handleGenerateReport = async (standard: ComplianceStandard) => {
        const report = await generateReport(standard, { reportType: 'detailed' });
        if (report && onReportGenerated) {
            onReportGenerated(report.reportId);
        }
    };

    const handleRunAssessment = async (standard: ComplianceStandard) => {
        const assessment = await runAssessment(standard);
        if (assessment && onAssessmentCompleted) {
            onAssessmentCompleted(assessment.assessmentId);
        }
    };

    const handleExport = async (reportId: string, format: ExportFormat) => {
        await exportReport(reportId, {
            format,
            includeCharts: true,
            includeTables: true,
            includeAuditTrail: false,
        });
    };

    const getStatusColor = (status: ComplianceStatus): string => {
        switch (status) {
            case ComplianceStatus.COMPLIANT:
                return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/30 border-green-200 dark:border-green-700';
            case ComplianceStatus.PARTIALLY_COMPLIANT:
                return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/30 border-yellow-200 dark:border-yellow-700';
            case ComplianceStatus.NON_COMPLIANT:
                return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-700';
            default:
                return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800 border-slate-200 dark:border-neutral-600';
        }
    };

    const getStatusIcon = (status: ComplianceStatus): string => {
        switch (status) {
            case ComplianceStatus.COMPLIANT:
                return '✓';
            case ComplianceStatus.PARTIALLY_COMPLIANT:
                return '⚠';
            case ComplianceStatus.NON_COMPLIANT:
                return '✗';
            default:
                return '?';
        }
    };

    const getSeverityColor = (severity: ComplianceIssueSeverity): string => {
        switch (severity) {
            case ComplianceIssueSeverity.CRITICAL:
                return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-red-900/30';
            case ComplianceIssueSeverity.HIGH:
                return 'text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-900/30';
            case ComplianceIssueSeverity.MEDIUM:
                return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/30';
            case ComplianceIssueSeverity.LOW:
                return 'text-blue-600 dark:text-indigo-400 bg-blue-50 dark:bg-blue-900/30';
            default:
                return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800';
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-md p-4 text-red-800 dark:text-red-300">
                <p className="font-semibold">Error loading compliance dashboard</p>
                <p className="text-sm mt-1">{error.message}</p>
                <button
                    onClick={refresh}
                    className="mt-2 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
                >
                    Retry
                </button>
            </div>
        );
    }

    if (!dashboard) {
        return (
            <div className="bg-slate-50 dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-md p-4 text-slate-600 dark:text-neutral-400">
                <p>No compliance data available</p>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        Compliance Dashboard
                    </h2>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                        Monitor and manage compliance across standards
                    </p>
                </div>
                <button
                    onClick={refresh}
                    disabled={loading}
                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
                >
                    {loading ? 'Refreshing...' : '🔄 Refresh'}
                </button>
            </div>

            {/* Overall Status Card */}
            <div
                className={`border-2 rounded-lg p-6 ${getStatusColor(dashboard.overallStatus)}`}
            >
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm font-medium uppercase tracking-wide">Overall Status</p>
                        <div className="flex items-center mt-2">
                            <span className="text-4xl font-bold mr-2">
                                {getStatusIcon(dashboard.overallStatus)}
                            </span>
                            <div>
                                <p className="text-2xl font-bold">{dashboard.overallStatus}</p>
                                <p className="text-sm">Compliance Score: {dashboard.overallScore.toFixed(1)}%</p>
                            </div>
                        </div>
                    </div>
                    <div className="text-right">
                        <div className="text-3xl font-bold">{dashboard.criticalIssues}</div>
                        <p className="text-sm">Critical Issues</p>
                    </div>
                </div>
            </div>

            {/* Standards Status Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {dashboard.standards.map((standard) => (
                    <div
                        key={standard.standard}
                        className={`border rounded-lg p-4 ${getStatusColor(standard.status)}`}
                    >
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-lg font-semibold">{standard.standard}</h3>
                            <span className="text-2xl">{getStatusIcon(standard.status)}</span>
                        </div>
                        <div className="space-y-2">
                            <div className="flex justify-between text-sm">
                                <span>Score:</span>
                                <span className="font-semibold">{standard.score.toFixed(1)}%</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span>Last Assessment:</span>
                                <span className="font-semibold">
                                    {new Date(standard.lastAssessment).toLocaleDateString()}
                                </span>
                            </div>
                        </div>
                        <div className="flex gap-2 mt-4">
                            <button
                                onClick={() => handleGenerateReport(standard.standard)}
                                disabled={generatingReport}
                                className="flex-1 px-3 py-2 bg-white dark:bg-neutral-800 border border-slate-300 dark:border-neutral-600 rounded text-sm hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50"
                            >
                                📄 Report
                            </button>
                            <button
                                onClick={() => handleRunAssessment(standard.standard)}
                                disabled={runningAssessment}
                                className="flex-1 px-3 py-2 bg-white dark:bg-neutral-800 border border-slate-300 dark:border-neutral-600 rounded text-sm hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50"
                            >
                                🔍 Assess
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* Metrics Grid */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Critical Issues</p>
                            <p className="text-3xl font-bold text-red-600 mt-1">{dashboard.criticalIssues}</p>
                        </div>
                        <div className="text-4xl">🚨</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Open Issues</p>
                            <p className="text-3xl font-bold text-orange-600 mt-1">{dashboard.openIssues}</p>
                        </div>
                        <div className="text-4xl">⚠️</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Recent Audits</p>
                            <p className="text-3xl font-bold text-blue-600 mt-1">{dashboard.recentAudits}</p>
                        </div>
                        <div className="text-4xl">📊</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Deadlines</p>
                            <p className="text-3xl font-bold text-purple-600 mt-1">
                                {dashboard.upcomingDeadlines.length}
                            </p>
                        </div>
                        <div className="text-4xl">📅</div>
                    </div>
                </div>
            </div>

            {/* Two Column Layout */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Upcoming Deadlines */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4">Upcoming Deadlines</h3>
                    {dashboard.upcomingDeadlines.length === 0 ? (
                        <p className="text-sm text-slate-600 dark:text-neutral-400">No upcoming deadlines</p>
                    ) : (
                        <div className="space-y-3">
                            {dashboard.upcomingDeadlines.map((deadline, index) => (
                                <div key={index} className="flex items-start justify-between border-b pb-2">
                                    <div className="flex-1">
                                        <p className="text-sm font-medium">{deadline.title}</p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                                            Due: {new Date(deadline.dueDate).toLocaleDateString()}
                                        </p>
                                    </div>
                                    <span
                                        className={`text-xs px-2 py-1 rounded ${getSeverityColor(
                                            deadline.severity
                                        )}`}
                                    >
                                        {deadline.severity}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Recent Activity */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4">Recent Activity</h3>
                    {dashboard.recentActivity.length === 0 ? (
                        <p className="text-sm text-slate-600 dark:text-neutral-400">No recent activity</p>
                    ) : (
                        <div className="space-y-3">
                            {dashboard.recentActivity.slice(0, 5).map((activity, index) => (
                                <div key={index} className="flex items-start gap-3 border-b pb-2">
                                    <span
                                        className={`text-xs px-2 py-1 rounded ${getSeverityColor(
                                            activity.severity
                                        )}`}
                                    >
                                        {activity.type.split('.')[1]}
                                    </span>
                                    <div className="flex-1">
                                        <p className="text-sm">{activity.description}</p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                                            {new Date(activity.timestamp).toLocaleString()}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* Compliance Trends (Simple bar chart with ASCII for now) */}
            <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                <h3 className="text-lg font-semibold mb-4">Compliance Trend (Last 30 Days)</h3>
                <div className="space-y-2">
                    {dashboard.trends.slice(-7).map((trend, index) => (
                        <div key={index} className="flex items-center gap-3">
                            <span className="text-xs w-16 text-slate-600 dark:text-neutral-400">
                                {new Date(trend.date).toLocaleDateString('en-US', {
                                    month: 'short',
                                    day: 'numeric',
                                })}
                            </span>
                            <div className="flex-1 bg-slate-100 dark:bg-neutral-700 rounded-full h-6 relative">
                                <div
                                    className="bg-green-500 h-full rounded-full flex items-center justify-end pr-2"
                                    style={{ width: `${trend.overallScore}%` }}
                                >
                                    <span className="text-xs text-white font-semibold">
                                        {trend.overallScore.toFixed(0)}%
                                    </span>
                                </div>
                            </div>
                            <span className="text-xs w-20 text-right">
                                <span className="text-red-600">{trend.criticalIssues}</span> critical
                            </span>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}
