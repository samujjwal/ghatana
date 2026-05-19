import React, { createContext, useContext, useMemo, useState } from 'react';
import type { HTMLAttributes, ReactNode } from 'react';

interface TabsContextValue {
  value: string;
  setValue: (next: string) => void;
}

const TabsContext = createContext<TabsContextValue | null>(null);

interface TabsProps extends HTMLAttributes<HTMLDivElement> {
  defaultValue: string;
  children: ReactNode;
}

export const Tabs: React.FC<TabsProps> = ({ defaultValue, children, className = '', ...props }) => {
  const [value, setValue] = useState<string>(defaultValue);
  const context = useMemo<TabsContextValue>(() => ({ value, setValue }), [value]);

  return (
    <TabsContext.Provider value={context}>
      <div className={className} {...props}>
        {children}
      </div>
    </TabsContext.Provider>
  );
};

interface TabsListProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export const TabsList: React.FC<TabsListProps> = ({ children, className = '', ...props }) => (
  <div
    role="tablist"
    className={['inline-flex gap-2 rounded-md bg-zinc-100 p-1', className].filter(Boolean).join(' ')}
    {...props}
  >
    {children}
  </div>
);

interface TabsTriggerProps extends HTMLAttributes<HTMLButtonElement> {
  value: string;
  children: ReactNode;
}

export const TabsTrigger: React.FC<TabsTriggerProps> = ({ value, children, className = '', ...props }) => {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error('TabsTrigger must be used within Tabs');
  }

  const active = context.value === value;

  return (
    <button
      role="tab"
      type="button"
      aria-selected={active}
      onClick={() => context.setValue(value)}
      className={[
        'rounded px-3 py-1.5 text-sm',
        active ? 'bg-white font-medium shadow-sm' : 'text-zinc-600',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </button>
  );
};

interface TabsContentProps extends HTMLAttributes<HTMLDivElement> {
  value: string;
  children: ReactNode;
}

export const TabsContent: React.FC<TabsContentProps> = ({ value, children, className = '', ...props }) => {
  const context = useContext(TabsContext);
  if (!context || context.value !== value) {
    return null;
  }

  return (
    <div role="tabpanel" className={className} {...props}>
      {children}
    </div>
  );
};
