import {
  buildContractMap,
  findBuilderComponents,
  getRegistryStore,
  registerStarterContracts,
  type BuilderPaletteEntry,
} from '@ghatana/ds-registry';
import type { ComponentContract } from '@ghatana/ds-schema';
import type { ComponentProp, PropType } from '@ghatana/ds-schema';
import {
  getContractVersionProfile,
  migrateContractInstance,
  normalizeVersionedContractName,
  resolveVersionedContract,
  type ContractCompatibilityResult,
  type ContractInstanceMigrationInput,
  type ContractInstanceMigrationResult,
  type ContractVersionProfile,
} from './contractVersioning';

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
  readonly control:
    | 'text'
    | 'number'
    | 'boolean'
    | 'toggle'
    | 'select'
    | 'multiselect'
    | 'color'
    | 'token-select'
    | 'json'
    | 'code';
  /** @deprecated Use `control` instead. Present for backward-compat with callers that read `.type`. */
  readonly type: 'text' | 'number' | 'boolean';
  readonly valueType: PropType;
  readonly required: boolean;
  readonly defaultValue?: unknown;
  readonly description?: string;
  readonly tokenTypes?: readonly string[];
  /** Ordered list of allowed values when `control === 'select'`. */
  readonly enumOptions?: readonly string[];
}

export interface ConfiguratorGroup {
  readonly label: string;
  readonly propNames: readonly string[];
}

export interface ContractGovernanceProfile {
  readonly reviewRequiredProps: readonly string[];
  readonly privacyLevel?: ComponentContract['privacy'];
  readonly telemetryEventNames: readonly string[];
  readonly observabilityMarks: readonly string[];
  readonly requiredA11yProps: readonly string[];
}

export interface BuilderPaletteFilters {
  readonly query?: string;
  readonly category?: string;
  readonly phaseMode?: 'design' | 'preview' | 'code' | 'validate';
  readonly contextTags?: readonly string[];
  readonly includeReadOnlyPhaseComponents?: boolean;
}

export function getBuilderPalette(): readonly BuilderPaletteEntry[] {
  return findBuilderComponents(getRegistryStore());
}

function matchesPaletteQuery(entry: BuilderPaletteEntry, query: string): boolean {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) {
    return true;
  }

  const searchableText = [
    entry.name,
    entry.displayName,
    entry.tooltip,
    entry.group,
    entry.subGroup,
    ...entry.searchKeywords,
  ]
    .filter((value): value is string => typeof value === 'string')
    .join(' ')
    .toLowerCase();

  return searchableText.includes(normalizedQuery);
}

function matchesPaletteContext(entry: BuilderPaletteEntry, contextTags: readonly string[]): boolean {
  if (contextTags.length === 0) {
    return true;
  }

  const normalizedTags = new Set(contextTags.map((tag) => tag.toLowerCase()));
  const entryTags = [
    entry.group,
    entry.subGroup,
    ...entry.searchKeywords,
  ]
    .filter((value): value is string => typeof value === 'string')
    .map((value) => value.toLowerCase());

  return entryTags.some((tag) => normalizedTags.has(tag));
}

export function getBuilderPaletteCategories(
  entries: readonly BuilderPaletteEntry[] = getBuilderPalette()
): readonly string[] {
  return Array.from(new Set(entries.map((entry) => entry.group))).sort((a, b) => a.localeCompare(b));
}

export function getFilteredBuilderPalette(
  filters: BuilderPaletteFilters = {},
  entries: readonly BuilderPaletteEntry[] = getBuilderPalette()
): readonly BuilderPaletteEntry[] {
  const query = filters.query ?? '';
  const contextTags = filters.contextTags ?? [];

  return entries
    .filter((entry) => !filters.category || entry.group === filters.category)
    .filter((entry) => matchesPaletteQuery(entry, query))
    .filter((entry) => matchesPaletteContext(entry, contextTags))
    .filter(
      () =>
        filters.includeReadOnlyPhaseComponents ||
        !filters.phaseMode ||
        filters.phaseMode === 'design'
    )
    .sort((a, b) => {
      if (a.featured !== b.featured) {
        return a.featured ? -1 : 1;
      }
      const groupCmp = a.group.localeCompare(b.group);
      if (groupCmp !== 0) return groupCmp;
      const rankCmp = a.rank - b.rank;
      if (rankCmp !== 0) return rankCmp;
      return a.displayName.localeCompare(b.displayName);
    });
}

export function getContractMap(): ReadonlyMap<string, ComponentContract> {
  return buildContractMap(getRegistryStore());
}

export function getContractByName(contractName: string): ComponentContract | undefined {
  return resolveVersionedContract(contractName);
}

export function normalizeContractName(typeOrName: string): string {
  return LEGACY_TO_CONTRACT_NAME[typeOrName as LegacyComponentType] ?? normalizeVersionedContractName(typeOrName);
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
      if (builderControl === 'json' || prop.type === 'object' || prop.type === 'array') {
        control = 'json';
      } else if (builderControl === 'multiselect') {
        control = 'multiselect';
      } else if (builderControl === 'token-select' || prop.type === 'token-ref') {
        control = 'token-select';
      } else if (builderControl === 'color') {
        control = 'color';
      } else if (builderControl === 'code') {
        control = 'code';
      } else if (builderControl === 'select') {
        control = 'select';
      } else if (builderControl === 'toggle' || prop.type === 'boolean') {
        control = 'toggle';
      } else if (prop.type === 'number') {
        control = 'number';
      } else {
        control = 'text';
      }

      const legacyType: RegistryFieldDescriptor['type'] =
        control === 'number' ? 'number' : control === 'toggle' ? 'boolean' : 'text';

      return {
        name: prop.name,
        label: prop.name,
        control,
        type: legacyType,
        valueType: prop.type,
        required: prop.required,
        description: prop.description,
        defaultValue: prop.defaultValue,
        tokenTypes: prop.builderMetadata?.tokenTypes,
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

export function getRegistryContractVersionProfile(contractName: string): ContractVersionProfile | undefined {
  return getContractVersionProfile(contractName);
}

export function migrateRegistryContractInstance(
  input: ContractInstanceMigrationInput
): ContractInstanceMigrationResult {
  return migrateContractInstance(input);
}

export type {
  ContractCompatibilityResult,
  ContractInstanceMigrationInput,
  ContractInstanceMigrationResult,
  ContractVersionProfile,
};

function mapPropType(propType: ComponentProp['type']): RegistryFieldDescriptor['type'] {
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
