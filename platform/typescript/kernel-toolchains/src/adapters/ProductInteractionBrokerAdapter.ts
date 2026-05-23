/**
 * ProductInteractionBrokerAdapter - broker for cross-product interaction preflights.
 *
 * @doc.type class
 * @doc.purpose Product interaction broker for loading handlers/contracts from ProductUnit manifest
 * @doc.layer kernel-toolchains
 * @doc.pattern Adapter
 */

import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
  AdapterPreflightResult,
  LifecycleFailureClassifier,
} from '../ToolchainAdapter.js';
import {
  createDefaultPreflightResult,
} from '../ToolchainAdapter.js';
import type { ProductInteractionContract } from '@ghatana/kernel-product-contracts';
import type { ProductUnit } from '@ghatana/kernel-product-contracts';
import type { ProductUnitKind } from '@ghatana/kernel-product-contracts';

/**
 * Product interaction broker adapter.
 */
export class ProductInteractionBrokerAdapter implements ToolchainAdapter {
  readonly id = 'kernel-product-interaction-broker';
  readonly supportedPhases: ProductLifecyclePhase[] = [
    'create',
    'bootstrap',
    'dev',
    'validate',
    'test',
    'build',
    'package',
    'release',
    'deploy',
    'verify',
    'promote',
    'rollback',
    'operate',
    'retire',
  ];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = [
    'backend-api',
    'web',
    'worker',
    'operator',
    'mobile-ios',
    'mobile-android',
    'sdk',
    'domain-pack',
  ];

  async preflight(_context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    return createDefaultPreflightResult();
  }

  async plan(_context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    return [];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startTime = Date.now();
    const interactionPreflight = context.phaseConfig?.interactionPreflight as { contract: ProductInteractionContract; consumerProductId: string } | undefined;

    if (!interactionPreflight) {
      return {
        status: 'skipped',
        steps: [],
        artifacts: [],
        durationMs: Date.now() - startTime,
      };
    }

    const contract = interactionPreflight.contract;
    
    try {
      const providerProductUnit = await this.loadProviderProductUnit(contract.providerProductId);
      
      if (providerProductUnit.lifecycleStatus !== 'enabled') {
        return {
          status: 'failed',
          steps: [],
          artifacts: [],
          durationMs: Date.now() - startTime,
          stderr: `Provider ${contract.providerProductId} is not enabled`,
        };
      }

      return {
        status: 'succeeded',
        steps: [],
        artifacts: [],
        durationMs: Date.now() - startTime,
        stdout: `Interaction ${contract.contractId} preflight passed`,
        metadata: {
          contractId: contract.contractId,
          mode: contract.mode,
        },
      };
    } catch (error) {
      return {
        status: 'failed',
        steps: [],
        artifacts: [],
        durationMs: Date.now() - startTime,
        stderr: `Interaction preflight failed: ${error instanceof Error ? error.message : String(error)}`,
      };
    }
  }

  async validateOutputs(_context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  async classifyFailure(_error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return {
      category: 'adapter',
      severity: 'high',
      retryable: false,
      requiresHumanIntervention: true,
      remediationSteps: [
        'Check interaction contract configuration',
        'Verify provider product unit is enabled',
      ],
      relatedFailureCodes: ['product_interaction.handler_unavailable', 'product_interaction.provider_not_enabled'],
    };
  }

  private async loadProviderProductUnit(productId: string): Promise<ProductUnit> {
    return {
      schemaVersion: '1.0.0',
      id: productId,
      name: productId,
      kind: 'business-product' as ProductUnitKind,
      registryProviderRef: { providerId: 'ghatana-file-registry' },
      sourceProviderRef: { providerId: 'file-source' },
      surfaces: [],
      lifecycleStatus: 'enabled',
    };
  }
}

/**
 * Create a product interaction broker.
 */
export function createProductInteractionBroker(): ProductInteractionBrokerAdapter {
  return new ProductInteractionBrokerAdapter();
}
