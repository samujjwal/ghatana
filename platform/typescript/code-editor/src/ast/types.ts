/**
 * Type definitions for AST parser.
 *
 * @doc.type module
 * @doc.purpose AST parser type definitions
 * @doc.layer product
 * @doc.pattern Value Object
 */

/**
 * Component metadata extracted from AST.
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
  /** Component props */
  props: PropDefinition[];
  /** Type definitions */
  types: TypeDefinition[];
  /** Interface definitions */
  interfaces: InterfaceDefinition[];
  /** Source code */
  source: string;
}

/**
 * Property definition with type information.
 *
 * @doc.type interface
 * @doc.purpose Property definition
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
 * Type alias definition.
 *
 * @doc.type interface
 * @doc.purpose Type definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TypeDefinition {
  /** Type name */
  name: string;
  /** Type definition */
  definition: string;
  /** Type kind */
  kind: 'type-alias' | 'enum' | 'union';
}

/**
 * Interface definition.
 *
 * @doc.type interface
 * @doc.purpose Interface definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface InterfaceDefinition {
  /** Interface name */
  name: string;
  /** Interface members */
  members: Record<string, string>;
}
