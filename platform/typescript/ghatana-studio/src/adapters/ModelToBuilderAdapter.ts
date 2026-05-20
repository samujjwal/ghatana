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
import { createBuilderDocument, insertNode } from '@ghatana/ui-builder';
import type { LogicalArtifactModel } from '@ghatana/artifact-contracts';
import type { BuilderDocument } from '@ghatana/ui-builder';

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

  // Start with an empty document owned by the model ID
  let doc = createBuilderDocument(model.modelId, {
    metadata: { description: model.label },
  });

  // Insert each projected component instance into the document
  for (const instance of Object.values(projected.nodes)) {
    doc = insertNode(doc, {
      contractName: instance.contractName,
      props: instance.props as Record<string, unknown>,
      slots: Object.fromEntries(
        Object.entries(instance.slots).map(([k, v]) => [k, v as string[] as import('@ghatana/ui-builder').NodeId[]]),
      ) as Record<string, import('@ghatana/ui-builder').NodeId[]>,
      bindings: [],
      metadata: {
        name: instance.metadata.name,
        locked: instance.metadata.locked,
        hidden: instance.metadata.hidden,
      },
    });
  }

  return doc;
}
