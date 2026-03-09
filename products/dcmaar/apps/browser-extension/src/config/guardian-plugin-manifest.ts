import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";
import {
    GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
    GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
} from "@ghatana/dcmaar-guardian-plugins";

/**
 * Guardian Extension Plugin Manifest
 *
 * Describes which DCMAAR plugins are enabled for the Guardian extension
 * and how they are connected. This is intentionally minimal scaffolding
 * that can be extended as Guardian plugins evolve.
 */
export const guardianPluginManifest: ExtensionPluginManifest = {
    appId: "guardian",
    version: "1.0.0",
    plugins: [
        {
            pluginId: GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
            enabled: true,
            options: {
                sampleRate: 1.0,
            },
        },
        {
            pluginId: GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
            enabled: true,
            options: {},
        },
    ],
    connectors: [],
    metadata: {
        description: "Guardian browser extension plugin manifest",
    },
};

export default guardianPluginManifest;
