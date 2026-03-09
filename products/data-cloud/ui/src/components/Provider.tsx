import React, { useEffect } from 'react';
import { Provider as JotaiProvider, useStore, type Atom, type WritableAtom } from 'jotai';
import type { Store } from 'jotai/vanilla/store';

interface ProviderProps {
  children: React.ReactNode;
  store?: Store;
  initialValues?: Array<[Atom<unknown>, unknown]>;
}

/**
 * Provider component that wraps the application with Jotai and initial values.
 * 
 * @component
 * @example
 * ```tsx
 * <Provider 
 *   initialValues={[[countAtom, 10], [nameAtom, 'John']]}
 * >
 *   <App />
 * </Provider>
 * ```
 */
export const Provider: React.FC<ProviderProps> = ({ 
  children, 
  store, 
  initialValues = [] 
}) => {
  if (initialValues.length > 0) {
    return (
      <JotaiProvider store={store}>
        <AtomInitializer initialValues={initialValues}>
          {children}
        </AtomInitializer>
      </JotaiProvider>
    );
  }
  
  return <JotaiProvider store={store}>{children}</JotaiProvider>;
};

interface AtomInitializerProps {
  children: React.ReactNode;
  initialValues: Array<[Atom<unknown>, unknown]>;
}

const AtomInitializer: React.FC<AtomInitializerProps> = ({ 
  children, 
  initialValues 
}) => {
  const store = useStore();

  useEffect(() => {
    initialValues.forEach(([atom, value]) => {
      store.set(atom as WritableAtom<unknown, [unknown], void>, value);
    });
  }, [initialValues, store]);

  return <>{children}</>;
};
