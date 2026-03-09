/**
 * Persona Schema Definitions with Zod Validation
 *
 * <p><b>Purpose</b><br>
 * Type-safe schemas for persona configurations, supporting:
 * - Multi-role composition
 * - Plugin architecture
 * - Dashboard layout customization
 * - Server-side persistence
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { PersonaConfigV2Schema, QuickActionSchema } from '@/schemas/persona.schema';
 *
 * const config = PersonaConfigV2Schema.parse(rawData);
 * const action = QuickActionSchema.parse({ id: '...', title: '...' });
 * }</pre>
 *
 * @doc.type schema
 * @doc.purpose Zod schemas for persona type validation
 * @doc.layer product
 * @doc.pattern Schema Validation
 */

import { z } from 'zod';

/**
 * User role types - matches existing UserRole
 */
export const UserRoleSchema = z.enum(['admin', 'lead', 'engineer', 'viewer']);
export type UserRole = z.infer<typeof UserRoleSchema>;

/**
 * Badge binding types for dynamic badges
 */
export const BadgeBindingSchema = z.object({
    type: z.enum(['pendingTasks', 'metric', 'static']),
    key: z.string().optional(),
    value: z.number().optional(),
});
export type BadgeBinding = z.infer<typeof BadgeBindingSchema>;

/**
 * Quick Action Schema with enhanced features
 */
export const QuickActionSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    title: z.string().min(1, 'Title is required'),
    description: z.string(),
    icon: z.string().optional(),
    href: z.string().optional(),
    onClickAction: z.string().optional(),
    variant: z.enum(['primary', 'secondary', 'warning', 'success']).default('secondary'),
    badge: BadgeBindingSchema.optional(),
    badgeKey: z.string().optional(), // Backward compatibility
    permissions: z.array(z.string()).default([]),
    personaScope: z.array(z.string()).optional(),
    priority: z.number().default(0),
    shortcut: z.string().optional(),
}).refine(
    (data) => data.href || data.onClickAction,
    { message: 'Either href or onClickAction must be provided' }
);
export type QuickAction = z.infer<typeof QuickActionSchema>;

/**
 * Metric threshold definition
 */
export const MetricThresholdSchema = z.object({
    warning: z.number(),
    critical: z.number(),
});
export type MetricThreshold = z.infer<typeof MetricThresholdSchema>;

/**
 * Metric Definition Schema
 */
export const MetricDefinitionSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    title: z.string().min(1, 'Title is required'),
    icon: z.string().optional(),
    color: z.string().default('blue'),
    dataKey: z.string().min(1, 'Data key is required'),
    format: z.enum(['number', 'percentage', 'duration', 'currency']).default('number'),
    threshold: MetricThresholdSchema.optional(),
    refreshInterval: z.number().positive().optional(),
    personaScope: z.array(z.string()).optional(),
});
export type MetricDefinition = z.infer<typeof MetricDefinitionSchema>;

/**
 * Feature Configuration Schema
 */
export const FeatureConfigSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    title: z.string().min(1, 'Title is required'),
    description: z.string(),
    icon: z.string().optional(),
    href: z.string().url('Invalid URL').or(z.string().startsWith('/', 'Invalid path')),
    color: z.string().default('blue'),
    badge: z.union([z.string(), BadgeBindingSchema]).optional(),
    category: z.string().optional(),
    priority: z.number().default(0),
    permissions: z.array(z.string()).default([]),
    personaScope: z.array(z.string()).optional(),
});
export type FeatureConfig = z.infer<typeof FeatureConfigSchema>;

/**
 * Widget Configuration Schema
 */
export const WidgetConfigSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    type: z.enum(['metric', 'chart', 'table', 'list', 'custom']),
    title: z.string().min(1, 'Title is required'),
    slot: z.string().optional(), // Slot name for plugin slot rendering
    component: z.string().optional(), // Component name for lazy loading
    dataSource: z.string().optional(),
    refreshInterval: z.number().positive().optional(),
    permissions: z.array(z.string()).default([]),
    personaScope: z.array(z.string()).optional(),
    layout: z
        .record(
            z.string(),
            z.object({
                x: z.number().nonnegative().optional(),
                y: z.number().nonnegative().optional(),
                w: z.number().positive().optional(),
                h: z.number().positive().optional(),
                minW: z.number().positive().optional(),
                minH: z.number().positive().optional(),
                maxW: z.number().positive().optional(),
                maxH: z.number().positive().optional(),
            })
        )
        .optional(),
    config: z.record(z.string(), z.unknown()).optional(), // Widget-specific config
});
export type WidgetConfig = z.infer<typeof WidgetConfigSchema>;

/**
 * Dashboard Layout Configuration Schema
 */
export const DashboardLayoutConfigSchema = z.object({
    grid: z.object({
        cols: z.number().positive().default(12),
        rowHeight: z.number().positive().default(100),
        breakpoints: z.record(z.string(), z.number()).optional(),
    }).default({ cols: 12, rowHeight: 100 }),
    widgets: z.array(
        z.object({
            i: z.string(), // Widget ID
            x: z.number().nonnegative(),
            y: z.number().nonnegative(),
            w: z.number().positive(),
            h: z.number().positive(),
            minW: z.number().positive().optional(),
            minH: z.number().positive().optional(),
            maxW: z.number().positive().optional(),
            maxH: z.number().positive().optional(),
            static: z.boolean().default(false),
        })
    ).default([]),
});
export type DashboardLayoutConfig = z.infer<typeof DashboardLayoutConfigSchema>;

/**
 * Persona Extension Schema (for plugins)
 */
export const PersonaExtensionSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    type: z.enum(['metric', 'quickAction', 'widget', 'insight', 'route', 'persona-extension']),
    providedBy: z.string(), // Plugin ID
    enabled: z.boolean().default(true),
    config: z.record(z.string(), z.unknown()).optional(),
});
export type PersonaExtension = z.infer<typeof PersonaExtensionSchema>;

/**
 * AI Insight Configuration Schema
 */
export const InsightConfigSchema = z.object({
    id: z.string().min(1, 'ID is required'),
    type: z.enum(['recommendation', 'alert', 'briefing', 'suggestion']),
    priority: z.number().default(0),
    enabled: z.boolean().default(true),
    refreshInterval: z.number().positive().optional(),
});
export type InsightConfig = z.infer<typeof InsightConfigSchema>;

/**
 * Persona Config V2 Schema (Enhanced)
 */
export const PersonaConfigV2Schema = z.object({
    version: z.literal('2.0').default('2.0'),
    role: UserRoleSchema,
    displayName: z.string().min(1, 'Display name is required'),
    tagline: z.string(),
    welcomeMessage: z.string().optional(),
    quickActions: z.array(QuickActionSchema).default([]),
    metrics: z.array(MetricDefinitionSchema).default([]),
    features: z.array(FeatureConfigSchema).default([]),
    widgets: z.array(WidgetConfigSchema).optional(),
    layout: DashboardLayoutConfigSchema.optional(),
    permissions: z.array(z.string()).default([]),
    contextualTips: z.array(z.string()).default([]),
    personaExtensions: z.array(PersonaExtensionSchema).optional(),
    aiInsights: z.array(InsightConfigSchema).optional(),
});
export type PersonaConfigV2 = z.infer<typeof PersonaConfigV2Schema>;

/**
 * Merged Persona Config Schema (Multi-Role Composition)
 */
export const MergedPersonaConfigV2Schema = z.object({
    version: z.literal('2.0').default('2.0'),
    mergedRoles: z.array(UserRoleSchema).min(1, 'At least one role required'),
    quickActions: z.array(QuickActionSchema).default([]),
    metrics: z.array(MetricDefinitionSchema).default([]),
    features: z.array(FeatureConfigSchema).default([]),
    widgets: z.array(WidgetConfigSchema).optional(),
    layout: DashboardLayoutConfigSchema.optional(),
    permissions: z.array(z.string()).default([]),
    taglines: z.array(z.string()).default([]),
});
export type MergedPersonaConfigV2 = z.infer<typeof MergedPersonaConfigV2Schema>;

/**
 * Plugin Manifest Schema
 */
export const PluginManifestSchema = z.object({
    id: z.string().min(1, 'Plugin ID is required'),
    name: z.string().min(1, 'Plugin name is required'),
    version: z.string().regex(/^\d+\.\d+\.\d+$/, 'Invalid version format'),
    description: z.string().optional(),
    author: z.string().optional(),
    type: z.enum(['metric', 'quickAction', 'widget', 'insight', 'route', 'persona-extension']),
    component: z.string().optional(), // Component path for lazy loading
    permissions: z.array(z.string()).default([]),
    slot: z.string().optional(),
    slots: z.array(z.string()).optional(),
    priority: z.number().default(0),
    enabled: z.boolean().default(true),
    config: z.record(z.string(), z.unknown()).optional(),
});
export type PluginManifest = z.infer<typeof PluginManifestSchema>;

/**
 * User Preferences Schema (Server-side persistence)
 */
export const UserPreferencesSchema = z.object({
    userId: z.string().min(1, 'User ID is required'),
    workspaceId: z.string().optional(),
    teamId: z.string().optional(),
    preferences: z.object({
        activeRoles: z.array(UserRoleSchema).min(1, 'At least one role required'),
        layout: DashboardLayoutConfigSchema.optional(),
        pinnedWidgets: z.array(z.string()).default([]),
        enabledPlugins: z.array(z.string()).default([]),
        theme: z.enum(['light', 'dark', 'system']).default('system'),
        customizations: z.record(z.string(), z.unknown()).optional(),
    }),
    lastModified: z.string().datetime().optional(),
});
export type UserPreferences = z.infer<typeof UserPreferencesSchema>;

/**
 * Workspace Override Schema
 */
export const WorkspaceOverrideSchema = z.object({
    workspaceId: z.string().min(1, 'Workspace ID is required'),
    overrides: z.object({
        disabledPlugins: z.array(z.string()).default([]),
        forcedLayout: DashboardLayoutConfigSchema.optional(),
        defaultRoles: z.array(UserRoleSchema).optional(),
        customBranding: z.record(z.string(), z.unknown()).optional(),
    }),
});
export type WorkspaceOverride = z.infer<typeof WorkspaceOverrideSchema>;

/**
 * Validation helpers
 */
export const validatePersonaConfig = (data: unknown) => {
    return PersonaConfigV2Schema.safeParse(data);
};

export const validateQuickAction = (data: unknown) => {
    return QuickActionSchema.safeParse(data);
};

export const validateMetricDefinition = (data: unknown) => {
    return MetricDefinitionSchema.safeParse(data);
};

export const validateUserPreferences = (data: unknown) => {
    return UserPreferencesSchema.safeParse(data);
};
