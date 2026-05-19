/**
 * @fileoverview Synthesis Engine — orchestrates artifact graph ingestion,
 * analysis via the Java artifact graph service, and assembly of the
 * SemanticProductModel.
 */

import type { ArtifactGraph, GraphNode, GraphEdge } from '../graph/types';
import type {
  SemanticProductModel,
  SemanticModelElement,
  ComponentModel,
  PageModel,
  DataModel,
  StateStoreModel,
  TokenModel,
  ApiModel,
} from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Configuration
// ============================================================================

const ARTIFACT_API_BASE = process.env.YAPPC_ARTIFACT_API_URL ?? 'http://localhost:8080/api/v1/yappc/artifact';

// ============================================================================
// HTTP Client (lightweight fetch wrapper)
// ============================================================================

interface ApiResponse<T> {
  readonly data: T;
  readonly status: number;
}

async function artifactApiPost<T>(path: string, body: unknown): Promise<ApiResponse<T>> {
  const url = `${ARTIFACT_API_BASE}${path}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const text = await response.text().catch(() => 'Unknown error');
    throw new Error(`Artifact API error (${response.status}): ${text}`);
  }

  const data = (await response.json()) as T;
  return { data, status: response.status };
}

async function artifactApiGet<T>(path: string, query: Record<string, string>): Promise<ApiResponse<T>> {
  const url = new URL(`${ARTIFACT_API_BASE}${path}`);
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      url.searchParams.set(key, value);
    }
  });

  const response = await fetch(url.toString(), {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    const text = await response.text().catch(() => 'Unknown error');
    throw new Error(`Artifact API error (${response.status}): ${text}`);
  }

  const data = (await response.json()) as T;
  return { data, status: response.status };
}

// ============================================================================
// DTO Mappers — ArtifactGraph → Java service payload
// ============================================================================

interface ArtifactNodePayload {
  readonly id: string;
  readonly type: string;
  readonly name: string;
  readonly filePath: string;
  readonly content: string;
  readonly properties: Record<string, unknown>;
  readonly tags: readonly string[];
  readonly tenantId: string;
  readonly projectId: string;
}

interface ArtifactEdgePayload {
  readonly sourceNodeId: string;
  readonly targetNodeId: string;
  readonly relationshipType: string;
  readonly properties: Record<string, unknown>;
}

function mapNodeToPayload(node: GraphNode, tenantId: string, projectId: string): ArtifactNodePayload {
  return {
    id: node.id,
    type: node.type,
    name: node.label,
    filePath: (node.sourceLocation?.filePath ?? ''),
    content: JSON.stringify(node.metadata),
    properties: node.metadata,
    tags: node.privacySecurityFlags,
    tenantId,
    projectId,
  };
}

function mapEdgeToPayload(edge: GraphEdge): ArtifactEdgePayload {
  return {
    sourceNodeId: edge.sourceId,
    targetNodeId: edge.targetId,
    relationshipType: edge.relationshipType,
    properties: edge.metadata,
  };
}

// ============================================================================
// Synthesis Engine
// ============================================================================

export interface SynthesisEngineOptions {
  readonly apiBaseUrl?: string;
  readonly tenantId: string;
  readonly projectId: string;
}

export class SynthesisEngine {
  private readonly tenantId: string;
  private readonly projectId: string;

  constructor(options: SynthesisEngineOptions) {
    this.tenantId = options.tenantId;
    this.projectId = options.projectId;
  }

  /**
   * Ingest an ArtifactGraph into the Java graph service.
   */
  async ingest(graph: ArtifactGraph): Promise<{ success: boolean; nodeCount: number; edgeCount: number }> {
    const payload = {
      projectId: this.projectId,
      tenantId: this.tenantId,
      nodes: graph.nodes.map(n => mapNodeToPayload(n, this.tenantId, this.projectId)),
      edges: graph.edges.map(mapEdgeToPayload),
    };

    const result = await artifactApiPost<{
      success: boolean;
      result: { nodeCount: number; edgeCount: number };
      message: string;
    }>('/graph/ingest', payload);

    return {
      success: result.data.success,
      nodeCount: result.data.result.nodeCount,
      edgeCount: result.data.result.edgeCount,
    };
  }

  /**
   * Run graph analysis algorithms (centrality, cycles, communities, build-order).
   */
  async analyze(
    algorithmTypes: readonly string[],
    nodeIds?: readonly string[]
  ): Promise<readonly AnalysisResult[]> {
    const payload = {
      projectId: this.projectId,
      tenantId: this.tenantId,
      algorithmTypes: [...algorithmTypes],
      nodeIds: nodeIds ? [...nodeIds] : undefined,
    };

    const result = await artifactApiPost<readonly AnalysisResult[]>('/graph/analyze', payload);
    return result.data;
  }

  /**
   * Query the artifact graph for orphaned nodes, dependencies, dependents, or stats.
   */
  async query(queryType: string, seedIds?: readonly string[]): Promise<QueryResult> {
    const query: Record<string, string> = {
      projectId: this.projectId,
      tenantId: this.tenantId,
      queryType,
    };
    if (seedIds && seedIds.length > 0) {
      query.seedIds = seedIds.join(',');
    }

    const result = await artifactApiGet<QueryResult>('/graph/query', query);
    return result.data;
  }

  /**
   * Three-way semantic merge of artifact models.
   */
  async merge(
    baseModel: Record<string, unknown>,
    leftModel: Record<string, unknown>,
    rightModel: Record<string, unknown>,
    resolutionStrategy = 'auto-resolve'
  ): Promise<{ success: boolean; mergedModel: Record<string, unknown> }> {
    const payload = {
      projectId: this.projectId,
      tenantId: this.tenantId,
      baseModel,
      leftModel,
      rightModel,
      resolutionStrategy,
    };

    const result = await artifactApiPost<{
      success: boolean;
      result: { mergedModel: Record<string, unknown> };
      message: string;
    }>('/graph/merge', payload);

    return {
      success: result.data.success,
      mergedModel: result.data.result.mergedModel,
    };
  }

  /**
   * Analyze residual islands flagged by the TypeScript scanner.
   */
  async analyzeResidual(islands: readonly ResidualIsland[]): Promise<{
    success: boolean;
    enrichedIslands: readonly Record<string, unknown>[];
    count: number;
  }> {
    const payload = {
      projectId: this.projectId,
      tenantId: this.tenantId,
      residualIslands: islands.map(i => ({
        id: i.id,
        kind: i.kind,
        sourceLocation: i.sourceLocation,
        confidence: i.confidence,
        regenerationStrategy: i.regenerationStrategy,
      })),
    };

    const result = await artifactApiPost<{
      success: boolean;
      result: { islands: readonly Record<string, unknown>[]; count: number };
      message: string;
    }>('/residual/analyze', payload);

    return {
      success: result.data.success,
      enrichedIslands: result.data.result.islands,
      count: result.data.result.count,
    };
  }

  /**
   * Full synthesis pipeline: ingest → analyze → assemble SemanticProductModel.
   */
  async synthesize(graph: ArtifactGraph, residualIslands: readonly ResidualIsland[]): Promise<{
    model: SemanticProductModel;
    analysisResults: readonly AnalysisResult[];
    enrichedResiduals: readonly Record<string, unknown>[];
  }> {
    // Step 1: Ingest graph into Java service
    await this.ingest(graph);

    // Step 2: Run analysis algorithms
    const analysisResults = await this.analyze([
      'centrality',
      'cycles',
      'topological',
      'communities',
      'reachability',
    ]);

    // Step 3: Analyze residual islands
    const residualAnalysis = residualIslands.length > 0
      ? await this.analyzeResidual(residualIslands)
      : { success: true, enrichedIslands: [] as readonly Record<string, unknown>[], count: 0 };

    // Step 4: Assemble SemanticProductModel locally from graph nodes
    const elements = this.assembleSemanticElements(graph);

    const model: SemanticProductModel = {
      id: crypto.randomUUID(),
      repositoryRoot: graph.repositoryRoot,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: graph.version + 1,
      elements,
      elementIndex: this.buildElementIndex(elements),
      residualIslandIds: residualIslands.map(i => i.id),
    };

    return {
      model,
      analysisResults,
      enrichedResiduals: residualAnalysis.enrichedIslands,
    };
  }

  // -------------------------------------------------------------------------
  // Private: Semantic element assembly (local, no heavy computation)
  // -------------------------------------------------------------------------

  private assembleSemanticElements(graph: ArtifactGraph): SemanticModelElement[] {
    const elements: SemanticModelElement[] = [];

    for (const node of graph.nodes) {
      const element = this.trySynthesizeNode(node, graph);
      if (element !== null) {
        elements.push(element);
      }
    }

    return elements;
  }

  private trySynthesizeNode(node: GraphNode, graph: ArtifactGraph): SemanticModelElement | null {
    const provenanceBase = {
      extractorId: node.extractorId,
      extractorVersion: node.extractorVersion,
      sourcePaths: [(node.sourceLocation?.filePath ?? '')],
      kind: node.provenance,
      extractedAt: new Date().toISOString(),
    };

    switch (node.type) {
      case 'component': {
        const props = (node.metadata['props'] as Array<{ name: string; type: string; required?: boolean }>) ?? [];
        const tags = (node.metadata['tags'] as string[] | undefined) ?? [];
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'component',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          contractName: node.label,
          props: props.map(p => ({
            name: p.name,
            type: p.type,
            required: p.required ?? false,
            examples: [],
          })),
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: this.findConnectedNodes(node.id, graph, 'queries'),
          styleDependencies: this.findConnectedNodes(node.id, graph, 'styles'),
          accessibility: node.metadata['accessibility'] as ComponentModel['accessibility'] ?? undefined,
          storyIds: this.findConnectedNodes(node.id, graph, 'story-for'),
          builderCanvasHints: {},
          securityFlags: node.privacySecurityFlags,
          privacyFlags: node.privacySecurityFlags,
          tags,
        } as ComponentModel;
      }

      case 'page': {
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'page',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          routePath: (node.metadata['routePath'] as string) ?? `/${node.label.toLowerCase()}`,
          layoutId: this.findFirstConnectedNode(node.id, graph, 'layout-of'),
          componentIds: this.findConnectedNodes(node.id, graph, 'contains'),
          dataDependencies: this.findConnectedNodes(node.id, graph, 'queries'),
          authGuard: node.metadata['authGuard'] as PageModel['authGuard'] ?? undefined,
          seoMetadata: node.metadata['seoMetadata'] as PageModel['seoMetadata'] ?? undefined,
          visibility: (node.metadata['visibility'] as PageModel['visibility']) ?? 'public',
        } as PageModel;
      }

      case 'state-store': {
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'state-store',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          storeType: (node.metadata['storeType'] as StateStoreModel['storeType']) ?? 'unknown',
          stateTree: (node.metadata['stateTree'] as Record<string, unknown>) ?? undefined,
          actionTypes: (node.metadata['actions'] as StateStoreModel['actionTypes']) ?? [],
          reducers: (node.metadata['reducers'] as StateStoreModel['reducers']) ?? [],
          selectors: (node.metadata['selectors'] as StateStoreModel['selectors']) ?? [],
          connectedComponentIds: this.findConnectedNodes(node.id, graph, 'uses'),
        } as StateStoreModel;
      }

      case 'token': {
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'token',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          tokenPath: (node.metadata['tokenPath'] as string[]) ?? [node.label],
          value: {
            value: (node.metadata['value'] as string | number) ?? '',
            type: (node.metadata['valueType'] as TokenModel['value']['type']) ?? 'string',
          },
          aliases: (node.metadata['aliases'] as string[][]) ?? [],
          platformOverrides: (node.metadata['platformOverrides'] as TokenModel['platformOverrides']) ?? {},
        } as TokenModel;
      }

      case 'entity': {
        const tags = (node.metadata['tags'] as string[] | undefined) ?? [];
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'data-entity',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          tableName: (node.metadata['tableName'] as string) ?? node.label.toLowerCase(),
          fields: (node.metadata['fields'] as DataModel['fields']) ?? [],
          relations: (node.metadata['relations'] as DataModel['relations']) ?? [],
          indexes: (node.metadata['indexes'] as DataModel['indexes']) ?? [],
          constraints: (node.metadata['constraints'] as string[]) ?? [],
          unsupportedFeatures: (node.metadata['unsupportedFeatures'] as DataModel['unsupportedFeatures']) ?? [],
          migrationLineage: [],
          securityFlags: node.privacySecurityFlags,
          privacyFlags: node.privacySecurityFlags,
          tags,
        } as DataModel;
      }

      case 'api-endpoint': {
        const tags = (node.metadata['tags'] as ApiModel['tags']) ?? [];
        return {
          id: node.id,
          name: node.label,
          confidence: node.confidence,
          provenance: provenanceBase,
          kind: 'api-endpoint',
          graphNodeIds: [node.id],
          sourceRefs: node.sourceRef ? [node.sourceRef] : [],
          residualIslandIds: node.residualFragmentIds,
          path: (node.metadata['path'] as string) ?? '/',
          methods: (node.metadata['methods'] as string[]) ?? ['GET'],
          additionalOperations: [],
          tags,
          parameters: (node.metadata['parameters'] as ApiModel['parameters']) ?? [],
          requestBodySchema: (node.metadata['requestBodySchema'] as string) ?? undefined,
          responses: (node.metadata['responses'] as ApiModel['responses']) ?? [],
          authRequired: (node.metadata['authRequired'] as boolean) ?? false,
          rateLimited: (node.metadata['rateLimited'] as boolean) ?? false,
          securityFlags: node.privacySecurityFlags,
          privacyFlags: node.privacySecurityFlags,
        } as ApiModel;
      }

      default:
        return null;
    }
  }

  private findConnectedNodes(nodeId: string, graph: ArtifactGraph, edgeKind: string): string[] {
    return graph.edges
      .filter(e => e.sourceId === nodeId && e.relationshipType === edgeKind)
      .map(e => e.targetId);
  }

  private findFirstConnectedNode(nodeId: string, graph: ArtifactGraph, edgeKind: string): string | undefined {
    const edge = graph.edges.find(e => e.sourceId === nodeId && e.relationshipType === edgeKind);
    return edge?.targetId;
  }

  private buildElementIndex(elements: SemanticModelElement[]): Record<string, string[]> {
    const index: Record<string, string[]> = {};
    for (const element of elements) {
      const list = index[element.kind] ?? (index[element.kind] = []);
      list.push(element.id);
    }
    return index;
  }
}

// ============================================================================
// Result Types
// ============================================================================

export interface AnalysisResult {
  readonly algorithm: string;
  readonly centralityScores?: Record<string, number>;
  readonly cycles?: readonly (readonly string[])[];
  readonly communities?: readonly (readonly string[])[];
  readonly topologicalOrder?: readonly string[];
  readonly metadata?: Record<string, unknown>;
}

export interface QueryResult {
  readonly orphanedNodes?: readonly string[];
  readonly dependencies?: Record<string, readonly string[]>;
  readonly dependents?: Record<string, readonly string[]>;
  readonly nodeCount?: number;
  readonly edgeCount?: number;
  readonly nodeTypeDistribution?: Record<string, number>;
  readonly error?: string;
}
