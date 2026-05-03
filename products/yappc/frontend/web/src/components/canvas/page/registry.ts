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
  readonly type: 'text' | 'number' | 'boolean';
  readonly required: boolean;
  readonly defaultValue?: unknown;
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
    .map((prop) => ({
      name: prop.name,
      label: prop.name,
      type: mapPropType(prop.type),
      required: prop.required,
      defaultValue: prop.defaultValue,
    }));
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
