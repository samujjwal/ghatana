/**
 * Unit tests for personaConfigAdapter
 *
 * Tests validate:
 * - Legacy QuickAction → v2 QuickAction adaptation
 * - Legacy MetricDefinition → v2 MetricDefinition adaptation
 * - Legacy FeatureConfig → v2 FeatureConfig adaptation
 * - Complete PersonaConfig → PersonaConfigV2 adaptation
 * - Multiple configs adaptation (adaptPersonaConfigs)
 * - Default value handling for missing fields
 * - Edge cases and boundary conditions
 */

import { describe, it, expect } from 'vitest';
import { adaptPersonaConfig, adaptPersonaConfigs } from '../personaConfigAdapter';
import type { PersonaConfig, QuickAction, MetricDefinition, FeatureConfig } from '@/config/personaConfig';
import type { PersonaConfigV2 } from '@/schemas/persona.schema';

describe('personaConfigAdapter', () => {
    describe('adaptPersonaConfig', () => {
        /**
         * Should adapt complete persona config with all fields
         *
         * GIVEN: A legacy PersonaConfig with all fields populated
         * WHEN: adaptPersonaConfig is called
         * THEN: Returns PersonaConfigV2 with all v1 fields mapped and v2 fields set to defaults
         */
        it('should adapt complete persona config with all fields', () => {
            const legacyConfig: PersonaConfig = {
                role: 'admin',
                displayName: 'Administrator',
                tagline: 'Full system control',
                welcomeMessage: 'Welcome, Administrator',
                quickActions: [
                    {
                        id: 'create-user',
                        title: 'Create User',
                        description: 'Add a new user',
                        icon: 'UserPlus',
                        href: '/users/new',
                        variant: 'primary',
                        badgeKey: 'pendingUsers',
                        permissions: ['user.create'],
                        shortcut: 'Ctrl+U',
                    },
                ],
                metrics: [
                    {
                        id: 'total-users',
                        title: 'Total Users',
                        icon: 'Users',
                        color: 'blue',
                        dataKey: 'users.total',
                        format: 'number',
                        threshold: { warning: 100, critical: 500 },
                    },
                ],
                features: [
                    {
                        id: 'user-management',
                        title: 'User Management',
                        description: 'Manage system users',
                        icon: 'Users',
                        href: '/users',
                        color: 'blue',
                        badge: 'New',
                        priority: 1,
                        permissions: ['user.read'],
                    },
                ],
                permissions: ['admin.all'],
                contextualTips: ['Tip 1', 'Tip 2'],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.version).toBe('2.0');
            expect(adapted.role).toBe('admin');
            expect(adapted.displayName).toBe('Administrator');
            expect(adapted.tagline).toBe('Full system control');
            expect(adapted.welcomeMessage).toBe('Welcome, Administrator');
            expect(adapted.permissions).toEqual(['admin.all']);
            expect(adapted.contextualTips).toEqual(['Tip 1', 'Tip 2']);

            // v2-only fields should be undefined
            expect(adapted.widgets).toBeUndefined();
            expect(adapted.layout).toBeUndefined();
            expect(adapted.personaExtensions).toBeUndefined();
            expect(adapted.aiInsights).toBeUndefined();
        });

        /**
         * Should adapt quickActions correctly
         *
         * GIVEN: QuickActions with all fields
         * WHEN: adaptPersonaConfig is called
         * THEN: QuickActions are adapted with priority defaulted to 0
         */
        it('should adapt quickActions with default priority', () => {
            const legacyConfig: PersonaConfig = {
                role: 'developer',
                displayName: 'Developer',
                tagline: 'Build amazing things',
                welcomeMessage: 'Welcome, Developer',
                quickActions: [
                    {
                        id: 'deploy',
                        title: 'Deploy',
                        description: 'Deploy to production',
                        icon: 'Rocket',
                        href: '/deploy',
                        variant: 'primary',
                        badgeKey: 'pendingDeploys',
                        permissions: ['deploy.execute'],
                        shortcut: 'Ctrl+D',
                    },
                    {
                        id: 'rollback',
                        title: 'Rollback',
                        description: 'Rollback deployment',
                        icon: 'Undo',
                        href: '/rollback',
                        variant: 'secondary',
                    },
                ],
                metrics: [],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.quickActions).toHaveLength(2);
            expect(adapted.quickActions[0]).toMatchObject({
                id: 'deploy',
                title: 'Deploy',
                description: 'Deploy to production',
                icon: 'Rocket',
                href: '/deploy',
                variant: 'primary',
                badgeKey: 'pendingDeploys',
                permissions: ['deploy.execute'],
                shortcut: 'Ctrl+D',
                priority: 0,
            });
            expect(adapted.quickActions[1]).toMatchObject({
                id: 'rollback',
                title: 'Rollback',
                priority: 0,
            });
        });

        /**
         * Should adapt quickActions with missing permissions to empty array
         *
         * GIVEN: QuickAction without permissions field
         * WHEN: adaptPersonaConfig is called
         * THEN: Permissions default to empty array
         */
        it('should adapt quickActions with missing permissions to empty array', () => {
            const legacyConfig: PersonaConfig = {
                role: 'viewer',
                displayName: 'Viewer',
                tagline: 'Read-only access',
                welcomeMessage: 'Welcome, Viewer',
                quickActions: [
                    {
                        id: 'view-dashboard',
                        title: 'View Dashboard',
                        description: 'View dashboard',
                        icon: 'Eye',
                        href: '/dashboard',
                        variant: 'secondary',
                    },
                ],
                metrics: [],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.quickActions[0].permissions).toEqual([]);
        });

        /**
         * Should adapt metrics correctly
         *
         * GIVEN: MetricDefinitions with thresholds
         * WHEN: adaptPersonaConfig is called
         * THEN: Metrics are adapted with all fields preserved
         */
        it('should adapt metrics with thresholds', () => {
            const legacyConfig: PersonaConfig = {
                role: 'engineer',
                displayName: 'Engineer',
                tagline: 'Build and maintain',
                welcomeMessage: 'Welcome, Engineer',
                quickActions: [],
                metrics: [
                    {
                        id: 'cpu-usage',
                        title: 'CPU Usage',
                        icon: 'Cpu',
                        color: 'red',
                        dataKey: 'system.cpu',
                        format: 'percentage',
                        threshold: { warning: 70, critical: 90 },
                    },
                    {
                        id: 'memory-usage',
                        title: 'Memory Usage',
                        icon: 'Memory',
                        color: 'orange',
                        dataKey: 'system.memory',
                        format: 'bytes',
                    },
                ],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.metrics).toHaveLength(2);
            expect(adapted.metrics[0]).toMatchObject({
                id: 'cpu-usage',
                title: 'CPU Usage',
                icon: 'Cpu',
                color: 'red',
                dataKey: 'system.cpu',
                format: 'percentage',
                threshold: { warning: 70, critical: 90 },
            });
            expect(adapted.metrics[1]).toMatchObject({
                id: 'memory-usage',
                title: 'Memory Usage',
                threshold: undefined,
            });
        });

        /**
         * Should adapt features correctly
         *
         * GIVEN: FeatureConfigs with all fields
         * WHEN: adaptPersonaConfig is called
         * THEN: Features are adapted with permissions defaulted to empty array
         */
        it('should adapt features with default permissions', () => {
            const legacyConfig: PersonaConfig = {
                role: 'lead',
                displayName: 'Tech Lead',
                tagline: 'Lead the team',
                welcomeMessage: 'Welcome, Tech Lead',
                quickActions: [],
                metrics: [],
                features: [
                    {
                        id: 'team-management',
                        title: 'Team Management',
                        description: 'Manage your team',
                        icon: 'Users',
                        href: '/team',
                        color: 'blue',
                        badge: 'Important',
                        priority: 1,
                        permissions: ['team.manage'],
                    },
                    {
                        id: 'code-review',
                        title: 'Code Review',
                        description: 'Review pull requests',
                        icon: 'GitPullRequest',
                        href: '/reviews',
                        color: 'green',
                        priority: 2,
                    },
                ],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.features).toHaveLength(2);
            expect(adapted.features[0]).toMatchObject({
                id: 'team-management',
                title: 'Team Management',
                permissions: ['team.manage'],
            });
            expect(adapted.features[1]).toMatchObject({
                id: 'code-review',
                permissions: [],
            });
        });

        /**
         * Should handle empty arrays
         *
         * GIVEN: PersonaConfig with empty arrays
         * WHEN: adaptPersonaConfig is called
         * THEN: Returns config with empty arrays preserved
         */
        it('should handle empty arrays', () => {
            const legacyConfig: PersonaConfig = {
                role: 'guest',
                displayName: 'Guest',
                tagline: 'Limited access',
                welcomeMessage: 'Welcome, Guest',
                quickActions: [],
                metrics: [],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.quickActions).toEqual([]);
            expect(adapted.metrics).toEqual([]);
            expect(adapted.features).toEqual([]);
            expect(adapted.permissions).toEqual([]);
            expect(adapted.contextualTips).toEqual([]);
        });

        /**
         * Should adapt multiple quick actions preserving order
         *
         * GIVEN: PersonaConfig with multiple quickActions
         * WHEN: adaptPersonaConfig is called
         * THEN: Order is preserved
         */
        it('should preserve order of quickActions', () => {
            const legacyConfig: PersonaConfig = {
                role: 'admin',
                displayName: 'Admin',
                tagline: 'Admin tagline',
                welcomeMessage: 'Welcome',
                quickActions: [
                    {
                        id: 'action1',
                        title: 'Action 1',
                        description: 'First action',
                        icon: 'Icon1',
                        href: '/action1',
                        variant: 'primary',
                    },
                    {
                        id: 'action2',
                        title: 'Action 2',
                        description: 'Second action',
                        icon: 'Icon2',
                        href: '/action2',
                        variant: 'secondary',
                    },
                    {
                        id: 'action3',
                        title: 'Action 3',
                        description: 'Third action',
                        icon: 'Icon3',
                        href: '/action3',
                        variant: 'tertiary',
                    },
                ],
                metrics: [],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.quickActions.map(a => a.id)).toEqual(['action1', 'action2', 'action3']);
        });
    });

    describe('adaptPersonaConfigs', () => {
        /**
         * Should adapt multiple persona configs
         *
         * GIVEN: Multiple legacy PersonaConfigs
         * WHEN: adaptPersonaConfigs is called
         * THEN: Returns map of adapted configs with same keys
         */
        it('should adapt multiple persona configs', () => {
            const legacyConfigs: Record<string, PersonaConfig> = {
                admin: {
                    role: 'admin',
                    displayName: 'Administrator',
                    tagline: 'Full control',
                    welcomeMessage: 'Welcome, Admin',
                    quickActions: [],
                    metrics: [],
                    features: [],
                    permissions: ['admin.all'],
                    contextualTips: [],
                },
                developer: {
                    role: 'developer',
                    displayName: 'Developer',
                    tagline: 'Build things',
                    welcomeMessage: 'Welcome, Developer',
                    quickActions: [],
                    metrics: [],
                    features: [],
                    permissions: ['code.write'],
                    contextualTips: [],
                },
                viewer: {
                    role: 'viewer',
                    displayName: 'Viewer',
                    tagline: 'Read-only',
                    welcomeMessage: 'Welcome, Viewer',
                    quickActions: [],
                    metrics: [],
                    features: [],
                    permissions: ['read.all'],
                    contextualTips: [],
                },
            };

            const adapted = adaptPersonaConfigs(legacyConfigs);

            expect(Object.keys(adapted)).toEqual(['admin', 'developer', 'viewer']);
            expect(adapted.admin.role).toBe('admin');
            expect(adapted.admin.version).toBe('2.0');
            expect(adapted.developer.role).toBe('developer');
            expect(adapted.developer.version).toBe('2.0');
            expect(adapted.viewer.role).toBe('viewer');
            expect(adapted.viewer.version).toBe('2.0');
        });

        /**
         * Should handle empty config map
         *
         * GIVEN: Empty PersonaConfig map
         * WHEN: adaptPersonaConfigs is called
         * THEN: Returns empty map
         */
        it('should handle empty config map', () => {
            const legacyConfigs: Record<string, PersonaConfig> = {};

            const adapted = adaptPersonaConfigs(legacyConfigs);

            expect(adapted).toEqual({});
        });

        /**
         * Should handle single config in map
         *
         * GIVEN: Single PersonaConfig in map
         * WHEN: adaptPersonaConfigs is called
         * THEN: Returns map with single adapted config
         */
        it('should handle single config in map', () => {
            const legacyConfigs: Record<string, PersonaConfig> = {
                admin: {
                    role: 'admin',
                    displayName: 'Administrator',
                    tagline: 'Full control',
                    welcomeMessage: 'Welcome, Admin',
                    quickActions: [],
                    metrics: [],
                    features: [],
                    permissions: ['admin.all'],
                    contextualTips: [],
                },
            };

            const adapted = adaptPersonaConfigs(legacyConfigs);

            expect(Object.keys(adapted)).toEqual(['admin']);
            expect(adapted.admin.version).toBe('2.0');
        });
    });

    describe('edge cases', () => {
        /**
         * Should handle quickActions with only required fields
         *
         * GIVEN: QuickAction with minimal fields
         * WHEN: adaptPersonaConfig is called
         * THEN: Optional fields are undefined, priority defaults to 0
         */
        it('should handle quickActions with minimal fields', () => {
            const legacyConfig: PersonaConfig = {
                role: 'minimal',
                displayName: 'Minimal',
                tagline: 'Minimal config',
                welcomeMessage: 'Welcome',
                quickActions: [
                    {
                        id: 'minimal-action',
                        title: 'Minimal',
                        description: 'Minimal description',
                        icon: 'Icon',
                        href: '/minimal',
                        variant: 'primary',
                    },
                ],
                metrics: [],
                features: [],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.quickActions[0]).toMatchObject({
                id: 'minimal-action',
                title: 'Minimal',
                priority: 0,
                permissions: [],
            });
            expect(adapted.quickActions[0].badgeKey).toBeUndefined();
            expect(adapted.quickActions[0].shortcut).toBeUndefined();
        });

        /**
         * Should handle features with only required fields
         *
         * GIVEN: FeatureConfig with minimal fields
         * WHEN: adaptPersonaConfig is called
         * THEN: Optional fields are undefined, permissions default to empty array
         */
        it('should handle features with minimal fields', () => {
            const legacyConfig: PersonaConfig = {
                role: 'minimal',
                displayName: 'Minimal',
                tagline: 'Minimal config',
                welcomeMessage: 'Welcome',
                quickActions: [],
                metrics: [],
                features: [
                    {
                        id: 'minimal-feature',
                        title: 'Minimal Feature',
                        description: 'Minimal description',
                        icon: 'Icon',
                        href: '/feature',
                        color: 'blue',
                        priority: 1,
                    },
                ],
                permissions: [],
                contextualTips: [],
            };

            const adapted = adaptPersonaConfig(legacyConfig);

            expect(adapted.features[0]).toMatchObject({
                id: 'minimal-feature',
                title: 'Minimal Feature',
                permissions: [],
            });
            expect(adapted.features[0].badge).toBeUndefined();
        });
    });
});
