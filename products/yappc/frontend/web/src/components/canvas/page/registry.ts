import {
  buildContractMap,
  findBuilderComponents,
  getRegistryStore,
  registerStarterContracts,
  type BuilderPaletteEntry,
} from '@ghatana/ds-registry';
import type { ComponentContract } from '@ghatana/ds-schema';

registerStarterContracts(getRegistryStore());

const LEGACY_TO_CONTRACT_NAME = {
  button: 'Button',
  card: 'Card',
  textfield: 'TextField',
  typography: 'Typography',
  box: 'Box',
} as const satisfies Record<string, string>;

const CONTRACT_TO_LEGACY_TYPE = {
  Button: 'button',
  Card: 'card',
  TextField: 'textfield',
  Typography: 'typography',
  Box: 'box',
} as const satisfies Record<string, string>;

export type LegacyComponentType = keyof typeof LEGACY_TO_CONTRACT_NAME;

export interface RegistryFieldDescriptor {
  readonly name: string;
  readonly label: string;
  readonly control: 'text' | 'number' | 'boolean' | 'toggle' | 'select';
  /** @deprecated Use `control` instead. Present for backward-compat with callers that read `.type`. */
  readonly type: 'text' | 'number' | 'boolean';
  readonly required: boolean;
  readonly defaultValue?: unknown;
  /** Ordered list of allowed values when `control === 'select'`. */
  readonly enumOptions?: readonly string[];
}

export interface ConfiguratorGroup {
  readonly label: string;
  readonly propNames: readonly string[];
}

export interface ContractGovernanceProfile {
  readonly reviewRequiredProps: readonly string[];
  readonly privacyLevel?: string;
  readonly telemetryEventNames: readonly string[];
  readonly observabilityMarks: readonly string[];
  readonly requiredA11yProps: readonly string[];
}

export function getBuilderPalette(): readonly BuilderPaletteEntry[] {
  return findBuilderComponents(getRegistryStore());
}

export function getContractMap(): ReadonlyMap<string, ComponentContract> {
  return buildContractMap(getRegistryStore());
}

export function getContractByName(contractName: string): ComponentContract | undefined {
  return getContractMap().get(contractName);
}

export function normalizeContractName(typeOrName: string): string {
  return LEGACY_TO_CONTRACT_NAME[typeOrName as LegacyComponentType] ?? typeOrName;
}

export function toLegacyComponentType(contractName: string): string {
  return CONTRACT_TO_LEGACY_TYPE[contractName as keyof typeof CONTRACT_TO_LEGACY_TYPE] ?? contractName.toLowerCase();
}

export function getDefaultSlotName(contractName: string): string | undefined {
  const contract = getContractByName(contractName);
  return contract?.slots.find((slot) => slot.isDefault)?.name;
}

export function isContainerContract(contractName: string): boolean {
  return getContractByName(contractName)?.layout?.isContainer ?? false;
}

export function getRegistryFields(contractName: string): readonly RegistryFieldDescriptor[] {
  const contract = getContractByName(contractName);
  if (!contract) {
    return [];
  }

  return contract.props
    .filter((prop) => prop.name !== 'children')
    .map((prop) => {
      const builderControl = prop.builderMetadata?.control;
      const enumValues = prop.validation?.enum;

      let control: RegistryFieldDescriptor['control'];
      if (builderControl === 'select') {
        control = 'select';
      } else if (builderControl === 'toggle' || prop.type === 'boolean') {
        control = 'toggle';
      } else if (prop.type === 'number') {
        control = 'number';
      } else {
        control = 'text';
      }

      const legacyType: RegistryFieldDescriptor['type'] =
        control === 'number' ? 'number' : control === 'boolean' || control === 'toggle' ? 'boolean' : 'text';

      return {
        name: prop.name,
        label: prop.name,
        control,
        type: legacyType,
        required: prop.required,
        defaultValue: prop.defaultValue,
        ...(control === 'select' && Array.isArray(enumValues)
          ? { enumOptions: enumValues as readonly string[] }
          : {}),
      } satisfies RegistryFieldDescriptor;
    });
}

/**
 * Returns the configurator groups defined in the contract, if any.
 * Groups drive the tabbed/section layout in PropertyForm.
 */
export function getConfiguratorGroups(contractName: string): readonly ConfiguratorGroup[] {
  const contract = getContractByName(contractName);
  if (!contract?.configurator?.groups) {
    return [];
  }
  return contract.configurator.groups.map((g) => ({
    label: g.label,
    propNames: g.propNames,
  }));
}

export function getContractGovernanceProfile(contractName: string): ContractGovernanceProfile {
  const contract = getContractByName(contractName);

  return {
    reviewRequiredProps: contract?.aiPolicy?.reviewRequiredProps ?? [],
    privacyLevel: contract?.privacy,
    telemetryEventNames: contract?.telemetry?.emittedEvents.map((event) => event.name) ?? [],
    observabilityMarks: contract?.observability?.performanceMarks ?? [],
    requiredA11yProps: contract?.builderA11y?.requiredA11yProps ?? [],
  };
}

function mapPropType(propType: string): RegistryFieldDescriptor['type'] {
  if (propType === 'number') {
    return 'number';
  }
  if (propType === 'boolean') {
    return 'boolean';
  }
  return 'text';
}

// Keep the internal mapPropType for any future callers that depend on it,
// but getRegistryFields no longer uses it directly.
void mapPropType;
