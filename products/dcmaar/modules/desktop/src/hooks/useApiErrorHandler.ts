import { AxiosError } from 'axios';
import { useCallback } from 'react';

interface ApiError {
  message?: string;
  errors?: Record<string, string[]>;
  status?: number;
}

/**
 * Custom hook to handle API errors consistently across the application
 */
export function useApiErrorHandler() {
  // Simple notification function - can be replaced with toast library later
  const enqueueSnackbar = useCallback((message: string, options?: { variant?: string }) => {
    console.log(`${options?.variant?.toUpperCase() || 'INFO'}: ${message}`);
  }, []);

  /**
   * Handle API errors and show appropriate error messages
   */
  const handleError = useCallback((error: unknown, defaultMessage: string = 'An error occurred') => {
    const axiosError = error as AxiosError<ApiError>;
    let errorMessage = defaultMessage;

    if (axiosError.response) {
      const { data, status } = axiosError.response;
      
      // Handle different error statuses
      switch (status) {
        case 400:
          errorMessage = data.message || 'Bad request';
          // Handle validation errors
          if (data.errors) {
            const errorMessages = Object.values(data.errors).flat();
            errorMessage = errorMessages.join('\n');
          }
          break;
        case 401:
          errorMessage = 'Unauthorized. Please log in again.';
          // TODO: Handle logout or redirect to login
          break;
        case 403:
          errorMessage = 'You do not have permission to perform this action.';
          break;
        case 404:
          errorMessage = 'The requested resource was not found.';
          break;
        case 500:
          errorMessage = 'An internal server error occurred. Please try again later.';
          break;
        default:
          errorMessage = data?.message || `Error: ${status}`;
      }
    } else if (axiosError.request) {
      // The request was made but no response was received
      errorMessage = 'No response from server. Please check your connection.';
    } else {
      // Something happened in setting up the request
      errorMessage = axiosError.message || defaultMessage;
    }

    // Show error message to the user
    enqueueSnackbar(errorMessage, { variant: 'error' });
    
    // Log the full error in development
    if (process.env.NODE_ENV === 'development') {
      console.error('API Error:', error);
    }
    
    // Re-throw the error for further handling if needed
    throw error;
  }, [enqueueSnackbar]);

  return { handleError, enqueueSnackbar };
}

export default useApiErrorHandler;
