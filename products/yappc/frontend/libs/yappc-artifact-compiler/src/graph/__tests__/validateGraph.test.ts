/**
 * @fileoverview Tests for graph validation.
 *
 * Phase 2 test: Validates that the graph validator correctly detects:
 * - Duplicate node IDs
 * - Edge source/target not found
 * - Invalid confidence scores
 * - Missing required fields
 * - Index consistency
 */

import { describe, it, expect } from 'vitest';
import { validateGraph } from '../validateGraph';
import type { ArtifactGraph } from '../types';

describe('validateGraph', () => {
  const createNode = (id: string, kind: string, label: string, confidence: number) => ({
    id,
    kind: kind as any,
    label,
    confidence,
    sourceLocation: { filePath: 'src/test.tsx', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
    metadata: {},
    extractorId: 'test-extractor',
    extractorVersion: '1.0.0',
    provenance: 'exact' as const,
    privacySecurityFlags: [],
    residualFragmentIds: [],
  });

  it('should validate a valid graph', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [
        createNode('node-1', 'component', 'Button', 0.9),
        createNode('node-2', 'page', 'HomePage', 0.95),
      ],
      edges: [
        {
          id: 'edge-1',
          sourceId: 'node-1',
          targetId: 'node-2',
          kind: 'imports',
          confidence: 0.85,
          bidirectional: false,
          metadata: {},
        },
      ],
      nodeIndex: {
        component: ['node-1'],
        page: ['node-2'],
      },
      edgeIndex: {
        imports: ['edge-1'],
      },
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.warnings).toHaveLength(0);
  });

  it('should detect duplicate node IDs', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [
        createNode('node-1', 'component', 'Button', 0.9),
        createNode('node-1', 'page', 'HomePage', 0.95), // Duplicate ID
      ],
      edges: [],
      nodeIndex: {},
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.code).toBe('DUPLICATE_NODE_ID');
  });

  it('should detect edge source not found', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [createNode('node-1', 'component', 'Button', 0.9)],
      edges: [
        {
          id: 'edge-1',
          sourceId: 'node-missing', // Non-existent source
          targetId: 'node-1',
          kind: 'imports',
          confidence: 0.85,
          bidirectional: false,
          metadata: {},
        },
      ],
      nodeIndex: {},
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.code).toBe('EDGE_SOURCE_NOT_FOUND');
  });

  it('should detect edge target not found', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [createNode('node-1', 'component', 'Button', 0.9)],
      edges: [
        {
          id: 'edge-1',
          sourceId: 'node-1',
          targetId: 'node-missing', // Non-existent target
          kind: 'imports',
          confidence: 0.85,
          bidirectional: false,
          metadata: {},
        },
      ],
      nodeIndex: {},
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.code).toBe('EDGE_TARGET_NOT_FOUND');
  });

  it('should detect invalid confidence scores', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [createNode('node-1', 'component', 'Button', 1.5)], // Invalid: > 1
      edges: [],
      nodeIndex: {},
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.code).toBe('INVALID_CONFIDENCE');
  });

  it('should detect missing required fields', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [createNode('', 'component', 'Button', 0.9)], // Missing required ID
      edges: [],
      nodeIndex: {},
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.code).toBe('MISSING_REQUIRED_FIELD');
  });

  it('should detect index inconsistencies', () => {
    const graph: ArtifactGraph = {
      id: 'graph-1',
      repositoryRoot: '/repo',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [createNode('node-1', 'component', 'Button', 0.9)],
      edges: [],
      nodeIndex: {
        component: ['node-missing'], // Index references non-existent node
      },
      edgeIndex: {},
      unresolvedEdges: [],
      edgeResolutionRecords: [],
    };

    const result = validateGraph(graph);

    expect(result.valid).toBe(true); // Index issues are warnings, not errors
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]?.code).toBe('INDEX_NODE_NOT_FOUND');
  });
});
