import React, { createContext, useContext, useState } from "react";

interface PopoverContextValue {
  open: boolean;
  setOpen: (v: boolean) => void;
}

const PopoverContext = createContext<PopoverContextValue>({ open: false, setOpen: () => undefined });

export interface PopoverProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  children?: React.ReactNode;
}

export interface PopoverTriggerProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  asChild?: boolean;
  children?: React.ReactNode;
}

export interface PopoverContentProps {
  className?: string;
  align?: "start" | "center" | "end";
  sideOffset?: number;
  children?: React.ReactNode;
}

function PopoverRoot({ open: controlledOpen, onOpenChange, children }: PopoverProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;
  const setOpen = (v: boolean) => {
    setUncontrolledOpen(v);
    onOpenChange?.(v);
  };
  return (
    <PopoverContext.Provider value={{ open, setOpen }}>
      <div style={{ position: "relative", display: "inline-block" }}>{children}</div>
    </PopoverContext.Provider>
  );
}

PopoverRoot.Trigger = function PopoverTrigger({ children, asChild: _asChild, ...props }: PopoverTriggerProps) {
  const { setOpen, open } = useContext(PopoverContext);
  return (
    <button type="button" onClick={() => setOpen(!open)} {...props}>
      {children}
    </button>
  );
};

PopoverRoot.Content = function PopoverContent({ className, children }: PopoverContentProps) {
  const { open } = useContext(PopoverContext);
  if (!open) return null;
  return (
    <div className={className} style={{ position: "absolute", zIndex: 50, background: "white", border: "1px solid #e2e8f0", borderRadius: 6, boxShadow: "0 4px 6px rgba(0,0,0,0.1)", minWidth: 200 }}>
      {children}
    </div>
  );
};

export const Popover = PopoverRoot;
export default Popover;
