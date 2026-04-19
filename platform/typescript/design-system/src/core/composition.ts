import * as React from 'react';

import {
  createPrimitiveAttributes,
  mergePrimitiveProps,
  sanitizePrimitiveToken,
  useComponentTelemetry,
  type PrimitiveMetadata,
  type PrimitiveProps,
} from './primitives';

export type PrimitiveStateValue = boolean | string | number | null | undefined;
export type PrimitiveStateBag = Record<string, PrimitiveStateValue>;
export type PrimitiveFeature = string;
export type PrimitiveSlotPropsMap = Record<string, PrimitiveProps | undefined>;
export type PrimitiveTelemetryHandler = ReturnType<typeof useComponentTelemetry>;

export interface ComponentBehaviorContext {
  metadata: PrimitiveMetadata;
  state: PrimitiveStateBag;
  features: PrimitiveFeature[];
  telemetry: PrimitiveTelemetryHandler;
}

type LazyValue<T> = T | ((context: ComponentBehaviorContext) => T | undefined) | undefined;

export interface ComponentBehavior {
  name: string;
  metadata?: LazyValue<Partial<PrimitiveMetadata>>;
  state?: LazyValue<PrimitiveStateBag>;
  features?: LazyValue<Array<PrimitiveFeature | undefined>>;
  rootProps?: LazyValue<PrimitiveProps>;
  slots?: Record<string, LazyValue<PrimitiveProps>>;
}

export interface UseComponentCompositionOptions {
  metadata: PrimitiveMetadata;
  state?: PrimitiveStateBag;
  features?: Array<PrimitiveFeature | undefined>;
  rootProps?: PrimitiveProps;
  slotProps?: PrimitiveSlotPropsMap;
  behaviors?: Array<ComponentBehavior | undefined>;
  telemetryEnabled?: boolean;
}

export interface UseComponentCompositionResult {
  metadata: PrimitiveMetadata;
  state: PrimitiveStateBag;
  features: PrimitiveFeature[];
  telemetry: PrimitiveTelemetryHandler;
  rootProps: PrimitiveProps;
  getSlotProps: (slot: string, overrideProps?: PrimitiveProps, slotState?: PrimitiveStateBag) => PrimitiveProps;
}

function mergeMetadata(base: PrimitiveMetadata, override?: Partial<PrimitiveMetadata>): PrimitiveMetadata {
  return {
    ...base,
    ...override,
  };
}

function resolveLazyValue<T>(value: LazyValue<T>, context: ComponentBehaviorContext): T | undefined {
  if (typeof value === 'function') {
    return (value as (ctx: ComponentBehaviorContext) => T | undefined)(context);
  }
  return value;
}

export function createStateAttributes(state: PrimitiveStateBag = {}): Record<string, string> {
  const attributes: Record<string, string> = {};
  const aggregateTokens: string[] = [];

  for (const [rawKey, rawValue] of Object.entries(state)) {
    if (rawValue === false || rawValue === null || rawValue === undefined || rawValue === '') {
      continue;
    }

    const key = sanitizePrimitiveToken(rawKey);
    if (!key) continue;

    if (rawValue === true) {
      attributes[`data-state-${key}`] = 'true';
      aggregateTokens.push(key);
      continue;
    }

    const normalizedValue = sanitizePrimitiveToken(String(rawValue));
    if (normalizedValue) {
      attributes[`data-state-${key}`] = normalizedValue;
      aggregateTokens.push(`${key}:${normalizedValue}`);
    } else {
      attributes[`data-state-${key}`] = '[redacted]';
      aggregateTokens.push(key);
    }
  }

  if (aggregateTokens.length > 0) {
    attributes['data-state'] = aggregateTokens.join(' ');
  }

  return attributes;
}

export function createFeatureAttributes(features: Array<PrimitiveFeature | undefined> = []): Record<string, string> {
  const tokens = Array.from(
    new Set(
      features
        .map((feature) => sanitizePrimitiveToken(feature))
        .filter((feature): feature is string => Boolean(feature))
    )
  );

  if (tokens.length === 0) return {};

  const attributes: Record<string, string> = {
    'data-features': tokens.join(' '),
  };

  for (const token of tokens) {
    attributes[`data-feature-${token}`] = 'true';
  }

  return attributes;
}

export function useComponentComposition(
  options: UseComponentCompositionOptions
): UseComponentCompositionResult {
  const {
    metadata: baseMetadata,
    state: baseState = {},
    features: baseFeatures = [],
    rootProps,
    slotProps = {},
    behaviors = [],
    telemetryEnabled = true,
  } = options;

  const firstPassContext = React.useMemo<ComponentBehaviorContext>(() => ({
    metadata: baseMetadata,
    state: baseState,
    features: baseFeatures.filter((feature): feature is string => Boolean(feature)),
    telemetry: () => undefined,
  }), [baseFeatures, baseMetadata, baseState]);

  const mergedMetadata = React.useMemo(() => behaviors.reduce((current, behavior) => {
    if (!behavior?.metadata) return current;
    return mergeMetadata(current, resolveLazyValue(behavior.metadata, {
      ...firstPassContext,
      metadata: current,
    }));
  }, baseMetadata), [baseMetadata, behaviors, firstPassContext]);

  const mergedState = React.useMemo(() => behaviors.reduce((current, behavior) => {
    if (!behavior?.state) return current;
    return {
      ...current,
      ...resolveLazyValue(behavior.state, {
        ...firstPassContext,
        metadata: mergedMetadata,
        state: current,
      }),
    };
  }, baseState), [baseState, behaviors, firstPassContext, mergedMetadata]);

  const mergedFeatures = React.useMemo(() => {
    const featureSet = new Set(
      baseFeatures
        .map((feature) => sanitizePrimitiveToken(feature))
        .filter((feature): feature is string => Boolean(feature))
    );

    for (const behavior of behaviors) {
      if (!behavior?.features) continue;
      const resolved = resolveLazyValue(behavior.features, {
        ...firstPassContext,
        metadata: mergedMetadata,
        state: mergedState,
        features: Array.from(featureSet),
      });
      for (const feature of resolved ?? []) {
        const normalized = sanitizePrimitiveToken(feature);
        if (normalized) featureSet.add(normalized);
      }
    }

    return Array.from(featureSet);
  }, [baseFeatures, behaviors, firstPassContext, mergedMetadata, mergedState]);

  const telemetry = useComponentTelemetry({
    metadata: mergedMetadata,
    enabled: telemetryEnabled,
  });

  const runtimeContext = React.useMemo<ComponentBehaviorContext>(() => ({
    metadata: mergedMetadata,
    state: mergedState,
    features: mergedFeatures,
    telemetry,
  }), [mergedFeatures, mergedMetadata, mergedState, telemetry]);

  const behaviorRootProps = React.useMemo(() => behaviors.reduce<PrimitiveProps | undefined>((acc, behavior) => {
    if (!behavior?.rootProps) return acc;
    return mergePrimitiveProps(acc, resolveLazyValue(behavior.rootProps, runtimeContext));
  }, undefined), [behaviors, runtimeContext]);

  const behaviorSlots = React.useMemo(() => behaviors.reduce<PrimitiveSlotPropsMap>((acc, behavior) => {
    if (!behavior?.slots) return acc;

    for (const [slot, slotValue] of Object.entries(behavior.slots)) {
      const normalizedSlot = sanitizePrimitiveToken(slot);
      if (!normalizedSlot) continue;
      acc[normalizedSlot] = mergePrimitiveProps(
        acc[normalizedSlot],
        resolveLazyValue(slotValue, runtimeContext)
      );
    }

    return acc;
  }, {}), [behaviors, runtimeContext]);

  const sharedAttributes = React.useMemo(() => mergePrimitiveProps<PrimitiveProps>(
    createPrimitiveAttributes(mergedMetadata),
    createStateAttributes(mergedState),
    createFeatureAttributes(mergedFeatures)
  ), [mergedFeatures, mergedMetadata, mergedState]);

  const composedRootProps = React.useMemo(() => mergePrimitiveProps<PrimitiveProps>(
    sharedAttributes,
    behaviorRootProps,
    rootProps
  ), [behaviorRootProps, rootProps, sharedAttributes]);

  const getSlotProps = React.useCallback((
    slot: string,
    overrideProps?: PrimitiveProps,
    slotState: PrimitiveStateBag = {}
  ): PrimitiveProps => {
    const normalizedSlot = sanitizePrimitiveToken(slot);
    if (!normalizedSlot) {
      throw new Error(`Invalid slot name "${slot}".`);
    }

    return mergePrimitiveProps<PrimitiveProps>(
      createPrimitiveAttributes({ ...mergedMetadata, slot: normalizedSlot }),
      createStateAttributes({ ...mergedState, ...slotState }),
      createFeatureAttributes(mergedFeatures),
      behaviorSlots[normalizedSlot],
      slotProps[normalizedSlot],
      overrideProps
    );
  }, [behaviorSlots, mergedFeatures, mergedMetadata, mergedState, slotProps]);

  return {
    metadata: mergedMetadata,
    state: mergedState,
    features: mergedFeatures,
    telemetry,
    rootProps: composedRootProps,
    getSlotProps,
  };
}
