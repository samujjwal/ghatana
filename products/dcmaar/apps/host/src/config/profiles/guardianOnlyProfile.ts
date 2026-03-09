import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";
import {
    GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
    GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
} from "@ghatana/dcmaar-guardian-plugins";

export const guardianOnlyProfile: ExtensionPluginManifest = {
    appId: "dcmaar-unified-host-guardian-only",
    version: "0.1.0",
    plugins: [
        {
            pluginId: GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
            enabled: true,
            type: "data-collector",
        },
        {
            pluginId: GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
            enabled: true,
            type: "data-collector",
        },
    ],
    connectors: [],
    metadata: {
        profileId: "guardian-only",
    },
};
