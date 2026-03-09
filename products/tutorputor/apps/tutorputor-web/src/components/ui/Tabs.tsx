import React, { useState } from 'react';

interface TabsProps extends React.HTMLAttributes<HTMLDivElement> {
  defaultValue?: string;
  value?: string;
  onValueChange?: (value: string) => void;
}

export function Tabs({ 
  defaultValue, 
  value: controlledValue,
  onValueChange,
  children, 
  className = '', 
  ...props 
}: TabsProps) {
  const [internalActiveTab, setInternalActiveTab] = useState(defaultValue || '');
  
  // Support both controlled and uncontrolled components
  const activeTab = controlledValue !== undefined ? controlledValue : internalActiveTab;
  const setActiveTab = (newValue: string) => {
    setInternalActiveTab(newValue);
    onValueChange?.(newValue);
  };

  return (
    <div className={`w-full ${className}`} {...props}>
      {React.Children.map(children, child =>
        React.cloneElement(child as React.ReactElement<any>, { activeTab, setActiveTab })
      )}
    </div>
  );
}

export function TabList({ children, className = '', activeTab, setActiveTab, ...props }: any) {
  return (
    <div 
      className={`flex border-b border-gray-200 ${className}`}
      {...props}
    >
      {React.Children.map(children, child =>
        React.cloneElement(child as React.ReactElement<any>, { activeTab, setActiveTab })
      )}
    </div>
  );
}

export function Tab({ 
  value, 
  children, 
  className = '',
  activeTab,
  setActiveTab,
  ...props 
}: any) {
  const isActive = activeTab === value;
  return (
    <button
      onClick={() => setActiveTab(value)}
      className={`px-4 py-2 font-medium border-b-2 transition-colors ${
        isActive 
          ? 'border-blue-600 text-blue-600' 
          : 'border-transparent text-gray-600 hover:text-gray-900'
      } ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function TabPanel({ 
  value, 
  children, 
  className = '',
  activeTab,
  ...props 
}: any) {
  if (activeTab !== value) return null;

  return (
    <div className={`w-full ${className}`} {...props}>
      {children}
    </div>
  );
}

// Aliases for alternate naming convention
export const TabsList = TabList;
export const TabsTrigger = Tab;
export const TabsContent = TabPanel;
