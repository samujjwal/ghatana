/**
 * Theme Applicator
 *
 * Applies design tokens to component props in real-time,
 * supporting the 4-layer theme system (base/brand/workspace/app).
 *
 * @module canvas/renderer/ThemeApplicator
 */

import { DesignTokenMapper } from '../adapters/DesignTokenMapper';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type ThemeLayer = 'base' | 'brand' | 'workspace' | 'app';

/**
 *
 */
export interface TokenRegistry {
  base: Record<string, unknown>;
  brand: Record<string, unknown>;
  workspace: Record<string, unknown>;
  app: Record<string, unknown>;
}

/**
 *
 */
export interface ResolvedProps {
  props: Record<string, unknown>;
  styles: Record<string, unknown>;
  unresolvedTokens: string[];
}

/**
 *
 */
export interface ThemeContext {
  activeLayer: ThemeLayer;
  tokens: TokenRegistry;
  fallbackValues: Record<string, unknown>;
}

// ============================================================================
// Theme Applicator Implementation
// ============================================================================

/**
 *
 */
export class ThemeApplicator {
  /**
   * Apply theme tokens to component props
   */
  static applyTheme(
    props: Record<string, unknown>,
    tokens: Record<string, string> | undefined,
    context: ThemeContext
  ): ResolvedProps {
    const resolvedProps = { ...props };
    const styles: Record<string, unknown> = {};
    const unresolvedTokens: string[] = [];

    // If no tokens, return props as-is
    if (!tokens || Object.keys(tokens).length === 0) {
      return {
        props: resolvedProps,
        styles: {},
        unresolvedTokens: [],
      };
    }

    // Resolve each token reference
    for (const [propName, tokenPath] of Object.entries(tokens)) {
      const resolvedValue = this.resolveToken(tokenPath, context);

      if (resolvedValue !== undefined) {
        // Apply to props
        resolvedProps[propName] = resolvedValue;

        // Also generate CSS styles if applicable
        const cssValue = DesignTokenMapper.tokensToStyles(
          { [propName]: tokenPath },
          this.flattenTokens(context.tokens)
        );
        Object.assign(styles, cssValue);
      } else {
        unresolvedTokens.push(tokenPath);

        // Try fallback
        const fallbackValue = context.fallbackValues[propName];
        if (fallbackValue !== undefined) {
          resolvedProps[propName] = fallbackValue;
        }
      }
    }

    return {
      props: resolvedProps,
      styles,
      unresolvedTokens,
    };
  }

  /**
   * Resolve a token path through the theme layers
   */
  static resolveToken(
    tokenPath: string,
    context: ThemeContext
  ): unknown {
    // Remove $ prefix if present
    const cleanPath = tokenPath.startsWith('$') ? tokenPath.slice(1) : tokenPath;

    // Try to resolve from active layer first, then cascade down
    const layerOrder = this.getLayerCascade(context.activeLayer);

    for (const layer of layerOrder) {
      const value = this.getNestedValue(context.tokens[layer], cleanPath);
      if (value !== undefined) {
        return value;
      }
    }

    return undefined;
  }

  /**
   * Get layer cascade order (higher layers override lower ones)
   */
  private static getLayerCascade(activeLayer: ThemeLayer): ThemeLayer[] {
    const order: ThemeLayer[] = ['base'];

    if (activeLayer === 'brand' || activeLayer === 'workspace' || activeLayer === 'app') {
      order.push('brand');
    }

    if (activeLayer === 'workspace' || activeLayer === 'app') {
      order.push('workspace');
    }

    if (activeLayer === 'app') {
      order.push('app');
    }

    return order;
  }

  /**
   * Get nested value from object using dot notation
   */
  private static getNestedValue(obj: Record<string, unknown>, path: string): unknown {
    const keys = path.split('.');
    let current: unknown = obj;

    for (const key of keys) {
      if (current === undefined || current === null) {
        return undefined;
      }
      current = (current as Record<string, unknown>)[key];
    }

    return current;
  }

  /**
   * Flatten token registry to single object for DesignTokenMapper
   */
  private static flattenTokens(tokens: TokenRegistry): Record<string, unknown> {
    return {
      ...tokens.base,
      ...tokens.brand,
      ...tokens.workspace,
      ...tokens.app,
    };
  }

  /**
   * Create CSS custom properties from tokens
   */
  static createCSSVariables(
    tokens: TokenRegistry,
    layer: ThemeLayer
  ): Record<string, string> {
    const cssVars: Record<string, string> = {};
    const layerTokens = tokens[layer];

    const flattenObject = (obj: Record<string, unknown>, prefix = '') => {
      for (const [key, value] of Object.entries(obj)) {
        const varName = prefix ? `${prefix}-${key}` : key;

        if (typeof value === 'object' && !Array.isArray(value)) {
          flattenObject(value, varName);
        } else {
          cssVars[`--${varName}`] = String(value);
        }
      }
    };

    flattenObject(layerTokens);
    return cssVars;
  }

  /**
   * Merge multiple theme contexts (for component hierarchies)
   */
  static mergeContexts(
    parentContext: ThemeContext,
    childOverrides: Partial<ThemeContext>
  ): ThemeContext {
    return {
      activeLayer: childOverrides.activeLayer ?? parentContext.activeLayer,
      tokens: childOverrides.tokens ?? parentContext.tokens,
      fallbackValues: {
        ...parentContext.fallbackValues,
        ...(childOverrides.fallbackValues || {}),
      },
    };
  }

  /**
   * Validate token paths
   */
  static validateTokenPaths(
    tokens: Record<string, string>,
    context: ThemeContext
  ): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    for (const [propName, tokenPath] of Object.entries(tokens)) {
      const resolved = this.resolveToken(tokenPath, context);
      if (resolved === undefined) {
        errors.push(`Token not found: ${tokenPath} for prop ${propName}`);
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Get all available tokens for a given context
   */
  static getAvailableTokens(context: ThemeContext): string[] {
    const allTokens = this.flattenTokens(context.tokens);
    const paths: string[] = [];

    const collectPaths = (obj: Record<string, unknown>, prefix = '') => {
      for (const [key, value] of Object.entries(obj)) {
        const path = prefix ? `${prefix}.${key}` : key;

        if (typeof value === 'object' && !Array.isArray(value)) {
          collectPaths(value, path);
        } else {
          paths.push(path);
        }
      }
    };

    collectPaths(allTokens);
    return paths;
  }

  /**
   * Create a default theme context
   */
  static createDefaultContext(): ThemeContext {
    return {
      activeLayer: 'base',
      tokens: {
        base: {},
        brand: {},
        workspace: {},
        app: {},
      },
      fallbackValues: {},
    };
  }
}
