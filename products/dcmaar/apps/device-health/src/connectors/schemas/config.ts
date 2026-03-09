/**
 * Configuration Schemas (Stub)
 *
 * Legacy connector schemas - marked for Phase 6 replacement
 */

export interface DynamicRuntimeConfig {
  version?: string;
  [key: string]: unknown;
}

export interface ExtensionBootstrapConfig {
  version?: string;
  [key: string]: unknown;
}

export interface UserConnectorConfig {
  version?: string;
  [key: string]: unknown;
}

export interface AnalysisConfig {
  version?: string;
  [key: string]: unknown;
}

export interface DataCollectionSpec {
  version?: string;
  [key: string]: unknown;
}

export const DynamicRuntimeConfigSchema = {
  type: 'object',
  properties: {},
};

export const ExtensionBootstrapConfigSchema = {
  type: 'object',
  properties: {},
};

export const UserConnectorConfigSchema = {
  type: 'object',
  properties: {},
};

export function validateConfig(_config: unknown): boolean {
  return true;
}
