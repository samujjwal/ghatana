/**
 * Type definitions for Vite live edit plugin.
 *
 * @doc.type module
 * @doc.purpose Live edit plugin types
 * @doc.layer product
 * @doc.pattern Value Object
 */

/**
 * Configuration options for the live edit plugin.
 *
 * @doc.type interface
 * @doc.purpose Plugin configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface LiveEditOptions {
  /** Enable/disable the plugin */
  enabled?: boolean;
  /** Dev server port */
  port?: number;
  /** File patterns to include */
  include?: string[];
  /** File patterns to exclude */
  exclude?: string[];
  /** Custom component detector */
  isComponent?: (code: string) => boolean;
}

/**
 * Component property definition.
 *
 * @doc.type interface
 * @doc.purpose Component property metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PropDefinition {
  /** Property name */
  name: string;
  /** Property type */
  type: string;
  /** Whether property is required */
  required: boolean;
  /** Default value */
  defaultValue?: unknown;
  /** Property description */
  description?: string;
}

/**
 * Component state definition.
 *
 * @doc.type interface
 * @doc.purpose Component state metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface StateDefinition {
  /** State variable name */
  name: string;
  /** State type */
  type: string;
  /** Initial value */
  initialValue?: unknown;
  /** State description */
  description?: string;
}

/**
 * Component event definition.
 *
 * @doc.type interface
 * @doc.purpose Component event metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface EventDefinition {
  /** Event name */
  name: string;
  /** Event handler type */
  type: string;
  /** Event description */
  description?: string;
}

/**
 * Component import definition.
 *
 * @doc.type interface
 * @doc.purpose Component import metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ImportDefinition {
  /** Import name */
  name: string;
  /** Import source */
  source: string;
  /** Whether it's a default import */
  default: boolean;
}

/**
 * Complete component metadata.
 *
 * @doc.type interface
 * @doc.purpose Component metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ComponentMetadata {
  /** Component name */
  name: string;
  /** File path */
  file: string;
  /** Component properties */
  props: PropDefinition[];
  /** Component state */
  state: StateDefinition[];
  /** Component events */
  events: EventDefinition[];
  /** Component imports */
  imports: ImportDefinition[];
  /** Component description */
  description?: string;
  /** Whether component is exported as default */
  isDefault: boolean;
  /** Component source code */
  source: string;
}

/**
 * Live edit message types.
 *
 * @doc.type type
 * @doc.purpose Message type union
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type LiveEditMessage =
  | {
      type: 'component-updated';
      data: {
        file: string;
        metadata: ComponentMetadata;
        timestamp: number;
      };
    }
  | {
      type: 'component-error';
      data: {
        file: string;
        error: string;
        timestamp: number;
      };
    }
  | {
      type: 'metadata-request';
      data: {
        file?: string;
      };
    }
  | {
      type: 'metadata-response';
      data: ComponentMetadata[];
    };
