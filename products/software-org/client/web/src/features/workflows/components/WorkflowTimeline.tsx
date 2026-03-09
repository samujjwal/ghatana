import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Workflow execution step interface.
 */
export interface WorkflowExecutionStep {
    stepId: string;
    nodeName: string;
    startTime: Date;
    endTime?: Date;
    status: 'pending' | 'running' | 'completed' | 'failed';
    duration?: number;
    errorMessage?: string;
}

/**
 * Workflow Timeline Props interface.
 */
export interface WorkflowTimelineProps {
    steps: WorkflowExecutionStep[];
    workflowId: string;
    onStepClick?: (step: WorkflowExecutionStep) => void;
    isLive?: boolean;
}

/**
 * Workflow Timeline - Displays execution flow and timing of workflow steps.
 *
 * <p><b>Purpose</b><br>
 * Visualizes workflow execution with step-by-step timeline, durations, and status indicators.
 *
 * <p><b>Features</b><br>
 * - Vertical timeline of execution steps
 * - Status indicators (pending/running/completed/failed)
 * - Duration tracking for each step
 * - Live execution animation for running steps
 * - Error message display for failed steps
 * - Step drill-down
 * - Dark mode support
 * - Responsive layout
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <WorkflowTimeline 
 *   steps={executionSteps}
 *   workflowId="wf_123"
 *   onStepClick={handleStepClick}
 *   isLive={true}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Workflow execution timeline
 * @doc.layer product
 * @doc.pattern Organism
 */
export const WorkflowTimeline = React.memo(
    ({ steps, workflowId, onStepClick, isLive }: WorkflowTimelineProps) => {
        const statusColors = {
            pending: 'bg-slate-100 border-slate-300 dark:bg-neutral-800 dark:border-neutral-600',
            running: 'bg-blue-100 border-blue-300 dark:bg-blue-900/30 dark:border-blue-700 animate-pulse',
            completed: 'bg-green-100 border-green-300 dark:bg-green-900/30 dark:border-green-700',
            failed: 'bg-red-100 border-red-300 dark:bg-red-900/30 dark:border-red-700',
        };

        const statusTextColors = {
            pending: 'text-slate-700 dark:text-neutral-300',
            running: 'text-blue-700 dark:text-blue-300',
            completed: 'text-green-700 dark:text-green-300',
            failed: 'text-red-700 dark:text-red-300',
        };

        const statusDotColors = {
            pending: 'bg-slate-400 dark:bg-slate-600',
            running: 'bg-blue-500 dark:bg-blue-400',
            completed: 'bg-green-500 dark:bg-green-400',
            failed: 'bg-red-500 dark:bg-red-400',
        };

        const statusIcons = {
            pending: '⏱',
            running: '⟳',
            completed: '✓',
            failed: '✕',
        };

        const formatDuration = (ms: number) => {
            if (ms < 1000) return `${Math.round(ms)}ms`;
            if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
            return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
        };

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        Execution Timeline
                    </h3>
                    {isLive && (
                        <div className="flex items-center gap-2 text-sm">
                            <span className="inline-block w-2 h-2 rounded-full bg-red-500 animate-pulse" />
                            <span className="text-slate-600 dark:text-neutral-400">Live</span>
                        </div>
                    )}
                </div>

                {steps.length === 0 ? (
                    <p className="text-center text-slate-500 dark:text-neutral-400 py-8">
                        No execution steps yet
                    </p>
                ) : (
                    <div className="relative">
                        {/* Vertical line */}
                        <div className="absolute left-4 top-8 bottom-8 w-0.5 bg-gradient-to-b from-slate-300 to-transparent dark:from-slate-600 dark:to-transparent" />

                        {/* Steps */}
                        <div className="space-y-4">
                            {steps.map((step, index) => (
                                <div
                                    key={step.stepId}
                                    onClick={() => onStepClick?.(step)}
                                    role={onStepClick ? 'button' : undefined}
                                    tabIndex={onStepClick ? 0 : undefined}
                                    onKeyDown={(e) => {
                                        if ((e.key === 'Enter' || e.key === ' ') && onStepClick) {
                                            onStepClick(step);
                                        }
                                    }}
                                    className={`relative pl-16 ${onStepClick
                                        ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 -mx-2 px-4 py-2 rounded transition-colors'
                                        : ''
                                        }`}
                                >
                                    {/* Status dot */}
                                    <div className="absolute left-0 top-1">
                                        <div
                                            className={`relative w-10 h-10 rounded-full border-4 flex items-center justify-center text-lg font-bold ${statusColors[step.status]
                                                } ${statusTextColors[step.status]}`}
                                        >
                                            <div
                                                className={`absolute inset-1 rounded-full ${statusDotColors[step.status]
                                                    }`}
                                            />
                                            <span className="relative z-10">{statusIcons[step.status]}</span>
                                        </div>
                                    </div>

                                    {/* Step content */}
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <h4 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {step.nodeName}
                                            </h4>
                                            <div className="flex items-center gap-3 mt-1 text-sm text-slate-600 dark:text-neutral-400">
                                                <time>
                                                    {step.startTime.toLocaleTimeString()}
                                                </time>
                                                {step.duration && (
                                                    <>
                                                        <span>•</span>
                                                        <span>{formatDuration(step.duration)}</span>
                                                    </>
                                                )}
                                            </div>
                                            {step.errorMessage && (
                                                <p className="mt-2 text-sm text-red-600 dark:text-rose-400 font-medium">
                                                    {step.errorMessage}
                                                </p>
                                            )}
                                        </div>
                                        <span>
                                            <StatusBadge status={step.status} />
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Total execution time */}
                {steps.length > 0 && (
                    <div className="mt-6 pt-6 border-t border-slate-200 dark:border-neutral-600">
                        <div className="flex items-center justify-between text-sm">
                            <span className="font-medium text-slate-600 dark:text-neutral-400">
                                Total Execution Time
                            </span>
                            {steps[steps.length - 1]?.endTime && (
                                <span className="font-mono font-bold text-slate-900 dark:text-neutral-100">
                                    {formatDuration(
                                        steps[steps.length - 1].endTime!.getTime() - steps[0].startTime.getTime()
                                    )}
                                </span>
                            )}
                        </div>
                    </div>
                )}
            </div>
        );
    }
);

WorkflowTimeline.displayName = 'WorkflowTimeline';

export default WorkflowTimeline;
