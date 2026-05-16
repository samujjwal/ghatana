import { describe, expect, it } from 'vitest';
import { confidenceScorer } from '../confidence';
import type { GraphNode, ArtifactGraph } from '../../graph/types';
import type { SemanticModelElement } from '../../model/types';

describe('ConfidenceScorer', () => {
  it('calculates overall confidence from factors', () => {
    const factors = {
      parseSuccess: true,
      fieldCompleteness: 1.0,
      referenceResolution: 1.0,
      extractorConfidence: 0.9,
      patternConformance: 1.0,
    };

    const score = confidenceScorer.calculateOverallScore(factors);
    expect(score).toBeGreaterThan(0.8);
    expect(score).toBeLessThanOrEqual(1.0);
  });

  it('returns low confidence when parse fails', () => {
    const factors = {
      parseSuccess: false,
      fieldCompleteness: 1.0,
      referenceResolution: 1.0,
      extractorConfidence: 0.9,
      patternConformance: 1.0,
    };

    const score = confidenceScorer.calculateOverallScore(factors);
    expect(score).toBe(0.3);
  });

  it('extracts factors from a graph node', () => {
    const node: GraphNode = {
      id: 'test-node',
      kind: 'component',
      label: 'TestComponent',
      confidence: 0.85,
      extractorId: 'test-extractor',
      extractorVersion: '1.0.0',
      provenance: 'exact',
      sourceLocation: {
        filePath: 'src/TestComponent.tsx',
        startLine: 1,
        startColumn: 1,
        endLine: 10,
        endColumn: 1,
      },
      metadata: {
        props: [{ name: 'title', type: 'string', required: true }],
      },
      privacySecurityFlags: [],
      residualFragmentIds: [],
    } as GraphNode;

    const graph: ArtifactGraph = {
      id: 'test-graph',
      repositoryRoot: '/test',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [node],
      edges: [],
      unresolvedEdges: [],
      snapshotRef: undefined,
    } as ArtifactGraph;

    const factors = confidenceScorer.extractFactors(node, graph);
    expect(factors.parseSuccess).toBe(true);
    expect(factors.fieldCompleteness).toBeGreaterThan(0);
    expect(factors.extractorConfidence).toBe(0.85);
  });

  it('determines if element requires review based on confidence threshold', () => {
    const lowConfidenceNode: GraphNode = {
      id: 'test-node',
      kind: 'component',
      label: 'TestComponent',
      confidence: 0.5,
      extractorId: 'test-extractor',
      extractorVersion: '1.0.0',
      provenance: 'inferred',
      sourceLocation: {
        filePath: 'src/TestComponent.tsx',
        startLine: 1,
        startColumn: 1,
        endLine: 10,
        endColumn: 1,
      },
      metadata: {},
      privacySecurityFlags: [],
      residualFragmentIds: [],
    } as GraphNode;

    const highConfidenceNode: GraphNode = {
      id: 'test-node-2',
      kind: 'component',
      label: 'TestComponent2',
      confidence: 0.9,
      extractorId: 'test-extractor',
      extractorVersion: '1.0.0',
      provenance: 'exact',
      sourceLocation: {
        filePath: 'src/TestComponent2.tsx',
        startLine: 1,
        startColumn: 1,
        endLine: 10,
        endColumn: 1,
      },
      metadata: {},
      privacySecurityFlags: [],
      residualFragmentIds: [],
    } as GraphNode;

    const graph: ArtifactGraph = {
      id: 'test-graph',
      repositoryRoot: '/test',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      nodes: [lowConfidenceNode, highConfidenceNode],
      edges: [],
      unresolvedEdges: [],
      snapshotRef: undefined,
      edgeResolutionRecords: [],
      nodeIndex: new Map(),
      edgeIndex: new Map(),
    } as unknown as ArtifactGraph;

    const lowElement = {
      id: lowConfidenceNode.id,
      name: lowConfidenceNode.label,
      confidence: lowConfidenceNode.confidence,
      provenance: {
        extractorId: lowConfidenceNode.extractorId,
        extractorVersion: lowConfidenceNode.extractorVersion,
        sourcePaths: [lowConfidenceNode.sourceLocation.filePath],
        kind: lowConfidenceNode.provenance,
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    const highElement = {
      id: highConfidenceNode.id,
      name: highConfidenceNode.label,
      confidence: highConfidenceNode.confidence,
      provenance: {
        extractorId: highConfidenceNode.extractorId,
        extractorVersion: highConfidenceNode.extractorVersion,
        sourcePaths: [highConfidenceNode.sourceLocation.filePath],
        kind: highConfidenceNode.provenance,
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    expect(confidenceScorer.requiresReview(lowElement, graph)).toBe(true);
    expect(confidenceScorer.requiresReview(highElement, graph)).toBe(false);
  });
});
