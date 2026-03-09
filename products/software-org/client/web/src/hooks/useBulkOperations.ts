/**
 * React hooks for bulk operations
 * 
 * Provides hooks for:
 * - Executing bulk operations with progress tracking
 * - Previewing operations before execution
 * - Undo/redo functionality
 * - Operation history queries
 * - Statistics and analytics
 */

import { useState, useCallback, useEffect } from 'react';
import { BulkOperationsService } from '@/services/bulkOperationsService';
import {
    BulkOperationType,
    BulkOperationResult,
    BulkOperationPreview,
    BulkOperationProgress,
    BulkOperationHistoryResult,
    BulkOperationHistoryFilter,
    BulkOperationStats,
    BulkOperationOptions,
    BulkAssignPermissionsParams,
    BulkRevokePermissionsParams,
    BulkUpdateRolesParams,
    CloneRoleParams,
} from '@/types/bulkOperations';

/**
 * Hook for executing bulk operations
 * 
 * @example
 * ```tsx
 * const { execute, progress, result, loading, error } = useBulkOperation();
 * 
 * const handleAssign = async () => {
 *   const result = await execute({
 *     type: BulkOperationType.ASSIGN_PERMISSIONS,
 *     params: { roleIds: ['role1', 'role2'], permissionIds: ['perm1'] }
 *   });
 *   console.log('Operation completed:', result);
 * };
 * ```
 */
export function useBulkOperation() {
    const [loading, setLoading] = useState(false);
    const [progress, setProgress] = useState<BulkOperationProgress | null>(null);
    const [result, setResult] = useState<BulkOperationResult | null>(null);
    const [error, setError] = useState<Error | null>(null);

    const execute = useCallback(
        async (config: {
            type: BulkOperationType;
            params: any;
            options?: BulkOperationOptions;
        }): Promise<BulkOperationResult | null> => {
            setLoading(true);
            setError(null);
            setProgress(null);
            setResult(null);

            try {
                const options: BulkOperationOptions = {
                    ...config.options,
                    onProgress: (prog) => {
                        setProgress(prog);
                        config.options?.onProgress?.(prog);
                    },
                };

                let operationResult: BulkOperationResult;

                switch (config.type) {
                    case BulkOperationType.ASSIGN_PERMISSIONS:
                        operationResult = await BulkOperationsService.assignPermissions(
                            config.params as BulkAssignPermissionsParams,
                            options
                        );
                        break;

                    case BulkOperationType.REVOKE_PERMISSIONS:
                        operationResult = await BulkOperationsService.revokePermissions(
                            config.params as BulkRevokePermissionsParams,
                            options
                        );
                        break;

                    case BulkOperationType.UPDATE_ROLES:
                        operationResult = await BulkOperationsService.updateRoles(
                            config.params as BulkUpdateRolesParams,
                            options
                        );
                        break;

                    case BulkOperationType.CLONE_ROLE:
                        operationResult = await BulkOperationsService.cloneRole(
                            config.params as CloneRoleParams,
                            options
                        );
                        break;

                    default:
                        throw new Error(`Unsupported operation type: ${config.type}`);
                }

                setResult(operationResult);
                return operationResult;
            } catch (err: any) {
                setError(err);
                return null;
            } finally {
                setLoading(false);
            }
        },
        []
    );

    const reset = useCallback(() => {
        setLoading(false);
        setProgress(null);
        setResult(null);
        setError(null);
    }, []);

    return {
        execute,
        progress,
        result,
        loading,
        error,
        reset,
    };
}

/**
 * Hook for previewing bulk operations
 * 
 * @example
 * ```tsx
 * const { preview, previewData, loading, error } = useBulkOperationPreview();
 * 
 * const handlePreview = async () => {
 *   const previewData = await preview({
 *     type: BulkOperationType.ASSIGN_PERMISSIONS,
 *     params: { roleIds: ['role1'], permissionIds: ['perm1'] }
 *   });
 *   console.log('Preview:', previewData);
 * };
 * ```
 */
export function useBulkOperationPreview() {
    const [loading, setLoading] = useState(false);
    const [previewData, setPreviewData] = useState<BulkOperationPreview | null>(null);
    const [error, setError] = useState<Error | null>(null);

    const preview = useCallback(
        async (config: {
            type: BulkOperationType;
            params: any;
        }): Promise<BulkOperationPreview | null> => {
            setLoading(true);
            setError(null);
            setPreviewData(null);

            try {
                let previewResult: BulkOperationPreview;

                switch (config.type) {
                    case BulkOperationType.ASSIGN_PERMISSIONS:
                        previewResult = await BulkOperationsService.previewAssignPermissions(
                            config.params as BulkAssignPermissionsParams
                        );
                        break;

                    case BulkOperationType.REVOKE_PERMISSIONS:
                        previewResult = await BulkOperationsService.previewRevokePermissions(
                            config.params as BulkRevokePermissionsParams
                        );
                        break;

                    case BulkOperationType.UPDATE_ROLES:
                        previewResult = await BulkOperationsService.previewUpdateRoles(
                            config.params as BulkUpdateRolesParams
                        );
                        break;

                    case BulkOperationType.CLONE_ROLE:
                        previewResult = await BulkOperationsService.previewCloneRole(
                            config.params as CloneRoleParams
                        );
                        break;

                    default:
                        throw new Error(`Unsupported operation type: ${config.type}`);
                }

                setPreviewData(previewResult);
                return previewResult;
            } catch (err: any) {
                setError(err);
                return null;
            } finally {
                setLoading(false);
            }
        },
        []
    );

    const clear = useCallback(() => {
        setPreviewData(null);
        setError(null);
    }, []);

    return {
        preview,
        previewData,
        loading,
        error,
        clear,
    };
}

/**
 * Hook for undo/redo functionality
 * 
 * @example
 * ```tsx
 * const { undo, redo, canUndo, canRedo, loading } = useUndoRedo();
 * 
 * <button onClick={undo} disabled={!canUndo}>Undo</button>
 * <button onClick={redo} disabled={!canRedo}>Redo</button>
 * ```
 */
export function useUndoRedo() {
    const [canUndo, setCanUndo] = useState(false);
    const [canRedo, setCanRedo] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const updateState = useCallback(() => {
        setCanUndo(BulkOperationsService.canUndo());
        setCanRedo(BulkOperationsService.canRedo());
    }, []);

    useEffect(() => {
        updateState();
    }, [updateState]);

    const undo = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            await BulkOperationsService.undo();
            updateState();
        } catch (err: any) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, [updateState]);

    const redo = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            await BulkOperationsService.redo();
            updateState();
        } catch (err: any) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, [updateState]);

    return {
        undo,
        redo,
        canUndo,
        canRedo,
        loading,
        error,
    };
}

/**
 * Hook for querying operation history
 * 
 * @example
 * ```tsx
 * const { operations, total, hasMore, loading, error, setFilter, loadMore } = 
 *   useBulkOperationHistory();
 * 
 * // Filter by operation type
 * setFilter({ operationTypes: [BulkOperationType.ASSIGN_PERMISSIONS] });
 * ```
 */
export function useBulkOperationHistory(initialFilter?: BulkOperationHistoryFilter) {
    const [operations, setOperations] = useState<BulkOperationHistoryResult['operations']>([]);
    const [total, setTotal] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const [filter, setFilter] = useState<BulkOperationHistoryFilter>(initialFilter || {});

    const load = useCallback(async (append = false) => {
        setLoading(true);
        setError(null);

        try {
            const currentFilter = append
                ? { ...filter, offset: operations.length }
                : filter;

            const result = await BulkOperationsService.queryHistory(currentFilter);

            if (append) {
                setOperations((prev) => [...prev, ...result.operations]);
            } else {
                setOperations(result.operations);
            }

            setTotal(result.total);
            setHasMore(result.hasMore);
        } catch (err: any) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, [filter, operations.length]);

    const loadMore = useCallback(() => load(true), [load]);

    const refresh = useCallback(() => load(false), [load]);

    useEffect(() => {
        load(false);
    }, [filter]); // Re-load when filter changes

    return {
        operations,
        total,
        hasMore,
        loading,
        error,
        filter,
        setFilter,
        loadMore,
        refresh,
    };
}

/**
 * Hook for bulk operation statistics
 * 
 * @example
 * ```tsx
 * const { stats, loading, error, refresh } = useBulkOperationStats();
 * 
 * console.log('Total operations:', stats?.totalOperations);
 * console.log('Success rate:', stats?.successRate);
 * ```
 */
export function useBulkOperationStats() {
    const [stats, setStats] = useState<BulkOperationStats | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const result = await BulkOperationsService.getStats();
            setStats(result);
        } catch (err: any) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    const refresh = useCallback(() => load(), [load]);

    useEffect(() => {
        load();
    }, [load]);

    return {
        stats,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for getting a specific operation
 * 
 * @example
 * ```tsx
 * const { operation, loading, error } = useBulkOperationDetails('op-123');
 * ```
 */
export function useBulkOperationDetails(operationId: string | null) {
    const [operation, setOperation] = useState<BulkOperationResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (!operationId) {
            setOperation(null);
            return;
        }

        const load = async () => {
            setLoading(true);
            setError(null);

            try {
                const result = await BulkOperationsService.getOperation(operationId);
                setOperation(result);
            } catch (err: any) {
                setError(err);
            } finally {
                setLoading(false);
            }
        };

        load();
    }, [operationId]);

    return {
        operation,
        loading,
        error,
    };
}
