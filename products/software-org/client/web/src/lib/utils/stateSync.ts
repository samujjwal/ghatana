/**
 * State Synchronization Utilities
 *
 * <p><b>Purpose</b><br>
 * Utilities for synchronizing Jotai atom state with external data sources,
 * managing derived state, handling updates, and coordinating between stores.
 *
 * <p><b>Functions</b><br>
 * - syncAtomWithQuery: Sync Jotai atom with React Query
 * - createDerivedAtom: Create derived state atom
 * - syncAtomWithStorage: Persist atom state to localStorage
 - - debounceAtom: Debounce atom updates
 * - createAtomSubscription: Subscribe to atom changes
 * - batchAtomUpdates: Batch multiple atom updates
 *
 * @doc.type utility
 * @doc.purpose Jotai state synchronization and coordination
 * @doc.layer product
 * @doc.pattern Utility Module
 */

import { atom, Atom } from 'jotai';
import { atomWithStorage, createJSONStorage } from 'jotai/utils';
import { useEffect, useRef } from 'react';

type AtomUpdater<T> = (prev: T) => T;
type AtomSetWithUpdater<T> = (update: AtomUpdater<T>) => void;
type AtomSetValue<T> = (value: T) => void;

/**
 * Options for atom-query synchronization
 */
export interface AtomQuerySyncOptions {
    selectFn?: (data: any) => any;
    transformFn?: (data: any) => any;
    errorHandler?: (error: any) => any;
}

/**
 * Options for storage persistence
 */
export interface StoragePersistenceOptions {
    key: string;
    serializer?: (value: any) => string;
    deserializer?: (value: string) => any;
    version?: number;
}

/**
 * Debounce configuration
 */
export interface DebounceConfig {
    delayMs: number;
    trailing?: boolean;
    leading?: boolean;
}

/**
 * Synchronize Jotai atom with React Query data
 *
 * @param queryData - Data from React Query
 * @param setAtom - Atom setter
 * @param options - Sync options
 */
export function syncAtomWithQuery<T>(
    queryData: T | undefined,
    setAtom: AtomSetWithUpdater<{ data: T; lastUpdate: Date } & Record<string, unknown>>,
    options: AtomQuerySyncOptions = {}
): void {
    const { selectFn, transformFn, errorHandler } = options;

    if (queryData === undefined) return;

    try {
        let finalData = queryData;

        if (selectFn) {
            finalData = selectFn(finalData);
        }

        if (transformFn) {
            finalData = transformFn(finalData);
        }

        setAtom((prev) => ({
            ...prev,
            data: finalData,
            lastUpdate: new Date(),
        }));
    } catch (error) {
        if (errorHandler) {
            errorHandler(error);
        }
    }
}

/**
 * Create persistent atom with localStorage
 *
 * @param initialValue - Initial value
 * @param options - Storage options
 * @returns Jotai atom
 */
export function createPersistentAtom<T>(
    initialValue: T,
    options: StoragePersistenceOptions
): Atom<T> {
    const { key, version } = options;

    const versionedKey = version ? `${key}_v${version}` : key;

    return atomWithStorage(versionedKey, initialValue, createJSONStorage(() => localStorage), {
        getOnInit: true,
    });
}

/**
 * Create derived atom that depends on another atom
 *
 * @param sourceAtom - Source atom to derive from
 * @param deriveFn - Function to derive value
 * @returns Derived atom
 */
export function createDerivedAtom<T, U>(
    sourceAtom: Atom<T>,
    deriveFn: (value: T) => U
): Atom<U> {
    return atom((get) => {
        const source = get(sourceAtom);
        return deriveFn(source);
    });
}

/**
 * Create debounced atom update
 *
 * @param atom - Target atom
 * @param config - Debounce configuration
 * @returns Debounced setter function
 */
export function createDebouncedAtomUpdater<T>(
    setAtom: AtomSetValue<T>,
    config: DebounceConfig
): (value: T) => void {
    const { delayMs, trailing = true, leading = false } = config;
    let timeoutId: NodeJS.Timeout | null = null;
    let leadingExecuted = false;

    return (value: T) => {
        if (leading && !leadingExecuted) {
            setAtom(value);
            leadingExecuted = true;
        }

        if (timeoutId) {
            clearTimeout(timeoutId);
        }

        timeoutId = setTimeout(() => {
            if (trailing) {
                setAtom(value);
            }
            leadingExecuted = false;
            timeoutId = null;
        }, delayMs);
    };
}

/**
 * Hook for atom subscription
 *
 * @param atom - Atom to subscribe to
 * @param callback - Callback on atom change
 */
export function useAtomSubscription<T>(
    atom: Atom<T>,
    callback: (value: T) => void
): void {
    const callbackRef = useRef(callback);

    useEffect(() => {
        callbackRef.current = callback;
    }, [callback]);

    useEffect(() => {
        // Note: This is a placeholder for actual atom subscription
        // In real implementation, you'd use Jotai's built-in subscription mechanism
        // through useEffect with proper atom reading
    }, [atom]);
}

/**
 * Create atom for managing list items
 *
 * @param initialItems - Initial items
 * @returns List management atom and helpers
 */
export function createListAtom<T extends { id: string | number }>(
    initialItems: T[] = []
) {
    const itemsAtom = atom<T[]>(initialItems);

    const addItemAtom = atom(null, (_, set, item: T) => {
        set(itemsAtom, (prev) => [...prev, item]);
    });

    const removeItemAtom = atom(null, (_, set, id: string | number) => {
        set(itemsAtom, (prev) => prev.filter((item) => item.id !== id));
    });

    const updateItemAtom = atom(null, (_, set, id: string | number, updates: Partial<T>) => {
        set(itemsAtom, (prev) =>
            prev.map((item) => (item.id === id ? { ...item, ...updates } : item))
        );
    });

    const clearItemsAtom = atom(null, (_, set) => {
        set(itemsAtom, []);
    });

    return {
        itemsAtom,
        addItemAtom,
        removeItemAtom,
        updateItemAtom,
        clearItemsAtom,
    };
}

/**
 * Create atom for managing pagination state
 *
 * @param itemsPerPage - Items per page
 * @returns Pagination atom and helpers
 */
export function createPaginationAtom(itemsPerPage: number = 10) {
    const paginationAtom = atom({
        page: 1,
        pageSize: itemsPerPage,
        total: 0,
    });

    const setPageAtom = atom(null, (_, set, page: number) => {
        set(paginationAtom, (prev) => ({
            ...prev,
            page,
        }));
    });

    const setPageSizeAtom = atom(null, (_, set, pageSize: number) => {
        set(paginationAtom, (prev) => ({
            ...prev,
            pageSize,
            page: 1,
        }));
    });

    const setTotalAtom = atom(null, (_, set, total: number) => {
        set(paginationAtom, (prev) => ({
            ...prev,
            total,
        }));
    });

    const nextPageAtom = atom(null, (get, set) => {
        const { page, pageSize, total } = get(paginationAtom);
        const maxPage = Math.ceil(total / pageSize);
        if (page < maxPage) {
            set(paginationAtom, (prev) => ({
                ...prev,
                page: page + 1,
            }));
        }
    });

    const prevPageAtom = atom(null, (get, set) => {
        const { page } = get(paginationAtom);
        if (page > 1) {
            set(paginationAtom, (prev) => ({
                ...prev,
                page: page - 1,
            }));
        }
    });

    return {
        paginationAtom,
        setPageAtom,
        setPageSizeAtom,
        setTotalAtom,
        nextPageAtom,
        prevPageAtom,
    };
}

/**
 * Create atom for managing filter state
 *
 * @param initialFilters - Initial filter values
 * @returns Filter atom and helpers
 */
export function createFilterAtom(
    initialFilters: Record<string, any> = {}
) {
    const filterAtom = atom(initialFilters);

    const setFilterAtom = atom(
        null,
        (_, set, key: string, value: any) => {
            set(filterAtom, (prev) => ({
                ...prev,
                [key]: value,
            }));
        }
    );

    const clearFilterAtom = atom(null, (_, set, key?: string) => {
        set(filterAtom, (prev) => {
            if (key) {
                const { [key]: _, ...rest } = prev;
                return rest;
            }
            return {};
        });
    });

    const resetFiltersAtom = atom(null, (_, set) => {
        set(filterAtom, initialFilters);
    });

    return {
        filterAtom,
        setFilterAtom,
        clearFilterAtom,
        resetFiltersAtom,
    };
}

/**
 * Create atom for managing sort state
 *
 * @param initialSort - Initial sort configuration
 * @returns Sort atom and helpers
 */
export function createSortAtom(
    initialSort: { by?: string; direction: 'asc' | 'desc' } = { direction: 'asc' }
) {
    const sortAtom = atom(initialSort);

    const setSortAtom = atom(
        null,
        (_, set, by: string, direction: 'asc' | 'desc') => {
            set(sortAtom, { by, direction });
        }
    );

    const toggleSortAtom = atom(
        null,
        (get, set, by: string) => {
            const current = get(sortAtom);
            const direction =
                current.by === by && current.direction === 'asc' ? 'desc' : 'asc';
            set(sortAtom, { by, direction });
        }
    );

    return {
        sortAtom,
        setSortAtom,
        toggleSortAtom,
    };
}

export default {
    syncAtomWithQuery,
    createPersistentAtom,
    createDerivedAtom,
    createDebouncedAtomUpdater,
    useAtomSubscription,
    createListAtom,
    createPaginationAtom,
    createFilterAtom,
    createSortAtom,
};
