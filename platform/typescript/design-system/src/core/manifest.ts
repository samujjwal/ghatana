import type { BuilderComponentManifest } from '@ghatana/ds-schema';

import type { CompiledComponentRecipe } from './recipes';
import type { PrimitiveStateBag } from './composition';

export function createBuilderManifestFromRecipe<
  Props extends Record<string, unknown>,
  State extends PrimitiveStateBag = PrimitiveStateBag,
  SlotName extends string = string,
>(
  compiled: CompiledComponentRecipe<Props, State, SlotName>,
): BuilderComponentManifest {
  const seedPlan = compiled.createRenderPlan();

  return {
    name: compiled.name,
    version: compiled.staticMetadata?.state ? '1.0.0' : '1.0.0',
    targets: [...compiled.platforms],
    features: [...seedPlan.features],
    semantics: {
      role:
        typeof seedPlan.rootAttributes.role === 'string'
          ? seedPlan.rootAttributes.role
          : undefined,
      eventNames: [...seedPlan.platform.root.semantics.eventNames],
    },
    slots: compiled.slotOrder.map((slotName) => {
      const slotPlan = seedPlan.slots[slotName];
      return {
        name: slotName,
        allowsMultiple: true,
        required: slotPlan.visible,
        features: Object.keys(slotPlan.state).filter((key) => slotPlan.state[key] === true),
        exposure: slotName === 'default' ? 'children' : 'prop',
        semantics: {
          role: slotPlan.semantics.role,
          eventNames: [...slotPlan.semantics.eventNames],
        },
      };
    }),
    capabilities: {
      ...seedPlan.platform.capabilities,
      privacy: seedPlan.platform.capabilities.privacy,
      optimizedFor: [...seedPlan.platform.capabilities.optimizedFor],
    },
    dataClassification: seedPlan.metadata.privacy,
    reviewRequired: false,
  };
}
