import React, { useEffect, useRef } from 'react';

/**
 * Hook to measure component render time
 * @param componentName Name of the component to measure
 * @param threshold Threshold in milliseconds to log warnings
 */
export function useRenderPerformance(componentName: string, threshold: number = 16) {
  const renderStartTime = useRef<number>(0);
  
  useEffect(() => {
    const renderTime = performance.now() - renderStartTime.current;
    
    if (renderTime > threshold) {
      console.warn(`[Performance] ${componentName} took ${renderTime.toFixed(2)}ms to render (threshold: ${threshold}ms)`);
    }
    
    // Log render time to performance monitoring if available
    if (window.performance && window.performance.mark) {
      window.performance.mark(`${componentName}-render-end`);
      window.performance.measure(
        `${componentName}-render-duration`,
        `${componentName}-render-start`,
        `${componentName}-render-end`
      );
    }
    
    return () => {
      // Clean up performance marks
      if (window.performance && window.performance.clearMarks) {
        window.performance.clearMarks(`${componentName}-render-start`);
        window.performance.clearMarks(`${componentName}-render-end`);
      }
    };
  });
  
  // Set render start time
  renderStartTime.current = performance.now();
  
  // Create performance mark for render start
  if (window.performance && window.performance.mark) {
    window.performance.mark(`${componentName}-render-start`);
  }
}

/**
 * Hook to detect unnecessary re-renders
 * @param componentName Name of the component to monitor
 * @param props Component props
 */
export function useDetectReRenders(componentName: string, props: Record<string, unknown>) {
  const prevPropsRef = useRef<Record<string, unknown>>({});
  
  useEffect(() => {
    const changedProps: Record<string, { prev: unknown; next: unknown }> = {};
    const prevProps = prevPropsRef.current;
    
    // Find changed props
    Object.keys(props).forEach(key => {
      if (props[key] !== prevProps[key]) {
        changedProps[key] = {
          prev: prevProps[key],
          next: props[key]
        };
      }
    });
    
    // Log changed props if any
    if (Object.keys(changedProps).length > 0) {
      console.log(`[ReRender] ${componentName} re-rendered due to prop changes:`, changedProps);
    }
    
    // Update prevProps
    prevPropsRef.current = { ...props };
  });
}

/**
 * Creates a debounced version of a function
 * @param func Function to debounce
 * @param wait Wait time in milliseconds
 * @returns Debounced function
 */
export function debounce<T extends (...args: unknown[]) => any>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: ReturnType<typeof setTimeout> | null = null;
  
  return function(...args: Parameters<T>): void {
    const later = () => {
      timeout = null;
      func(...args);
    };
    
    if (timeout !== null) {
      clearTimeout(timeout);
    }
    
    timeout = setTimeout(later, wait);
  };
}

/**
 * Creates a throttled version of a function
 * @param func Function to throttle
 * @param limit Limit time in milliseconds
 * @returns Throttled function
 */
export function throttle<T extends (...args: unknown[]) => any>(
  func: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false;
  let lastArgs: Parameters<T> | null = null;
  
  return function(...args: Parameters<T>): void {
    if (!inThrottle) {
      func(...args);
      inThrottle = true;
      
      setTimeout(() => {
        inThrottle = false;
        if (lastArgs) {
          func(...lastArgs);
          lastArgs = null;
        }
      }, limit);
    } else {
      lastArgs = args;
    }
  };
}

/**
 * Utility to help with React.memo usage
 * @param Component Component to memoize
 * @param propsAreEqual Custom props comparison function
 * @returns Memoized component
 */
export function memoWithLogging<P extends object>(
  Component: React.ComponentType<P>,
  propsAreEqual?: (prevProps: Readonly<P>, nextProps: Readonly<P>) => boolean
): React.MemoExoticComponent<React.ComponentType<P>> {
  const componentName = Component.displayName || Component.name || 'Component';
  
  // Custom props comparison with logging
  const customPropsAreEqual = (prevProps: Readonly<P>, nextProps: Readonly<P>): boolean => {
    // Use custom comparison if provided
    if (propsAreEqual) {
      const areEqual = propsAreEqual(prevProps, nextProps);
      
      // If props are equal, no re-render needed
      if (!areEqual) {
        const changedProps: Record<string, { prev: unknown; next: unknown }> = {};
        
        // Find changed props
        Object.keys(prevProps).forEach(key => {
          if ((prevProps as unknown)[key] !== (nextProps as unknown)[key]) {
            changedProps[key] = {
              prev: (prevProps as unknown)[key],
              next: (nextProps as unknown)[key]
            };
          }
        });
        
        // Log changed props
        if (Object.keys(changedProps).length > 0) {
          console.log(`[Memo] ${componentName} will re-render due to prop changes:`, changedProps);
        }
      }
      
      return areEqual;
    }
    
    // Default shallow comparison
    return false;
  };
  
  // Set display name for the memoized component
  const MemoizedComponent = React.memo(Component, customPropsAreEqual);
  MemoizedComponent.displayName = `Memo(${componentName})`;
  
  return MemoizedComponent;
}
