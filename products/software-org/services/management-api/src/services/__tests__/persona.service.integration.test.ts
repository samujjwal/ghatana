/**
 * Integration tests for persona.service.ts with MockPersonaRoleService
 *
 * Purpose:
 * Validates persona service correctly integrates with role domain service
 * for validation before saving user preferences to database.
 *
 * Test Strategy:
 * - Use MockPersonaRoleService (not HTTP client)
 * - Mock Prisma database operations
 * - Test validation flow: roles validated → preferences saved
 * - Test error handling: validation fails → no database write
 *
 * Architecture Validation:
 * ✅ Node.js validates with Java domain service before persisting
 * ✅ Node.js does NOT implement validation logic itself
 * ✅ Node.js handles user preferences persistence (its responsibility)
 * ✅ Validation errors propagate correctly to API layer
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import * as personaService from '../persona.service.js';

// Mock db client (Prisma singleton)
const mockPrisma = {
    personaPreference: {
        findUnique: vi.fn(),
        upsert: vi.fn(),
        delete: vi.fn(),
        findMany: vi.fn(),
    },
    workspace: {
        findFirst: vi.fn(),
    },
};

vi.mock('../db/client.js', () => ({
    prisma: mockPrisma,
}));

// Mock role client to return MockPersonaRoleService
vi.mock('../persona-role-domain.client.js', async () => {
    const { MockPersonaRoleService } = await import('../persona-role-mock.service.js');
    const mockRoleService = new MockPersonaRoleService();

    return {
        getPersonaRoleDomainClient: () => mockRoleService,
        ValidationError: class ValidationError extends Error {
            constructor(message: string) {
                super(message);
                this.name = 'ValidationError';
            }
        },
    };
});

describe('PersonaService Integration Tests', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('upsertPersonaPreference - Validation Flow', () => {
        it('should save preferences when mock validation succeeds', async () => {
            // GIVEN: Valid role combination
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const validRoles = ['developer', 'backend-developer'];

            // Mock database returns success
            mockPrisma.personaPreference.upsert.mockResolvedValue({
                id: 'config-1',
                userId,
                workspaceId,
                activeRoles: validRoles,
                preferences: {},
                createdAt: new Date(),
                updatedAt: new Date(),
            });

            // WHEN: Upsert persona preference
            const result = await personaService.upsertPersonaPreference(userId, workspaceId, {
                activeRoles: validRoles,
                preferences: {},
            });

            // THEN: Validation passed → database write called
            expect(result).toBeDefined();
            expect(result.activeRoles).toEqual(validRoles);
            expect(mockPrisma.personaPreference.upsert).toHaveBeenCalledTimes(1);
        });

        it('should reject preferences when validation fails (empty role list)', async () => {
            // GIVEN: Empty role list (violates min 1 role rule)
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const invalidRoles: string[] = [];

            // WHEN: Upsert with empty roles
            // THEN: Should throw validation error
            await expect(
                personaService.upsertPersonaPreference(userId, workspaceId, {
                    activeRoles: invalidRoles,
                    preferences: {},
                })
            ).rejects.toThrow('At least one role must be activated');

            // Database write should NOT be called
            expect(mockPrisma.personaPreference.upsert).not.toHaveBeenCalled();
        });

        it('should reject preferences when validation fails (>5 roles)', async () => {
            // GIVEN: 6 roles (violates max 5 roles rule)
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const tooManyRoles = [
                'developer',
                'backend-developer',
                'frontend-developer',
                'devops-engineer',
                'qa-engineer',
                'security-engineer', // 6th role
            ];

            // WHEN: Upsert with too many roles
            // THEN: Should throw validation error
            await expect(
                personaService.upsertPersonaPreference(userId, workspaceId, {
                    activeRoles: tooManyRoles,
                    preferences: {},
                })
            ).rejects.toThrow('Maximum 5 roles can be activated');

            // Database write should NOT be called
            expect(mockPrisma.personaPreference.upsert).not.toHaveBeenCalled();
        });

        it('should reject preferences when validation fails (admin+viewer)', async () => {
            // GIVEN: Admin + Viewer (incompatible combination)
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const incompatibleRoles = ['admin', 'viewer'];

            // WHEN: Upsert with incompatible roles
            // THEN: Should throw validation error
            await expect(
                personaService.upsertPersonaPreference(userId, workspaceId, {
                    activeRoles: incompatibleRoles,
                    preferences: {},
                })
            ).rejects.toThrow('Admin and Viewer roles are incompatible');

            // Database write should NOT be called
            expect(mockPrisma.personaPreference.upsert).not.toHaveBeenCalled();
        });

        it('should reject preferences when validation fails (unknown role)', async () => {
            // GIVEN: Unknown role ID
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const unknownRoles = ['developer', 'unknown-role'];

            // WHEN: Upsert with unknown role
            // THEN: Should throw validation error
            await expect(
                personaService.upsertPersonaPreference(userId, workspaceId, {
                    activeRoles: unknownRoles,
                    preferences: {},
                })
            ).rejects.toThrow('Unknown role: unknown-role');

            // Database write should NOT be called
            expect(mockPrisma.personaPreference.upsert).not.toHaveBeenCalled();
        });

        it('should accept maximum 5 valid roles', async () => {
            // GIVEN: Exactly 5 valid roles (at max limit)
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const maxValidRoles = [
                'developer',
                'backend-developer',
                'frontend-developer',
                'devops-engineer',
                'qa-engineer',
            ];

            // Mock database returns success
            mockPrisma.personaPreference.upsert.mockResolvedValue({
                id: 'config-1',
                userId,
                workspaceId,
                activeRoles: maxValidRoles,
                preferences: {},
                createdAt: new Date(),
                updatedAt: new Date(),
            });

            // WHEN: Upsert with 5 roles
            const result = await personaService.upsertPersonaPreference(userId, workspaceId, {
                activeRoles: maxValidRoles,
                preferences: {},
            });

            // THEN: Validation passed → database write called
            expect(result).toBeDefined();
            expect(result.activeRoles).toEqual(maxValidRoles);
            expect(mockPrisma.personaPreference.upsert).toHaveBeenCalledTimes(1);
        });
    });

    describe('getPersonaPreference', () => {
        it('should return preference when found', async () => {
            // GIVEN: User has saved preference
            const userId = 'user-123';
            const workspaceId = 'workspace-456';
            const activeRoles = ['tech-lead'];

            mockPrisma.personaPreference.findUnique.mockResolvedValue({
                id: 'config-1',
                userId,
                workspaceId,
                activeRoles,
                preferences: { dashboardLayout: 'grid' },
                createdAt: new Date(),
                updatedAt: new Date(),
            });

            // WHEN: Get preference
            const result = await personaService.getPersonaPreference(userId, workspaceId);

            // THEN: Should return preference
            expect(result).toBeDefined();
            expect(result?.activeRoles).toEqual(activeRoles);
            expect(result?.preferences.dashboardLayout).toBe('grid');
        });

        it('should return null when no preference exists', async () => {
            // GIVEN: No saved preference
            mockPrisma.personaPreference.findUnique.mockResolvedValue(null);

            // WHEN: Get preference
            const result = await personaService.getPersonaPreference('user-123', 'workspace-456');

            // THEN: Should return null
            expect(result).toBeNull();
        });
    });

    describe('deletePersonaPreference', () => {
        it('should delete preference when found', async () => {
            // GIVEN: User has saved preference
            const userId = 'user-123';
            const workspaceId = 'workspace-456';

            mockPrisma.personaPreference.delete.mockResolvedValue({
                id: 'config-1',
                userId,
                workspaceId,
            });

            // WHEN: Delete preference
            const result = await personaService.deletePersonaPreference(userId, workspaceId);

            // THEN: Should return true
            expect(result).toBe(true);
            expect(mockPrisma.personaPreference.delete).toHaveBeenCalledTimes(1);
        });

        it('should return false when preference not found', async () => {
            // GIVEN: No saved preference
            mockPrisma.personaPreference.delete.mockRejectedValue(
                new Error('Record not found')
            );

            // WHEN: Delete preference
            const result = await personaService.deletePersonaPreference('user-123', 'workspace-456');

            // THEN: Should return false
            expect(result).toBe(false);
        });
    });
});
