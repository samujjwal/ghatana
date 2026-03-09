import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";

/**
 * Device Health Extension Plugin Manifest
 *
 * Declares the plugin set for the Device Health browser extension using
 * the shared DCMAAR extension manifest model. This is intentionally
 * minimal scaffolding that can be extended over time.
 */
export const deviceHealthPluginManifest: ExtensionPluginManifest = {
    appId: "device-health",
    version: "0.1.0",
    plugins: [
        // Monitor plugins
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
        // Storage plugins
        {
            pluginId: "device-health-storage-inmemory",
            enabled: true,
            options: {},
        },
        {
            pluginId: "device-health-storage-local",
            enabled: true,
        },
        {
            pluginId: "device-health-storage-remote",
            enabled: true,
        },
        // Notification plugins
        {
            pluginId: "device-health-notify-slack",
            enabled: true,
        },
        {
            pluginId: "device-health-notify-webhook",
            enabled: true,
        },
    ],
    connectors: [],
    metadata: {
        description: "Device Health browser extension plugin manifest",
    },
};

export default deviceHealthPluginManifest;
