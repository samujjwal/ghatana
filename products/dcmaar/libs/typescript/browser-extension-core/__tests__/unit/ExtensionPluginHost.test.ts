/**
 * @fileoverview ExtensionPluginHost Tests
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import {
    ExtensionPluginHost,
    type ExtensionPluginFactoryContext,
} from "../../src/plugins/ExtensionPluginHost";
import type {
    ExtensionPluginManifest,
    ExtensionPluginDescriptor,
} from "@ghatana/dcmaar-types";

// Stub @ghatana/dcmaar-plugin-abstractions with a minimal in-memory implementation
class FakePluginManager {
    private readonly plugins: Record<string, unknown>[] = [];

    async installPlugin(plugin: Record<string, unknown>): Promise<void> {
        this.plugins.push(plugin);
    }

    async uninstallPlugin(pluginId: string): Promise<void> {
        const index = this.plugins.findIndex(
            (p) => (p as { id?: string }).id === pluginId,
        );
        if (index >= 0) {
            this.plugins.splice(index, 1);
        }
    }

    getAllPlugins(): Record<string, unknown>[] {
        return [...this.plugins];
    }
}

vi.mock("@ghatana/dcmaar-plugin-abstractions", () => {
    class FakeRegistry { }
    class FakeLifecycleManager { }
    class FakeLoader {
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        constructor(
            _registry: Record<string, unknown>,
            _lifecycle: Record<string, unknown>,
            _config: Record<string, unknown>,
        ) { }
    }

    return {
        PluginRegistry: FakeRegistry,
        PluginLifecycleManager: FakeLifecycleManager,
        PluginLoader: FakeLoader,
        PluginManager: FakePluginManager,
    };
});

describe("ExtensionPluginHost", () => {
    let manifest: ExtensionPluginManifest;

    beforeEach(() => {
        manifest = {
            appId: "guardian",
            version: "1.0.0",
            plugins: [
                {
                    pluginId: "usage-collector",
                    enabled: true,
                    options: { sampleRate: 1.0 },
                } as ExtensionPluginDescriptor,
                {
                    pluginId: "disabled-plugin",
                    enabled: false,
                } as ExtensionPluginDescriptor,
            ],
        };
    });

    it("installs only enabled plugins from the manifest", async () => {
        const host = new ExtensionPluginHost();

        const factory = vi.fn(
            (ctx: ExtensionPluginFactoryContext): Record<string, unknown> => ({
                id: ctx.descriptor.pluginId,
                name: ctx.descriptor.pluginId,
                version: manifest.version ?? "0.0.0",
            }),
        );

        host.registerFactory("usage-collector", factory);

        await host.initializeFromManifest(manifest);

        const manager = host.getPluginManager() as unknown as FakePluginManager;
        const plugins = manager.getAllPlugins();

        expect(factory).toHaveBeenCalledTimes(1);
        expect(plugins).toHaveLength(1);
        expect((plugins[0] as { id?: string }).id).toBe("usage-collector");
    });

    it("shuts down all plugins via the underlying manager", async () => {
        const host = new ExtensionPluginHost();

        host.registerFactory("usage-collector", (ctx: ExtensionPluginFactoryContext) => ({
            id: ctx.descriptor.pluginId,
            name: ctx.descriptor.pluginId,
            version: ctx.manifest.version ?? "0.0.0",
        }));

        await host.initializeFromManifest(manifest);

        const manager = host.getPluginManager() as unknown as FakePluginManager;
        expect(manager.getAllPlugins()).toHaveLength(1);

        await host.shutdown();

        expect(manager.getAllPlugins()).toHaveLength(0);
    });
});
