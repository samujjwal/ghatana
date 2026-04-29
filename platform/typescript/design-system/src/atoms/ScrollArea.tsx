import React from "react";

export interface ScrollAreaProps {
  className?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export function ScrollArea({ className, style, children }: ScrollAreaProps) {
  return (
    <div className={className} style={{ overflow: "auto", ...style }}>
      {children}
    </div>
  );
}

export default ScrollArea;
