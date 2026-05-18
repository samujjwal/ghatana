import type { HTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  readonly onValueChange?: (value: string) => void;
}

interface SelectItemProps extends HTMLAttributes<HTMLOptionElement> {
  readonly value: string;
}

export function Select({
  children,
  onValueChange,
  onChange,
  ...props
}: SelectProps): ReactNode {
  return (
    <select
      className="h-10 rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100"
      onChange={(event) => {
        onChange?.(event);
        onValueChange?.(event.currentTarget.value);
      }}
      {...props}
    >
      {children}
    </select>
  );
}

export function SelectTrigger({
  children,
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  return <>{children}</>;
}

export function SelectValue({
  placeholder,
}: {
  readonly placeholder?: string;
}): ReactNode {
  return placeholder ? (
    <option value="" disabled>
      {placeholder}
    </option>
  ) : null;
}

export function SelectContent({
  children,
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  return <>{children}</>;
}

export function SelectItem({
  value,
  children,
  ...props
}: SelectItemProps): ReactNode {
  return (
    <option value={value} {...props}>
      {children}
    </option>
  );
}
