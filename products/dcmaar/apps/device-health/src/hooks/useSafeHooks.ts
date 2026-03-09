import { useEffect, useRef, createElement } from 'react';

/**
 * Custom hook that ensures React is fully initialized before any hooks are used
 */
export const useSafeHooks = () => {
  const isInitialized = useRef(false);
  
  // This effect runs after the component mounts
  useEffect(() => {
    isInitialized.current = true;
    return () => {
      isInitialized.current = false;
    };
  }, []);

  // Return the current initialization state
  return isInitialized.current;
};

/**
 * Higher-Order Component that ensures safe hook usage
 */
export const withSafeHooks = <P extends Record<string, unknown>>(
  Component: React.ComponentType<P>
): React.FC<P> => {
  const SafeComponent: React.FC<P> = (props: P) => {
    const isReady = useSafeHooks();
    
    // Don't render anything until hooks are safe to use
    if (!isReady) {
      return null;
    }
    
    return createElement(Component, props);
  };
  
  return SafeComponent;
};
