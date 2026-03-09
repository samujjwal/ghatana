/**
 * Property Transformer
 *
 * Transforms component props to canvas node data and vice versa
 */

import type { ComponentNodeData } from '../types';

/**
 * Property transformation utilities
 */
export class PropertyTransformer {
  /**
   * Transform component props to canvas node data
   */
  static propsToNodeData(
    componentType: string,
    props: Record<string, unknown>
  ): Partial<ComponentNodeData> {
    // Separate token references from raw values
    const tokens: Record<string, string> = {};
    const transformedProps: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(props)) {
      if (this.isTokenReference(value)) {
        tokens[key] = value;
        // Store token reference in props for now (will be resolved during render)
        transformedProps[key] = value;
      } else {
        transformedProps[key] = this.transformValue(value);
      }
    }

    return {
      componentType,
      props: transformedProps,
      tokens: Object.keys(tokens).length > 0 ? tokens : undefined,
    };
  }

  /**
   * Transform canvas node data to component props
   */
  static nodeDataToProps(nodeData: ComponentNodeData): Record<string, unknown> {
    const props = { ...nodeData.props };

    // Token references are already in props
    // They will be resolved by the renderer with actual theme values

    return props;
  }

  /**
   * Transform individual value
   */
  private static transformValue(value: unknown): unknown {
    // Handle special types
    if (value === null || value === undefined) {
      return value;
    }

    // Handle arrays
    if (Array.isArray(value)) {
      return value.map((item) => this.transformValue(item));
    }

    // Handle objects (but not Date, RegExp, etc.)
    if (typeof value === 'object' && value.constructor === Object) {
      const transformed: Record<string, unknown> = {};
      for (const [key, val] of Object.entries(value)) {
        transformed[key] = this.transformValue(val);
      }
      return transformed;
    }

    // Return as-is for primitives
    return value;
  }

  /**
   * Check if value is a token reference
   */
  static isTokenReference(value: unknown): value is string {
    return typeof value === 'string' && value.startsWith('$');
  }

  /**
   * Extract token path from reference
   */
  static getTokenPath(tokenReference: string): string {
    return tokenReference.substring(1); // Remove leading $
  }

  /**
   * Create token reference from path
   */
  static createTokenReference(tokenPath: string): string {
    return `$${tokenPath}`;
  }

  /**
   * Validate property transformation
   */
  static validate(
    original: Record<string, unknown>,
    transformed: Record<string, unknown>
  ): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    // Check all keys exist
    for (const key of Object.keys(original)) {
      if (!(key in transformed)) {
        errors.push(`Missing property: ${key}`);
      }
    }

    // Check for unexpected keys
    for (const key of Object.keys(transformed)) {
      if (!(key in original)) {
        errors.push(`Unexpected property: ${key}`);
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Deep merge properties
   */
  static mergeProps(
    base: Record<string, unknown>,
    override: Record<string, unknown>
  ): Record<string, unknown> {
    const result: Record<string, unknown> = { ...base };

    for (const [key, value] of Object.entries(override)) {
      if (
        value &&
        typeof value === 'object' &&
        !Array.isArray(value) &&
        key in result &&
        typeof result[key] === 'object'
      ) {
        result[key] = this.mergeProps(result[key], value);
      } else {
        result[key] = value;
      }
    }

    return result;
  }

  /**
   * Extract metadata from props
   */
  static extractMetadata(props: Record<string, unknown>): {
    props: Record<string, unknown>;
    metadata: Record<string, unknown>;
  } {
    const metadata: Record<string, unknown> = {};
    const cleanProps: Record<string, unknown> = {};

    const metadataKeys = ['_meta', '_metadata', 'metadata'];

    for (const [key, value] of Object.entries(props)) {
      if (metadataKeys.includes(key)) {
        Object.assign(metadata, value);
      } else {
        cleanProps[key] = value;
      }
    }

    return { props: cleanProps, metadata };
  }
}
