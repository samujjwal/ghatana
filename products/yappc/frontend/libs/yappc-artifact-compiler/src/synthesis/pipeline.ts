/**
 * @fileoverview SynthesisPipeline — full scan→extract→resolve→assemble orchestrator.
 *
 * Drives the six-step compilation process described in yappc-todos.md:
 *   1. Source acquisition  → RepositorySnapshot (via SourceProvider)
 *   2. Inventory scan      → ArtifactInventory  (via scanRepository)
 *   3. Extraction          → ExtractionResult[] (via registered ArtifactExtractors)
 *   4. Symbol resolution   → Resolved GraphEdge[] (via resolveSymbols)
 *   5. Graph assembly      → ArtifactGraph
 *   6. Provenance indexing → ProvenanceIndex
 *
 * The pipeline is stateless: calling run() twice with the same inputs produces
 * identical deterministic output (given the same snapshotRef).
 */

import { randomUUID } from 'crypto';
import type { ArtifactRecord } from '../inventory/types';
import { scanRepository, type ScannerConfig } from '../inventory/scanner';
import type { ExtractionContext, ExtractionResult, ArtifactExtractor } from '../extractors/types';
import type {
  ArtifactGraph,
  GraphNode,
  GraphEdge,
  UnresolvedGraphEdge,
  EdgeResolutionRecord,
  SnapshotRef,
} from '../graph/types';
import { buildDeterministicNodeId } from '../graph/types';
import { resolveSymbols } from './symbol-resolver';
import type { RepositorySnapshot } from '../source-providers/types';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Pipeline configuration
// ============================================================================

export interface SynthesisPipelineConfig {
  /** Override scan config (e.g. to restrict to specific paths). */
  readonly scannerConfig?: Partial<ScannerConfig>;
  /** Registered extractors to run against eligible artifacts. */
  readonly extractors: readonly ArtifactExtractor[];
  /**
   * Confidence threshold below which extracted elements are sent to residuals
   * instead of the main model. Defaults to 0.5.
   */
  readonly residualConfidenceThreshold?: number;
  /**
   * Maximum number of artifacts to extract (limits CPU for large repos).
   * Defaults to unlimited.
   */
  readonly maxExtractArtifacts?: number;
}

// ============================================================================
// Pipeline result
// ============================================================================

export interface SynthesisPipelineResult {
  readonly snapshot: RepositorySnapshot | null;
  readonly graph: ArtifactGraph;
  readonly modelElements: SemanticModelElement[];
  readonly residualIslands: ResidualIsland[];
  readonly extractionResults: ExtractionResult[];
  readonly errors: PipelineError[];
  readonly warnings: PipelineWarning[];
  readonly durationMs: number;
  readonly stats: PipelineStats;
}

export interface PipelineError {
  readonly phase: 'scan' | 'extract' | 'resolve' | 'assemble';
  readonly message: string;
  readonly artifactPath?: string;
  readonly recoverable: boolean;
}

export interface PipelineWarning {
  readonly phase: 'scan' | 'extract' | 'resolve' | 'assemble';
  readonly message: string;
  readonly artifactPath?: string;
}

export interface PipelineStats {
  readonly scannedFiles: number;
  readonly eligibleArtifacts: number;
  readonly extractedNodes: number;
  readonly resolvedEdges: number;
  readonly unresolvedEdges: number;
  readonly ambiguousEdges: number;
  readonly crossRepoEdges: number;
  readonly modelElementsGenerated: number;
  readonly residualIslandsGenerated: number;
}

// ============================================================================
// Build ExtractionContext for a given artifact
// ============================================================================

function buildExtractionContext(
  rootPath: string,
  allArtifacts: ReadonlyMap<string, ArtifactRecord>,
  existingNodes: ReadonlyMap<string, GraphNode>,
  existingElements: ReadonlyMap<string, SemanticModelElement>,
  snapshotRef: SnapshotRef | undefined,
): ExtractionContext {
  const base: ExtractionContext = {
    repositoryRoot: rootPath,
    allArtifacts,
    readFile: async (relativePath: string): Promise<string> => {
      const { readFile } = await import('fs/promises');
      const { join } = await import('path');
      return readFile(join(rootPath, relativePath), 'utf-8');
    },
    existingGraphNodes: existingNodes,
    existingModelElements: existingElements,
  };
  // Only include snapshotRef when defined to satisfy exactOptionalPropertyTypes
  return snapshotRef ? { ...base, snapshotRef } : base;
}

// ============================================================================
// Graph assembly
// ============================================================================

function assembleGraph(
  snapshotRef: SnapshotRef | undefined,
  rootPath: string,
  allNodes: GraphNode[],
  resolvedEdges: GraphEdge[],
  unresolvedEdges: UnresolvedGraphEdge[],
  resolutionRecords: EdgeResolutionRecord[],
): ArtifactGraph {
  const now = new Date().toISOString();

  // Build nodeIndex (kind → nodeIds) and edgeIndex (kind → edgeIds)
  const nodeIndex: Record<string, string[]> = {};
  for (const node of allNodes) {
    const list = nodeIndex[node.kind] ?? (nodeIndex[node.kind] = []);
    list.push(node.id);
  }

  const edgeIndex: Record<string, string[]> = {};
  for (const edge of resolvedEdges) {
    const list = edgeIndex[edge.kind] ?? (edgeIndex[edge.kind] = []);
    list.push(edge.id);
  }

  // Deterministic graph ID: hash of snapshotRef + node count
  const graphId = snapshotRef
    ? buildDeterministicNodeId(snapshotRef, rootPath, 'graph', `nodes:${allNodes.length}`)
    : randomUUID();

  return {
    id: graphId,
    repositoryRoot: rootPath,
    createdAt: now,
    updatedAt: now,
    version: 1,
    snapshotRef,
    nodes: allNodes,
    edges: resolvedEdges,
    unresolvedEdges,
    edgeResolutionRecords: resolutionRecords,
    nodeIndex,
    edgeIndex,
  };
}

// ============================================================================
// SynthesisPipeline
// ============================================================================

export class SynthesisPipeline {
  private readonly config: SynthesisPipelineConfig;

  constructor(config: SynthesisPipelineConfig) {
    this.config = config;
  }

  /**
   * Run the full synthesis pipeline against an already-acquired RepositorySnapshot.
   */
  async runFromSnapshot(snapshot: RepositorySnapshot): Promise<SynthesisPipelineResult> {
    return this.run(snapshot.localRootPath, snapshot);
  }

  /**
   * Run the full synthesis pipeline against a local directory path.
   * No remote source acquisition — use runFromSnapshot for that.
   */
  async runFromLocalPath(rootPath: string): Promise<SynthesisPipelineResult> {
    return this.run(rootPath, null);
  }

  private async run(
    rootPath: string,
    snapshot: RepositorySnapshot | null,
  ): Promise<SynthesisPipelineResult> {
    const startTime = Date.now();
    const errors: PipelineError[] = [];
    const warnings: PipelineWarning[] = [];
    const allNodes: GraphNode[] = [];
    const allUnresolvedEdges: UnresolvedGraphEdge[] = [];
    const allResolvedEdges: GraphEdge[] = [];
    const allModelElements: SemanticModelElement[] = [];
    const allResidualIslands: ResidualIsland[] = [];
    const extractionResults: ExtractionResult[] = [];

    const snapshotRef = snapshot?.snapshotRef;
    const residualThreshold = this.config.residualConfidenceThreshold ?? 0.5;

    // ── Phase 1: Scan ─────────────────────────────────────────────────────────
    let inventory: Awaited<ReturnType<typeof scanRepository>>;
    try {
      const scanConfig: Partial<ScannerConfig> = {
        ...this.config.scannerConfig,
        rootPath,
        ...(snapshotRef ? { snapshotRef } : {}),
      };
      inventory = await scanRepository(scanConfig);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      errors.push({ phase: 'scan', message: msg, recoverable: false });
      return this.buildEmptyResult(snapshot, errors, warnings, startTime);
    }

    // ── Phase 2: Extract ──────────────────────────────────────────────────────
    const artifactMap = new Map<string, ArtifactRecord>(
      inventory.artifacts.map(a => [a.relativePath, a]),
    );
    const existingNodes = new Map<string, GraphNode>();
    const existingElements = new Map<string, SemanticModelElement>();

    const ctx = buildExtractionContext(
      rootPath,
      artifactMap,
      existingNodes,
      existingElements,
      snapshotRef,
    );

    const maxExtract = this.config.maxExtractArtifacts ?? Infinity;
    let extractCount = 0;

    for (const artifact of inventory.artifacts) {
      if (extractCount >= maxExtract) break;
      if (artifact.isGenerated || artifact.isBinary) continue;
      if (!artifact.extractorEligibility.some(e => e.eligible)) continue;

      for (const extractor of this.config.extractors) {
        if (!extractor.canExtract(artifact)) continue;

        try {
          const result = await extractor.extract(artifact, ctx);
          extractionResults.push(result);

          for (const node of result.nodes) {
            allNodes.push(node);
            existingNodes.set(node.id, node);
          }
          allUnresolvedEdges.push(...result.unresolvedEdges);
          allResolvedEdges.push(...result.edges);

          // Route model elements by confidence
          for (const element of result.modelElements) {
            if (element.confidence >= residualThreshold) {
              allModelElements.push(element);
              existingElements.set(element.id, element);
            } else {
              // Convert low-confidence elements into residual islands
              allResidualIslands.push({
                id: element.id,
                kind: 'code',
                originalSource: artifact.relativePath,
                normalizedSummary: `Low-confidence extraction: ${element.name}`,
                reasonUnmodeled: `Confidence ${element.confidence.toFixed(2)} below threshold ${residualThreshold}`,
                reviewRequired: true,
                regenerationStrategy: 'verbatim-preserve',
                sourceLocation: {
                  filePath: artifact.relativePath,
                  startLine: 0,
                  startColumn: 0,
                  endLine: 0,
                  endColumn: 0,
                },
                extractorId: result.extractorId,
                extractorVersion: result.extractorVersion,
                extractedAt: new Date().toISOString(),
                confidence: element.confidence,
                linkedModelElementIds: [],
                tags: [],
              });
            }
          }
          allResidualIslands.push(...result.residualIslands);

          for (const warn of result.warnings) {
            warnings.push({
              phase: 'extract',
              message: warn.message,
              artifactPath: artifact.relativePath,
            });
          }
          for (const err of result.errors) {
            (err.recoverable ? warnings : errors).push({
              phase: 'extract',
              message: err.message,
              artifactPath: artifact.relativePath,
              recoverable: err.recoverable,
            } as PipelineError);
          }
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          errors.push({
            phase: 'extract',
            message: msg,
            artifactPath: artifact.relativePath,
            recoverable: true,
          });
        }
      }
      extractCount++;
    }

    // ── Phase 3: Resolve symbols ──────────────────────────────────────────────
    let resolutionRecords: EdgeResolutionRecord[] = [];
    let remainingUnresolved: UnresolvedGraphEdge[] = [];
    let ambiguousCount = 0;
    let crossRepoCount = 0;

    if (allUnresolvedEdges.length > 0) {
      try {
        const resolution = resolveSymbols(allUnresolvedEdges, allNodes);
        allResolvedEdges.push(...resolution.resolvedEdges);
        resolutionRecords = resolution.resolutionRecords;
        remainingUnresolved = resolution.remainingUnresolved;

        ambiguousCount = resolution.resolutionRecords.filter(r => r.status === 'ambiguous').length;
        crossRepoCount = resolution.resolutionRecords.filter(r => r.status === 'cross-repo').length;

        if (ambiguousCount > 0) {
          warnings.push({
            phase: 'resolve',
            message: `${ambiguousCount} edges have ambiguous targets and require human review`,
          });
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        errors.push({ phase: 'resolve', message: msg, recoverable: true });
      }
    }

    // ── Phase 4: Assemble graph ───────────────────────────────────────────────
    const graph = assembleGraph(
      snapshotRef,
      rootPath,
      allNodes,
      allResolvedEdges,
      remainingUnresolved,
      resolutionRecords,
    );

    const stats: PipelineStats = {
      scannedFiles: inventory.summary.totalFiles,
      eligibleArtifacts: inventory.summary.eligibleForExtraction,
      extractedNodes: allNodes.length,
      resolvedEdges: allResolvedEdges.length,
      unresolvedEdges: remainingUnresolved.length,
      ambiguousEdges: ambiguousCount,
      crossRepoEdges: crossRepoCount,
      modelElementsGenerated: allModelElements.length,
      residualIslandsGenerated: allResidualIslands.length,
    };

    return {
      snapshot,
      graph,
      modelElements: allModelElements,
      residualIslands: allResidualIslands,
      extractionResults,
      errors,
      warnings,
      durationMs: Date.now() - startTime,
      stats,
    };
  }

  private buildEmptyResult(
    snapshot: RepositorySnapshot | null,
    errors: PipelineError[],
    warnings: PipelineWarning[],
    startTime: number,
  ): SynthesisPipelineResult {
    const emptyGraph: ArtifactGraph = {
      id: randomUUID(),
      repositoryRoot: snapshot?.localRootPath ?? '',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 0,
      nodes: [],
      edges: [],
      unresolvedEdges: [],
      edgeResolutionRecords: [],
      nodeIndex: {},
      edgeIndex: {},
    };
    return {
      snapshot,
      graph: emptyGraph,
      modelElements: [],
      residualIslands: [],
      extractionResults: [],
      errors,
      warnings,
      durationMs: Date.now() - startTime,
      stats: {
        scannedFiles: 0,
        eligibleArtifacts: 0,
        extractedNodes: 0,
        resolvedEdges: 0,
        unresolvedEdges: 0,
        ambiguousEdges: 0,
        crossRepoEdges: 0,
        modelElementsGenerated: 0,
        residualIslandsGenerated: 0,
      },
    };
  }
}
