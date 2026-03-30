// @ts-nocheck
export interface ComponentDefinition {
  id: string;
  name: string;
  tags?: string[];
  schema?: unknown;
}

export interface RegistryEntry {
  id: string;
  definition: ComponentDefinition;
  metadata?: Record<string, unknown>;
}

export interface RegistryFilter {
  query?: string;
  tags?: string[];
}
