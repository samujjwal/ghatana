/**
 * @fileoverview Model Synthesizer — converts ArtifactGraph nodes into SemanticModel elements.
 *
 * This module contains synthesizers for different node kinds (components, pages, data entities, etc.).
 * Each synthesizer is responsible for converting raw graph nodes into rich semantic model elements
 * with proper confidence scoring, provenance tracking, and cross-references.
 */

import type { ArtifactGraph, GraphNode } from '../graph/types';
import type {
  SemanticModelElement,
  ComponentModel,
  PageModel,
  DataModel,
  StateStoreModel,
  TokenModel,
  ApiModel,
  WorkflowModel,
} from '../model/types';
import type { SynthesisResult } from './types';

// ============================================================================
// Component Synthesizer
// ============================================================================

export class ComponentSynthesizer {
  readonly id = 'component-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['component'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'component';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const props = (node.metadata['props'] as Array<{ name: string; type: string; required?: boolean; defaultValue?: unknown; description?: string }>) ?? [];
    const slots = (node.metadata['slots'] as Array<{ name: string; multiple: boolean; required: boolean }>) ?? [];
    const events = (node.metadata['events'] as Array<{ name: string; payloadType: string; description?: string }>) ?? [];
    const variants = (node.metadata['variants'] as Array<{ name: string; propOverrides: Record<string, unknown>; description?: string }>) ?? [];
    const accessibility = node.metadata['accessibility'] as ComponentModel['accessibility'] ?? undefined;
    const tags = (node.metadata['tags'] as string[]) ?? [];

    const component: ComponentModel = {
      id: node.id,
      name: node.label,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'component',
      contractName: node.label,
      props: props.map(p => ({
        name: p.name,
        type: p.type,
        required: p.required ?? false,
        defaultValue: p.defaultValue,
        description: p.description,
        examples: [],
      })),
      slots,
      events,
      variants,
      stateConnections: this.findConnectedNodeIds(node.id, graph, 'uses'),
      dataDependencies: this.findConnectedNodeIds(node.id, graph, 'queries'),
      styleDependencies: this.findConnectedNodeIds(node.id, graph, 'styles'),
      accessibility,
      storyIds: this.findConnectedNodeIds(node.id, graph, 'story-for'),
      builderCanvasHints: (node.metadata['builderCanvasHints'] as Record<string, unknown>) ?? {},
      securityFlags: node.privacySecurityFlags,
      privacyFlags: node.privacySecurityFlags,
      tags,
    };

    return {
      elements: [component],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }

  private findConnectedNodeIds(nodeId: string, graph: ArtifactGraph, edgeKind: string): string[] {
    return graph.edges
      .filter(e => e.sourceId === nodeId && e.kind === edgeKind)
      .map(e => e.targetId);
  }
}

// ============================================================================
// Page Synthesizer
// ============================================================================

export class PageSynthesizer {
  readonly id = 'page-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['page'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'page' || node.kind === 'route';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const page: PageModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'page',
      routePath: (node.metadata['routePath'] as string) ?? `/${node.label.toLowerCase()}`,
      layoutId: this.findFirstConnectedNodeId(node.id, graph, 'layout-of'),
      componentIds: this.findConnectedNodeIds(node.id, graph, 'contains'),
      dataDependencies: this.findConnectedNodeIds(node.id, graph, 'queries'),
      authGuard: node.metadata['authGuard'] as PageModel['authGuard'] ?? undefined,
      seoMetadata: node.metadata['seoMetadata'] as PageModel['seoMetadata'] ?? undefined,
      visibility: (node.metadata['visibility'] as PageModel['visibility']) ?? 'public',
      securityFlags: node.privacySecurityFlags,
      privacyFlags: node.privacySecurityFlags,
      tags: (node.metadata['tags'] as string[]) ?? [],
    };

    return {
      elements: [page],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }

  private findConnectedNodeIds(nodeId: string, graph: ArtifactGraph, edgeKind: string): string[] {
    return graph.edges
      .filter(e => e.sourceId === nodeId && e.kind === edgeKind)
      .map(e => e.targetId);
  }

  private findFirstConnectedNodeId(nodeId: string, graph: ArtifactGraph, edgeKind: string): string | undefined {
    const edge = graph.edges.find(e => e.sourceId === nodeId && e.kind === edgeKind);
    return edge?.targetId;
  }
}

// ============================================================================
// Data Entity Synthesizer
// ============================================================================

export class DataEntitySynthesizer {
  readonly id = 'data-entity-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['data-entity'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'entity' || node.kind === 'database-object';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const tags = (node.metadata['tags'] as string[]) ?? [];
    const dataEntity: DataModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'data-entity',
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
    };

    return {
      elements: [dataEntity],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }
}

// ============================================================================
// State Store Synthesizer
// ============================================================================

export class StateStoreSynthesizer {
  readonly id = 'state-store-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['state-store'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'state-store';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const stateStore: StateStoreModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'state-store',
      storeType: (node.metadata['storeType'] as StateStoreModel['storeType']) ?? 'unknown',
      stateTree: (node.metadata['stateTree'] as Record<string, unknown>) ?? undefined,
      actionTypes: (node.metadata['actions'] as StateStoreModel['actionTypes']) ?? [],
      reducers: (node.metadata['reducers'] as StateStoreModel['reducers']) ?? [],
      selectors: (node.metadata['selectors'] as StateStoreModel['selectors']) ?? [],
      connectedComponentIds: this.findConnectedNodeIds(node.id, graph, 'uses'),
      securityFlags: node.privacySecurityFlags,
      privacyFlags: node.privacySecurityFlags,
      tags: (node.metadata['tags'] as string[]) ?? [],
    };

    return {
      elements: [stateStore],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }

  private findConnectedNodeIds(nodeId: string, graph: ArtifactGraph, edgeKind: string): string[] {
    return graph.edges
      .filter(e => e.sourceId === nodeId && e.kind === edgeKind)
      .map(e => e.targetId);
  }
}

// ============================================================================
// Token Synthesizer
// ============================================================================

export class TokenSynthesizer {
  readonly id = 'token-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['token'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'token';
  }

  synthesize(node: GraphNode, _graph: ArtifactGraph): SynthesisResult {
    const token: TokenModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'token',
      tokenPath: (node.metadata['tokenPath'] as string[]) ?? [node.label],
      value: {
        value: (node.metadata['value'] as string | number) ?? '',
        type: (node.metadata['valueType'] as TokenModel['value']['type']) ?? 'string',
      },
      aliases: (node.metadata['aliases'] as string[][]) ?? [],
      platformOverrides: (node.metadata['platformOverrides'] as TokenModel['platformOverrides']) ?? {},
      securityFlags: node.privacySecurityFlags,
      privacyFlags: node.privacySecurityFlags,
      tags: (node.metadata['tags'] as string[]) ?? [],
    };

    return {
      elements: [token],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: _graph.version,
    };
  }
}

// ============================================================================
// API Endpoint Synthesizer
// ============================================================================

export class ApiEndpointSynthesizer {
  readonly id = 'api-endpoint-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['api-endpoint'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'api-endpoint';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const tags = (node.metadata['tags'] as ApiModel['tags']) ?? [];
    const apiEndpoint: ApiModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'api-endpoint',
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
    };

    return {
      elements: [apiEndpoint],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }
}

// ============================================================================
// Workflow Synthesizer
// ============================================================================

export class WorkflowSynthesizer {
  readonly id = 'workflow-synthesizer';
  readonly version = '1.0.0';
  readonly targetModelKinds = ['workflow-job'] as const;

  canSynthesize(node: GraphNode, _graph: ArtifactGraph): boolean {
    return node.kind === 'workflow-job';
  }

  synthesize(node: GraphNode, graph: ArtifactGraph): SynthesisResult {
    const jobs = (node.metadata['jobs'] as Array<{ id: string; name: string; dependsOn: string[]; steps: Array<{ name: string; command: string }> }>) ?? [];
    const workflow: WorkflowModel = {
      id: node.id,
      name: node.label,
      description: undefined,
      confidence: node.confidence,
      provenance: {
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        sourcePaths: [node.sourceLocation.filePath],
        kind: node.provenance,
        extractedAt: new Date().toISOString(),
      },
      kind: 'workflow',
      trigger: (node.metadata['trigger'] as WorkflowModel['trigger']) ?? 'manual',
      branchFilter: (node.metadata['branchFilter'] as string[]) ?? [],
      jobs: jobs.map(job => ({
        id: job.id,
        name: job.name,
        dependsOn: job.dependsOn,
        steps: job.steps.map(step => ({
          name: step.name,
          command: step.command,
          environment: {},
        })),
      })),
      environment: (node.metadata['environment'] as Record<string, string>) ?? {},
      artifactOutputs: (node.metadata['artifactOutputs'] as string[]) ?? [],
      securityFlags: node.privacySecurityFlags,
      privacyFlags: node.privacySecurityFlags,
      tags: (node.metadata['tags'] as string[]) ?? [],
    };

    return {
      elements: [workflow],
      residualIslands: [],
      confidence: node.confidence,
      sourceGraphVersion: graph.version,
    };
  }
}

// ============================================================================
// Orchestrator
// ============================================================================

export const MODEL_SYNTHESIZERS = [
  new ComponentSynthesizer(),
  new PageSynthesizer(),
  new DataEntitySynthesizer(),
  new StateStoreSynthesizer(),
  new TokenSynthesizer(),
  new ApiEndpointSynthesizer(),
  new WorkflowSynthesizer(),
] as const;

export function synthesizeModelElement(node: GraphNode, graph: ArtifactGraph): SemanticModelElement | null {
  for (const synthesizer of MODEL_SYNTHESIZERS) {
    if (synthesizer.canSynthesize(node, graph)) {
      const result = synthesizer.synthesize(node, graph);
      return result.elements[0] ?? null;
    }
  }
  return null;
}
