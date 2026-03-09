/**
 * Common Component Hooks
 *
 * <p><b>Purpose</b><br>
 * Custom React hooks for common UI patterns, state management, and interactions
 * including loading states, error handling, modal controls, and keyboard navigation.
 *
 * <p><b>Hooks</b><br>
 * - useAsync: Handle async operations
 * - useToggle: Simple boolean toggle
 * - useLocalStorage: Persist state to localStorage
 * - useDebounce: Debounce value changes
 * - useThrottle: Throttle function calls
 * - useKeyboardNavigation: Handle keyboard events
 * - useOutsideClick: Detect outside clicks
 * - usePrevious: Track previous value
 *
 * @doc.type hook
 * @doc.purpose Common UI hooks and patterns
 * @doc.layer product
 * @doc.pattern Custom Hook Library
 */

import { useState, useCallback, useEffect, useRef } from 'react';

/**
 * Async operation state
 */
export interface AsyncState<T> {
    data: T | null;
    error: Error | null;
    isLoading: boolean;
}

/**
 * Handle async operations with loading and error states
 *
 * @param fn - Async function to execute
 * @param deps - Dependency array
 * @returns Async state
 */
export function useAsync<T>(
    fn: () => Promise<T>,
    deps?: React.DependencyList
): AsyncState<T> {
    const [state, setState] = useState<AsyncState<T>>({
        data: null,
        error: null,
        isLoading: true,
    });

    useEffect(() => {
        let isMounted = true;

        const execute = async () => {
            setState({ data: null, error: null, isLoading: true });
            try {
                const result = await fn();
                if (isMounted) {
                    setState({ data: result, error: null, isLoading: false });
                }
            } catch (error) {
                if (isMounted) {
                    setState({
                        data: null,
                        error: error instanceof Error ? error : new Error(String(error)),
                        isLoading: false,
                    });
                }
            }
        };

        execute();

        return () => {
            isMounted = false;
        };
    }, deps);

    return state;
}

/**
 * Simple boolean toggle
 *
 * @param initialValue - Initial value
 * @returns [value, toggle, setValue]
 */
export function useToggle(
    initialValue: boolean = false
): [boolean, () => void, (value: boolean) => void] {
    const [value, setValue] = useState(initialValue);

    const toggle = useCallback(() => {
        setValue((v) => !v);
    }, []);

    return [value, toggle, setValue];
}

/**
 * Persist state to localStorage
 *
 * @param key - localStorage key
 * @param initialValue - Initial value
 * @returns [value, setValue]
 */
export function useLocalStorage<T>(
    key: string,
    initialValue: T
): [T, (value: T | ((prev: T) => T)) => void] {
    const [value, setValue] = useState<T>(() => {
        try {
            const item = localStorage.getItem(key);
            return item ? JSON.parse(item) : initialValue;
        } catch (error) {
            console.error(`Error reading localStorage key "${key}":`, error);
            return initialValue;
        }
    });

    const setLocalStorageValue = useCallback(
        (newValue: T | ((prev: T) => T)) => {
            try {
                const valueToStore =
                    newValue instanceof Function ? newValue(value) : newValue;
                setValue(valueToStore);
                localStorage.setItem(key, JSON.stringify(valueToStore));
            } catch (error) {
                console.error(`Error setting localStorage key "${key}":`, error);
            }
        },
        [key, value]
    );

    return [value, setLocalStorageValue];
}

/**
 * Debounce value changes
 *
 * @param value - Value to debounce
 * @param delayMs - Debounce delay in milliseconds
 * @returns Debounced value
 */
export function useDebounce<T>(value: T, delayMs: number = 500): T {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedValue(value);
        }, delayMs);

        return () => clearTimeout(handler);
    }, [value, delayMs]);

    return debouncedValue;
}

/**
 * Throttle function calls
 *
 * @param fn - Function to throttle
 * @param delayMs - Throttle delay in milliseconds
 * @returns Throttled function
 */
export function useThrottle<T extends (...args: unknown[]) => unknown>(
    fn: T,
    delayMs: number = 500
): T {
    const lastCallRef = useRef(0);
    const timerRef = useRef<NodeJS.Timeout | null>(null);

    const throttled = useCallback(
        ((...args: unknown[]) => {
            const now = Date.now();
            const timeSinceLastCall = now - lastCallRef.current;

            if (timeSinceLastCall >= delayMs) {
                fn(...args);
                lastCallRef.current = now;
            } else {
                if (timerRef.current) {
                    clearTimeout(timerRef.current);
                }
                timerRef.current = setTimeout(() => {
                    fn(...args);
                    lastCallRef.current = Date.now();
                }, delayMs - timeSinceLastCall);
            }
        }) as T,
        [fn, delayMs]
    );

    return throttled;
}

/**
 * Handle keyboard navigation (Arrow keys, Enter, Escape)
 *
 * @param handlers - Keyboard event handlers
 */
export function useKeyboardNavigation(
    handlers: Partial<Record<string, () => void>>
): void {
    const handleKeyDown = useCallback(
        (event: KeyboardEvent) => {
            switch (event.key) {
                case 'ArrowUp':
                    event.preventDefault();
                    handlers.ArrowUp?.();
                    break;
                case 'ArrowDown':
                    event.preventDefault();
                    handlers.ArrowDown?.();
                    break;
                case 'ArrowLeft':
                    event.preventDefault();
                    handlers.ArrowLeft?.();
                    break;
                case 'ArrowRight':
                    event.preventDefault();
                    handlers.ArrowRight?.();
                    break;
                case 'Enter':
                    event.preventDefault();
                    handlers.Enter?.();
                    break;
                case 'Escape':
                    event.preventDefault();
                    handlers.Escape?.();
                    break;
                case ' ':
                    event.preventDefault();
                    handlers.Space?.();
                    break;
                default:
                    break;
            }
        },
        [handlers]
    );

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);
}

/**
 * Detect clicks outside an element
 *
 * @param ref - Reference to element
 * @param onOutsideClick - Callback when click outside
 */
export function useOutsideClick(
    ref: React.RefObject<HTMLElement>,
    onOutsideClick: () => void
): void {
    useEffect(() => {
        const handleMouseDown = (event: MouseEvent) => {
            if (ref.current && !ref.current.contains(event.target as Node)) {
                onOutsideClick();
            }
        };

        document.addEventListener('mousedown', handleMouseDown);
        return () => document.removeEventListener('mousedown', handleMouseDown);
    }, [ref, onOutsideClick]);
}

/**
 * Track previous value
 *
 * @param value - Current value
 * @returns Previous value
 */
export function usePrevious<T>(value: T): T | undefined {
    const ref = useRef<T | undefined>(undefined);

    useEffect(() => {
        ref.current = value;
    }, [value]);

    return ref.current;
}

/**
 * Handle window resize events
 *
 * @returns Current window size
 */
export function useWindowSize(): { width: number; height: number } {
    const [windowSize, setWindowSize] = useState({
        width: typeof window !== 'undefined' ? window.innerWidth : 0,
        height: typeof window !== 'undefined' ? window.innerHeight : 0,
    });

    useEffect(() => {
        const handleResize = () => {
            setWindowSize({
                width: window.innerWidth,
                height: window.innerHeight,
            });
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    return windowSize;
}

/**
 * Check if element is in viewport
 *
 * @param ref - Reference to element
 * @returns True if visible in viewport
 */
export function useInViewport(
    ref: React.RefObject<HTMLElement>
): boolean {
    const [isInViewport, setIsInViewport] = useState(false);

    useEffect(() => {
        const observer = new IntersectionObserver(([entry]) => {
            setIsInViewport(entry.isIntersecting);
        });

        if (ref.current) {
            observer.observe(ref.current);
        }

        return () => {
            if (ref.current) {
                observer.unobserve(ref.current);
            }
        };
    }, [ref]);

    return isInViewport;
}

/**
 * Track mounted state
 *
 * @returns True if component is mounted
 */
export function useIsMounted(): boolean {
    const [isMounted, setIsMounted] = useState(false);

    useEffect(() => {
        setIsMounted(true);
    }, []);

    return isMounted;
}

/**
 * Handle clipboard operations
 *
 * @returns Copy to clipboard function
 */
export function useClipboard(): (text: string) => Promise<boolean> {
    const copyToClipboard = useCallback(async (text: string) => {
        try {
            if (navigator.clipboard) {
                await navigator.clipboard.writeText(text);
                return true;
            } else {
                // Fallback for older browsers
                const textArea = document.createElement('textarea');
                textArea.value = text;
                document.body.appendChild(textArea);
                textArea.select();
                const success = document.execCommand('copy');
                document.body.removeChild(textArea);
                return success;
            }
        } catch (error) {
            console.error('Failed to copy to clipboard:', error);
            return false;
        }
    }, []);

    return copyToClipboard;
}

export default {
    useAsync,
    useToggle,
    useLocalStorage,
    useDebounce,
    useThrottle,
    useKeyboardNavigation,
    useOutsideClick,
    usePrevious,
    useWindowSize,
    useInViewport,
    useIsMounted,
    useClipboard,
};
