import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { env } from '../../utils/env';

/**
 * Configuration for the API client
 */
const apiConfig: AxiosRequestConfig = {
  baseURL: env.apiBaseUrl,
  timeout: 30000, // 30 seconds
  headers: {
    'Content-Type': 'application/json',
    'X-App-Version': env.appVersion,
  },
  withCredentials: true, // Important for cookies/auth
};

/**
 * Create an Axios instance with default configuration
 */
const apiClient: AxiosInstance = axios.create(apiConfig);

// Request interceptor for API calls
apiClient.interceptors.request.use(
  (config) => {
    // You can add auth tokens here
    // const token = getAuthToken();
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    
    // Log request in development
    if (import.meta.env.DEV) {
      console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`, {
        params: config.params,
        data: config.data,
      });
    }
    
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// Response interceptor for API calls
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    // Log response in development
    if (import.meta.env.DEV) {
      console.log(
        `[API] ${response.config.method?.toUpperCase()} ${response.config.url} ${response.status}`,
        response.data
      );
    }
    
    return response;
  },
  (error: AxiosError) => {
    // Handle common errors
    if (error.response) {
      // The request was made and the server responded with a status code
      // that falls out of the range of 2xx
      console.error('[API] Response error:', {
        status: error.response.status,
        statusText: error.response.statusText,
        url: error.config?.url,
        method: error.config?.method,
        data: error.response.data,
      });
      
      // Handle specific status codes
      if (error.response.status === 401) {
        // Handle unauthorized (e.g., redirect to login)
        // router.navigate('/login');
      }
    } else if (error.request) {
      // The request was made but no response was received
      console.error('[API] No response received:', error.request);
    } else {
      // Something happened in setting up the request that triggered an Error
      console.error('[API] Request error:', error.message);
    }
    
    return Promise.reject(error);
  }
);

/**
 * Helper function to make API requests with proper typing
 */
export async function apiRequest<T = any>(
  config: AxiosRequestConfig
): Promise<AxiosResponse<T>> {
  try {
    return await apiClient.request<T>(config);
  } catch (error) {
    // Re-throw the error to be handled by the caller
    throw error;
  }
}

/**
 * Helper function for GET requests
 */
export async function get<T = any>(
  url: string,
  config?: AxiosRequestConfig
): Promise<AxiosResponse<T>> {
  return apiRequest<T>({ ...config, method: 'GET', url });
}

/**
 * Helper function for POST requests
 */
export async function post<T = any>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<AxiosResponse<T>> {
  return apiRequest<T>({ ...config, method: 'POST', url, data });
}

/**
 * Helper function for PUT requests
 */
export async function put<T = any>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<AxiosResponse<T>> {
  return apiRequest<T>({ ...config, method: 'PUT', url, data });
}

/**
 * Helper function for DELETE requests
 */
export async function del<T = any>(
  url: string,
  config?: AxiosRequestConfig
): Promise<AxiosResponse<T>> {
  return apiRequest<T>({ ...config, method: 'DELETE', url });
}

export default {
  // HTTP methods
  get,
  post,
  put,
  delete: del,
  
  // Axios instance for custom requests
  client: apiClient,
  
  // Helper to set auth token
  setAuthToken: (token: string | null) => {
    if (token) {
      apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    } else {
      delete apiClient.defaults.headers.common['Authorization'];
    }
  },
  
  // Helper to set base URL
  setBaseURL: (baseURL: string) => {
    apiClient.defaults.baseURL = baseURL;
  },
};
