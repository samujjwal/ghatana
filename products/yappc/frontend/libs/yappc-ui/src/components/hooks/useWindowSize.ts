import { useState, useEffect } from 'react';

/**
 * Window dimensions containing width and height in pixels
 */
interface WindowSize {
  width: number;
  height: number;
}

/**
 * Hook that tracks window size
 * 
 * @returns Object with width and height
 * 
 * @example
 * ```tsx
 * const { width, height } = useWindowSize();
 * 
 * return <div>Window is {width}x{height}</div>;
 * ```
 */
export function useWindowSize(): WindowSize {
  const [windowSize, setWindowSize] = useState<WindowSize>({
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
    
    // Call handler right away so state gets updated with initial window size
    handleResize();

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return windowSize;
}
