import { lazy, type ComponentType } from 'react';

/**
 * Lazy load a component with a retry mechanism
 * Handles network failures by retrying the import
 */
export function lazyWithRetry<T extends ComponentType<Record<string, unknown>>>(
  componentImport: () => Promise<{ default: T }>,
  retries = 3,
  interval = 1000
): React.LazyExoticComponent<T> {
  return lazy(() => 
    new Promise<{ default: T }>((resolve, reject) => {
      const attemptImport = (retriesLeft: number) => {
        componentImport()
          .then(resolve)
          .catch((error: unknown) => {
            if (retriesLeft === 0) {
              reject(error);
              return;
            }
            
            setTimeout(() => {
              console.log(`Retrying import... (${retriesLeft} attempts left)`);
              attemptImport(retriesLeft - 1);
            }, interval);
          });
      };
      
      attemptImport(retries);
    })
  );
}

/**
 * Preload a lazy component
 * Useful for prefetching components before user navigates to them
 */
export function preloadComponent<T extends ComponentType<Record<string, unknown>>>(
  LazyComponent: React.LazyExoticComponent<T>
): void {
  // TypeScript workaround to access the _init method
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component = LazyComponent as any;
  if (component._init) {
    component._init(component._payload);
  }
}
