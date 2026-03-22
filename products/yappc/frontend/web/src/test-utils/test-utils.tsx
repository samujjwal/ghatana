import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';

import { handlers } from './msw-handlers';

import type { RenderOptions, RenderResult} from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';

// Import the theme from the app
const theme = {
  colors: {
    primary: '#0070f3',
    secondary: '#1e88e5',
    background: '#ffffff',
    text: '#000000',
    // Add other theme properties as needed
  },
  // Add other theme properties as needed
};

// Default options for the query client (internal)
const createTestQueryClientInternal = (options?: unknown) => {
  return new QueryClient(options as unknown);
};

// Create a custom renderer that includes providers
const AllTheProviders = ({
  children,
  route = '/',
  initialEntries = ['/'],
  queryClient,
}: {
  children: ReactNode;
  route?: string;
  initialEntries?: string[];
  queryClient?: unknown;
}) => {
  const client = queryClient || createTestQueryClient();

  window.history.pushState({}, 'Test page', route);

  return (
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="*" element={children} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
};

// Custom render function with all providers
/**
 *
 */
type CustomRenderOptions = Omit<RenderOptions, 'wrapper'> & {
  route?: string;
  initialEntries?: string[];
  queryClient?: unknown;
};

/**
 *
 */
interface CustomRenderResult extends RenderResult {
  user: ReturnType<typeof userEvent.setup>;
}

const customRender = (
  ui: ReactElement,
  {
    route = '/',
    initialEntries = ['/'],
    queryClient,
    ...options
  }: CustomRenderOptions = {}
): CustomRenderResult => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <AllTheProviders
      route={route}
      initialEntries={initialEntries}
      queryClient={queryClient}
    >
      {children}
    </AllTheProviders>
  );

  const result = render(ui, { wrapper, ...options }) as CustomRenderResult;
  result.user = userEvent.setup();
  return result;
};

// Re-export everything from @testing-library/react
export * from '@testing-library/react';

export * from '@testing-library/user-event';

// Override the render method
export { customRender as render };

// Re-export screen for convenience
export { screen };

// Utility functions
export const mockConsoleError = () => {
  const spy = vi.spyOn(console, 'error').mockImplementation(() => { });
  return () => spy.mockRestore();
};

export const mockConsoleWarn = () => {
  const spy = vi.spyOn(console, 'warn').mockImplementation(() => { });
  return () => spy.mockRestore();
};

export const mockConsoleLog = () => {
  const spy = vi.spyOn(console, 'log').mockImplementation(() => { });
  return () => spy.mockRestore();;
};

/**
 * Wait for a specific amount of time (in milliseconds)
 */
export const waitForTimeout = (ms: number) =>
  new Promise(resolve => setTimeout(resolve, ms));

/**
 * Wait for a condition to be true
 */
export const waitForCondition = async (
  condition: () => boolean | Promise<boolean>,
  timeout = 1000,
  interval = 50
) => {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await condition()) return true;
    await waitForTimeout(interval);
  }
  throw new Error(`Condition not met within ${timeout}ms`);
};

// Mock matchMedia
export const setupMatchMedia = () => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: unknown) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
};

// Mock IntersectionObserver
export const setupIntersectionObserver = () => {
  /**
   *
   */
  class IntersectionObserverMock {
    readonly root: Element | null = null;
    readonly rootMargin: string = '';
    readonly thresholds: ReadonlyArray<number> = [];

    /**
     *
     */
    constructor(private callback: IntersectionObserverCallback, _options?: IntersectionObserverInit) { }

    /**
     *
     */
    observe() {
      const entry: IntersectionObserverEntry = {
        boundingClientRect: {} as DOMRectReadOnly,
        intersectionRatio: 1,
        intersectionRect: {} as DOMRectReadOnly,
        isIntersecting: true,
        rootBounds: null,
        target: document.createElement('div'),
        time: 0,
      };
      this.callback([entry], this);
    }

    /**
     *
     */
    unobserve() { }
    /**
     *
     */
    disconnect() { }
    /**
     *
     */
    takeRecords(): IntersectionObserverEntry[] { return []; }
  }

  Object.defineProperty(window, 'IntersectionObserver', {
    writable: true,
    configurable: true,
    value: IntersectionObserverMock,
  });
};

// Setup all mocks
export const setupMocks = () => {
  setupMatchMedia();
  setupIntersectionObserver();
};

// MSW Server setup for tests
const server = setupServer(...handlers);

// Enable API mocking before tests.
beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });

  // Mock the scrollTo method
  window.scrollTo = vi.fn();

  // Mock the ResizeObserver
  global.ResizeObserver = vi.fn().mockImplementation(() => ({
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  }));
});

// Reset any request handlers that we may add during the tests,
// so they don't affect other tests.
afterEach(() => {
  server.resetHandlers();
  // Clear all mocks after each test
  vi.clearAllMocks();
});

// Clean up after the tests are finished.
afterAll(() => server.close());

// Re-exported helper (simple wrapper)
export const createTestQueryClient = () => createTestQueryClientInternal();

// Helper to render a component with a specific route
export const renderWithRouter = (
  ui: ReactElement,
  { route = '/', path = '*', ...options }: { route?: string; path?: string } & CustomRenderOptions = {}
) => {
  window.history.pushState({}, 'Test page', route);

  return customRender(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path={path} element={ui} />
      </Routes>
    </MemoryRouter>,
    options
  );
};

// Helper to test form submission
/**
 *
 */
interface FormTestHelpers extends CustomRenderResult {
  submitButton: HTMLElement;
  getInput: (name: string) => HTMLInputElement | HTMLTextAreaElement | null;
}

export const setupFormTest = async (
  form: ReactElement,
  options: CustomRenderOptions = {}
): Promise<FormTestHelpers> => {
  const result = customRender(form, options);

  const getInput = (name: string) =>
    result.container.querySelector(`[name="${name}"]`) as HTMLInputElement | HTMLTextAreaElement | null;

  const submitButton = screen.getByRole('button', { name: /submit|save/i });

  return {
    ...result,
    getInput,
    submitButton,
  };
};

// Helper to mock API responses
export const mockApiResponse = (
  method: 'get' | 'post' | 'put' | 'delete' | 'patch',
  path: string,
  response: unknown,
  status = 200
) => {
  server.use(
    http[method](path, () => {
      return HttpResponse.json(response, { status });
    })
  );
};

// Helper to test loading states
export const waitForLoadingToFinish = async () => {
  await waitFor(() => {
    expect(screen.queryByText(/loading/i)).not.toBeInTheDocument();
  }, { timeout: 4000 });
};
