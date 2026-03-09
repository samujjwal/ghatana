import { useEffect, useState } from 'react';

/**
 * Debounce hook for optimizing expensive operations.
 *
 * <p><b>Purpose</b><br>
 * Delays value updates until a specified delay has passed since last change.
 * Commonly used for search inputs, API calls, and filtering to reduce
 * unnecessary computations and network requests.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const [search, setSearch] = useState('');
 * const debouncedSearch = useDebounce(search, 300);
 *
 * const { data: results } = useQuery({
 *   queryKey: ['search', debouncedSearch],
 *   queryFn: () => api.search(debouncedSearch),
 *   enabled: debouncedSearch.length > 0,
 * });
 *
 * return (
 *   <>
 *     <input
 *       value={search}
 *       onChange={(e) => setSearch(e.target.value)}
 *     />
 *     {results?.map(...)}
 *   </>
 * );
 * }</pre>
 *
 * @param value - Value to debounce
 * @param delay - Delay in milliseconds (default: 500)
 * @returns Debounced value
 *
 * @doc.type hook
 * @doc.purpose Value debouncing for performance optimization
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDebounce<T>(value: T, delay: number = 500): T {
    const [debouncedValue, setDebouncedValue] = useState<T>(value);

    useEffect(() => {
        // Set up timeout to update debounced value
        const handler = setTimeout(() => {
            setDebouncedValue(value);
        }, delay);

        // Cancel timeout on value change or cleanup
        return () => {
            clearTimeout(handler);
        };
    }, [value, delay]);

    return debouncedValue;
}
