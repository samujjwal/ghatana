import {
    InMemoryStoragePlugin,
    LocalStoragePlugin,
    RemoteStoragePlugin,
    SlackNotificationPlugin,
    WebhookNotificationPlugin,
} from "@ghatana/dcmaar-plugin-abstractions";
import type {
    ExtensionPluginFactory,
    ExtensionPluginFactoryContext,
} from "@ghatana/dcmaar-browser-extension-core";
import { CPUMonitor } from "./monitors/CPUMonitor";
import { MemoryMonitor } from "./monitors/MemoryMonitor";
import { BatteryMonitor } from "./monitors/BatteryMonitor";

/**
 * Register Device Health plugins with a given ExtensionPluginHost.
 *
 * This keeps plugin registration in one place and avoids duplication
 * across background wiring.
 */
export function registerDeviceHealthPlugins(
    host: {
        registerFactory: (pluginId: string, factory: ExtensionPluginFactory) => void;
    },
): void {
    // Monitor plugins
    host.registerFactory(
        "cpu-monitor",
        (_ctx: ExtensionPluginFactoryContext) =>
            new CPUMonitor() as unknown as Record<string, unknown>,
    );

    host.registerFactory(
        "memory-monitor",
        (_ctx: ExtensionPluginFactoryContext) =>
            new MemoryMonitor() as unknown as Record<string, unknown>,
    );

    host.registerFactory(
        "battery-monitor",
        (_ctx: ExtensionPluginFactoryContext) =>
            new BatteryMonitor() as unknown as Record<string, unknown>,
    );

    // Storage plugins
    host.registerFactory(
        "device-health-storage-inmemory",
        (_ctx: ExtensionPluginFactoryContext) =>
            new InMemoryStoragePlugin() as unknown as Record<string, unknown>,
    );

    host.registerFactory(
        "device-health-storage-local",
        (_ctx: ExtensionPluginFactoryContext) =>
            new LocalStoragePlugin() as unknown as Record<string, unknown>,
    );

    host.registerFactory(
        "device-health-storage-remote",
        (_ctx: ExtensionPluginFactoryContext) =>
            new RemoteStoragePlugin() as unknown as Record<string, unknown>,
    );

    // Notification plugins
    host.registerFactory(
        "device-health-notify-slack",
        (_ctx: ExtensionPluginFactoryContext) =>
            new SlackNotificationPlugin() as unknown as Record<string, unknown>,
    );

    host.registerFactory(
        "device-health-notify-webhook",
        (_ctx: ExtensionPluginFactoryContext) =>
            new WebhookNotificationPlugin() as unknown as Record<string, unknown>,
    );
}
