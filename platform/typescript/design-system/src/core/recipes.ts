import * as React from 'react';

import {
  createFeatureAttributes,
  createStateAttributes,
  useComponentComposition,
  type ComponentBehavior,
  type PrimitiveFeature,
  type PrimitiveStateBag,
} from './composition';
import {
  createPrimitiveAttributes,
  mergePrimitiveProps,
  sanitizePrimitiveToken,
  type PrimitiveMetadata,
  type PrimitiveProps,
} from './primitives';
import {
  createPlatformRenderPlan,
  type PlatformCapabilityHints,
  type PlatformGeneratorTarget,
  type PlatformRenderPlan,
} from './platform';

type ValueOrFactory<T, Ctx> = T | ((context: Ctx) => T);

export interface ComponentRecipeContext<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
> {
  props: Props;
  metadata: PrimitiveMetadata;
  state: State;
  features: PrimitiveFeature[];
}

export interface ComponentSlotRecipe<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
> {
  visible?: ValueOrFactory<boolean, ComponentRecipeContext<Props, State>>;
  state?: ValueOrFactory<PrimitiveStateBag | undefined, ComponentRecipeContext<Props, State>>;
  props?: ValueOrFactory<PrimitiveProps | undefined, ComponentRecipeContext<Props, State>>;
}

export interface ComponentRecipeDefinition<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
> {
  name: string;
  defaultProps?: Partial<Props>;
  metadata?: ValueOrFactory<PrimitiveMetadata, ComponentRecipeContext<Props, State>>;
  deriveState?: (props: Props) => State;
  deriveFeatures?: (context: ComponentRecipeContext<Props, State>) => Array<PrimitiveFeature | undefined>;
  rootProps?: ValueOrFactory<PrimitiveProps | undefined, ComponentRecipeContext<Props, State>>;
  slots?: Record<SlotName, ComponentSlotRecipe<Props, State>>;
  behaviors?: Array<ValueOrFactory<ComponentBehavior | undefined, ComponentRecipeContext<Props, State>>>;
  platforms?: Array<PlatformGeneratorTarget | undefined>;
  platformHints?: ValueOrFactory<Partial<PlatformCapabilityHints> | undefined, ComponentRecipeContext<Props, State>>;
}

export interface ComponentRecipeSlotOverride {
  visible?: boolean;
  state?: PrimitiveStateBag;
  props?: PrimitiveProps;
}

export interface ComponentRecipeInput<
  Props extends Record<string, unknown>,
  SlotName extends string = string,
> {
  props?: Partial<Props>;
  metadata?: Partial<PrimitiveMetadata>;
  state?: PrimitiveStateBag;
  features?: Array<PrimitiveFeature | undefined>;
  rootProps?: PrimitiveProps;
  slotProps?: Partial<Record<SlotName, PrimitiveProps | undefined>>;
  slotOverrides?: Partial<Record<SlotName, ComponentRecipeSlotOverride>>;
  behaviors?: Array<ComponentBehavior | undefined>;
  telemetryEnabled?: boolean;
  platforms?: Array<PlatformGeneratorTarget | undefined>;
  platformHints?: Partial<PlatformCapabilityHints>;
}

export interface ComponentRecipeSlotPlan {
  name: string;
  visible: boolean;
  state: PrimitiveStateBag;
  props: PrimitiveProps;
  attributes: PrimitiveProps;
  semantics?: {
    role?: string;
    eventNames: readonly string[];
  };
}

export interface ComponentRenderPlan<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
> {
  readonly component: string;
  readonly props: Props;
  readonly metadata: PrimitiveMetadata;
  readonly state: State;
  readonly features: readonly PrimitiveFeature[];
  readonly slotOrder: readonly SlotName[];
  readonly slots: Readonly<Record<SlotName, ComponentRecipeSlotPlan>>;
  readonly rootProps: PrimitiveProps;
  readonly rootAttributes: PrimitiveProps;
  readonly platform: PlatformRenderPlan;
  readonly signature: string;
}

export interface CompiledComponentRecipe<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
> {
  readonly kind: 'compiled-component-recipe';
  readonly name: string;
  readonly definition: ComponentRecipeDefinition<Props, State, SlotName>;
  readonly slotOrder: readonly SlotName[];
  readonly staticMetadata?: PrimitiveMetadata;
  readonly staticRootAttributes?: PrimitiveProps;
  readonly platforms: readonly PlatformGeneratorTarget[];
  createRenderPlan: (input?: ComponentRecipeInput<Props, SlotName>) => ComponentRenderPlan<Props, State, SlotName>;
}

export interface UseComponentRecipeResult<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
> {
  readonly props: Props;
  readonly plan: ComponentRenderPlan<Props, State, SlotName>;
  readonly metadata: PrimitiveMetadata;
  readonly state: State;
  readonly features: readonly PrimitiveFeature[];
  readonly rootProps: PrimitiveProps;
  readonly getSlotProps: (slot: SlotName, overrideProps?: PrimitiveProps, slotState?: PrimitiveStateBag) => PrimitiveProps;
}

function evaluateValue<T, Props extends Record<string, unknown>, State extends PrimitiveStateBag>(
  value: ValueOrFactory<T, ComponentRecipeContext<Props, State>> | undefined,
  context: ComponentRecipeContext<Props, State>
): T | undefined {
  if (typeof value === 'function') {
    return (value as (ctx: ComponentRecipeContext<Props, State>) => T)(context);
  }
  return value;
}

function createRecipeContext<Props extends Record<string, unknown>, State extends PrimitiveStateBag>(
  props: Props,
  metadata: PrimitiveMetadata,
  state: State,
  features: PrimitiveFeature[]
): ComponentRecipeContext<Props, State> {
  return { props, metadata, state, features };
}

function computeRenderSignature(
  metadata: PrimitiveMetadata,
  state: PrimitiveStateBag,
  features: readonly PrimitiveFeature[],
  slotPlans: Readonly<Record<string, ComponentRecipeSlotPlan>>
): string {
  const metadataAttrs = createPrimitiveAttributes(metadata);
  const stateAttrs = createStateAttributes(state);
  const featureAttrs = createFeatureAttributes([...features]);
  const visibleSlots = Object.values(slotPlans)
    .filter((slot) => slot.visible)
    .map((slot) => slot.name)
    .sort()
    .join(',');

  return [
    metadataAttrs['data-component'],
    metadataAttrs['data-variant'],
    metadataAttrs['data-tone'],
    stateAttrs['data-state'],
    featureAttrs['data-features'],
    visibleSlots,
  ]
    .filter(Boolean)
    .join('|');
}

export function resolveComponentRenderPlan<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
>(
  compiled: CompiledComponentRecipe<Props, State, SlotName>,
  input: ComponentRecipeInput<Props, SlotName> = {}
): ComponentRenderPlan<Props, State, SlotName> {
  const mergedProps = {
    ...(compiled.definition.defaultProps ?? {}),
    ...(input.props ?? {}),
  } as Props;

  const derivedState = compiled.definition.deriveState?.(mergedProps) ?? ({} as State);

  const metadataSeed = compiled.staticMetadata ?? evaluateValue(
    compiled.definition.metadata,
    createRecipeContext(mergedProps, { component: compiled.name }, derivedState, [])
  ) ?? { component: compiled.name };

  const metadata = {
    ...metadataSeed,
    ...input.metadata,
    component: sanitizePrimitiveToken(input.metadata?.component ?? metadataSeed.component ?? compiled.name) ?? compiled.name,
  };

  const state = {
    ...derivedState,
    ...(input.state ?? {}),
  } as State;

  const featureContext = createRecipeContext(mergedProps, metadata, state, []);
  const features = Array.from(
    new Set(
      [
        ...(compiled.definition.deriveFeatures?.(featureContext) ?? []),
        ...(input.features ?? []),
      ]
        .map((feature) => sanitizePrimitiveToken(feature))
        .filter((feature): feature is PrimitiveFeature => Boolean(feature))
    )
  );

  const runtimeContext = createRecipeContext(mergedProps, metadata, state, features);
  const recipeRootProps = evaluateValue(compiled.definition.rootProps, runtimeContext);

  const slotPlans = compiled.slotOrder.reduce((acc, slotName) => {
    const slotDefinition = compiled.definition.slots?.[slotName];
    const slotOverride = input.slotOverrides?.[slotName];
    const visible = slotOverride?.visible ?? evaluateValue(slotDefinition?.visible, runtimeContext) ?? true;
    const slotState = {
      ...state,
      ...(evaluateValue(slotDefinition?.state, runtimeContext) ?? {}),
      ...(slotOverride?.state ?? {}),
    };
    const slotProps = mergePrimitiveProps<PrimitiveProps>(
      evaluateValue(slotDefinition?.props, runtimeContext),
      input.slotProps?.[slotName],
      slotOverride?.props
    );

    acc[slotName] = {
      name: slotName,
      visible,
      state: slotState,
      props: slotProps,
      attributes: mergePrimitiveProps<PrimitiveProps>(
        createPrimitiveAttributes({ ...metadata, slot: slotName }),
        createStateAttributes(slotState),
        createFeatureAttributes(features),
        slotProps
      ),
    };

    return acc;
  }, {} as Record<SlotName, ComponentRecipeSlotPlan>);

  const rootAttributes = mergePrimitiveProps<PrimitiveProps>(
    compiled.staticRootAttributes,
    createPrimitiveAttributes(metadata),
    createStateAttributes(state),
    createFeatureAttributes(features),
    recipeRootProps,
    input.rootProps
  );

  const platformHints = {
    ...(evaluateValue(compiled.definition.platformHints, runtimeContext) ?? {}),
    ...(input.platformHints ?? {}),
  };

  const platform = createPlatformRenderPlan({
    metadata,
    state,
    features,
    signature: computeRenderSignature(metadata, state, features, slotPlans),
    targets: input.platforms ?? [...compiled.platforms],
    capabilityHints: platformHints,
    rootProps: rootAttributes,
    slots: Object.fromEntries(
      (Object.entries(slotPlans) as Array<[string, ComponentRecipeSlotPlan]>).map(([slotName, slotPlan]) => [
        slotName,
        {
          visible: slotPlan.visible,
          state: slotPlan.state,
          props: slotPlan.attributes,
        },
      ])
    ) as Record<string, { visible: boolean; state: PrimitiveStateBag; props?: PrimitiveProps }>,
  });

  return Object.freeze({
    component: metadata.component,
    props: mergedProps,
    metadata,
    state,
    features,
    slotOrder: compiled.slotOrder,
    slots: slotPlans,
    rootProps: rootAttributes,
    rootAttributes,
    platform,
    signature: platform.signature,
  });
}

export function compileComponentRecipe<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
>(
  definition: ComponentRecipeDefinition<Props, State, SlotName>
): CompiledComponentRecipe<Props, State, SlotName> {
  const slotOrder = Object.keys(definition.slots ?? {})
    .map((slot) => sanitizePrimitiveToken(slot))
    .filter((slot): slot is SlotName => Boolean(slot)) as SlotName[];

  const staticMetadata =
    definition.metadata && typeof definition.metadata !== 'function'
      ? definition.metadata
      : undefined;

  const staticRootAttributes = staticMetadata
    ? createPrimitiveAttributes(staticMetadata)
    : undefined;
  const platforms = Array.from(
    new Set(
      (definition.platforms ?? ['react'])
        .map((platform) => sanitizePrimitiveToken(platform) as PlatformGeneratorTarget | undefined)
        .filter((platform): platform is PlatformGeneratorTarget => Boolean(platform))
    )
  );

  return {
    kind: 'compiled-component-recipe',
    name: sanitizePrimitiveToken(definition.name) ?? definition.name,
    definition,
    slotOrder,
    staticMetadata,
    staticRootAttributes,
    platforms: platforms.length > 0 ? platforms : ['react'],
    createRenderPlan(input) {
      return resolveComponentRenderPlan(this, input);
    },
  };
}

export function useComponentRecipe<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
>(
  compiled: CompiledComponentRecipe<Props, State, SlotName>,
  input: ComponentRecipeInput<Props, SlotName> = {}
): UseComponentRecipeResult<Props, State, SlotName> {
  const plan = React.useMemo(
    () => compiled.createRenderPlan(input),
    [compiled, input]
  );

  const behaviorContext = createRecipeContext(plan.props, plan.metadata, plan.state, [...plan.features]);
  const recipeBehaviors = (compiled.definition.behaviors ?? [])
    .map((behavior) => evaluateValue(behavior, behaviorContext))
    .filter((behavior): behavior is ComponentBehavior => Boolean(behavior));

  const composition = useComponentComposition({
    metadata: plan.metadata,
    state: plan.state,
    features: [...plan.features],
    rootProps: plan.rootProps,
    slotProps: Object.fromEntries(
      plan.slotOrder.map((slot) => [slot, plan.slots[slot]?.props])
    ),
    behaviors: [...recipeBehaviors, ...(input.behaviors ?? [])],
    telemetryEnabled: input.telemetryEnabled,
  });

  return {
    props: plan.props,
    plan,
    metadata: plan.metadata,
    state: plan.state,
    features: plan.features,
    rootProps: composition.rootProps,
    getSlotProps: (slot, overrideProps, slotState) => composition.getSlotProps(slot, overrideProps, slotState),
  };
}
