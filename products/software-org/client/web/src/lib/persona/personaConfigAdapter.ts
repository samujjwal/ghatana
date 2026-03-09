/**
 * Persona Config Adapter
 *
 * <p><b>Purpose</b><br>
 * Adapts legacy PersonaConfig to PersonaConfigV2 schema for backward compatibility.
 * Allows gradual migration from v1 to v2 configuration format.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { adaptPersonaConfig } from '@/lib/persona/personaConfigAdapter';
 *
 * const legacyConfig = getPersonaConfig('admin'); // v1 config
 * const v2Config = adaptPersonaConfig(legacyConfig); // v2 config
 * }</pre>
 *
 * @doc.type adapter
 * @doc.purpose Backward compatibility adapter for persona configs
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { PersonaConfig, QuickAction, MetricDefinition, FeatureConfig } from '@/config/personaConfig';
import type { PersonaConfigV2, QuickAction as QuickActionV2, MetricDefinition as MetricDefinitionV2, FeatureConfig as FeatureConfigV2 } from '@/schemas/persona.schema';

/**
 * Adapt legacy QuickAction to v2 schema
 */
function adaptQuickAction(action: QuickAction): QuickActionV2 {
    return {
        id: action.id,
        title: action.title,
        description: action.description,
        icon: action.icon,
        href: action.href,
        variant: action.variant,
        badgeKey: action.badgeKey,
        permissions: action.permissions ?? [],
        priority: 0, // Default priority for v1 actions
        shortcut: action.shortcut,
    };
}

/**
 * Adapt legacy MetricDefinition to v2 schema
 */
function adaptMetricDefinition(metric: MetricDefinition): MetricDefinitionV2 {
    return {
        id: metric.id,
        title: metric.title,
        icon: metric.icon,
        color: metric.color,
        dataKey: metric.dataKey,
        format: metric.format,
        threshold: metric.threshold,
    };
}

/**
 * Adapt legacy FeatureConfig to v2 schema
 */
function adaptFeatureConfig(feature: FeatureConfig): FeatureConfigV2 {
    return {
        id: feature.id,
        title: feature.title,
        description: feature.description,
        icon: feature.icon,
        href: feature.href,
        color: feature.color,
        badge: feature.badge,
        priority: feature.priority,
        permissions: feature.permissions ?? [],
    };
}

/**
 * Adapt legacy PersonaConfig to PersonaConfigV2
 *
 * @param config Legacy persona configuration
 * @returns PersonaConfigV2 compatible configuration
 */
export function adaptPersonaConfig(config: PersonaConfig): PersonaConfigV2 {
    return {
        version: '2.0',
        role: config.role,
        displayName: config.displayName,
        tagline: config.tagline,
        welcomeMessage: config.welcomeMessage,
        quickActions: config.quickActions.map(adaptQuickAction),
        metrics: config.metrics.map(adaptMetricDefinition),
        features: config.features.map(adaptFeatureConfig),
        permissions: config.permissions,
        contextualTips: config.contextualTips,
        // v2-only fields set to defaults
        widgets: undefined,
        layout: undefined,
        personaExtensions: undefined,
        aiInsights: undefined,
    };
}

/**
 * Adapt multiple persona configs to a registry
 *
 * @param configs Map of role to legacy PersonaConfig
 * @returns Map of role to PersonaConfigV2
 */
export function adaptPersonaConfigs(
    configs: Record<string, PersonaConfig>
): Record<string, PersonaConfigV2> {
    const adapted: Record<string, PersonaConfigV2> = {};

    for (const [role, config] of Object.entries(configs)) {
        adapted[role] = adaptPersonaConfig(config);
    }

    return adapted;
}
