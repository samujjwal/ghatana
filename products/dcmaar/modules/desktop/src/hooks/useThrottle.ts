import { useRef, useCallback, useEffect, useState } from 'react';

/**
 * Custom hook that returns a throttled version of the provided callback function
 * @param callback The function to throttle
 * @param limit The time in milliseconds to throttle the function
 * @returns The throttled function
 */
export function useThrottle<T extends (...args: unknown[]) => any>(
  callback: T,
  limit: number
): (...args: Parameters<T>) => void {
  const lastRan = useRef<number>(0);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const pendingArgsRef = useRef<Parameters<T> | null>(null);
  const callbackRef = useRef(callback);

  // Update callback ref if callback changes
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  // Cleanup function to clear any pending timeouts
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const executeCallback = useCallback((args: Parameters<T>) => {
    callbackRef.current(...args);
    lastRan.current = Date.now();
    pendingArgsRef.current = null;
  }, []);

  return useCallback(
    (...args: Parameters<T>) => {
      const now = Date.now();
      const timeSinceLastRun = now - lastRan.current;
      
      // If we haven't run for more than the limit, execute immediately
      if (timeSinceLastRun >= limit) {
        executeCallback(args);
        return;
      }

      // Otherwise, store the latest arguments for later execution
      pendingArgsRef.current = args;

      // If we don't have a timeout set, set one up to execute after the remaining time
      if (!timeoutRef.current) {
        const timeRemaining = limit - timeSinceLastRun;
        
        timeoutRef.current = setTimeout(() => {
          if (pendingArgsRef.current) {
            executeCallback(pendingArgsRef.current);
          }
          timeoutRef.current = null;
        }, timeRemaining);
      }
    },
    [executeCallback, limit]
  );
}

/**
 * Custom hook that returns a throttled version of the provided value
 * @param value The value to throttle
 * @param limit The time in milliseconds to throttle the value
 * @returns The throttled value
 */
export function useThrottledValue<T>(value: T, limit: number): T {
  const [throttledValue, setThrottledValue] = useState<T>(value);
  const lastRan = useRef<number>(0);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const pendingValueRef = useRef<T | null>(null);

  useEffect(() => {
    const now = Date.now();
    const timeSinceLastRun = now - lastRan.current;
    
    // If we haven't updated for more than the limit, update immediately
    if (timeSinceLastRun >= limit) {
      setThrottledValue(value);
      lastRan.current = now;
      return;
    }

    // Otherwise, store the latest value for later update
    pendingValueRef.current = value;

    // If we don't have a timeout set, set one up to update after the remaining time
    if (!timeoutRef.current) {
      const timeRemaining = limit - timeSinceLastRun;
      
      timeoutRef.current = setTimeout(() => {
        if (pendingValueRef.current !== null) {
          setThrottledValue(pendingValueRef.current);
          lastRan.current = Date.now();
          pendingValueRef.current = null;
        }
        timeoutRef.current = null;
      }, timeRemaining);
    }

    // Cleanup function to clear the timeout if the component unmounts
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [value, limit]);

  return throttledValue;
}

export default useThrottle;
