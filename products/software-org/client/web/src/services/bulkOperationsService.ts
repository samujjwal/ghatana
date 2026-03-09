/**
 * Bulk Operations Service
 * 
 * Provides functionality for:
 * - Bulk permission assignment/revocation
 * - Bulk role updates
 * - Role cloning
 * - Operation preview and validation
 * - Undo/redo support
 * - Operation history tracking
 * 
 * Performance targets:
 * - Preview generation: <100ms for 100 items
 * - Bulk operation: <50ms per item
 * - Undo/redo: <200ms
 * 
 * @doc.type service
 * @doc.purpose Bulk operations service for roles and permissions
 * @doc.layer product
 * @doc.pattern Service
 */

import { v4 as uuidv4 } from 'uuid';
import {
    BulkOperationType,
    BulkOperationStatus,
    BulkOperationPreview,
    BulkOperationResult,
    BulkOperationHistoryEntry,
    BulkOperationHistoryResult,
    BulkOperationHistoryFilter,
    BulkOperationStats,
    BulkOperationOptions,
    BulkOperationProgress,
    BulkOperationItemResult,
    BulkAssignPermissionsParams,
    BulkRevokePermissionsParams,
    BulkUpdateRolesParams,
    CloneRoleParams,
    UndoRedoEntry,
    ValidationIssue,
    ValidationSeverity,
} from '@/types/bulkOperations';
import { AuditService } from './auditService';
import { AuditAction, AuditResourceType, AuditSeverity } from '@/types/audit';

/**
 * In-memory storage for bulk operations
 */
class BulkOperationStore {
    private operations: Map<string, BulkOperationResult> = new Map();
    private history: BulkOperationHistoryEntry[] = [];
    private undoStack: UndoRedoEntry[] = [];
    private redoStack: UndoRedoEntry[] = [];
    private maxHistorySize = 1000;
    private maxUndoStackSize = 50;

    addOperation(operation: BulkOperationResult): void {
        this.operations.set(operation.operationId, operation);

        // Add to history
        const historyEntry: BulkOperationHistoryEntry = {
            operationId: operation.operationId,
            operationType: operation.operationType,
            status: operation.status,
            timestamp: operation.startTime,
            userId: 'current-user', // TODO: Get from auth context
            userName: 'Current User',
            description: this.getOperationDescription(operation),
            affectedItemsCount: operation.totalItems,
            successCount: operation.successCount,
            failureCount: operation.failureCount,
            canUndo: operation.canUndo,
            canRedo: false,
        };

        this.history.unshift(historyEntry);

        // Trim history if needed
        if (this.history.length > this.maxHistorySize) {
            this.history = this.history.slice(0, this.maxHistorySize);
        }
    }

    getOperation(operationId: string): BulkOperationResult | undefined {
        return this.operations.get(operationId);
    }

    getHistory(filter?: BulkOperationHistoryFilter): BulkOperationHistoryResult {
        let filtered = [...this.history];

        if (filter) {
            if (filter.operationTypes?.length) {
                filtered = filtered.filter((op) =>
                    filter.operationTypes!.includes(op.operationType)
                );
            }

            if (filter.statuses?.length) {
                filtered = filtered.filter((op) =>
                    filter.statuses!.includes(op.status)
                );
            }

            if (filter.userIds?.length) {
                filtered = filtered.filter((op) =>
                    filter.userIds!.includes(op.userId)
                );
            }

            if (filter.startDate) {
                filtered = filtered.filter((op) => op.timestamp >= filter.startDate!);
            }

            if (filter.endDate) {
                filtered = filtered.filter((op) => op.timestamp <= filter.endDate!);
            }
        }

        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paged = filtered.slice(offset, offset + limit);

        return {
            operations: paged,
            total: filtered.length,
            hasMore: offset + limit < filtered.length,
        };
    }

    addToUndoStack(entry: UndoRedoEntry): void {
        this.undoStack.push(entry);

        // Clear redo stack when new operation is added
        this.redoStack = [];

        // Trim undo stack if needed
        if (this.undoStack.length > this.maxUndoStackSize) {
            this.undoStack.shift();
        }
    }

    popUndoStack(): UndoRedoEntry | undefined {
        const entry = this.undoStack.pop();
        if (entry) {
            this.redoStack.push(entry);
        }
        return entry;
    }

    popRedoStack(): UndoRedoEntry | undefined {
        const entry = this.redoStack.pop();
        if (entry) {
            this.undoStack.push(entry);
        }
        return entry;
    }

    canUndo(): boolean {
        return this.undoStack.length > 0;
    }

    canRedo(): boolean {
        return this.redoStack.length > 0;
    }

    clear(): void {
        this.operations.clear();
        this.history = [];
        this.undoStack = [];
        this.redoStack = [];
    }

    private getOperationDescription(operation: BulkOperationResult): string {
        const type = operation.operationType.replace(/_/g, ' ').toLowerCase();
        return `${type} - ${operation.successCount}/${operation.totalItems} successful`;
    }
}

// Global store instance
const store = new BulkOperationStore();

/**
 * Bulk Operations Service
 */
export class BulkOperationsService {
    /**
     * Preview bulk permission assignment
     */
    static async previewAssignPermissions(
        params: BulkAssignPermissionsParams
    ): Promise<BulkOperationPreview> {
        const { roleIds, permissionIds } = params;

        const affectedItems = roleIds.map((roleId) => ({
            itemId: roleId,
            itemName: `Role ${roleId}`,
            itemType: 'role' as const,
            currentState: { permissions: [] }, // TODO: Fetch actual state
            proposedState: { permissions: permissionIds },
            changes: permissionIds.map((pid) => `Add permission ${pid}`),
        }));

        const validationIssues: ValidationIssue[] = [];

        // Validate: Check for duplicate permissions
        // TODO: Add actual validation logic

        return {
            operationType: BulkOperationType.ASSIGN_PERMISSIONS,
            affectedItemsCount: roleIds.length,
            affectedItems,
            validationIssues,
            estimatedDuration: roleIds.length * 50, // 50ms per role
            canProceed: validationIssues.filter((i) => i.severity === ValidationSeverity.ERROR).length === 0,
            warnings: validationIssues
                .filter((i) => i.severity === ValidationSeverity.WARNING)
                .map((i) => i.message),
        };
    }

    /**
     * Preview bulk permission revocation
     */
    static async previewRevokePermissions(
        params: BulkRevokePermissionsParams
    ): Promise<BulkOperationPreview> {
        const { roleIds, permissionIds } = params;

        const affectedItems = roleIds.map((roleId) => ({
            itemId: roleId,
            itemName: `Role ${roleId}`,
            itemType: 'role' as const,
            currentState: { permissions: permissionIds },
            proposedState: { permissions: [] },
            changes: permissionIds.map((pid) => `Remove permission ${pid}`),
        }));

        const validationIssues: ValidationIssue[] = [];

        // Validate: Check if permissions exist
        // TODO: Add actual validation logic

        return {
            operationType: BulkOperationType.REVOKE_PERMISSIONS,
            affectedItemsCount: roleIds.length,
            affectedItems,
            validationIssues,
            estimatedDuration: roleIds.length * 50,
            canProceed: validationIssues.filter((i) => i.severity === ValidationSeverity.ERROR).length === 0,
            warnings: validationIssues
                .filter((i) => i.severity === ValidationSeverity.WARNING)
                .map((i) => i.message),
        };
    }

    /**
     * Preview bulk role updates
     */
    static async previewUpdateRoles(
        params: BulkUpdateRolesParams
    ): Promise<BulkOperationPreview> {
        const { roleIds, updates } = params;

        const affectedItems = roleIds.map((roleId) => {
            const changes: string[] = [];
            if (updates.displayName) changes.push(`Update display name to "${updates.displayName}"`);
            if (updates.description) changes.push(`Update description to "${updates.description}"`);
            if (updates.isActive !== undefined) changes.push(`Set active to ${updates.isActive}`);

            return {
                itemId: roleId,
                itemName: `Role ${roleId}`,
                itemType: 'role' as const,
                currentState: {}, // TODO: Fetch actual state
                proposedState: updates,
                changes,
            };
        });

        const validationIssues: ValidationIssue[] = [];

        return {
            operationType: BulkOperationType.UPDATE_ROLES,
            affectedItemsCount: roleIds.length,
            affectedItems,
            validationIssues,
            estimatedDuration: roleIds.length * 30,
            canProceed: true,
            warnings: [],
        };
    }

    /**
     * Preview role cloning
     */
    static async previewCloneRole(
        params: CloneRoleParams
    ): Promise<BulkOperationPreview> {
        const { sourceRoleId, newRoleId, newDisplayName, includePermissions, includeInheritance } = params;

        const changes: string[] = [
            `Clone from ${sourceRoleId}`,
            `New role ID: ${newRoleId}`,
            `New display name: ${newDisplayName}`,
        ];

        if (includePermissions) changes.push('Include permissions');
        if (includeInheritance) changes.push('Include inheritance');

        const affectedItems = [
            {
                itemId: newRoleId,
                itemName: newDisplayName,
                itemType: 'role' as const,
                currentState: null,
                proposedState: { clonedFrom: sourceRoleId },
                changes,
            },
        ];

        const validationIssues: ValidationIssue[] = [];

        // Validate: Check if new role ID already exists
        // TODO: Add actual validation logic

        return {
            operationType: BulkOperationType.CLONE_ROLE,
            affectedItemsCount: 1,
            affectedItems,
            validationIssues,
            estimatedDuration: 100,
            canProceed: validationIssues.filter((i) => i.severity === ValidationSeverity.ERROR).length === 0,
            warnings: [],
        };
    }

    /**
     * Execute bulk permission assignment
     */
    static async assignPermissions(
        params: BulkAssignPermissionsParams,
        options?: BulkOperationOptions
    ): Promise<BulkOperationResult> {
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const { roleIds, permissionIds, metadata } = params;

        const itemResults: BulkOperationItemResult[] = [];
        let successCount = 0;
        let failureCount = 0;

        // Execute operation for each role
        for (let i = 0; i < roleIds.length; i++) {
            const roleId = roleIds[i];

            try {
                // TODO: Actual permission assignment logic here
                // For now, simulate success
                await new Promise((resolve) => setTimeout(resolve, 10)); // Simulate async operation

                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: true,
                    previousState: { permissions: [] },
                    newState: { permissions: permissionIds },
                });
                successCount++;

                // Progress callback
                if (options?.onProgress) {
                    options.onProgress({
                        operationId,
                        status: BulkOperationStatus.IN_PROGRESS,
                        currentItem: i + 1,
                        totalItems: roleIds.length,
                        percentComplete: ((i + 1) / roleIds.length) * 100,
                        currentItemName: `Role ${roleId}`,
                        errors: [],
                    });
                }
            } catch (error: any) {
                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: false,
                    error: error.message,
                });
                failureCount++;

                if (options?.stopOnError) {
                    break;
                }
            }
        }

        const endTime = new Date().toISOString();
        const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

        const result: BulkOperationResult = {
            operationId,
            operationType: BulkOperationType.ASSIGN_PERMISSIONS,
            status: failureCount === 0 ? BulkOperationStatus.COMPLETED :
                successCount === 0 ? BulkOperationStatus.FAILED :
                    BulkOperationStatus.PARTIAL_SUCCESS,
            startTime,
            endTime,
            duration,
            totalItems: roleIds.length,
            successCount,
            failureCount,
            itemResults,
            errors: itemResults.filter((r) => !r.success).map((r) => r.error || 'Unknown error'),
            canUndo: true,
            undoData: { roleIds, permissionIds }, // Store for undo
        };

        // Store operation
        store.addOperation(result);

        // Add to undo stack
        if (result.canUndo) {
            store.addToUndoStack({
                operationId,
                operationType: BulkOperationType.ASSIGN_PERMISSIONS,
                timestamp: startTime,
                undoData: { roleIds, permissionIds },
                redoData: { roleIds, permissionIds },
                description: `Assign ${permissionIds.length} permissions to ${roleIds.length} roles`,
            });
        }

        // Log to audit trail
        await AuditService.logEvent({
            action: AuditAction.BULK_PERMISSION_ASSIGN,
            resourceType: AuditResourceType.PERMISSION,
            resourceId: operationId,
            resourceName: `Bulk assign to ${roleIds.length} roles`,
            changes: [
                {
                    field: 'permissions',
                    oldValue: [],
                    newValue: permissionIds,
                },
            ],
            severity: failureCount > 0 ? AuditSeverity.WARNING : AuditSeverity.INFO,
            success: failureCount === 0,
            metadata: {
                userId: 'current-user',
                userName: 'Current User',
                ...metadata,
            },
        });

        return result;
    }

    /**
     * Execute bulk permission revocation
     */
    static async revokePermissions(
        params: BulkRevokePermissionsParams,
        options?: BulkOperationOptions
    ): Promise<BulkOperationResult> {
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const { roleIds, permissionIds, metadata } = params;

        const itemResults: BulkOperationItemResult[] = [];
        let successCount = 0;
        let failureCount = 0;

        for (let i = 0; i < roleIds.length; i++) {
            const roleId = roleIds[i];

            try {
                await new Promise((resolve) => setTimeout(resolve, 10));

                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: true,
                    previousState: { permissions: permissionIds },
                    newState: { permissions: [] },
                });
                successCount++;

                if (options?.onProgress) {
                    options.onProgress({
                        operationId,
                        status: BulkOperationStatus.IN_PROGRESS,
                        currentItem: i + 1,
                        totalItems: roleIds.length,
                        percentComplete: ((i + 1) / roleIds.length) * 100,
                        currentItemName: `Role ${roleId}`,
                        errors: [],
                    });
                }
            } catch (error: any) {
                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: false,
                    error: error.message,
                });
                failureCount++;

                if (options?.stopOnError) {
                    break;
                }
            }
        }

        const endTime = new Date().toISOString();
        const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

        const result: BulkOperationResult = {
            operationId,
            operationType: BulkOperationType.REVOKE_PERMISSIONS,
            status: failureCount === 0 ? BulkOperationStatus.COMPLETED :
                successCount === 0 ? BulkOperationStatus.FAILED :
                    BulkOperationStatus.PARTIAL_SUCCESS,
            startTime,
            endTime,
            duration,
            totalItems: roleIds.length,
            successCount,
            failureCount,
            itemResults,
            errors: itemResults.filter((r) => !r.success).map((r) => r.error || 'Unknown error'),
            canUndo: true,
            undoData: { roleIds, permissionIds },
        };

        store.addOperation(result);

        if (result.canUndo) {
            store.addToUndoStack({
                operationId,
                operationType: BulkOperationType.REVOKE_PERMISSIONS,
                timestamp: startTime,
                undoData: { roleIds, permissionIds },
                redoData: { roleIds, permissionIds },
                description: `Revoke ${permissionIds.length} permissions from ${roleIds.length} roles`,
            });
        }

        await AuditService.logEvent({
            action: AuditAction.BULK_PERMISSION_REVOKE,
            resourceType: AuditResourceType.PERMISSION,
            resourceId: operationId,
            resourceName: `Bulk revoke from ${roleIds.length} roles`,
            changes: [
                {
                    field: 'permissions',
                    oldValue: permissionIds,
                    newValue: [],
                },
            ],
            severity: failureCount > 0 ? AuditSeverity.WARNING : AuditSeverity.INFO,
            success: failureCount === 0,
            metadata: {
                userId: 'current-user',
                userName: 'Current User',
                ...metadata,
            },
        });

        return result;
    }

    /**
     * Execute bulk role updates
     */
    static async updateRoles(
        params: BulkUpdateRolesParams,
        options?: BulkOperationOptions
    ): Promise<BulkOperationResult> {
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const { roleIds, updates } = params;

        const itemResults: BulkOperationItemResult[] = [];
        let successCount = 0;
        let failureCount = 0;

        for (let i = 0; i < roleIds.length; i++) {
            const roleId = roleIds[i];

            try {
                await new Promise((resolve) => setTimeout(resolve, 10));

                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: true,
                    previousState: {},
                    newState: updates,
                });
                successCount++;

                if (options?.onProgress) {
                    options.onProgress({
                        operationId,
                        status: BulkOperationStatus.IN_PROGRESS,
                        currentItem: i + 1,
                        totalItems: roleIds.length,
                        percentComplete: ((i + 1) / roleIds.length) * 100,
                        currentItemName: `Role ${roleId}`,
                        errors: [],
                    });
                }
            } catch (error: any) {
                itemResults.push({
                    itemId: roleId,
                    itemName: `Role ${roleId}`,
                    success: false,
                    error: error.message,
                });
                failureCount++;

                if (options?.stopOnError) {
                    break;
                }
            }
        }

        const endTime = new Date().toISOString();
        const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

        const result: BulkOperationResult = {
            operationId,
            operationType: BulkOperationType.UPDATE_ROLES,
            status: failureCount === 0 ? BulkOperationStatus.COMPLETED :
                successCount === 0 ? BulkOperationStatus.FAILED :
                    BulkOperationStatus.PARTIAL_SUCCESS,
            startTime,
            endTime,
            duration,
            totalItems: roleIds.length,
            successCount,
            failureCount,
            itemResults,
            errors: itemResults.filter((r) => !r.success).map((r) => r.error || 'Unknown error'),
            canUndo: true,
            undoData: { roleIds, previousStates: {} },
        };

        store.addOperation(result);

        if (result.canUndo) {
            store.addToUndoStack({
                operationId,
                operationType: BulkOperationType.UPDATE_ROLES,
                timestamp: startTime,
                undoData: { roleIds, updates: {} },
                redoData: { roleIds, updates },
                description: `Update ${roleIds.length} roles`,
            });
        }

        await AuditService.logEvent({
            action: AuditAction.BULK_ROLE_UPDATE,
            resourceType: AuditResourceType.ROLE,
            resourceId: operationId,
            resourceName: `Bulk update ${roleIds.length} roles`,
            changes: Object.entries(updates).map(([field, value]) => ({
                field,
                oldValue: undefined,
                newValue: value,
            })),
            severity: failureCount > 0 ? AuditSeverity.WARNING : AuditSeverity.INFO,
            success: failureCount === 0,
            metadata: {
                userId: 'current-user',
                userName: 'Current User',
            },
        });

        return result;
    }

    /**
     * Clone a role
     */
    static async cloneRole(
        params: CloneRoleParams,
        options?: BulkOperationOptions
    ): Promise<BulkOperationResult> {
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const { sourceRoleId, newRoleId, newDisplayName } = params;

        try {
            // TODO: Actual cloning logic
            await new Promise((resolve) => setTimeout(resolve, 50));

            const endTime = new Date().toISOString();
            const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

            const result: BulkOperationResult = {
                operationId,
                operationType: BulkOperationType.CLONE_ROLE,
                status: BulkOperationStatus.COMPLETED,
                startTime,
                endTime,
                duration,
                totalItems: 1,
                successCount: 1,
                failureCount: 0,
                itemResults: [
                    {
                        itemId: newRoleId,
                        itemName: newDisplayName,
                        success: true,
                        previousState: null,
                        newState: { clonedFrom: sourceRoleId },
                    },
                ],
                errors: [],
                canUndo: true,
                undoData: { newRoleId },
            };

            store.addOperation(result);

            if (result.canUndo) {
                store.addToUndoStack({
                    operationId,
                    operationType: BulkOperationType.CLONE_ROLE,
                    timestamp: startTime,
                    undoData: { newRoleId },
                    redoData: params,
                    description: `Clone role ${sourceRoleId} to ${newRoleId}`,
                });
            }

            await AuditService.logEvent({
                action: AuditAction.ROLE_CLONED,
                resourceType: AuditResourceType.ROLE,
                resourceId: newRoleId,
                resourceName: newDisplayName,
                changes: [
                    {
                        field: 'clonedFrom',
                        oldValue: null,
                        newValue: sourceRoleId,
                    },
                ],
                metadata: {
                    userId: 'current-user',
                    userName: 'Current User',
                },
            });

            return result;
        } catch (error: any) {
            const endTime = new Date().toISOString();
            const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

            const result: BulkOperationResult = {
                operationId,
                operationType: BulkOperationType.CLONE_ROLE,
                status: BulkOperationStatus.FAILED,
                startTime,
                endTime,
                duration,
                totalItems: 1,
                successCount: 0,
                failureCount: 1,
                itemResults: [
                    {
                        itemId: newRoleId,
                        itemName: newDisplayName,
                        success: false,
                        error: error.message,
                    },
                ],
                errors: [error.message],
                canUndo: false,
            };

            store.addOperation(result);

            await AuditService.logEvent({
                action: AuditAction.ROLE_CLONED,
                resourceType: AuditResourceType.ROLE,
                resourceId: newRoleId,
                resourceName: newDisplayName,
                success: false,
                errorMessage: error.message,
                severity: AuditSeverity.ERROR,
                metadata: {
                    userId: 'current-user',
                    userName: 'Current User',
                },
            });

            return result;
        }
    }

    /**
     * Undo last operation
     */
    static async undo(): Promise<BulkOperationResult | null> {
        const entry = store.popUndoStack();
        if (!entry) {
            return null;
        }

        // TODO: Implement actual undo logic based on operation type
        // For now, just return a placeholder result
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const endTime = new Date().toISOString();

        const result: BulkOperationResult = {
            operationId,
            operationType: entry.operationType,
            status: BulkOperationStatus.COMPLETED,
            startTime,
            endTime,
            duration: 0,
            totalItems: 1,
            successCount: 1,
            failureCount: 0,
            itemResults: [],
            errors: [],
            canUndo: false,
        };

        await AuditService.logEvent({
            action: AuditAction.ROLE_UPDATED,
            resourceType: AuditResourceType.ROLE,
            resourceId: entry.operationId,
            resourceName: 'Undo operation',
            metadata: {
                userId: 'current-user',
                userName: 'Current User',
                reason: 'Undo operation',
            },
        });

        return result;
    }

    /**
     * Redo last undone operation
     */
    static async redo(): Promise<BulkOperationResult | null> {
        const entry = store.popRedoStack();
        if (!entry) {
            return null;
        }

        // TODO: Implement actual redo logic
        const operationId = uuidv4();
        const startTime = new Date().toISOString();
        const endTime = new Date().toISOString();

        const result: BulkOperationResult = {
            operationId,
            operationType: entry.operationType,
            status: BulkOperationStatus.COMPLETED,
            startTime,
            endTime,
            duration: 0,
            totalItems: 1,
            successCount: 1,
            failureCount: 0,
            itemResults: [],
            errors: [],
            canUndo: true,
        };

        await AuditService.logEvent({
            action: AuditAction.ROLE_UPDATED,
            resourceType: AuditResourceType.ROLE,
            resourceId: entry.operationId,
            resourceName: 'Redo operation',
            metadata: {
                userId: 'current-user',
                userName: 'Current User',
                reason: 'Redo operation',
            },
        });

        return result;
    }

    /**
     * Check if undo is available
     */
    static canUndo(): boolean {
        return store.canUndo();
    }

    /**
     * Check if redo is available
     */
    static canRedo(): boolean {
        return store.canRedo();
    }

    /**
     * Get operation by ID
     */
    static async getOperation(operationId: string): Promise<BulkOperationResult | null> {
        return store.getOperation(operationId) || null;
    }

    /**
     * Query operation history
     */
    static async queryHistory(
        filter?: BulkOperationHistoryFilter
    ): Promise<BulkOperationHistoryResult> {
        return store.getHistory(filter);
    }

    /**
     * Get statistics
     */
    static async getStats(): Promise<BulkOperationStats> {
        const history = store.getHistory({ limit: 1000 });
        const operations = history.operations;

        const operationsByType: Record<BulkOperationType, number> = {} as any;
        const operationsByStatus: Record<BulkOperationStatus, number> = {} as any;

        operations.forEach((op) => {
            operationsByType[op.operationType] = (operationsByType[op.operationType] || 0) + 1;
            operationsByStatus[op.status] = (operationsByStatus[op.status] || 0) + 1;
        });

        const successfulOps = operations.filter((op) => op.status === BulkOperationStatus.COMPLETED);
        const successRate = operations.length > 0 ? (successfulOps.length / operations.length) * 100 : 0;

        const typeEntries = Object.entries(operationsByType).map(([type, count]) => ({
            operationType: type as BulkOperationType,
            count,
        }));
        typeEntries.sort((a, b) => b.count - a.count);

        const recentFailures = operations
            .filter((op) => op.status === BulkOperationStatus.FAILED)
            .slice(0, 5);

        return {
            totalOperations: operations.length,
            operationsByType,
            operationsByStatus,
            averageDuration: 50, // TODO: Calculate from actual operations
            successRate,
            mostCommonOperations: typeEntries.slice(0, 5),
            recentFailures,
        };
    }

    /**
     * Clear all operations (for testing)
     */
    static clearAll(): void {
        store.clear();
    }
}
