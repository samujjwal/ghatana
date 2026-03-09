import type {
    ExtensionPluginFactory,
    ExtensionPluginFactoryContext,
} from "@ghatana/dcmaar-browser-extension-core";
import {
    GuardianUsageCollectorPlugin,
    GuardianPolicyEvaluationPlugin,
    GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
    GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
} from "@ghatana/dcmaar-guardian-plugins";
import type { GuardianPluginMetadata } from "@ghatana/dcmaar-guardian-plugins";

/**
 * Factory for GuardianUsageCollectorPlugin used by ExtensionPluginHost.
 */
export const guardianUsageCollectorFactory: ExtensionPluginFactory = (
    context: ExtensionPluginFactoryContext,
) => {
    const metadata: GuardianPluginMetadata = {
        appId: context.manifest.appId,
        pluginId: context.descriptor.pluginId,
        capabilities: context.descriptor.capabilities ?? [],
        options: context.descriptor.options ?? {},
    };

    const plugin = new GuardianUsageCollectorPlugin(metadata);
    const result = {
        ...plugin,
        id: context.descriptor.pluginId,
        // Ensure the plugin descriptor is available for registry lookups
        descriptor: context.descriptor,
        manifest: context.manifest,
    } as unknown as Record<string, unknown>;
    
    console.log("[guardianUsageCollectorFactory] Created plugin:", {
        id: result.id,
        descriptorPluginId: (result.descriptor as any)?.pluginId,
        hasManifest: !!result.manifest,
        keys: Object.keys(result).slice(0, 15),
    });
    
    return result;
};

export const guardianPolicyEvaluationFactory: ExtensionPluginFactory = (
    context: ExtensionPluginFactoryContext,
) => {
    const metadata: GuardianPluginMetadata = {
        appId: context.manifest.appId,
        pluginId: context.descriptor.pluginId,
        capabilities: context.descriptor.capabilities ?? [],
        options: context.descriptor.options ?? {},
    };

    const plugin = new GuardianPolicyEvaluationPlugin(metadata);
    const result = {
        ...plugin,
        id: context.descriptor.pluginId,
        // Ensure the plugin descriptor is available for registry lookups
        descriptor: context.descriptor,
        manifest: context.manifest,
    } as unknown as Record<string, unknown>;
    
    console.log("[guardianPolicyEvaluationFactory] Created plugin:", {
        id: result.id,
        descriptorPluginId: (result.descriptor as any)?.pluginId,
        hasManifest: !!result.manifest,
        keys: Object.keys(result).slice(0, 15),
    });
    
    return result;
};

/**
 * Register all Guardian plugins with a given ExtensionPluginHost.
 *
 * Keeping this logic in a single place avoids duplication across
 * background/controller wiring.
 */
export function registerGuardianPlugins(
    host: {
        registerFactory: (pluginId: string, factory: ExtensionPluginFactory) => void;
    },
): void {
    host.registerFactory(
        GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
        guardianUsageCollectorFactory,
    );
    host.registerFactory(
        GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
        guardianPolicyEvaluationFactory,
    );
}
