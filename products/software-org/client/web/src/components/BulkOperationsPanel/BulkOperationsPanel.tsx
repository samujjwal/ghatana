/**
 * Bulk Operations Panel Component
 * 
 * Provides UI for:
 * - Selecting bulk operation type
 * - Configuring operation parameters
 * - Previewing operation before execution
 * - Executing with progress tracking
 * - Viewing results
 * - Undo/redo functionality
 * - Operation history
 */

import React, { useState } from 'react';
import { useBulkOperation, useBulkOperationPreview, useUndoRedo } from '@/hooks/useBulkOperations';
import {
    BulkOperationType,
    BulkOperationStatus,
    BulkOperationPreview,
    BulkOperationResult,
    ValidationSeverity,
} from '@/types/bulkOperations';

interface BulkOperationsPanelProps {
    availableRoles?: Array<{ roleId: string; displayName: string }>;
    availablePermissions?: Array<{ permissionId: string; displayName: string }>;
    onClose?: () => void;
}

export function BulkOperationsPanel({
    availableRoles = [],
    availablePermissions = [],
    onClose,
}: BulkOperationsPanelProps) {
    const [operationType, setOperationType] = useState<BulkOperationType | null>(null);
    const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
    const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
    const [showPreview, setShowPreview] = useState(false);

    const { execute, progress, result, loading: executing, error: executeError } = useBulkOperation();
    const { preview, previewData, loading: previewing, error: previewError } = useBulkOperationPreview();
    const { undo, redo, canUndo, canRedo, loading: undoing } = useUndoRedo();

    const handlePreview = async () => {
        if (!operationType) return;

        const params = getOperationParams();
        if (!params) return;

        await preview({ type: operationType, params });
        setShowPreview(true);
    };

    const handleExecute = async () => {
        if (!operationType) return;

        const params = getOperationParams();
        if (!params) return;

        await execute({ type: operationType, params });
        setShowPreview(false);
    };

    const getOperationParams = () => {
        switch (operationType) {
            case BulkOperationType.ASSIGN_PERMISSIONS:
            case BulkOperationType.REVOKE_PERMISSIONS:
                return {
                    roleIds: selectedRoles,
                    permissionIds: selectedPermissions,
                    metadata: {
                        requestedBy: 'current-user',
                        reason: 'Bulk operation via UI',
                    },
                };
            case BulkOperationType.UPDATE_ROLES:
                return {
                    roleIds: selectedRoles,
                    updates: {
                        isActive: true,
                    },
                };
            default:
                return null;
        }
    };

    const handleReset = () => {
        setOperationType(null);
        setSelectedRoles([]);
        setSelectedPermissions([]);
        setShowPreview(false);
    };

    return (
        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between border-b border-slate-200 dark:border-neutral-600 pb-4">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">Bulk Operations</h2>
                    <p className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
                        Perform operations on multiple roles or permissions at once
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={undo}
                        disabled={!canUndo || undoing}
                        className="px-3 py-1.5 text-sm font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-neutral-700 rounded hover:bg-slate-200 dark:hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
                        title="Undo last operation"
                    >
                        ↶ Undo
                    </button>
                    <button
                        onClick={redo}
                        disabled={!canRedo || undoing}
                        className="px-3 py-1.5 text-sm font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-neutral-700 rounded hover:bg-slate-200 dark:hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
                        title="Redo last operation"
                    >
                        ↷ Redo
                    </button>
                    {onClose && (
                        <button
                            onClick={onClose}
                            className="px-3 py-1.5 text-sm font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-neutral-700 rounded hover:bg-slate-200 dark:hover:bg-slate-600"
                        >
                            ✕ Close
                        </button>
                    )}
                </div>
            </div>

            {/* Operation Type Selection */}
            {!operationType && (
                <div className="space-y-4">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        Select Operation Type
                    </h3>
                    <div className="grid grid-cols-2 gap-4">
                        <OperationTypeCard
                            title="Assign Permissions"
                            description="Add permissions to multiple roles at once"
                            icon="➕"
                            onClick={() => setOperationType(BulkOperationType.ASSIGN_PERMISSIONS)}
                        />
                        <OperationTypeCard
                            title="Revoke Permissions"
                            description="Remove permissions from multiple roles"
                            icon="➖"
                            onClick={() => setOperationType(BulkOperationType.REVOKE_PERMISSIONS)}
                        />
                        <OperationTypeCard
                            title="Update Roles"
                            description="Modify properties of multiple roles"
                            icon="✏️"
                            onClick={() => setOperationType(BulkOperationType.UPDATE_ROLES)}
                        />
                        <OperationTypeCard
                            title="Clone Role"
                            description="Create a copy of an existing role"
                            icon="📋"
                            onClick={() => setOperationType(BulkOperationType.CLONE_ROLE)}
                        />
                    </div>
                </div>
            )}

            {/* Configuration Form */}
            {operationType && !showPreview && !result && (
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                            {getOperationTitle(operationType)}
                        </h3>
                        <button
                            onClick={handleReset}
                            className="text-sm text-blue-600 dark:text-indigo-400 hover:underline"
                        >
                            ← Back
                        </button>
                    </div>

                    {/* Role Selection */}
                    {(operationType === BulkOperationType.ASSIGN_PERMISSIONS ||
                        operationType === BulkOperationType.REVOKE_PERMISSIONS ||
                        operationType === BulkOperationType.UPDATE_ROLES) && (
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Select Roles ({selectedRoles.length} selected)
                                </label>
                                <div className="max-h-60 overflow-y-auto border border-slate-300 dark:border-neutral-600 rounded-lg p-3 space-y-2">
                                    {availableRoles.map((role) => (
                                        <label
                                            key={role.roleId}
                                            className="flex items-center gap-2 p-2 hover:bg-slate-50 dark:hover:bg-slate-700 rounded cursor-pointer"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={selectedRoles.includes(role.roleId)}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setSelectedRoles([...selectedRoles, role.roleId]);
                                                    } else {
                                                        setSelectedRoles(selectedRoles.filter((id) => id !== role.roleId));
                                                    }
                                                }}
                                                className="rounded border-slate-300 dark:border-neutral-600"
                                            />
                                            <span className="text-sm text-slate-900 dark:text-neutral-100">{role.displayName}</span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}

                    {/* Permission Selection */}
                    {(operationType === BulkOperationType.ASSIGN_PERMISSIONS ||
                        operationType === BulkOperationType.REVOKE_PERMISSIONS) && (
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Select Permissions ({selectedPermissions.length} selected)
                                </label>
                                <div className="max-h-60 overflow-y-auto border border-slate-300 dark:border-neutral-600 rounded-lg p-3 space-y-2">
                                    {availablePermissions.map((perm) => (
                                        <label
                                            key={perm.permissionId}
                                            className="flex items-center gap-2 p-2 hover:bg-slate-50 dark:hover:bg-slate-700 rounded cursor-pointer"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={selectedPermissions.includes(perm.permissionId)}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setSelectedPermissions([...selectedPermissions, perm.permissionId]);
                                                    } else {
                                                        setSelectedPermissions(
                                                            selectedPermissions.filter((id) => id !== perm.permissionId)
                                                        );
                                                    }
                                                }}
                                                className="rounded border-slate-300 dark:border-neutral-600"
                                            />
                                            <span className="text-sm text-slate-900 dark:text-neutral-100">
                                                {perm.displayName}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}

                    {/* Action Buttons */}
                    <div className="flex items-center gap-3 pt-4 border-t border-slate-200 dark:border-neutral-600">
                        <button
                            onClick={handlePreview}
                            disabled={selectedRoles.length === 0 || previewing}
                            className="flex-1 px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-neutral-700 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {previewing ? 'Loading Preview...' : '👁️ Preview Changes'}
                        </button>
                        <button
                            onClick={handleExecute}
                            disabled={selectedRoles.length === 0 || executing}
                            className="flex-1 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {executing ? 'Executing...' : '✓ Execute Now'}
                        </button>
                    </div>

                    {(previewError || executeError) && (
                        <div className="p-3 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg">
                            <p className="text-sm text-red-800 dark:text-red-200">
                                {previewError?.message || executeError?.message}
                            </p>
                        </div>
                    )}
                </div>
            )}

            {/* Preview Display */}
            {showPreview && previewData && (
                <PreviewDisplay
                    preview={previewData}
                    onExecute={handleExecute}
                    onCancel={() => setShowPreview(false)}
                    executing={executing}
                />
            )}

            {/* Progress Display */}
            {progress && (
                <ProgressDisplay progress={progress} />
            )}

            {/* Result Display */}
            {result && (
                <ResultDisplay result={result} onReset={handleReset} />
            )}
        </div>
    );
}

// Operation Type Card Component
function OperationTypeCard({
    title,
    description,
    icon,
    onClick,
}: {
    title: string;
    description: string;
    icon: string;
    onClick: () => void;
}) {
    return (
        <button
            onClick={onClick}
            className="p-4 text-left border-2 border-slate-200 dark:border-neutral-600 rounded-lg hover:border-blue-500 dark:hover:border-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
        >
            <div className="text-3xl mb-2">{icon}</div>
            <h4 className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">{title}</h4>
            <p className="text-sm text-slate-600 dark:text-neutral-400">{description}</p>
        </button>
    );
}

// Preview Display Component
function PreviewDisplay({
    preview,
    onExecute,
    onCancel,
    executing,
}: {
    preview: BulkOperationPreview;
    onExecute: () => void;
    onCancel: () => void;
    executing: boolean;
}) {
    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">Preview Changes</h3>
                <button onClick={onCancel} className="text-sm text-blue-600 dark:text-indigo-400 hover:underline">
                    ← Back to Edit
                </button>
            </div>

            {/* Summary */}
            <div className="grid grid-cols-3 gap-4">
                <div className="p-4 bg-blue-50 dark:bg-indigo-600/30 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Affected Items</p>
                    <p className="text-2xl font-bold text-blue-600 dark:text-indigo-400">
                        {preview.affectedItemsCount}
                    </p>
                </div>
                <div className="p-4 bg-green-50 dark:bg-green-600/30 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Estimated Time</p>
                    <p className="text-2xl font-bold text-green-600 dark:text-green-400">
                        {preview.estimatedDuration}ms
                    </p>
                </div>
                <div className="p-4 bg-purple-50 dark:bg-violet-600/30 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Validation</p>
                    <p className="text-2xl font-bold text-purple-600 dark:text-violet-400">
                        {preview.validationIssues.length} issues
                    </p>
                </div>
            </div>

            {/* Validation Issues */}
            {preview.validationIssues.length > 0 && (
                <div className="space-y-2">
                    <h4 className="font-semibold text-slate-900 dark:text-neutral-100">Validation Issues</h4>
                    {preview.validationIssues.map((issue, index) => (
                        <div
                            key={index}
                            className={`p-3 rounded-lg border ${issue.severity === ValidationSeverity.ERROR
                                    ? 'bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800'
                                    : issue.severity === ValidationSeverity.WARNING
                                        ? 'bg-yellow-50 dark:bg-orange-600/30 border-yellow-200 dark:border-yellow-800'
                                        : 'bg-blue-50 dark:bg-indigo-600/30 border-blue-200 dark:border-blue-800'
                                }`}
                        >
                            <p className="text-sm font-medium">{issue.message}</p>
                            {issue.suggestion && (
                                <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                                    Suggestion: {issue.suggestion}
                                </p>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {/* Affected Items */}
            <div className="space-y-2">
                <h4 className="font-semibold text-slate-900 dark:text-neutral-100">Affected Items</h4>
                <div className="max-h-60 overflow-y-auto space-y-2">
                    {preview.affectedItems.map((item, index) => (
                        <div
                            key={index}
                            className="p-3 bg-slate-50 dark:bg-neutral-700 rounded-lg"
                        >
                            <p className="font-medium text-slate-900 dark:text-neutral-100">{item.itemName}</p>
                            <ul className="mt-2 space-y-1">
                                {item.changes.map((change, idx) => (
                                    <li key={idx} className="text-sm text-slate-600 dark:text-neutral-400">
                                        • {change}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ))}
                </div>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center gap-3 pt-4 border-t border-slate-200 dark:border-neutral-600">
                <button
                    onClick={onCancel}
                    disabled={executing}
                    className="flex-1 px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-neutral-700 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-600 disabled:opacity-50"
                >
                    Cancel
                </button>
                <button
                    onClick={onExecute}
                    disabled={!preview.canProceed || executing}
                    className="flex-1 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {executing ? 'Executing...' : '✓ Confirm & Execute'}
                </button>
            </div>
        </div>
    );
}

// Progress Display Component
function ProgressDisplay({ progress }: { progress: any }) {
    return (
        <div className="space-y-4">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">Operation in Progress</h3>
            <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-600 dark:text-neutral-400">
                        Processing: {progress.currentItem} / {progress.totalItems}
                    </span>
                    <span className="font-medium text-blue-600 dark:text-indigo-400">
                        {Math.round(progress.percentComplete)}%
                    </span>
                </div>
                <div className="w-full bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                    <div
                        className="bg-blue-600 dark:bg-blue-500 h-2 rounded-full transition-all duration-300"
                        style={{ width: `${progress.percentComplete}%` }}
                    />
                </div>
                {progress.currentItemName && (
                    <p className="text-sm text-slate-600 dark:text-neutral-400">
                        Current: {progress.currentItemName}
                    </p>
                )}
            </div>
        </div>
    );
}

// Result Display Component
function ResultDisplay({ result, onReset }: { result: BulkOperationResult; onReset: () => void }) {
    const isSuccess = result.status === BulkOperationStatus.COMPLETED;
    const isPartial = result.status === BulkOperationStatus.PARTIAL_SUCCESS;

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">Operation Complete</h3>
                <button
                    onClick={onReset}
                    className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded hover:bg-blue-700"
                >
                    New Operation
                </button>
            </div>

            {/* Status Badge */}
            <div
                className={`p-4 rounded-lg ${isSuccess
                        ? 'bg-green-50 dark:bg-green-600/30 border-2 border-green-200 dark:border-green-800'
                        : isPartial
                            ? 'bg-yellow-50 dark:bg-orange-600/30 border-2 border-yellow-200 dark:border-yellow-800'
                            : 'bg-red-50 dark:bg-rose-600/30 border-2 border-red-200 dark:border-red-800'
                    }`}
            >
                <div className="flex items-center gap-2">
                    <span className="text-2xl">
                        {isSuccess ? '✅' : isPartial ? '⚠️' : '❌'}
                    </span>
                    <div>
                        <p className="font-semibold text-slate-900 dark:text-neutral-100">
                            {isSuccess
                                ? 'All operations completed successfully'
                                : isPartial
                                    ? 'Some operations failed'
                                    : 'Operation failed'}
                        </p>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            {result.successCount} succeeded, {result.failureCount} failed
                        </p>
                    </div>
                </div>
            </div>

            {/* Statistics */}
            <div className="grid grid-cols-3 gap-4">
                <div className="p-4 bg-slate-50 dark:bg-neutral-700 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Total Items</p>
                    <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{result.totalItems}</p>
                </div>
                <div className="p-4 bg-green-50 dark:bg-green-600/30 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Successful</p>
                    <p className="text-2xl font-bold text-green-600 dark:text-green-400">
                        {result.successCount}
                    </p>
                </div>
                <div className="p-4 bg-red-50 dark:bg-rose-600/30 rounded-lg">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">Failed</p>
                    <p className="text-2xl font-bold text-red-600 dark:text-rose-400">{result.failureCount}</p>
                </div>
            </div>

            {/* Errors */}
            {result.errors.length > 0 && (
                <div className="space-y-2">
                    <h4 className="font-semibold text-slate-900 dark:text-neutral-100">Errors</h4>
                    <div className="max-h-40 overflow-y-auto space-y-1">
                        {result.errors.map((error, index) => (
                            <div key={index} className="p-2 bg-red-50 dark:bg-rose-600/30 rounded text-sm text-red-800 dark:text-red-200">
                                {error}
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

function getOperationTitle(type: BulkOperationType): string {
    switch (type) {
        case BulkOperationType.ASSIGN_PERMISSIONS:
            return 'Assign Permissions to Roles';
        case BulkOperationType.REVOKE_PERMISSIONS:
            return 'Revoke Permissions from Roles';
        case BulkOperationType.UPDATE_ROLES:
            return 'Update Multiple Roles';
        case BulkOperationType.CLONE_ROLE:
            return 'Clone Role';
        default:
            return 'Bulk Operation';
    }
}
