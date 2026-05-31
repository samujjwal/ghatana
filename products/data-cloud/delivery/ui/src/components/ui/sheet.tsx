import type { HTMLAttributes, ReactElement, ReactNode } from "react";

export function Sheet({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  return <>{children}</>;
}

export function SheetTrigger({
  children,
}: {
  readonly asChild?: boolean;
  readonly children: ReactElement;
}): ReactNode {
  return children;
}

export function SheetContent({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  return (
    <aside
      className={`mt-4 rounded-md border border-gray-200 bg-white p-4 shadow-sm ${className}`}
      {...props}
    />
  );
}

export function SheetHeader({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  return <div className={`space-y-1 ${className}`} {...props} />;
}

export function SheetTitle({
  className = "",
  ...props
}: HTMLAttributes<HTMLHeadingElement>): ReactNode {
  return (
    // eslint-disable-next-line jsx-a11y/heading-has-content -- Content is supplied through component props.
    <h2
      className={`text-lg font-semibold text-gray-900 ${className}`}
      {...props}
    />
  );
}

export function SheetDescription({
  className = "",
  ...props
}: HTMLAttributes<HTMLParagraphElement>): ReactNode {
  return <p className={`text-sm text-gray-600 ${className}`} {...props} />;
}
