/**
 * Comprehensive tests for Bulk Operations Service
 * 
 * Tests validate:
 * - Preview generation for all operation types
 * - Bulk permission assignment/revocation
 * - Bulk role updates
 * - Role cloning
 * - Undo/redo functionality
 * - Operation history tracking
 * - Statistics aggregation
 * - Error handling
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { BulkOperationsService } from '../bulkOperationsService';
import {
    BulkOperationType,
    BulkOperationStatus,
    BulkAssignPermissionsParams,
    BulkRevokePermissionsParams,
    BulkUpdateRolesParams,
    CloneRoleParams,
} from '@/types/bulkOperations';
import { AuditService } from '../auditService';

describe('BulkOperationsService', () => {
    beforeEach(() => {
        // Clear all operations before each test
        BulkOperationsService.clearAll();
        AuditService.clearAll();
    });

    afterEach(() => {
        // Clean up after each test
        BulkOperationsService.clearAll();
        AuditService.clearAll();
    });

    describe('Preview Operations', () => {
        it('should preview bulk permission assignment', async () => {
            // GIVEN: Parameters for assigning permissions to multiple roles
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1', 'role2', 'role3'],
                permissionIds: ['perm1', 'perm2'],
            };

            // WHEN: Previewing the operation
            const preview = await BulkOperationsService.previewAssignPermissions(params);

            // THEN: Preview should contain expected data
            expect(preview).toBeDefined();
            expect(preview.operationType).toBe(BulkOperationType.ASSIGN_PERMISSIONS);
            expect(preview.affectedItemsCount).toBe(3);
            expect(preview.affectedItems).toHaveLength(3);
            expect(preview.canProceed).toBe(true);
            expect(preview.estimatedDuration).toBeGreaterThan(0);
        });

        it('should preview bulk permission revocation', async () => {
            // GIVEN: Parameters for revoking permissions from multiple roles
            const params: BulkRevokePermissionsParams = {
                roleIds: ['role1', 'role2'],
                permissionIds: ['perm1'],
            };

            // WHEN: Previewing the operation
            const preview = await BulkOperationsService.previewRevokePermissions(params);

            // THEN: Preview should contain expected data
            expect(preview).toBeDefined();
            expect(preview.operationType).toBe(BulkOperationType.REVOKE_PERMISSIONS);
            expect(preview.affectedItemsCount).toBe(2);
            expect(preview.affectedItems).toHaveLength(2);
        });

        it('should preview bulk role updates', async () => {
            // GIVEN: Parameters for updating multiple roles
            const params: BulkUpdateRolesParams = {
                roleIds: ['role1', 'role2'],
                updates: {
                    displayName: 'Updated Role',
                    isActive: false,
                },
            };

            // WHEN: Previewing the operation
            const preview = await BulkOperationsService.previewUpdateRoles(params);

            // THEN: Preview should contain expected data
            expect(preview).toBeDefined();
            expect(preview.operationType).toBe(BulkOperationType.UPDATE_ROLES);
            expect(preview.affectedItemsCount).toBe(2);
            expect(preview.affectedItems[0].changes).toContain('Update display name to "Updated Role"');
            expect(preview.affectedItems[0].changes).toContain('Set active to false');
        });

        it('should preview role cloning', async () => {
            // GIVEN: Parameters for cloning a role
            const params: CloneRoleParams = {
                sourceRoleId: 'source-role',
                newRoleId: 'new-role',
                newDisplayName: 'New Role',
                newDescription: 'Cloned role',
                includePermissions: true,
                includeInheritance: true,
                includeMetadata: false,
            };

            // WHEN: Previewing the operation
            const preview = await BulkOperationsService.previewCloneRole(params);

            // THEN: Preview should contain expected data
            expect(preview).toBeDefined();
            expect(preview.operationType).toBe(BulkOperationType.CLONE_ROLE);
            expect(preview.affectedItemsCount).toBe(1);
            expect(preview.affectedItems[0].itemId).toBe('new-role');
            expect(preview.affectedItems[0].itemName).toBe('New Role');
            expect(preview.affectedItems[0].changes).toContain('Include permissions');
            expect(preview.affectedItems[0].changes).toContain('Include inheritance');
        });
    });

    describe('Execute Operations', () => {
        it('should execute bulk permission assignment', async () => {
            // GIVEN: Parameters for assigning permissions
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1', 'role2'],
                permissionIds: ['perm1', 'perm2'],
            };

            // WHEN: Executing the operation
            const result = await BulkOperationsService.assignPermissions(params);

            // THEN: Operation should complete successfully
            expect(result).toBeDefined();
            expect(result.operationType).toBe(BulkOperationType.ASSIGN_PERMISSIONS);
            expect(result.status).toBe(BulkOperationStatus.COMPLETED);
            expect(result.totalItems).toBe(2);
            expect(result.successCount).toBe(2);
            expect(result.failureCount).toBe(0);
            expect(result.canUndo).toBe(true);
            expect(result.itemResults).toHaveLength(2);
        });

        it('should execute bulk permission revocation', async () => {
            // GIVEN: Parameters for revoking permissions
            const params: BulkRevokePermissionsParams = {
                roleIds: ['role1', 'role2', 'role3'],
                permissionIds: ['perm1'],
            };

            // WHEN: Executing the operation
            const result = await BulkOperationsService.revokePermissions(params);

            // THEN: Operation should complete successfully
            expect(result).toBeDefined();
            expect(result.operationType).toBe(BulkOperationType.REVOKE_PERMISSIONS);
            expect(result.status).toBe(BulkOperationStatus.COMPLETED);
            expect(result.totalItems).toBe(3);
            expect(result.successCount).toBe(3);
            expect(result.canUndo).toBe(true);
        });

        it('should execute bulk role updates', async () => {
            // GIVEN: Parameters for updating roles
            const params: BulkUpdateRolesParams = {
                roleIds: ['role1', 'role2'],
                updates: {
                    displayName: 'Updated Role',
                    description: 'Updated description',
                    isActive: true,
                },
            };

            // WHEN: Executing the operation
            const result = await BulkOperationsService.updateRoles(params);

            // THEN: Operation should complete successfully
            expect(result).toBeDefined();
            expect(result.operationType).toBe(BulkOperationType.UPDATE_ROLES);
            expect(result.status).toBe(BulkOperationStatus.COMPLETED);
            expect(result.totalItems).toBe(2);
            expect(result.successCount).toBe(2);
        });

        it('should execute role cloning', async () => {
            // GIVEN: Parameters for cloning a role
            const params: CloneRoleParams = {
                sourceRoleId: 'source-role',
                newRoleId: 'new-role',
                newDisplayName: 'New Role',
                includePermissions: true,
                includeInheritance: false,
                includeMetadata: false,
            };

            // WHEN: Executing the operation
            const result = await BulkOperationsService.cloneRole(params);

            // THEN: Operation should complete successfully
            expect(result).toBeDefined();
            expect(result.operationType).toBe(BulkOperationType.CLONE_ROLE);
            expect(result.status).toBe(BulkOperationStatus.COMPLETED);
            expect(result.totalItems).toBe(1);
            expect(result.successCount).toBe(1);
            expect(result.canUndo).toBe(true);
        });

        it('should track progress during operation', async () => {
            // GIVEN: Parameters and progress callback
            const progressUpdates: number[] = [];
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1', 'role2', 'role3'],
                permissionIds: ['perm1'],
            };

            // WHEN: Executing with progress tracking
            await BulkOperationsService.assignPermissions(params, {
                onProgress: (progress) => {
                    progressUpdates.push(progress.percentComplete);
                },
            });

            // THEN: Progress should have been reported
            expect(progressUpdates.length).toBeGreaterThan(0);
            expect(progressUpdates[progressUpdates.length - 1]).toBe(100);
        });

        it('should log audit events for operations', async () => {
            // GIVEN: Parameters for operation
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            };

            // WHEN: Executing the operation
            await BulkOperationsService.assignPermissions(params);

            // THEN: Audit event should be logged
            const auditEvents = await AuditService.queryEvents({});
            expect(auditEvents.events.length).toBeGreaterThan(0);
            // Action format is lowercase with dots, not uppercase with underscores
            expect(auditEvents.events[0].action).toBe('bulk.permission.assign');
        });
    });

    describe('Undo/Redo', () => {
        it('should support undo after operation', async () => {
            // GIVEN: A completed operation
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            };
            await BulkOperationsService.assignPermissions(params);

            // WHEN: Checking if undo is available
            const canUndo = BulkOperationsService.canUndo();

            // THEN: Undo should be available
            expect(canUndo).toBe(true);
        });

        it('should support redo after undo', async () => {
            // GIVEN: A completed operation that was undone
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            };
            await BulkOperationsService.assignPermissions(params);
            await BulkOperationsService.undo();

            // WHEN: Checking if redo is available
            const canRedo = BulkOperationsService.canRedo();

            // THEN: Redo should be available
            expect(canRedo).toBe(true);
        });

        it('should execute undo operation', async () => {
            // GIVEN: A completed operation
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            };
            await BulkOperationsService.assignPermissions(params);

            // WHEN: Executing undo
            const undoResult = await BulkOperationsService.undo();

            // THEN: Undo should complete
            expect(undoResult).toBeDefined();
            expect(undoResult?.status).toBe(BulkOperationStatus.COMPLETED);
        });

        it('should execute redo operation', async () => {
            // GIVEN: A completed operation that was undone
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            };
            await BulkOperationsService.assignPermissions(params);
            await BulkOperationsService.undo();

            // WHEN: Executing redo
            const redoResult = await BulkOperationsService.redo();

            // THEN: Redo should complete
            expect(redoResult).toBeDefined();
            expect(redoResult?.status).toBe(BulkOperationStatus.COMPLETED);
        });

        it('should return null when undo is not available', async () => {
            // GIVEN: No operations

            // WHEN: Attempting to undo
            const undoResult = await BulkOperationsService.undo();

            // THEN: Should return null
            expect(undoResult).toBeNull();
        });

        it('should return null when redo is not available', async () => {
            // GIVEN: No undone operations

            // WHEN: Attempting to redo
            const redoResult = await BulkOperationsService.redo();

            // THEN: Should return null
            expect(redoResult).toBeNull();
        });
    });

    describe('Operation History', () => {
        it('should track operation history', async () => {
            // GIVEN: Multiple completed operations
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });
            await BulkOperationsService.revokePermissions({
                roleIds: ['role2'],
                permissionIds: ['perm2'],
            });

            // WHEN: Querying history
            const history = await BulkOperationsService.queryHistory();

            // THEN: History should contain operations
            expect(history.operations.length).toBe(2);
            expect(history.total).toBe(2);
        });

        it('should filter history by operation type', async () => {
            // GIVEN: Mixed operation types
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });
            await BulkOperationsService.revokePermissions({
                roleIds: ['role2'],
                permissionIds: ['perm2'],
            });

            // WHEN: Filtering by ASSIGN_PERMISSIONS
            const history = await BulkOperationsService.queryHistory({
                operationTypes: [BulkOperationType.ASSIGN_PERMISSIONS],
            });

            // THEN: Only assign operations should be returned
            expect(history.operations.length).toBe(1);
            expect(history.operations[0].operationType).toBe(BulkOperationType.ASSIGN_PERMISSIONS);
        });

        it('should filter history by status', async () => {
            // GIVEN: Operations with different statuses
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });

            // WHEN: Filtering by COMPLETED status
            const history = await BulkOperationsService.queryHistory({
                statuses: [BulkOperationStatus.COMPLETED],
            });

            // THEN: Only completed operations should be returned
            expect(history.operations.length).toBeGreaterThan(0);
            expect(history.operations[0].status).toBe(BulkOperationStatus.COMPLETED);
        });

        it('should support pagination', async () => {
            // GIVEN: Multiple operations
            for (let i = 0; i < 5; i++) {
                await BulkOperationsService.assignPermissions({
                    roleIds: [`role${i}`],
                    permissionIds: ['perm1'],
                });
            }

            // WHEN: Querying with limit
            const history = await BulkOperationsService.queryHistory({
                limit: 2,
                offset: 0,
            });

            // THEN: Should return limited results
            expect(history.operations.length).toBe(2);
            expect(history.hasMore).toBe(true);
            expect(history.total).toBe(5);
        });

        it('should get specific operation by ID', async () => {
            // GIVEN: A completed operation
            const result = await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });

            // WHEN: Getting operation by ID
            const operation = await BulkOperationsService.getOperation(result.operationId);

            // THEN: Should return the operation
            expect(operation).toBeDefined();
            expect(operation?.operationId).toBe(result.operationId);
        });
    });

    describe('Statistics', () => {
        it('should calculate statistics', async () => {
            // GIVEN: Multiple operations
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });
            await BulkOperationsService.revokePermissions({
                roleIds: ['role2'],
                permissionIds: ['perm2'],
            });

            // WHEN: Getting statistics
            const stats = await BulkOperationsService.getStats();

            // THEN: Statistics should be calculated
            expect(stats).toBeDefined();
            expect(stats.totalOperations).toBe(2);
            expect(stats.successRate).toBeGreaterThan(0);
            expect(stats.mostCommonOperations.length).toBeGreaterThan(0);
        });

        it('should track operations by type', async () => {
            // GIVEN: Operations of different types
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });
            await BulkOperationsService.assignPermissions({
                roleIds: ['role2'],
                permissionIds: ['perm2'],
            });
            await BulkOperationsService.revokePermissions({
                roleIds: ['role3'],
                permissionIds: ['perm3'],
            });

            // WHEN: Getting statistics
            const stats = await BulkOperationsService.getStats();

            // THEN: Should count by type
            expect(stats.operationsByType[BulkOperationType.ASSIGN_PERMISSIONS]).toBe(2);
            expect(stats.operationsByType[BulkOperationType.REVOKE_PERMISSIONS]).toBe(1);
        });

        it('should calculate success rate', async () => {
            // GIVEN: All successful operations
            await BulkOperationsService.assignPermissions({
                roleIds: ['role1'],
                permissionIds: ['perm1'],
            });

            // WHEN: Getting statistics
            const stats = await BulkOperationsService.getStats();

            // THEN: Success rate should be 100%
            expect(stats.successRate).toBe(100);
        });
    });

    describe('Error Handling', () => {
        it('should handle empty role IDs', async () => {
            // GIVEN: Empty role IDs
            const params: BulkAssignPermissionsParams = {
                roleIds: [],
                permissionIds: ['perm1'],
            };

            // WHEN: Executing operation
            const result = await BulkOperationsService.assignPermissions(params);

            // THEN: Should complete with 0 items
            expect(result.totalItems).toBe(0);
            expect(result.successCount).toBe(0);
        });

        it('should handle empty permission IDs', async () => {
            // GIVEN: Empty permission IDs
            const params: BulkAssignPermissionsParams = {
                roleIds: ['role1'],
                permissionIds: [],
            };

            // WHEN: Previewing operation
            const preview = await BulkOperationsService.previewAssignPermissions(params);

            // THEN: Preview should reflect empty permissions
            expect(preview.affectedItems[0].changes).toHaveLength(0);
        });
    });
});
