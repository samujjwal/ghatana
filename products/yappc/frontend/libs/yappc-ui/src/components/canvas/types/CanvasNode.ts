/**
 * Canvas Node Types
 *
 * Extends existing @ghatana/yappc-canvas node structure with component-specific data
 */

/**
 * Base canvas node (compatible with @ghatana/yappc-canvas)
 */
export interface BaseCanvasNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  size: { width: number; height: number };
  style?: Record<string, unknown>;
  data?: Record<string, unknown>;
}

/**
 * Component-specific node data
 */
export interface ComponentNodeData {
  /**
   * Component type from registry (e.g., 'Button', 'TextField')
   */
  componentType: string;

  /**
   * Component props
   */
  props: Record<string, unknown>;

  /**
   * Design token references
   */
  tokens?: {
    [propName: string]: string; // Token path (e.g., 'color.primary.500')
  };

  /**
   * Data binding configuration
   */
  dataBinding?: {
    source: string;
    mode: 'one-way' | 'two-way' | 'one-time' | 'expression';
    path?: string;
  };

  /**
   * Event handlers
   */
  events?: {
    [eventName: string]: {
      emit: string; // Event name to emit
      payload?: Record<string, unknown>;
    };
  };

  /**
   * Validation rules
   */
  validation?: {
    rules: Array<{
      type: string;
      params?: unknown[];
      message?: string;
    }>;
  };

  /**
   * Component metadata
   */
  metadata?: {
    label?: string;
    description?: string;
    category?: string;
    tags?: string[];
  };
}

/**
 * Component node (extends canvas node)
 */
export interface ComponentNode extends BaseCanvasNode {
  type: 'component';
  data: ComponentNodeData;
}

/**
 * Data source node
 */
export interface DataSourceNodeData {
  sourceType: 'rest' | 'graphql' | 'static' | 'computed';
  config: Record<string, unknown>;
  schema?: Record<string, unknown>;
}

/**
 *
 */
export interface DataSourceNode extends BaseCanvasNode {
  type: 'datasource';
  data: DataSourceNodeData;
}

/**
 * Event node
 */
export interface EventNodeData {
  eventName: string;
  payload?: Record<string, unknown>;
  middleware?: string[];
}

/**
 *
 */
export interface EventNode extends BaseCanvasNode {
  type: 'event';
  data: EventNodeData;
}

/**
 * Union type for all node types
 */
export type CanvasNodeType = ComponentNode | DataSourceNode | EventNode | BaseCanvasNode;

/**
 * Type guards
 */
export function isComponentNode(node: BaseCanvasNode): node is ComponentNode {
  return node.type === 'component';
}

/**
 *
 */
export function isDataSourceNode(node: BaseCanvasNode): node is DataSourceNode {
  return node.type === 'datasource';
}

/**
 *
 */
export function isEventNode(node: BaseCanvasNode): node is EventNode {
  return node.type === 'event';
}

/**
 * Node creation helpers
 */
export function createComponentNode(
  id: string,
  componentType: string,
  position: { x: number; y: number }
): ComponentNode {
  return {
    id,
    type: 'component',
    position,
    size: { width: 200, height: 100 },
    data: {
      componentType,
      props: {},
    },
  };
}

/**
 *
 */
export function createDataSourceNode(
  id: string,
  sourceType: DataSourceNodeData['sourceType'],
  position: { x: number; y: number }
): DataSourceNode {
  return {
    id,
    type: 'datasource',
    position,
    size: { width: 250, height: 150 },
    data: {
      sourceType,
      config: {},
    },
  };
}
