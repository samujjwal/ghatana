/**
 * Validation Run Dialog Component
 *
 * Modal dialog for running validation with progress and results.
 *
 * @doc.type component
 * @doc.purpose VALIDATE phase validation execution dialog
 * @doc.layer product
 * @doc.pattern Dialog Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import { X as Close, Play as PlayArrow, CheckCircle, XCircle as Cancel, AlertTriangle as Warning, Hourglass as HourglassEmpty } from 'lucide-react';

export interface ValidationRunStep {
    id: string;
    name: string;
    status: 'pending' | 'running' | 'passed' | 'failed' | 'warning' | 'skipped';
    message?: string;
    duration?: number;
}

export interface ValidationRunDialogProps {
    isOpen: boolean;
    onClose: () => void;
    steps: ValidationRunStep[];
    onStart: () => Promise<void>;
    onCancel: () => void;
    isRunning: boolean;
    progress: number; // 0-100
    title?: string;
}

const STATUS_ICONS: Record<ValidationRunStep['status'], React.ReactNode> = {
    pending: <HourglassEmpty className="w-5 h-5 text-grey-400" />,
    running: <div className="w-5 h-5 border-2 border-primary-500 border-t-transparent rounded-full animate-spin" />,
    passed: <CheckCircle className="w-5 h-5 text-green-500" />,
    failed: <Cancel className="w-5 h-5 text-red-500" />,
    warning: <Warning className="w-5 h-5 text-yellow-500" />,
    skipped: <span className="w-5 h-5 text-grey-400 text-center">—</span>,
};

/**
 * Validation Run Dialog for executing validation checks.
 */
export const ValidationRunDialog: React.FC<ValidationRunDialogProps> = ({
    isOpen,
    onClose,
    steps,
    onStart,
    onCancel,
    isRunning,
    progress,
    title = 'Run Validation',
}) => {
    const [hasStarted, setHasStarted] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setHasStarted(false);
        }
    }, [isOpen]);

    const handleStart = useCallback(async () => {
        setHasStarted(true);
        await onStart();
    }, [onStart]);

    const handleClose = useCallback(() => {
        if (isRunning) {
            onCancel();
        }
        onClose();
    }, [isRunning, onCancel, onClose]);

    const completedSteps = steps.filter((s) => s.status === 'passed' || s.status === 'failed' || s.status === 'warning' || s.status === 'skipped').length;
    const passedSteps = steps.filter((s) => s.status === 'passed').length;
    const failedSteps = steps.filter((s) => s.status === 'failed').length;
    const warningSteps = steps.filter((s) => s.status === 'warning').length;

    const isComplete = hasStarted && !isRunning && completedSteps === steps.length;

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 transition-opacity"
                onClick={handleClose}
            />

            {/* Dialog */}
            <div className="relative w-full max-w-lg mx-4 bg-bg-paper rounded-xl shadow-xl overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-divider">
                    <h2 className="text-lg font-semibold text-text-primary">{title}</h2>
                    <button
                        onClick={handleClose}
                        className="p-1 text-text-secondary hover:text-text-primary rounded transition-colors"
                    >
                        <Close className="w-5 h-5" />
                    </button>
                </div>

                {/* Progress Bar */}
                {hasStarted && (
                    <div className="h-1 bg-grey-200 dark:bg-grey-700">
                        <div
                            className={`h-full transition-all duration-300 ${failedSteps > 0
                                    ? 'bg-red-500'
                                    : warningSteps > 0
                                        ? 'bg-yellow-500'
                                        : 'bg-green-500'
                                }`}
                            style={{ width: `${progress}%` }}
                        />
                    </div>
                )}

                {/* Content */}
                <div className="p-6">
                    {!hasStarted ? (
                        <div className="text-center py-4">
                            <div className="mb-4">
                                <PlayArrow className="w-16 h-16 mx-auto text-primary-500" />
                            </div>
                            <h3 className="text-lg font-medium text-text-primary mb-2">
                                Ready to Validate
                            </h3>
                            <p className="text-sm text-text-secondary mb-4">
                                {steps.length} validation checks will be executed.
                            </p>
                            <button
                                onClick={handleStart}
                                className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
                            >
                                Start Validation
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {/* Summary Stats */}
                            {isComplete && (
                                <div className="grid grid-cols-4 gap-2 p-4 bg-grey-50 dark:bg-grey-800/50 rounded-lg mb-4">
                                    <div className="text-center">
                                        <div className="text-xl font-bold text-text-primary">{steps.length}</div>
                                        <div className="text-xs text-text-secondary">Total</div>
                                    </div>
                                    <div className="text-center">
                                        <div className="text-xl font-bold text-green-500">{passedSteps}</div>
                                        <div className="text-xs text-text-secondary">Passed</div>
                                    </div>
                                    <div className="text-center">
                                        <div className="text-xl font-bold text-red-500">{failedSteps}</div>
                                        <div className="text-xs text-text-secondary">Failed</div>
                                    </div>
                                    <div className="text-center">
                                        <div className="text-xl font-bold text-yellow-500">{warningSteps}</div>
                                        <div className="text-xs text-text-secondary">Warnings</div>
                                    </div>
                                </div>
                            )}

                            {/* Steps List */}
                            <div className="max-h-64 overflow-y-auto space-y-2">
                                {steps.map((step) => (
                                    <div
                                        key={step.id}
                                        className={`flex items-start gap-3 p-3 rounded-lg transition-colors ${step.status === 'running'
                                                ? 'bg-primary-50 dark:bg-primary-900/20'
                                                : step.status === 'failed'
                                                    ? 'bg-red-50/50 dark:bg-red-900/10'
                                                    : step.status === 'warning'
                                                        ? 'bg-yellow-50/50 dark:bg-yellow-900/10'
                                                        : step.status === 'passed'
                                                            ? 'bg-green-50/50 dark:bg-green-900/10'
                                                            : 'bg-grey-50 dark:bg-grey-800/30'
                                            }`}
                                    >
                                        <div className="flex-shrink-0 mt-0.5">
                                            {STATUS_ICONS[step.status]}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center justify-between">
                                                <span className="font-medium text-sm text-text-primary">
                                                    {step.name}
                                                </span>
                                                {step.duration !== undefined && (
                                                    <span className="text-xs text-text-secondary">
                                                        {step.duration}ms
                                                    </span>
                                                )}
                                            </div>
                                            {step.message && (
                                                <p className="text-xs text-text-secondary mt-0.5">
                                                    {step.message}
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>

                            {/* Running Status */}
                            {isRunning && (
                                <div className="text-center text-sm text-text-secondary">
                                    Running validation... {completedSteps}/{steps.length} complete
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-divider bg-grey-50 dark:bg-grey-800/50">
                    {isRunning ? (
                        <button
                            onClick={onCancel}
                            className="px-4 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                        >
                            Cancel
                        </button>
                    ) : isComplete ? (
                        <>
                            {failedSteps > 0 && (
                                <button
                                    onClick={handleStart}
                                    className="px-4 py-2 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors"
                                >
                                    Run Again
                                </button>
                            )}
                            <button
                                onClick={onClose}
                                className="px-4 py-2 text-sm bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
                            >
                                Done
                            </button>
                        </>
                    ) : hasStarted ? null : (
                        <button
                            onClick={onClose}
                            className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        >
                            Cancel
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ValidationRunDialog;
