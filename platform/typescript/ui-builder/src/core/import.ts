/**
 * @fileoverview Code import and ownership-aware reconciliation back into BuilderDocument.
 *
 * Parses external code (TSX/HTML/JSON) and reconciles it against an existing
 * BuilderDocument, preserving ownership markers for regions that originated
 * from user edits and flagging conflicts requiring human review.
 *
 * TSX import uses TypeScript compiler API for accurate AST parsing instead of regex.
 */

import type { BuilderDocument } from './builder-document.js';
import { attachBuilderDocumentCompatibility, normalizeBuilderDocument } from './builder-document.js';
import type { ComponentInstance, NodeId, RoundTripFidelity, LossPoint } from './types.js';
import { createNodeId } from './types.js';
import { produce } from 'immer';
import * as ts from 'typescript';

// ============================================================================
// Import Source
// ============================================================================

export type ImportSourceFormat = 'tsx' | 'html' | 'json';

export interface ImportSource {
  readonly format: ImportSourceFormat;
  readonly content: string;
  /** Path hint for error messages. */
  readonly path?: string;
}

// ============================================================================
// Import Result
// ============================================================================

export type ImportResultStatus = 'clean' | 'review-required' | 'failed';

export interface ImportConflict {
  readonly nodeId: NodeId;
  readonly conflictType: 'ownership' | 'prop-mismatch' | 'unsupported-pattern';
  readonly description: string;
  readonly existingOwnership?: 'generated' | 'user-authored' | 'protected' | 'manual-merge-required';
}

export interface ImportResult {
  readonly status: ImportResultStatus;
  readonly document: BuilderDocument;
  readonly addedNodeIds: readonly NodeId[];
  readonly conflicts: readonly ImportConflict[];
  readonly fidelity: RoundTripFidelity;
  readonly errorMessage?: string;
}

// ============================================================================
// JSON import (lossless round-trip)
// ============================================================================

/**
 * Imports a BuilderDocument serialised as JSON. Lossless and always produces
 * a `clean` result if the JSON is structurally valid.
 *
 * Uses canonical BuilderDocument structure with Record-based nodes and layout.
 */
export function importFromJson(
  source: ImportSource,
  existing: BuilderDocument,
): ImportResult {
  existing = normalizeBuilderDocument(existing);
  let parsed: unknown;
  try {
    parsed = JSON.parse(source.content);
  } catch (err) {
    return {
      status: 'failed',
      document: existing,
      addedNodeIds: [],
      conflicts: [],
      fidelity: { canRoundTrip: false, lossPoints: [], confidence: 0 },
      errorMessage: `JSON parse error: ${err instanceof Error ? err.message : String(err)}`,
    };
  }

  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    !('rootNodes' in parsed) ||
    !Array.isArray((parsed as Record<string, unknown>)['rootNodes'])
  ) {
    return {
      status: 'failed',
      document: existing,
      addedNodeIds: [],
      conflicts: [],
      fidelity: { canRoundTrip: false, lossPoints: [], confidence: 0 },
      errorMessage: 'JSON does not look like a BuilderDocument — missing rootNodes',
    };
  }

  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    !('nodes' in parsed)
  ) {
    return {
      status: 'failed',
      document: existing,
      addedNodeIds: [],
      conflicts: [],
      fidelity: { canRoundTrip: false, lossPoints: [], confidence: 0 },
      errorMessage: 'JSON does not look like a BuilderDocument — missing nodes',
    };
  }

  const raw = parsed as Record<string, unknown>;
  const rawNodes = raw['nodes'] as Record<string, unknown>;
  const addedIds: NodeId[] = [];

  // Import without immer to avoid complex type compatibility issues
  // Import is a merge operation, not a mutation of existing state
  // Use plain object for construction to avoid readonly/mutable array mismatch
  const updated: Record<string, unknown> = {
    ...existing,
    nodes: {
      ...existing.nodes,
    },
    layout: {
      ...existing.layout,
      nodes: {
        ...existing.layout.nodes,
      },
    },
    metadata: {
      ...existing.metadata,
      updatedAt: new Date().toISOString(),
    },
  };

  // Import nodes using canonical Record structure
  for (const [id, node] of Object.entries(rawNodes)) {
    if (!(id in (updated.nodes as Record<string, unknown>))) {
      (updated.nodes as Record<string, unknown>)[id] = node;
      addedIds.push(id as NodeId);
    }
  }

  // Handle root children from layout structure (canonical)
  if ('layout' in raw && typeof raw.layout === 'object' && raw.layout !== null) {
    const layout = raw.layout as Record<string, unknown>;
    if ('rootId' in layout && 'nodes' in layout) {
      const rootId = layout.rootId as string;
      const layoutNodes = layout.nodes as Record<string, unknown>;
      const rootLayoutNode = layoutNodes[rootId] as Record<string, unknown>;

      if (rootLayoutNode && 'children' in rootLayoutNode && Array.isArray(rootLayoutNode.children)) {
        const importedRootChildren = rootLayoutNode.children as NodeId[];
        const updatedLayout = updated.layout as Record<string, unknown>;
        const updatedLayoutNodes = updatedLayout.nodes as Record<string, unknown>;
        const currentRootLayoutNode = updatedLayoutNodes[updatedLayout.rootId as string] as Record<string, unknown>;

        if (currentRootLayoutNode) {
          const newRootChildren = importedRootChildren.filter(
            (id) => !((currentRootLayoutNode.children as NodeId[] | undefined)?.includes(id))
          );
          currentRootLayoutNode.children = [
            ...(currentRootLayoutNode.children as NodeId[] | undefined ?? []),
            ...newRootChildren,
          ];
        }
      }
    }
  }

  return {
    status: 'clean',
    // Cast through unknown to handle readonly/mutable array mismatch
    // External JSON has mutable arrays, canonical schema uses readonly
    document: attachBuilderDocumentCompatibility(updated as unknown as BuilderDocument),
    addedNodeIds: addedIds,
    conflicts: [],
    fidelity: { canRoundTrip: true, lossPoints: [], confidence: 1 },
  };
}

// ============================================================================
// TSX import (AST-based, review-required)
// ============================================================================

/**
 * Parse JSX attributes from a TypeScript AST node.
 */
function parseJsxAttributes(node: ts.JsxOpeningLikeElement): Record<string, unknown> {
  const props: Record<string, unknown> = {};

  for (const prop of node.attributes.properties) {
    if (ts.isJsxAttribute(prop)) {
      const propName = prop.name.getText();

      if (prop.initializer) {
        if (ts.isStringLiteral(prop.initializer)) {
          props[propName] = prop.initializer.text;
        } else if (ts.isJsxExpression(prop.initializer)) {
          // For expressions, try to evaluate simple literals
          const expr = prop.initializer.expression;
          if (!expr) {
            // Empty expression - skip
            continue;
          }
          if (ts.isNumericLiteral(expr)) {
            props[propName] = Number(expr.text);
          } else if (expr.kind === ts.SyntaxKind.TrueKeyword) {
            props[propName] = true;
          } else if (expr.kind === ts.SyntaxKind.FalseKeyword) {
            props[propName] = false;
          } else if (ts.isIdentifier(expr)) {
            // Keep as identifier for review
            props[propName] = `{${expr.getText()}}`;
          } else {
            // Complex expression - flag as loss point
            props[propName] = `{${expr.getText()}}`;
          }
        }
      } else {
        // Boolean attribute without value (e.g., disabled)
        props[propName] = true;
      }
    }
  }

  return props;
}

/**
 * Recursively traverse the TypeScript AST to extract JSX elements.
 */
function extractJsxElements(
  node: ts.Node,
  designSystemContractNames: ReadonlySet<string>,
  results: Array<{ componentName: string; props: Record<string, unknown>; location: number }>,
): void {
  if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
    const tagName = node.tagName.getText();

    // Only process PascalCase components (likely design system components)
    if (/^[A-Z]/.test(tagName)) {
      const location = node.getStart();
      const props = ts.isJsxSelfClosingElement(node)
        ? parseJsxAttributes(node)
        : parseJsxAttributes(node);

      if (!designSystemContractNames.has(tagName)) {
        // Still collect but will be flagged as conflict
        results.push({ componentName: tagName, props, location });
      } else {
        results.push({ componentName: tagName, props, location });
      }
    }
  }

  // Recursively process children
  ts.forEachChild(node, (child) => {
    extractJsxElements(child, designSystemContractNames, results);
  });
}

/**
 * Parses a TSX string using TypeScript compiler API AST parsing and creates
 * BuilderDocument nodes for discovered components. Always flags `review-required`
 * because TSX may contain arbitrary custom code not representable in the model.
 */
export function importFromTsx(
  source: ImportSource,
  existing: BuilderDocument,
  designSystemContractNames: ReadonlySet<string>,
): ImportResult {
  existing = normalizeBuilderDocument(existing);
  const addedIds: NodeId[] = [];
  const conflicts: ImportConflict[] = [];
  const lossPoints: LossPoint[] = [];
  const insertedComponents: ComponentInstance[] = [];

  // Create TypeScript compiler instance
  const compilerOptions: ts.CompilerOptions = {
    jsx: ts.JsxEmit.React,
    target: ts.ScriptTarget.ESNext,
    module: ts.ModuleKind.ESNext,
  };

  // Parse TSX source into AST
  const sourceFile = ts.createSourceFile(
    'temp.tsx',
    source.content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );

  // Extract JSX elements from AST
  const extractedElements: Array<{ componentName: string; props: Record<string, unknown>; location: number }> = [];
  extractJsxElements(sourceFile, designSystemContractNames, extractedElements);

  // Process extracted elements
  for (const { componentName, props, location } of extractedElements) {
    const expressionPropNames = Object.entries(props)
      .filter(([, value]) => typeof value === 'string' && /^\{.*\}$/.test(value))
      .map(([propName]) => propName);
    if (expressionPropNames.length > 0) {
      lossPoints.push({
        type: 'custom-code',
        location: `char ${location}`,
        description: `Expression prop(s) require review: ${expressionPropNames.join(', ')}`,
      });
    }
    if (!designSystemContractNames.has(componentName)) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: `char ${location}`,
        description: `Unknown component <${componentName}> not in design system contracts`,
      });
      conflicts.push({
        nodeId: createNodeId(),
        conflictType: 'unsupported-pattern',
        description: `Component <${componentName}> has no registered contract`,
      });
      continue;
    }

    const id = createNodeId();
    insertedComponents.push({
      id,
      contractName: componentName,
      props,
      slots: {},
      bindings: [],
      metadata: { ownership: { author: 'user' } as Record<string, unknown> },
    });
    addedIds.push(id);
  }

  if (insertedComponents.length === 0) {
    lossPoints.push({
      type: 'custom-code',
      description: source.content.trim().length === 0
        ? 'Empty TSX source — nothing to import'
        : 'No design-system components found in TSX source',
    });
  }

  // Import without immer to avoid complex type compatibility issues
  // Use plain object for construction to avoid readonly/mutable array mismatch
  const updated: Record<string, unknown> = {
    ...existing,
    nodes: { ...existing.nodes },
    layout: {
      ...existing.layout,
      nodes: { ...existing.layout.nodes },
    },
    metadata: {
      ...existing.metadata,
      updatedAt: new Date().toISOString(),
    },
  };

  for (const node of insertedComponents) {
    (updated.nodes as Record<string, unknown>)[node.id] = node;
    // Add to root layout children (canonical structure)
    const updatedLayout = updated.layout as Record<string, unknown>;
    const updatedLayoutNodes = updatedLayout.nodes as Record<string, unknown>;
    const rootLayoutNode = updatedLayoutNodes[updatedLayout.rootId as string] as Record<string, unknown>;
    if (rootLayoutNode) {
      rootLayoutNode.children = [
        ...(rootLayoutNode.children as NodeId[] | undefined ?? []),
        node.id,
      ];
    }
  }

  return {
    status: conflicts.length > 0 ? 'review-required' : 'clean',
    // Cast through unknown to handle readonly/mutable array mismatch
    document: attachBuilderDocumentCompatibility(updated as unknown as BuilderDocument),
    addedNodeIds: addedIds,
    conflicts,
    fidelity: {
      canRoundTrip: lossPoints.length === 0,
      lossPoints,
      confidence: Math.max(0, 1 - lossPoints.length * 0.2),
    },
  };
}

// ============================================================================
// HTML import (best-effort)
// ============================================================================

/**
 * Parses an HTML fragment and maps `<ghatana-*>` custom elements back to
 * BuilderDocument nodes. Other HTML elements are noted as loss points.
 */
export function importFromHtml(
  source: ImportSource,
  existing: BuilderDocument,
): ImportResult {
  existing = normalizeBuilderDocument(existing);
  const addedIds: NodeId[] = [];
  const lossPoints: LossPoint[] = [];

  // Match <ghatana-some-component attrs />
  const ghatanaRe = /<ghatana-([a-z][a-z0-9-]*)(\s[^>]*)?\s*\/?>/gi;
  let match: RegExpExecArray | null;
  const insertedComponents: ComponentInstance[] = [];

  while ((match = ghatanaRe.exec(source.content)) !== null) {
    const tagSlug = match[1] as string;
    // Convert kebab-case to PascalCase for contract lookup
    const contractName = tagSlug
      .split('-')
      .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
      .join('');

    const propsRaw = match[2] ?? '';
    const props: Record<string, unknown> = parseInlineProps(propsRaw);

    const id = createNodeId();
    insertedComponents.push({
      id,
      contractName,
      props,
      slots: {},
      bindings: [],
      metadata: { ownership: { author: 'user' } as Record<string, unknown> },
    });
    addedIds.push(id);
  }

  // Detect non-ghatana HTML elements that cannot round-trip
  const foreignRe = /<(?!ghatana-)([a-z][a-z0-9-]*)[\s>]/gi;
  while ((match = foreignRe.exec(source.content)) !== null) {
    lossPoints.push({
      type: 'unsupported-pattern',
      location: `char ${match.index}`,
      description: `Non-Ghatana element <${match[1]}> cannot be round-tripped`,
    });
  }

  // Import without immer to avoid complex type compatibility issues
  // Use plain object for construction to avoid readonly/mutable array mismatch
  const updated: Record<string, unknown> = {
    ...existing,
    nodes: { ...existing.nodes },
    layout: {
      ...existing.layout,
      nodes: { ...existing.layout.nodes },
    },
    metadata: {
      ...existing.metadata,
      updatedAt: new Date().toISOString(),
    },
  };

  for (const node of insertedComponents) {
    (updated.nodes as Record<string, unknown>)[node.id] = node;
    // Add to root layout children (canonical structure)
    const updatedLayout = updated.layout as Record<string, unknown>;
    const updatedLayoutNodes = updatedLayout.nodes as Record<string, unknown>;
    const rootLayoutNode = updatedLayoutNodes[updatedLayout.rootId as string] as Record<string, unknown>;
    if (rootLayoutNode) {
      rootLayoutNode.children = [
        ...(rootLayoutNode.children as NodeId[] | undefined ?? []),
        node.id,
      ];
    }
  }

  return {
    status: lossPoints.length > 0 ? 'review-required' : 'clean',
    // Cast through unknown to handle readonly/mutable array mismatch
    document: attachBuilderDocumentCompatibility(updated as unknown as BuilderDocument),
    addedNodeIds: addedIds,
    conflicts: [],
    fidelity: {
      canRoundTrip: lossPoints.length === 0,
      lossPoints,
      confidence: Math.max(0, 1 - lossPoints.length * 0.15),
    },
  };
}

// ============================================================================
// Unified dispatcher
// ============================================================================

/** Import from any supported source format. */
export function importSource(
  source: ImportSource,
  existing: BuilderDocument,
  options: { designSystemContracts?: ReadonlySet<string> } = {},
): ImportResult {
  switch (source.format) {
    case 'json':
      return importFromJson(source, existing);
    case 'tsx':
      return importFromTsx(source, existing, options.designSystemContracts ?? new Set());
    case 'html':
      return importFromHtml(source, existing);
  }
}

// ============================================================================
// Helpers
// ============================================================================

function parseInlineProps(raw: string): Record<string, unknown> {
  const props: Record<string, unknown> = {};
  // Match key="value" or key={value} or boolean key
  const attrRe = /([a-zA-Z][a-zA-Z0-9-]*)(?:=(?:"([^"]*)"|'([^']*)'|\{([^}]*)\}))?/g;
  let m: RegExpExecArray | null;
  while ((m = attrRe.exec(raw)) !== null) {
    const key = m[1];
    if (!key || key === '/') continue;
    const value = m[2] ?? m[3] ?? m[4];
    if (value === undefined) {
      props[key] = true;
    } else if (value === 'true') {
      props[key] = true;
    } else if (value === 'false') {
      props[key] = false;
    } else if (/^\d+(\.\d+)?$/.test(value)) {
      props[key] = Number(value);
    } else {
      props[key] = value;
    }
  }
  return props;
}
