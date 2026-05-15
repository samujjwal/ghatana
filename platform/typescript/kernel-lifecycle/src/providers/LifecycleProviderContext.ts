/**
 * LifecycleProviderContext - lifecycle truth provider context.
 *
 * @doc.type module
 * @doc.purpose Provider context helpers for lifecycle execution
 * @doc.layer kernel-lifecycle
 * @doc.pattern Context
 */

import type {
  KernelLifecycleProviderContext,
  KernelProvider,
  RegistryProvider,
  SourceProvider,
} from '@ghatana/kernel-product-contracts';
import {
  KernelProviderModeRequirements,
  requireLifecycleProviderSet,
  validateKernelLifecycleProviderContext,
  type KernelLifecycleProviderContextValidationResult,
} from '@ghatana/kernel-product-contracts';

export interface LifecycleProviderContext extends KernelLifecycleProviderContext {
  readonly registryProvider?: RegistryProvider;
  readonly sourceProvider?: SourceProvider;
}

export type LifecycleProviderName = keyof Omit<LifecycleProviderContext, 'mode'>;

export function requireLifecycleContextProvider<TProvider extends KernelProvider | Record<string, KernelProvider>>(
  context: LifecycleProviderContext | undefined,
  providerName: LifecycleProviderName,
): TProvider {
  if (context === undefined) {
    throw new Error(`Kernel provider context is required for lifecycle provider: ${String(providerName)}`);
  }

  const provider = context[providerName];
  if (provider === undefined) {
    throw new Error(
      `Kernel ${context.mode} mode requires lifecycle provider: ${String(providerName)}`,
    );
  }

  return provider as unknown as TProvider;
}

export function validateLifecycleProviderContext(
  context: LifecycleProviderContext,
): KernelLifecycleProviderContextValidationResult {
  return validateKernelLifecycleProviderContext(context);
}

export function requireBootstrapLifecycleContext(
  context: LifecycleProviderContext | undefined,
  correlationId?: string,
): LifecycleProviderContext {
  if (context === undefined) {
    throw new Error(
      `Kernel bootstrap mode requires lifecycle provider context${formatCorrelationId(correlationId)}`,
    );
  }
  if (context.mode !== 'bootstrap') {
    throw new Error(
      `Expected bootstrap lifecycle provider context but received ${context.mode}${formatCorrelationId(correlationId)}`,
    );
  }
  try {
    requireLifecycleProviderSet(context, KernelProviderModeRequirements.bootstrap);
  } catch (error) {
    throw new Error(`${error instanceof Error ? error.message : String(error)}${formatCorrelationId(correlationId)}`);
  }
  return context;
}

export function requirePlatformLifecycleContext(
  context: LifecycleProviderContext | undefined,
  correlationId?: string,
): LifecycleProviderContext {
  if (context === undefined) {
    throw new Error(
      `Kernel platform mode requires lifecycle provider context${formatCorrelationId(correlationId)}`,
    );
  }
  if (context.mode !== 'platform') {
    throw new Error(
      `Expected platform lifecycle provider context but received ${context.mode}${formatCorrelationId(correlationId)}`,
    );
  }
  try {
    requireLifecycleProviderSet(context, KernelProviderModeRequirements.platform);
  } catch (error) {
    throw new Error(`${error instanceof Error ? error.message : String(error)}${formatCorrelationId(correlationId)}`);
  }

  // Platform mode must be backed by Data Cloud providers rather than file-backed fallbacks.
  const platformProviders = [
    ['events provider', context.events],
    ['artifacts provider', context.artifacts],
    ['health provider', context.health],
    ['approvals provider', context.approvals],
    ['provenance provider', context.provenance],
    ['memory provider', context.memory],
    ['runtimeTruth provider', context.runtimeTruth],
  ] as const;
  const invalidProviders = platformProviders
    .filter(([, provider]) => provider?.backingStore === 'file')
    .map(([providerName]) => providerName);
  if (invalidProviders.length > 0) {
    throw new Error(
      `Kernel platform mode requires Data Cloud-backed ${invalidProviders.join(', ')}${formatCorrelationId(correlationId)}`,
    );
  }

  return context;
}

function formatCorrelationId(correlationId: string | undefined): string {
  return correlationId === undefined ? '' : ` (correlationId=${correlationId})`;
}
