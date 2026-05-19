/**
 * @fileoverview Token Graph with Semantic/Base/Alias Relationships
 *
 * Implements a graph data structure to track relationships between design tokens:
 * - Base tokens: Primitive values (colors, spacing, etc.)
 * - Semantic tokens: Meaningful aliases (primary, danger, etc.)
 * - Alias tokens: References to other tokens
 *
 * @doc.type module
 * @doc.purpose Token relationship graph
 * @doc.layer platform
 * @doc.pattern GraphDataStructure
 */

// ============================================================================
// TOKEN TYPES
// ============================================================================

export type TokenType = 'base' | 'semantic' | 'alias';

export interface TokenNode {
  id: string;
  type: TokenType;
  value: string | number;
  category: 'color' | 'spacing' | 'typography' | 'border' | 'shadow' | 'transition' | 'z-index' | 'breakpoint';
  description?: string;
  references?: string[]; // IDs of tokens this token references
  referencedBy?: string[]; // IDs of tokens that reference this token
  metadata?: {
    originalValue?: string;
    source?: string;
    deprecated?: boolean;
    deprecationMessage?: string;
  };
}

export interface TokenEdge {
  from: string; // Token ID that references
  to: string; // Token ID being referenced
  type: 'alias' | 'semantic' | 'derived';
}

export interface TokenGraph {
  nodes: Map<string, TokenNode>;
  edges: Map<string, TokenEdge[]>;
  categories: Map<string, Set<string>>;
}

// ============================================================================
// TOKEN GRAPH BUILDER
// ============================================================================

export class TokenGraphBuilder {
  private graph: TokenGraph;

  constructor() {
    this.graph = {
      nodes: new Map(),
      edges: new Map(),
      categories: new Map(),
    };
  }

  /**
   * Add a base token (primitive value).
   */
  addBaseToken(
    id: string,
    value: string | number,
    category: TokenNode['category'],
    description?: string,
  ): this {
    this.addNode(id, {
      id,
      type: 'base',
      value,
      category,
      description,
    });
    return this;
  }

  /**
   * Add a semantic token (meaningful alias).
   */
  addSemanticToken(
    id: string,
    value: string | number,
    category: TokenNode['category'],
    references?: string[],
    description?: string,
  ): this {
    const node: TokenNode = {
      id,
      type: 'semantic',
      value,
      category,
      references,
      description,
    };

    this.addNode(id, node);

    if (references) {
      for (const refId of references) {
        this.addEdge(id, refId, 'semantic');
      }
    }

    return this;
  }

  /**
   * Add an alias token (direct reference to another token).
   */
  addAliasToken(
    id: string,
    value: string | number,
    category: TokenNode['category'],
    referenceId: string,
    description?: string,
  ): this {
    const node: TokenNode = {
      id,
      type: 'alias',
      value,
      category,
      references: [referenceId],
      description,
    };

    this.addNode(id, node);
    this.addEdge(id, referenceId, 'alias');

    return this;
  }

  /**
   * Add a derived token (computed from other tokens).
   */
  addDerivedToken(
    id: string,
    value: string | number,
    category: TokenNode['category'],
    references: string[],
    description?: string,
  ): this {
    const node: TokenNode = {
      id,
      type: 'alias', // Derived tokens are a type of alias
      value,
      category,
      references,
      description,
      metadata: {
        originalValue: String(value),
      },
    };

    this.addNode(id, node);

    for (const refId of references) {
      this.addEdge(id, refId, 'derived');
    }

    return this;
  }

  private addNode(id: string, node: TokenNode): void {
    this.graph.nodes.set(id, node);

    // Add to category index
    if (!this.graph.categories.has(node.category)) {
      this.graph.categories.set(node.category, new Set());
    }
    this.graph.categories.get(node.category)!.add(id);
  }

  private addEdge(from: string, to: string, type: TokenEdge['type']): void {
    if (!this.graph.edges.has(from)) {
      this.graph.edges.set(from, []);
    }
    this.graph.edges.get(from)!.push({ from, to, type });

    // Update referencedBy on target node
    const targetNode = this.graph.nodes.get(to);
    if (targetNode) {
      if (!targetNode.referencedBy) {
        targetNode.referencedBy = [];
      }
      targetNode.referencedBy.push(from);
    }
  }

  /**
   * Build the token graph.
   */
  build(): TokenGraph {
    return this.graph;
  }

  /**
   * Reset the builder.
   */
  reset(): this {
    this.graph = {
      nodes: new Map(),
      edges: new Map(),
      categories: new Map(),
    };
    return this;
  }
}

// ============================================================================
// TOKEN GRAPH QUERIES
// ============================================================================

export class TokenGraphQuery {
  constructor(private graph: TokenGraph) {}

  /**
   * Get a token by ID.
   */
  getToken(id: string): TokenNode | undefined {
    return this.graph.nodes.get(id);
  }

  /**
   * Get all tokens of a specific type.
   */
  getTokensByType(type: TokenType): TokenNode[] {
    return Array.from(this.graph.nodes.values()).filter(node => node.type === type);
  }

  /**
   * Get all tokens in a category.
   */
  getTokensByCategory(category: TokenNode['category']): TokenNode[] {
    const ids = this.graph.categories.get(category);
    if (!ids) return [];
    return Array.from(ids).map(id => this.graph.nodes.get(id)!);
  }

  /**
   * Get all tokens that reference a given token.
   */
  getReferencingTokens(tokenId: string): TokenNode[] {
    const node = this.graph.nodes.get(tokenId);
    if (!node?.referencedBy) return [];
    return node.referencedBy.map(id => this.graph.nodes.get(id)!).filter(Boolean);
  }

  /**
   * Get all tokens that a given token references.
   */
  getReferencedTokens(tokenId: string): TokenNode[] {
    const node = this.graph.nodes.get(tokenId);
    if (!node?.references) return [];
    return node.references.map(id => this.graph.nodes.get(id)!).filter(Boolean);
  }

  /**
   * Resolve a token to its base value (follows alias chain).
   */
  resolveToken(tokenId: string): string | number | undefined {
    const node = this.graph.nodes.get(tokenId);
    if (!node) return undefined;

    if (node.type === 'base') {
      return node.value;
    }

    // Follow alias chain
    if (node.references && node.references.length > 0) {
      return this.resolveToken(node.references[0]);
    }

    return node.value;
  }

  /**
   * Get the resolution path for a token (all tokens in the alias chain).
   */
  getResolutionPath(tokenId: string): string[] {
    const path: string[] = [];
    let currentId = tokenId;

    while (true) {
      const node = this.graph.nodes.get(currentId);
      if (!node) break;

      path.push(currentId);

      if (node.type === 'base' || !node.references || node.references.length === 0) {
        break;
      }

      currentId = node.references[0];

      // Prevent infinite loops
      if (path.includes(currentId)) {
        path.push(currentId); // Add the circular reference
        break;
      }
    }

    return path;
  }

  /**
   * Check for circular references in the token graph.
   */
  detectCircularReferences(): Array<{ path: string[] }> {
    const circularRefs: Array<{ path: string[] }> = [];

    for (const [id] of this.graph.nodes) {
      const path = this.getResolutionPath(id);
      const uniqueIds = new Set(path);

      if (path.length !== uniqueIds.size) {
        circularRefs.push({ path });
      }
    }

    return circularRefs;
  }

  /**
   * Get orphaned tokens (tokens not referenced by any other token).
   */
  getOrphanedTokens(): TokenNode[] {
    const allIds = new Set(this.graph.nodes.keys());
    const referencedIds = new Set<string>();

    for (const node of this.graph.nodes.values()) {
      if (node.references) {
        for (const ref of node.references) {
          referencedIds.add(ref);
        }
      }
    }

    const orphanedIds = Array.from(allIds).filter(id => !referencedIds.has(id));
    return orphanedIds.map(id => this.graph.nodes.get(id)!);
  }

  /**
   * Get statistics about the token graph.
   */
  getStatistics() {
    const typeCounts = {
      base: 0,
      semantic: 0,
      alias: 0,
    };

    for (const node of this.graph.nodes.values()) {
      typeCounts[node.type]++;
    }

    const categoryCounts: Record<string, number> = {};
    for (const [category, ids] of this.graph.categories.entries()) {
      categoryCounts[category] = ids.size;
    }

    return {
      totalTokens: this.graph.nodes.size,
      totalEdges: Array.from(this.graph.edges.values()).reduce((sum, edges) => sum + edges.length, 0),
      typeCounts,
      categoryCounts,
      circularReferences: this.detectCircularReferences().length,
      orphanedTokens: this.getOrphanedTokens().length,
    };
  }
}

// ============================================================================
// CREATE TOKEN GRAPH FROM EXISTING TOKENS
// ============================================================================

/**
 * Create a token graph from the existing token registry.
 * This would be called with the actual token values from colors.ts, spacing.ts, etc.
 */
export function createTokenGraph(): TokenGraph {
  const builder = new TokenGraphBuilder();

  // Base color tokens
  builder.addBaseToken('color.blue.500', '#3b82f6', 'color', 'Primary blue');
  builder.addBaseToken('color.red.500', '#ef4444', 'color', 'Primary red');
  builder.addBaseToken('color.green.500', '#22c55e', 'color', 'Primary green');
  builder.addBaseToken('color.gray.50', '#f9fafb', 'color', 'Light gray');
  builder.addBaseToken('color.gray.900', '#111827', 'color', 'Dark gray');

  // Semantic color tokens
  builder.addSemanticToken('color.primary', '#3b82f6', 'color', ['color.blue.500'], 'Primary brand color');
  builder.addSemanticToken('color.danger', '#ef4444', 'color', ['color.red.500'], 'Danger/error color');
  builder.addSemanticToken('color.success', '#22c55e', 'color', ['color.green.500'], 'Success color');
  builder.addSemanticToken('color.background', '#ffffff', 'color', ['color.gray.50'], 'Background color');
  builder.addSemanticToken('color.foreground', '#111827', 'color', ['color.gray.900'], 'Foreground color');

  // Base spacing tokens
  builder.addBaseToken('spacing.0', '0px', 'spacing', 'No spacing');
  builder.addBaseToken('spacing.1', '4px', 'spacing', 'Extra small spacing');
  builder.addBaseToken('spacing.2', '8px', 'spacing', 'Small spacing');
  builder.addBaseToken('spacing.4', '16px', 'spacing', 'Medium spacing');
  builder.addBaseToken('spacing.8', '32px', 'spacing', 'Large spacing');

  // Semantic spacing tokens
  builder.addSemanticToken('spacing.xs', '4px', 'spacing', ['spacing.1'], 'Extra small spacing');
  builder.addSemanticToken('spacing.sm', '8px', 'spacing', ['spacing.2'], 'Small spacing');
  builder.addSemanticToken('spacing.md', '16px', 'spacing', ['spacing.4'], 'Medium spacing');
  builder.addSemanticToken('spacing.lg', '32px', 'spacing', ['spacing.8'], 'Large spacing');

  // Base typography tokens
  builder.addBaseToken('font.size.12', '12px', 'typography', 'Extra small font');
  builder.addBaseToken('font.size.14', '14px', 'typography', 'Small font');
  builder.addBaseToken('font.size.16', '16px', 'typography', 'Base font');
  builder.addBaseToken('font.size.18', '18px', 'typography', 'Medium font');
  builder.addBaseToken('font.size.24', '24px', 'typography', 'Large font');

  // Semantic typography tokens
  builder.addSemanticToken('font.size.xs', '12px', 'typography', ['font.size.12'], 'Extra small text');
  builder.addSemanticToken('font.size.sm', '14px', 'typography', ['font.size.14'], 'Small text');
  builder.addSemanticToken('font.size.base', '16px', 'typography', ['font.size.16'], 'Base text');
  builder.addSemanticToken('font.size.lg', '18px', 'typography', ['font.size.18'], 'Large text');
  builder.addSemanticToken('font.size.xl', '24px', 'typography', ['font.size.24'], 'Extra large text');

  return builder.build();
}

/**
 * Get the singleton token graph query instance.
 */
export const tokenGraph = new TokenGraphQuery(createTokenGraph());
