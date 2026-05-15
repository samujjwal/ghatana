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
