import { useEffect, useRef } from 'react';

/**
 * Hook for setting up an interval
 * 
 * @param callback - Function to call on each interval
 * @param delay - Delay in milliseconds (null to pause)
 * 
 * @example
 * ```tsx
 * const [count, setCount] = useState(0);
 * useInterval(() => {
 *   setCount(count + 1);
 * }, 1000);
 * ```
 */
export function useInterval(callback: () => void, delay: number | null): void {
  const savedCallback = useRef(callback);

  // Remember the latest callback
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  // Set up the interval
  useEffect(() => {
    if (delay === null) {
      return;
    }

    const id = setInterval(() => {
      savedCallback.current();
    }, delay);

    return () => {
      clearInterval(id);
    };
  }, [delay]);
}
