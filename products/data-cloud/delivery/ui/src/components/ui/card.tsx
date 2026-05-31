import type { HTMLAttributes, ReactNode } from "react";

type DivProps = HTMLAttributes<HTMLDivElement>;

export function Card({ className = "", ...props }: DivProps): ReactNode {
  return (
    <div
      className={`rounded-lg border border-gray-200 bg-white shadow-sm ${className}`}
      {...props}
    />
  );
}

export function CardHeader({ className = "", ...props }: DivProps): ReactNode {
  return <div className={`space-y-1.5 p-6 ${className}`} {...props} />;
}

export function CardTitle({ className = "", ...props }: DivProps): ReactNode {
  return (
    // eslint-disable-next-line jsx-a11y/heading-has-content -- Content is supplied through component props.
    <h3
      className={`text-lg font-semibold leading-none tracking-normal text-gray-900 ${className}`}
      {...props}
    />
  );
}

export function CardDescription({
  className = "",
  ...props
}: DivProps): ReactNode {
  return <p className={`text-sm text-gray-600 ${className}`} {...props} />;
}

export function CardContent({ className = "", ...props }: DivProps): ReactNode {
  return <div className={`p-6 pt-0 ${className}`} {...props} />;
}
