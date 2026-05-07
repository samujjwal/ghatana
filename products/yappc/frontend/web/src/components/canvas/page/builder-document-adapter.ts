/**
 * Adapter for migrating YAPPC PageDesigner to use BuilderDocument.
 *
 * Converts the legacy local ComponentData schema into the canonical
 * registry-backed BuilderDocument format. This file remains the boundary for
 * old page-designer fixtures, but the produced document now uses registry
 * contract names and persisted-safe metadata.
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

import type {
  BoxData,
  ButtonData,
  CardData,
  ComponentData,
  TextFieldData,
  TypographyData,
} from './schemas';
import {
  getContractMap,
  migrateRegistryContractInstance,
  toLegacyComponentType,
} from './registry';

const LEGACY_DOCUMENT_TAG = 'legacy-component-data-v1';
const LEGACY_DOCUMENT_AUTHOR = 'yappc-page-designer-adapter';
const DEFAULT_ADAPTER_TRUST_LEVEL =
  'trusted-local' as NonNullable<BuilderDocument['metadata']['trustLevel']>;

const CANONICAL_BUTTON_VARIANTS = {
  solid: 'contained',
  outline: 'outlined',
  ghost: 'text',
} as const satisfies Record<string, ButtonData['variant']>;

const CANONICAL_BUTTON_SIZES = {
  sm: 'small',
  md: 'medium',
  lg: 'large',
} as const satisfies Record<string, ButtonData['size']>;

const CANONICAL_BUTTON_COLORS = {
  danger: 'error',
} as const satisfies Partial<Record<string, ButtonData['color']>>;

function mapRecordValue<TValue>(
  mapping: Readonly<Record<string, TValue>>,
  value: unknown
): TValue | undefined {
  return typeof value === 'string' ? mapping[value] : undefined;
}

interface BuilderDocumentAdapterOptions {
  readonly documentName?: string;
  readonly existingDocument?: BuilderDocument;
}

function createDesignSystemModel(existingDocument?: BuilderDocument): DesignSystemModel {
  const componentContracts = [...getContractMap().values()];

  return existingDocument?.designSystem ?? {
    id: 'ghatana-ds-v1',
    name: 'Ghatana Design System',
    version: '1.0.0',
    tokenSetIds: [],
    componentContracts,
    themeId: 'default',
  };
}

function toComponentProps(comp: ComponentData): Record<string, unknown> {
  switch (comp.type) {
    case 'button':
      return {
        variant: comp.variant,
        color: comp.color,
        size: comp.size,
        disabled: comp.disabled,
        fullWidth: comp.fullWidth,
        children: typeof comp.text === 'string' ? comp.text : comp.label,
      };
    case 'card':
      return {
        elevation: comp.elevation,
        title: comp.title,
        subtitle: comp.subtitle,
        content: comp.content,
        showActions: comp.showActions,
        label: comp.label,
      };
    case 'textfield':
      return {
        label: comp.label,
        placeholder: comp.placeholder,
        variant: comp.variant,
        size: comp.size,
        required: comp.required,
        disabled: comp.disabled,
        fullWidth: comp.fullWidth,
        multiline: comp.multiline,
        rows: comp.rows,
      };
    case 'typography':
      return {
        variant: comp.variant,
        color: comp.color,
        align: comp.align,
        children: comp.text,
        label: comp.label,
      };
    case 'box':
      return {
        label: comp.label,
        padding: comp.padding,
        margin: comp.margin,
        backgroundColor: comp.backgroundColor,
        borderRadius: comp.borderRadius,
        display: comp.display,
        flexDirection: comp.flexDirection,
        justifyContent: comp.justifyContent,
        alignItems: comp.alignItems,
      };
    default:
      return {};
  }
}

function createProvenanceRecord(comp: ComponentData): NonNullable<ComponentInstance['metadata']['provenance']> {
  const now = new Date().toISOString();
  return {
    source: LEGACY_DOCUMENT_AUTHOR,
    author: LEGACY_DOCUMENT_AUTHOR,
    generatorVersion: 'legacy-component-data-v1',
    triggeredBy: 'explicit',
    createdAt: now,
    modifiedAt: now,
    migrationLineage: [comp.id],
  };
}

function mapInstanceToComponentData(instance: ComponentInstance): ComponentData {
  const type = toLegacyComponentType(instance.contractName);

  switch (type) {
    case 'button':
      return {
        id: instance.id,
        type: 'button',
        label: typeof instance.metadata.name === 'string' ? instance.metadata.name : undefined,
        text: typeof instance.props.children === 'string' ? instance.props.children : 'Button',
        variant:
          mapRecordValue(CANONICAL_BUTTON_VARIANTS, instance.props.variant) ??
          (instance.props.variant as ButtonData['variant']) ??
          'contained',
        color:
          mapRecordValue(CANONICAL_BUTTON_COLORS, instance.props.color) ??
          (instance.props.color as ButtonData['color']) ??
          'primary',
        size:
          mapRecordValue(CANONICAL_BUTTON_SIZES, instance.props.size) ??
          (instance.props.size as ButtonData['size']) ??
          'medium',
        disabled: Boolean(instance.props.disabled),
        fullWidth: Boolean(instance.props.fullWidth),
      };
    case 'card':
      return {
        id: instance.id,
        type: 'card',
        label:
          typeof instance.props.label === 'string'
            ? instance.props.label
            : typeof instance.metadata.name === 'string'
              ? instance.metadata.name
              : undefined,
        elevation: typeof instance.props.elevation === 'number' ? instance.props.elevation : 2,
        title: instance.props.title as CardData['title'],
        subtitle: instance.props.subtitle as CardData['subtitle'],
        content: instance.props.content as CardData['content'],
        showActions: Boolean(instance.props.showActions),
      };
    case 'textfield':
      return {
        id: instance.id,
        type: 'textfield',
        label: instance.props.label as TextFieldData['label'],
        placeholder: instance.props.placeholder as TextFieldData['placeholder'],
        variant: (instance.props.variant as TextFieldData['variant']) ?? 'outlined',
        size: (instance.props.size as TextFieldData['size']) ?? 'medium',
        required: Boolean(instance.props.required),
        disabled: Boolean(instance.props.disabled),
        fullWidth: Boolean(instance.props.fullWidth),
        multiline: Boolean(instance.props.multiline),
        rows: typeof instance.props.rows === 'number' ? instance.props.rows : 1,
      };
    case 'typography':
      return {
        id: instance.id,
        type: 'typography',
        label:
          typeof instance.props.label === 'string'
            ? instance.props.label
            : typeof instance.metadata.name === 'string'
              ? instance.metadata.name
              : undefined,
        text: typeof instance.props.children === 'string' ? instance.props.children : 'Typography',
        variant: (instance.props.variant as TypographyData['variant']) ?? 'body1',
        color: instance.props.color as TypographyData['color'],
        align: (instance.props.align as TypographyData['align']) ?? 'left',
      };
    case 'box':
      return {
        id: instance.id,
        type: 'box',
        label:
          typeof instance.props.label === 'string'
            ? instance.props.label
            : typeof instance.metadata.name === 'string'
              ? instance.metadata.name
              : undefined,
        padding: typeof instance.props.padding === 'number' ? instance.props.padding : 2,
        margin: typeof instance.props.margin === 'number' ? instance.props.margin : 0,
        backgroundColor: instance.props.backgroundColor as BoxData['backgroundColor'],
        borderRadius:
          typeof instance.props.borderRadius === 'number' ? instance.props.borderRadius : 0,
        display: (instance.props.display as BoxData['display']) ?? 'block',
        flexDirection: instance.props.flexDirection as BoxData['flexDirection'],
        justifyContent: instance.props.justifyContent as BoxData['justifyContent'],
        alignItems: instance.props.alignItems as BoxData['alignItems'],
      };
    default:
      return {
        id: instance.id,
        type: 'box',
        padding: 2,
        margin: 0,
        borderRadius: 0,
        display: 'block',
      };
  }
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
      dataClassification: existingDocument?.metadata.dataClassification ?? 'INTERNAL',
      trustLevel: existingDocument?.metadata.trustLevel ?? DEFAULT_ADAPTER_TRUST_LEVEL,
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
  const migrated = migrateRegistryContractInstance({
    contractName: comp.type,
    props: toComponentProps(comp),
  });

  return {
    contractName: migrated.contractName,
    props: migrated.props,
    slots: {},
    bindings: [],
    metadata: {
      name: typeof comp.label === 'string' ? comp.label : undefined,
      provenance: createProvenanceRecord(comp),
    },
  };
}

/**
 * Convert ComponentData into a prop payload for BuilderDocument operations.
 */
export function componentDataToBuilderProps(
  comp: ComponentData,
): Record<string, unknown> {
  return migrateRegistryContractInstance({
    contractName: comp.type,
    props: toComponentProps(comp),
  }).props;
}

/**
 * Convert single ComponentData to ComponentInstance.
 */
function componentDataToInstance(
  comp: ComponentData,
  existingInstance?: ComponentInstance,
): ComponentInstance {
  const migrated = migrateRegistryContractInstance({
    contractName: comp.type,
    props: toComponentProps(comp),
  });

  return {
    id: comp.id as NodeId,
    contractName: migrated.contractName,
    props: migrated.props,
    slots: existingInstance?.slots ?? {},
    bindings: existingInstance?.bindings ?? [],
    metadata: {
      ...existingInstance?.metadata,
      name: typeof comp.label === 'string' ? comp.label : existingInstance?.metadata.name,
      provenance: existingInstance?.metadata.provenance ?? createProvenanceRecord(comp),
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

    components.push(mapInstanceToComponentData(instance));
  });

  return components;
}

/**
 * Check if data is in BuilderDocument format.
 */
export function isBuilderDocument(data: unknown): data is BuilderDocument {
  if (!data || typeof data !== 'object') return false;
  const doc = data as Record<string, unknown>;
  const hasNodeShape =
    doc.nodes instanceof Map ||
    (typeof doc.nodes === 'object' && doc.nodes !== null && !Array.isArray(doc.nodes));
  return (
    typeof doc.id === 'string' &&
    typeof doc.version === 'string' &&
    typeof doc.name === 'string' &&
    typeof doc.designSystem === 'object' &&
    Array.isArray(doc.rootNodes) &&
    hasNodeShape
  );
}
