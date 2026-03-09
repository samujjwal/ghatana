/**
 * Unit tests for PersonaCompositionEngine (Phase 1)
 *
 * Tests validate multi-role composition with priority-based conflict resolution.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { PersonaCompositionEngine } from '@/lib/persona/PersonaCompositionEngine';
import type { PersonaConfigV2 } from '@/schemas/persona.schema';

describe('PersonaCompositionEngine', () => {
    let engine: PersonaCompositionEngine;
    let adminConfig: PersonaConfigV2;
    let engineerConfig: PersonaConfigV2;
    let viewerConfig: PersonaConfigV2;

    beforeEach(() => {
        engine = new PersonaCompositionEngine();

        adminConfig = {
            role: 'admin',
            tagline: 'System Administrator',
            quickActions: [
                {
                    id: 'admin-action-1',
                    title: 'User Management',
                    description: 'Manage users',
                    href: '/users',
                    variant: 'primary',
                    permissions: ['users.manage'],
                    priority: 10,
                },
            ],
            metrics: [
                {
                    id: 'admin-metric-1',
                    title: 'System Health',
                    dataKey: 'system.health',
                    format: 'percentage',
                },
            ],
            features: [
                {
                    id: 'admin-feature-1',
                    title: 'Security',
                    description: 'Security settings',
                    href: '/security',
                    priority: 1,
                },
            ],
            permissions: ['admin.*', 'users.manage'],
            widgets: [],
        };

        engineerConfig = {
            role: 'engineer',
            tagline: 'Software Engineer',
            quickActions: [
                {
                    id: 'engineer-action-1',
                    title: 'Create Workflow',
                    description: 'Create new workflow',
                    href: '/workflows/new',
                    variant: 'success',
                    permissions: ['workflows.create'],
                    priority: 8,
                },
                {
                    id: 'admin-action-1', // Duplicate with admin (lower priority)
                    title: 'User Management (Engineer)',
                    description: 'View users',
                    href: '/users',
                    variant: 'secondary',
                    permissions: ['users.read'],
                    priority: 5,
                },
            ],
            metrics: [
                {
                    id: 'engineer-metric-1',
                    title: 'Deployments',
                    dataKey: 'deployments.count',
                    format: 'number',
                },
            ],
            features: [
                {
                    id: 'engineer-feature-1',
                    title: 'Workflows',
                    description: 'Manage workflows',
                    href: '/workflows',
                    priority: 2,
                },
            ],
            permissions: ['workflows.*', 'deployments.read'],
            widgets: [],
        };

        viewerConfig = {
            role: 'viewer',
            tagline: 'Read-Only User',
            quickActions: [
                {
                    id: 'viewer-action-1',
                    title: 'View Dashboard',
                    description: 'View dashboard',
                    href: '/dashboard',
                    variant: 'secondary',
                    permissions: [],
                    priority: 1,
                },
            ],
            metrics: [],
            features: [],
            permissions: ['dashboard.read'],
            widgets: [],
        };
    });

    describe('compose()', () => {
        it('should merge multiple roles with priority-based conflict resolution', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged).toBeDefined();
            expect(merged.mergedRoles).toEqual(['admin', 'engineer']);
            expect(merged.taglines).toHaveLength(2);
            expect(merged.taglines).toContain('System Administrator');
            expect(merged.taglines).toContain('Software Engineer');
        });

        it('should deduplicate quick actions by ID (highest priority wins)', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            const userManagementAction = merged.quickActions.find((a) => a.id === 'admin-action-1');
            expect(userManagementAction).toBeDefined();
            // Admin has priority 10, engineer has priority 5, so admin wins
            expect(userManagementAction?.title).toBe('User Management');
            expect(userManagementAction?.variant).toBe('primary');
            expect(userManagementAction?.permissions).toContain('users.manage');
        });

        it('should include unique quick actions from both roles', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged.quickActions).toHaveLength(2); // admin-action-1 (deduplicated) + engineer-action-1
            const uniqueIds = new Set(merged.quickActions.map((a) => a.id));
            expect(uniqueIds.size).toBe(2);
        });

        it('should sort quick actions by priority (highest first)', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged.quickActions[0].priority).toBeGreaterThanOrEqual(merged.quickActions[1].priority);
        });

        it('should merge permissions (union without duplicates)', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged.permissions).toContain('admin.*');
            expect(merged.permissions).toContain('users.manage');
            expect(merged.permissions).toContain('workflows.*');
            expect(merged.permissions).toContain('deployments.read');
            // Should NOT contain dashboard.read (viewer role not included)
            expect(merged.permissions).not.toContain('dashboard.read');
            // Check no duplicates
            const uniquePermissions = new Set(merged.permissions);
            expect(uniquePermissions.size).toBe(merged.permissions.length);
        });

        it('should merge metrics (deduplicate by ID, highest priority wins)', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged.metrics).toHaveLength(2);
            expect(merged.metrics[0].id).toBe('admin-metric-1');
            expect(merged.metrics[1].id).toBe('engineer-metric-1');
        });

        it('should merge features and sort by priority', () => {
            const merged = engine.compose(['admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
            });

            expect(merged.features).toHaveLength(2);
            expect(merged.features[0].priority).toBeLessThanOrEqual(merged.features[1].priority);
        });

        it('should handle single role composition', () => {
            const merged = engine.compose(['admin'], {
                admin: adminConfig,
            });

            expect(merged.mergedRoles).toEqual(['admin']);
            expect(merged.quickActions).toHaveLength(adminConfig.quickActions.length);
            expect(merged.permissions).toEqual(adminConfig.permissions);
        });

        it('should handle unknown roles gracefully', () => {
            // @ts-expect-error Testing with unknown role
            const merged = engine.compose(['unknown-role' as any], {});

            expect(merged.mergedRoles).toBeDefined();
            expect(merged.quickActions).toEqual([]);
            expect(merged.metrics).toEqual([]);
            expect(merged.features).toEqual([]);
            expect(merged.permissions).toEqual([]);
        });

        it('should respect role priority (Admin > Lead > Engineer > Viewer)', () => {
            const merged = engine.compose(['viewer', 'admin', 'engineer'], {
                admin: adminConfig,
                engineer: engineerConfig,
                viewer: viewerConfig,
            });

            // Roles should be sorted: admin, engineer, viewer
            expect(merged.mergedRoles[0]).toBe('admin');
            // Admin (priority 100) > Engineer (priority 50) > Viewer (priority 25)
            // Actions should be sorted by role priority, then by action priority
            expect(merged.quickActions[0].id).toBe('admin-action-1'); // Admin action (highest priority)
        });
    });

    describe('hasPermission()', () => {
        it('should check exact permission match', () => {
            const merged: MergedPersonaConfigV2 = {
                version: '2.0',
                mergedRoles: ['admin'],
                permissions: ['users:read', 'workflows:create'],
                quickActions: [],
                metrics: [],
                features: [],
                widgets: [],
                layout: { sections: [] },
                taglines: ['Admin'],
            };
            expect(engine.hasPermission(merged, 'users:read')).toBe(true);
            expect(engine.hasPermission(merged, 'users:write')).toBe(false);
        });

        it('should support wildcard permissions', () => {
            const merged: MergedPersonaConfigV2 = {
                version: '2.0',
                mergedRoles: ['admin'],
                permissions: ['admin:*', 'users:read'],
                quickActions: [],
                metrics: [],
                features: [],
                widgets: [],
                layout: { sections: [] },
                taglines: ['Admin'],
            };
            expect(engine.hasPermission(merged, 'admin:anything')).toBe(true);
            expect(engine.hasPermission(merged, 'users:read')).toBe(true);
            expect(engine.hasPermission(merged, 'workflows:create')).toBe(false);
        });

        it('should handle empty permissions', () => {
            const merged: MergedPersonaConfigV2 = {
                version: '2.0',
                mergedRoles: ['viewer'],
                permissions: [],
                quickActions: [],
                metrics: [],
                features: [],
                widgets: [],
                layout: { sections: [] },
                taglines: ['Viewer'],
            };
            expect(engine.hasPermission(merged, 'any:permission')).toBe(false);
        });

        it('should handle null/undefined permission check', () => {
            const merged: MergedPersonaConfigV2 = {
                version: '2.0',
                mergedRoles: ['admin'],
                permissions: ['users:read'],
                quickActions: [],
                metrics: [],
                features: [],
                widgets: [],
                layout: { sections: [] },
                taglines: ['Admin'],
            };
            expect(engine.hasPermission(merged, '')).toBe(false);
        });
    });

    describe('filterByPermissions()', () => {
        interface TestItem {
            id: string;
            permissions?: string[];
        }

        it('should filter items by user permissions', () => {
            const items: TestItem[] = [
                { id: 'item-1', permissions: ['users:read'] },
                { id: 'item-2', permissions: ['admin:write'] },
                { id: 'item-3', permissions: [] },
            ];

            const userPermissions = ['users:read', 'users:write'];
            const filtered = engine.filterByPermissions(items, userPermissions);

            expect(filtered).toHaveLength(2); // item-1 and item-3 (no permissions required)
            expect(filtered.map((i) => i.id)).toEqual(['item-1', 'item-3']);
        });

        it('should include items without permissions', () => {
            const items: TestItem[] = [
                { id: 'item-1', permissions: ['admin:write'] },
                { id: 'item-2' }, // No permissions property
                { id: 'item-3', permissions: [] }, // Empty permissions
            ];

            const userPermissions = ['users:read'];
            const filtered = engine.filterByPermissions(items, userPermissions);

            expect(filtered).toHaveLength(2); // item-2 and item-3
            expect(filtered.map((i) => i.id)).toEqual(['item-2', 'item-3']);
        });

        it('should support wildcard permissions in filtering', () => {
            const items: TestItem[] = [
                { id: 'item-1', permissions: ['admin:read'] },
                { id: 'item-2', permissions: ['admin:write'] },
                { id: 'item-3', permissions: ['users:read'] },
            ];

            const userPermissions = ['admin:*'];
            const filtered = engine.filterByPermissions(items, userPermissions);

            expect(filtered).toHaveLength(2); // item-1 and item-2
            expect(filtered.map((i) => i.id)).toEqual(['item-1', 'item-2']);
        });
    });
});
