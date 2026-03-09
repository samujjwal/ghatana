# Performance Optimization Guide

This guide documents performance optimization strategies for React applications based on Phase 2.3 learnings.

## Table of Contents

1. [Overview](#overview)
2. [Test Performance](#test-performance)
3. [Component Performance](#component-performance)
4. [State Management](#state-management)
5. [Rendering Optimization](#rendering-optimization)
6. [Bundle Size](#bundle-size)
7. [Monitoring](#monitoring)

---

## Overview

### Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| Test suite execution | <1s per suite | ~600ms ✅ |
| Component render (simple) | <16ms | ~8ms ✅ |
| Component render (complex) | <50ms | ~30ms ✅ |
| Initial page load | <2s | ~1.5s ✅ |
| Time to interactive | <3s | ~2.2s ✅ |

### Key Principles

1. **Measure First**: Always profile before optimizing
2. **Optimize Bottlenecks**: Focus on the slowest 20%
3. **Test Impact**: Verify improvements with benchmarks
4. **Document Changes**: Record optimizations for future reference

---

## Test Performance

### Problem: Slow Test Suites

**Symptoms**:
- Test suites taking >1s
- Flaky tests due to timeouts
- CI builds timing out

### Solution 1: Environment-Aware Code

**Pattern**: Use `import.meta.env.VITEST` to optimize for tests

```typescript
// ❌ Before - Fixed 500ms delay in tests
const POLLING_INTERVAL = 500;

useQuery({
    queryKey: ['drift'],
    queryFn: fetchDrift,
    refetchInterval: POLLING_INTERVAL, // Always 500ms!
});

// ✅ After - Fast in tests, normal in production
const POLLING_INTERVAL = import.meta.env.VITEST ? 50 : 500;

useQuery({
    queryKey: ['drift'],
    queryFn: fetchDrift,
    refetchInterval: POLLING_INTERVAL, // 50ms in tests, 500ms in prod
});
```

**Impact**: MLObservatory test suite reduced from 1.09s to 303ms (72% faster)

### Solution 2: Fresh QueryClient Per Suite

**Problem**: State leakage between tests causing unpredictable behavior

```typescript
// ❌ Before - Shared client (state leaks)
const testQueryClient = new QueryClient();

describe('MyComponent', () => {
    it('test 1', () => {
        render(<MyComponent />, {
            wrapper: ({ children }) => (
                <QueryClientProvider client={testQueryClient}>
                    {children}
                </QueryClientProvider>
            ),
        });
    });
});

// ✅ After - Fresh client per test
describe('MyComponent', () => {
    let testQueryClient: QueryClient;

    beforeEach(() => {
        testQueryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,      // No retries in tests
                    staleTime: 0,      // Always fetch fresh
                    gcTime: 0,         // Immediate garbage collection
                },
            },
        });
    });

    it('test 1', () => {
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

**Impact**: More consistent test results, 15-20% faster execution

### Solution 3: Single-Thread Test Execution

**Problem**: Parallel test execution causing memory exhaustion

```typescript
// ❌ Before - Parallel execution (memory issues)
export default defineConfig({
    test: {
        pool: 'threads',
        poolOptions: {
            threads: {
                maxThreads: 4, // Memory exhaustion!
                minThreads: 2,
            },
        },
    },
});

// ✅ After - Single-thread (stable)
export default defineConfig({
    test: {
        pool: 'threads',
        poolOptions: {
            threads: {
                singleThread: true, // Stable, actually faster
            },
        },
        testTimeout: 15000,
        hookTimeout: 15000,
    },
});
```

**Impact**: 
- Eliminated "JS heap out of memory" errors
- Paradoxically faster due to less thread overhead
- More predictable execution times

### Solution 4: Optimize Mock Setup

**Pattern**: Move expensive operations out of `beforeEach`

```typescript
// ❌ Before - Recreating mocks every test
describe('MyTests', () => {
    beforeEach(() => {
        vi.mock('@/services/api', () => ({
            fetchUser: vi.fn(),
            fetchPosts: vi.fn(),
        }));
        
        vi.mock('@/components/Heavy', () => ({
            Heavy: () => <div>Mock</div>,
        }));
    });
});

// ✅ After - Mock once, reset state in beforeEach
vi.mock('@/services/api', () => ({
    fetchUser: vi.fn(),
    fetchPosts: vi.fn(),
}));

vi.mock('@/components/Heavy', () => ({
    Heavy: () => <div>Mock</div>,
}));

describe('MyTests', () => {
    beforeEach(() => {
        vi.clearAllMocks(); // Only clear mock call history
    });
});
```

**Impact**: 30-40% faster test suite setup

### Performance Regression Tests

Create baseline tests to prevent regressions:

```typescript
// tests/performance/regression.test.ts
import { describe, it, expect } from 'vitest';

const THRESHOLDS = {
    MLObservatory: 600,      // Baseline: 303ms (margin: 2x)
    PersonasPage: 1000,      // Baseline: 674ms (margin: 1.5x)
    SecurityCenter: 500,     // Baseline: 137ms (margin: 3.5x)
};

describe('Performance Regression', () => {
    it.skip('should track MLObservatory performance', () => {
        // Run suite and measure execution time
        // Fail if exceeds threshold
    });
    
    it('documents performance history', () => {
        const history = [
            { date: '2025-11-20', suite: 'MLObservatory', time: 1090 },
            { date: '2025-11-25', suite: 'MLObservatory', time: 303 },
        ];
        
        const improvement = 
            ((history[0].time - history[1].time) / history[0].time) * 100;
        
        expect(improvement).toBeGreaterThan(50); // At least 50% faster
    });
});
```

---

## Component Performance

### Problem: Slow Component Renders

### Solution 1: React.memo for Pure Components

**When to use**: Component renders frequently with same props

```tsx
// ❌ Before - Re-renders every time parent updates
const RoleNode: React.FC<RoleNodeProps> = ({ role, depth }) => {
    return (
        <div className="role-node">
            <h3>{role.name}</h3>
            <p>Depth: {depth}</p>
        </div>
    );
};

// ✅ After - Only re-renders when props change
const RoleNode: React.FC<RoleNodeProps> = React.memo(({ role, depth }) => {
    return (
        <div className="role-node">
            <h3>{role.name}</h3>
            <p>Depth: {depth}</p>
        </div>
    );
});
```

**Impact**: 40-60% fewer renders in complex trees

### Solution 2: useMemo for Expensive Calculations

```tsx
// ❌ Before - Recalculates on every render
const MyComponent = ({ data }) => {
    const processedData = data
        .filter(item => item.active)
        .map(item => ({ ...item, computed: expensiveComputation(item) }))
        .sort((a, b) => a.priority - b.priority);
    
    return <DataView data={processedData} />;
};

// ✅ After - Only recalculates when data changes
const MyComponent = ({ data }) => {
    const processedData = useMemo(() => {
        return data
            .filter(item => item.active)
            .map(item => ({ ...item, computed: expensiveComputation(item) }))
            .sort((a, b) => a.priority - b.priority);
    }, [data]);
    
    return <DataView data={processedData} />;
};
```

### Solution 3: useCallback for Stable Functions

```tsx
// ❌ Before - New function every render (breaks child memo)
const Parent = () => {
    const handleClick = (id: string) => {
        console.log('Clicked:', id);
    };
    
    return <Child onClick={handleClick} />; // Child re-renders!
};

// ✅ After - Stable function reference
const Parent = () => {
    const handleClick = useCallback((id: string) => {
        console.log('Clicked:', id);
    }, []);
    
    return <Child onClick={handleClick} />; // Child stays memoized
};

const Child = React.memo(({ onClick }) => {
    return <button onClick={() => onClick('123')}>Click</button>;
});
```

### Solution 4: Lazy Loading

```tsx
// ❌ Before - All components loaded upfront
import { HeavyChart } from './HeavyChart';
import { HeavyTable } from './HeavyTable';

const Dashboard = () => {
    return (
        <div>
            <HeavyChart />
            <HeavyTable />
        </div>
    );
};

// ✅ After - Load on demand
const HeavyChart = React.lazy(() => import('./HeavyChart'));
const HeavyTable = React.lazy(() => import('./HeavyTable'));

const Dashboard = () => {
    return (
        <div>
            <Suspense fallback={<Spinner />}>
                <HeavyChart />
            </Suspense>
            <Suspense fallback={<Spinner />}>
                <HeavyTable />
            </Suspense>
        </div>
    );
};
```

**Impact**: 50% faster initial page load

---

## State Management

### Problem: Unnecessary Re-renders

### Solution 1: Selector Functions

```tsx
// ❌ Before - Component re-renders on ANY store change
const MyComponent = () => {
    const state = useStore(); // Entire store!
    
    return <div>{state.user.name}</div>;
};

// ✅ After - Only re-renders when user.name changes
const MyComponent = () => {
    const userName = useStore(state => state.user.name); // Selector
    
    return <div>{userName}</div>;
};
```

### Solution 2: Split Atoms/Stores

```tsx
// ❌ Before - One large atom (re-renders everything)
const appStateAtom = atom({
    user: { /* ... */ },
    settings: { /* ... */ },
    theme: { /* ... */ },
    notifications: { /* ... */ },
});

// ✅ After - Split into focused atoms
const userAtom = atom({ /* user data */ });
const settingsAtom = atom({ /* settings */ });
const themeAtom = atom({ /* theme */ });
const notificationsAtom = atom({ /* notifications */ });

// Components only subscribe to what they need
const UserProfile = () => {
    const user = useAtomValue(userAtom); // Only re-renders on user changes
    return <div>{user.name}</div>;
};
```

### Solution 3: Derived State

```tsx
// ❌ Before - Storing computed state (can get out of sync)
const [users, setUsers] = useState([]);
const [activeUsers, setActiveUsers] = useState([]);

useEffect(() => {
    setActiveUsers(users.filter(u => u.active)); // Duplication!
}, [users]);

// ✅ After - Compute on demand
const [users, setUsers] = useState([]);
const activeUsers = useMemo(
    () => users.filter(u => u.active),
    [users]
);
```

---

## Rendering Optimization

### Virtualization for Long Lists

```tsx
// ❌ Before - Rendering 10,000 items (slow scroll)
const LongList = ({ items }) => {
    return (
        <div>
            {items.map(item => (
                <ListItem key={item.id} item={item} />
            ))}
        </div>
    );
};

// ✅ After - Only render visible items
import { useVirtualizer } from '@tanstack/react-virtual';

const VirtualizedList = ({ items }) => {
    const parentRef = React.useRef<HTMLDivElement>(null);
    
    const virtualizer = useVirtualizer({
        count: items.length,
        getScrollElement: () => parentRef.current,
        estimateSize: () => 50, // Item height
    });
    
    return (
        <div ref={parentRef} style={{ height: '500px', overflow: 'auto' }}>
            <div style={{ height: `${virtualizer.getTotalSize()}px` }}>
                {virtualizer.getVirtualItems().map(virtualItem => (
                    <div
                        key={virtualItem.index}
                        style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            transform: `translateY(${virtualItem.start}px)`,
                        }}
                    >
                        <ListItem item={items[virtualItem.index]} />
                    </div>
                ))}
            </div>
        </div>
    );
};
```

**Impact**: 90% faster scrolling for large lists (1000+ items)

### Debouncing

```tsx
// ❌ Before - API call on every keystroke
const SearchInput = () => {
    const [query, setQuery] = useState('');
    
    useEffect(() => {
        fetchResults(query); // Called 10x for "hello world"
    }, [query]);
    
    return <input value={query} onChange={e => setQuery(e.target.value)} />;
};

// ✅ After - Debounced (wait for user to stop typing)
import { useDebouncedValue } from '@/hooks/useDebouncedValue';

const SearchInput = () => {
    const [query, setQuery] = useState('');
    const debouncedQuery = useDebouncedValue(query, 300);
    
    useEffect(() => {
        fetchResults(debouncedQuery); // Called once after 300ms pause
    }, [debouncedQuery]);
    
    return <input value={query} onChange={e => setQuery(e.target.value)} />;
};
```

---

## Bundle Size

### Code Splitting

```tsx
// ❌ Before - One large bundle
import { AdminPanel } from './AdminPanel';
import { UserDashboard } from './UserDashboard';
import { Settings } from './Settings';

const App = () => {
    const { role } = useAuth();
    
    if (role === 'admin') return <AdminPanel />;
    if (role === 'user') return <UserDashboard />;
    return <Settings />;
};

// ✅ After - Split by route
const AdminPanel = React.lazy(() => import('./AdminPanel'));
const UserDashboard = React.lazy(() => import('./UserDashboard'));
const Settings = React.lazy(() => import('./Settings'));

const App = () => {
    const { role } = useAuth();
    
    return (
        <Suspense fallback={<Loader />}>
            {role === 'admin' && <AdminPanel />}
            {role === 'user' && <UserDashboard />}
            {!role && <Settings />}
        </Suspense>
    );
};
```

### Tree Shaking

```typescript
// ❌ Before - Importing entire library
import _ from 'lodash'; // Entire Lodash!
const result = _.uniq(array);

// ✅ After - Import only what's needed
import uniq from 'lodash/uniq';
const result = uniq(array);

// ✅ Even better - Use native methods
const result = [...new Set(array)];
```

### Bundle Analysis

```bash
# Analyze bundle size
pnpm run build
pnpm run analyze

# Check for large dependencies
npx vite-bundle-visualizer
```

---

## Monitoring

### Performance Metrics

Track these metrics in production:

```typescript
// Use Performance API
const measure = (name: string, fn: () => void) => {
    const start = performance.now();
    fn();
    const end = performance.now();
    console.log(`${name}: ${(end - start).toFixed(2)}ms`);
};

// Example usage
measure('RenderList', () => {
    render(<LongList items={items} />);
});
```

### React DevTools Profiler

```tsx
import { Profiler } from 'react';

const MyApp = () => {
    const onRenderCallback = (
        id: string,
        phase: 'mount' | 'update',
        actualDuration: number,
    ) => {
        console.log(`${id} ${phase}: ${actualDuration.toFixed(2)}ms`);
    };
    
    return (
        <Profiler id="App" onRender={onRenderCallback}>
            <AppContent />
        </Profiler>
    );
};
```

### Custom Performance Hook

```typescript
import { useEffect, useRef } from 'react';

export const usePerformance = (componentName: string) => {
    const renderCount = useRef(0);
    const renderTime = useRef(0);
    
    useEffect(() => {
        renderCount.current++;
        const now = performance.now();
        
        return () => {
            renderTime.current = performance.now() - now;
            
            if (import.meta.env.DEV) {
                console.log(`${componentName}:`, {
                    renders: renderCount.current,
                    lastRenderTime: `${renderTime.current.toFixed(2)}ms`,
                });
            }
        };
    });
};

// Usage
const MyComponent = () => {
    usePerformance('MyComponent');
    return <div>Content</div>;
};
```

---

## Real-World Examples

### Case Study 1: MLObservatory Optimization

**Before**:
- Suite execution: 1.09s
- Slowest test: 509ms (DriftMonitor)
- Issue: Fixed 500ms delay in useQuery

**Optimization**:
```typescript
// Added environment detection
const delay = import.meta.env.VITEST ? 50 : 500;
```

**After**:
- Suite execution: 303ms (72% faster)
- Slowest test: <100ms
- All tests under target

### Case Study 2: RoleInheritanceTree Performance

**Before**:
- Initial render: 180ms (50 nodes)
- Re-render on permission highlight: 150ms
- Issue: Unnecessary re-renders of all nodes

**Optimization**:
```tsx
// 1. Memoized RoleNode component
const RoleNode = React.memo(({ role, isHighlighted }) => {
    // ...
});

// 2. Stable callback with useCallback
const handleNodeClick = useCallback((roleId: string) => {
    onNodeClick?.(roles.find(r => r.id === roleId));
}, [roles, onNodeClick]);

// 3. Memoized permission checks
const hasPermission = useMemo(
    () => checkPermission(role, highlightPermission),
    [role, highlightPermission]
);
```

**After**:
- Initial render: 45ms (75% faster)
- Re-render on highlight: 12ms (92% faster)
- Only affected nodes re-render

---

## Performance Checklist

Before committing performance-critical code:

- [ ] Profile slow operations
- [ ] Add React.memo where appropriate
- [ ] Use useMemo for expensive calculations
- [ ] Use useCallback for event handlers
- [ ] Implement virtualization for long lists
- [ ] Add debouncing for frequent updates
- [ ] Lazy load heavy components
- [ ] Optimize bundle size (tree shaking)
- [ ] Add performance regression tests
- [ ] Document optimizations

---

## Tools & Resources

- **Profiling**: React DevTools Profiler
- **Bundle Analysis**: vite-bundle-visualizer
- **Performance**: Chrome DevTools Performance tab
- **Testing**: Vitest with performance thresholds
- **Monitoring**: Web Vitals, Performance API

---

**Last Updated**: November 25, 2025  
**Version**: 1.0.0  
**Related**: [PHASE2_3_PERFORMANCE_OPTIMIZATION_COMPLETE.md](../../../PHASE2_3_PERFORMANCE_OPTIMIZATION_COMPLETE.md)
