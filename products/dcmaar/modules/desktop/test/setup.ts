// Global test setup
import { vi, beforeAll, afterAll, afterEach } from 'vitest';
import { WebSocket } from 'ws';

// Mock WebSocket globally
// @ts-ignore
global.WebSocket = WebSocket;

// Mock console methods to keep test output clean
const consoleError = console.error;
const consoleWarn = console.warn;

beforeAll(() => {
  // Mock any global setup here
  console.error = (...args) => {
    // Don't log expected errors in tests
    if (args[0]?.includes('intentional error')) return;
    consoleError(...args);
  };
  
  console.warn = (...args) => {
    // Don't log expected warnings in tests
    if (args[0]?.includes('deprecated')) return;
    consoleWarn(...args);
  };
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

afterAll(() => {
  // Restore console methods
  console.error = consoleError;
  console.warn = consoleWarn;
});

// Ensure tests use the real WebSocket implementation
vi.mock('ws', async () => {
  const actual = await vi.importActual<typeof import('ws')>('ws');
  return actual;
});

// Mock @tanstack/react-query to avoid resolution issues in tests
vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn((options: any) => ({
    data: undefined,
    error: null,
    isLoading: false,
    isError: false,
    isSuccess: true,
    refetch: vi.fn(),
    ...options,
  })),
  useMutation: vi.fn(() => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isLoading: false,
    isError: false,
    isSuccess: false,
    error: null,
    data: undefined,
    reset: vi.fn(),
  })),
  QueryClient: vi.fn().mockImplementation(() => ({
    setQueryData: vi.fn(),
    getQueryData: vi.fn(),
    invalidateQueries: vi.fn(),
    refetchQueries: vi.fn(),
    cancelQueries: vi.fn(),
    removeQueries: vi.fn(),
    clear: vi.fn(),
  })),
  QueryClientProvider: vi.fn(({ children }: any) => children),
  useQueryClient: vi.fn(() => ({
    setQueryData: vi.fn(),
    getQueryData: vi.fn(),
    invalidateQueries: vi.fn(),
  })),
}));
