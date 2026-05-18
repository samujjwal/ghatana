import type { HTMLAttributes, ReactNode } from "react";

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  readonly variant?: BadgeVariant;
}

const variantClass: Record<BadgeVariant, string> = {
  default: "border-transparent bg-blue-600 text-white",
  secondary: "border-transparent bg-gray-100 text-gray-900",
  destructive: "border-transparent bg-red-600 text-white",
  outline: "border-gray-300 text-gray-900",
};

export function Badge({
  className = "",
  variant = "default",
  ...props
}: BadgeProps): ReactNode {
  return (
    <span
      className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${variantClass[variant]} ${className}`}
      {...props}
    />
  );
}
