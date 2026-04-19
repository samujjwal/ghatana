import type {
  BuilderComponentManifest,
  BuilderComponentSlotManifest,
  BuilderSlotExposure,
  ComponentContract,
} from '@ghatana/ds-schema';

import type { BuilderDocument, ComponentInstance, NodeId, Binding } from './types';

export type BuilderPlatformTarget =
  | 'react'
  | 'html'
  | 'web-components'
  | 'react-native'
  | 'swiftui'
  | 'jetpack-compose'
  | 'figma';

export interface BuilderPlatformSemantics {
  readonly role?: string;
  readonly tabIndex?: number;
  readonly title?: string;
  readonly id?: string;
  readonly eventNames: readonly string[];
}

export interface BuilderSerializedProps {
  readonly attributes: Readonly<Record<string, string | number | boolean>>;
  readonly style: Readonly<Record<string, string | number>>;
  readonly className?: string;
  readonly semantics: BuilderPlatformSemantics;
}

export interface BuilderPlatformSlotPlan {
  readonly name: string;
  readonly childIds: readonly NodeId[];
  readonly allowsMultiple: boolean;
  readonly required: boolean;
  readonly features: readonly string[];
  readonly exposure: BuilderSlotExposure;
  readonly semantics: BuilderPlatformSemantics;
}

export interface BuilderPlatformNodePlan {
  readonly nodeId: NodeId;
  readonly contractName: string;
  readonly signature: string;
  readonly targets: readonly BuilderPlatformTarget[];
  readonly features: readonly string[];
  readonly dataClassification?: string;
  readonly reviewRequired: boolean;
  readonly props: BuilderSerializedProps;
  readonly slots: readonly BuilderPlatformSlotPlan[];
}

export interface BuilderPlatformDocumentPlan {
  readonly documentId: string;
  readonly targets: readonly BuilderPlatformTarget[];
  readonly nodes: ReadonlyMap<NodeId, BuilderPlatformNodePlan>;
}

export type ContractLookup =
  | ReadonlyMap<string, ComponentContract>
  | readonly ComponentContract[];

export type ManifestLookup =
  | ReadonlyMap<string, BuilderComponentManifest>
  | readonly BuilderComponentManifest[];

function toContractMap(contracts?: ContractLookup): ReadonlyMap<string, ComponentContract> {
  if (!contracts) return new Map();
  if (contracts instanceof Map) return contracts;
  if (Array.isArray(contracts)) {
    return new Map(contracts.map((contract) => [contract.name, contract]));
  }
  return new Map();
}

function toManifestMap(manifests?: ManifestLookup): ReadonlyMap<string, BuilderComponentManifest> {
  if (!manifests) return new Map();
  if (manifests instanceof Map) return manifests;
  if (Array.isArray(manifests)) {
    return new Map(manifests.map((manifest) => [manifest.name, manifest]));
  }
  return new Map();
}

function normalizeEventName(name: string): string {
  return name.trim().replace(/^on/, '').replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
}

function getManifestSlot(
  manifest: BuilderComponentManifest | undefined,
  slotName: string,
): BuilderComponentSlotManifest | undefined {
  return manifest?.slots.find((slot) => slot.name === slotName);
}

function inferSlotExposure(
  slotName: string,
  manifestSlot?: BuilderComponentSlotManifest,
): BuilderSlotExposure {
  return manifestSlot?.exposure ?? (slotName === 'default' ? 'children' : 'prop');
}

function collectSlotNames(
  instance: ComponentInstance,
  contract?: ComponentContract,
  manifest?: BuilderComponentManifest,
): string[] {
  const names = new Set<string>();

  for (const slotName of Object.keys(instance.slots)) {
    names.add(slotName);
  }
  for (const slot of contract?.slots ?? []) {
    names.add(slot.name);
  }
  for (const slot of manifest?.slots ?? []) {
    names.add(slot.name);
  }

  return Array.from(names);
}

function serializeInstanceProps(
  instance: ComponentInstance,
  contract?: ComponentContract,
  manifest?: BuilderComponentManifest,
): BuilderSerializedProps {
  const attributes: Record<string, string | number | boolean> = {};
  const style: Record<string, string | number> = {};
  let className: string | undefined;

  for (const [key, value] of Object.entries(instance.props)) {
    if (value === undefined || value === null) continue;

    if (key === 'className' && typeof value === 'string') {
      className = value;
      continue;
    }

    if (key === 'style' && typeof value === 'object') {
      for (const [styleKey, styleValue] of Object.entries(value as Record<string, unknown>)) {
        if (typeof styleValue === 'string' || typeof styleValue === 'number') {
          style[styleKey] = styleValue;
        }
      }
      continue;
    }

    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      attributes[key] = value;
    }
  }

  const boundEvents = instance.bindings
    .filter((binding) => binding.type === 'event')
    .map((binding) => normalizeEventName(binding.target));

  const declaredEvents = (contract?.events ?? []).map((event) => normalizeEventName(event.name));

  return {
    attributes,
    style,
    className,
    semantics: {
      role: typeof instance.props.role === 'string'
        ? instance.props.role
        : (manifest?.semantics.role ?? contract?.metadata.a11y?.role),
      tabIndex: typeof instance.props.tabIndex === 'number' ? instance.props.tabIndex : undefined,
      title: typeof instance.props.title === 'string' ? instance.props.title : undefined,
      id: typeof instance.props.id === 'string' ? instance.props.id : undefined,
      eventNames: Array.from(new Set([...boundEvents, ...declaredEvents, ...(manifest?.semantics.eventNames ?? [])])),
    },
  };
}

function inferTargets(contract?: ComponentContract, manifest?: BuilderComponentManifest): BuilderPlatformTarget[] {
  if (manifest?.targets?.length) {
    return [...manifest.targets];
  }
  if (!contract) return ['react'];

  const targets = new Set<BuilderPlatformTarget>();
  const platforms = contract.metadata.platforms ?? ['web'];

  for (const platform of platforms) {
    switch (platform) {
      case 'web':
        targets.add('react');
        targets.add('html');
        targets.add('web-components');
        break;
      case 'ios':
        targets.add('swiftui');
        targets.add('react-native');
        break;
      case 'android':
        targets.add('jetpack-compose');
        targets.add('react-native');
        break;
      case 'figma':
        targets.add('figma');
        break;
      default:
        targets.add('react');
        break;
    }
  }

  return Array.from(targets);
}

function inferFeatures(instance: ComponentInstance, contract?: ComponentContract, manifest?: BuilderComponentManifest): string[] {
  const features = new Set<string>();

  if ((contract?.slots.length ?? 0) > 0 || Object.keys(instance.slots).length > 0) {
    features.add('slots');
  }
  if (Object.values(instance.slots).some((children) => children.length > 0)) {
    features.add('composite');
  }
  if ((contract?.builder?.canvas?.container ?? false) || Object.keys(instance.slots).length > 0) {
    features.add('container');
  }
  if (instance.bindings.some((binding) => binding.type === 'event') || (contract?.events.length ?? 0) > 0) {
    features.add('interactive');
  }
  if ((instance.metadata.stateVariants?.length ?? 0) > 0) {
    features.add('stateful');
  }
  if ((instance.metadata.actions?.length ?? 0) > 0) {
    features.add('actionable');
  }
  if ((contract?.metadata.a11y?.keyboardNavigation ?? false) || typeof instance.props.tabIndex === 'number') {
    features.add('keyboard-accessible');
  }
  for (const feature of manifest?.features ?? []) {
    features.add(feature);
  }

  return Array.from(features);
}

function createSignature(
  instance: ComponentInstance,
  props: BuilderSerializedProps,
  targets: readonly BuilderPlatformTarget[],
  features: readonly string[],
): string {
  return [
    instance.contractName,
    targets.join(','),
    features.join(','),
    props.semantics.eventNames.join(','),
    Object.keys(instance.slots).sort().join(','),
  ]
    .filter(Boolean)
    .join('|');
}

export function projectInstanceToPlatformPlan(
  instance: ComponentInstance,
  contract?: ComponentContract,
  manifest?: BuilderComponentManifest,
): BuilderPlatformNodePlan {
  const props = serializeInstanceProps(instance, contract, manifest);
  const targets = inferTargets(contract, manifest);
  const features = inferFeatures(instance, contract, manifest);

  return {
    nodeId: instance.id,
    contractName: instance.contractName,
    signature: createSignature(instance, props, targets, features),
    targets,
    features,
    dataClassification:
      instance.metadata.dataClassification ?? manifest?.dataClassification,
    reviewRequired:
      instance.metadata.reviewStatus?.status === 'requires-manual' || manifest?.reviewRequired === true,
    props,
    slots: collectSlotNames(instance, contract, manifest).map((slotName) => {
      const childIds = instance.slots[slotName] ?? [];
      const manifestSlot = getManifestSlot(manifest, slotName);
      const slotContract = contract?.slots.find((slot) => slot.name === slotName);
      return {
        name: slotName,
        childIds,
        allowsMultiple: manifestSlot?.allowsMultiple ?? ((slotContract?.maxChildren ?? 2) > 1),
        required:
          manifestSlot?.required ??
          slotContract?.builderMetadata?.required ??
          Boolean((slotContract?.minChildren ?? 0) > 0),
        features: manifestSlot?.features ?? [],
        exposure: inferSlotExposure(slotName, manifestSlot),
        semantics: {
          role: manifestSlot?.semantics.role,
          eventNames: manifestSlot?.semantics.eventNames ?? [],
        },
      };
    }),
  };
}

export function projectDocumentToPlatformPlan(
  document: BuilderDocument,
  contracts?: ContractLookup,
  manifests?: ManifestLookup,
): BuilderPlatformDocumentPlan {
  const contractMap = toContractMap(contracts ?? document.designSystem.componentContracts);
  const manifestMap = toManifestMap(manifests);
  const plans = new Map<NodeId, BuilderPlatformNodePlan>();
  const targets = new Set<BuilderPlatformTarget>();

  for (const [nodeId, instance] of document.nodes) {
    const plan = projectInstanceToPlatformPlan(
      instance,
      contractMap.get(instance.contractName),
      manifestMap.get(instance.contractName),
    );
    plans.set(nodeId, plan);
    for (const target of plan.targets) {
      targets.add(target);
    }
  }

  return {
    documentId: document.id,
    targets: Array.from(targets),
    nodes: plans,
  };
}
