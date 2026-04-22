/**
 * @fileoverview Extractor type definitions and interfaces.
 *
 * Extractors convert source files into graph nodes, model elements, and residual islands.
 * All extractors operate on local source files only — no external service calls.
 */

import type { ArtifactRecord } from '../inventory/types';
import type { GraphNode, GraphEdge } from '../graph/types';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Extractor Identity
// ============================================================================

export interface ExtractorIdentity {
  readonly id: string;
  readonly version: string;
  readonly supportedKinds: readonly string[];
  readonly supportedLanguages: readonly string[];
  readonly supportedFrameworks: readonly string[];
}

// ============================================================================
// Extraction Result
// ============================================================================

export interface ExtractionResult {
  readonly extractorId: string;
  readonly extractorVersion: string;
  readonly artifact: ArtifactRecord;
  readonly nodes: readonly GraphNode[];
  readonly edges: readonly GraphEdge[];
  readonly modelElements: readonly SemanticModelElement[];
  readonly residualIslands: readonly ResidualIsland[];
  readonly errors: readonly ExtractionError[];
  readonly warnings: readonly ExtractionWarning[];
  readonly durationMs: number;
}

// ============================================================================
// Extraction Error / Warning
// ============================================================================

export interface ExtractionError {
  readonly message: string;
  readonly location?: { readonly line: number; readonly column: number };
  readonly recoverable: boolean;
}

export interface ExtractionWarning {
  readonly message: string;
  readonly location?: { readonly line: number; readonly column: number };
  readonly category: 'confidence-low' | 'unsupported-feature' | 'pattern-ambiguous' | 'partial-extraction';
}

// ============================================================================
// Extractor Interface
// ============================================================================

export interface ArtifactExtractor {
  readonly identity: ExtractorIdentity;
  canExtract(record: ArtifactRecord): boolean;
  extract(record: ArtifactRecord, context: ExtractionContext): Promise<ExtractionResult>;
}

// ============================================================================
// Extraction Context
// ============================================================================

export interface ExtractionContext {
  readonly repositoryRoot: string;
  readonly allArtifacts: ReadonlyMap<string, ArtifactRecord>;
  readonly readFile: (relativePath: string) => Promise<string>;
  readonly existingGraphNodes: ReadonlyMap<string, GraphNode>;
  readonly existingModelElements: ReadonlyMap<string, SemanticModelElement>;
}
