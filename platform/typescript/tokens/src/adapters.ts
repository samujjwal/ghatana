/**
 * @fileoverview Target Adapters for Design System Tokens
 *
 * Provides adapters to convert token graph into different target formats:
 * - CSS variables (for web styling)
 * - Tailwind config (for utility-first CSS)
 * - React theme object (for theming libraries)
 * - JSON tokens (for Design Token Community Group format)
 *
 * @doc.type module
 * @doc.purpose Token target adapters
 * @doc.layer platform
 * @doc.pattern AdapterPattern
 */

import type { TokenGraph, TokenNode } from './token-graph.js';

// ============================================================================
// CSS VARIABLES ADAPTER
// ============================================================================

export interface CSSVariableConfig {
  prefix?: string;
  selector?: string;
  format?: 'css' | 'scss' | 'less';
}

export class CSSVariableAdapter {
  constructor(private graph: TokenGraph) {}

  /**
   * Convert tokens to CSS variables.
   */
  toCSSVariables(config: CSSVariableConfig = {}): string {
    const { prefix = '--', selector = ':root', format = 'css' } = config;
    const lines: string[] = [];

    lines.push(`${selector} {`);

    for (const [id, node] of this.graph.nodes.entries()) {
      const varName = this.toCSSVarName(id, prefix);
      const value = this.formatValue(node.value, format);
      const comment = node.description ? `/* ${node.description} */` : '';
      
      if (comment) lines.push(`  ${comment}`);
      lines.push(`  ${varName}: ${value};`);
    }

    lines.push('}');

    return lines.join('\n');
  }

  private toCSSVarName(id: string, prefix: string): string {
    // Convert token.id (e.g., 'color.primary') to CSS var (e.g., '--color-primary')
    return prefix + id.replace(/\./g, '-');
  }

  private formatValue(value: string | number, format: string): string {
    if (typeof value === 'number') {
      return `${value}px`;
    }
    return value;
  }

  /**
   * Generate CSS variable usage map.
   */
  toUsageMap(): Record<string, string> {
    const map: Record<string, string> = {};

    for (const [id, node] of this.graph.nodes.entries()) {
      const varName = this.toCSSVarName(id, '--');
      map[id] = `var(${varName})`;
    }

    return map;
  }
}

// ============================================================================
// TAILWIND CONFIG ADAPTER
// ============================================================================

export interface TailwindConfigConfig {
  extend?: boolean;
  colorNames?: Record<string, string>;
}

export class TailwindConfigAdapter {
  constructor(private graph: TokenGraph) {}

  /**
   * Convert tokens to Tailwind config format.
   */
  toTailwindConfig(config: TailwindConfigConfig = {}): Record<string, unknown> {
    const { extend = true, colorNames = {} } = config;
    const tailwindConfig: Record<string, unknown> = {};

    if (extend) {
      tailwindConfig.extend = {};
    }

    const colors = this.extractColorTokens(colorNames);
    const spacing = this.extractSpacingTokens();
    const typography = this.extractTypographyTokens();
    const borderRadius = this.extractBorderRadiusTokens();
    const boxShadow = this.extractShadowTokens();

    if (extend) {
      (tailwindConfig.extend as Record<string, unknown>).colors = colors;
      (tailwindConfig.extend as Record<string, unknown>).spacing = spacing;
      (tailwindConfig.extend as Record<string, unknown>).fontSize = typography;
      (tailwindConfig.extend as Record<string, unknown>).borderRadius = borderRadius;
      (tailwindConfig.extend as Record<string, unknown>).boxShadow = boxShadow;
    } else {
      tailwindConfig.colors = colors;
      tailwindConfig.spacing = spacing;
      tailwindConfig.fontSize = typography;
      tailwindConfig.borderRadius = borderRadius;
      tailwindConfig.boxShadow = boxShadow;
    }

    return tailwindConfig;
  }

  private extractColorTokens(nameMap: Record<string, string>): Record<string, string> {
    const colors: Record<string, string> = {};
    const colorNodes = this.getTokensByCategory('color');

    for (const node of colorNodes) {
      const name = nameMap[node.id] || this.toTailwindName(node.id);
      colors[name] = String(node.value);
    }

    return colors;
  }

  private extractSpacingTokens(): Record<string, string> {
    const spacing: Record<string, string> = {};
    const spacingNodes = this.getTokensByCategory('spacing');

    for (const node of spacingNodes) {
      const name = this.toTailwindName(node.id);
      spacing[name] = String(node.value);
    }

    return spacing;
  }

  private extractTypographyTokens(): Record<string, Record<string, string | number>> {
    const typography: Record<string, Record<string, string | number>> = {};
    const typeNodes = this.getTokensByCategory('typography');

    for (const node of typeNodes) {
      const name = this.toTailwindName(node.id);
      typography[name] = {
        fontSize: String(node.value),
        lineHeight: '1.5',
      };
    }

    return typography;
  }

  private extractBorderRadiusTokens(): Record<string, string> {
    const borderRadius: Record<string, string> = {};
    const borderNodes = this.getTokensByCategory('border');

    for (const node of borderNodes) {
      const name = this.toTailwindName(node.id);
      borderRadius[name] = String(node.value);
    }

    return borderRadius;
  }

  private extractShadowTokens(): Record<string, string> {
    const shadows: Record<string, string> = {};
    const shadowNodes = this.getTokensByCategory('shadow');

    for (const node of shadowNodes) {
      const name = this.toTailwindName(node.id);
      shadows[name] = String(node.value);
    }

    return shadows;
  }

  private toTailwindName(id: string): string {
    // Convert token.id (e.g., 'color.primary') to Tailwind name (e.g., 'primary')
    return id.split('.').pop() || id;
  }

  private getTokensByCategory(category: TokenNode['category']): TokenNode[] {
    const categoryIds = this.graph.categories.get(category);
    if (!categoryIds) return [];
    return Array.from(categoryIds).map(id => this.graph.nodes.get(id)!);
  }
}

// ============================================================================
// REACT THEME ADAPTER
// ============================================================================

export interface ReactThemeConfig {
  camelCase?: boolean;
  flatten?: boolean;
}

export class ReactThemeAdapter {
  constructor(private graph: TokenGraph) {}

  /**
   * Convert tokens to React theme object format.
   */
  toReactTheme(config: ReactThemeConfig = {}): Record<string, unknown> {
    const { camelCase = true, flatten = false } = config;
    const theme: Record<string, unknown> = {};

    if (flatten) {
      // Flat structure: { colorPrimary: '#3b82f6', spacingSm: '8px' }
      for (const [id, node] of this.graph.nodes.entries()) {
        const key = camelCase ? this.toCamelCase(id) : id;
        theme[key] = node.value;
      }
    } else {
      // Nested structure: { colors: { primary: '#3b82f6' }, spacing: { sm: '8px' } }
      this.buildNestedTheme(theme, camelCase);
    }

    return theme;
  }

  private buildNestedTheme(theme: Record<string, unknown>, camelCase: boolean): void {
    const categories = Array.from(this.graph.categories.keys());

    for (const category of categories) {
      const categoryObj: Record<string, unknown> = {};
      const nodes = this.getTokensByCategory(category as TokenNode['category']);

      for (const node of nodes) {
        const key = this.toThemeKey(node.id, camelCase);
        categoryObj[key] = node.value;
      }

      const categoryKey = camelCase ? this.toCamelCase(category) : category;
      theme[categoryKey] = categoryObj;
    }
  }

  private toThemeKey(id: string, camelCase: boolean): string {
    if (camelCase) {
      return this.toCamelCase(id.split('.').pop() || id);
    }
    return id.split('.').pop() || id;
  }

  private toCamelCase(str: string): string {
    return str.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
  }

  private getTokensByCategory(category: TokenNode['category']): TokenNode[] {
    const categoryIds = this.graph.categories.get(category);
    if (!categoryIds) return [];
    return Array.from(categoryIds).map(id => this.graph.nodes.get(id)!);
  }
}

// ============================================================================
// JSON TOKENS ADAPTER (DTCG FORMAT)
// ============================================================================

export interface JSONTokensConfig {
  version?: string;
  includeMetadata?: boolean;
}

export class JSONTokensAdapter {
  constructor(private graph: TokenGraph) {}

  /**
   * Convert tokens to Design Token Community Group JSON format.
   */
  toJSONTokens(config: JSONTokensConfig = {}): Record<string, unknown> {
    const { version = '1.0.0', includeMetadata = true } = config;

    const dtcg: Record<string, unknown> = {
      $schema: 'https://tr.designtokens.org/format/',
      version,
      tokens: this.buildDTCGStructure(includeMetadata),
    };

    return dtcg;
  }

  private buildDTCGStructure(includeMetadata: boolean): Record<string, unknown> {
    const tokens: Record<string, unknown> = {};

    for (const [id, node] of this.graph.nodes.entries()) {
      const path = id.split('.');
      let current = tokens;

      // Build nested structure
      for (let i = 0; i < path.length - 1; i++) {
        const segment = path[i];
        if (!current[segment]) {
          current[segment] = {};
        }
        current = current[segment] as Record<string, unknown>;
      }

      const leaf = path[path.length - 1];
      current[leaf] = {
        $value: this.toDTCGValue(node),
        $type: this.toDTCGType(node),
        ...(includeMetadata && node.description ? { $description: node.description } : {}),
      };
    }

    return tokens;
  }

  private toDTCGValue(node: TokenNode): string | number {
    return node.value;
  }

  private toDTCGType(node: TokenNode): string {
    switch (node.category) {
      case 'color':
        return 'color';
      case 'spacing':
      case 'border':
      case 'shadow':
        return 'dimension';
      case 'typography':
        return 'dimension';
      case 'transition':
        return 'transition';
      case 'z-index':
        return 'number';
      case 'breakpoint':
        return 'dimension';
      default:
        return 'custom';
    }
  }
}

// ============================================================================
// ADAPTER FACTORY
// ============================================================================

export class TokenAdapterFactory {
  constructor(private graph: TokenGraph) {}

  /**
   * Create a CSS variable adapter.
   */
  cssVariables(config?: CSSVariableConfig): CSSVariableAdapter {
    return new CSSVariableAdapter(this.graph);
  }

  /**
   * Create a Tailwind config adapter.
   */
  tailwind(config?: TailwindConfigConfig): TailwindConfigAdapter {
    return new TailwindConfigAdapter(this.graph);
  }

  /**
   * Create a React theme adapter.
   */
  reactTheme(config?: ReactThemeConfig): ReactThemeAdapter {
    return new ReactThemeAdapter(this.graph);
  }

  /**
   * Create a JSON tokens adapter.
   */
  jsonTokens(config?: JSONTokensConfig): JSONTokensAdapter {
    return new JSONTokensAdapter(this.graph);
  }

  /**
   * Export to all formats.
   */
  exportAll(options: {
    css?: CSSVariableConfig;
    tailwind?: TailwindConfigConfig;
    react?: ReactThemeConfig;
    json?: JSONTokensConfig;
  }): {
    css: string;
    tailwind: Record<string, unknown>;
    react: Record<string, unknown>;
    json: Record<string, unknown>;
  } {
    return {
      css: this.cssVariables(options.css).toCSSVariables(options.css),
      tailwind: this.tailwind(options.tailwind).toTailwindConfig(options.tailwind),
      react: this.reactTheme(options.react).toReactTheme(options.react),
      json: this.jsonTokens(options.json).toJSONTokens(options.json),
    };
  }
}
