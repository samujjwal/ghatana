import {
  sanitizePrimitiveToken,
  type PrimitiveMetadata,
  type PrimitivePrivacyLevel,
  type PrimitiveProps,
} from './primitives';
import type { PrimitiveFeature, PrimitiveStateBag } from './composition';

export type PlatformGeneratorTarget =
  | 'react'
  | 'html'
  | 'web-components'
  | 'react-native'
  | 'flutter'
  | 'swiftui'
  | 'jetpack-compose';

export interface PlatformCapabilityHints {
  interactive: boolean;
  collection: boolean;
  virtualizable: boolean;
  async: boolean;
  privacy: PrimitivePrivacyLevel;
  optimizedFor: readonly string[];
}

export interface PlatformSemanticProps {
  role?: string;
  tabIndex?: number;
  title?: string;
  id?: string;
  eventNames: readonly string[];
}

export interface SerializedPrimitiveProps {
  attributes: Record<string, string | number | boolean>;
  style: Record<string, string | number>;
  className?: string;
  semantics: PlatformSemanticProps;
}

export interface PlatformSlotPlan {
  name: string;
  visible: boolean;
  state: PrimitiveStateBag;
  features: readonly PrimitiveFeature[];
  attributes: Record<string, string | number | boolean>;
  style: Record<string, string | number>;
  className?: string;
  semantics: PlatformSemanticProps;
}

export interface PlatformRenderPlan {
  component: string;
  signature: string;
  metadata: PrimitiveMetadata;
  state: PrimitiveStateBag;
  features: readonly PrimitiveFeature[];
  targets: readonly PlatformGeneratorTarget[];
  capabilities: PlatformCapabilityHints;
  root: PlatformSlotPlan;
  slots: Readonly<Record<string, PlatformSlotPlan>>;
}

export interface CreatePlatformRenderPlanOptions {
  metadata: PrimitiveMetadata;
  state: PrimitiveStateBag;
  features: readonly PrimitiveFeature[];
  signature: string;
  targets?: Array<PlatformGeneratorTarget | undefined>;
  capabilityHints?: Partial<PlatformCapabilityHints>;
  rootProps?: PrimitiveProps;
  slots: Readonly<Record<string, { visible: boolean; state: PrimitiveStateBag; props?: PrimitiveProps }>>;
}

function isSerializableAttributeValue(value: unknown): value is string | number | boolean {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean';
}

function isSerializableStyleValue(value: unknown): value is string | number {
  return typeof value === 'string' || typeof value === 'number';
}

function normalizeEventName(key: string): string | undefined {
  if (!/^on[A-Z]/.test(key)) return undefined;
  const raw = key.slice(2);
  if (!raw) return undefined;
  return sanitizePrimitiveToken(raw);
}

export function serializePrimitiveProps(props?: PrimitiveProps): SerializedPrimitiveProps {
  const attributes: Record<string, string | number | boolean> = {};
  const style: Record<string, string | number> = {};
  const eventNames: string[] = [];
  let className: string | undefined;
  let role: string | undefined;
  let tabIndex: number | undefined;
  let title: string | undefined;
  let id: string | undefined;

  if (!props) {
    return {
      attributes,
      style,
      semantics: { eventNames },
    };
  }

  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null) continue;

    if (key === 'className' && typeof value === 'string') {
      className = value;
      continue;
    }

    if (key === 'style' && typeof value === 'object') {
      for (const [styleKey, styleValue] of Object.entries(value as Record<string, unknown>)) {
        if (isSerializableStyleValue(styleValue)) {
          style[styleKey] = styleValue;
        }
      }
      continue;
    }

    if (key === 'role' && typeof value === 'string') {
      role = value;
      attributes.role = value;
      continue;
    }

    if (key === 'tabIndex' && typeof value === 'number') {
      tabIndex = value;
      attributes.tabIndex = value;
      continue;
    }

    if (key === 'title' && typeof value === 'string') {
      title = value;
      attributes.title = value;
      continue;
    }

    if (key === 'id' && typeof value === 'string') {
      id = value;
      attributes.id = value;
      continue;
    }

    const eventName = typeof value === 'function' ? normalizeEventName(key) : undefined;
    if (eventName) {
      eventNames.push(eventName);
      continue;
    }

    if (isSerializableAttributeValue(value)) {
      attributes[key] = value;
    }
  }

  return {
    attributes,
    style,
    className,
    semantics: {
      role,
      tabIndex,
      title,
      id,
      eventNames: Array.from(new Set(eventNames)),
    },
  };
}

function inferCapabilities(
  metadata: PrimitiveMetadata,
  state: PrimitiveStateBag,
  features: readonly PrimitiveFeature[],
  rootProps: PrimitiveProps | undefined,
  hints: Partial<PlatformCapabilityHints> | undefined,
  slots: Readonly<Record<string, { visible: boolean; state: PrimitiveStateBag; props?: PrimitiveProps }>>,
): PlatformCapabilityHints {
  const serializedRoot = serializePrimitiveProps(rootProps);
  const slotStates = Object.values(slots).map((slot) => slot.state);
  const featureSet = new Set(features);
  const hasVirtualizedState = slotStates.some((slotState) => slotState.virtualized === true);

  return {
    interactive:
      hints?.interactive ??
      Boolean(serializedRoot.semantics.role || featureSet.has('interactive') || featureSet.has('pressable')),
    collection:
      hints?.collection ??
      Boolean(featureSet.has('collection') || typeof state.itemCount === 'number'),
    virtualizable:
      hints?.virtualizable ??
      Boolean(featureSet.has('virtualized') || hasVirtualizedState),
    async:
      hints?.async ??
      Boolean(featureSet.has('async') || state.loading === true || state.pending === true),
    privacy: hints?.privacy ?? metadata.privacy ?? 'public',
    optimizedFor: hints?.optimizedFor ?? [
      'render-plan',
      'codegen',
      'builder-handoff',
      'target-adaptation',
    ],
  };
}

export function createPlatformRenderPlan({
  metadata,
  state,
  features,
  signature,
  targets,
  capabilityHints,
  rootProps,
  slots,
}: CreatePlatformRenderPlanOptions): PlatformRenderPlan {
  const normalizedTargets: PlatformGeneratorTarget[] = Array.from(
    new Set(
      (targets ?? ['react'])
        .map((target) => sanitizePrimitiveToken(target) as PlatformGeneratorTarget | undefined)
        .filter((target): target is PlatformGeneratorTarget => Boolean(target))
    )
  );

  const root = serializePrimitiveProps(rootProps);
  const slotPlans = Object.fromEntries(
    Object.entries(slots).map(([slotName, slot]) => {
      const serialized = serializePrimitiveProps(slot.props);
      return [
        slotName,
        {
          name: slotName,
          visible: slot.visible,
          state: slot.state,
          features,
          attributes: serialized.attributes,
          style: serialized.style,
          className: serialized.className,
          semantics: serialized.semantics,
        } satisfies PlatformSlotPlan,
      ];
    })
  );

  return Object.freeze({
    component: metadata.component,
    signature,
    metadata,
    state,
    features,
    targets: normalizedTargets.length > 0 ? normalizedTargets : (['react'] as PlatformGeneratorTarget[]),
    capabilities: inferCapabilities(metadata, state, features, rootProps, capabilityHints, slots),
    root: {
      name: 'root',
      visible: true,
      state,
      features,
      attributes: root.attributes,
      style: root.style,
      className: root.className,
      semantics: root.semantics,
    },
    slots: slotPlans,
  });
}
