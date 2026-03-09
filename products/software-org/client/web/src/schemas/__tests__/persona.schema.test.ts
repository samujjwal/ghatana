/**
 * Unit tests for Persona Schemas (Phase 1)
 *
 * Tests validate Zod schema validation for PersonaConfigV2 and related schemas.
 */

import { describe, it, expect } from 'vitest';
import {
    PersonaConfigV2Schema,
    QuickActionSchema,
    MetricDefinitionSchema,
    FeatureConfigSchema,
    WidgetConfigSchema,
    DashboardLayoutConfigSchema,
    PersonaExtensionSchema,
    PluginManifestSchema,
    UserPreferencesSchema,
    WorkspaceOverrideSchema,
    BadgeBindingSchema,
    MetricThresholdSchema,
} from '@/schemas/persona.schema';

describe('PersonaSchemas', () => {
    describe('QuickActionSchema', () => {
        it('should validate valid quick action', () => {
            const validAction = {
                id: 'action-1',
                title: 'Create Workflow',
                description: 'Create a new workflow',
                icon: '🚀',
                href: '/workflows/new',
                variant: 'primary' as const,
                permissions: ['workflows.create'],
                priority: 10,
            };

            const result = QuickActionSchema.safeParse(validAction);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.id).toBe('action-1');
                expect(result.data.variant).toBe('primary');
            }
        });

        it('should require either href or onClickAction', () => {
            const invalidAction = {
                id: 'action-1',
                title: 'Invalid Action',
                description: 'Missing href and onClickAction',
                variant: 'primary' as const,
            };

            const result = QuickActionSchema.safeParse(invalidAction);
            expect(result.success).toBe(false);
        });

        it('should validate action with onClickAction instead of href', () => {
            const validAction = {
                id: 'action-1',
                title: 'Toggle Feature',
                description: 'Toggle feature flag',
                onClickAction: 'toggle-feature',
                variant: 'secondary' as const,
            };

            const result = QuickActionSchema.safeParse(validAction);
            expect(result.success).toBe(true);
        });

        it('should apply default values', () => {
            const minimalAction = {
                id: 'action-1',
                title: 'Minimal Action',
                description: 'Minimal valid action',
                href: '/test',
            };

            const result = QuickActionSchema.safeParse(minimalAction);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.variant).toBe('secondary'); // Default
                expect(result.data.permissions).toEqual([]); // Default
                expect(result.data.priority).toBe(0); // Default
            }
        });
    });

    describe('MetricDefinitionSchema', () => {
        it('should validate valid metric definition', () => {
            const validMetric = {
                id: 'metric-1',
                title: 'Active Users',
                icon: '👥',
                color: 'blue',
                dataKey: 'users.active',
                format: 'number' as const,
                threshold: {
                    warning: 80,
                    critical: 100,
                },
            };

            const result = MetricDefinitionSchema.safeParse(validMetric);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.format).toBe('number');
                expect(result.data.threshold?.warning).toBe(80);
            }
        });

        it('should allow metrics without threshold', () => {
            const metricWithoutThreshold = {
                id: 'metric-1',
                title: 'Simple Metric',
                icon: '📊',
                color: 'green',
                dataKey: 'simple.metric',
                format: 'percentage' as const,
            };

            const result = MetricDefinitionSchema.safeParse(metricWithoutThreshold);
            expect(result.success).toBe(true);
        });

        it('should apply default format', () => {
            const minimalMetric = {
                id: 'metric-1',
                title: 'Minimal Metric',
                dataKey: 'test.metric',
            };

            const result = MetricDefinitionSchema.safeParse(minimalMetric);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.format).toBe('number'); // Default
            }
        });
    });

    describe('WidgetConfigSchema', () => {
        it('should validate valid widget config', () => {
            const validWidget = {
                id: 'widget-1',
                title: 'Metric Widget',
                type: 'metric' as const,
                slot: 'dashboard.metrics',
                pluginId: 'custom-metric',
                enabled: true,
                permissions: ['metrics.read'],
                layout: {
                    lg: { x: 0, y: 0, w: 4, h: 4 },
                    md: { x: 0, y: 0, w: 6, h: 4 },
                },
                config: {
                    threshold: 100,
                    format: 'number',
                },
            };

            const result = WidgetConfigSchema.safeParse(validWidget);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.type).toBe('metric');
            }
        });

        it('should apply default permissions array', () => {
            const minimalWidget = {
                id: 'widget-1',
                title: 'Minimal Widget',
                type: 'metric' as const,
            };

            const result = WidgetConfigSchema.safeParse(minimalWidget);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.permissions).toEqual([]); // Default
            }
        });
    });

    describe('DashboardLayoutConfigSchema', () => {
        it('should validate valid dashboard layout', () => {
            const validLayout = {
                grid: {
                    cols: 12,
                    rowHeight: 100,
                },
                sections: [],
            };

            const result = DashboardLayoutConfigSchema.safeParse(validLayout);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.grid.cols).toBe(12);
            }
        });

        it('should apply default grid values', () => {
            const minimalLayout = {
                sections: [],
            };

            const result = DashboardLayoutConfigSchema.safeParse(minimalLayout);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.grid.cols).toBe(12); // Default
                expect(result.data.grid.rowHeight).toBe(100); // Default
            }
        });
    });

    describe('PluginManifestSchema', () => {
        it('should validate valid plugin manifest', () => {
            const validManifest = {
                id: 'plugin-1',
                name: 'Custom Plugin',
                version: '1.0.0',
                type: 'metric' as const,
                author: 'Test Author',
                description: 'Test plugin description',
                component: 'lazy',
                slots: ['dashboard.metrics'],
                permissions: ['metrics.read'],
                config: {
                    defaultValue: 100,
                },
            };

            const result = PluginManifestSchema.safeParse(validManifest);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.id).toBe('plugin-1');
                expect(result.data.type).toBe('metric');
            }
        });

        it('should require required fields', () => {
            const invalidManifest = {
                id: 'plugin-1',
                name: 'Incomplete Plugin',
                // Missing version, type, author, description, component
            };

            const result = PluginManifestSchema.safeParse(invalidManifest);
            expect(result.success).toBe(false);
        });
    });

    describe('PersonaConfigV2Schema', () => {
        it('should validate complete persona config', () => {
            const validConfig = {
                role: 'admin' as const,
                displayName: 'Administrator',
                tagline: 'System Administrator',
                quickActions: [
                    {
                        id: 'action-1',
                        title: 'User Management',
                        description: 'Manage users',
                        href: '/users',
                        variant: 'primary' as const,
                    },
                ],
                metrics: [
                    {
                        id: 'metric-1',
                        title: 'Active Users',
                        dataKey: 'users.active',
                        format: 'number' as const,
                    },
                ],
                features: [
                    {
                        id: 'feature-1',
                        title: 'Security',
                        description: 'Security features',
                        href: '/security',
                        priority: 1,
                    },
                ],
                permissions: ['admin.*'],
            };

            const result = PersonaConfigV2Schema.safeParse(validConfig);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.role).toBe('admin');
                expect(result.data.quickActions).toHaveLength(1);
                expect(result.data.metrics).toHaveLength(1);
            }
        });

        it('should apply default empty arrays', () => {
            const minimalConfig = {
                role: 'viewer' as const,
                displayName: 'Viewer',
                tagline: 'Read-only access',
            };

            const result = PersonaConfigV2Schema.safeParse(minimalConfig);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.version).toBe('2.0'); // Default
                expect(result.data.quickActions).toEqual([]); // Default
                expect(result.data.metrics).toEqual([]); // Default
                expect(result.data.features).toEqual([]); // Default
                expect(result.data.permissions).toEqual([]); // Default
                expect(result.data.contextualTips).toEqual([]); // Default
            }
        });
    });

    describe('BadgeBindingSchema', () => {
        it('should validate static badge', () => {
            const staticBadge = {
                type: 'static' as const,
                value: 5, // number, not string
            };

            const result = BadgeBindingSchema.safeParse(staticBadge);
            expect(result.success).toBe(true);
        });

        it('should validate dynamic badge', () => {
            const dynamicBadge = {
                type: 'metric' as const,
                key: 'pending.count', // Use 'key' not 'dataKey'
            };

            const result = BadgeBindingSchema.safeParse(dynamicBadge);
            expect(result.success).toBe(true);
        });
    });

    describe('UserPreferencesSchema', () => {
        it('should validate user preferences', () => {
            const preferences = {
                userId: 'user-123',
                workspaceId: 'workspace-abc',
                preferences: {
                    activeRoles: ['admin'] as const,
                    theme: 'dark' as const,
                    pinnedWidgets: [],
                    enabledPlugins: [],
                    customizations: {
                        primaryColor: '#3b82f6',
                    },
                },
            };

            const result = UserPreferencesSchema.safeParse(preferences);
            expect(result.success).toBe(true);
            if (result.success) {
                expect(result.data.preferences.theme).toBe('dark');
            }
        });
    });

    describe('WorkspaceOverrideSchema', () => {
        it('should validate workspace override', () => {
            const override = {
                workspaceId: 'workspace-123',
                overrides: {
                    disabledPlugins: ['plugin-1', 'plugin-2'],
                    defaultRoles: ['admin', 'engineer'] as const,
                    customBranding: {
                        logo: 'https://example.com/logo.png',
                    },
                },
            };

            const result = WorkspaceOverrideSchema.safeParse(override);
            expect(result.success).toBe(true);
        });
    });
});
