/**
 * @fileoverview Integration tests for @ghatana/tokens ↔ @ghatana/ds-registry.
 *
 * Verifies that the canonical token registry can be registered into, retrieved
 * from, and validated against the ds-registry token-set store.
 *
 * This is the registry-backed validation flow required by task 1.4.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  createRegistryStore,
  type RegistryStore,
} from '@ghatana/ds-registry';
import { validateDTCGTokenFile } from '@ghatana/ds-schema';
import { tokens } from '../registry';
import { validateTokenRegistryDTCG } from '../validation';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Converts the Ghatana flat token registry into a minimal DTCG token-file
 * structure so it can be ingested by the registry store.
 */
function wrapAsDTCGTokenFile(registry: Record<string, unknown>): Record<string, unknown> {
  return {
    $schema: 'https://design-tokens.org/schema',
    $version: '1.0.0',
    ...registry,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('@ghatana/tokens ↔ @ghatana/ds-registry integration', () => {
  let store: RegistryStore;

  beforeEach(() => {
    store = createRegistryStore();
  });

  it('registers the canonical token set without errors', () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);

    const entry = store.registerTokenSet({
      id: 'ghatana-tokens-v1',
      name: 'Ghatana Platform Tokens',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });

    expect(entry.id).toBe('ghatana-tokens-v1');
    expect(entry.name).toBe('Ghatana Platform Tokens');
    expect(entry.source).toBe('@ghatana/tokens');
    expect(entry.registeredAt).toBeTruthy();
  });

  it('retrieves the registered token set by id', () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    store.registerTokenSet({
      id: 'ghatana-tokens-v1',
      name: 'Ghatana Platform Tokens',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });

    const retrieved = store.getTokenSet('ghatana-tokens-v1');
    expect(retrieved).toBeDefined();
    expect(retrieved!.name).toBe('Ghatana Platform Tokens');
  });

  it('getAllTokenSets returns registered set', () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    store.registerTokenSet({
      id: 'ghatana-tokens-v1',
      name: 'Ghatana Platform Tokens',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });

    expect(store.getAllTokenSets()).toHaveLength(1);
    expect(store.getAllTokenSets()[0]?.id).toBe('ghatana-tokens-v1');
  });

  it('unregisters a token set', () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    store.registerTokenSet({
      id: 'ghatana-tokens-v1',
      name: 'Ghatana Platform Tokens',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });

    const removed = store.unregisterTokenSet('ghatana-tokens-v1');
    expect(removed).toBe(true);
    expect(store.getTokenSet('ghatana-tokens-v1')).toBeUndefined();
    expect(store.getAllTokenSets()).toHaveLength(0);
  });

  it('updates a token set and bumps updatedAt', async () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    store.registerTokenSet({
      id: 'ghatana-tokens-v1',
      name: 'Ghatana Platform Tokens',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });

    const original = store.getTokenSet('ghatana-tokens-v1')!;

    await new Promise((r) => setTimeout(r, 2));

    const newFile = { ...dtcgFile, $version: '1.1.0' } as Parameters<typeof store.registerTokenSet>[0]['tokens'];
    const updated = store.updateTokenSet('ghatana-tokens-v1', newFile);

    expect(updated).toBeDefined();
    expect(updated!.updatedAt).not.toBe(original.updatedAt);
  });

  it('DTCG validation succeeds for the wrapped token file', () => {
    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    const result = validateDTCGTokenFile(dtcgFile);
    expect(result.success).toBe(true);
  });

  it('cross-validates: DTCG schema and registry agree on canonical tokens', () => {
    const dtcgResult = validateTokenRegistryDTCG(tokens as unknown as Record<string, unknown>);
    expect(dtcgResult.success).toBe(true);

    const dtcgFile = wrapAsDTCGTokenFile(tokens as unknown as Record<string, unknown>);
    const fileResult = validateDTCGTokenFile(dtcgFile);
    expect(fileResult.success).toBe(true);

    store.registerTokenSet({
      id: 'cross-validate',
      name: 'Cross Validation',
      tokens: dtcgFile as Parameters<typeof store.registerTokenSet>[0]['tokens'],
      source: '@ghatana/tokens',
      version: '0.1.0',
    });
    const stored = store.getTokenSet('cross-validate');
    expect(stored).toBeDefined();
  });
});
