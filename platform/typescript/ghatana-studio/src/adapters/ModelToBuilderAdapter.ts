/**
 * @fileoverview ModelToBuilderAdapter — projects a LogicalArtifactModel into
 * a canonical BuilderDocument via @ghatana/ui-builder.
 *
 * This adapter is the correct integration point for combining
 * @ghatana/artifact-compiler-ts (projection) with @ghatana/ui-builder
 * (document model). The compiler package deliberately avoids a hard
 * dependency on ui-builder; this adapter bridges the gap at the product layer.
 *
 * @doc.type module
 * @doc.purpose Bridge LogicalArtifactModel → canonical BuilderDocument
 * @doc.layer studio
 * @doc.pattern Adapter
 */

import { projectToBuilder } from '@ghatana/artifact-compiler-ts';
import { createBuilderDocument, parseNodeId, parseNodeIdArray } from '@ghatana/ui-builder';
import type { LogicalArtifactModel } from '@ghatana/artifact-contracts';
import type { ProjectedBuilderDocument } from '@ghatana/artifact-compiler-ts';
import type { BuilderDocument, ComponentInstance } from '@ghatana/ui-builder';

// ============================================================================
// ADAPTER
// ============================================================================

/**
 * Projects a LogicalArtifactModel into a canonical BuilderDocument.
 *
 * Uses `projectToBuilder` from the artifact compiler to extract the component
 * structure, then materialises the result as a real BuilderDocument via
 * `createBuilderDocument` + `insertNode`.
 *
 * Only component, page, and layout nodes are included. Hooks, utilities, and
 * other artifact kinds are excluded.
 *
 * @doc.type function
 * @doc.purpose Convert LogicalArtifactModel to BuilderDocument
 * @doc.layer studio
 * @doc.pattern Adapter
 */
export function projectModelToBuilderDocument(
  model: LogicalArtifactModel,
): BuilderDocument {
  const { document: projected } = projectToBuilder(model, {
    includeKinds: ['component', 'page', 'layout'],
  });

  return materializeProjectedBuilderDocument(projected, {
    owner: model.modelId,
    description: model.label,
  });
}

export interface MaterializeProjectedBuilderDocumentOptions {
  readonly owner: string;
  readonly description?: string;
}

export function materializeProjectedBuilderDocument(
  projected: ProjectedBuilderDocument,
  options: MaterializeProjectedBuilderDocumentOptions,
): BuilderDocument {
  const knownProjectedIds = new Set(Object.keys(projected.nodes));
  const nodes: Record<string, ComponentInstance> = {};
  const diagnostics: string[] = [];

  // Preserve projected node IDs so validated slot and root references remain coherent.
  for (const instance of Object.values(projected.nodes)) {
    const nodeId = parseNodeId(instance.id, knownProjectedIds);
    if (nodeId === null) {
      diagnostics.push(`Dropped projected node with invalid id "${String(instance.id)}".`);
      continue;
    }

    const slots: ComponentInstance['slots'] = {};
    for (const [slotName, rawSlotIds] of Object.entries(instance.slots)) {
      const parsed = parseNodeIdArray(rawSlotIds, knownProjectedIds);
      slots[slotName] = parsed.nodeIds;
      for (const issue of parsed.issues) {
        diagnostics.push(
          `Dropped invalid slot reference "${String(issue.value)}" from "${instance.id}.${slotName}": ${issue.reason}.`,
        );
      }
    }

    nodes[nodeId] = {
      id: nodeId,
      contractName: instance.contractName,
      props: instance.props,
      slots,
      bindings: [],
      metadata: {
        name: instance.metadata.name,
        locked: instance.metadata.locked,
        hidden: instance.metadata.hidden,
      },
    };
  }

  const rootChildren = projected.layout.nodes[projected.layout.rootId]?.children ?? [];
  const rootNodeResult = parseNodeIdArray(rootChildren, knownProjectedIds);
  for (const issue of rootNodeResult.issues) {
    diagnostics.push(`Dropped invalid root node reference "${String(issue.value)}": ${issue.reason}.`);
  }

  return createBuilderDocument(options.owner, {
    nodes,
    rootNodes: rootNodeResult.nodeIds,
    metadata: {
      description: options.description,
      artifactProjectionDiagnostics: diagnostics,
    },
  });
}
