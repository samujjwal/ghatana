import { useMutation, UseMutationOptions, UseMutationResult } from '@tanstack/react-query';
import { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';
import { useApiErrorHandler } from './useApiErrorHandler';

export interface UseApiMutationOptions<TData, TVariables, TError = AxiosError>
  extends Omit<UseMutationOptions<AxiosResponse<TData>, TError, TVariables>, 'mutationFn'> {
  mutationFn: (variables: TVariables, config?: AxiosRequestConfig) => Promise<AxiosResponse<TData>>;
  errorMessage?: string;
  successMessage?: string;
}

/**
 * Custom hook for handling API mutations with React Query and error handling
 */
export function useApiMutation<TData = unknown, TVariables = void, TError = AxiosError>(
  options: UseApiMutationOptions<TData, TVariables, TError>
): UseMutationResult<AxiosResponse<TData>, TError, TVariables> {
  const { 
    mutationFn, 
    errorMessage = 'An error occurred while processing your request.',
    successMessage,
    onError,
    onSuccess,
    ...mutationOptions 
  } = options;
  
  const { handleError, enqueueSnackbar } = useApiErrorHandler();

  return useMutation({
    mutationFn: async (variables: TVariables) => {
      try {
        return await mutationFn(variables);
      } catch (error) {
        handleError(error, errorMessage);
        throw error;
      }
    },
    ...mutationOptions,
    onError: (error: TError, variables: TVariables, context?: unknown) => {
      if (onError) {
        // Pass undefined for the additional context parameter
        onError(error, variables, undefined, context as any);
      }
    },
    onSuccess: (data: AxiosResponse<TData>, variables: TVariables, context?: unknown) => {
      if (successMessage) {
        enqueueSnackbar(successMessage, { variant: 'success' });
      }
      
      if (onSuccess) {
        // Pass undefined for the additional context parameter  
        onSuccess(data, variables, undefined, context as any);
      }
    },
  });
}

export default useApiMutation;
