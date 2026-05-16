/**
 * ProductUnitIntentApplier - applies ProductUnitIntent to Kernel lifecycle system.
 *
 * @doc.type class
 * @doc.purpose Applies ProductUnitIntent from YAPPC and other producers to Kernel
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type {
  ProductUnitIntent,
  ProductUnitIntentApplicationResult,
} from '@ghatana/kernel-product-contracts';
import { validateProductUnitIntentDetailed } from '@ghatana/kernel-product-contracts';
import type { KernelLifecycleService } from './KernelLifecycleService.js';

export interface ProductUnitIntentApplierOptions {
  readonly kernelLifecycleService: KernelLifecycleService;
  readonly providerMode?: 'bootstrap' | 'platform';
}

export class ProductUnitIntentApplier {
  private readonly kernelLifecycleService: KernelLifecycleService;
  private readonly providerMode: 'bootstrap' | 'platform';

  constructor(options: ProductUnitIntentApplierOptions) {
    this.kernelLifecycleService = options.kernelLifecycleService;
    this.providerMode = options.providerMode ?? 'bootstrap';
  }

  /**
   * Applies a ProductUnitIntent to the Kernel system.
   *
   * @param intent The ProductUnitIntent to apply
   * @param applyMode Whether to preview or actually apply the intent
   * @returns ProductUnitIntentApplicationResult with the application status
   */
  async applyIntent(
    intent: ProductUnitIntent,
    applyMode: 'preview' | 'apply' = 'apply'
  ): Promise<ProductUnitIntentApplicationResult> {
    // Validate the intent
    const validation = validateProductUnitIntentDetailed(intent);
    if (!validation.valid) {
      return this.createFailedResult(intent, validation.errors, 'schema-invalid');
    }

    const correlationId = intent.producer.correlationId;

    try {
      // Check if the product unit already exists
      const existingProductUnit = await this.kernelLifecycleService.getProductUnit(
        intent.productUnit.id
      );

      if (existingProductUnit && intent.intentType === 'create') {
        return this.createFailedResult(
          intent,
          [`ProductUnit ${intent.productUnit.id} already exists`],
          'target-provider-mismatch'
        );
      }

      if (!existingProductUnit && intent.intentType === 'update') {
        return this.createFailedResult(
          intent,
          [`ProductUnit ${intent.productUnit.id} does not exist`],
          'target-provider-mismatch'
        );
      }

      // In preview mode, return previewed status without applying
      if (applyMode === 'preview') {
        return this.createPreviewResult(intent);
      }

      // In apply mode, register the product unit with the registry
      const result = await this.registerProductUnit(intent);

      if (!result.success) {
        return this.createFailedResult(
          intent,
          [result.error || 'Failed to register ProductUnit'],
          'registry-apply-failed'
        );
      }

      // Record provenance
      await this.recordProvenance(intent, correlationId);

      // Record lifecycle event
      await this.recordLifecycleEvent(intent, correlationId);

      // Record runtime truth
      await this.recordRuntimeTruth(intent, correlationId);

      return this.createSuccessResult(intent, result.ref || '');
    } catch (error) {
      return this.createFailedResult(
        intent,
        [error instanceof Error ? error.message : String(error)],
        'kernel-service-unreachable'
      );
    }
  }

  private async registerProductUnit(intent: ProductUnitIntent): Promise<{ success: boolean; ref?: string; error?: string }> {
    // This would call the registry provider to register the product unit
    // For now, return a success result with a mock ref
    return {
      success: true,
      ref: `registry:${intent.productUnit.id}`,
    };
  }

  private async recordProvenance(_intent: ProductUnitIntent, _correlationId: string): Promise<void> {
    // This would use the provenance provider to record the intent application
    // For now, this is a no-op
  }

  private async recordLifecycleEvent(_intent: ProductUnitIntent, _correlationId: string): Promise<void> {
    // This would use the event provider to record the lifecycle event
    // For now, this is a no-op
  }

  private async recordRuntimeTruth(_intent: ProductUnitIntent, _correlationId: string): Promise<void> {
    // This would use the runtime truth provider to record the current state
    // For now, this is a no-op
  }

  private createSuccessResult(intent: ProductUnitIntent, applicationRef: string): ProductUnitIntentApplicationResult {
    return {
      schemaVersion: '1.0.0',
      intentId: intent.intentId,
      status: 'applied',
      productUnitId: intent.productUnit.id,
      correlationId: intent.producer.correlationId,
      providerMode: this.providerMode,
      registryProviderId: intent.target.registryProvider,
      sourceProviderId: intent.target.sourceProvider,
      applicationRef,
      lifecycleEventRefs: [`event:${intent.intentId}`],
      provenanceRefs: [`provenance:${intent.intentId}`],
      runtimeTruthRefs: [`runtime-truth:${intent.intentId}`],
      blockedReasons: [],
      errors: [],
      appliedAt: new Date().toISOString(),
    };
  }

  private createPreviewResult(intent: ProductUnitIntent): ProductUnitIntentApplicationResult {
    return {
      schemaVersion: '1.0.0',
      intentId: intent.intentId,
      status: 'previewed',
      productUnitId: intent.productUnit.id,
      correlationId: intent.producer.correlationId,
      providerMode: this.providerMode,
      registryProviderId: intent.target.registryProvider,
      sourceProviderId: intent.target.sourceProvider,
      previewRef: `preview:${intent.intentId}`,
      lifecycleEventRefs: [],
      provenanceRefs: [],
      runtimeTruthRefs: [],
      blockedReasons: [],
      errors: [],
    };
  }

  private createFailedResult(
    intent: ProductUnitIntent,
    errors: readonly string[],
    reasonCode: string
  ): ProductUnitIntentApplicationResult {
    return {
      schemaVersion: '1.0.0',
      intentId: intent.intentId,
      status: 'failed',
      productUnitId: intent.productUnit.id,
      correlationId: intent.producer.correlationId,
      providerMode: this.providerMode,
      registryProviderId: intent.target.registryProvider,
      sourceProviderId: intent.target.sourceProvider,
      lifecycleEventRefs: [],
      provenanceRefs: [],
      runtimeTruthRefs: [],
      blockedReasons: [reasonCode],
      errors,
    };
  }
}
