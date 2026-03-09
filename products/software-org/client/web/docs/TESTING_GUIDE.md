# Testing Best Practices Guide

This guide documents best practices for testing React components and hooks in the software-org web application, based on learnings from Phase 2 implementation.

## Table of Contents

1. [General Principles](#general-principles)
2. [React Testing Library Patterns](#react-testing-library-patterns)
3. [act() Warnings Prevention](#act-warnings-prevention)
4. [Performance Optimization](#performance-optimization)
5. [Mock Patterns](#mock-patterns)
6. [Test Organization](#test-organization)
7. [Common Pitfalls](#common-pitfalls)

---

## General Principles

### 1. Test Behavior, Not Implementation

```tsx
// ❌ Bad - Testing implementation details
it('should set state to true', () => {
    const { result } = renderHook(() => useMyHook());
    act(() => {
        result.current.internalSetState(true);
    });
    expect(result.current.internalState).toBe(true);
});

// ✅ Good - Testing user-facing behavior
it('should show success message when form submitted', async () => {
    render(<MyForm />);
    const submitButton = screen.getByRole('button', { name: /submit/i });
    
    fireEvent.click(submitButton);
    
    await waitFor(() => {
        expect(screen.getByText(/success/i)).toBeInTheDocument();
    });
});
```

### 2. Isolate Test Cases

Each test should:
- ✅ Be independent (no shared state)
- ✅ Create its own test data
- ✅ Clean up after itself
- ✅ Not depend on test execution order

```tsx
// ✅ Good - Isolated test with fresh state
describe('UserProfile', () => {
    let testQueryClient: QueryClient;

    beforeEach(() => {
        // Fresh client per test
        testQueryClient = new QueryClient({
            defaultOptions: {
                queries: { retry: false, staleTime: 0, gcTime: 0 },
            },
        });
    });

    it('should display user name', () => {
        render(<UserProfile userId="123" />, {
            wrapper: ({ children }) => (
                <QueryClientProvider client={testQueryClient}>
                    {children}
                </QueryClientProvider>
            ),
        });
        
        expect(screen.getByText(/john doe/i)).toBeInTheDocument();
    });
});
```

### 3. Use Descriptive Test Names

Follow the pattern: `should [expected behavior] when [condition]`

```tsx
// ❌ Bad - Vague test names
it('works', () => { /* ... */ });
it('test login', () => { /* ... */ });

// ✅ Good - Clear, descriptive names
it('should display error message when login fails', () => { /* ... */ });
it('should redirect to dashboard when login succeeds', () => { /* ... */ });
it('should disable submit button when form is invalid', () => { /* ... */ });
```

---

## React Testing Library Patterns

### Query Priority

Use queries in this order (most to least preferred):

1. **Accessible queries** (queries users can interact with)
   - `getByRole`, `getByLabelText`, `getByPlaceholderText`, `getByText`, `getByDisplayValue`

2. **Semantic queries**
   - `getByAltText`, `getByTitle`

3. **Test IDs** (last resort)
   - `getByTestId`

```tsx
// ✅ Best - Using accessible queries
const button = screen.getByRole('button', { name: /submit/i });
const input = screen.getByLabelText(/email/i);

// ❌ Worst - Using test IDs unnecessarily
const button = screen.getByTestId('submit-button');
```

### Async Operations

Always use `waitFor` for async operations:

```tsx
// ✅ Good - Proper async handling
it('should load user data', async () => {
    render(<UserProfile userId="123" />);
    
    await waitFor(() => {
        expect(screen.getByText(/john doe/i)).toBeInTheDocument();
    });
});

// ❌ Bad - No async handling (flaky test)
it('should load user data', () => {
    render(<UserProfile userId="123" />);
    expect(screen.getByText(/john doe/i)).toBeInTheDocument(); // Might fail!
});
```

### User Events

Prefer `@testing-library/user-event` over `fireEvent`:

```tsx
import userEvent from '@testing-library/user-event';

// ✅ Better - More realistic user interactions
it('should update input value', async () => {
    const user = userEvent.setup();
    render(<MyInput />);
    
    const input = screen.getByRole('textbox');
    await user.type(input, 'Hello World');
    
    expect(input).toHaveValue('Hello World');
});

// ❌ Acceptable but less realistic
it('should update input value', () => {
    render(<MyInput />);
    
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'Hello World' } });
    
    expect(input).toHaveValue('Hello World');
});
```

---

## act() Warnings Prevention

### The Problem

React warns when state updates occur outside of `act()`:

```
Warning: An update to TestComponent inside a test was not wrapped in act(...).
```

### Root Cause

State updates happening outside React Testing Library's automatic `act()` wrapping.

### Solution Patterns

#### Pattern 1: Wrap Event Handlers in waitFor()

```tsx
// ❌ Wrong - Event handler called outside act()
it('should handle connection', async () => {
    const { result } = renderHook(() => useSocket());
    
    const mockOn = mockSocket.on as any;
    mockSocket.connected = true;
    mockOn.handlers['connect'](); // State update happens here!
    
    await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
    });
});

// ✅ Correct - Event handler wrapped in waitFor()
it('should handle connection', async () => {
    const { result } = renderHook(() => useSocket());
    
    const mockOn = mockSocket.on as any;
    mockSocket.connected = true;
    
    await waitFor(() => {
        mockOn.handlers['connect'](); // Now wrapped!
        expect(result.current.isConnected).toBe(true);
    });
});
```

#### Pattern 2: Wrap Async State Updates

```tsx
// ❌ Wrong - Async update outside act()
it('should load data', async () => {
    render(<MyComponent />);
    
    // Trigger async operation
    fireEvent.click(screen.getByRole('button'));
    
    // Wait for result
    await waitFor(() => {
        expect(screen.getByText(/loaded/i)).toBeInTheDocument();
    });
});

// ✅ Correct - Use userEvent which handles act() automatically
it('should load data', async () => {
    const user = userEvent.setup();
    render(<MyComponent />);
    
    await user.click(screen.getByRole('button'));
    
    await waitFor(() => {
        expect(screen.getByText(/loaded/i)).toBeInTheDocument();
    });
});
```

#### Pattern 3: Mock Timers Properly

```tsx
// ❌ Wrong - Real timers with state updates
it('should debounce search', async () => {
    render(<SearchInput />);
    
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test' } });
    
    // Wait for debounce
    await new Promise(resolve => setTimeout(resolve, 500));
    
    expect(mockSearch).toHaveBeenCalled();
});

// ✅ Correct - Fake timers with act()
it('should debounce search', async () => {
    vi.useFakeTimers();
    render(<SearchInput />);
    
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test' } });
    
    await act(async () => {
        vi.advanceTimersByTime(500);
    });
    
    expect(mockSearch).toHaveBeenCalled();
    vi.useRealTimers();
});
```

### Quick Reference: act() Warning Fixes

| Scenario | Solution |
|----------|----------|
| Mock event handlers | Wrap handler call in `waitFor()` |
| User interactions | Use `@testing-library/user-event` |
| Timers/delays | Use fake timers with `act()` |
| Async operations | Always use `await waitFor()` |
| State updates in hooks | Wrap in `act()` or `waitFor()` |

---

## Performance Optimization

### 1. Environment-Aware Components

Use `import.meta.env.VITEST` to optimize for tests:

```tsx
// In component
const delay = import.meta.env.VITEST ? 50 : 500;

useQuery({
    queryKey: ['data'],
    queryFn: async () => {
        await new Promise(resolve => setTimeout(resolve, delay));
        return fetchData();
    },
});
```

**Impact**: MLObservatory test reduced from 1.09s to 303ms (72% faster)

### 2. Fresh QueryClient Per Suite

Prevent state leakage between tests:

```tsx
describe('MyComponent', () => {
    let testQueryClient: QueryClient;

    beforeEach(() => {
        testQueryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,      // No retries in tests
                    staleTime: 0,      // Always fresh
                    gcTime: 0,         // Immediate cleanup
                },
            },
        });
    });

    it('test case', () => {
        render(<MyComponent />, {
            wrapper: ({ children }) => (
                <QueryClientProvider client={testQueryClient}>
                    {children}
                </QueryClientProvider>
            ),
        });
    });
});
```

### 3. Optimize Mock Setup

Move static mocks to `beforeAll`, dynamic mocks to `beforeEach`:

```tsx
describe('MyTests', () => {
    // ✅ Static mocks - beforeAll
    beforeAll(() => {
        vi.mock('@/services/api', () => ({
            fetchUser: vi.fn(),
        }));
    });

    // ✅ Dynamic state - beforeEach
    beforeEach(() => {
        vi.clearAllMocks();
        (fetchUser as any).mockResolvedValue({ name: 'John' });
    });

    // ❌ Don't recreate static mocks in beforeEach
    beforeEach(() => {
        vi.mock('@/services/api', () => ({ /* ... */ })); // Expensive!
    });
});
```

### 4. Reduce waitFor Timeouts

Use shorter timeouts for faster failures:

```tsx
// ❌ Default timeout (1000ms)
await waitFor(() => {
    expect(screen.getByText(/data/i)).toBeInTheDocument();
});

// ✅ Shorter timeout (100ms for fast operations)
await waitFor(
    () => {
        expect(screen.getByText(/data/i)).toBeInTheDocument();
    },
    { timeout: 100 }
);
```

### 5. Test Configuration

Optimal Vitest configuration:

```typescript
// vitest.config.ts
export default defineConfig({
    test: {
        environment: 'jsdom',
        globals: true,
        
        // Single-threaded for stability
        pool: 'threads',
        poolOptions: {
            threads: {
                singleThread: true,
            },
        },
        
        // Reasonable timeouts
        testTimeout: 15000,
        hookTimeout: 15000,
        
        // Coverage thresholds
        coverage: {
            provider: 'v8',
            thresholds: {
                lines: 80,
                functions: 80,
                branches: 80,
                statements: 80,
            },
        },
    },
});
```

---

## Mock Patterns

### Mocking React Query

```tsx
// Mock useQuery
vi.mock('@tanstack/react-query', async () => {
    const actual = await vi.importActual('@tanstack/react-query');
    return {
        ...actual,
        useQuery: vi.fn(() => ({
            data: { name: 'John Doe' },
            isLoading: false,
            isError: false,
        })),
    };
});
```

### Mocking Hooks

```tsx
// Mock custom hook
vi.mock('@/hooks/useUser', () => ({
    useUser: vi.fn(() => ({
        user: { id: '123', name: 'John' },
        isLoading: false,
    })),
}));

// Use in test
it('should display user name', () => {
    const mockUseUser = vi.mocked(useUser);
    mockUseUser.mockReturnValue({
        user: { id: '456', name: 'Jane' },
        isLoading: false,
    });
    
    render(<UserProfile />);
    expect(screen.getByText(/jane/i)).toBeInTheDocument();
});
```

### Mocking Socket.IO

```tsx
const mockSocket = {
    on: vi.fn(),
    emit: vi.fn(),
    disconnect: vi.fn(),
    connected: false,
};

vi.mock('socket.io-client', () => ({
    io: vi.fn(() => mockSocket),
}));

// Store handlers for later triggering
const mockOn = mockSocket.on as any;
mockOn.mockImplementation((event: string, handler: Function) => {
    if (!mockOn.handlers) mockOn.handlers = {};
    mockOn.handlers[event] = handler;
    return mockSocket;
});

// Trigger handler in test
await waitFor(() => {
    mockOn.handlers['connect']();
    expect(result.current.isConnected).toBe(true);
});
```

---

## Test Organization

### File Structure

```
src/
├── components/
│   └── MyComponent/
│       ├── MyComponent.tsx
│       ├── __tests__/
│       │   └── MyComponent.test.tsx
│       ├── types.ts
│       └── index.ts
```

### Test Structure

```tsx
describe('ComponentName', () => {
    // Setup
    let testQueryClient: QueryClient;
    
    beforeEach(() => {
        // Fresh state per test
    });
    
    afterEach(() => {
        // Cleanup
        vi.clearAllMocks();
    });
    
    // Group related tests
    describe('Rendering', () => {
        it('should render with default props', () => { });
        it('should render with custom props', () => { });
    });
    
    describe('Interactions', () => {
        it('should handle button click', () => { });
        it('should handle form submission', () => { });
    });
    
    describe('Edge Cases', () => {
        it('should handle null data', () => { });
        it('should handle loading state', () => { });
        it('should handle error state', () => { });
    });
});
```

---

## Common Pitfalls

### 1. Testing Implementation Details

```tsx
// ❌ Bad - Testing internal state
expect(component.state.count).toBe(5);

// ✅ Good - Testing visible output
expect(screen.getByText('Count: 5')).toBeInTheDocument();
```

### 2. Not Cleaning Up Mocks

```tsx
// ❌ Bad - Mocks leak between tests
describe('MyTests', () => {
    it('test 1', () => {
        mockFn.mockReturnValue('test1');
        // ...
    });
    
    it('test 2', () => {
        // mockFn still returns 'test1'!
    });
});

// ✅ Good - Clean up after each test
describe('MyTests', () => {
    afterEach(() => {
        vi.clearAllMocks();
    });
    
    it('test 1', () => {
        mockFn.mockReturnValue('test1');
    });
    
    it('test 2', () => {
        mockFn.mockReturnValue('test2');
    });
});
```

### 3. Forgetting Async/Await

```tsx
// ❌ Bad - Missing await
it('loads data', () => {
    render(<MyComponent />);
    waitFor(() => {
        expect(screen.getByText(/data/i)).toBeInTheDocument();
    }); // Forgotten await!
});

// ✅ Good - Proper await
it('loads data', async () => {
    render(<MyComponent />);
    await waitFor(() => {
        expect(screen.getByText(/data/i)).toBeInTheDocument();
    });
});
```

### 4. Over-Mocking

```tsx
// ❌ Bad - Mocking everything (no real behavior tested)
vi.mock('@/components/UserProfile', () => ({
    UserProfile: () => <div>Mocked Profile</div>,
}));

// ✅ Good - Mock only external dependencies
vi.mock('@/services/api', () => ({
    fetchUser: vi.fn(),
}));
// Real component renders, only API is mocked
```

---

## Performance Benchmarks

Target execution times for different test types:

| Test Type | Target Time | Current Average |
|-----------|-------------|-----------------|
| Unit (component) | <100ms | ~50ms |
| Unit (hook) | <50ms | ~20ms |
| Integration | <500ms | ~300ms |
| Suite (10-30 tests) | <1s | ~600ms |

---

## Quick Reference Checklist

Before committing tests:

- [ ] All tests pass locally
- [ ] No act() warnings
- [ ] Descriptive test names
- [ ] Tests are isolated (no shared state)
- [ ] Async operations use await
- [ ] Mocks are cleaned up
- [ ] Tests cover edge cases
- [ ] Coverage meets 80% threshold
- [ ] Tests run in <1s per suite

---

## Resources

- [React Testing Library Docs](https://testing-library.com/react)
- [Common Mistakes](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library)
- [Phase 2.3 Performance Report](../../../PHASE2_3_PERFORMANCE_OPTIMIZATION_COMPLETE.md)

---

**Last Updated**: November 25, 2025  
**Version**: 1.0.0
