/**
 * Design Token Mapper
 *
 * Maps design tokens to canvas styles and vice versa
 */

/**
 * Style property mapping
 */
interface StyleMapping {
  tokenCategory: string;
  cssProperty: string;
  transform?: (value: unknown) => any;
  reverse?: (value: unknown) => any;
}

/**
 * Design token mapper for canvas integration
 */
export class DesignTokenMapper {
  /**
   * Common token-to-CSS mappings
   */
  private static readonly STYLE_MAPPINGS: Record<string, StyleMapping> = {
    color: {
      tokenCategory: 'color',
      cssProperty: 'color',
    },
    backgroundColor: {
      tokenCategory: 'color',
      cssProperty: 'backgroundColor',
    },
    borderColor: {
      tokenCategory: 'color',
      cssProperty: 'borderColor',
    },
    fontSize: {
      tokenCategory: 'typography.fontSize',
      cssProperty: 'fontSize',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    fontFamily: {
      tokenCategory: 'typography.fontFamily',
      cssProperty: 'fontFamily',
    },
    fontWeight: {
      tokenCategory: 'typography.fontWeight',
      cssProperty: 'fontWeight',
    },
    lineHeight: {
      tokenCategory: 'typography.lineHeight',
      cssProperty: 'lineHeight',
    },
    padding: {
      tokenCategory: 'spacing',
      cssProperty: 'padding',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    paddingTop: {
      tokenCategory: 'spacing',
      cssProperty: 'paddingTop',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    paddingRight: {
      tokenCategory: 'spacing',
      cssProperty: 'paddingRight',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    paddingBottom: {
      tokenCategory: 'spacing',
      cssProperty: 'paddingBottom',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    paddingLeft: {
      tokenCategory: 'spacing',
      cssProperty: 'paddingLeft',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    margin: {
      tokenCategory: 'spacing',
      cssProperty: 'margin',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    marginTop: {
      tokenCategory: 'spacing',
      cssProperty: 'marginTop',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    marginRight: {
      tokenCategory: 'spacing',
      cssProperty: 'marginRight',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    marginBottom: {
      tokenCategory: 'spacing',
      cssProperty: 'marginBottom',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    marginLeft: {
      tokenCategory: 'spacing',
      cssProperty: 'marginLeft',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    borderRadius: {
      tokenCategory: 'borderRadius',
      cssProperty: 'borderRadius',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    borderWidth: {
      tokenCategory: 'borderWidth',
      cssProperty: 'borderWidth',
      transform: (value) => (typeof value === 'number' ? `${value}px` : value),
      reverse: (value) => parseInt(value, 10),
    },
    boxShadow: {
      tokenCategory: 'shadow',
      cssProperty: 'boxShadow',
    },
    opacity: {
      tokenCategory: 'opacity',
      cssProperty: 'opacity',
    },
  };

  /**
   * Convert design tokens to canvas styles
   */
  static tokensToStyles(
    tokens: Record<string, string>,
    tokenValues?: Record<string, unknown>
  ): Record<string, unknown> {
    const styles: Record<string, unknown> = {};

    for (const [propName, tokenPath] of Object.entries(tokens)) {
      const mapping = this.STYLE_MAPPINGS[propName];

      if (mapping) {
        // Resolve token value
        const value = tokenValues
          ? this.resolveTokenFromValues(tokenPath, tokenValues)
          : tokenPath; // Keep as reference if no values provided

        // Apply transformation if defined
        const transformedValue = mapping.transform && value !== tokenPath
          ? mapping.transform(value)
          : value;

        // Set CSS property
        styles[mapping.cssProperty] = transformedValue;
      } else {
        // No mapping, use as-is
        const value = tokenValues
          ? this.resolveTokenFromValues(tokenPath, tokenValues)
          : tokenPath;
        styles[propName] = value;
      }
    }

    return styles;
  }

  /**
   * Extract token references from styles
   */
  static stylesToTokens(styles: Record<string, unknown>): Record<string, string> {
    const tokens: Record<string, string> = {};

    for (const [cssProperty, value] of Object.entries(styles)) {
      // Check if value is a token reference
      if (this.isTokenReference(value)) {
        // Find matching prop name
        const propName = this.findPropNameForCSS(cssProperty);
        if (propName) {
          tokens[propName] = value;
        }
      }
    }

    return tokens;
  }

  /**
   * Check if value is a token reference
   */
  static isTokenReference(value: unknown): value is string {
    return typeof value === 'string' && value.startsWith('$');
  }

  /**
   * Resolve token value from path using token values object
   */
  private static resolveTokenFromValues(
    tokenPath: string,
    tokenValues: Record<string, unknown>
  ): unknown {
    // Remove $ prefix if present
    const path = tokenPath.startsWith('$') ? tokenPath.substring(1) : tokenPath;

    // Split path and traverse object
    const parts = path.split('.');
    let current: unknown = tokenValues;

    for (const part of parts) {
      if (current && typeof current === 'object' && part in current) {
        current = (current as Record<string, unknown>)[part];
      } else {
        // Token not found, return reference
        return tokenPath;
      }
    }

    // Return resolved value or reference if not found
    return current !== undefined ? current : tokenPath;
  }

  /**
   * Find component prop name for CSS property
   */
  private static findPropNameForCSS(cssProperty: string): string | null {
    for (const [propName, mapping] of Object.entries(this.STYLE_MAPPINGS)) {
      if (mapping.cssProperty === cssProperty) {
        return propName;
      }
    }
    return null;
  }

  /**
   * Apply theme layer to tokens
   */
  static applyThemeLayer(
    tokens: Record<string, string>,
    layer: 'base' | 'brand' | 'workspace' | 'app'
  ): Record<string, string> {
    const themedTokens: Record<string, string> = {};

    for (const [propName, tokenPath] of Object.entries(tokens)) {
      // Only prepend if not already themed
      if (!tokenPath.includes('.')) {
        themedTokens[propName] = `${layer}.${tokenPath}`;
      } else {
        themedTokens[propName] = tokenPath;
      }
    }

    return themedTokens;
  }

  /**
   * Extract tokens from component props
   */
  static extractTokensFromProps(props: Record<string, unknown>): {
    tokens: Record<string, string>;
    cleanProps: Record<string, unknown>;
  } {
    const tokens: Record<string, string> = {};
    const cleanProps: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(props)) {
      if (this.isTokenReference(value)) {
        tokens[key] = value;
        cleanProps[key] = value; // Keep reference in props
      } else {
        cleanProps[key] = value;
      }
    }

    return { tokens, cleanProps };
  }

  /**
   * Merge token mappings
   */
  static mergeTokens(
    base: Record<string, string>,
    override: Record<string, string>
  ): Record<string, string> {
    return { ...base, ...override };
  }

  /**
   * Validate token path format
   */
  static validateTokenPath(tokenPath: string): { valid: boolean; error?: string } {
    if (!tokenPath.startsWith('$')) {
      return { valid: false, error: 'Token path must start with $' };
    }

    const path = tokenPath.substring(1);
    if (path.length === 0) {
      return { valid: false, error: 'Token path cannot be empty' };
    }

    // Check for valid characters
    if (!/^[a-zA-Z0-9._-]+$/.test(path)) {
      return { valid: false, error: 'Token path contains invalid characters' };
    }

    return { valid: true };
  }

  /**
   * Get token category from path
   */
  static getTokenCategory(tokenPath: string): string | null {
    const path = tokenPath.startsWith('$') ? tokenPath.substring(1) : tokenPath;
    const parts = path.split('.');
    return parts[0] || null;
  }

  /**
   * Create style mapping for custom property
   */
  static createCustomMapping(
    propName: string,
    tokenCategory: string,
    cssProperty: string,
    transform?: (value: unknown) => any
  ): void {
    this.STYLE_MAPPINGS[propName] = {
      tokenCategory,
      cssProperty,
      transform,
    };
  }
}
