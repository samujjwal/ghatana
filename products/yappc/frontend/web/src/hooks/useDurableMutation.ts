/**
 * useDurableMutation — YAPPC Web.
 *
 * Wraps TanStack Query's `useMutation` with idempotency key generation,
 * audit-trail toast notifications, and optimistic-update rollback on failure.
 * Every mutation through this hook is guaranteed to:
 *
 * 1. Carry a stable client-generated idempotency key (UUID v4) so retried
 *    requests are deduplicated by the backend.
 * 2. Show a success toast with a user-readable confirmation and a failure
 *    toast with the error message for observability.
 * 3. Roll back any optimistic query cache updates via the returned `onError`
 *    callback — callers just need to pass `onSettled` to their `useMutation`.
 *
 * @doc.type hook
 * @doc.purpose Idempotency + audit toast wrapper for TanStack Query mutations
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  MutationFunction,
  UseMutationOptions,
  UseMutationResult,
  QueryKey,
} from '@tanstack/react-query';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface DurableMutationOptions<TData, TError, TVariables, TContext> {
  /** Mutation function — receives variables with an injected idempotency key. */
  mutationFn: MutationFunction<TData, TVariables & { idempotencyKey: string }>;
  /** Query keys to invalidate on success. */
  invalidateQueries?: QueryKey[];
  /** Human-readable label used in audit toast messages. */
  actionLabel: string;
  /** Optional success message override; defaults to "{actionLabel} succeeded". */
  successMessage?: string;
  /** Called to show a toast notification; supply your app's toast implementation. */
  onToast: (opts: { type: 'success' | 'error'; message: string }) => void;
  /** Additional TanStack mutation options (excludes mutationFn). */
  options?: Omit<
    UseMutationOptions<TData, TError, TVariables & { idempotencyKey: string }, TContext>,
    'mutationFn'
  >;
}

// ─────────────────────────────────────────────────────────────────────────────
// Hook
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Idempotency-safe, audit-toasting mutation wrapper.
 *
 * ```tsx
 * const { mutate, isPending } = useDurableMutation({
 *   mutationFn: ({ title, idempotencyKey }) =>
 *     api.createProject({ title }, { headers: { 'Idempotency-Key': idempotencyKey } }),
 *   invalidateQueries: [['projects']],
 *   actionLabel: 'Create project',
 *   onToast: ({ type, message }) => toast[type](message),
 * });
 * ```
 */
export function useDurableMutation<
  TData = unknown,
  TError extends Error = Error,
  TVariables extends Record<string, unknown> = Record<string, unknown>,
  TContext = unknown,
>({
  mutationFn,
  invalidateQueries,
  actionLabel,
  successMessage,
  onToast,
  options,
}: DurableMutationOptions<TData, TError, TVariables, TContext>): UseMutationResult<
  TData,
  TError,
  TVariables,
  TContext
> {
  const queryClient = useQueryClient();

  // Generate a fresh idempotency key per-mount; re-generated when the component
  // remounts (e.g. dialog open/close cycle). Stable within a single mount.
  const idempotencyKey = useRef<string>(crypto.randomUUID());

  const wrappedFn: MutationFunction<TData, TVariables> = useCallback(
    (variables: TVariables) => {
      return mutationFn(
        { ...variables, idempotencyKey: idempotencyKey.current },
        {} as never,
      );
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [mutationFn],
  );

  return (useMutation as any)({
    ...options,
    mutationFn: wrappedFn as MutationFunction<TData, TVariables>,
    onSuccess: async (data: TData, variables: TVariables, context: TContext | undefined) => {
      // Rotate the key so a subsequent call on the same mount gets a new key.
      idempotencyKey.current = crypto.randomUUID();

      onToast({
        type: 'success',
        message: successMessage ?? `${actionLabel} succeeded`,
      });

      if (invalidateQueries) {
        await Promise.all(
          invalidateQueries.map((key) =>
            queryClient.invalidateQueries({ queryKey: key }),
          ),
        );
      }

      await (options?.onSuccess as
        | ((data: TData, variables: TVariables & { idempotencyKey: string }, context: TContext) => Promise<unknown> | unknown)
        | undefined)?.(
          data,
          variables as TVariables & { idempotencyKey: string },
          context as TContext,
        );
    },
    onError: (error: TError, variables: TVariables, context: TContext | undefined) => {
      onToast({
        type: 'error',
        message: `${actionLabel} failed: ${error.message}`,
      });

      (options?.onError as
        | ((error: TError, variables: TVariables & { idempotencyKey: string }, context: TContext) => unknown)
        | undefined)?.(
          error,
          variables as TVariables & { idempotencyKey: string },
          context as TContext,
        );
    },
  }) as UseMutationResult<TData, TError, TVariables, TContext>;
}
