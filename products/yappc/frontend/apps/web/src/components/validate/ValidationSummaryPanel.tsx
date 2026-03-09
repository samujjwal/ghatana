/**
 * Validation Summary Panel Component
 *
 * Displays validation checklist, status, and generates validation reports.
 * Used in Preview surface with ?panel=validation query parameter.
 *
 * @doc.type component
 * @doc.purpose VALIDATE phase summary and checklist
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { CheckCircle, XCircle as Cancel, AlertTriangle as Warning, Hourglass as HourglassEmpty, Play as PlayArrow, RefreshCw as Refresh, Download, Sparkles as AutoAwesome, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import type { LifecyclePhase } from '@/shared/types/lifecycle';

export interface ValidationCheck {
    id: string;
    category: 'requirements' | 'architecture' | 'security' | 'ux' | 'performance' | 'accessibility';
    name: string;
    description: string;
    status: 'pending' | 'running' | 'passed' | 'failed' | 'warning' | 'skipped';
    details?: string;
    timestamp?: string;
    autoFix?: boolean;
}

export interface ValidationReport {
    id: string;
    createdAt: string;
    summary: {
        total: number;
        passed: number;
        failed: number;
        warnings: number;
        skipped: number;
    };
    checks: ValidationCheck[];
    recommendations: string[];
}

export interface ValidationSummaryPanelProps {
    projectId: string;
    checks: ValidationCheck[];
    lastReport?: ValidationReport | null;
    isRunning?: boolean;
    onRunValidation: () => Promise<void>;
    onRunCheck: (checkId: string) => Promise<void>;
    onAutoFix?: (checkId: string) => Promise<void>;
    onGenerateReport: () => Promise<ValidationReport>;
    onExportReport?: (report: ValidationReport, format: 'pdf' | 'json' | 'md') => Promise<void>;
    onAIAssist?: (checks: ValidationCheck[]) => Promise<{ suggestions: string[]; prioritizedFixes: string[] } | null>;
}

type CategoryType = ValidationCheck['category'];

const CATEGORY_LABELS: Record<CategoryType, { label: string; icon: string }> = {
    requirements: { label: 'Requirements', icon: '📋' },
    architecture: { label: 'Architecture', icon: '🏗️' },
    security: { label: 'Security', icon: '🔒' },
    ux: { label: 'UX/Design', icon: '🎨' },
    performance: { label: 'Performance', icon: '⚡' },
    accessibility: { label: 'Accessibility', icon: '♿' },
};

const STATUS_CONFIG: Record<ValidationCheck['status'], { icon: React.ReactNode; color: string; label: string }> = {
    pending: {
        icon: <HourglassEmpty className="w-4 h-4" />,
        color: 'text-grey-500',
        label: 'Pending',
    },
    running: {
        icon: <div className="w-4 h-4 border-2 border-primary-500 border-t-transparent rounded-full animate-spin" />,
        color: 'text-primary-500',
        label: 'Running',
    },
    passed: {
        icon: <CheckCircle className="w-4 h-4" />,
        color: 'text-green-500',
        label: 'Passed',
    },
    failed: {
        icon: <Cancel className="w-4 h-4" />,
        color: 'text-red-500',
        label: 'Failed',
    },
    warning: {
        icon: <Warning className="w-4 h-4" />,
        color: 'text-yellow-500',
        label: 'Warning',
    },
    skipped: {
        icon: <span className="w-4 h-4 text-center">—</span>,
        color: 'text-grey-400',
        label: 'Skipped',
    },
};

/**
 * Validation Summary Panel for the VALIDATE phase.
 */
export const ValidationSummaryPanel: React.FC<ValidationSummaryPanelProps> = ({
    projectId,
    checks,
    lastReport,
    isRunning = false,
    onRunValidation,
    onRunCheck,
    onAutoFix,
    onGenerateReport,
    onExportReport,
    onAIAssist,
}) => {
    const [expandedCategories, setExpandedCategories] = useState<Set<CategoryType>>(new Set(['requirements', 'security']));
    const [isGeneratingReport, setIsGeneratingReport] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [aiSuggestions, setAiSuggestions] = useState<{ suggestions: string[]; prioritizedFixes: string[] } | null>(null);
    const [currentReport, setCurrentReport] = useState<ValidationReport | null>(lastReport || null);

    // Group checks by category
    const checksByCategory = useMemo(() => {
        const grouped = new Map<CategoryType, ValidationCheck[]>();
        Object.keys(CATEGORY_LABELS).forEach((cat) => {
            grouped.set(cat as CategoryType, []);
        });
        checks.forEach((check) => {
            const categoryChecks = grouped.get(check.category) || [];
            categoryChecks.push(check);
            grouped.set(check.category, categoryChecks);
        });
        return grouped;
    }, [checks]);

    // Calculate summary stats
    const summary = useMemo(() => {
        return {
            total: checks.length,
            passed: checks.filter((c) => c.status === 'passed').length,
            failed: checks.filter((c) => c.status === 'failed').length,
            warnings: checks.filter((c) => c.status === 'warning').length,
            pending: checks.filter((c) => c.status === 'pending').length,
            running: checks.filter((c) => c.status === 'running').length,
        };
    }, [checks]);

    const overallStatus = useMemo(() => {
        if (summary.failed > 0) return 'failed';
        if (summary.warnings > 0) return 'warning';
        if (summary.pending > 0 || summary.running > 0) return 'pending';
        return 'passed';
    }, [summary]);

    const toggleCategory = useCallback((category: CategoryType) => {
        setExpandedCategories((prev) => {
            const next = new Set(prev);
            if (next.has(category)) {
                next.delete(category);
            } else {
                next.add(category);
            }
            return next;
        });
    }, []);

    const handleGenerateReport = useCallback(async () => {
        setIsGeneratingReport(true);
        try {
            const report = await onGenerateReport();
            setCurrentReport(report);
        } finally {
            setIsGeneratingReport(false);
        }
    }, [onGenerateReport]);

    const handleExportReport = useCallback(
        async (format: 'pdf' | 'json' | 'md') => {
            if (!currentReport || !onExportReport) return;
            await onExportReport(currentReport, format);
        },
        [currentReport, onExportReport],
    );

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist(checks);
            setAiSuggestions(result);
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, checks]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <CheckCircle className={`w-5 h-5 ${STATUS_CONFIG[overallStatus].color}`} />
                    <div>
                        <h3 className="font-semibold text-text-primary">Validation</h3>
                        <p className="text-xs text-text-secondary">
                            {summary.passed}/{summary.total} checks passed
                        </p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isRunning}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'AI Assist'}
                        </button>
                    )}
                    <button
                        onClick={onRunValidation}
                        disabled={isRunning}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        {isRunning ? (
                            <>
                                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                                Running...
                            </>
                        ) : (
                            <>
                                <PlayArrow className="w-4 h-4" />
                                Run All
                            </>
                        )}
                    </button>
                </div>
            </div>

            {/* Summary Stats */}
            <div className="grid grid-cols-5 gap-2 p-4 bg-grey-50 dark:bg-grey-800/50 border-b border-divider">
                <div className="text-center">
                    <div className="text-2xl font-bold text-text-primary">{summary.total}</div>
                    <div className="text-xs text-text-secondary">Total</div>
                </div>
                <div className="text-center">
                    <div className="text-2xl font-bold text-green-500">{summary.passed}</div>
                    <div className="text-xs text-text-secondary">Passed</div>
                </div>
                <div className="text-center">
                    <div className="text-2xl font-bold text-red-500">{summary.failed}</div>
                    <div className="text-xs text-text-secondary">Failed</div>
                </div>
                <div className="text-center">
                    <div className="text-2xl font-bold text-yellow-500">{summary.warnings}</div>
                    <div className="text-xs text-text-secondary">Warnings</div>
                </div>
                <div className="text-center">
                    <div className="text-2xl font-bold text-grey-400">{summary.pending}</div>
                    <div className="text-xs text-text-secondary">Pending</div>
                </div>
            </div>

            {/* Checks by Category */}
            <div className="flex-1 overflow-auto p-4 space-y-3">
                {Array.from(checksByCategory.entries()).map(([category, categoryChecks]) => {
                    if (categoryChecks.length === 0) return null;
                    const isExpanded = expandedCategories.has(category);
                    const categoryPassed = categoryChecks.filter((c) => c.status === 'passed').length;
                    const categoryFailed = categoryChecks.filter((c) => c.status === 'failed').length;

                    return (
                        <div key={category} className="border border-divider rounded-lg bg-bg-paper overflow-hidden">
                            <button
                                onClick={() => toggleCategory(category)}
                                className="w-full flex items-center justify-between px-4 py-3 hover:bg-grey-50 dark:hover:bg-grey-800/50 transition-colors"
                            >
                                <div className="flex items-center gap-2">
                                    <span>{CATEGORY_LABELS[category].icon}</span>
                                    <span className="font-medium text-text-primary">
                                        {CATEGORY_LABELS[category].label}
                                    </span>
                                    <span className="text-xs text-text-secondary">
                                        ({categoryPassed}/{categoryChecks.length})
                                    </span>
                                </div>
                                <div className="flex items-center gap-2">
                                    {categoryFailed > 0 && (
                                        <span className="px-2 py-0.5 text-xs bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300 rounded">
                                            {categoryFailed} failed
                                        </span>
                                    )}
                                    {isExpanded ? (
                                        <ExpandLess className="w-4 h-4 text-text-secondary" />
                                    ) : (
                                        <ExpandMore className="w-4 h-4 text-text-secondary" />
                                    )}
                                </div>
                            </button>
                            {isExpanded && (
                                <div className="border-t border-divider divide-y divide-divider">
                                    {categoryChecks.map((check) => (
                                        <div
                                            key={check.id}
                                            className={`px-4 py-3 flex items-start gap-3 ${check.status === 'failed'
                                                    ? 'bg-red-50/50 dark:bg-red-900/10'
                                                    : check.status === 'warning'
                                                        ? 'bg-yellow-50/50 dark:bg-yellow-900/10'
                                                        : ''
                                                }`}
                                        >
                                            <div className={STATUS_CONFIG[check.status].color}>
                                                {STATUS_CONFIG[check.status].icon}
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center justify-between gap-2">
                                                    <span className="font-medium text-sm text-text-primary">
                                                        {check.name}
                                                    </span>
                                                    <span className={`text-xs ${STATUS_CONFIG[check.status].color}`}>
                                                        {STATUS_CONFIG[check.status].label}
                                                    </span>
                                                </div>
                                                <p className="text-xs text-text-secondary mt-0.5">
                                                    {check.description}
                                                </p>
                                                {check.details && (
                                                    <p className="text-xs text-text-primary mt-1 p-2 bg-grey-100 dark:bg-grey-800 rounded">
                                                        {check.details}
                                                    </p>
                                                )}
                                                <div className="flex items-center gap-2 mt-2">
                                                    <button
                                                        onClick={() => onRunCheck(check.id)}
                                                        disabled={isRunning || check.status === 'running'}
                                                        className="text-xs text-primary-600 hover:text-primary-700 disabled:opacity-50"
                                                    >
                                                        <Refresh className="w-3 h-3 inline mr-1" />
                                                        Re-run
                                                    </button>
                                                    {check.autoFix && check.status === 'failed' && onAutoFix && (
                                                        <button
                                                            onClick={() => onAutoFix(check.id)}
                                                            className="text-xs text-green-600 hover:text-green-700"
                                                        >
                                                            <AutoAwesome className="w-3 h-3 inline mr-1" />
                                                            Auto-fix
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    );
                })}

                {/* AI Suggestions */}
                {aiSuggestions && (
                    <div className="p-4 border border-primary-200 dark:border-primary-800 rounded-lg bg-primary-50 dark:bg-primary-900/20 space-y-3">
                        <h4 className="font-medium text-text-primary flex items-center gap-2">
                            <AutoAwesome className="w-4 h-4 text-primary-600" /> AI Analysis
                        </h4>
                        {aiSuggestions.prioritizedFixes.length > 0 && (
                            <div>
                                <h5 className="text-xs font-medium text-text-secondary mb-1">Prioritized Fixes</h5>
                                <ol className="list-decimal list-inside text-sm text-text-primary space-y-1">
                                    {aiSuggestions.prioritizedFixes.map((fix, idx) => (
                                        <li key={idx}>{fix}</li>
                                    ))}
                                </ol>
                            </div>
                        )}
                        {aiSuggestions.suggestions.length > 0 && (
                            <div>
                                <h5 className="text-xs font-medium text-text-secondary mb-1">Suggestions</h5>
                                <ul className="list-disc list-inside text-sm text-text-primary space-y-1">
                                    {aiSuggestions.suggestions.map((suggestion, idx) => (
                                        <li key={idx}>{suggestion}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        <button
                            onClick={() => setAiSuggestions(null)}
                            className="text-xs text-text-secondary hover:text-text-primary"
                        >
                            Dismiss
                        </button>
                    </div>
                )}
            </div>

            {/* Report Footer */}
            <div className="px-4 py-3 border-t border-divider bg-grey-50 dark:bg-grey-800/50 flex items-center justify-between">
                <div className="text-xs text-text-secondary">
                    {currentReport && (
                        <>Last report: {new Date(currentReport.createdAt).toLocaleString()}</>
                    )}
                </div>
                <div className="flex gap-2">
                    <button
                        onClick={handleGenerateReport}
                        disabled={isGeneratingReport || isRunning}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded transition-colors disabled:opacity-50"
                    >
                        {isGeneratingReport ? 'Generating...' : 'Generate Report'}
                    </button>
                    {currentReport && onExportReport && (
                        <div className="flex gap-1">
                            <button
                                onClick={() => handleExportReport('pdf')}
                                className="px-2 py-1 text-xs text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors"
                            >
                                <Download className="w-3 h-3 inline mr-1" />
                                PDF
                            </button>
                            <button
                                onClick={() => handleExportReport('md')}
                                className="px-2 py-1 text-xs text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors"
                            >
                                <Download className="w-3 h-3 inline mr-1" />
                                MD
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ValidationSummaryPanel;
