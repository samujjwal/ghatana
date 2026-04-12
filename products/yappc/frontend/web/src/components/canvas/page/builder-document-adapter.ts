/**
 * Adapter for migrating YAPPC PageDesigner to use BuilderDocument.
 * 
 * Converts local ComponentData schema to platform BuilderDocument format.
 * This allows gradual migration while maintaining backward compatibility.
 *
 * @doc.type module
 * @doc.purpose Migration adapter for BuilderDocument adoption
 * @doc.layer product
 */

import type {
  BuilderDocument,
  ComponentInstance,
  NodeId,
  DocumentId,
  DesignSystemModel,
} from '@ghatana/ui-builder/core';

import type { ComponentData } from './schemas';

/**
 * Convert ComponentData array to BuilderDocument format.
 */
export function componentDataToBuilderDocument(
  components: ComponentData[],
  documentName: string = 'Untitled Page'
): BuilderDocument {
  const nodes = new Map<NodeId, ComponentInstance>();
  const rootNodes: NodeId[] = [];

  components.forEach((comp, index) => {
    const nodeId = comp.id as NodeId;
    const componentInstance = componentDataToInstance(comp);
    nodes.set(nodeId, componentInstance);
    rootNodes.push(nodeId);
  });

  const designSystem: DesignSystemModel = {
    id: 'ghatana-ds-v1',
    name: 'Ghatana Design System',
    version: '1.0.0',
    tokenSetIds: [],
    componentContracts: [], // Will be populated from @ghatana/design-system
    themeId: 'default',
  };

  return {
    id: crypto.randomUUID() as DocumentId,
    version: '1',
    name: documentName,
    designSystem,
    rootNodes,
    nodes,
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  };
}

/**
 * Convert single ComponentData to ComponentInstance.
 */
function componentDataToInstance(comp: ComponentData): ComponentInstance {
  return {
    id: comp.id as NodeId,
    contractName: comp.type, // Map type to contract name
    props: comp as Record<string, unknown>, // Pass all props
    slots: {},
    bindings: [],
    metadata: {
      name: comp.label,
    },
  };
}

/**
 * Convert BuilderDocument back to ComponentData array.
 * Used for backward compatibility during migration.
 */
export function builderDocumentToComponentData(
  document: BuilderDocument
): ComponentData[] {
  const components: ComponentData[] = [];

  document.nodes.forEach((instance) => {
    const comp = instance.props as ComponentData;
    comp.id = instance.id;
    comp.type = instance.contractName;
    components.push(comp);
  });

  return components;
}

/**
 * Check if data is in BuilderDocument format.
 */
export function isBuilderDocument(data: unknown): data is BuilderDocument {
  if (!data || typeof data !== 'object') return false;
  const doc = data as Record<string, unknown>;
  return (
    typeof doc.id === 'string' &&
    typeof doc.version === 'string' &&
    typeof doc.name === 'string' &&
    typeof doc.designSystem === 'object' &&
    Array.isArray(doc.rootNodes) &&
    doc.nodes instanceof Map
  );
}
