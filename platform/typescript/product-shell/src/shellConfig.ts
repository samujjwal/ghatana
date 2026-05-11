import { useMemo } from 'react';
import type { ProductShellConfig } from './types';

export function createProductShellConfig(config: ProductShellConfig): ProductShellConfig {
  return config;
}

export function useStableProductShellConfig(
  factory: () => ProductShellConfig,
  dependencies: readonly unknown[],
): ProductShellConfig {
  return useMemo(factory, dependencies);
}
