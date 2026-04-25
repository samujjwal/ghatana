/**
 * TenantScopedMutation — thin wrapper around @tanstack/react-query
 * useMutation that auto-injects the active tenantId into every mutation.
 *
 * Guarantees:
 *   - No mutation is sent without a tenant scope
 *   - Tenant ID is visible in the mutationFn signature for auditability
 *   - Graceful degradation when tenant is not yet resolved
 *
 * @doc.type component
 * @doc.purpose Enforce tenant scoping on all backend mutations
 * @doc.layer frontend
 * @doc.pattern Higher-Order Hook
 */
import { useMutation, UseMutationOptions, UseMutationResult } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';

export interface TenantScopedVariables {
  tenantId: string;
}

export function useTenantScopedMutation<
  TData = unknown,
  TError = Error,
  TVariables extends Record<string, unknown> = Record<string, unknown>,
  TContext = unknown,
>(
  mutationFn: (variables: TVariables & TenantScopedVariables) => Promise<TData>,
  options?: Omit<UseMutationOptions<TData, TError, TVariables, TContext>, 'mutationFn'>,
): UseMutationResult<TData, TError, TVariables, TContext> {
  const tenantId = useAtomValue(tenantIdAtom);

  return useMutation<TData, TError, TVariables, TContext>({
    ...options,
    mutationFn: async (variables) => {
      if (!tenantId) {
        throw new Error('Tenant scope is required but no active tenant is selected.');
      }
      return mutationFn({ ...variables, tenantId } as TVariables & TenantScopedVariables);
    },
  });
}
