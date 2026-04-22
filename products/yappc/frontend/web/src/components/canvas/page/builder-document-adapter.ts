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
  DesignSystemModel,
} from '@ghatana/ui-builder';
import { createDocumentId } from '@ghatana/ui-builder';

import type { ComponentData } from './schemas';

const LEGACY_DOCUMENT_TAG = 'legacy-component-data-v1';
const LEGACY_DOCUMENT_AUTHOR = 'yappc-page-designer-adapter';

interface BuilderDocumentAdapterOptions {
  readonly documentName?: string;
  readonly existingDocument?: BuilderDocument;
}

function createDesignSystemModel(existingDocument?: BuilderDocument): DesignSystemModel {
  return existingDocument?.designSystem ?? {
    id: 'ghatana-ds-v1',
    name: 'Ghatana Design System',
    version: '1.0.0',
    tokenSetIds: [],
    componentContracts: [],
    themeId: 'default',
  };
}

function toComponentProps(comp: ComponentData): Record<string, unknown> {
  const { id: _id, type: _type, ...props } = comp;
  return { ...props };
}

/**
 * Convert ComponentData array to BuilderDocument format.
 */
export function componentDataToBuilderDocument(
  components: ComponentData[],
  options: BuilderDocumentAdapterOptions = {},
): BuilderDocument {
  const existingDocument = options.existingDocument;
  const nodes = new Map<NodeId, ComponentInstance>();
  const rootNodes: NodeId[] = [];
  const createdAt = existingDocument?.metadata.createdAt ?? new Date().toISOString();
  const updatedAt = new Date().toISOString();
  const tags = new Set(existingDocument?.metadata.tags ?? []);

  tags.add(LEGACY_DOCUMENT_TAG);

  components.forEach((comp) => {
    const nodeId = comp.id as NodeId;
    const componentInstance = componentDataToInstance(
      comp,
      existingDocument?.nodes.get(nodeId),
    );
    nodes.set(nodeId, componentInstance);
    rootNodes.push(nodeId);
  });

  return {
    id: existingDocument?.id ?? createDocumentId(),
    version: existingDocument?.version ?? '1',
    name: options.documentName ?? existingDocument?.name ?? 'Untitled Page',
    designSystem: createDesignSystemModel(existingDocument),
    rootNodes,
    nodes,
    metadata: {
      ...existingDocument?.metadata,
      createdAt,
      updatedAt,
      author: existingDocument?.metadata.author ?? LEGACY_DOCUMENT_AUTHOR,
      tags: Array.from(tags),
    },
  };
}

/**
 * Convert ComponentData into a BuilderDocument insert payload.
 */
export function componentDataToInsertableInstance(
  comp: ComponentData,
): Omit<ComponentInstance, 'id'> {
  return {
    contractName: comp.type,
    props: toComponentProps(comp),
    slots: {},
    bindings: [],
    metadata: {
      name: typeof comp.label === 'string' ? comp.label : undefined,
      provenance: {
        source: LEGACY_DOCUMENT_AUTHOR,
        sourceId: comp.id,
        importedAt: new Date().toISOString(),
      },
    },
  };
}

/**
 * Convert ComponentData into a prop payload for BuilderDocument operations.
 */
export function componentDataToBuilderProps(
  comp: ComponentData,
): Record<string, unknown> {
  return toComponentProps(comp);
}

/**
 * Convert single ComponentData to ComponentInstance.
 */
function componentDataToInstance(
  comp: ComponentData,
  existingInstance?: ComponentInstance,
): ComponentInstance {
  return {
    id: comp.id as NodeId,
    contractName: comp.type,
    props: toComponentProps(comp),
    slots: existingInstance?.slots ?? {},
    bindings: existingInstance?.bindings ?? [],
    metadata: {
      ...existingInstance?.metadata,
      name: typeof comp.label === 'string' ? comp.label : existingInstance?.metadata.name,
      provenance: existingInstance?.metadata.provenance ?? {
        source: LEGACY_DOCUMENT_AUTHOR,
        sourceId: comp.id,
        importedAt: new Date().toISOString(),
      },
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

  const orderedIds = [
    ...document.rootNodes,
    ...Array.from(document.nodes.keys()).filter((nodeId) => !document.rootNodes.includes(nodeId)),
  ];

  orderedIds.forEach((nodeId) => {
    const instance = document.nodes.get(nodeId);
    if (!instance) {
      return;
    }

    // Spread into a new object — do NOT mutate instance.props
    const comp = {
      ...(instance.props as Omit<ComponentData, 'id' | 'type'>),
      id: instance.id,
      type: instance.contractName,
      ...(typeof instance.metadata.name === 'string' && !('label' in instance.props)
        ? { label: instance.metadata.name }
        : {}),
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
