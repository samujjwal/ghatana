import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";

export const deviceHealthOnlyProfile: ExtensionPluginManifest = {
    appId: "dcmaar-unified-host-device-health",
    version: "0.1.0",
    plugins: [
        {
            pluginId: "cpu-monitor",
            enabled: true,
            type: "data-collector",
        },
        {
            pluginId: "memory-monitor",
            enabled: true,
            type: "data-collector",
        },
        {
            pluginId: "battery-monitor",
            enabled: true,
            type: "data-collector",
        },
    ],
    connectors: [],
    metadata: {
        profileId: "device-health-only",
    },
};
