import { useQuery, UseQueryOptions, UseQueryResult } from '@tanstack/react-query';
import { AxiosError, AxiosResponse } from 'axios';
import { useApiErrorHandler } from './useApiErrorHandler';

interface UseApiQueryOptions<TData, TError = AxiosError> 
  extends Omit<UseQueryOptions<AxiosResponse<TData>, TError, TData>, 'queryFn' | 'queryKey'> {
  errorMessage?: string;
}

function useApiQuery<TData = any, TError = AxiosError>(
  queryKey: readonly unknown[],
  queryFn: () => Promise<AxiosResponse<TData>>,
  options: UseApiQueryOptions<TData, TError> = {}
): UseQueryResult<TData, TError> {
  const { errorMessage, ...queryOptions } = options;
  const { handleError } = useApiErrorHandler();

  return useQuery({
    queryKey,
    queryFn: async () => {
      try {
        return await queryFn();
      } catch (error) {
        handleError(error, errorMessage);
        throw error;
      }
    },
    select: (response: AxiosResponse<TData>) => response.data,
    ...queryOptions,
  });
}

export default useApiQuery;