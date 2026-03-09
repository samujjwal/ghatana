/**
 * Plugin type definition
 * Represents a plugin interface for extending DCMAAR
 */
export interface Plugin {
    id: string;
    name: string;
    version: string;
    description?: string;
    enabled: boolean;
    metadata?: Record<string, unknown>;
}
export interface PluginManager {
    register(plugin: Plugin): void;
    unregister(pluginId: string): void;
    get(pluginId: string): Plugin | null;
    list(): Plugin[];
    enable(pluginId: string): Promise<void>;
    disable(pluginId: string): Promise<void>;
}
export interface IPlugin extends Plugin {
    initialize(): Promise<void>;
    shutdown(): Promise<void>;
    execute(command: string, params?: Record<string, unknown>): Promise<unknown>;
}
//# sourceMappingURL=Plugin.d.ts.map