/**
 * Configuration type definition
 * Represents configuration for DCMAAR components
 */

export interface Config {
  [key: string]: unknown;
}

export interface ConfigProvider {
  get<T = unknown>(key: string, defaultValue?: T): T | undefined;
  set(key: string, value: unknown): void;
  has(key: string): boolean;
  getAll(): Config;
  validate(): boolean;
}

export interface AgentConfig extends Config {
  agentId: string;
  agentName: string;
  capabilities?: string[];
  settings?: Record<string, unknown>;
}

export interface PluginConfig extends Config {
  pluginId: string;
  enabled: boolean;
  options?: Record<string, unknown>;
}
