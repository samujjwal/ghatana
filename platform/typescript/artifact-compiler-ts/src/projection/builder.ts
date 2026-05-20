/**
 * @fileoverview Projection: LogicalArtifactModel → BuilderDocument.
 *
 * Projects a LogicalArtifactModel into the canonical BuilderDocument format
 * used by @ghatana/ui-builder. Each artifact node of kind "component",
 * "page", or "layout" becomes a ComponentInstance in the resulting document.
 *
 * This module deliberately avoids a hard runtime dependency on
 * @ghatana/ui-builder to keep the compiler package lightweight. The projected
 * data uses the same structural shape but is typed locally so consumers can
 * choose to feed it into ui-builder APIs without circular coupling.
 *
 * @doc.type module
 * @doc.purpose Project LogicalArtifactModel into BuilderDocument shape
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import type { ArtifactNode, LogicalArtifactModel } from "@ghatana/artifact-contracts";
import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import { computeFidelityReport } from "@ghatana/artifact-contracts";

// ============================================================================
// PROJECTED TYPES
// ============================================================================
// These types mirror @ghatana/ui-builder's BuilderDocument structure so
// consumers can use them without a direct import from ui-builder.

/**
 * Minimal ComponentInstance projection.
 */
export interface ProjectedComponentInstance {
  readonly id: string;
  readonly contractName: string;
  readonly props: Record<string, unknown>;
  readonly slots: Record<string, readonly string[]>;
  readonly bindings: readonly unknown[];
  readonly metadata: {
    readonly name: string;
    readonly locked: boolean;
    readonly hidden: boolean;
  };
}

/**
 * Minimal BuilderDocument projection.
 */
export interface ProjectedBuilderDocument {
  readonly schemaVersion: string;
  readonly documentId: string;
  readonly label: string;
  readonly nodes: Record<string, ProjectedComponentInstance>;
  readonly layout: {
    readonly rootId: string;
    readonly nodes: Record<string, { readonly children: readonly string[] }>;
  };
  readonly metadata: {
    readonly createdAt: string;
    readonly updatedAt: string;
    readonly sourceModelId: string;
  };
}

// ============================================================================
// OPTIONS
// ============================================================================

export interface ProjectBuilderOptions {
  /**
   * Only project nodes of these kinds. Defaults to component, page, layout.
   */
  readonly includeKinds?: ReadonlyArray<ArtifactNode["kind"]>;
  /**
   * Schema version string for the projected document.
   * Default: "1.0.0".
   */
  readonly schemaVersion?: string;
}

const DEFAULT_INCLUDE_KINDS: ReadonlyArray<ArtifactNode["kind"]> = [
  "component",
  "page",
  "layout",
];

// ============================================================================
// RESULT
// ============================================================================

export interface ProjectBuilderResult {
  readonly document: ProjectedBuilderDocument;
  readonly fidelityReport: FidelityReport;
  /**
   * Node IDs that were excluded because their kind is not in includeKinds.
   */
  readonly excludedNodeIds: readonly string[];
}

// ============================================================================
// PROJECTION LOGIC
// ============================================================================

/**
 * Project a LogicalArtifactModel into a BuilderDocument-shaped object.
 *
 * Only nodes whose `kind` is in `options.includeKinds` are projected.
 * Other nodes are recorded in `excludedNodeIds`.
 *
 * @doc.type function
 * @doc.purpose Project LogicalArtifactModel → BuilderDocument shape
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export function projectToBuilder(
  model: LogicalArtifactModel,
  options?: ProjectBuilderOptions,
): ProjectBuilderResult {
  const includeKinds = options?.includeKinds ?? DEFAULT_INCLUDE_KINDS;
  const schemaVersion = options?.schemaVersion ?? "1.0.0";

  const projectedNodes: Record<string, ProjectedComponentInstance> = {};
  const rootChildren: string[] = [];
  const excludedNodeIds: string[] = [];
  const lossPoints: LossPoint[] = [];

  for (const node of Object.values(model.nodes)) {
    if (!includeKinds.includes(node.kind)) {
      excludedNodeIds.push(node.id);
      continue;
    }

    const instance: ProjectedComponentInstance = {
      id: node.id,
      contractName: node.displayName,
      props: Object.fromEntries(
        Object.entries(node.inferredProps).map(([key, type]) => [key, `/* ${type} */`]),
      ),
      slots: {},
      bindings: [],
      metadata: {
        name: node.displayName,
        locked: false,
        hidden: false,
      },
    };

    projectedNodes[node.id] = instance;
    rootChildren.push(node.id);

    // Flag low-confidence nodes as advisory loss points
    if (node.classificationConfidence < 0.8) {
      lossPoints.push({
        code: "low-confidence-node-projected",
        description: `Node "${node.displayName}" projected with confidence ${(node.classificationConfidence * 100).toFixed(0)}%.`,
        severity: "info",
        sourceRef: node.sourceRef,
        confidenceImpact: (1 - node.classificationConfidence) * 0.1,
      });
    }
  }

  const rootId = `${model.modelId}-root`;
  const now = new Date().toISOString();

  const document: ProjectedBuilderDocument = {
    schemaVersion,
    documentId: model.modelId,
    label: model.label,
    nodes: projectedNodes,
    layout: {
      rootId,
      nodes: {
        [rootId]: { children: rootChildren },
        ...Object.fromEntries(
          rootChildren.map((id) => [id, { children: [] }]),
        ),
      },
    },
    metadata: {
      createdAt: model.scannedAt,
      updatedAt: now,
      sourceModelId: model.modelId,
    },
  };

  const fidelityReport = computeFidelityReport(
    lossPoints,
    model.modelId,
    "pipeline",
  );

  return { document, fidelityReport, excludedNodeIds };
}
