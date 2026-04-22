/**
 * @fileoverview Synthesis type definitions.
 *
 * Synthesis converts ArtifactGraph nodes into SemanticProductModel elements.
 */

import type { ArtifactGraph, GraphNode } from '../graph/types';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Synthesis Result
// ============================================================================

export interface SynthesisResult {
  readonly elements: readonly SemanticModelElement[];
  readonly residualIslands: readonly ResidualIsland[];
  readonly confidence: number;
  readonly sourceGraphVersion: number;
}

// ============================================================================
// Synthesizer Interface
// ============================================================================

export interface GraphSynthesizer {
  readonly id: string;
  readonly version: string;
  readonly targetModelKinds: readonly string[];
  canSynthesize(node: GraphNode, graph: ArtifactGraph): boolean;
  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult;
}

// ============================================================================
// Multi-source Synthesis
// ============================================================================

export interface MultiSourceSynthesisRequest {
  readonly primaryNodeId: string;
  readonly supportingNodeIds: readonly string[];
  readonly graph: ArtifactGraph;
}

export interface MultiSourceSynthesizer {
  readonly id: string;
  synthesize(request: MultiSourceSynthesisRequest): SynthesisResult;
}
