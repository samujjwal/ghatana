import { describe, it, expect } from 'vitest';
import * as path from 'node:path';
import * as url from 'node:url';
import { promises as fs } from 'node:fs';
import { ProductLifecyclePlanner } from '../planning/ProductLifecyclePlanner.js';

const REPO_ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '../../../../..');

/**
 * P2.8 / P2.9: Preserve disabled/partial state for PHR, Finance, FlashIt, Data Cloud, and YAPPC.
 *
 * These tests assert:
 * 1. Disabled products cannot produce executable lifecycle plans.
 * 2. Each product's reason codes are present in the registry.
 * 3. Accidental enablement is prevented through explicit regression coverage.
 */

async function loadProductRegistry(): Promise<Record<string, unknown>> {
  const registryPath = path.join(REPO_ROOT, 'config', 'canonical-product-registry.json');
  const raw = await fs.readFile(registryPath, 'utf8');
  const parsed = JSON.parse(raw) as { registry: Record<string, Record<string, unknown>> };
  return parsed.registry;
}

describe('PHR disabled lifecycle plan', () => {
  it('should reject lifecycle execution for PHR', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    await expect(planner.loadProductConfig('phr')).rejects.toThrow(
      'is not ready for lifecycle execution',
    );
  });

  it('should have required reason codes in the registry', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'] as Record<string, unknown>;
    const readiness = phr['lifecycleReadiness'] as Record<string, unknown>;
    const reasonCodes = readiness['reasonCodes'] as string[];

    expect(reasonCodes).toContain('requires-consent-gate');
    expect(reasonCodes).toContain('requires-pii-classification');
    expect(reasonCodes).toContain('requires-audit-evidence');
    expect(reasonCodes).toContain('requires-fhir-contract-validation');
    expect(reasonCodes).toContain('requires-data-sovereignty-gate');
  });

  it('should remain lifecycle disabled', async () => {
    const registry = await loadProductRegistry();
    const phr = registry['phr'] as Record<string, unknown>;
    const lifecycle = phr['lifecycle'] as Record<string, unknown>;

    expect(lifecycle['enabled']).toBe(false);
    expect(phr['lifecycleStatus']).toBe('planned');
  });
});

describe('Finance disabled lifecycle plan', () => {
  it('should reject lifecycle execution for Finance', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    await expect(planner.loadProductConfig('finance')).rejects.toThrow(
      'is not ready for lifecycle execution',
    );
  });

  it('should have required reason codes in the registry', async () => {
    const registry = await loadProductRegistry();
    const finance = registry['finance'] as Record<string, unknown>;
    const readiness = finance['lifecycleReadiness'] as Record<string, unknown>;
    const reasonCodes = readiness['reasonCodes'] as string[];

    expect(reasonCodes).toContain('requires-regulatory-gates');
    expect(reasonCodes).toContain('requires-multi-module-build-validation');
    expect(reasonCodes).toContain('requires-promotion-approval');
    expect(reasonCodes).toContain('requires-portal-operator-sdk-adapters');
  });

  it('should remain lifecycle disabled', async () => {
    const registry = await loadProductRegistry();
    const finance = registry['finance'] as Record<string, unknown>;
    const lifecycle = finance['lifecycle'] as Record<string, unknown>;

    expect(lifecycle['enabled']).toBe(false);
    expect(finance['lifecycleStatus']).toBe('planned');
  });
});

describe('FlashIt disabled lifecycle plan', () => {
  it('should reject lifecycle execution for FlashIt', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    await expect(planner.loadProductConfig('flashit')).rejects.toThrow(
      'is not ready for lifecycle execution',
    );
  });

  it('should have required reason codes in the registry', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'] as Record<string, unknown>;
    const readiness = flashit['lifecycleReadiness'] as Record<string, unknown>;
    const reasonCodes = readiness['reasonCodes'] as string[];

    expect(reasonCodes).toContain('requires-mobile-adapters');
    expect(reasonCodes).toContain('requires-preview-security-gate');
    expect(reasonCodes).toContain('requires-personal-data-classification');
    expect(reasonCodes).toContain('requires-mobile-bundle-artifacts');
  });

  it('should remain lifecycle disabled', async () => {
    const registry = await loadProductRegistry();
    const flashit = registry['flashit'] as Record<string, unknown>;
    const lifecycle = flashit['lifecycle'] as Record<string, unknown>;

    expect(lifecycle['enabled']).toBe(false);
    expect(flashit['lifecycleStatus']).toBe('planned');
  });
});

describe('Data Cloud platform provider blocked state', () => {
  it('should not have lifecycle execution enabled', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;

    expect(dataCloud['kind']).toBe('platform-provider');
    expect(dataCloud['lifecycle']).toBeUndefined();
  });

  it('should have required reason codes in the registry', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;
    const readiness = dataCloud['lifecycleReadiness'] as Record<string, unknown>;
    const reasonCodes = readiness['reasonCodes'] as string[];

    expect(reasonCodes).toContain('platform-provider-mode-required');
    expect(reasonCodes).toContain('requires-bootstrap-platform-separation');
    expect(reasonCodes).toContain('requires-runtime-truth-provider');
  });

  it('should remain platform-provider kind without lifecycle config', async () => {
    const registry = await loadProductRegistry();
    const dataCloud = registry['data-cloud'] as Record<string, unknown>;

    expect(dataCloud['kind']).toBe('platform-provider');
    expect(dataCloud['lifecycleStatus']).toBeUndefined();
    expect(dataCloud['lifecycleConfigPath']).toBeUndefined();
  });
});

describe('YAPPC creator lifecycle separation blocked state', () => {
  it('should not have lifecycle execution enabled', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;

    expect(yappc['kind']).toBe('platform-provider');
    expect(yappc['lifecycle']).toBeUndefined();
  });

  it('should have required reason codes in the registry', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;
    const readiness = yappc['lifecycleReadiness'] as Record<string, unknown>;
    const reasonCodes = readiness['reasonCodes'] as string[];

    expect(reasonCodes).toContain('platform-provider-mode-required');
    expect(reasonCodes).toContain('creator-lifecycle-distinct-from-kernel');
    expect(reasonCodes).toContain('artifact-intelligence-evidence-contracts-ready');
  });

  it('should remain platform-provider kind without lifecycle config', async () => {
    const registry = await loadProductRegistry();
    const yappc = registry['yappc'] as Record<string, unknown>;

    expect(yappc['kind']).toBe('platform-provider');
    expect(yappc['lifecycleStatus']).toBeUndefined();
    expect(yappc['lifecycleConfigPath']).toBeUndefined();
  });
});

describe('Digital Marketing remains enabled pilot', () => {
  it('should load product configuration successfully', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const config = await planner.loadProductConfig('digital-marketing');

    expect(config.productId).toBe('digital-marketing');
    expect(config.lifecycleProfile).toBe('standard-web-api-product');
  });

  it('should remain lifecycle enabled in the registry', async () => {
    const registry = await loadProductRegistry();
    const dm = registry['digital-marketing'] as Record<string, unknown>;
    const lifecycle = dm['lifecycle'] as Record<string, unknown>;

    expect(dm['lifecycleStatus']).toBe('enabled');
    expect(lifecycle['enabled']).toBe(true);
  });

  it('should produce a valid build plan', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'build');

    expect(plan.productId).toBe('digital-marketing');
    expect(plan.phase).toBe('build');
    expect(plan.surfaces.length).toBeGreaterThan(0);
    expect(plan.steps.length).toBeGreaterThan(0);
  });
});
