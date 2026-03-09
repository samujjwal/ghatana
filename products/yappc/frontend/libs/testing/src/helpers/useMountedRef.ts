import { useRef, useEffect } from 'react';

/**
 * useMountedRef
 *
 * Returns a ref whose current value reflects whether the component is mounted.
 * Useful for guarding setState calls in async callbacks during tests.
 */
export function useMountedRef(): { current: boolean } {
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  return mountedRef;
}

export default useMountedRef;
