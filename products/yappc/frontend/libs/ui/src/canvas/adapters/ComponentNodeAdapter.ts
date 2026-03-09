/**
 * Component Node Adapter
 *
 * Bidirectional conversion between Component Schema and Canvas Node
 */

import { DesignTokenMapper } from './DesignTokenMapper';
import { PropertyTransformer } from './PropertyTransformer';

import type { ComponentNode, ComponentNodeData } from '../types';
import type { TransformContext, TransformOptions, TransformResult } from '../types/ComponentSchema';

/**
 * Component schema (simplified, will integrate with Phase 2)
 */
export interface ComponentSchema {
  id?: string;
  type: string;
  props?: Record<string, unknown>;
  children?: ComponentSchema[];
  dataBinding?: {
    source: string;
    mode: 'one-way' | 'two-way' | 'one-time' | 'expression';
    path?: string;
  };
  validation?: Array<{
    type: string;
    params?: unknown[];
    message?: string;
  }>;
  events?: Record<string, { emit: string; payload?: Record<string, unknown> }>;
  metadata?: {
    label?: string;
    description?: string;
    category?: string;
  };
}

/**
 * Component size calculator
 */
interface ComponentSizeConfig {
  width: number;
  height: number;
}

/**
 * Default component sizes
 */
const DEFAULT_COMPONENT_SIZES: Record<string, ComponentSizeConfig> = {
  Button: { width: 120, height: 40 },
  TextField: { width: 200, height: 40 },
  TextArea: { width: 300, height: 120 },
  Select: { width: 200, height: 40 },
  Checkbox: { width: 150, height: 32 },
  Radio: { width: 150, height: 32 },
  Switch: { width: 60, height: 32 },
  Card: { width: 300, height: 200 },
  Container: { width: 400, height: 300 },
  Grid: { width: 500, height: 400 },
  Flex: { width: 400, height: 300 },
  Stack: { width: 300, height: 400 },
  Box: { width: 200, height: 200 },
  Text: { width: 200, height: 32 },
  Heading: { width: 300, height: 48 },
  Image: { width: 200, height: 200 },
  Icon: { width: 24, height: 24 },
  Badge: { width: 80, height: 24 },
  Tag: { width: 80, height: 24 },
  Avatar: { width: 40, height: 40 },
  Divider: { width: 200, height: 1 },
  Spacer: { width: 16, height: 16 },
};

/**
 * Component Node Adapter
 */
export class ComponentNodeAdapter {
  /**
   * Convert Component Schema to Canvas Node
   */
  static schemaToNode(
    schema: ComponentSchema,
    context: TransformContext,
    options: TransformOptions = {}
  ): TransformResult<ComponentNode> {
    const warnings: string[] = [];
    const errors: string[] = [];

    try {
      // Generate or use existing ID
      const id = options.preserveIds && schema.id
        ? schema.id
        : options.generateIds
        ? this.generateId()
        : schema.id || this.generateId();

      // Transform props to node data
      const nodeData = PropertyTransformer.propsToNodeData(
        schema.type,
        schema.props || {}
      );

      // Extract and apply tokens
      if (nodeData.tokens) {
        // Apply theme layer if specified
        nodeData.tokens = DesignTokenMapper.applyThemeLayer(
          nodeData.tokens,
          context.theme
        );
      }

      // Convert tokens to styles for canvas
      const styles = nodeData.tokens
        ? DesignTokenMapper.tokensToStyles(nodeData.tokens, context.tokens)
        : {};

      // Add data binding if present
      if (schema.dataBinding) {
        nodeData.dataBinding = schema.dataBinding;
      }

      // Add validation if present
      if (schema.validation) {
        nodeData.validation = {
          rules: schema.validation.map((rule) => ({
            type: rule.type,
            params: rule.params,
            message: rule.message,
          })),
        };
      }

      // Add events if present
      if (schema.events) {
        nodeData.events = schema.events;
      }

      // Add metadata if present
      if (schema.metadata || options.includeMetadata) {
        nodeData.metadata = schema.metadata;
      }

      // Calculate component size
      const size = this.calculateSize(schema.type, nodeData.props, schema);

      // Create canvas node
      const node: ComponentNode = {
        id,
        type: 'component',
        position: context.offset || { x: 0, y: 0 },
        size,
        style: styles,
        data: nodeData as ComponentNodeData,
      };

      // Validate if requested
      if (options.validate) {
        const validation = this.validateNode(node);
        if (!validation.valid) {
          errors.push(...validation.errors.map((e) => e.message));
        }
        warnings.push(...validation.errors.filter((e) => e.severity === 'warning').map((e) => e.message));
      }

      return {
        data: node,
        warnings,
        errors,
        metadata: options.includeMetadata
          ? {
              transformedAt: Date.now(),
              context,
            }
          : undefined,
      };
    } catch (error) {
      errors.push(`Schema to node conversion failed: ${(error as Error).message}`);
      throw new Error(`Failed to convert schema to node: ${errors.join(', ')}`);
    }
  }

  /**
   * Convert Canvas Node to Component Schema
   */
  static nodeToSchema(
    node: ComponentNode,
    options: TransformOptions = {}
  ): TransformResult<ComponentSchema> {
    const warnings: string[] = [];
    const errors: string[] = [];

    try {
      // Extract node data
      const { componentType, props, tokens, dataBinding, validation, events, metadata } = node.data;

      // Transform node data to props
      const transformedProps = PropertyTransformer.nodeDataToProps(node.data);

      // Create component schema
      const schema: ComponentSchema = {
        id: node.id,
        type: componentType,
        props: transformedProps,
      };

      // Add data binding if present
      if (dataBinding) {
        schema.dataBinding = dataBinding;
      }

      // Add validation if present
      if (validation) {
        schema.validation = validation.rules.map((rule) => ({
          type: rule.type,
          params: rule.params,
          message: rule.message,
        }));
      }

      // Add events if present
      if (events) {
        schema.events = events;
      }

      // Add metadata if present or requested
      if (metadata || options.includeMetadata) {
        schema.metadata = metadata;
      }

      // Validate if requested
      if (options.validate) {
        const validation = this.validateSchema(schema);
        if (!validation.valid) {
          errors.push(...validation.errors.map((e) => e.message));
        }
        warnings.push(...validation.errors.filter((e) => e.severity === 'warning').map((e) => e.message));
      }

      return {
        data: schema,
        warnings,
        errors,
        metadata: options.includeMetadata
          ? {
              transformedAt: Date.now(),
            }
          : undefined,
      };
    } catch (error) {
      errors.push(`Node to schema conversion failed: ${(error as Error).message}`);
      throw new Error(`Failed to convert node to schema: ${errors.join(', ')}`);
    }
  }

  /**
   * Roundtrip conversion test
   */
  static validateRoundtrip(
    originalSchema: ComponentSchema,
    context: TransformContext,
    options: TransformOptions = {}
  ): { valid: boolean; errors: string[]; warnings: string[] } {
    const errors: string[] = [];
    const warnings: string[] = [];

    try {
      // Schema -> Node
      const nodeResult = this.schemaToNode(originalSchema, context, { ...options, validate: true });

      if (nodeResult.errors.length > 0) {
        errors.push(...nodeResult.errors);
      }
      warnings.push(...nodeResult.warnings);

      // Node -> Schema
      const schemaResult = this.nodeToSchema(nodeResult.data, { ...options, validate: true });

      if (schemaResult.errors.length > 0) {
        errors.push(...schemaResult.errors);
      }
      warnings.push(...schemaResult.warnings);

      // Compare schemas
      const comparisonErrors = this.compareSchemas(originalSchema, schemaResult.data);
      if (comparisonErrors.length > 0) {
        errors.push(...comparisonErrors);
      }

      return {
        valid: errors.length === 0,
        errors,
        warnings,
      };
    } catch (error) {
      errors.push(`Roundtrip validation failed: ${(error as Error).message}`);
      return { valid: false, errors, warnings };
    }
  }

  /**
   * Calculate component size based on type and props
   */
  private static calculateSize(
    componentType: string,
    props: Record<string, unknown>,
    schema?: ComponentSchema
  ): { width: number; height: number } {
    // Check for explicit size in props
    if (props.width && props.height) {
      return {
        width: typeof props.width === 'number' ? props.width : parseInt(props.width, 10),
        height: typeof props.height === 'number' ? props.height : parseInt(props.height, 10),
      };
    }

    // Check for children (containers should be larger)
    const hasChildren = schema?.children && schema.children.length > 0;
    if (hasChildren) {
      const defaultSize = DEFAULT_COMPONENT_SIZES[componentType] || { width: 400, height: 300 };
      return {
        width: defaultSize.width * 1.5,
        height: defaultSize.height * 1.5,
      };
    }

    // Return default size for component type
    return DEFAULT_COMPONENT_SIZES[componentType] || { width: 200, height: 100 };
  }

  /**
   * Generate unique ID
   */
  private static generateId(): string {
    return `comp-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Validate canvas node
   */
  private static validateNode(node: ComponentNode): {
    valid: boolean;
    errors: Array<{ field: string; message: string; severity: 'error' | 'warning' | 'info' }>;
  } {
    const errors: Array<{ field: string; message: string; severity: 'error' | 'warning' | 'info' }> = [];

    if (!node.id) {
      errors.push({ field: 'id', message: 'Node missing ID', severity: 'error' });
    }

    if (!node.data.componentType) {
      errors.push({ field: 'componentType', message: 'Node missing component type', severity: 'error' });
    }

    if (!node.position) {
      errors.push({ field: 'position', message: 'Node missing position', severity: 'error' });
    }

    if (!node.size) {
      errors.push({ field: 'size', message: 'Node missing size', severity: 'error' });
    }

    // Validate token references
    if (node.data.tokens) {
      for (const [prop, tokenPath] of Object.entries(node.data.tokens)) {
        const validation = DesignTokenMapper.validateTokenPath(tokenPath);
        if (!validation.valid) {
          errors.push({
            field: `tokens.${prop}`,
            message: validation.error || 'Invalid token path',
            severity: 'warning',
          });
        }
      }
    }

    return { valid: errors.filter((e) => e.severity === 'error').length === 0, errors };
  }

  /**
   * Validate component schema
   */
  private static validateSchema(schema: ComponentSchema): {
    valid: boolean;
    errors: Array<{ field: string; message: string; severity: 'error' | 'warning' | 'info' }>;
  } {
    const errors: Array<{ field: string; message: string; severity: 'error' | 'warning' | 'info' }> = [];

    if (!schema.type) {
      errors.push({ field: 'type', message: 'Schema missing type', severity: 'error' });
    }

    if (!schema.props) {
      errors.push({ field: 'props', message: 'Schema missing props', severity: 'warning' });
    }

    return { valid: errors.filter((e) => e.severity === 'error').length === 0, errors };
  }

  /**
   * Compare two schemas for equality
   */
  private static compareSchemas(schema1: ComponentSchema, schema2: ComponentSchema): string[] {
    const errors: string[] = [];

    if (schema1.type !== schema2.type) {
      errors.push(`Type mismatch: ${schema1.type} !== ${schema2.type}`);
    }

    // Compare props keys (values may differ due to transformations)
    const props1Keys = Object.keys(schema1.props || {}).sort();
    const props2Keys = Object.keys(schema2.props || {}).sort();

    if (JSON.stringify(props1Keys) !== JSON.stringify(props2Keys)) {
      errors.push(`Props keys mismatch: ${props1Keys.join(',')} !== ${props2Keys.join(',')}`);
    }

    return errors;
  }

  /**
   * Batch convert multiple schemas to nodes
   */
  static batchSchemaToNode(
    schemas: ComponentSchema[],
    context: TransformContext,
    options: TransformOptions = {}
  ): TransformResult<ComponentNode[]> {
    const nodes: ComponentNode[] = [];
    const warnings: string[] = [];
    const errors: string[] = [];

    for (const schema of schemas) {
      try {
        const result = this.schemaToNode(schema, context, options);
        nodes.push(result.data);
        warnings.push(...result.warnings);
        errors.push(...result.errors);
      } catch (error) {
        errors.push(`Failed to convert schema ${schema.id}: ${(error as Error).message}`);
      }
    }

    return { data: nodes, warnings, errors };
  }

  /**
   * Batch convert multiple nodes to schemas
   */
  static batchNodeToSchema(
    nodes: ComponentNode[],
    options: TransformOptions = {}
  ): TransformResult<ComponentSchema[]> {
    const schemas: ComponentSchema[] = [];
    const warnings: string[] = [];
    const errors: string[] = [];

    for (const node of nodes) {
      try {
        const result = this.nodeToSchema(node, options);
        schemas.push(result.data);
        warnings.push(...result.warnings);
        errors.push(...result.errors);
      } catch (error) {
        errors.push(`Failed to convert node ${node.id}: ${(error as Error).message}`);
      }
    }

    return { data: schemas, warnings, errors };
  }
}
