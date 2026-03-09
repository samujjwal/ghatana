type ReactNode = import('react').ReactNode;
type FC<P = Record<string, unknown>> = import('react').FC<P>;

declare module '@tanstack/react-query' {
  export type QueryKey = readonly unknown[] | string;

  export interface QueryClientConfig {
    defaultOptions?: {
      queries?: Record<string, unknown>;
      mutations?: Record<string, unknown>;
    };
  }

  export class QueryClient {
    constructor(config?: QueryClientConfig);
    invalidateQueries(options?: { queryKey?: QueryKey }): Promise<void>;
  }

  export interface QueryClientProviderProps {
    client: QueryClient;
    children?: ReactNode;
  }

  export const QueryClientProvider: FC<QueryClientProviderProps>;

  export interface QueryObserverResult<TData = unknown, TError = unknown> {
    data: TData | undefined;
    error: TError | null;
    isLoading: boolean;
    isError: boolean;
    refetch: () => Promise<void>;
  }

  export interface UseQueryOptions<TData = unknown, TError = unknown> {
    queryKey: QueryKey;
    queryFn: () => Promise<TData>;
    enabled?: boolean;
    staleTime?: number;
    refetchInterval?: number;
    retry?: number | boolean;
    suspense?: boolean;
  }

  export function useQuery<TData = unknown, TError = unknown>(
    options: UseQueryOptions<TData, TError>
  ): QueryObserverResult<TData, TError>;

  export interface UseMutationOptions<TData = unknown, TError = unknown, TVariables = void> {
    mutationFn: (variables: TVariables) => Promise<TData>;
    onSuccess?: (data: TData, variables: TVariables) => void;
    onError?: (error: TError, variables: TVariables) => void;
  }

  export interface UseMutationResult<TData = unknown, TError = unknown, TVariables = void> {
    mutate: (variables: TVariables) => void;
    mutateAsync: (variables: TVariables) => Promise<TData>;
    data: TData | undefined;
    error: TError | null;
    isLoading: boolean;
    isError: boolean;
    isPending: boolean;
  }

  export function useMutation<TData = unknown, TError = unknown, TVariables = void>(
    options: UseMutationOptions<TData, TError, TVariables>
  ): UseMutationResult<TData, TError, TVariables>;

  export function useQueryClient(): QueryClient;
}

export {};
