import { describe, expect, it } from 'vitest';

import {
  checkContractCompatibility,
  getContractVersionProfile,
  migrateContractInstance,
  normalizeVersionedContractName,
  resolveVersionedContract,
} from '../contractVersioning';
import {
  componentDataToBuilderProps,
  componentDataToInsertableInstance,
} from '../builder-document-adapter';

describe('page-builder contract versioning', () => {
  it('resolves starter contract versions and deprecation status from the registry', () => {
    const profile = getContractVersionProfile('Button');

    expect(profile).toMatchObject({
      name: 'Button',
      version: '1.0.0',
      status: 'stable',
      deprecated: false,
      availableVersions: ['1.0.0'],
    });
    expect(profile?.migrations.map((migration) => migration.id)).toContain(
      'legacy-button-to-button-1.0.0'
    );
  });

  it('normalizes legacy aliases through registered migrations', () => {
    expect(normalizeVersionedContractName('button')).toBe('Button');
    expect(normalizeVersionedContractName('text-field')).toBe('TextField');
    expect(resolveVersionedContract('textfield')?.name).toBe('TextField');
  });

  it('migrates legacy props into canonical design-system props', () => {
    const migrated = migrateContractInstance({
      contractName: 'button',
      props: {
        variant: 'contained',
        size: 'medium',
        color: 'error',
        text: 'Save',
      },
    });

    expect(migrated).toMatchObject({
      contractName: 'Button',
      version: '1.0.0',
      props: {
        variant: 'solid',
        size: 'md',
        color: 'danger',
        text: 'Save',
        children: 'Save',
      },
      migrationsApplied: ['legacy-button-to-button-1.0.0'],
      compatibility: {
        compatible: true,
        requiresMigration: true,
      },
    });
  });

  it('reports incompatible major-version upgrades without a registered migration', () => {
    const compatibility = checkContractCompatibility('Button', '0.9.0', '1.0.0');

    expect(compatibility.compatible).toBe(false);
    expect(compatibility.errors[0]).toContain('No migration is registered');
  });

  it('applies contract migration when adapting legacy page-designer component data', () => {
    const props = componentDataToBuilderProps({
      id: 'button-1',
      type: 'button',
      variant: 'outlined',
      color: 'error',
      size: 'large',
      disabled: false,
      fullWidth: false,
      text: 'Continue',
    });
    const instance = componentDataToInsertableInstance({
      id: 'button-1',
      type: 'button',
      variant: 'text',
      color: 'primary',
      size: 'small',
      disabled: false,
      fullWidth: false,
      text: 'Cancel',
    });

    expect(props).toMatchObject({
      variant: 'outline',
      color: 'danger',
      size: 'lg',
      children: 'Continue',
    });
    expect(instance.contractName).toBe('Button');
    expect(instance.props).toMatchObject({
      variant: 'ghost',
      size: 'sm',
      children: 'Cancel',
    });
  });
});
