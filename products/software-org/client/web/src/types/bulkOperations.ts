/**
 * Type definitions for bulk operations on roles and permissions
 * 
 * Provides types for:
 * - Bulk permission assignment/revocation
 * - Bulk role updates
 * - Role cloning
 * - Operation history and undo/redo
 * - Preview and validation
 */

/**
 * Types of bulk operations supported
 */
export enum BulkOperationType {
    ASSIGN_PERMISSIONS = 'ASSIGN_PERMISSIONS',
    REVOKE_PERMISSIONS = 'REVOKE_PERMISSIONS',
    UPDATE_ROLES = 'UPDATE_ROLES',
    CLONE_ROLE = 'CLONE_ROLE',
    DELETE_ROLES = 'DELETE_ROLES',
    ADD_INHERITANCE = 'ADD_INHERITANCE',
    REMOVE_INHERITANCE = 'REMOVE_INHERITANCE',
}

/**
 * Status of a bulk operation
 */
export enum BulkOperationStatus {
    PENDING = 'PENDING',
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
    PARTIAL_SUCCESS = 'PARTIAL_SUCCESS',
    CANCELLED = 'CANCELLED',
}

/**
 * Severity of validation issues
 */
export enum ValidationSeverity {
    ERROR = 'ERROR',
    WARNING = 'WARNING',
    INFO = 'INFO',
}

/**
 * Validation issue found during preview
 */
export interface ValidationIssue {
    severity: ValidationSeverity;
    message: string;
    affectedItems: string[];
    suggestion?: string;
}

/**
 * Result of a single item in a bulk operation
 */
export interface BulkOperationItemResult {
    itemId: string;
    itemName?: string;
    success: boolean;
    error?: string;
    previousState?: unknown;
    newState?: unknown;
}

/**
 * Parameters for bulk permission assignment
 */
export interface BulkAssignPermissionsParams {
    roleIds: string[];
    permissionIds: string[];
    metadata?: {
        reason?: string;
        requestedBy?: string;
        approvedBy?: string;
    };
}

/**
 * Parameters for bulk permission revocation
 */
export interface BulkRevokePermissionsParams {
    roleIds: string[];
    permissionIds: string[];
    metadata?: {
        reason?: string;
        requestedBy?: string;
        approvedBy?: string;
    };
}

/**
 * Parameters for bulk role updates
 */
export interface BulkUpdateRolesParams {
    roleIds: string[];
    updates: {
        displayName?: string;
        description?: string;
        isActive?: boolean;
        metadata?: Record<string, unknown>;
    };
}

/**
 * Parameters for role cloning
 */
export interface CloneRoleParams {
    sourceRoleId: string;
    newRoleId: string;
    newDisplayName: string;
    newDescription?: string;
    includePermissions: boolean;
    includeInheritance: boolean;
    includeMetadata: boolean;
}

/**
 * Preview of a bulk operation before execution
 */
export interface BulkOperationPreview {
    operationType: BulkOperationType;
    affectedItemsCount: number;
    affectedItems: Array<{
        itemId: string;
        itemName: string;
        itemType: 'role' | 'permission' | 'inheritance';
        currentState?: unknown;
        proposedState?: unknown;
        changes: string[];
    }>;
    validationIssues: ValidationIssue[];
    estimatedDuration: number; // milliseconds
    canProceed: boolean;
    warnings: string[];
}

/**
 * Result of a bulk operation execution
 */
export interface BulkOperationResult {
    operationId: string;
    operationType: BulkOperationType;
    status: BulkOperationStatus;
    startTime: string;
    endTime?: string;
    duration?: number; // milliseconds
    totalItems: number;
    successCount: number;
    failureCount: number;
    itemResults: BulkOperationItemResult[];
    errors: string[];
    canUndo: boolean;
    undoData?: unknown;
}

/**
 * Bulk operation in history
 */
export interface BulkOperationHistoryEntry {
    operationId: string;
    operationType: BulkOperationType;
    status: BulkOperationStatus;
    timestamp: string;
    userId: string;
    userName?: string;
    description: string;
    affectedItemsCount: number;
    successCount: number;
    failureCount: number;
    canUndo: boolean;
    canRedo: boolean;
}

/**
 * Undo/redo stack entry
 */
export interface UndoRedoEntry {
    operationId: string;
    operationType: BulkOperationType;
    timestamp: string;
    undoData: unknown;
    redoData: unknown;
    description: string;
}

/**
 * Bulk operation progress event
 */
export interface BulkOperationProgress {
    operationId: string;
    status: BulkOperationStatus;
    currentItem: number;
    totalItems: number;
    percentComplete: number;
    estimatedTimeRemaining?: number; // milliseconds
    currentItemName?: string;
    errors: string[];
}

/**
 * Options for bulk operation execution
 */
export interface BulkOperationOptions {
    dryRun?: boolean;
    stopOnError?: boolean;
    batchSize?: number;
    delayBetweenBatches?: number; // milliseconds
    onProgress?: (progress: BulkOperationProgress) => void;
    validateBefore?: boolean;
}

/**
 * Filter for bulk operation history
 */
export interface BulkOperationHistoryFilter {
    operationTypes?: BulkOperationType[];
    statuses?: BulkOperationStatus[];
    userIds?: string[];
    startDate?: string;
    endDate?: string;
    limit?: number;
    offset?: number;
}

/**
 * Query result for bulk operation history
 */
export interface BulkOperationHistoryResult {
    operations: BulkOperationHistoryEntry[];
    total: number;
    hasMore: boolean;
}

/**
 * Statistics for bulk operations
 */
export interface BulkOperationStats {
    totalOperations: number;
    operationsByType: Record<BulkOperationType, number>;
    operationsByStatus: Record<BulkOperationStatus, number>;
    averageDuration: number;
    successRate: number;
    mostCommonOperations: Array<{
        operationType: BulkOperationType;
        count: number;
    }>;
    recentFailures: BulkOperationHistoryEntry[];
}
