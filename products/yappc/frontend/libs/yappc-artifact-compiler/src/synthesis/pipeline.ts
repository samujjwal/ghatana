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

import { createHash, randomUUID } from 'crypto';
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
import type { SemanticModelElement, SemanticProductModel } from '../model';
import type { ResidualIsland } from '../residual/types';
import { validateGraph } from '../graph/validateGraph';
import type { ExtractorRegistry } from '../extractors/extractor-registry';

// ============================================================================
// Pipeline configuration
// ============================================================================

export interface SynthesisPipelineConfig {
  /** Override scan config (e.g. to restrict to specific paths). */
  readonly scannerConfig?: Partial<ScannerConfig>;
  /** Registered extractors to run against eligible artifacts. */
  readonly extractors: readonly ArtifactExtractor[];
  /**
   * P1-8: Enforce extractor registry capability.
   * When true, only extractors registered in the canonical registry are allowed.
   * Defaults to true in production.
   */
  readonly enforceExtractorRegistry?: boolean;
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
  readonly model: SemanticProductModel;
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

function buildFileSourceLocation(relativePath: string, source: string): {
  filePath: string;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
} {
  const lines = source.split('\n');
  const lastLine = lines.length > 0 ? lines[lines.length - 1]! : '';

  return {
    filePath: relativePath,
    startLine: 0,
    startColumn: 0,
    endLine: Math.max(lines.length - 1, 0),
    endColumn: lastLine.length,
  };
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

  // Build nodeIndex (type -> nodeIds) and edgeIndex (relationshipType -> edgeIds)
  const nodeIndex: Record<string, string[]> = {};
  for (const node of allNodes) {
    const list = nodeIndex[node.type] ?? (nodeIndex[node.type] = []);
    list.push(node.id);
  }

  const edgeIndex: Record<string, string[]> = {};
  for (const edge of resolvedEdges) {
    const list = edgeIndex[edge.relationshipType] ?? (edgeIndex[edge.relationshipType] = []);
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
// Build SemanticProductModel container
// ============================================================================

function buildSemanticProductModel(
  snapshotRef: SnapshotRef | undefined,
  rootPath: string,
  modelElements: SemanticModelElement[],
  residualIslands: ResidualIsland[],
): SemanticProductModel {
  const now = new Date().toISOString();

  // Build elementIndex (kind -> elementIds)
  const elementIndex: Record<string, string[]> = {};
  for (const element of modelElements) {
    const list = elementIndex[element.kind] ?? (elementIndex[element.kind] = []);
    list.push(element.id);
  }

  // Collect residual island IDs
  const residualIslandIds = residualIslands.map(island => island.id);

  const sourceModelRef = snapshotRef
    ? buildDeterministicNodeId(snapshotRef, rootPath, 'model', `elements:${modelElements.length}`)
    : undefined;

  // P1-8: Deterministic SemanticProductModel.id from snapshot + element checksum
  // Compute a deterministic hash of all element IDs and snapshot info
  const hashInput = [
    snapshotRef ? `${snapshotRef.provider}:${snapshotRef.repoId}:${snapshotRef.commitSha}` : 'no-snapshot',
    rootPath,
    ...modelElements.map(e => e.id).sort(),
    ...residualIslandIds.sort(),
  ].join('|');
  const deterministicId = createHash('sha256').update(hashInput).digest('hex');

  return {
    id: deterministicId,
    sourceModelRef,
    repositoryRoot: rootPath,
    createdAt: now,
    updatedAt: now,
    version: 1,
    elements: modelElements,
    elementIndex,
    residualIslandIds,
  };
}

// ============================================================================
// SynthesisPipeline
// ============================================================================

export class SynthesisPipeline {
  private readonly config: SynthesisPipelineConfig;
  private readonly extractorRegistry?: ExtractorRegistry | undefined;

  constructor(config: SynthesisPipelineConfig, extractorRegistry?: ExtractorRegistry | undefined) {
    this.config = config;
    this.extractorRegistry = extractorRegistry;

    // P1-8: Enforce extractor registry capability when enabled
    if (this.config.enforceExtractorRegistry !== false && this.extractorRegistry) {
      for (const extractor of this.config.extractors) {
        const extractorId = extractor.identity.id;
        const registered = this.extractorRegistry.get(extractorId);
        if (!registered) {
          throw new Error(
            `Extractor "${extractorId}" is not registered in the canonical registry. ` +
            'Only registered extractors are allowed when enforceExtractorRegistry is enabled.'
          );
        }
        // Verify version compatibility
        if (registered.identity.version !== extractor.identity.version) {
          throw new Error(
            `Extractor "${extractorId}" version mismatch: ` +
            `registered version "${registered.identity.version}" vs provided version "${extractor.identity.version}"`
          );
        }
      }
    }
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
      // P0: When running from snapshot, pass snapshot.files as allowed inventory boundary
      // This ensures the pipeline only processes files that were in the snapshot
      const allowedFiles = snapshot?.files.map(f => f.relativePath);
      const scanConfig: Partial<ScannerConfig> = {
        ...this.config.scannerConfig,
        rootPath,
        ...(snapshotRef ? { snapshotRef } : {}),
        ...(allowedFiles ? { allowedFiles } : {}),
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
              const originalSource = await ctx.readFile(artifact.relativePath);
              const sourceLocation = buildFileSourceLocation(artifact.relativePath, originalSource);

              // Convert low-confidence elements into residual islands
              allResidualIslands.push({
                id: element.id,
                kind: 'code',
                originalSource,
                normalizedSummary: `Low-confidence extraction: ${element.name}`,
                reasonUnmodeled: `Confidence ${element.confidence.toFixed(2)} below threshold ${residualThreshold}`,
                reviewRequired: true,
                reviewReason: 'Low-confidence extraction was preserved verbatim for manual review.',
                regenerationStrategy: 'verbatim-preserve',
                sourceLocation,
                extractorId: result.extractorId,
                extractorVersion: result.extractorVersion,
                extractedAt: new Date().toISOString(),
                confidence: element.confidence,
                linkedModelElementIds: [element.id],
                tags: [],
                rawFragmentRef: `${artifact.relativePath}#full-file`,
                checksum: artifact.checksum,
                risk: element.confidence < 0.25 ? 'high' : 'medium',
                relatedGraphNodeIds: element.graphNodeIds,
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

    // Validate graph before proceeding
    const validationResult = validateGraph(graph);
    if (validationResult.errors.length > 0) {
      for (const error of validationResult.errors) {
        errors.push({
          phase: 'assemble',
          message: error.message,
          recoverable: false,
        });
      }
    }
    if (validationResult.warnings.length > 0) {
      for (const warning of validationResult.warnings) {
        warnings.push({
          phase: 'assemble',
          message: warning.message,
        });
      }
    }

    // ── Phase 5: Assemble SemanticProductModel ──────────────────────────────────
    const model = buildSemanticProductModel(
      snapshotRef,
      rootPath,
      allModelElements,
      allResidualIslands,
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
      model,
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
    const emptyModel: SemanticProductModel = {
      id: randomUUID(),
      repositoryRoot: snapshot?.localRootPath ?? '',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 0,
      elements: [],
      elementIndex: {},
      residualIslandIds: [],
    };
    return {
      snapshot,
      graph: emptyGraph,
      model: emptyModel,
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
