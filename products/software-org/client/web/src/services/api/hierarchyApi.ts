/**
 * Hierarchy Operations API Client
 *
 * <p><b>Purpose</b><br>
 * API client for organizational hierarchy operations including
 * create, move, merge, split, promote, demote, and transfer.
 *
 * <p><b>Endpoints</b><br>
 * - POST /hierarchy/operations - Create hierarchy operation
 * - GET /hierarchy/operations/:id - Get operation status
 * - GET /hierarchy/operations - List operations (with filters)
 * - POST /hierarchy/operations/:id/approve - Approve operation
 * - POST /hierarchy/operations/:id/reject - Reject operation
 * - DELETE /hierarchy/operations/:id - Cancel operation
 *
 * <p><b>Backend Integration</b><br>
 * These endpoints route to the Java domain service for validation
 * and execution of hierarchy operations.
 *
 * @doc.type service
 * @doc.purpose Hierarchy operations API client
 * @doc.layer product
 * @doc.pattern API Client
 */

import { apiClient } from './index';
import type { HierarchyLayer } from '@/state/atoms/persona.atoms';

// ============================================================================
// Types
// ============================================================================

/**
 * Operation types for hierarchy changes
 */
export type HierarchyOperationType =
    | 'CREATE'
    | 'MOVE'
    | 'MERGE'
    | 'SPLIT'
    | 'PROMOTE'
    | 'DEMOTE'
    | 'TRANSFER'
    | 'DELETE';

/**
 * Target types for hierarchy operations
 */
export type HierarchyTargetType =
    | 'ORGANIZATION'
    | 'DEPARTMENT'
    | 'TEAM'
    | 'ROLE'
    | 'PERSON';

/**
 * Operation status
 */
export type HierarchyOperationStatus =
    | 'PENDING'
    | 'AWAITING_APPROVAL'
    | 'APPROVED'
    | 'REJECTED'
    | 'IN_PROGRESS'
    | 'COMPLETED'
    | 'FAILED'
    | 'CANCELLED';

/**
 * Hierarchy operation model
 */
export interface HierarchyOperation {
    operationId: string;
    type: HierarchyOperationType;
    targetId: string;
    targetType: HierarchyTargetType;
    initiatorId: string;
    initiatorLayer: HierarchyLayer;
    parameters: Record<string, unknown>;
    status: HierarchyOperationStatus;
    approvalId?: string;
    createdAt: string;
    updatedAt: string;
}

/**
 * Input for creating a hierarchy operation
 */
export interface CreateHierarchyOperationInput {
    type: HierarchyOperationType;
    targetId: string;
    targetType: HierarchyTargetType;
    parameters: Record<string, unknown>;
}

/**
 * API response wrapper
 */
export interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    timestamp: string;
}

/**
 * Validation result from backend
 */
export interface ValidationResult {
    valid: boolean;
    errors: string[];
    warnings: string[];
}

// ============================================================================
// API Functions
// ============================================================================

/**
 * Create a new hierarchy operation
 *
 * @param input - Operation input data
 * @returns Created operation with status
 */
export async function createHierarchyOperation(
    input: CreateHierarchyOperationInput
): Promise<HierarchyOperation> {
    const response = await apiClient.post<ApiResponse<HierarchyOperation>>(
        '/hierarchy/operations',
        input
    );
    return response.data.data;
}

/**
 * Get a hierarchy operation by ID
 *
 * @param operationId - Operation identifier
 * @returns Operation details
 */
export async function getHierarchyOperation(
    operationId: string
): Promise<HierarchyOperation> {
    const response = await apiClient.get<ApiResponse<HierarchyOperation>>(
        `/hierarchy/operations/${operationId}`
    );
    return response.data.data;
}

/**
 * List hierarchy operations with optional filters
 *
 * @param filters - Optional filters
 * @returns List of operations
 */
export async function listHierarchyOperations(filters?: {
    status?: HierarchyOperationStatus;
    type?: HierarchyOperationType;
    targetType?: HierarchyTargetType;
    initiatorId?: string;
    limit?: number;
    offset?: number;
}): Promise<HierarchyOperation[]> {
    const params = new URLSearchParams();
    if (filters?.status) params.append('status', filters.status);
    if (filters?.type) params.append('type', filters.type);
    if (filters?.targetType) params.append('targetType', filters.targetType);
    if (filters?.initiatorId) params.append('initiatorId', filters.initiatorId);
    if (filters?.limit) params.append('limit', String(filters.limit));
    if (filters?.offset) params.append('offset', String(filters.offset));

    const response = await apiClient.get<ApiResponse<HierarchyOperation[]>>(
        `/hierarchy/operations?${params.toString()}`
    );
    return response.data.data;
}

/**
 * Get pending operations awaiting approval
 *
 * @returns List of pending operations
 */
export async function getPendingApprovals(): Promise<HierarchyOperation[]> {
    return listHierarchyOperations({ status: 'AWAITING_APPROVAL' });
}

/**
 * Approve a hierarchy operation
 *
 * @param operationId - Operation identifier
 * @param comment - Optional approval comment
 * @returns Updated operation
 */
export async function approveOperation(
    operationId: string,
    comment?: string
): Promise<HierarchyOperation> {
    const response = await apiClient.post<ApiResponse<HierarchyOperation>>(
        `/hierarchy/operations/${operationId}/approve`,
        { comment }
    );
    return response.data.data;
}

/**
 * Reject a hierarchy operation
 *
 * @param operationId - Operation identifier
 * @param reason - Rejection reason
 * @returns Updated operation
 */
export async function rejectOperation(
    operationId: string,
    reason: string
): Promise<HierarchyOperation> {
    const response = await apiClient.post<ApiResponse<HierarchyOperation>>(
        `/hierarchy/operations/${operationId}/reject`,
        { reason }
    );
    return response.data.data;
}

/**
 * Cancel a pending hierarchy operation
 *
 * @param operationId - Operation identifier
 * @returns True if cancelled
 */
export async function cancelOperation(operationId: string): Promise<boolean> {
    const response = await apiClient.delete<ApiResponse<{ cancelled: boolean }>>(
        `/hierarchy/operations/${operationId}`
    );
    return response.data.data.cancelled;
}

/**
 * Validate a hierarchy operation before submission
 *
 * @param input - Operation input to validate
 * @returns Validation result
 */
export async function validateOperation(
    input: CreateHierarchyOperationInput
): Promise<ValidationResult> {
    const response = await apiClient.post<ApiResponse<ValidationResult>>(
        '/hierarchy/operations/validate',
        input
    );
    return response.data.data;
}

// ============================================================================
// Convenience Functions for Common Operations
// ============================================================================

/**
 * Create a new department
 */
export async function createDepartment(params: {
    name: string;
    parentId: string;
    description?: string;
}): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'CREATE',
        targetId: 'new-department',
        targetType: 'DEPARTMENT',
        parameters: params,
    });
}

/**
 * Create a new team
 */
export async function createTeam(params: {
    name: string;
    departmentId: string;
    description?: string;
}): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'CREATE',
        targetId: 'new-team',
        targetType: 'TEAM',
        parameters: {
            ...params,
            parentId: params.departmentId,
        },
    });
}

/**
 * Move a team to a different department
 */
export async function moveTeam(
    teamId: string,
    newDepartmentId: string
): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'MOVE',
        targetId: teamId,
        targetType: 'TEAM',
        parameters: { newParentId: newDepartmentId },
    });
}

/**
 * Promote a person to a higher role
 */
export async function promotePerson(params: {
    personId: string;
    currentRole: string;
    currentLayer: HierarchyLayer;
    newRole: string;
    newLayer: HierarchyLayer;
    justification?: string;
}): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'PROMOTE',
        targetId: params.personId,
        targetType: 'PERSON',
        parameters: {
            currentRole: params.currentRole,
            currentLayer: params.currentLayer,
            newRole: params.newRole,
            newLayer: params.newLayer,
            justification: params.justification,
        },
    });
}

/**
 * Transfer a person to a different team
 */
export async function transferPerson(
    personId: string,
    newTeamId: string
): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'TRANSFER',
        targetId: personId,
        targetType: 'PERSON',
        parameters: { newTeamId },
    });
}

/**
 * Merge two teams into one
 */
export async function mergeTeams(params: {
    sourceTeamIds: string[];
    targetName: string;
    departmentId: string;
}): Promise<HierarchyOperation> {
    return createHierarchyOperation({
        type: 'MERGE',
        targetId: params.sourceTeamIds[0],
        targetType: 'TEAM',
        parameters: {
            sourceIds: params.sourceTeamIds,
            targetName: params.targetName,
            parentId: params.departmentId,
        },
    });
}
