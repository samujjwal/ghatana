/**
 * Build Progress Tracker Component
 *
 * Real-time build and deployment progress tracking.
 * Used in Deploy surface Deployments segment.
 *
 * @doc.type component
 * @doc.purpose RUN phase build progress visualization
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Hammer as Build, CheckCircle, XCircle as Cancel, Hourglass as HourglassEmpty, RefreshCw as Refresh, Terminal, ChevronDown as ExpandMore, ChevronUp as ExpandLess, Download } from 'lucide-react';

export interface BuildStep {
    id: string;
    name: string;
    status: 'pending' | 'running' | 'success' | 'failed' | 'skipped';
    startedAt?: string;
    completedAt?: string;
    duration?: number;
    logs?: string[];
    artifacts?: { name: string; url: string; size?: string }[];
}

export interface BuildInfo {
    id: string;
    version: string;
    branch: string;
    commit: string;
    commitMessage?: string;
    triggeredBy: string;
    triggeredAt: string;
    status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled';
    steps: BuildStep[];
    environment?: string;
}

export interface BuildProgressTrackerProps {
    build: BuildInfo;
    onRefresh: () => Promise<void>;
    onCancel?: () => Promise<void>;
    onRetry?: () => Promise<void>;
    onDownloadArtifact?: (artifact: { name: string; url: string }) => Promise<void>;
    isPolling?: boolean;
}

const STATUS_CONFIG: Record<string, { icon: React.ReactNode; color: string; bgColor: string }> = {
    pending: {
        icon: <HourglassEmpty className="w-4 h-4" />,
        color: 'text-grey-500',
        bgColor: 'bg-grey-100 dark:bg-grey-800',
    },
    running: {
        icon: <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />,
        color: 'text-blue-500',
        bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    },
    success: {
        icon: <CheckCircle className="w-4 h-4" />,
        color: 'text-green-500',
        bgColor: 'bg-green-100 dark:bg-green-900/30',
    },
    failed: {
        icon: <Cancel className="w-4 h-4" />,
        color: 'text-red-500',
        bgColor: 'bg-red-100 dark:bg-red-900/30',
    },
    skipped: {
        icon: <span className="w-4 h-4 text-grey-400 text-center text-sm">—</span>,
        color: 'text-grey-400',
        bgColor: 'bg-grey-50 dark:bg-grey-900',
    },
    cancelled: {
        icon: <Cancel className="w-4 h-4" />,
        color: 'text-orange-500',
        bgColor: 'bg-orange-100 dark:bg-orange-900/30',
    },
};

function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
}

function formatTime(isoString: string): string {
    return new Date(isoString).toLocaleTimeString();
}

/**
 * Build Progress Tracker for RUN phase.
 */
export const BuildProgressTracker: React.FC<BuildProgressTrackerProps> = ({
    build,
    onRefresh,
    onCancel,
    onRetry,
    onDownloadArtifact,
    isPolling = false,
}) => {
    const [expandedSteps, setExpandedSteps] = useState<Set<string>>(new Set());
    const [isRefreshing, setIsRefreshing] = useState(false);

    // Auto-expand first running or failed step
    useEffect(() => {
        const runningStep = build.steps.find((s) => s.status === 'running');
        const failedStep = build.steps.find((s) => s.status === 'failed');
        if (runningStep) {
            setExpandedSteps((prev) => new Set([...prev, runningStep.id]));
        } else if (failedStep) {
            setExpandedSteps((prev) => new Set([...prev, failedStep.id]));
        }
    }, [build.steps]);

    const toggleStep = useCallback((stepId: string) => {
        setExpandedSteps((prev) => {
            const next = new Set(prev);
            if (next.has(stepId)) {
                next.delete(stepId);
            } else {
                next.add(stepId);
            }
            return next;
        });
    }, []);

    const handleRefresh = useCallback(async () => {
        setIsRefreshing(true);
        try {
            await onRefresh();
        } finally {
            setIsRefreshing(false);
        }
    }, [onRefresh]);

    const completedSteps = build.steps.filter((s) => s.status === 'success' || s.status === 'failed' || s.status === 'skipped').length;
    const progress = build.steps.length > 0 ? Math.round((completedSteps / build.steps.length) * 100) : 0;
    const totalDuration = build.steps.reduce((sum, s) => sum + (s.duration || 0), 0);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${STATUS_CONFIG[build.status].bgColor}`}>
                        <Build className={`w-5 h-5 ${STATUS_CONFIG[build.status].color}`} />
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <h3 className="font-semibold text-text-primary">Build #{build.id}</h3>
                            <span className={`text-xs ${STATUS_CONFIG[build.status].color} font-medium`}>
                                {build.status.toUpperCase()}
                            </span>
                        </div>
                        <p className="text-xs text-text-secondary">
                            v{build.version} • {build.branch} • {build.commit.substring(0, 7)}
                        </p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {build.status === 'running' && onCancel && (
                        <button
                            onClick={onCancel}
                            className="px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                        >
                            Cancel
                        </button>
                    )}
                    {build.status === 'failed' && onRetry && (
                        <button
                            onClick={onRetry}
                            className="px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors"
                        >
                            Retry
                        </button>
                    )}
                    <button
                        onClick={handleRefresh}
                        disabled={isRefreshing}
                        className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors disabled:opacity-50"
                    >
                        <Refresh className={`w-4 h-4 ${isRefreshing || isPolling ? 'animate-spin' : ''}`} />
                    </button>
                </div>
            </div>

            {/* Progress Bar */}
            <div className="px-4 py-3 bg-grey-50 dark:bg-grey-800/50 border-b border-divider">
                <div className="flex items-center justify-between text-xs text-text-secondary mb-2">
                    <span>
                        {completedSteps}/{build.steps.length} steps
                    </span>
                    <span>{formatDuration(totalDuration)}</span>
                </div>
                <div className="h-2 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                    <div
                        className={`h-full transition-all duration-500 ${build.status === 'failed'
                                ? 'bg-red-500'
                                : build.status === 'success'
                                    ? 'bg-green-500'
                                    : 'bg-blue-500'
                            }`}
                        style={{ width: `${progress}%` }}
                    />
                </div>
            </div>

            {/* Build Info */}
            <div className="px-4 py-3 border-b border-divider flex flex-wrap gap-4 text-xs">
                <div>
                    <span className="text-text-secondary">Triggered by:</span>{' '}
                    <span className="text-text-primary font-medium">{build.triggeredBy}</span>
                </div>
                <div>
                    <span className="text-text-secondary">Started:</span>{' '}
                    <span className="text-text-primary">{formatTime(build.triggeredAt)}</span>
                </div>
                {build.environment && (
                    <div>
                        <span className="text-text-secondary">Environment:</span>{' '}
                        <span className="text-text-primary font-medium">{build.environment}</span>
                    </div>
                )}
            </div>

            {/* Steps */}
            <div className="flex-1 overflow-auto p-4">
                <div className="space-y-2">
                    {build.steps.map((step, idx) => (
                        <div
                            key={step.id}
                            className={`border border-divider rounded-lg overflow-hidden ${step.status === 'running'
                                    ? 'ring-2 ring-blue-500/50'
                                    : step.status === 'failed'
                                        ? 'ring-2 ring-red-500/50'
                                        : ''
                                }`}
                        >
                            <button
                                onClick={() => toggleStep(step.id)}
                                className={`w-full flex items-center gap-3 p-3 text-left transition-colors ${STATUS_CONFIG[step.status].bgColor}`}
                            >
                                <div className="w-6 h-6 flex items-center justify-center bg-bg-paper rounded-full text-xs font-medium text-text-secondary">
                                    {idx + 1}
                                </div>
                                <div className={STATUS_CONFIG[step.status].color}>
                                    {STATUS_CONFIG[step.status].icon}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <span className="font-medium text-sm text-text-primary">{step.name}</span>
                                </div>
                                {step.duration !== undefined && (
                                    <span className="text-xs text-text-secondary">
                                        {formatDuration(step.duration)}
                                    </span>
                                )}
                                {expandedSteps.has(step.id) ? (
                                    <ExpandLess className="w-4 h-4 text-text-secondary" />
                                ) : (
                                    <ExpandMore className="w-4 h-4 text-text-secondary" />
                                )}
                            </button>

                            {expandedSteps.has(step.id) && (
                                <div className="border-t border-divider bg-bg-paper">
                                    {/* Logs */}
                                    {step.logs && step.logs.length > 0 && (
                                        <div className="p-3 bg-grey-900 text-grey-100 font-mono text-xs max-h-48 overflow-auto">
                                            {step.logs.map((log, logIdx) => (
                                                <div key={logIdx} className="flex gap-2">
                                                    <span className="text-grey-500 select-none">{logIdx + 1}</span>
                                                    <span className="break-all">{log}</span>
                                                </div>
                                            ))}
                                        </div>
                                    )}

                                    {/* Artifacts */}
                                    {step.artifacts && step.artifacts.length > 0 && (
                                        <div className="p-3 border-t border-divider">
                                            <div className="flex items-center gap-2 mb-2">
                                                <Terminal className="w-4 h-4 text-text-secondary" />
                                                <span className="text-xs font-medium text-text-secondary">
                                                    Artifacts
                                                </span>
                                            </div>
                                            <div className="space-y-1">
                                                {step.artifacts.map((artifact, aIdx) => (
                                                    <button
                                                        key={aIdx}
                                                        onClick={() => onDownloadArtifact?.(artifact)}
                                                        className="flex items-center gap-2 w-full p-2 text-left text-xs bg-grey-50 dark:bg-grey-800/50 rounded hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
                                                    >
                                                        <Download className="w-3 h-3 text-primary-600" />
                                                        <span className="flex-1 text-text-primary truncate">
                                                            {artifact.name}
                                                        </span>
                                                        {artifact.size && (
                                                            <span className="text-text-secondary">
                                                                {artifact.size}
                                                            </span>
                                                        )}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    {/* Timestamps */}
                                    {(step.startedAt || step.completedAt) && (
                                        <div className="px-3 py-2 border-t border-divider flex gap-4 text-xs text-text-secondary">
                                            {step.startedAt && <span>Started: {formatTime(step.startedAt)}</span>}
                                            {step.completedAt && <span>Completed: {formatTime(step.completedAt)}</span>}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            </div>

            {/* Commit Message */}
            {build.commitMessage && (
                <div className="px-4 py-3 border-t border-divider bg-grey-50 dark:bg-grey-800/50">
                    <div className="text-xs text-text-secondary mb-1">Commit Message</div>
                    <div className="text-sm text-text-primary">{build.commitMessage}</div>
                </div>
            )}
        </div>
    );
};

export default BuildProgressTracker;
