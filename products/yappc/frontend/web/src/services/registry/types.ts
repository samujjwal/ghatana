export interface ComponentDefinition {
  id: string;
  name?: string;
  label?: string;
  description?: string;
  category?: string;
  type?: string;
  version?: string;
  icon?: string;
  component?: unknown;
  props?: Record<string, unknown>;
  defaultProps?: Record<string, unknown>;
  defaultData?: Record<string, unknown>;
  dataSchemaRef?: string;
  deprecated?: boolean;
  metadata?: Record<string, unknown>;
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
