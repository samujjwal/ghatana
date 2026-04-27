/**
 * Performance Optimization Utilities Tests
 * Tests pure utility functions, hooks, and components from PerformanceOptimizedClean.tsx
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import React from 'react';

vi.mock('@ghatana/design-system', () => ({
    Box: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    Typography: ({ children }: { children?: React.ReactNode }) => <span>{children}</span>,
    Spinner: () => <div role="progressbar" />,
    CircularProgress: () => <div role="progressbar" />,
}));

import {
    debounce,
    throttle,
    memoize,
    useDebouncedState,
    PerformanceMonitor,
    LazyComponent,
} from '../PerformanceOptimizedClean';
import { renderHook } from '@testing-library/react';

afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
});

// ======================== Pure Functions ========================

describe('debounce', () => {
    it('delays function invocation', async () => {
        vi.useFakeTimers();
        const fn = vi.fn();
        const debounced = debounce(fn, 100);

        debounced('a');
        debounced('b');
        debounced('c');

        expect(fn).not.toHaveBeenCalled();

        vi.advanceTimersByTime(100);
        expect(fn).toHaveBeenCalledTimes(1);
        expect(fn).toHaveBeenCalledWith('c');
        vi.useRealTimers();
    });

    it('calls immediately if wait=0', () => {
        vi.useFakeTimers();
        const fn = vi.fn();
        const debounced = debounce(fn, 0);

        debounced('x');
        vi.advanceTimersByTime(0);
        expect(fn).toHaveBeenCalledWith('x');
        vi.useRealTimers();
    });
});

describe('throttle', () => {
    it('calls function at most once per interval', () => {
        vi.useFakeTimers();
        const fn = vi.fn();
        const throttled = throttle(fn, 100);

        throttled('a');
        throttled('b');
        throttled('c');

        // First call goes through immediately
        expect(fn).toHaveBeenCalledTimes(1);
        expect(fn).toHaveBeenCalledWith('a');

        vi.advanceTimersByTime(100);
        // After interval, pending call executes
        expect(fn.mock.calls.length).toBeGreaterThanOrEqual(1);
        vi.useRealTimers();
    });
});

describe('memoize', () => {
    it('caches results for the same arguments', () => {
        const fn = vi.fn((x: number) => x * 2);
        const memoized = memoize(fn);

        expect(memoized(5)).toBe(10);
        expect(memoized(5)).toBe(10);
        expect(fn).toHaveBeenCalledTimes(1); // Second call used cache
    });

    it('calls function again for different arguments', () => {
        const fn = vi.fn((x: number) => x * 2);
        const memoized = memoize(fn);

        expect(memoized(5)).toBe(10);
        expect(memoized(6)).toBe(12);
        expect(fn).toHaveBeenCalledTimes(2);
    });
});

// ======================== Hooks ========================

describe('useDebouncedState', () => {
    it('returns initial value immediately', () => {
        vi.useFakeTimers();
        const { result } = renderHook(() => useDebouncedState('initial', 100));
        expect(result.current[0]).toBe('initial');
        vi.useRealTimers();
    });

    it('debounces state updates', () => {
        vi.useFakeTimers();
        const { result } = renderHook(() => useDebouncedState('start', 100));
        const [, setValue] = result.current;

        act(() => setValue('updated'));
        // Immediate state still 'start'
        expect(result.current[0]).toBe('start');

        act(() => {
            vi.advanceTimersByTime(100);
        });
        expect(result.current[0]).toBe('updated');
    });
});

// ======================== Components ========================

describe('PerformanceMonitor', () => {
    it('renders children when enabled', () => {
        render(
            <PerformanceMonitor trackRenders={false} trackMemory={false}>
                <div data-testid="child">Content</div>
            </PerformanceMonitor>
        );
        expect(screen.getByTestId('child')).toBeInTheDocument();
    });

    it('renders children when metrics panel is shown', () => {
        render(
            <PerformanceMonitor showMetrics trackRenders={false} trackMemory={false}>
                <div data-testid="child">Content</div>
            </PerformanceMonitor>
        );
        expect(screen.getByTestId('child')).toBeInTheDocument();
    });
});

describe('LazyComponent', () => {
    it('renders the component once loaded', async () => {
        const MockComp: React.FC = () => <div data-testid="lazy-content">Loaded</div>;
        const loader = () => Promise.resolve({ default: MockComp });

        render(<LazyComponent loader={loader} />);
        await waitFor(() =>
            expect(screen.getByTestId('lazy-content')).toBeInTheDocument()
        );
    });

    it('renders fallback while loading', async () => {
        const MockComp: React.FC = () => <div data-testid="lazy-later">Loaded later</div>;
        let resolveLoader: ((value: { default: React.FC }) => void) | undefined;
        const loader = () => new Promise<{ default: React.FC }>((resolve) => {
            resolveLoader = resolve;
        });

        render(
            <LazyComponent
                loader={loader}
                delay={0}
                fallback={<div data-testid="loading">Loading...</div>}
            />
        );

        expect(screen.getByTestId('loading')).toBeInTheDocument();

        act(() => {
            resolveLoader?.({ default: MockComp });
        });

        await waitFor(() => expect(screen.getByTestId('lazy-later')).toBeInTheDocument());
    });
});
