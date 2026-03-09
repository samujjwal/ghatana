/**
 * Integration tests for MockPersonaRoleService
 *
 * Purpose:
 * Validates mock service matches Java domain logic exactly.
 *
 * Test Coverage:
 * - Role definitions (14 roles with correct permissions)
 * - Validation rules (min 1, max 5, admin+viewer incompatible)
 * - Permission resolution (union + inheritance)
 * - Parent role inheritance (backend-developer → developer)
 * - Multi-level inheritance (architect → tech-lead)
 *
 * CRITICAL:
 * These tests validate the mock matches Java behavior. If Java tests
 * change, these tests MUST change to match. See PersonaRoleServiceTest.java
 * for canonical behavior.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { MockPersonaRoleService } from '../persona-role-mock.service.js';

describe('MockPersonaRoleService', () => {
    let service: MockPersonaRoleService;

    beforeEach(() => {
        service = new MockPersonaRoleService();
    });

    describe('getAllRoles', () => {
        it('should return all 14 default roles', async () => {
            const roles = await service.getAllRoles();

            expect(roles).toHaveLength(14);

            // Check base roles (4)
            const baseRoles = roles.filter((r) => r.type === 'BASE');
            expect(baseRoles).toHaveLength(4);
            expect(baseRoles.map((r) => r.roleId)).toEqual(
                expect.arrayContaining(['admin', 'tech-lead', 'developer', 'viewer'])
            );

            // Check specialized roles (10)
            const specializedRoles = roles.filter((r) => r.type === 'SPECIALIZED');
            expect(specializedRoles).toHaveLength(10);
            expect(specializedRoles.map((r) => r.roleId)).toEqual(
                expect.arrayContaining([
                    'fullstack-developer',
                    'backend-developer',
                    'frontend-developer',
                    'devops-engineer',
                    'qa-engineer',
                    'product-manager',
                    'designer',
                    'data-analyst',
                    'security-engineer',
                    'architect',
                ])
            );
        });

        it('should have correct admin role definition', async () => {
            const roles = await service.getAllRoles();
            const admin = roles.find((r) => r.roleId === 'admin');

            expect(admin).toBeDefined();
            expect(admin?.displayName).toBe('Administrator');
            expect(admin?.type).toBe('BASE');
            expect(admin?.permissions).toContain('workspace.manage');
            expect(admin?.permissions).toContain('deployment.production');
            expect(admin?.capabilities).toContain('viewAllProjects');
            expect(admin?.capabilities).toContain('deployProduction');
            expect(admin?.parentRoles).toEqual([]);
        });

        it('should have correct backend-developer inheriting from developer', async () => {
            const roles = await service.getAllRoles();
            const backendDev = roles.find((r) => r.roleId === 'backend-developer');

            expect(backendDev).toBeDefined();
            expect(backendDev?.type).toBe('SPECIALIZED');
            expect(backendDev?.parentRoles).toEqual(['developer']);
            expect(backendDev?.permissions).toContain('code.write');
            expect(backendDev?.permissions).toContain('database.read');
        });

        it('should have correct architect inheriting from tech-lead', async () => {
            const roles = await service.getAllRoles();
            const architect = roles.find((r) => r.roleId === 'architect');

            expect(architect).toBeDefined();
            expect(architect?.type).toBe('SPECIALIZED');
            expect(architect?.parentRoles).toEqual(['tech-lead']);
            expect(architect?.permissions).toContain('architecture.review');
            expect(architect?.permissions).toContain('architecture.design');
        });
    });

    describe('getRoleDefinition', () => {
        it('should return role for valid ID', async () => {
            const role = await service.getRoleDefinition('tech-lead');

            expect(role).toBeDefined();
            expect(role?.roleId).toBe('tech-lead');
            expect(role?.displayName).toBe('Tech Lead');
            expect(role?.permissions).toContain('code.approve');
        });

        it('should return null for unknown role ID', async () => {
            const role = await service.getRoleDefinition('unknown-role');

            expect(role).toBeNull();
        });
    });

    describe('validateRoleActivation', () => {
        it('should accept single role activation', async () => {
            const result = await service.validateRoleActivation(['developer']);

            expect(result.isValid).toBe(true);
            expect(result.errorMessage).toBeUndefined();
        });

        it('should accept multiple valid roles', async () => {
            const result = await service.validateRoleActivation([
                'tech-lead',
                'backend-developer',
            ]);

            expect(result.isValid).toBe(true);
        });

        it('should reject empty role list', async () => {
            const result = await service.validateRoleActivation([]);

            expect(result.isValid).toBe(false);
            expect(result.errorMessage).toBe('At least one role must be activated');
        });

        it('should reject more than 5 roles', async () => {
            const result = await service.validateRoleActivation([
                'developer',
                'backend-developer',
                'frontend-developer',
                'devops-engineer',
                'qa-engineer',
                'security-engineer', // 6th role
            ]);

            expect(result.isValid).toBe(false);
            expect(result.errorMessage).toBe('Maximum 5 roles can be activated');
        });

        it('should reject unknown role ID', async () => {
            const result = await service.validateRoleActivation([
                'developer',
                'unknown-role',
            ]);

            expect(result.isValid).toBe(false);
            expect(result.errorMessage).toBe('Unknown role: unknown-role');
        });

        it('should reject incompatible admin + viewer combination', async () => {
            const result = await service.validateRoleActivation(['admin', 'viewer']);

            expect(result.isValid).toBe(false);
            expect(result.errorMessage).toBe('Admin and Viewer roles are incompatible');
        });

        it('should accept 5 valid roles (max limit)', async () => {
            const result = await service.validateRoleActivation([
                'developer',
                'backend-developer',
                'frontend-developer',
                'devops-engineer',
                'qa-engineer',
            ]);

            expect(result.isValid).toBe(true);
        });
    });

    describe('resolveEffectivePermissions', () => {
        it('should resolve permissions from single role', async () => {
            const permissions = await service.resolveEffectivePermissions(['developer']);

            expect(permissions.permissions['code.write']).toBe(true);
            expect(permissions.permissions['code.review']).toBe(true);
            expect(permissions.permissions['deployment.dev']).toBe(true);
            expect(permissions.permissions['deployment.production']).toBeUndefined();
            expect(permissions.capabilities['submitCode']).toBe(true);
        });

        it('should resolve union of permissions from multiple roles', async () => {
            const permissions = await service.resolveEffectivePermissions([
                'tech-lead',
                'backend-developer',
            ]);

            // Tech-lead permissions
            expect(permissions.permissions['code.approve']).toBe(true);
            expect(permissions.permissions['architecture.review']).toBe(true);

            // Backend-developer permissions
            expect(permissions.permissions['code.write']).toBe(true);
            expect(permissions.permissions['database.read']).toBe(true);

            // Inherited from developer (parent of backend-developer)
            expect(permissions.permissions['code.review']).toBe(true);
            expect(permissions.permissions['deployment.dev']).toBe(true);

            // Tech-lead capabilities
            expect(permissions.capabilities['approveCodeReviews']).toBe(true);

            // Backend-developer capabilities
            expect(permissions.capabilities['submitCode']).toBe(true);
            expect(permissions.capabilities['queryDatabase']).toBe(true);
        });

        it('should resolve inherited permissions from parent roles', async () => {
            const permissions = await service.resolveEffectivePermissions([
                'backend-developer',
            ]);

            // Direct permissions
            expect(permissions.permissions['database.read']).toBe(true);

            // Inherited from developer parent
            expect(permissions.permissions['code.write']).toBe(true);
            expect(permissions.permissions['code.review']).toBe(true);
            expect(permissions.permissions['deployment.dev']).toBe(true);
            expect(permissions.capabilities['submitCode']).toBe(true);
        });

        it('should resolve architect inheriting from tech-lead', async () => {
            const permissions = await service.resolveEffectivePermissions(['architect']);

            // Direct permissions
            expect(permissions.permissions['architecture.design']).toBe(true);

            // Inherited from tech-lead
            expect(permissions.permissions['code.approve']).toBe(true);
            expect(permissions.permissions['architecture.review']).toBe(true);
            expect(permissions.capabilities['approveCodeReviews']).toBe(true);
        });

        it('should handle empty permissions for unknown roles', async () => {
            const permissions = await service.resolveEffectivePermissions([
                'unknown-role',
            ]);

            expect(Object.keys(permissions.permissions)).toHaveLength(0);
            expect(Object.keys(permissions.capabilities)).toHaveLength(0);
        });
    });

    describe('hasPermission', () => {
        it('should return true for existing permission', async () => {
            const result = await service.hasPermission(['developer'], 'code.write');

            expect(result).toBe(true);
        });

        it('should return false for non-existing permission', async () => {
            const result = await service.hasPermission(
                ['developer'],
                'deployment.production'
            );

            expect(result).toBe(false);
        });

        it('should return true for inherited permission', async () => {
            const result = await service.hasPermission(
                ['backend-developer'],
                'code.write'
            );

            expect(result).toBe(true); // Inherited from developer
        });
    });

    describe('hasCapability', () => {
        it('should return true for existing capability', async () => {
            const result = await service.hasCapability(['tech-lead'], 'approveCodeReviews');

            expect(result).toBe(true);
        });

        it('should return false for non-existing capability', async () => {
            const result = await service.hasCapability(['developer'], 'deployProduction');

            expect(result).toBe(false);
        });

        it('should return true for inherited capability', async () => {
            const result = await service.hasCapability(['architect'], 'approveCodeReviews');

            expect(result).toBe(true); // Inherited from tech-lead
        });
    });
});
