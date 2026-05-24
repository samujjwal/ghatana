import type { HTMLAttributes, ReactNode } from "react";

type AlertVariant = "default" | "destructive";

interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  readonly variant?: AlertVariant;
}

type DivProps = HTMLAttributes<HTMLDivElement>;

const variantClasses: Record<AlertVariant, string> = {
  default: "border-gray-200 bg-white text-gray-900",
  destructive: "border-red-200 bg-red-50 text-red-900",
};

export function Alert({
  className = "",
  variant = "default",
  ...props
}: AlertProps): ReactNode {
  return (
    <div
      role="alert"
      className={`relative w-full rounded-lg border p-4 ${variantClasses[variant]} ${className}`}
      {...props}
    />
  );
}

export function AlertTitle({ className = "", ...props }: DivProps): ReactNode {
  return <div className={`mb-1 font-medium leading-none ${className}`} {...props} />;
}

export function AlertDescription({
  className = "",
  ...props
}: DivProps): ReactNode {
  return <div className={`text-sm leading-relaxed ${className}`} {...props} />;
}
