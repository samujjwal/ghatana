/**
 * @fileoverview Tests for builder-facing registry queries.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createRegistryStore, resetRegistryStore, registerStarterContracts, ButtonContract, CardContract } from '../index';
import {
  findBuilderComponents,
  resolveContractForCodegen,
  resolveAllContractsForCodegen,
  resolvePreviewPolicy,
  resolveAllPreviewPolicies,
  resolveLatestContract,
  resolveContractAtVersion,
  resolveAllContractVersions,
  buildContractMap,
} from '../queries';
import type { ComponentContract } from '@ghatana/ds-schema';
import type { RegistryStore } from '../registry/store';

function makeBuilderContract(
  name: string,
  version: string,
  group = 'Components',
  status: 'stable' | 'experimental' | 'draft' | 'deprecated' = 'stable',
): ComponentContract {
  return {
    name,
    version,
    description: `${name} component`,
    metadata: {
      category: 'display',
      status,
      platforms: ['web'],
    },
    props: [],
    slots: [],
    events: [],
    builder: {
      icon: 'square',
      defaultProps: { size: 'medium' },
      palette: {
        group,
        displayName: `${name} Display`,
        rank: 1,
        searchKeywords: [name.toLowerCase()],
        featured: false,
      },
      codegen: {
        importPath: `@ghatana/design-system`,
        componentName: name,
        namedExport: true,
      },
    },
    preview: {
      minimumTrustLevel: 'semi-trusted',
      requiresNetwork: false,
      requiresStorage: false,
      requiresConsent: false,
    },
  };
}

describe('builder registry queries', () => {
  let store: RegistryStore;

  beforeEach(() => {
    resetRegistryStore();
    store = createRegistryStore();
  });

  // ==========================================================================
  // findBuilderComponents
  // ==========================================================================

  describe('findBuilderComponents', () => {
    it('returns empty array when registry is empty', () => {
      expect(findBuilderComponents(store)).toHaveLength(0);
    });

    it('returns palette entries for stable components with builder section', () => {
      store.registerComponent({
        id: 'c-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'abc',
        source: 'test',
        version: '1.0.0',
      });

      const results = findBuilderComponents(store);
      expect(results).toHaveLength(1);
      expect(results[0].name).toBe('Button');
      expect(results[0].displayName).toBe('Button Display');
      expect(results[0].group).toBe('Components');
    });

    it('excludes deprecated components', () => {
      store.registerComponent({
        id: 'c-1',
        contract: makeBuilderContract('OldButton', '1.0.0', 'Components', 'deprecated'),
        hash: 'abc',
        source: 'test',
        version: '1.0.0',
      });

      expect(findBuilderComponents(store)).toHaveLength(0);
    });

    it('excludes components without builder section', () => {
      const contract: ComponentContract = {
        name: 'PlainComponent',
        version: '1.0.0',
        metadata: { category: 'display', status: 'stable', platforms: ['web'] },
        props: [],
        slots: [],
        events: [],
      };
      store.registerComponent({
        id: 'c-1',
        contract,
        hash: 'abc',
        source: 'test',
        version: '1.0.0',
      });

      expect(findBuilderComponents(store)).toHaveLength(0);
    });

    it('sorts by group then rank then name', () => {
      const c1 = makeBuilderContract('Zeta', '1.0.0', 'Layout');
      const c2 = makeBuilderContract('Alpha', '1.0.0', 'Controls');
      const c3 = makeBuilderContract('Beta', '1.0.0', 'Controls');
      if (c2.builder?.palette) (c2.builder.palette as { rank: number }).rank = 2;
      if (c3.builder?.palette) (c3.builder.palette as { rank: number }).rank = 1;

      store.registerComponent({ id: 'c-1', contract: c1, hash: 'a', source: 'test', version: '1.0.0' });
      store.registerComponent({ id: 'c-2', contract: c2, hash: 'b', source: 'test', version: '1.0.0' });
      store.registerComponent({ id: 'c-3', contract: c3, hash: 'c', source: 'test', version: '1.0.0' });

      const results = findBuilderComponents(store);
      expect(results.map((r) => r.name)).toEqual(['Beta', 'Alpha', 'Zeta']);
    });

    it('includes all five starter contracts after registration', () => {
      registerStarterContracts(store);
      const results = findBuilderComponents(store);
      expect(results.length).toBeGreaterThanOrEqual(5);
      const names = results.map((r) => r.name);
      expect(names).toContain('Button');
      expect(names).toContain('Card');
      expect(names).toContain('TextField');
      expect(names).toContain('Typography');
      expect(names).toContain('Box');
    });
  });

  // ==========================================================================
  // resolveContractForCodegen
  // ==========================================================================

  describe('resolveContractForCodegen', () => {
    it('returns undefined for unknown contract', () => {
      expect(resolveContractForCodegen(store, 'Unknown')).toBeUndefined();
    });

    it('returns undefined when contract has no codegen section', () => {
      const contract: ComponentContract = {
        name: 'NoCodegen',
        version: '1.0.0',
        metadata: { category: 'display', status: 'stable', platforms: ['web'] },
        props: [],
        slots: [],
        events: [],
        builder: { palette: { group: 'Other' } },
      };
      store.registerComponent({ id: 'nc', contract, hash: 'h', source: 's', version: '1.0.0' });

      expect(resolveContractForCodegen(store, 'NoCodegen')).toBeUndefined();
    });

    it('returns resolved codegen contract with correct fields', () => {
      store.registerComponent({
        id: 'btn',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h',
        source: 's',
        version: '1.0.0',
      });

      const result = resolveContractForCodegen(store, 'Button');
      expect(result).toBeDefined();
      expect(result?.contractName).toBe('Button');
      expect(result?.importPath).toBe('@ghatana/design-system');
      expect(result?.componentName).toBe('Button');
      expect(result?.namedExport).toBe(true);
      expect(result?.htmlTagName).toBe('ghatana-button');
    });

    it('uses explicit htmlTagName when provided', () => {
      const contract = makeBuilderContract('MyComponent', '1.0.0');
      (contract.builder!.codegen as { htmlTagName: string }).htmlTagName = 'my-component';
      store.registerComponent({ id: 'mc', contract, hash: 'h', source: 's', version: '1.0.0' });

      const result = resolveContractForCodegen(store, 'MyComponent');
      expect(result?.htmlTagName).toBe('my-component');
    });

    it('resolves the latest version when multiple versions are registered', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });

      const result = resolveContractForCodegen(store, 'Button');
      expect(result?.version).toBe('2.0.0');
    });
  });

  // ==========================================================================
  // resolveAllContractsForCodegen
  // ==========================================================================

  describe('resolveAllContractsForCodegen', () => {
    it('returns empty map when registry is empty', () => {
      expect(resolveAllContractsForCodegen(store).size).toBe(0);
    });

    it('returns map with all contracts that have codegen sections', () => {
      store.registerComponent({
        id: 'btn',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'card',
        contract: makeBuilderContract('Card', '1.0.0'),
        hash: 'h2',
        source: 's',
        version: '1.0.0',
      });

      const map = resolveAllContractsForCodegen(store);
      expect(map.size).toBe(2);
      expect(map.has('Button')).toBe(true);
      expect(map.has('Card')).toBe(true);
    });
  });

  // ==========================================================================
  // resolvePreviewPolicy
  // ==========================================================================

  describe('resolvePreviewPolicy', () => {
    it('returns fallback defaults for unknown contract', () => {
      const policy = resolvePreviewPolicy(store, 'Unknown');
      expect(policy.contractName).toBe('Unknown');
      expect(policy.minimumTrustLevel).toBe('semi-trusted');
      expect(policy.requiresNetwork).toBe(false);
      expect(policy.requiresStorage).toBe(false);
      expect(policy.requiresConsent).toBe(false);
    });

    it('returns contract-defined preview policy', () => {
      const contract = makeBuilderContract('Button', '1.0.0');
      (contract.preview as { minimumTrustLevel: string }).minimumTrustLevel = 'trusted-local';
      (contract.preview as { requiresNetwork: boolean }).requiresNetwork = true;
      store.registerComponent({ id: 'btn', contract, hash: 'h', source: 's', version: '1.0.0' });

      const policy = resolvePreviewPolicy(store, 'Button');
      expect(policy.minimumTrustLevel).toBe('trusted-local');
      expect(policy.requiresNetwork).toBe(true);
    });
  });

  // ==========================================================================
  // resolveAllPreviewPolicies
  // ==========================================================================

  describe('resolveAllPreviewPolicies', () => {
    it('returns map with one entry per distinct contract name', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });
      store.registerComponent({
        id: 'card',
        contract: makeBuilderContract('Card', '1.0.0'),
        hash: 'h3',
        source: 's',
        version: '1.0.0',
      });

      const map = resolveAllPreviewPolicies(store);
      // Should have one entry per name, not per entry
      expect(map.size).toBe(2);
      expect(map.has('Button')).toBe(true);
      expect(map.has('Card')).toBe(true);
    });
  });

  // ==========================================================================
  // resolveLatestContract / resolveContractAtVersion / resolveAllContractVersions
  // ==========================================================================

  describe('resolveLatestContract', () => {
    it('returns undefined for unknown contract', () => {
      expect(resolveLatestContract(store, 'Unknown')).toBeUndefined();
    });

    it('returns the contract for the latest version', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });

      const contract = resolveLatestContract(store, 'Button');
      expect(contract?.version).toBe('2.0.0');
    });
  });

  describe('resolveContractAtVersion', () => {
    it('returns the contract at the requested version', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });

      const contract = resolveContractAtVersion(store, 'Button', '1.0.0');
      expect(contract?.version).toBe('1.0.0');
    });

    it('returns undefined for unregistered version', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });

      expect(resolveContractAtVersion(store, 'Button', '9.9.9')).toBeUndefined();
    });
  });

  describe('resolveAllContractVersions', () => {
    it('returns all entries in chronological order', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });

      const versions = resolveAllContractVersions(store, 'Button');
      expect(versions).toHaveLength(2);
      expect(versions[0].version).toBe('1.0.0');
      expect(versions[1].version).toBe('2.0.0');
    });
  });

  // ==========================================================================
  // buildContractMap
  // ==========================================================================

  describe('buildContractMap', () => {
    it('returns empty map when registry is empty', () => {
      expect(buildContractMap(store).size).toBe(0);
    });

    it('returns one entry per contract name using the latest version', () => {
      store.registerComponent({
        id: 'btn-1',
        contract: makeBuilderContract('Button', '1.0.0'),
        hash: 'h1',
        source: 's',
        version: '1.0.0',
      });
      store.registerComponent({
        id: 'btn-2',
        contract: makeBuilderContract('Button', '2.0.0'),
        hash: 'h2',
        source: 's',
        version: '2.0.0',
      });
      store.registerComponent({
        id: 'card',
        contract: makeBuilderContract('Card', '1.0.0'),
        hash: 'h3',
        source: 's',
        version: '1.0.0',
      });

      const map = buildContractMap(store);
      expect(map.size).toBe(2);
      expect(map.get('Button')?.version).toBe('2.0.0');
      expect(map.get('Card')?.version).toBe('1.0.0');
    });

    it('returns a map usable by @ghatana/ui-builder codegen', () => {
      registerStarterContracts(store);
      const map = buildContractMap(store);
      expect(map.size).toBeGreaterThanOrEqual(5);
      // Codegen expects entries keyed by name
      expect(map.has('Button')).toBe(true);
      expect(map.get('Button')).toBeDefined();
    });
  });
});
