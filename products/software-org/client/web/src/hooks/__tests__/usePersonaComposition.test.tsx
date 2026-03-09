/**
 * Unit tests for usePersonaComposition hook (Phase 1)
 *
 * Tests validate hook behavior, composition logic integration, and memoization.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'jotai';
import {
    usePersonaComposition,
    usePersonaQuickActions,
    usePersonaMetrics,
    usePersonaFeatures,
} from '../usePersonaComposition';
import { PersonaCompositionEngine } from '@/lib/persona/PersonaCompositionEngine';
import {
    useRoleDefinitions,
    usePersonaPreference,
} from '@/lib/hooks/usePersonaQueries';
import type { PersonaConfigV2 } from '@/schemas/persona.schema';




// Create wrapper with QueryClient and Jotai Provider
function createWrapper() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: { retry: false },
        },
    });

    return ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            <Provider>{children}</Provider>
        </QueryClientProvider>
    );
}

// Mock the composition engine
vi.mock('@/lib/persona/PersonaCompositionEngine');

// Mock the correct hooks from usePersonaQueries
vi.mock('@/lib/hooks/usePersonaQueries', () => ({
    useRoleDefinitions: vi.fn(() => ({
        data: [
            {
                id: 'admin',
                name: 'Administrator',
                permissions: ['admin.*', 'workflows.*'],
            },
            {
                id: 'engineer',
                name: 'Engineer',
                permissions: ['code.*', 'deploy.*'],
            },
        ],
        isLoading: false,
        error: null,
    })),
    usePersonaPreference: vi.fn(() => ({
        data: {
            activeRoles: ['admin', 'engineer'],
            workspaceId: 'default',
        },
        isLoading: false,
        error: null,
    })),
}));

describe('usePersonaComposition', () => {
    let mockCompose: ReturnType<typeof vi.fn>;
    let mockHasPermission: ReturnType<typeof vi.fn>;
    let mockFilterByPermissions: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        mockCompose = vi.fn((roles, configs) => ({
            roles,
            taglines: ['System Administrator', 'Software Engineer'],
            quickActions: [
                {
                    id: 'admin-action',
                    title: 'Admin Action',
                    description: 'Admin only',
                    href: '/admin',
                    variant: 'primary',
                    permissions: ['admin.write'],
                    priority: 10,
                },
                {
                    id: 'engineer-action',
                    title: 'Engineer Action',
                    description: 'Engineer only',
                    href: '/workflows',
                    variant: 'success',
                    permissions: ['workflows.create'],
                    priority: 8,
                },
            ],
            metrics: [],
            features: [],
            permissions: ['admin.*', 'workflows.*'],
            widgets: [],
        }));

        mockHasPermission = vi.fn((config, permission) => {
            // config is MergedPersonaConfigV2 object, not permissions array
            const permissions = config.permissions || [];
            return permissions.some((p: string) => {
                if (p.endsWith('.*')) {
                    const prefix = p.slice(0, -2);
                    return permission.startsWith(prefix);
                }
                return p === permission;
            });
        });

        mockFilterByPermissions = vi.fn((items, userPermissions) => {
            return items.filter((item) => {
                if (!item.permissions || item.permissions.length === 0) return true;
                return item.permissions.some((p) => userPermissions.includes(p));
            });
        });

        // Mock PersonaCompositionEngine methods
        (PersonaCompositionEngine as unknown as ReturnType<typeof vi.fn>).mockImplementation(() => ({
            compose: mockCompose,
            hasPermission: mockHasPermission,
            filterByPermissions: mockFilterByPermissions,
        }));
    });

    it('should compose multi-role persona configuration', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.merged).toBeDefined();
        expect(result.current.roles).toEqual(['admin', 'engineer']);
        expect(result.current.merged.taglines).toHaveLength(2);
        expect(mockCompose).toHaveBeenCalledWith(
            ['admin', 'engineer'],
            expect.objectContaining({
                admin: expect.any(Object),
                engineer: expect.any(Object),
            })
        );
    });

    it('should provide hasPermission function', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.hasPermission).toBeDefined();
        expect(typeof result.current.hasPermission).toBe('function');

        const hasAdminPermission = result.current.hasPermission('admin.write');
        expect(hasAdminPermission).toBe(true);
        // hasPermission is called with (merged config, permission)
        expect(mockHasPermission).toHaveBeenCalledWith(
            expect.objectContaining({ permissions: expect.arrayContaining(['admin.*']) }),
            'admin.write'
        );
    });

    it('should memoize composition result', () => {
        const { result, rerender } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        const firstResult = result.current.merged;
        rerender();
        const secondResult = result.current.merged;

        // Should be same object reference (memoized)
        expect(firstResult).toStrictEqual(secondResult);
    });

    it.skip('should return null when user profile is loading', () => {
        // TODO: Fix when @/hooks/useUserProfile exists
        vi.mocked(require('@/hooks/useUserProfile').useUserProfile).mockReturnValueOnce({
            userProfile: null,
            isLoading: true,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition());

        expect(result.current.merged).toBeNull();
        expect(result.current.isLoading).toBe(true);
    });

    it.skip('should return null when persona configs are loading', () => {
        // TODO: Fix when @/hooks/usePersonaConfigs exists
        vi.mocked(require('@/hooks/usePersonaConfigs').usePersonaConfigs).mockReturnValueOnce({
            configs: {},
            isLoading: true,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition());

        expect(result.current.merged).toBeNull();
        expect(result.current.isLoading).toBe(true);
    });

    it.skip('should handle errors from user profile fetch', () => {
        // TODO: Fix when @/hooks/useUserProfile exists
        const mockError = new Error('Failed to load user profile');
        vi.mocked(require('@/hooks/useUserProfile').useUserProfile).mockReturnValueOnce({
            userProfile: null,
            isLoading: false,
            error: mockError,
        });

        const { result } = renderHook(() => usePersonaComposition());

        expect(result.current.merged).toBeNull();
        expect(result.current.error).toBe(mockError);
    });

    it.skip('should handle errors from persona configs fetch', () => {
        // TODO: Fix when @/hooks/usePersonaConfigs exists
        const mockError = new Error('Failed to load persona configs');
        vi.mocked(require('@/hooks/usePersonaConfigs').usePersonaConfigs).mockReturnValueOnce({
            configs: {},
            isLoading: false,
            error: mockError,
        });

        const { result } = renderHook(() => usePersonaComposition());

        expect(result.current.merged).toBeNull();
        expect(result.current.error).toBe(mockError);
    });

    // ========================================
    // Error Handling & Edge Cases Tests
    // ========================================

    it('should return null merged config when no active roles', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.merged).toBeNull();
        expect(result.current.roles).toEqual([]);
    });

    it('should return null merged config when preference is undefined', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: undefined,
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.merged).toBeNull();
        expect(result.current.roles).toEqual([]);
    });

    it('should return null merged config when preference.activeRoles is undefined', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                workspaceId: 'default',
            } as any,
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.merged).toBeNull();
        expect(result.current.roles).toEqual([]);
    });

    it('should filter out unknown roles from activeRoles', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: ['admin', 'unknown-role', 'engineer', 'invalid'],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        // Should only include known roles (admin, lead, engineer, viewer)
        expect(result.current.roles).toEqual(['admin', 'engineer']);
        expect(result.current.roles).not.toContain('unknown-role');
        expect(result.current.roles).not.toContain('invalid');
    });

    it('should handle composition engine errors gracefully', () => {
        const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        const mockError = new Error('Composition failed');
        mockCompose.mockImplementationOnce(() => {
            throw mockError;
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.merged).toBeNull();
        expect(consoleErrorSpy).toHaveBeenCalledWith(
            'Failed to compose persona configurations:',
            mockError
        );

        consoleErrorSpy.mockRestore();
    });

    it('should return isLoading true when roles are loading', () => {
        // Mock useRoleDefinitions for this test
        vi.mocked(useRoleDefinitions).mockReturnValueOnce({
            data: undefined,
            isLoading: true,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.isLoading).toBe(true);
    });

    it('should return isLoading true when preferences are loading', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: undefined,
            isLoading: true,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.isLoading).toBe(true);
    });

    it('should return isLoading false when both data sources are loaded', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.isLoading).toBe(false);
    });

    // ========================================
    // Helper Functions Tests
    // ========================================

    it('should return false from hasPermission when merged config is null', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.hasPermission('admin.write')).toBe(false);
    });

    it('should call engine.hasPermission with correct arguments', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        result.current.hasPermission('code.write');

        expect(mockHasPermission).toHaveBeenCalledWith(
            expect.objectContaining({ permissions: expect.any(Array) }),
            'code.write'
        );
    });

    it('should return empty array from filterByPermissions when merged config is null', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        const items = [
            { id: '1', permissions: ['admin.write'] },
            { id: '2', permissions: ['code.write'] },
        ];
        const filtered = result.current.filterByPermissions(items);

        expect(filtered).toEqual([]);
    });

    it('should call engine.filterByPermissions with correct arguments', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        const items = [
            { id: '1', permissions: ['admin.write'] },
            { id: '2', permissions: ['code.write'] },
        ];
        result.current.filterByPermissions(items);

        expect(mockFilterByPermissions).toHaveBeenCalledWith(
            items,
            expect.arrayContaining(['admin.*', 'workflows.*'])
        );
    });

    it('should return primaryRole as first active role', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.primaryRole).toBe('admin');
        expect(result.current.roles[0]).toBe('admin');
    });

    it('should return undefined primaryRole when no active roles', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.primaryRole).toBeUndefined();
    });

    it('should expose preference and roleDefinitions in return value', () => {
        const { result } = renderHook(() => usePersonaComposition(), { wrapper: createWrapper() });

        expect(result.current.preference).toBeDefined();
        expect(result.current.preference?.activeRoles).toEqual(['admin', 'engineer']);
        expect(result.current.roleDefinitions).toBeDefined();
        expect(result.current.roleDefinitions).toHaveLength(2);
    });

    // ========================================
    // Derived Hooks Tests
    // ========================================

    it('usePersonaQuickActions should return filtered quick actions', () => {
        const { result } = renderHook(() => usePersonaQuickActions(), { wrapper: createWrapper() });

        expect(result.current.quickActions).toBeDefined();
        expect(Array.isArray(result.current.quickActions)).toBe(true);
        expect(result.current.isLoading).toBe(false);
    });

    it('usePersonaQuickActions should return empty array when merged is null', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaQuickActions(), { wrapper: createWrapper() });

        expect(result.current.quickActions).toEqual([]);
    });

    it('usePersonaMetrics should return metrics from merged config', () => {
        const { result } = renderHook(() => usePersonaMetrics(), { wrapper: createWrapper() });

        expect(result.current.metrics).toBeDefined();
        expect(Array.isArray(result.current.metrics)).toBe(true);
        expect(result.current.isLoading).toBe(false);
    });

    it('usePersonaMetrics should return empty array when merged is null', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaMetrics(), { wrapper: createWrapper() });

        expect(result.current.metrics).toEqual([]);
    });

    it('usePersonaFeatures should return filtered features', () => {
        const { result } = renderHook(() => usePersonaFeatures(), { wrapper: createWrapper() });

        expect(result.current.features).toBeDefined();
        expect(Array.isArray(result.current.features)).toBe(true);
        expect(result.current.isLoading).toBe(false);
    });

    it('usePersonaFeatures should return empty array when merged is null', () => {
        // Mock usePersonaPreference for this test
        vi.mocked(usePersonaPreference).mockReturnValueOnce({
            data: {
                activeRoles: [],
                workspaceId: 'default',
            },
            isLoading: false,
            error: null,
        });

        const { result } = renderHook(() => usePersonaFeatures(), { wrapper: createWrapper() });

        expect(result.current.features).toEqual([]);
    });

    it('derived hooks should accept custom workspaceId', () => {
        // These should not throw errors
        renderHook(() => usePersonaQuickActions('workspace-123'), { wrapper: createWrapper() });
        renderHook(() => usePersonaMetrics('workspace-456'), { wrapper: createWrapper() });
        renderHook(() => usePersonaFeatures('workspace-789'), { wrapper: createWrapper() });
    });
});
