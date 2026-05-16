/**
 * @fileoverview Unit tests for the Phase-2 symbol resolver.
 * Verifies resolution, ambiguity detection, cross-repo classification,
 * and self-reference exclusion.
 */

import { describe, it, expect } from 'vitest';
import { resolveSymbols } from '../synthesis/symbol-resolver';
import type { GraphNode, UnresolvedGraphEdge, GraphNodeKind } from '../graph/types';

// ============================================================================
// Helpers
// ============================================================================

function makeNode(overrides: Partial<GraphNode> & { id: string; label: string }): GraphNode {
  return {
    id: overrides.id,
    kind: (overrides.kind ?? 'component') as GraphNodeKind,
    label: overrides.label,
    symbolRef: overrides.symbolRef,
    sourceRef: overrides.sourceRef,
    sourceLocation: overrides.sourceLocation ?? {
      filePath: `src/${overrides.label}.tsx`,
      startLine: 0,
      startColumn: 0,
      endLine: 10,
      endColumn: 0,
    },
    extractorId: 'test-extractor',
    extractorVersion: '1.0.0',
    confidence: 0.9,
    provenance: 'exact',
    privacySecurityFlags: [],
    residualFragmentIds: [],
    metadata: {},
  };
}

const DEFAULT_LOC = { filePath: 'src/test.tsx', startLine: 0, startColumn: 0, endLine: 1, endColumn: 0 };

function makeUnresolved(
  _id: string,
  sourceId: string,
  targetRef: string,
  kindHint?: string,
): UnresolvedGraphEdge {
  return {
    sourceId,
    targetRef,
    relationship: 'renders',
    sourceLocation: DEFAULT_LOC,
    confidence: 0.85,
    metadata: {},
    ...(kindHint ? { targetKindHint: kindHint as GraphNodeKind } : {}),
  };
}

// ============================================================================
// Tests
// ============================================================================

describe('resolveSymbols — happy path', () => {
  it('resolves an unresolved edge by component label', () => {
    const button = makeNode({ id: 'node-button', label: 'Button' });
    const form = makeNode({ id: 'node-form', label: 'Form' });
    const edge = makeUnresolved('e1', 'node-form', 'Button', 'component');

    const { resolvedEdges, resolutionRecords, remainingUnresolved } = resolveSymbols(
      [edge],
      [button, form],
    );

    expect(resolvedEdges).toHaveLength(1);
    expect(resolvedEdges[0]!.targetId).toBe('node-button');
    expect(resolvedEdges[0]!.sourceId).toBe('node-form');
    expect(resolutionRecords[0]!.status).toBe('resolved');
    expect(remainingUnresolved).toHaveLength(0);
  });

  it('resolves by symbolRef when present', () => {
    const icon = makeNode({
      id: 'node-icon',
      label: 'Icon',
      symbolRef: 'urn:acme:Icon:Button#default',
    });
    const edge = makeUnresolved('e2', 'node-form', 'urn:acme:Icon:Button#default');

    const { resolvedEdges } = resolveSymbols([edge], [icon]);
    expect(resolvedEdges[0]!.targetId).toBe('node-icon');
  });

  it('resolves by file path', () => {
    const util = makeNode({ id: 'node-util', label: 'formatDate' });
    const edge = makeUnresolved('e3', 'node-form', 'src/formatDate.tsx');

    const { resolvedEdges } = resolveSymbols([edge], [util]);
    expect(resolvedEdges[0]!.targetId).toBe('node-util');
  });
});

describe('resolveSymbols — ambiguity and unresolvable', () => {
  it('marks edge as ambiguous when multiple nodes match the label', () => {
    const btn1 = makeNode({ id: 'node-btn1', label: 'Button' });
    const btn2 = makeNode({ id: 'node-btn2', label: 'Button' });
    const edge = makeUnresolved('e4', 'node-form', 'Button', 'component');

    const { resolvedEdges, resolutionRecords, remainingUnresolved } = resolveSymbols(
      [edge],
      [btn1, btn2],
    );

    expect(resolvedEdges).toHaveLength(0);
    expect(remainingUnresolved).toHaveLength(1);
    expect(resolutionRecords[0]!.status).toBe('ambiguous');
    expect(resolutionRecords[0]!.candidateIds).toHaveLength(2);
  });

  it('marks edge as unresolvable when no match', () => {
    const node = makeNode({ id: 'node-a', label: 'ComponentA' });
    const edge = makeUnresolved('e5', 'node-a', './NonExistent');

    const { resolutionRecords } = resolveSymbols([edge], [node]);
    expect(resolutionRecords[0]!.status).toBe('unresolvable');
  });

  it('marks node_modules imports as cross-repo', () => {
    const node = makeNode({ id: 'node-a', label: 'ComponentA' });
    const edge = makeUnresolved('e6', 'node-a', 'react');

    const { resolutionRecords } = resolveSymbols([edge], [node]);
    expect(resolutionRecords[0]!.status).toBe('cross-repo');
  });
});

describe('resolveSymbols — self-reference exclusion', () => {
  it('does not resolve a self-referencing edge', () => {
    const node = makeNode({ id: 'node-self', label: 'Self' });
    const edge = makeUnresolved('e7', 'node-self', 'Self');

    const { resolvedEdges, resolutionRecords } = resolveSymbols([edge], [node]);
    expect(resolvedEdges).toHaveLength(0);
    expect(resolutionRecords[0]!.status).not.toBe('resolved');
  });
});

describe('resolveSymbols — edge ID stability', () => {
  it('generates deterministic edge IDs for resolved edges', () => {
    const button = makeNode({ id: 'node-button', label: 'Button' });
    const form = makeNode({ id: 'node-form', label: 'Form' });
    const edge = makeUnresolved('e8', 'node-form', 'Button');

    const { resolvedEdges: edges1 } = resolveSymbols([edge], [button, form]);
    const { resolvedEdges: edges2 } = resolveSymbols([edge], [button, form]);

    expect(edges1[0]!.id).toBe(edges2[0]!.id);
  });

  it('produces different edge IDs for different source/target/kind combinations', () => {
    const a = makeNode({ id: 'node-a', label: 'A' });
    const b = makeNode({ id: 'node-b', label: 'B' });
    const c = makeNode({ id: 'node-c', label: 'C' });

    const edge1 = makeUnresolved('e9a', 'node-a', 'B');
    const edge2 = makeUnresolved('e9b', 'node-a', 'C');

    const { resolvedEdges: e1 } = resolveSymbols([edge1], [a, b, c]);
    const { resolvedEdges: e2 } = resolveSymbols([edge2], [a, b, c]);

    expect(e1[0]!.id).not.toBe(e2[0]!.id);
  });
});

describe('resolveSymbols — resolution metadata', () => {
  it('sets reviewRequired=true for ambiguous edges', () => {
    const btn1 = makeNode({ id: 'n1', label: 'Button' });
    const btn2 = makeNode({ id: 'n2', label: 'Button' });
    const edge = makeUnresolved('e10', 'n-other', 'Button');
    const other = makeNode({ id: 'n-other', label: 'Other' });

    const { resolutionRecords } = resolveSymbols([edge], [btn1, btn2, other]);
    expect(resolutionRecords[0]!.reviewRequired).toBe(true);
  });

  it('attaches resolvedFromRef metadata on resolved edges', () => {
    const node = makeNode({ id: 'target', label: 'Target' });
    const source = makeNode({ id: 'source', label: 'Source' });
    const edge = makeUnresolved('e11', 'source', 'Target');

    const { resolvedEdges } = resolveSymbols([edge], [node, source]);
    expect(resolvedEdges[0]!.metadata['resolvedFromRef']).toBe('Target');
  });
});

describe('resolveSymbols — alias and workspace package resolution', () => {
  it('resolves tsconfig-style aliases from resolver options', () => {
    const source = makeNode({
      id: 'node-source',
      label: 'Source',
      sourceLocation: {
        filePath: 'src/pages/Home.tsx',
        startLine: 0,
        startColumn: 0,
        endLine: 5,
        endColumn: 0,
      },
    });
    const target = makeNode({
      id: 'node-card',
      label: 'Card',
      sourceLocation: {
        filePath: 'src/components/Card.tsx',
        startLine: 0,
        startColumn: 0,
        endLine: 10,
        endColumn: 0,
      },
    });
    const edge = makeUnresolved('e12', 'node-source', '@app/components/Card');

    const { resolvedEdges, resolutionRecords } = resolveSymbols([edge], [source, target], {
      pathAliases: {
        '@app/*': 'src/*',
      },
    });

    expect(resolvedEdges).toHaveLength(1);
    expect(resolvedEdges[0]!.targetId).toBe('node-card');
    expect(resolutionRecords[0]!.status).toBe('resolved');
  });

  it('resolves workspace package imports with configured package prefixes', () => {
    const source = makeNode({
      id: 'node-source',
      label: 'Shell',
      sourceLocation: {
        filePath: 'apps/web/src/App.tsx',
        startLine: 0,
        startColumn: 0,
        endLine: 5,
        endColumn: 0,
      },
    });
    const target = makeNode({
      id: 'node-util',
      label: 'formatPrice',
      sourceLocation: {
        filePath: 'packages/shared/src/utils/formatPrice.ts',
        startLine: 0,
        startColumn: 0,
        endLine: 8,
        endColumn: 0,
      },
    });
    const edge = makeUnresolved('e13', 'node-source', '@workspace/shared/utils/formatPrice');

    const { resolvedEdges, resolutionRecords } = resolveSymbols([edge], [source, target], {
      workspacePackagePrefixes: ['@workspace'],
    });

    expect(resolvedEdges).toHaveLength(1);
    expect(resolvedEdges[0]!.targetId).toBe('node-util');
    expect(resolutionRecords[0]!.status).toBe('resolved');
  });
});
