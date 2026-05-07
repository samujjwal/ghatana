/**
 * Page-builder contract versioning and migration facade.
 *
 * @doc.type module
 * @doc.purpose Version, migration, deprecation, and compatibility checks for page-builder contracts
 * @doc.layer product
 * @doc.pattern Registry Facade
 */

import {
  getRegistryStore,
  registerStarterContracts,
  resolveAllContractVersions,
  resolveContractAtVersion,
  resolveLatestContract,
} from '@ghatana/ds-registry';
import type { ComponentContract } from '@ghatana/ds-schema';

registerStarterContracts(getRegistryStore());

export type ContractStatus = ComponentContract['metadata']['status'];

export interface ContractMigration {
  readonly id: string;
  readonly fromName: string;
  readonly fromVersion?: string;
  readonly toName: string;
  readonly toVersion: string;
  readonly description: string;
  readonly migrateProps: (props: Record<string, unknown>) => Record<string, unknown>;
}

export interface ContractVersionProfile {
  readonly name: string;
  readonly version: string;
  readonly status: ContractStatus;
  readonly deprecated: boolean;
  readonly availableVersions: readonly string[];
  readonly migrations: readonly ContractMigration[];
}

export interface ContractCompatibilityResult {
  readonly compatible: boolean;
  readonly requiresMigration: boolean;
  readonly warnings: readonly string[];
  readonly errors: readonly string[];
}

export interface ContractInstanceMigrationInput {
  readonly contractName: string;
  readonly version?: string;
  readonly props: Record<string, unknown>;
}

export interface ContractInstanceMigrationResult {
  readonly contractName: string;
  readonly version: string;
  readonly props: Record<string, unknown>;
  readonly migrationsApplied: readonly string[];
  readonly compatibility: ContractCompatibilityResult;
}

const BUTTON_VARIANT_MIGRATION: Record<string, string> = {
  contained: 'solid',
  outlined: 'outline',
  text: 'ghost',
};

const BUTTON_SIZE_MIGRATION: Record<string, string> = {
  small: 'sm',
  medium: 'md',
  large: 'lg',
};

const BUTTON_COLOR_MIGRATION: Record<string, string> = {
  error: 'danger',
};

const CONTRACT_MIGRATIONS: readonly ContractMigration[] = [
  {
    id: 'legacy-button-to-button-1.0.0',
    fromName: 'button',
    toName: 'Button',
    toVersion: '1.0.0',
    description: 'Migrates legacy local button props to the canonical Button contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({
      ...props,
      ...(typeof props.variant === 'string' && props.variant in BUTTON_VARIANT_MIGRATION
        ? { variant: BUTTON_VARIANT_MIGRATION[props.variant] }
        : {}),
      ...(typeof props.size === 'string' && props.size in BUTTON_SIZE_MIGRATION
        ? { size: BUTTON_SIZE_MIGRATION[props.size] }
        : {}),
      ...(typeof props.color === 'string' && props.color in BUTTON_COLOR_MIGRATION
        ? { color: BUTTON_COLOR_MIGRATION[props.color] }
        : {}),
      ...(typeof props.text === 'string' && typeof props.children !== 'string'
        ? { children: props.text }
        : {}),
    }),
  },
  {
    id: 'legacy-card-to-card-1.0.0',
    fromName: 'card',
    toName: 'Card',
    toVersion: '1.0.0',
    description: 'Migrates legacy local card nodes to the canonical Card contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({ ...props }),
  },
  {
    id: 'legacy-textfield-to-textfield-1.0.0',
    fromName: 'textfield',
    toName: 'TextField',
    toVersion: '1.0.0',
    description: 'Migrates legacy textfield nodes to the canonical TextField contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({
      ...props,
      ...(props.variant === 'standard' ? { variant: 'outlined' } : {}),
    }),
  },
  {
    id: 'legacy-text-field-to-textfield-1.0.0',
    fromName: 'text-field',
    toName: 'TextField',
    toVersion: '1.0.0',
    description: 'Migrates hyphenated legacy text-field nodes to the canonical TextField contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({
      ...props,
      ...(props.variant === 'standard' ? { variant: 'outlined' } : {}),
    }),
  },
  {
    id: 'legacy-typography-to-typography-1.0.0',
    fromName: 'typography',
    toName: 'Typography',
    toVersion: '1.0.0',
    description: 'Migrates legacy typography nodes to the canonical Typography contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({
      ...props,
      ...(typeof props.text === 'string' && typeof props.children !== 'string'
        ? { children: props.text }
        : {}),
    }),
  },
  {
    id: 'legacy-box-to-box-1.0.0',
    fromName: 'box',
    toName: 'Box',
    toVersion: '1.0.0',
    description: 'Migrates legacy box/container nodes to the canonical Box contract.',
    migrateProps: (props: Record<string, unknown>): Record<string, unknown> => ({ ...props }),
  },
];

function parseMajor(version: string): number | null {
  const major = Number.parseInt(version.split('.')[0] ?? '', 10);
  return Number.isFinite(major) ? major : null;
}

function findMigration(fromName: string, fromVersion?: string): ContractMigration | undefined {
  return CONTRACT_MIGRATIONS.find(
    (migration) =>
      migration.fromName === fromName &&
      (migration.fromVersion === undefined || migration.fromVersion === fromVersion)
  );
}

export function normalizeVersionedContractName(contractName: string): string {
  return findMigration(contractName)?.toName ?? contractName;
}

export function getContractMigrations(contractName: string): readonly ContractMigration[] {
  const normalizedName = normalizeVersionedContractName(contractName);
  return CONTRACT_MIGRATIONS.filter(
    (migration) => migration.fromName === contractName || migration.toName === normalizedName
  );
}

export function resolveVersionedContract(
  contractName: string,
  version?: string
): ComponentContract | undefined {
  const normalizedName = normalizeVersionedContractName(contractName);
  if (version) {
    return resolveContractAtVersion(getRegistryStore(), normalizedName, version);
  }
  return resolveLatestContract(getRegistryStore(), normalizedName);
}

export function getContractVersionProfile(contractName: string): ContractVersionProfile | undefined {
  const normalizedName = normalizeVersionedContractName(contractName);
  const contract = resolveVersionedContract(normalizedName);
  if (!contract) {
    return undefined;
  }

  return {
    name: contract.name,
    version: contract.version,
    status: contract.metadata.status,
    deprecated: contract.metadata.status === 'deprecated',
    availableVersions: Array.from(
      new Set(resolveAllContractVersions(getRegistryStore(), contract.name).map((entry) => entry.version))
    ),
    migrations: getContractMigrations(contractName),
  };
}

export function checkContractCompatibility(
  contractName: string,
  fromVersion?: string,
  toVersion?: string
): ContractCompatibilityResult {
  const normalizedName = normalizeVersionedContractName(contractName);
  const targetContract = resolveVersionedContract(normalizedName, toVersion);
  if (!targetContract) {
    return {
      compatible: false,
      requiresMigration: false,
      warnings: [],
      errors: [`Unknown target contract ${normalizedName}${toVersion ? `@${toVersion}` : ''}.`],
    };
  }

  const warnings: string[] = [];
  const errors: string[] = [];
  const migration = findMigration(contractName, fromVersion);
  const sourceVersion = fromVersion ?? targetContract.version;
  const sourceMajor = parseMajor(sourceVersion);
  const targetMajor = parseMajor(targetContract.version);

  if (targetContract.metadata.status === 'deprecated') {
    warnings.push(`Target contract ${targetContract.name}@${targetContract.version} is deprecated.`);
  }

  if (migration) {
    warnings.push(`Migration ${migration.id} is required before using ${migration.toName}@${migration.toVersion}.`);
  } else if (sourceMajor !== null && targetMajor !== null && sourceMajor !== targetMajor) {
    errors.push(
      `No migration is registered from ${contractName}@${sourceVersion} to ${targetContract.name}@${targetContract.version}.`
    );
  }

  return {
    compatible: errors.length === 0,
    requiresMigration: Boolean(migration),
    warnings,
    errors,
  };
}

export function migrateContractInstance(
  input: ContractInstanceMigrationInput
): ContractInstanceMigrationResult {
  const migration = findMigration(input.contractName, input.version);
  const normalizedName = migration?.toName ?? normalizeVersionedContractName(input.contractName);
  const targetContract = resolveVersionedContract(normalizedName, migration?.toVersion);
  const targetVersion = targetContract?.version ?? migration?.toVersion ?? input.version ?? '1.0.0';
  const compatibility = checkContractCompatibility(input.contractName, input.version, targetVersion);

  return {
    contractName: normalizedName,
    version: targetVersion,
    props: migration ? migration.migrateProps(input.props) : { ...input.props },
    migrationsApplied: migration ? [migration.id] : [],
    compatibility,
  };
}
