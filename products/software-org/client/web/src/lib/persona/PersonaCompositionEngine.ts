/**
 * Persona Composition Engine
 *
 * <p><b>Purpose</b><br>
 * Merges multiple persona roles into a unified configuration with priority-based conflict resolution.
 * Supports role hierarchy: Admin > Lead > Engineer > Viewer
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { PersonaCompositionEngine } from '@/lib/persona/PersonaCompositionEngine';
 *
 * const engine = new PersonaCompositionEngine();
 * const merged = engine.compose(['admin', 'engineer'], personaConfigs);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Multi-role persona composition with conflict resolution
 * @doc.layer product
 * @doc.pattern Composition Engine
 */

import type {
    PersonaConfigV2,
    MergedPersonaConfigV2,
    QuickAction,
    MetricDefinition,
    FeatureConfig,
    WidgetConfig,
    UserRole,
} from '@/schemas/persona.schema';

/**
 * Role priority hierarchy (higher number = higher priority)
 */
const ROLE_PRIORITY: Record<UserRole, number> = {
    admin: 100,
    lead: 75,
    engineer: 50,
    viewer: 25,
};

/**
 * Persona Composition Engine
 *
 * Merges multiple persona configurations with intelligent conflict resolution:
 * - Arrays: Deduplicated by ID, sorted by priority
 * - Permissions: Union of all permissions
 * - Layout: Highest priority role wins
 * - Taglines: Combined from all roles
 */
export class PersonaCompositionEngine {
    /**
     * Compose multiple persona roles into a single merged configuration
     *
     * @param roles Array of roles to merge (in order of preference)
     * @param configs Map of role to PersonaConfigV2
     * @returns Merged persona configuration
     */
    compose(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): MergedPersonaConfigV2 {
        if (roles.length === 0) {
            throw new Error('At least one role must be provided for composition');
        }

        // Sort roles by priority (highest first)
        const sortedRoles = this.sortByPriority(roles);

        // Merge configurations
        const merged: MergedPersonaConfigV2 = {
            version: '2.0',
            mergedRoles: sortedRoles,
            quickActions: this.mergeQuickActions(sortedRoles, configs),
            metrics: this.mergeMetrics(sortedRoles, configs),
            features: this.mergeFeatures(sortedRoles, configs),
            widgets: this.mergeWidgets(sortedRoles, configs),
            layout: this.mergeLayout(sortedRoles, configs),
            permissions: this.mergePermissions(sortedRoles, configs),
            taglines: this.mergeTaglines(sortedRoles, configs),
        };

        return merged;
    }

    /**
     * Sort roles by priority (highest first)
     */
    private sortByPriority(roles: UserRole[]): UserRole[] {
        return [...roles].sort((a, b) => ROLE_PRIORITY[b] - ROLE_PRIORITY[a]);
    }

    /**
     * Merge quick actions from multiple roles
     * - Deduplicate by ID
     * - Sort by priority (role priority first, then item priority)
     */
    private mergeQuickActions(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): QuickAction[] {
        const actionMap = new Map<string, QuickAction & { rolePriority: number }>();

        roles.forEach((role) => {
            const config = configs[role];
            if (!config) return;

            config.quickActions.forEach((action) => {
                const existing = actionMap.get(action.id);
                const rolePriority = ROLE_PRIORITY[role];

                // Keep higher priority action (by role, then by item priority)
                if (
                    !existing ||
                    rolePriority > existing.rolePriority ||
                    (rolePriority === existing.rolePriority &&
                        action.priority > existing.priority)
                ) {
                    actionMap.set(action.id, { ...action, rolePriority });
                }
            });
        });

        return Array.from(actionMap.values())
            .sort((a, b) => {
                // Sort by role priority first
                if (a.rolePriority !== b.rolePriority) {
                    return b.rolePriority - a.rolePriority;
                }
                // Then by item priority
                return b.priority - a.priority;
            })
            .map(({ rolePriority, ...action }) => action);
    }

    /**
     * Merge metrics from multiple roles
     * - Deduplicate by ID
     * - Sort by role priority
     */
    private mergeMetrics(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): MetricDefinition[] {
        const metricMap = new Map<string, MetricDefinition & { rolePriority: number }>();

        roles.forEach((role) => {
            const config = configs[role];
            if (!config) return;

            config.metrics.forEach((metric) => {
                const existing = metricMap.get(metric.id);
                const rolePriority = ROLE_PRIORITY[role];

                if (!existing || rolePriority > existing.rolePriority) {
                    metricMap.set(metric.id, { ...metric, rolePriority });
                }
            });
        });

        return Array.from(metricMap.values())
            .sort((a, b) => b.rolePriority - a.rolePriority)
            .map(({ rolePriority, ...metric }) => metric);
    }

    /**
     * Merge features from multiple roles
     * - Deduplicate by ID
     * - Sort by priority
     */
    private mergeFeatures(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): FeatureConfig[] {
        const featureMap = new Map<string, FeatureConfig & { rolePriority: number }>();

        roles.forEach((role) => {
            const config = configs[role];
            if (!config) return;

            config.features.forEach((feature) => {
                const existing = featureMap.get(feature.id);
                const rolePriority = ROLE_PRIORITY[role];

                if (
                    !existing ||
                    rolePriority > existing.rolePriority ||
                    (rolePriority === existing.rolePriority &&
                        feature.priority > existing.priority)
                ) {
                    featureMap.set(feature.id, { ...feature, rolePriority });
                }
            });
        });

        return Array.from(featureMap.values())
            .sort((a, b) => {
                if (a.rolePriority !== b.rolePriority) {
                    return b.rolePriority - a.rolePriority;
                }
                return b.priority - a.priority;
            })
            .map(({ rolePriority, ...feature }) => feature);
    }

    /**
     * Merge widgets from multiple roles
     * - Deduplicate by ID
     * - Highest priority role wins
     */
    private mergeWidgets(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): WidgetConfig[] | undefined {
        const widgetMap = new Map<string, WidgetConfig & { rolePriority: number }>();

        roles.forEach((role) => {
            const config = configs[role];
            if (!config?.widgets) return;

            config.widgets.forEach((widget) => {
                const existing = widgetMap.get(widget.id);
                const rolePriority = ROLE_PRIORITY[role];

                if (!existing || rolePriority > existing.rolePriority) {
                    widgetMap.set(widget.id, { ...widget, rolePriority });
                }
            });
        });

        if (widgetMap.size === 0) return undefined;

        return Array.from(widgetMap.values())
            .sort((a, b) => b.rolePriority - a.rolePriority)
            .map(({ rolePriority, ...widget }) => widget);
    }

    /**
     * Merge layouts from multiple roles
     * - Highest priority role wins
     */
    private mergeLayout(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ) {
        for (const role of roles) {
            const config = configs[role];
            if (config?.layout) {
                return config.layout;
            }
        }
        return undefined;
    }

    /**
     * Merge permissions from multiple roles
     * - Union of all permissions
     * - Deduplicated
     */
    private mergePermissions(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): string[] {
        const permissionSet = new Set<string>();

        roles.forEach((role) => {
            const config = configs[role];
            if (!config) return;

            config.permissions.forEach((permission) => {
                // Admin has wildcard permission
                if (permission === '*') {
                    permissionSet.add('*');
                } else {
                    permissionSet.add(permission);
                }
            });
        });

        return Array.from(permissionSet).sort();
    }

    /**
     * Merge taglines from multiple roles
     * - Collect unique taglines
     */
    private mergeTaglines(
        roles: UserRole[],
        configs: Record<UserRole, PersonaConfigV2>
    ): string[] {
        return roles
            .map((role) => configs[role]?.tagline)
            .filter((tagline): tagline is string => !!tagline);
    }

    /**
     * Check if user has a specific permission in merged config
     */
    hasPermission(merged: MergedPersonaConfigV2, permission: string): boolean {
        // Wildcard permission grants all
        if (merged.permissions.includes('*')) {
            return true;
        }

        // Exact match
        if (merged.permissions.includes(permission)) {
            return true;
        }

        // Wildcard permission patterns (e.g., "workflows:*")
        const permissionPrefix = permission.split(':')[0];
        const wildcardPattern = `${permissionPrefix}:*`;
        return merged.permissions.includes(wildcardPattern);
    }

    /**
     * Filter items by user permissions
     */
    filterByPermissions<T extends { permissions?: string[] }>(
        items: T[],
        userPermissions: string[]
    ): T[] {
        return items.filter((item) => {
            if (!item.permissions || item.permissions.length === 0) {
                return true; // No permissions required
            }

            // Check if user has any of the required permissions
            return item.permissions.some((required) => {
                if (userPermissions.includes('*')) return true;
                if (userPermissions.includes(required)) return true;

                // Check wildcard patterns
                const requiredPrefix = required.split(':')[0];
                const wildcardPattern = `${requiredPrefix}:*`;
                return userPermissions.includes(wildcardPattern);
            });
        });
    }
}

/**
 * Singleton instance for convenience
 */
export const personaCompositionEngine = new PersonaCompositionEngine();
