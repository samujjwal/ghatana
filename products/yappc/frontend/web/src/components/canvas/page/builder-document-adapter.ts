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
} from '@ghatana/ui-builder';
import { createDocumentId } from '@ghatana/ui-builder';

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

  components.forEach((comp) => {
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
    id: createDocumentId(),
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
  // Separate the structural id/type fields from the props payload to avoid
  // aliasing the original comp object inside the ComponentInstance.
  const { id: _id, type: _type, label, ...restProps } = comp as Record<string, unknown>;
  return {
    id: comp.id as NodeId,
    contractName: comp.type,
    props: { ...restProps },
    slots: {},
    bindings: [],
    metadata: {
      name: typeof label === 'string' ? label : undefined,
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
    // Spread into a new object — do NOT mutate instance.props
    const comp = {
      ...(instance.props as Omit<ComponentData, 'id' | 'type'>),
      id: instance.id,
      type: instance.contractName,
    } as ComponentData;
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
