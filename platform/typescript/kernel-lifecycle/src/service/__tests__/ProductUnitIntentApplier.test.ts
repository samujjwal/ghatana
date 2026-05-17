import { describe, expect, it, vi } from 'vitest';
import type {
  ProductUnitIntent,
  ProductUnitIntentApplicationResult,
} from '@ghatana/kernel-product-contracts';
import type { KernelLifecycleService } from '../KernelLifecycleService.js';
import { ProductUnitIntentApplier } from '../ProductUnitIntentApplier.js';

describe('ProductUnitIntentApplier', () => {
  it('delegates preview mode to KernelLifecycleService without local validation logic', async () => {
    const applyProductUnitIntent = vi.fn<[ProductUnitIntent, { mode?: 'bootstrap' | 'platform'; allowWrite: boolean }], Promise<ProductUnitIntentApplicationResult>>()
      .mockResolvedValue(createApplicationResult('previewed'));
    const applier = new ProductUnitIntentApplier({
      kernelLifecycleService: {
        applyProductUnitIntent,
      } as unknown as KernelLifecycleService,
      providerMode: 'bootstrap',
    });

    const intent = createIntent();
    const result = await applier.applyIntent(intent, 'preview');

    expect(result.status).toBe('previewed');
    expect(applyProductUnitIntent).toHaveBeenCalledWith(intent, {
      mode: 'bootstrap',
      allowWrite: false,
    });
  });

  it('delegates apply mode with allowWrite=true', async () => {
    const applyProductUnitIntent = vi.fn<[ProductUnitIntent, { mode?: 'bootstrap' | 'platform'; allowWrite: boolean }], Promise<ProductUnitIntentApplicationResult>>()
      .mockResolvedValue(createApplicationResult('applied'));
    const applier = new ProductUnitIntentApplier({
      kernelLifecycleService: {
        applyProductUnitIntent,
      } as unknown as KernelLifecycleService,
      providerMode: 'platform',
    });

    const intent = createIntent();
    const result = await applier.applyIntent(intent, 'apply');

    expect(result.status).toBe('applied');
    expect(applyProductUnitIntent).toHaveBeenCalledWith(intent, {
      mode: 'platform',
      allowWrite: true,
    });
  });
});

function createIntent(): ProductUnitIntent {
  return {
    schemaVersion: '1.0.0',
    intentId: 'intent:yappc:digital-marketing:corr-1',
    intentType: 'create',
    scope: {
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
    },
    producer: {
      id: 'yappc',
      type: 'yappc',
      correlationId: 'corr-1',
    },
    target: {
      registryProvider: 'ghatana-file-registry',
      sourceProvider: 'ghatana-file-registry',
    },
    productUnit: {
      schemaVersion: '1.0.0',
      id: 'digital-marketing',
      name: 'Digital Marketing',
      kind: 'business-product',
      surfaces: [
        {
          id: 'web',
          type: 'web',
          implementationStatus: 'implemented',
        },
      ],
    },
  };
}

function createApplicationResult(
  status: ProductUnitIntentApplicationResult['status']
): ProductUnitIntentApplicationResult {
  return {
    schemaVersion: '1.0.0',
    intentId: 'intent:yappc:digital-marketing:corr-1',
    status,
    productUnitId: 'digital-marketing',
    correlationId: 'corr-1',
    providerMode: 'bootstrap',
    registryProviderId: 'ghatana-file-registry',
    sourceProviderId: 'ghatana-file-registry',
    lifecycleEventRefs: [],
    provenanceRefs: [],
    runtimeTruthRefs: [],
    blockedReasons: [],
    errors: [],
  };
}
