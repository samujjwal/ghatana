import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from "react";
import { createContext, useContext } from "react";

interface TabsProps extends HTMLAttributes<HTMLDivElement> {
  readonly value?: string;
  readonly onValueChange?: (value: string) => void;
}

interface TabValueProps {
  readonly value: string;
}

interface TabsContextValue {
  readonly value?: string;
  readonly onValueChange?: (value: string) => void;
}

const TabsContext = createContext<TabsContextValue>({});

export function Tabs({
  className = "",
  value,
  onValueChange,
  ...props
}: TabsProps): ReactNode {
  return (
    <TabsContext.Provider value={{ value, onValueChange }}>
      <div className={className} {...props} />
    </TabsContext.Provider>
  );
}

export function TabsList({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  return (
    <div
      className={`inline-flex rounded-md bg-gray-100 p-1 ${className}`}
      role="tablist"
      {...props}
    />
  );
}

export function TabsTrigger({
  className = "",
  value,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & TabValueProps): ReactNode {
  const tabs = useContext(TabsContext);
  const selected = tabs.value === value;
  return (
    <button
      aria-selected={selected}
      className={`rounded px-3 py-1.5 text-sm font-medium ${selected ? "bg-white text-gray-900 shadow-sm" : "text-gray-700 hover:bg-white"} ${className}`}
      onClick={(event) => {
        props.onClick?.(event);
        tabs.onValueChange?.(value);
      }}
      role="tab"
      type="button"
      data-value={value}
      {...props}
    />
  );
}

export function TabsContent({
  className = "",
  value,
  ...props
}: HTMLAttributes<HTMLDivElement> & TabValueProps): ReactNode {
  const tabs = useContext(TabsContext);
  if (tabs.value !== undefined && tabs.value !== value) {
    return null;
  }
  return (
    <div
      className={`mt-4 ${className}`}
      data-value={value}
      role="tabpanel"
      {...props}
    />
  );
}
