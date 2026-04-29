import React, { createContext, useContext, useState } from "react";

interface CommandContextValue {
  value: string;
  setValue: (v: string) => void;
  search: string;
  setSearch: (s: string) => void;
}

const CommandContext = createContext<CommandContextValue>({
  value: "",
  setValue: () => undefined,
  search: "",
  setSearch: () => undefined,
});

export interface CommandProps {
  className?: string;
  children?: React.ReactNode;
  value?: string;
  onValueChange?: (v: string) => void;
}

export interface CommandInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  placeholder?: string;
}

export interface CommandListProps {
  className?: string;
  children?: React.ReactNode;
}

export interface CommandEmptyProps {
  children?: React.ReactNode;
}

export interface CommandGroupProps {
  className?: string;
  heading?: React.ReactNode;
  children?: React.ReactNode;
}

export interface CommandItemProps {
  className?: string;
  value?: string;
  onSelect?: (value: string) => void;
  disabled?: boolean;
  children?: React.ReactNode;
}

function CommandRoot({ className, children, value: controlledValue, onValueChange }: CommandProps) {
  const [uncontrolled, setUncontrolled] = useState("");
  const [search, setSearch] = useState("");
  const value = controlledValue ?? uncontrolled;
  const setValue = (v: string) => { setUncontrolled(v); onValueChange?.(v); };
  return (
    <CommandContext.Provider value={{ value, setValue, search, setSearch }}>
      <div className={className} role="listbox">{children}</div>
    </CommandContext.Provider>
  );
}

CommandRoot.Input = function CommandInput({ placeholder, className, ...props }: CommandInputProps) {
  const { setSearch, search } = useContext(CommandContext);
  return (
    <input
      role="combobox"
      aria-expanded="true"
      placeholder={placeholder}
      className={className}
      value={search}
      onChange={(e) => setSearch(e.target.value)}
      {...props}
    />
  );
};

CommandRoot.List = function CommandList({ className, children }: CommandListProps) {
  return <div className={className} role="listbox">{children}</div>;
};

CommandRoot.Empty = function CommandEmpty({ children }: CommandEmptyProps) {
  return <div role="presentation">{children}</div>;
};

CommandRoot.Group = function CommandGroup({ className, heading, children }: CommandGroupProps) {
  return (
    <div className={className} role="group">
      {heading && <div role="presentation">{heading}</div>}
      {children}
    </div>
  );
};

CommandRoot.Item = function CommandItem({ className, value = "", onSelect, disabled, children }: CommandItemProps) {
  const { setValue } = useContext(CommandContext);
  return (
    <div
      className={className}
      role="option"
      aria-disabled={disabled}
      aria-selected={false}
      onClick={() => { if (!disabled) { setValue(value); onSelect?.(value); } }}
    >
      {children}
    </div>
  );
};

export const Command = CommandRoot;
export default Command;
