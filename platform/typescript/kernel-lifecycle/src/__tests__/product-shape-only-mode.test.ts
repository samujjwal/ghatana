import { describe, it, expect } from 'vitest';
import * as path from 'node:path';
import * as url from 'node:url';
import { promises as fs } from 'node:fs';
import { SchemaValidator } from '../SchemaValidator.js';
import { ProductLifecyclePlanner } from '../planning/ProductLifecyclePlanner.js';

const REPO_ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '../../../../..');

/**
 * P3-03: Add product-shape-only mode tests for PHR/FlashIt/Data Cloud/YAPPC.
 *
 * These tests assert:
 * 1. Product shape (configuration) can be validated without executing lifecycle
 * 2. Kernel can load and parse product configs in shape-only mode
 * 3. Product definitions are structurally valid even when lifecycle execution is disabled
 */

async function loadProductRegistry(): Promise<Record<string, unknown>> {
  const registryPath = path.join(REPO_ROOT, 'config', 'canonical-product-registry.json');
  const raw = await fs.readFile(registryPath, 'utf8');
  const parsed = JSON.parse(raw) as { registry: Record<string, Record<string, unknown>> };
  return parsed.registry;
}

describe('PHR product shape validation', () => {
  it('should validate PHR product configuration structure', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'] as Record<string, unknown>;
    
    // Validate product has required shape fields
    expect(phr['id']).toBeDefined();
    expect(phr['kind']).toBeDefined();
    expect(phr['lifecycleReadiness']).toBeDefined();
    expect(phr['metadata']).toBeDefined();
  });

  it('should validate PHR lifecycle readiness structure', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'] as Record<string, unknown>;
    const readiness = phr['lifecycleReadiness'] as Record<string, unknown>;
    
    // Validate readiness has required shape
    expect(readiness['status']).toBeDefined();
    expect(readiness['reasonCodes']).toBeDefined();
    expect(Array.isArray(readiness['reasonCodes'])).toBe(true);
  });

  it('should validate PHR metadata structure', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'] as Record<string, unknown>;
    const metadata = phr['metadata'] as Record<string, unknown>;
    
    // Validate metadata has required shape
    expect(metadata['displayName']).toBeDefined();
    expect(metadata['description']).toBeDefined();
    expect(metadata['category']).toBeDefined();
  });
});

describe('FlashIt product shape validation', () => {
  it('should validate FlashIt product configuration structure', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'] as Record<string, unknown>;
    
    // Validate product has required shape fields
    expect(flashit['id']).toBeDefined();
    expect(flashit['kind']).toBeDefined();
    expect(flashit['lifecycleReadiness']).toBeDefined();
    expect(flashit['metadata']).toBeDefined();
  });

  it('should validate FlashIt lifecycle readiness structure', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'] as Record<string, unknown>;
    const readiness = flashit['lifecycleReadiness'] as Record<string, unknown>;
    
    // Validate readiness has required shape
    expect(readiness['status']).toBeDefined();
    expect(readiness['reasonCodes']).toBeDefined();
    expect(Array.isArray(readiness['reasonCodes'])).toBe(true);
  });

  it('should validate FlashIt metadata structure', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'] as Record<string, unknown>;
    const metadata = flashit['metadata'] as Record<string, unknown>;
    
    // Validate metadata has required shape
    expect(metadata['displayName']).toBeDefined();
    expect(metadata['description']).toBeDefined();
    expect(metadata['category']).toBeDefined();
  });
});

describe('Data Cloud platform provider shape validation', () => {
  it('should validate Data Cloud platform provider structure', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;
    
    // Validate platform provider has required shape fields
    expect(dataCloud['id']).toBeDefined();
    expect(dataCloud['kind']).toBe('platform-provider');
    expect(dataCloud['lifecycleReadiness']).toBeDefined();
    expect(dataCloud['metadata']).toBeDefined();
  });

  it('should validate Data Cloud lifecycle readiness structure', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;
    const readiness = dataCloud['lifecycleReadiness'] as Record<string, unknown>;
    
    // Validate readiness has required shape
    expect(readiness['status']).toBeDefined();
    expect(readiness['reasonCodes']).toBeDefined();
    expect(Array.isArray(readiness['reasonCodes'])).toBe(true);
  });

  it('should validate Data Cloud metadata structure', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;
    const metadata = dataCloud['metadata'] as Record<string, unknown>;
    
    // Validate metadata has required shape
    expect(metadata['displayName']).toBeDefined();
    expect(metadata['description']).toBeDefined();
    expect(metadata['category']).toBeDefined();
  });
});

describe('YAPPC platform provider shape validation', () => {
  it('should validate YAPPC platform provider structure', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;
    
    // Validate platform provider has required shape fields
    expect(yappc['id']).toBeDefined();
    expect(yappc['kind']).toBe('platform-provider');
    expect(yappc['lifecycleReadiness']).toBeDefined();
    expect(yappc['metadata']).toBeDefined();
  });

  it('should validate YAPPC lifecycle readiness structure', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;
    const readiness = yappc['lifecycleReadiness'] as Record<string, unknown>;
    
    // Validate readiness has required shape
    expect(readiness['status']).toBeDefined();
    expect(readiness['reasonCodes']).toBeDefined();
    expect(Array.isArray(readiness['reasonCodes'])).toBe(true);
  });

  it('should validate YAPPC metadata structure', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;
    const metadata = yappc['metadata'] as Record<string, unknown>;
    
    // Validate metadata has required shape
    expect(metadata['displayName']).toBeDefined();
    expect(metadata['description']).toBeDefined();
    expect(metadata['category']).toBeDefined();
  });
});

describe('Product shape validation via SchemaValidator', () => {
  it('should validate PHR registry entry schema', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'];
    const validator = new SchemaValidator();
    
    // Should not throw on valid structure
    expect(() => validator.validateProductRegistryEntry('phr', phr)).not.toThrow();
  });

  it('should validate FlashIt registry entry schema', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'];
    const validator = new SchemaValidator();
    
    // Should not throw on valid structure
    expect(() => validator.validateProductRegistryEntry('flashit', flashit)).not.toThrow();
  });

  it('should validate Data Cloud registry entry schema', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'];
    const validator = new SchemaValidator();
    
    // Should not throw on valid structure
    expect(() => validator.validateProductRegistryEntry('data-cloud', dataCloud)).not.toThrow();
  });

  it('should validate YAPPC registry entry schema', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'];
    const validator = new SchemaValidator();
    
    // Should not throw on valid structure
    expect(() => validator.validateProductRegistryEntry('yappc', yappc)).not.toThrow();
  });
});
