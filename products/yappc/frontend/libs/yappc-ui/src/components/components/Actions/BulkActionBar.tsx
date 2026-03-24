/**
 * Bulk Action Bar Component
 * 
 * Comprehensive interface for bulk operations with progress tracking,
 * confirmation dialogs, and undo functionality. Provides accessible
 * and intuitive bulk actions with real-time feedback and error handling.
 */

import React, { useState, useCallback, useEffect } from 'react';

import type { SelectionItem } from '../../hooks/useSelection';

/**
 *
 */
export interface BulkAction<T extends SelectionItem> {
    id: string;
    label: string;
    icon?: string;
    variant?: 'primary' | 'secondary' | 'danger' | 'success';
    disabled?: boolean | ((items: T[]) => boolean);
    requiresConfirmation?: boolean;
    confirmationTitle?: string;
    confirmationMessage?: string | ((items: T[]) => string);
    execute: (items: T[]) => Promise<void>;
    undoable?: boolean;
    undoAction?: (items: T[]) => Promise<void>;
}

/**
 *
 */
export interface BulkOperationProgress {
    total: number;
    completed: number;
    failed: number;
    inProgress: boolean;
    errors: { item: unknown; error: string }[];
}

/**
 *
 */
export interface BulkActionBarProps<T extends SelectionItem> {
    selectedItems: T[];
    actions: BulkAction<T>[];
    onClearSelection?: () => void;
    onUndo?: () => void;
    canUndo?: boolean;
    className?: string;
    position?: 'top' | 'bottom' | 'fixed-bottom';
    showProgress?: boolean;
    autoHide?: boolean;
    maxHeight?: string | number;
}

/**
 *
 */
export function BulkActionBar<T extends SelectionItem>({
    selectedItems,
    actions,
    onClearSelection,
    onUndo,
    canUndo = false,
    className,
    position = 'fixed-bottom',
    showProgress = true,
    autoHide = true,
    maxHeight = '200px'
}: BulkActionBarProps<T>) {

    const [progress, setProgress] = useState<BulkOperationProgress | null>(null);
    const [confirmationDialog, setConfirmationDialog] = useState<{
        action: BulkAction<T>;
        items: T[];
    } | null>(null);
    const [lastOperation, setLastOperation] = useState<{
        action: BulkAction<T>;
        items: T[];
        completedAt: number;
    } | null>(null);

    // Hide bar when no items selected (with delay for better UX)
    const [visible, setVisible] = useState(selectedItems.length > 0);

    useEffect(() => {
        if (selectedItems.length > 0) {
            setVisible(true);
        } else if (autoHide) {
            const timer = setTimeout(() => setVisible(false), 300);
            return () => clearTimeout(timer);
        }

        return undefined;
    }, [selectedItems.length, autoHide]);

    // Execute bulk action
    const executeBulkAction = useCallback(async (action: BulkAction<T>, items: T[]) => {
        setProgress({
            total: items.length,
            completed: 0,
            failed: 0,
            inProgress: true,
            errors: []
        });

        try {
            await action.execute(items);

            setProgress(prev => prev ? {
                ...prev,
                completed: items.length,
                inProgress: false
            } : null);

            // Store for undo functionality
            if (action.undoable && action.undoAction) {
                setLastOperation({
                    action,
                    items,
                    completedAt: Date.now()
                });
            }

            // Auto-hide progress after success
            setTimeout(() => setProgress(null), 2000);

        } catch (error) {
            setProgress(prev => prev ? {
                ...prev,
                failed: items.length,
                inProgress: false,
                errors: [{ item: items, error: error instanceof Error ? error.message : String(error) }]
            } : null);
        }
    }, []);

    // Handle action click
    const handleActionClick = useCallback((action: BulkAction<T>) => {
        if (action.requiresConfirmation) {
            setConfirmationDialog({ action, items: selectedItems });
        } else {
            executeBulkAction(action, selectedItems);
        }
    }, [selectedItems, executeBulkAction]);

    // Handle confirmation
    const handleConfirm = useCallback(() => {
        if (confirmationDialog) {
            executeBulkAction(confirmationDialog.action, confirmationDialog.items);
            setConfirmationDialog(null);
        }
    }, [confirmationDialog, executeBulkAction]);

    // Handle undo
    const handleUndo = useCallback(async () => {
        if (lastOperation && lastOperation.action.undoAction) {
            try {
                await lastOperation.action.undoAction(lastOperation.items);
                setLastOperation(null);
            } catch (error) {
                console.error('Undo failed:', error);
            }
        }
        onUndo?.();
    }, [lastOperation, onUndo]);

    // Check if action is disabled
    const isActionDisabled = useCallback((action: BulkAction<T>) => {
        if (typeof action.disabled === 'function') {
            return action.disabled(selectedItems);
        }
        return action.disabled || selectedItems.length === 0;
    }, [selectedItems]);

    // Container styles
    const containerStyle: React.CSSProperties = {
        position: position === 'fixed-bottom' ? 'fixed' : 'relative',
        bottom: position === 'fixed-bottom' ? '20px' : 'auto',
        left: position === 'fixed-bottom' ? '50%' : 'auto',
        transform: position === 'fixed-bottom' ? 'translateX(-50%)' : 'none',
        backgroundColor: 'white',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '8px',
        boxShadow: position === 'fixed-bottom'
            ? '0 4px 20px rgba(0, 0, 0, 0.15)'
            : '0 2px 8px rgba(0, 0, 0, 0.1)',
        padding: '1rem',
        zIndex: 1000,
        maxWidth: position === 'fixed-bottom' ? '90vw' : '100%',
        minWidth: '300px',
        opacity: visible ? 1 : 0,
        visibility: visible ? 'visible' : 'hidden',
        transition: 'all 0.3s ease'
    };

    const headerStyle: React.CSSProperties = {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: '1rem',
        paddingBottom: '0.75rem',
        borderBottom: '1px solid var(--color-border-light, #f0f0f0)'
    };

    const actionsStyle: React.CSSProperties = {
        display: 'flex',
        gap: '0.75rem',
        flexWrap: 'wrap',
        alignItems: 'center'
    };

    const buttonStyle = (action: BulkAction<T>): React.CSSProperties => ({
        padding: '0.5rem 1rem',
        borderRadius: '6px',
        border: 'none',
        cursor: isActionDisabled(action) ? 'not-allowed' : 'pointer',
        fontSize: '0.875rem',
        fontWeight: '500',
        display: 'flex',
        alignItems: 'center',
        gap: '0.5rem',
        transition: 'all 0.2s ease',
        opacity: isActionDisabled(action) ? 0.5 : 1,
        backgroundColor: getActionColor(action.variant || 'secondary'),
        color: action.variant === 'secondary' ? 'var(--color-text-primary, #333)' : 'white'
    });

    /**
     *
     */
    function getActionColor(variant: string): string {
        const colors = {
            primary: 'var(--color-primary, #1976d2)',
            secondary: 'var(--color-background-secondary, #f8f9fa)',
            danger: 'var(--color-error, #d32f2f)',
            success: 'var(--color-success, #2e7d32)'
        };
        return colors[variant as keyof typeof colors] || colors.secondary;
    }

    if (!visible && selectedItems.length === 0 && !progress) {
        return null;
    }

    return (
        <>
            <div className={className} style={containerStyle}>
                {/* Header */}
                <div style={headerStyle}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        <span style={{
                            fontSize: '1rem',
                            fontWeight: '600',
                            color: 'var(--color-text-primary, #333)'
                        }}>
                            {selectedItems.length} item{selectedItems.length === 1 ? '' : 's'} selected
                        </span>
                        {canUndo && lastOperation && (
                            <button
                                onClick={handleUndo}
                                style={{
                                    background: 'none',
                                    border: '1px solid var(--color-border, #e0e0e0)',
                                    borderRadius: '4px',
                                    padding: '0.25rem 0.5rem',
                                    cursor: 'pointer',
                                    fontSize: '0.75rem',
                                    color: 'var(--color-text-secondary, #666)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '0.25rem'
                                }}
                            >
                                ↶ Undo {lastOperation.action.label}
                            </button>
                        )}
                    </div>
                    <button
                        onClick={onClearSelection}
                        style={{
                            background: 'none',
                            border: 'none',
                            cursor: 'pointer',
                            fontSize: '1.25rem',
                            color: 'var(--color-text-secondary, #666)',
                            padding: '0.25rem'
                        }}
                        title="Clear selection"
                    >
                        ✕
                    </button>
                </div>

                {/* Progress Bar */}
                {showProgress && progress && (
                    <div style={{
                        marginBottom: '1rem',
                        padding: '0.75rem',
                        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
                        borderRadius: '4px'
                    }}>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '0.5rem'
                        }}>
                            <span style={{ fontSize: '0.875rem', fontWeight: '500' }}>
                                {progress.inProgress ? 'Processing...' :
                                    progress.failed > 0 ? 'Completed with errors' : 'Completed'}
                            </span>
                            <span style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary, #666)' }}>
                                {progress.completed + progress.failed} / {progress.total}
                            </span>
                        </div>

                        {/* Progress bar */}
                        <div style={{
                            height: '4px',
                            backgroundColor: 'var(--color-border, #e0e0e0)',
                            borderRadius: '2px',
                            overflow: 'hidden'
                        }}>
                            <div
                                style={{
                                    height: '100%',
                                    width: `${((progress.completed + progress.failed) / progress.total) * 100}%`,
                                    backgroundColor: progress.failed > 0
                                        ? 'var(--color-error, #d32f2f)'
                                        : 'var(--color-success, #2e7d32)',
                                    transition: 'width 0.3s ease'
                                }}
                            />
                        </div>

                        {/* Error details */}
                        {progress.errors.length > 0 && (
                            <div style={{
                                marginTop: '0.5rem',
                                maxHeight,
                                overflowY: 'auto'
                            }}>
                                {progress.errors.map((error, index) => (
                                    <div key={index} style={{
                                        fontSize: '0.75rem',
                                        color: 'var(--color-error, #d32f2f)',
                                        padding: '0.25rem 0'
                                    }}>
                                        Error: {error.error}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Actions */}
                {!progress?.inProgress && (
                    <div style={actionsStyle}>
                        {actions.map((action) => (
                            <button
                                key={action.id}
                                onClick={() => handleActionClick(action)}
                                disabled={isActionDisabled(action)}
                                style={buttonStyle(action)}
                                title={action.label}
                            >
                                {action.icon && <span>{action.icon}</span>}
                                <span>{action.label}</span>
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {/* Confirmation Dialog */}
            {confirmationDialog && (
                <div style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.5)',
                    zIndex: 10000,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                }}>
                    <div style={{
                        backgroundColor: 'white',
                        borderRadius: '8px',
                        padding: '2rem',
                        maxWidth: '500px',
                        width: '90vw',
                        boxShadow: '0 10px 30px rgba(0, 0, 0, 0.2)'
                    }}>
                        <h3 style={{
                            margin: '0 0 1rem 0',
                            fontSize: '1.25rem',
                            color: 'var(--color-text-primary, #333)'
                        }}>
                            {confirmationDialog.action.confirmationTitle || `Confirm ${confirmationDialog.action.label}`}
                        </h3>

                        <p style={{
                            margin: '0 0 1.5rem 0',
                            color: 'var(--color-text-secondary, #666)',
                            lineHeight: 1.5
                        }}>
                            {typeof confirmationDialog.action.confirmationMessage === 'function'
                                ? confirmationDialog.action.confirmationMessage(confirmationDialog.items)
                                : confirmationDialog.action.confirmationMessage ||
                                `Are you sure you want to ${confirmationDialog.action.label.toLowerCase()} ${confirmationDialog.items.length} item${confirmationDialog.items.length === 1 ? '' : 's'}?`
                            }
                        </p>

                        <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                            <button
                                onClick={() => setConfirmationDialog(null)}
                                style={{
                                    padding: '0.75rem 1.5rem',
                                    borderRadius: '6px',
                                    border: '1px solid var(--color-border, #e0e0e0)',
                                    backgroundColor: 'white',
                                    color: 'var(--color-text-primary, #333)',
                                    cursor: 'pointer',
                                    fontSize: '0.875rem'
                                }}
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleConfirm}
                                style={{
                                    padding: '0.75rem 1.5rem',
                                    borderRadius: '6px',
                                    border: 'none',
                                    backgroundColor: getActionColor(confirmationDialog.action.variant || 'primary'),
                                    color: 'white',
                                    cursor: 'pointer',
                                    fontSize: '0.875rem',
                                    fontWeight: '500'
                                }}
                            >
                                {confirmationDialog.action.label}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
