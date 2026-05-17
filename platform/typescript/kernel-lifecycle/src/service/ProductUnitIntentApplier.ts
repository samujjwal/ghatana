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
    // Delegate to KernelLifecycleService so intent validation and error semantics stay centralized.
    return this.kernelLifecycleService.applyProductUnitIntent(intent, {
      mode: this.providerMode,
      allowWrite: applyMode === 'apply',
    });
  }
}
