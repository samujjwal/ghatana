/**
 * Minimal stub for @tanstack/react-query — used in test environments
 * where the real package is not installed but the module is pulled in
 * transitively via @yappc/state barrel exports.
 *
 * Only stubs the subset of exports that appear in the codebase.
 */
import { vi } from 'vitest';

export const useQuery = vi.fn().mockReturnValue({
  data: undefined,
  isLoading: false,
  isError: false,
  error: null,
  refetch: vi.fn(),
});

export const useMutation = vi.fn().mockReturnValue({
  mutate: vi.fn(),
  mutateAsync: vi.fn(),
  isLoading: false,
  isError: false,
  error: null,
});

export const QueryClient = vi.fn().mockImplementation(() => ({}));

export const QueryClientProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => children as React.ReactElement;

export const useQueryClient = vi.fn().mockReturnValue({
  invalidateQueries: vi.fn(),
  setQueryData: vi.fn(),
  getQueryData: vi.fn(),
});
