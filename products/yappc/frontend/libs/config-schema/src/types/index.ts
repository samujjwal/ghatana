/**
 * Type definitions for config schemas
 *
 * @packageDocumentation
 */

/**
 * Interface definition for input/output contracts
 */
export interface InterfaceDefinition {
  /**
   * Unique identifier
   */
  id: string;

  /**
   * Interface name
   */
  name: string;

  /**
   * Interface type
   */
  type: 'input' | 'output' | 'api' | 'event';

  /**
   * Schema definition
   */
  schema: {
    type: 'object' | 'array' | 'string' | 'number' | 'boolean';
    properties: Record<string, PropertyDefinition> | undefined;
    required: string[] | undefined;
  };

  /**
   * Validation rules
   */
  validation: {
    rules: ValidationRule[];
  };

  /**
   * Documentation
   */
  description: string;

  /**
   * Example values
   */
  examples: unknown[];
}

/**
 * Property definition for interface schema
 */
export interface PropertyDefinition {
  type: 'string' | 'number' | 'boolean' | 'object' | 'array';
  description: string | undefined;
  required: boolean | undefined;
  validation: ValidationRule[] | undefined;
}

/**
 * Validation rule
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'custom';
  value: unknown | undefined;
  message: string;
}

/**
 * Event connection definition
 */
export interface EventConnection {
  /**
   * Unique identifier
   */
  id: string;

  /**
   * Source component ID
   */
  sourceComponentId: string;

  /**
   * Source event name
   */
  sourceEvent: string;

  /**
   * Target component ID
   */
  targetComponentId: string;

  /**
   * Target action name
   */
  targetAction: string;

  /**
   * Payload transformation expression
   */
  transform: string | undefined;

  /**
   * Conditional execution expression
   */
  condition: string | undefined;
}

/**
 * Data connection definition
 */
export interface DataConnection {
  /**
   * Unique identifier
   */
  id: string;

  /**
   * Source ID (DataSource or Component)
   */
  sourceId: string;

  /**
   * Source data path
   */
  sourcePath: string;

  /**
   * Target component ID
   */
  targetComponentId: string;

  /**
   * Target property name
   */
  targetProp: string;

  /**
   * Data transformation expression
   */
  transform: string | undefined;

  /**
   * Data binding mode
   */
  mode: 'one-way' | 'two-way' | 'one-time';
}

/**
 * Navigation connection definition
 */
export interface NavigationConnection {
  /**
   * Unique identifier
   */
  id: string;

  /**
   * Source component ID
   */
  sourceComponentId: string;

  /**
   * Source event name
   */
  sourceEvent: string;

  /**
   * Target page ID
   */
  targetPageId: string;

  /**
   * Target route
   */
  targetRoute: string;

  /**
   * Route parameters
   */
  params: Record<string, string> | undefined;
}

/**
 * Connection definitions union type
 */
export type ConnectionDefinition =
  | EventConnection
  | DataConnection
  | NavigationConnection;
