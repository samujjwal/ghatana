/**
 * Stub for @base-ui/react/tabs
 * Provides minimal Tabs component functionality for web builds
 */

import React, { createContext, useContext, useState, useCallback } from 'react';

interface TabsContextValue {
  value: string;
  onValueChange: (value: string) => void;
}

const TabsContext = createContext<TabsContextValue | null>(null);

function useTabs() {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error('Tabs components must be used within a Tabs provider');
  }
  return context;
}

interface TabsProps {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  children: React.ReactNode;
}

export function Tabs({ value, defaultValue, onValueChange, children }: TabsProps) {
  const [internalValue, setInternalValue] = useState(defaultValue || '');
  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;

  const handleValueChange = useCallback((newValue: string) => {
    if (!isControlled) {
      setInternalValue(newValue);
    }
    onValueChange?.(newValue);
  }, [isControlled, onValueChange]);

  return (
    <TabsContext.Provider value={{ value: currentValue, onValueChange: handleValueChange }}>
      {children}
    </TabsContext.Provider>
  );
}

interface TabListProps {
  className?: string;
  children: React.ReactNode;
}

export function TabList({ className, children }: TabListProps) {
  return <div className={className} role="tablist">{children}</div>;
}

interface TabProps {
  value: string;
  className?: string;
  children: React.ReactNode;
}

export function Tab({ value, className, children }: TabProps) {
  const { value: selectedValue, onValueChange } = useTabs();
  const isSelected = selectedValue === value;

  return (
    <button
      type="button"
      role="tab"
      aria-selected={isSelected}
      className={className}
      onClick={() => onValueChange(value)}
    >
      {children}
    </button>
  );
}

interface TabPanelProps {
  value: string;
  className?: string;
  children: React.ReactNode;
}

export function TabPanel({ value, className, children }: TabPanelProps) {
  const { value: selectedValue } = useTabs();
  const isSelected = selectedValue === value;

  if (!isSelected) return null;

  return (
    <div className={className} role="tabpanel">
      {children}
    </div>
  );
}

// Re-export as default Tabs object with all components
Tabs.List = TabList;
Tabs.Tab = Tab;
Tabs.Panel = TabPanel;

export default Tabs;
