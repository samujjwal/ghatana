/**
 * Mock for @tanstack/react-query
 * Used in tests to avoid dependency resolution issues
 */

import { vi } from 'vitest';

export const useQuery = vi.fn((options: any) => ({
  data: undefined,
  error: null,
  isLoading: false,
  isError: false,
  isSuccess: true,
  refetch: vi.fn(),
  ...options,
}));

export const useMutation = vi.fn(() => ({
  mutate: vi.fn(),
  mutateAsync: vi.fn(),
  isLoading: false,
  isError: false,
  isSuccess: false,
  error: null,
  data: undefined,
  reset: vi.fn(),
}));

export const QueryClient = vi.fn().mockImplementation(() => ({
  setQueryData: vi.fn(),
  getQueryData: vi.fn(),
  invalidateQueries: vi.fn(),
  refetchQueries: vi.fn(),
  cancelQueries: vi.fn(),
  removeQueries: vi.fn(),
  clear: vi.fn(),
}));

export const QueryClientProvider = vi.fn(({ children }: any) => children);

export const useQueryClient = vi.fn(() => new QueryClient());
