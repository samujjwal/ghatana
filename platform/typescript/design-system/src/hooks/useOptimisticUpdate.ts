import { useState, useCallback, useRef, useEffect } from 'react';

/**
 * Minimal toast interface for optimistic update notifications.
 * Consumers should provide a global toast implementation (e.g., from @ghatana/ui)
 * or override via the onSuccess/onError callbacks.
 */
const toast = {
  success: (message: string) => {
    if (typeof console !== 'undefined') {
      console.info('[toast:success]', message);
    }
  },
  error: (message: string) => {
    if (typeof console !== 'undefined') {
      console.warn('[toast:error]', message);
    }
  },
};

export interface OptimisticUpdateOptions<T> {
  /** Initial data */
  data: T;
  /** Function to update data (returns promise) */
  updateFn: (data: T) => Promise<T>;
  /** Success callback */
  onSuccess?: (data: T) => void;
  /** Error callback */
  onError?: (error: Error) => void;
  /** Show toast notifications */
  showToast?: boolean;
  /** Success toast message */
  successMessage?: string;
  /** Error toast message */
  errorMessage?: string;
}

export interface OptimisticUpdateResult<T> {
  /** Current data (optimistic or actual) */
  data: T;
  /** Whether update is in progress */
  isLoading: boolean;
  /** Error if update failed */
  error: Error | null;
  /** Trigger optimistic update */
  update: (newData: T) => Promise<void>;
  /** Reset to initial state */
  reset: () => void;
}

/**
 * Hook for optimistic UI updates
 * 
 * Immediately updates UI with new data, then performs async update.
 * Reverts on error with toast notification.
 * 
 * @doc.type hook
 * @doc.purpose Optimistic UI pattern for better perceived performance
 * @doc.layer core
 * @doc.pattern Optimistic Update Hook
 * 
 * @example
 * ```tsx
 * const { data, isLoading, update } = useOptimisticUpdate({
 *   data: content,
 *   updateFn: async (updated) => {
 *     return await api.updateContent(updated);
 *   },
 *   successMessage: 'Content saved!',
 *   errorMessage: 'Failed to save',
 * });
 * 
 * const handleTitleChange = (title: string) => {
 *   update({ ...data, title });
 * };
 * ```
 */
export function useOptimisticUpdate<T>({
  data: initialData,
  updateFn,
  onSuccess,
  onError,
  showToast = true,
  successMessage = 'Update successful',
  errorMessage = 'Update failed',
}: OptimisticUpdateOptions<T>): OptimisticUpdateResult<T> {
  const [data, setData] = useState<T>(initialData);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const previousDataRef = useRef<T>(initialData);
  const isUnmountedRef = useRef(false);

  useEffect(() => {
    return () => {
      isUnmountedRef.current = true;
    };
  }, []);

  const update = useCallback(
    async (newData: T) => {
      // Store previous data for rollback
      previousDataRef.current = data;

      // Optimistically update UI immediately
      setData(newData);
      setIsLoading(true);
      setError(null);

      try {
        // Perform actual update
        const result = await updateFn(newData);

        if (isUnmountedRef.current) return;

        // Update with server response
        setData(result);
        setIsLoading(false);

        if (showToast) {
          toast.success(successMessage);
        }

        onSuccess?.(result);
      } catch (err) {
        if (isUnmountedRef.current) return;

        const error = err instanceof Error ? err : new Error('Unknown error');

        // Rollback to previous data
        setData(previousDataRef.current);
        setIsLoading(false);
        setError(error);

        if (showToast) {
          toast.error(errorMessage);
        }

        onError?.(error);
      }
    },
    [data, updateFn, onSuccess, onError, showToast, successMessage, errorMessage]
  );

  const reset = useCallback(() => {
    setData(initialData);
    setIsLoading(false);
    setError(null);
  }, [initialData]);

  return {
    data,
    isLoading,
    error,
    update,
    reset,
  };
}

/**
 * Hook for optimistic list updates (add/remove/update items)
 */
export interface OptimisticListOptions<T> {
  /** Initial list */
  items: T[];
  /** Add item function */
  addFn?: (item: T) => Promise<T>;
  /** Update item function */
  updateFn?: (item: T) => Promise<T>;
  /** Remove item function */
  removeFn?: (id: string) => Promise<void>;
  /** Get item ID */
  getItemId: (item: T) => string;
  /** Show toast notifications */
  showToast?: boolean;
}

export interface OptimisticListResult<T> {
  /** Current items */
  items: T[];
  /** Whether any operation is in progress */
  isLoading: boolean;
  /** Add item optimistically */
  add: (item: T) => Promise<void>;
  /** Update item optimistically */
  update: (item: T) => Promise<void>;
  /** Remove item optimistically */
  remove: (id: string) => Promise<void>;
}

export function useOptimisticList<T>({
  items: initialItems,
  addFn,
  updateFn,
  removeFn,
  getItemId,
  showToast = true,
}: OptimisticListOptions<T>): OptimisticListResult<T> {
  const [items, setItems] = useState<T[]>(initialItems);
  const [isLoading, setIsLoading] = useState(false);
  const previousItemsRef = useRef<T[]>(initialItems);

  const add = useCallback(
    async (item: T) => {
      if (!addFn) return;

      previousItemsRef.current = items;
      setItems([...items, item]);
      setIsLoading(true);

      try {
        const result = await addFn(item);
        setItems((prev) => [...prev.filter((i) => getItemId(i) !== getItemId(item)), result]);
        setIsLoading(false);

        if (showToast) {
          toast.success('Item added');
        }
      } catch {
        setItems(previousItemsRef.current);
        setIsLoading(false);

        if (showToast) {
          toast.error('Failed to add item');
        }
      }
    },
    [items, addFn, getItemId, showToast]
  );

  const update = useCallback(
    async (item: T) => {
      if (!updateFn) return;

      previousItemsRef.current = items;
      const id = getItemId(item);
      setItems(items.map((i) => (getItemId(i) === id ? item : i)));
      setIsLoading(true);

      try {
        const result = await updateFn(item);
        setItems((prev) => prev.map((i) => (getItemId(i) === id ? result : i)));
        setIsLoading(false);

        if (showToast) {
          toast.success('Item updated');
        }
      } catch {
        setItems(previousItemsRef.current);
        setIsLoading(false);

        if (showToast) {
          toast.error('Failed to update item');
        }
      }
    },
    [items, updateFn, getItemId, showToast]
  );

  const remove = useCallback(
    async (id: string) => {
      if (!removeFn) return;

      previousItemsRef.current = items;
      setItems(items.filter((i) => getItemId(i) !== id));
      setIsLoading(true);

      try {
        await removeFn(id);
        setIsLoading(false);

        if (showToast) {
          toast.success('Item removed');
        }
      } catch {
        setItems(previousItemsRef.current);
        setIsLoading(false);

        if (showToast) {
          toast.error('Failed to remove item');
        }
      }
    },
    [items, removeFn, getItemId, showToast]
  );

  return {
    items,
    isLoading,
    add,
    update,
    remove,
  };
}

export default useOptimisticUpdate;
